package com.allan.imagineer.telas.biblioteca

import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.rede.AlvoDeFavorito
import com.allan.imagineer.rede.Favorito
import com.allan.imagineer.rede.LivroAjuste
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.RepositorioDeFavoritos
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.RespostaImportacao
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.TipoDeFavorito
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Os favoritos que um teste da biblioteca combina; registra o que o ViewModel pediu. */
internal class FavoritosDaBibliotecaFalsos(var falhar: String? = null) : RepositorioDeFavoritos {
    val favoritados = mutableListOf<Pair<Int, AlvoDeFavorito>>()
    val desfavoritados = mutableListOf<Int>()

    override suspend fun listar(livroId: Int, tipo: TipoDeFavorito?) = ResultadoDaChamada.Sucesso(emptyList<Favorito>())

    override suspend fun favoritar(livroId: Int, alvo: AlvoDeFavorito): ResultadoDaChamada<Favorito> {
        falhar?.let { return ResultadoDaChamada.Falha(it, 422) }
        favoritados += livroId to alvo
        return ResultadoDaChamada.Sucesso(Favorito(id = 500, livro_id = livroId, tipo = alvo.tipo.name, rotulo = "Livro"))
    }

    override suspend fun desfavoritar(favoritoId: Int): ResultadoDaChamada<Unit> {
        falhar?.let { return ResultadoDaChamada.Falha(it, 404) }
        desfavoritados += favoritoId
        return ResultadoDaChamada.Sucesso(Unit)
    }
}

/**
 * Os testes mais antigos da biblioteca constroem o ViewModel só com os livros; este atalho dá a eles um repositório de favoritos que
 * não faz nada, sem mexer em cada um.
 */
internal fun BibliotecaViewModel(repositorio: RepositorioDeLivros): BibliotecaViewModel =
    BibliotecaViewModel(repositorio, FavoritosDaBibliotecaFalsos())

private fun livroDe(id: Int, favorito: Int? = null) = LivroResumo(
    id = id, titulo = "Livro $id", nome_arquivo = "l$id.epub", data_importacao = "2026-10-05T00:00:00",
    total_de_capitulos = 3, capitulos_ignorados = 0, favorito_id = favorito,
)

/** A divisão da lista em seções e o item de favoritar (item 7.2b, BL1, BL2, BL4). */
class SecoesDaBibliotecaTest {

    @Test
    fun `BL1 os favoritos vao para a seção de cima e os outros para baixo, cada um em um lugar so`() {
        val secoes = dividirEmSecoes(listOf(livroDe(1), livroDe(2, favorito = 9), livroDe(3), livroDe(4, favorito = 10)))

        assertEquals(listOf(2, 4), secoes.favoritos.map { it.id })
        assertEquals(listOf(1, 3), secoes.outros.map { it.id })
    }

    @Test
    fun `BL1 a ordem do servidor e mantida dentro de cada secao`() {
        val secoes = dividirEmSecoes(listOf(livroDe(5, 1), livroDe(2), livroDe(9, 2), livroDe(1)))

        assertEquals(listOf(5, 9), secoes.favoritos.map { it.id })
        assertEquals(listOf(2, 1), secoes.outros.map { it.id })
    }

    @Test
    fun `BL2 sem favoritos nao ha cabecalho nenhum`() {
        val secoes = dividirEmSecoes(listOf(livroDe(1), livroDe(2)))

        assertFalse(secoes.cabecalhoDosFavoritos)
        assertFalse(secoes.cabecalhoDosOutros)
        assertEquals(listOf(1, 2), secoes.outros.map { it.id })
    }

    @Test
    fun `BL2 com favoritos e outros, os dois cabecalhos aparecem`() {
        val secoes = dividirEmSecoes(listOf(livroDe(1, 7), livroDe(2)))

        assertTrue(secoes.cabecalhoDosFavoritos)
        assertTrue(secoes.cabecalhoDosOutros)
    }

