package com.allan.imagineer.telas.capitulo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.CapituloDetalhe
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Navegação entre capítulos por gesto (item 7.5c): o leitor é um pager sobre os capítulos do livro, e a
// página vizinha acompanha o dedo. As regras são puras e testadas na JVM.

/**
 * Os capítulos por onde se pode passar a página, em ordem de leitura (N2): os **não arquivados**, mais o capítulo
 * aberto ([atualId]) **mesmo que arquivado** — abre-se um arquivado pela área de arquivados, e ele precisa estar
 * na lista para o leitor começar nele. Se o atual nem consta da lista do livro, a lista é só ele.
 */
fun idsDoLeitor(capitulos: List<CapituloResumo>, atualId: Int): List<Int> {
    val ids = capitulos
        .sortedBy { it.ordem }
        .filter { !it.ignorado || it.id == atualId }
        .map { it.id }
    return if (atualId in ids) ids else listOf(atualId)
}

/**
 * Por onde o leitor pode passar a página. [completa] é falso enquanto só se conhece o capítulo aberto (a lista do
 * livro ainda não chegou, ou não pôde ser lida): o leitor funciona do mesmo jeito, só sem vizinhos.
 */
data class ListaDoLeitor(
    val ids: List<Int>,
    val indiceInicial: Int,
    val completa: Boolean,
)

/**
 * Descobre a lista de capítulos do leitor. **O texto nunca espera por ela**: o capítulo aberto aparece como antes,
 * sozinho; quando a lista chega, o leitor passa a ser um pager com os vizinhos. Se a leitura falhar, fica sem
 * vizinhos (e o gesto não faz nada).
 */
class ListaDoLeitorViewModel(
    private val capituloInicialId: Int,
    private val livros: RepositorioDeLivros,
) : ViewModel() {

    private val _estado = MutableStateFlow(ListaDoLeitor(listOf(capituloInicialId), 0, completa = false))
    val estado: StateFlow<ListaDoLeitor> = _estado.asStateFlow()

    private var pedido = false

    /**
     * Lê a lista de capítulos do livro do [capitulo] aberto (o repositório de livros responde do aparelho quando
     * pode). Uma vez só: a rotação do tablet não repete o pedido.
     */
    fun carregar(capitulo: CapituloDetalhe) {
        if (pedido) return
        pedido = true
        viewModelScope.launch {
            val resultado = livros.abrirLivro(capitulo.livro_id)
            if (resultado is ResultadoDaChamada.Sucesso) {
                val ids = idsDoLeitor(resultado.dado.capitulos, capituloInicialId)
                _estado.value = ListaDoLeitor(ids, ids.indexOf(capituloInicialId).coerceAtLeast(0), completa = true)
            }
        }
    }
}
