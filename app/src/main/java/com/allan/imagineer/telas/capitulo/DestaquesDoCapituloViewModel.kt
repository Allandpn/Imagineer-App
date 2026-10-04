package com.allan.imagineer.telas.capitulo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.CorDeDestaque
import com.allan.imagineer.rede.Destaque
import com.allan.imagineer.rede.RepositorioDeDestaques
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Os destaques de **um capítulo** (RL9 a RL13). Como os ícones dos artefatos, **nunca seguram o texto**: vêm por uma chamada à
 * parte e, se falharem, o capítulo continua legível, só sem a cor. Já o que a pessoa **faz** (destacar, anotar, remover) mostra o
 * motivo em [aviso] quando o servidor não responde, em vez de fingir que gravou.
 */
class DestaquesDoCapituloViewModel(
    private val livroId: Int,
    private val capituloId: Int,
    private val repositorio: RepositorioDeDestaques,
) : ViewModel() {

    private val _destaques = MutableStateFlow<List<Destaque>>(emptyList())
    val destaques: StateFlow<List<Destaque>> = _destaques.asStateFlow()

    private val _aviso = MutableStateFlow<String?>(null)

    /** Uma mensagem curta para a pessoa ler (e [avisoLido] limpa); nula quando está tudo bem. */
    val aviso: StateFlow<String?> = _aviso.asStateFlow()

    fun avisoLido() {
        _aviso.value = null
    }

    fun carregar() {
        viewModelScope.launch {
            (repositorio.listar(livroId, capituloId) as? ResultadoDaChamada.Sucesso)?.let { _destaques.value = it.dado }
        }
    }

    /** Destaca [lugar] com [cor]. Devolve o destaque criado por [aoCriar], para quem quiser abrir a folha dele. */
    fun destacar(lugar: LugarDoTrecho, cor: CorDeDestaque = CorDeDestaque.AMARELO, aoCriar: (Destaque) -> Unit = {}) {
        viewModelScope.launch {
            when (val r = repositorio.criar(livroId, capituloId, lugar.inicio, lugar.fim, cor)) {
                is ResultadoDaChamada.Sucesso -> {
                    _destaques.value = (_destaques.value + r.dado).sortedBy { it.inicio }
                    aoCriar(r.dado)
                }
                is ResultadoDaChamada.Falha -> _aviso.value = "Não consegui destacar: ${r.motivo}"
            }
        }
    }

    fun mudarCor(destaqueId: Int, cor: CorDeDestaque) = ajustar { repositorio.ajustarCor(destaqueId, cor) }

    fun mudarNota(destaqueId: Int, nota: String?) = ajustar { repositorio.ajustarNota(destaqueId, nota?.trim()?.ifEmpty { null }) }

    fun ligarAoElemento(destaqueId: Int, elementoId: Int?) = ajustar { repositorio.ligarAoElemento(destaqueId, elementoId) }

    private fun ajustar(chamada: suspend () -> ResultadoDaChamada<Destaque>) {
        viewModelScope.launch {
            when (val r = chamada()) {
                is ResultadoDaChamada.Sucesso -> _destaques.value = _destaques.value.map { if (it.id == r.dado.id) r.dado else it }
                is ResultadoDaChamada.Falha -> _aviso.value = "Não consegui salvar: ${r.motivo}"
            }
        }
    }

    fun remover(destaqueId: Int) {
        viewModelScope.launch {
            when (val r = repositorio.remover(destaqueId)) {
                is ResultadoDaChamada.Sucesso -> _destaques.value = _destaques.value.filterNot { it.id == destaqueId }
                is ResultadoDaChamada.Falha -> _aviso.value = "Não consegui remover: ${r.motivo}"
            }
        }
    }
}
