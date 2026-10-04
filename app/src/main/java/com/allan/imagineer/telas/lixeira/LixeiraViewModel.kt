package com.allan.imagineer.telas.lixeira

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.ImagemNaLixeira
import com.allan.imagineer.rede.Lixeira
import com.allan.imagineer.rede.RepositorioDaLixeira
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** O que a tela da Lixeira mostra (item 7.5b, LX8). */
sealed interface CargaDaLixeira {
    data object Carregando : CargaDaLixeira
    data class Pronta(val lixeira: Lixeira) : CargaDaLixeira
    data class Erro(val motivo: String) : CargaDaLixeira
}

/** O que falta o usuário confirmar: apagar **uma** de vez, ou esvaziar **tudo** (LX8). Ambos não têm volta. */
sealed interface ConfirmacaoDaLixeira {
    data class ApagarUma(val imagem: ImagemNaLixeira) : ConfirmacaoDaLixeira
    data class EsvaziarTudo(val quantas: Int, val bytes: Long) : ConfirmacaoDaLixeira
}

data class EstadoDaLixeira(
    val carga: CargaDaLixeira = CargaDaLixeira.Carregando,
    val confirmacao: ConfirmacaoDaLixeira? = null,
    /** O recado do que acabou de acontecer, ou o erro de uma ação; some na próxima ação. */
    val recado: String? = null,
    val recadoEhErro: Boolean = false,
    /** Imagens com uma ação em andamento: os botões delas ficam desligados. */
    val ocupadas: Set<Int> = emptySet(),
    val esvaziando: Boolean = false,
)

/**
 * A lógica da tela da Lixeira (LX8): ler, **restaurar**, **apagar de vez** (com confirmação) e **esvaziar** (com confirmação que diz
 * quanto vai embora). Nada some sozinho (LX6): só o que o usuário confirma.
 */
class LixeiraViewModel(private val lixeira: RepositorioDaLixeira) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaLixeira())
    val estado: StateFlow<EstadoDaLixeira> = _estado.asStateFlow()

    /** Lê a lixeira; uma leitura que falha **não apaga** o que já estava na tela. */
    fun carregar() {
        viewModelScope.launch {
            when (val resultado = lixeira.listar()) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(carga = CargaDaLixeira.Pronta(resultado.dado)) }
                is ResultadoDaChamada.Falha -> _estado.update {
                    if (it.carga is CargaDaLixeira.Pronta) it.copy(recado = resultado.motivo, recadoEhErro = true)
                    else it.copy(carga = CargaDaLixeira.Erro(resultado.motivo))
                }
            }
        }
    }

    fun tentarDeNovo() {
        _estado.update { it.copy(carga = CargaDaLixeira.Carregando) }
        carregar()
    }

    /** Tira a imagem da lixeira: ela volta ao prompt, ao capítulo e à galeria (a canônica e as âncoras que tinha não voltam). */
    fun restaurar(imagemId: Int) {
        if (imagemId in _estado.value.ocupadas) return
        _estado.update { it.copy(ocupadas = it.ocupadas + imagemId, recado = null) }
        viewModelScope.launch {
            when (val resultado = lixeira.restaurar(imagemId)) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.update { it.copy(recado = "Imagem restaurada.", recadoEhErro = false) }
                    carregar()
                }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(recado = resultado.motivo, recadoEhErro = true) }
            }
            _estado.update { it.copy(ocupadas = it.ocupadas - imagemId) }
        }
    }

    fun pedirApagarDeVez(imagem: ImagemNaLixeira) {
        _estado.update { it.copy(confirmacao = ConfirmacaoDaLixeira.ApagarUma(imagem)) }
    }

    fun pedirEsvaziar() {
        val pronta = (_estado.value.carga as? CargaDaLixeira.Pronta)?.lixeira ?: return
        if (pronta.imagens.isEmpty()) return
        _estado.update { it.copy(confirmacao = ConfirmacaoDaLixeira.EsvaziarTudo(pronta.imagens.size, pronta.total_em_bytes)) }
    }

    fun cancelarConfirmacao() {
        _estado.update { it.copy(confirmacao = null) }
    }

    /** O usuário confirmou: apaga de vez a imagem ou esvazia tudo, conforme o que foi pedido. */
    fun confirmar() {
        val pedido = _estado.value.confirmacao ?: return
        _estado.update { it.copy(confirmacao = null, recado = null) }
        when (pedido) {
            is ConfirmacaoDaLixeira.ApagarUma -> apagarDeVez(pedido.imagem.id)
            is ConfirmacaoDaLixeira.EsvaziarTudo -> esvaziar()
        }
    }

    private fun apagarDeVez(imagemId: Int) {
        _estado.update { it.copy(ocupadas = it.ocupadas + imagemId) }
        viewModelScope.launch {
            when (val resultado = lixeira.apagarDeVez(imagemId)) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.update { it.copy(recado = "Imagem apagada de vez.", recadoEhErro = false) }
                    carregar()
                }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(recado = resultado.motivo, recadoEhErro = true) }
            }
            _estado.update { it.copy(ocupadas = it.ocupadas - imagemId) }
        }
    }

    private fun esvaziar() {
        _estado.update { it.copy(esvaziando = true) }
        viewModelScope.launch {
            when (val resultado = lixeira.esvaziar()) {
                is ResultadoDaChamada.Sucesso -> {
                    val r = resultado.dado
                    _estado.update { it.copy(recado = recadoDeEsvaziada(r.removidas, r.liberados_em_bytes), recadoEhErro = false) }
                    carregar()
                }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(recado = resultado.motivo, recadoEhErro = true) }
            }
            _estado.update { it.copy(esvaziando = false) }
        }
    }
}