    @Test
    fun `BL2 se todos sao favoritos so aparece a secao dos favoritos`() {
        val secoes = dividirEmSecoes(listOf(livroDe(1, 7), livroDe(2, 8)))

        assertTrue(secoes.cabecalhoDosFavoritos)
        assertFalse("não há 'Outros livros' vazio", secoes.cabecalhoDosOutros)
    }

    @Test
    fun `BL1 os titulos das secoes`() {
        assertEquals("Favoritos", TITULO_DOS_FAVORITOS) // PD11: sem a quantidade de livros
        assertEquals("Outros livros", TITULO_DOS_OUTROS_LIVROS)
    }

    @Test
    fun `BL4 o item do menu diz o que vai acontecer`() {
        assertEquals("Adicionar aos favoritos", rotuloDoFavoritar(livroDe(1)))
        assertEquals("Tirar dos favoritos", rotuloDoFavoritar(livroDe(1, favorito = 9)))
    }
}

private class LivrosFalsos(var livros: List<LivroResumo>) : RepositorioDeLivros {
    var listagens = 0

    override suspend fun listarLivros(): ResultadoDaChamada<List<LivroResumo>> {
        listagens++
        return ResultadoDaChamada.Sucesso(livros)
    }

    override suspend fun abrirLivro(livroId: Int): ResultadoDaChamada<LivroDetalhe> = error("não usado")
    override suspend fun importarLivro(arquivo: ArquivoEscolhido, aoProgredir: (enviados: Long, total: Long?) -> Unit): ResultadoDaChamada<RespostaImportacao> = error("não usado")
    override suspend fun ajustarLivro(livroId: Int, ajuste: LivroAjuste): ResultadoDaChamada<LivroDetalhe> = error("não usado")
    override suspend fun removerLivro(livroId: Int): ResultadoDaChamada<Unit> = error("não usado")
}

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritarNaBibliotecaTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    @Test
    fun `BL4 favoritar um livro manda o alvo LIVRO e relê a lista, sem aviso`() = runTest {
        val livros = LivrosFalsos(listOf(livroDe(1), livroDe(2)))
        val favoritos = FavoritosDaBibliotecaFalsos()
        val vm = BibliotecaViewModel(livros, favoritos)
        vm.carregar(); advanceUntilIdle()
        val antes = livros.listagens
        var aviso: String? = "não chamado"

        vm.alternarFavorito(livroDe(2)) { aviso = it }
        advanceUntilIdle()

        assertEquals(listOf(2 to AlvoDeFavorito.Livro), favoritos.favoritados)
        assertTrue(favoritos.desfavoritados.isEmpty())
        assertNull("sem falha o aviso é nulo", aviso)
        assertEquals("a lista foi relida, para o livro mudar de seção", antes + 1, livros.listagens)
    }

    @Test
    fun `BL4 tirar dos favoritos apaga o favorito do livro`() = runTest {
        val livros = LivrosFalsos(listOf(livroDe(1, favorito = 40)))
        val favoritos = FavoritosDaBibliotecaFalsos()
        val vm = BibliotecaViewModel(livros, favoritos)
        vm.carregar(); advanceUntilIdle()

        vm.alternarFavorito(livroDe(1, favorito = 40)) {}
        advanceUntilIdle()

        assertEquals(listOf(40), favoritos.desfavoritados)
        assertTrue(favoritos.favoritados.isEmpty())
    }

    @Test
    fun `BL4 se o servidor recusar, o aviso leva a mensagem e a lista nao e relida`() = runTest {
        val livros = LivrosFalsos(listOf(livroDe(1)))
        val vm = BibliotecaViewModel(livros, FavoritosDaBibliotecaFalsos(falhar = "Sem conexão com o servidor."))
        vm.carregar(); advanceUntilIdle()
        val antes = livros.listagens
        var aviso: String? = null

        vm.alternarFavorito(livroDe(1)) { aviso = it }
        advanceUntilIdle()

        assertEquals("Sem conexão com o servidor.", aviso)
        assertEquals(antes, livros.listagens)
    }
}
