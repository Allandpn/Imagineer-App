package com.allan.imagineer.telas.capitulo.painel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.RepositorioDeSugestoes
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** O que o painel de IA mostra (item 7.5b, incremento 9). */
sealed interface ConteudoDoPainel {
    /** O painel ainda não foi aberto: nada foi pedido ao servidor (P1). */
    data object NaoCarregado : ConteudoDoPainel

    /** Lendo o que está salvo (`GET`, que nunca gasta IA — P2). */
    data object Lendo : ConteudoDoPainel

    /** O capítulo nunca foi analisado. O único botão é "Analisar com IA" (P6). */
    data class NuncaAnalisado(val pendentesAnteriores: Int) : ConteudoDoPainel

    /** Há um resultado — inclusive uma análise que não achou nada (P14). */
    data class Pronto(val sugestoes: SugestoesDeCapitulo) : ConteudoDoPainel

    /** A leitura falhou. [motivo] já está escrito para o usuário. */
    data class Erro(val motivo: String) : ConteudoDoPainel
}

/**
 * Tudo o que o painel mostra. A análise é uma **camada por cima do conteúdo**
 * ([analisando], [erroDaAnalise]) e não um conteúdo próprio: enquanto a IA roda, e se ela
 * falhar, o conteúdo de antes continua ali (P8).
 */
data class EstadoDoPainel(
    val conteudo: ConteudoDoPainel = ConteudoDoPainel.NaoCarregado,
    val analisando: Boolean = false,
    /** A falha da última análise, já na mensagem da API; some ao começar outra. */
    val erroDaAnalise: String? = null,
    /** O diálogo "Isso refaz as sugestões e gasta IA" está aberto (P7). */
    val confirmandoReanalise: Boolean = false,
)

/**
 * A lógica do painel de IA de um capítulo. Só leitura e análise: confirmar, ajustar e
 * descartar sugestões são do incremento 10 (P15).
 *
 * **Ler nunca custa, gerar custa.** [aoAbrirPainel] e [tentarDeNovo] usam o `GET`;
 * [analisar] e [confirmarReanalise] são os **únicos** caminhos até o `POST` — e portanto
 * os únicos que gastam IA (item 6.8).
 */
class PainelDeIaViewModel(
    private val capituloId: Int,
    private val sugestoes: RepositorioDeSugestoes,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDoPainel())
    val estado: StateFlow<EstadoDoPainel> = _estado.asStateFlow()

    /**
     * O painel foi aberto. Na **primeira** vez, lê o que está salvo; depois guarda o
     * resultado e não relê a cada abrir e fechar (P1). Um erro de leitura só se refaz
     * por [tentarDeNovo], de propósito do usuário.
     */
    fun aoAbrirPainel() {
        if (_estado.value.conteudo is ConteudoDoPainel.NaoCarregado) ler()
    }

    /** "Tentar de novo" depois de um erro de leitura. */
    fun tentarDeNovo() {
        if (_estado.value.conteudo is ConteudoDoPainel.Erro) ler()
    }

    private fun ler() {
        _estado.update { it.copy(conteudo = ConteudoDoPainel.Lendo) }
        viewModelScope.launch {
            val resultado = sugestoes.ler(capituloId)
            _estado.update {
                it.copy(
                    conteudo = when (resultado) {
                        is ResultadoDaChamada.Sucesso -> conteudoDe(resultado.dado)
                        is ResultadoDaChamada.Falha -> ConteudoDoPainel.Erro(resultado.motivo)
                    },
                )
            }
        }
    }

    /**
     * "Analisar com IA" — **só existe quando o capítulo nunca foi analisado** (P6). Não
     * faz nada em nenhum outro estado, nem se já há uma análise em andamento.
     */
    fun analisar() {
        val atual = _estado.value
        if (atual.conteudo !is ConteudoDoPainel.NuncaAnalisado || atual.analisando) return
        executarAnalise(forcar = false)
    }

    /** "Reanalisar": só depois de analisado, e **pede confirmação** antes de gastar (P7). */
    fun pedirReanalise() {
        val atual = _estado.value
        if (atual.conteudo !is ConteudoDoPainel.Pronto || atual.analisando) return
        _estado.update { it.copy(confirmandoReanalise = true) }
    }

    fun cancelarReanalise() {
        _estado.update { it.copy(confirmandoReanalise = false) }
    }

    /** O "sim" do diálogo: refaz as sugestões ainda não confirmadas (`forcar = true`). */
    fun confirmarReanalise() {
        val atual = _estado.value
        if (!atual.confirmandoReanalise || atual.analisando) return
        _estado.update { it.copy(confirmandoReanalise = false) }
        executarAnalise(forcar = true)
    }

    /**
     * O único ponto que chama o `POST`. **Sem repetição automática** (P8): repetir sozinho
     * cobraria duas vezes sem ninguém pedir. Se falhar, o conteúdo de antes continua e a
     * mensagem da API aparece.
     */
    private fun executarAnalise(forcar: Boolean) {
        _estado.update { it.copy(analisando = true, erroDaAnalise = null) }
        viewModelScope.launch {
            when (val resultado = sugestoes.analisar(capituloId, forcar)) {
                is ResultadoDaChamada.Sucesso ->
                    _estado.update { EstadoDoPainel(conteudo = conteudoDe(resultado.dado)) }
                is ResultadoDaChamada.Falha ->
                    _estado.update { it.copy(analisando = false, erroDaAnalise = resultado.motivo) }
            }
        }
    }

    /** `gerado_em` nulo = nunca analisado; qualquer outra coisa é um resultado (P14). */
    private fun conteudoDe(dado: SugestoesDeCapitulo): ConteudoDoPainel =
        if (dado.gerado_em == null) {
            ConteudoDoPainel.NuncaAnalisado(dado.sugestoes_pendentes_anteriores)
        } else {
            ConteudoDoPainel.Pronto(dado)
        }
}
