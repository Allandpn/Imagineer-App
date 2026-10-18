package com.allan.imagineer.local

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Pergunta ao servidor se ele está respondendo (PL14b). Interface, para testar sem rede. */
fun interface SondaDoServidor {
    suspend fun responde(): Boolean
}

/**
 * O **Modo offline** (PL14): enquanto está ligado, toda chamada ao servidor falha na hora, sem esperar o tempo
 * de conexão, e as telas tratam o aparelho como "sem conexão" (PL8, PL9).
 *
 * Liga por pedido da pessoa ([entrar]) ou depois de ela aceitar a [oferta], que aparece quando uma chamada falha
 * sem resposta do servidor ([servidorSemResposta]). Desliga sozinho quando a [sonda] vê o servidor responder.
 */
class ModoOffline(
    private val sonda: SondaDoServidor,
    private val escopo: CoroutineScope,
    private val agora: () -> Long = System::currentTimeMillis,
    private val intervaloDaSondaEmMs: Long = 15_000,
    private val silencioDaRecusaEmMs: Long = 60_000,
) {
    private val _ativo = MutableStateFlow(false)
    val ativo: StateFlow<Boolean> = _ativo.asStateFlow()

    private val _oferta = MutableStateFlow(false)
    /** O aviso "Servidor indisponível. Usar o modo offline?" está pedindo resposta. */
    val oferta: StateFlow<Boolean> = _oferta.asStateFlow()

    private val _voltou = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Dispara quando o servidor voltou e o modo desligou sozinho (PL14b). */
    val voltou: SharedFlow<Unit> = _voltou.asSharedFlow()

    private var silenciadaAte = 0L
    private var vigia: Job? = null

    /** Uma chamada falhou sem resposta do servidor: oferece o modo offline, se couber (chamado de qualquer thread). */
    fun servidorSemResposta() {
        if (_ativo.value || _oferta.value || agora() < silenciadaAte) return
        _oferta.value = true
    }

    fun entrar() {
        _oferta.value = false
        if (_ativo.value) return
        _ativo.value = true
        vigia = escopo.launch {
            while (isActive) {
                delay(intervaloDaSondaEmMs)
                if (conferirESair()) break
            }
        }
    }

    /** "Agora não": o aviso some e não volta por um tempo. */
    fun recusarOferta() {
        _oferta.value = false
        silenciadaAte = agora() + silencioDaRecusaEmMs
    }

    /** Sonda o servidor já (ao voltar para o app, ao tocar na etiqueta); se ele responde, sai do modo offline. */
    fun verificarAgora() {
        if (!_ativo.value) return
        escopo.launch { conferirESair() }
    }

    private suspend fun conferirESair(): Boolean {
        if (!_ativo.value) return true
        if (!sonda.responde()) return false
        _ativo.value = false
        vigia?.cancel()
        _voltou.tryEmit(Unit)
        return true
    }
}

/**
 * O [Conexao] que as telas leem: há conexão quando a **rede do Android** está de pé **e** o [ModoOffline] está desligado.
 * É o que liga a etiqueta e os itens desativados do PL8 e do PL9 ao modo.
 */
class ConexaoEfetiva(rede: Conexao, modo: ModoOffline, escopo: CoroutineScope) : Conexao {
    override val online: StateFlow<Boolean> =
        combine(rede.online, modo.ativo) { redeLigada, offline -> redeLigada && !offline }
            .stateIn(escopo, SharingStarted.Eagerly, rede.online.value && !modo.ativo.value)
}

/**
 * A [SondaDoServidor] de verdade: um `HEAD /livros` com 4 s de limite, por um cliente à parte (o da API recusa chamadas
 * em modo offline). Qualquer resposta abaixo de 500 vale: até um 401 prova que o servidor está lá.
 */
class SondaDoServidorPelaRede(private val armazenamento: ArmazenamentoDeConfiguracao) : SondaDoServidor {
    private val cliente = OkHttpClient.Builder().callTimeout(4, TimeUnit.SECONDS).build()

    override suspend fun responde(): Boolean {
        val url = armazenamento.urlDoServidor.first() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                val pedido = Request.Builder().url(url.trimEnd('/') + "/livros").head().build()
                cliente.newCall(pedido).execute().use { it.code < 500 }
            } catch (erro: IOException) {
                false
            } catch (erro: IllegalArgumentException) {
                false // endereço que o OkHttp não entende
            }
        }
    }
}
