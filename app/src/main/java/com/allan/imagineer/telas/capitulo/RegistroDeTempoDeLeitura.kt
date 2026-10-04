package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.rede.RepositorioDeEstatisticas
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

// O tempo de leitura (RL16): contar só o que é leitura e não perder nada se o servidor estiver fora.

/**
 * Conta o tempo de leitura **só enquanto há movimento** (RL16): se ficam [limiteParadoMs] sem rolar nem trocar de página, a contagem
 * pausa até o próximo movimento. Funções puras sobre o relógio que se passa, para serem testadas sem esperar.
 */
class CronometroDeLeitura(private val limiteParadoMs: Long = LIMITE_PARADO_MS, inicioMs: Long = 0) {
    private var ultimaAtividade = inicioMs
    private var ultimoAssento = inicioMs
    private var contado = 0L

    /** Fecha a conta até [agoraMs]: o que passou desde o último fechamento conta **só até [limiteParadoMs] depois da última atividade**. */
    private fun assentar(agoraMs: Long) {
        val contaAte = minOf(agoraMs, ultimaAtividade + limiteParadoMs)
        contado += (contaAte - ultimoAssento).coerceAtLeast(0)
        ultimoAssento = maxOf(ultimoAssento, agoraMs)
    }

    /**
     * A pessoa rolou ou trocou de página em [agoraMs]. A conta é fechada **antes** de mudar a última atividade: o tempo parado entre
     * a pausa e este movimento não pode entrar na conta só porque agora há atividade.
     */
    fun atividade(agoraMs: Long) {
        assentar(agoraMs)
        ultimaAtividade = agoraMs
    }

    /** O que passou desde o último tique **e conta como leitura**, em milissegundos. */
    fun tique(agoraMs: Long): Long {
        assentar(agoraMs)
        val resultado = contado
        contado = 0
        return resultado
    }

    /** Recomeça a contar de [agoraMs] (o capítulo voltou à frente): o tempo em que esteve fora não conta. */
    fun retomar(agoraMs: Long) {
        contado = 0
        ultimoAssento = agoraMs
        ultimaAtividade = agoraMs
    }

    companion object {
        /** 3 minutos sem movimento pausam a contagem (RL16). */
        const val LIMITE_PARADO_MS = 3 * 60 * 1000L
    }
}

/**
 * Guarda o tempo lido e o envia ao servidor (RL16). O que ainda **não foi enviado** fica no aparelho (por livro e dia) e vai na próxima
 * chance: sem conexão, nada se perde. Vive no escopo do app; tudo passa por um [Mutex] para dois envios não se atropelarem.
 */
class RegistroDeTempoDeLeitura(
    private val armazenamento: ArmazenamentoDeConfiguracao,
    private val servidor: RepositorioDeEstatisticas,
    private val hoje: () -> LocalDate = { LocalDate.now() },
) {
    private val trava = Mutex()

    /** Soma [segundos] ao livro, **no dia de hoje do aparelho**, e tenta enviar o que está pendente. */
    suspend fun registrar(livroId: Int, segundos: Int) {
        if (segundos <= 0) return
        trava.withLock {
            val pendente = lerPendente().toMutableMap()
            val chave = chave(livroId, hoje().toString())
            pendente[chave] = (pendente[chave] ?: 0) + segundos
            armazenamento.salvarTempoPendente(JSON.encodeToString(SERIALIZADOR, pendente))
        }
        enviarPendentes()
    }

    /** Tenta enviar tudo o que está pendente; o que o servidor não aceitar continua guardado. */
    suspend fun enviarPendentes() {
        trava.withLock {
            val pendente = lerPendente().toMutableMap()
            for ((chave, segundos) in pendente.toList()) {
                val par = separar(chave)
                if (par == null) {  // uma chave que este app não entende: não adianta guardá-la
                    pendente.remove(chave)
                    continue
                }
                val (livroId, dia) = par
                var restante = segundos
                // O servidor aceita até 3600 por chamada.
                while (restante > 0) {
                    val parte = minOf(restante, LIMITE_POR_ENVIO)
                    val resposta = servidor.somarTempo(livroId, dia, parte)
                    // Livro que já não existe (404) ou dia recusado (422) nunca vão ser aceitos: descartar, em vez de tentar para sempre.
                    if (resposta is ResultadoDaChamada.Falha && (resposta.codigoHttp == 404 || resposta.codigoHttp == 422)) {
                        restante = 0
                        break
                    }
                    if (resposta !is ResultadoDaChamada.Sucesso) break
                    restante -= parte
                }
                if (restante <= 0) pendente.remove(chave) else pendente[chave] = restante
            }
            armazenamento.salvarTempoPendente(JSON.encodeToString(SERIALIZADOR, pendente))
        }
    }

    private suspend fun lerPendente(): Map<String, Int> =
        runCatching { JSON.decodeFromString(SERIALIZADOR, armazenamento.tempoPendente.first().orEmpty().ifBlank { "{}" }) }.getOrDefault(emptyMap())

    private fun chave(livroId: Int, dia: String) = "$livroId|$dia"

    private fun separar(chave: String): Pair<Int, String>? {
        val partes = chave.split("|")
        val livro = partes.getOrNull(0)?.toIntOrNull() ?: return null
        val dia = partes.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return livro to dia
    }

    companion object {
        /** O limite do servidor por chamada. */
        const val LIMITE_POR_ENVIO = 3600

        /** De quanto em quanto tempo lido o app envia (RL16). */
        const val ENVIAR_A_CADA_SEGUNDOS = 60

        private val JSON = Json { ignoreUnknownKeys = true }
        private val SERIALIZADOR = MapSerializer(String.serializer(), Int.serializer())
    }
}
