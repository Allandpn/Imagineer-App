package com.allan.imagineer.telas.biblioteca

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.comum.ControleDeRemocao
import com.allan.imagineer.telas.comum.EstadoDaRemocao
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Os quatro estados da Biblioteca (item 7.3a). A tela é uma função de um deles.
 */
sealed interface EstadoDaBiblioteca {
    /** Primeira carga, ou recarga depois de um erro ou de uma lista vazia. */
    data object Carregando : EstadoDaBiblioteca

    /**
     * Há livros. [atualizando] é a recarga silenciosa: a lista antiga continua
     * visível enquanto a nova não chega.
     */
    data class Lista(
        val livros: List<LivroResumo>,
        val atualizando: Boolean = false,
    ) : EstadoDaBiblioteca

    /** Nenhum livro importado ainda. Não é erro — é um convite a importar. */
    data object Vazia : EstadoDaBiblioteca

    /** [motivo] já está escrito para o usuário ler. */
    data class Erro(val motivo: String) : EstadoDaBiblioteca
}

/**
 * A lógica da tela de Biblioteca (item 7.2): carregar a lista, dizer em que
 * estado ela está e remover livros. Não sabe nada de rede — só fala com o
 * [RepositorioDeLivros].
 */
class BibliotecaViewModel(
    private val repositorio: RepositorioDeLivros,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoDaBiblioteca>(EstadoDaBiblioteca.Carregando)
    val estado: StateFlow<EstadoDaBiblioteca> = _estado.asStateFlow()

    // A máquina de estados de "remover livro" é compartilhada com a tela de Livro.
    // Depois de remover, a lista é recarregada.
    private val controleDeRemocao = ControleDeRemocao(repositorio, viewModelScope) { carregar() }
    val remocao: StateFlow<EstadoDaRemocao> = controleDeRemocao.estado

    private var carregamentoEmAndamento: Job? = null

    /**
     * Busca a lista no servidor. A tela chama isto toda vez que fica visível
     * (item 7.3a) e no "puxar para atualizar".
     *
     * Se já há uma lista na tela, a recarga é silenciosa (a lista antiga fica
     * visível); nos demais casos, mostra o indicador de carregando.
     */
    fun carregar() {
        carregamentoEmAndamento?.cancel()

        val atual = _estado.value
        _estado.value = if (atual is EstadoDaBiblioteca.Lista) {
            atual.copy(atualizando = true)
        } else {
            EstadoDaBiblioteca.Carregando
        }

        carregamentoEmAndamento = viewModelScope.launch {
            _estado.value = when (val resultado = repositorio.listarLivros()) {
                is ResultadoDaChamada.Sucesso ->
                    if (resultado.dado.isEmpty()) {
                        EstadoDaBiblioteca.Vazia
                    } else {
                        EstadoDaBiblioteca.Lista(resultado.dado)
                    }
                is ResultadoDaChamada.Falha -> EstadoDaBiblioteca.Erro(resultado.motivo)
            }
        }
    }

    /** O usuário escolheu "Remover" no menu do cartão: abre o diálogo de confirmação. */
    fun pedirRemocao(livro: LivroResumo) = controleDeRemocao.pedir(livro)

    /** "Cancelar" ou "Fechar": fecha o diálogo sem remover nada. */
    fun cancelarRemocao() = controleDeRemocao.cancelar()

    /** "Remover" (ou "Tentar de novo", depois de uma falha). */
    fun confirmarRemocao() = controleDeRemocao.confirmar()
}

/**
 * O texto de capítulos de um cartão: "12 capítulos", ou "12 capítulos · 3
 * ignorados" quando há ignorados. Só mostra os ignorados se houver algum.
 *
 * Função pura, fora do Compose, para ser testável na JVM.
 */
fun descreverCapitulos(total: Int, ignorados: Int): String {
    val capitulos = if (total == 1) "1 capítulo" else "$total capítulos"
    return when {
        ignorados <= 0 -> capitulos
        ignorados == 1 -> "$capitulos · 1 ignorado"
        else -> "$capitulos · $ignorados ignorados"
    }
}
