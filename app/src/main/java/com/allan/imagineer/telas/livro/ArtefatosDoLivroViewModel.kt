package com.allan.imagineer.telas.livro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.ArtefatosDoLivro
import com.allan.imagineer.rede.RepositorioDeArtefatosDoLivro
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Qual das duas telas novas do livro (LY7 e LY8). */
enum class TipoDaListaDoLivro(val titulo: String, val vazia: String) {
    PENDENCIAS("Pendências", "Nada a confirmar."),
    CENAS("Cenas", "Este livro ainda não tem cenas. Elas aparecem depois de analisar um capítulo."),
}

/** Para onde tocar num artefato leva: o capítulo, e o que abrir nele. */
data class AlvoNoCapitulo(
    val capituloId: Int,
    /** O frame (a cena já confirmada) cujo modal abre; nulo = abre o painel de IA do capítulo. */
    val abrirFrameId: Int? = null,
    val abrirRotulo: String? = null,
    val abrirPainel: Boolean = false,
)

/**
 * Para onde leva o toque num artefato (LY7, LY8): uma **cena com frame** abre o modal dela no capítulo; qualquer outra coisa (um
 * elemento ou uma cena ainda só sugerida) abre o **painel de IA** do capítulo, onde se confirma.
 */
fun alvoDoToque(capituloId: Int, artefato: Artefato): AlvoNoCapitulo =
    if (artefato.tipo == "CENA" && artefato.frame_id != null) AlvoNoCapitulo(capituloId, artefato.frame_id, artefato.rotulo)
    else AlvoNoCapitulo(capituloId, abrirPainel = true)

/** A situação como se lê na lista de cenas. */
fun rotuloDaSituacao(situacao: String): String = when (situacao) {
    "SUGERIDO" -> "sugerida"
    "CONFIRMADO" -> "confirmada"
    "PROMPT_PRONTO" -> "prompt pronto"
    "ILUSTRADO" -> "ilustrada"
    else -> situacao.lowercase()
}

/** O título do grupo: "Capítulo 3 · A chegada" (ou só "Capítulo 3" sem título). */
fun tituloDoGrupo(ordem: Int, titulo: String?): String = if (titulo.isNullOrBlank()) "Capítulo $ordem" else "Capítulo $ordem · $titulo"

sealed interface EstadoDaListaDoLivro {
    data object Carregando : EstadoDaListaDoLivro
    data class Pronto(val dados: ArtefatosDoLivro) : EstadoDaListaDoLivro
    data class Erro(val motivo: String) : EstadoDaListaDoLivro
}

/** A lógica das telas Pendências e Cenas (LY7, LY8): lê da rota do livro; uma releitura que falha **não apaga** o que já estava na tela. */
class ArtefatosDoLivroViewModel(
    private val livroId: Int,
    private val tipo: TipoDaListaDoLivro,
    private val repositorio: RepositorioDeArtefatosDoLivro,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoDaListaDoLivro>(EstadoDaListaDoLivro.Carregando)
    val estado: StateFlow<EstadoDaListaDoLivro> = _estado.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            val resultado = when (tipo) {
                TipoDaListaDoLivro.PENDENCIAS -> repositorio.pendencias(livroId)
                TipoDaListaDoLivro.CENAS -> repositorio.cenas(livroId)
            }
            when (resultado) {
                is ResultadoDaChamada.Sucesso -> _estado.value = EstadoDaListaDoLivro.Pronto(resultado.dado)
                is ResultadoDaChamada.Falha -> _estado.update { if (it is EstadoDaListaDoLivro.Pronto) it else EstadoDaListaDoLivro.Erro(resultado.motivo) }
            }
        }
    }

    fun tentarDeNovo() {
        _estado.value = EstadoDaListaDoLivro.Carregando
        carregar()
    }
}
