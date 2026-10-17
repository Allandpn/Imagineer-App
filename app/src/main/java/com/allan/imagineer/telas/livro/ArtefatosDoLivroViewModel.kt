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

/**
 * O que a pessoa escolheu para filtrar a lista de cenas (LF1 a LF3): [capitulos] são ids de capítulo e [elementos] são nomes. Conjunto vazio = sem
 * filtro daquele tipo. Os dois se combinam: a cena precisa estar num dos capítulos escolhidos **e** ter pelo menos um dos elementos escolhidos.
 */
data class FiltroDasCenas(val capitulos: Set<Int> = emptySet(), val elementos: Set<String> = emptySet()) {
    val vazio: Boolean get() = capitulos.isEmpty() && elementos.isEmpty()

    /** Quantos tipos de filtro estão ligados (0 a 2), para o botão "Limpar" e o texto de ajuda. */
    val ativos: Int get() = (if (capitulos.isEmpty()) 0 else 1) + (if (elementos.isEmpty()) 0 else 1)
}

/** Aplica o [filtro] à lista de cenas (LF3). Capítulo que fica sem nenhuma cena some do resultado; o `total` acompanha. */
fun filtrarCenas(dados: ArtefatosDoLivro, filtro: FiltroDasCenas): ArtefatosDoLivro {
    if (filtro.vazio) return dados
    val capitulos = dados.capitulos
        .filter { filtro.capitulos.isEmpty() || it.capitulo_id in filtro.capitulos }
        .map { grupo ->
            grupo.copy(artefatos = grupo.artefatos.filter { filtro.elementos.isEmpty() || it.elementos.any { nome -> nome in filtro.elementos } })
        }
        .filter { it.artefatos.isNotEmpty() }
    return ArtefatosDoLivro(total = capitulos.sumOf { it.artefatos.size }, capitulos = capitulos)
}

/** O texto de um botão de filtro: "Capítulos" sem escolha, "Capítulos (2)" com duas (LF3). */
fun rotuloDoFiltro(nome: String, escolhidos: Int): String = if (escolhidos == 0) nome else "$nome ($escolhidos)"

/** "3 de 12 cenas" / "1 de 12 cenas" (LF3): quantas sobraram do total. */
fun textoDasCenasQueSobraram(sobraram: Int, total: Int): String = "$sobraram de $total ${if (total == 1) "cena" else "cenas"}"

/** Os nomes de elemento que aparecem nas cenas, sem repetir e em ordem alfabética (sem diferenciar maiúsculas): as opções do filtro por elemento (LF2). */
fun elementosDasCenas(dados: ArtefatosDoLivro): List<String> =
    dados.capitulos.flatMap { it.artefatos }.flatMap { it.elementos }.distinct().sortedBy { it.lowercase() }

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

    private val _filtro = MutableStateFlow(FiltroDasCenas())
    val filtro: StateFlow<FiltroDasCenas> = _filtro.asStateFlow()

    /** Marca ou desmarca um capítulo no filtro (LF1). */
    fun alternarCapitulo(capituloId: Int) = _filtro.update { it.copy(capitulos = it.capitulos.alternar(capituloId)) }

    /** Marca ou desmarca um elemento no filtro (LF2). */
    fun alternarElemento(nome: String) = _filtro.update { it.copy(elementos = it.elementos.alternar(nome)) }

    /** Tira todos os filtros (LF3). */
    fun limparFiltro() {
        _filtro.value = FiltroDasCenas()
    }

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

private fun <T> Set<T>.alternar(item: T): Set<T> = if (item in this) this - item else this + item
