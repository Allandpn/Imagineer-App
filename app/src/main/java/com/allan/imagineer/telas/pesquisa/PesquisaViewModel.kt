package com.allan.imagineer.telas.pesquisa

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.RepositorioDeBusca
import com.allan.imagineer.rede.ResultadoDaBusca
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** As abas da pesquisa (LV5): onde procurar. */
enum class AbaDaPesquisa(val rotulo: String) {
    CAPITULO("Capítulo"),
    LIVRO("Livro"),
    BIBLIOTECA("Biblioteca"),
}

/** O que cada aba mostra. */
sealed interface ResultadoDaAba {
    /** Termo curto demais ou ainda não digitado. */
    data object Vazio : ResultadoDaAba
    data object Buscando : ResultadoDaAba
    data class Pronto(val resultado: ResultadoDaBusca) : ResultadoDaAba
    data class Erro(val motivo: String) : ResultadoDaAba
}

data class EstadoDaPesquisa(
    val termo: String = "",
    val resultados: Map<AbaDaPesquisa, ResultadoDaAba> = emptyMap(),
)

/** O menor termo que o servidor aceita pesquisar. */
const val MINIMO_DO_TERMO = 2

/** O tempo, depois da última tecla, para começar a procurar. */
const val ATRASO_DA_DIGITACAO_MS = 400L

/** As abas de uma pesquisa: a do capítulo só existe quando se pesquisa de dentro de um capítulo. */
fun abasDaPesquisa(temCapitulo: Boolean): List<AbaDaPesquisa> =
    if (temCapitulo) AbaDaPesquisa.entries.toList() else listOf(AbaDaPesquisa.LIVRO, AbaDaPesquisa.BIBLIOTECA)

/** O trecho de uma ocorrência com o achado em **negrito**, para a lista mostrar o que casou. */
fun trechoDestacado(trecho: String, inicio: Int, fim: Int): AnnotatedString {
    val de = inicio.coerceIn(0, trecho.length)
    val ate = fim.coerceIn(de, trecho.length)
    return buildAnnotatedString {
        append(trecho.substring(0, de))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(trecho.substring(de, ate)) }
        append(trecho.substring(ate))
    }
}

/**
 * A pesquisa no texto (LV5): procura o termo nas abas de uma vez (capítulo, livro, biblioteca), meio segundo depois da última tecla.
 * Pesquisar **nunca gasta IA**: é uma leitura no servidor.
 */
class PesquisaViewModel(
    private val livroId: Int,
    private val capituloId: Int?,
    private val repositorio: RepositorioDeBusca,
    private val atrasoMs: Long = ATRASO_DA_DIGITACAO_MS,
) : ViewModel() {

    val abas: List<AbaDaPesquisa> = abasDaPesquisa(capituloId != null)

    private val _estado = MutableStateFlow(EstadoDaPesquisa())
    val estado: StateFlow<EstadoDaPesquisa> = _estado.asStateFlow()

    private var busca: Job? = null

    /** A pessoa digitou: espera um instante (para não procurar a cada letra) e procura. */
    fun alterarTermo(novo: String) {
        _estado.update { it.copy(termo = novo) }
        busca?.cancel()
        val termo = novo.trim()
        if (termo.length < MINIMO_DO_TERMO) {
            _estado.update { it.copy(resultados = emptyMap()) }
            return
        }
        busca = viewModelScope.launch {
            delay(atrasoMs)
            procurar(termo)
        }
    }

    private suspend fun procurar(termo: String) {
        _estado.update { it.copy(resultados = abas.associateWith { ResultadoDaAba.Buscando }) }
        val respostas = abas.map { aba ->
            viewModelScope.async {
                val resposta = when (aba) {
                    AbaDaPesquisa.CAPITULO -> repositorio.buscar(termo, capituloId = capituloId)
                    AbaDaPesquisa.LIVRO -> repositorio.buscar(termo, livroId = livroId)
                    AbaDaPesquisa.BIBLIOTECA -> repositorio.buscar(termo)
                }
                aba to when (resposta) {
                    is ResultadoDaChamada.Sucesso -> ResultadoDaAba.Pronto(resposta.dado)
                    is ResultadoDaChamada.Falha -> ResultadoDaAba.Erro(resposta.motivo)
                }
            }
        }.awaitAll()
        _estado.update { it.copy(resultados = respostas.toMap()) }
    }
}
