package com.allan.imagineer.telas.biblioteca

import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.rede.LivroAjuste
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.RespostaImportacao
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class RepositorioQueDefineCapa(var resposta: ResultadoDaChamada<LivroDetalhe>) : RepositorioDeLivros {
    val capas = mutableListOf<Pair<Int, String>>()
    var listagens = 0

    override suspend fun listarLivros(): ResultadoDaChamada<List<LivroResumo>> {
        listagens++
        return ResultadoDaChamada.Sucesso(emptyList())
    }

    override suspend fun definirCapa(livroId: Int, arquivo: ArquivoEscolhido): ResultadoDaChamada<LivroDetalhe> {
        capas += livroId to arquivo.nome
        return resposta
    }

    override suspend fun abrirLivro(livroId: Int): ResultadoDaChamada<LivroDetalhe> = error("não usado")
    override suspend fun importarLivro(arquivo: ArquivoEscolhido, aoProgredir: (Long, Long?) -> Unit): ResultadoDaChamada<RespostaImportacao> = error("não usado")
    override suspend fun ajustarLivro(livroId: Int, ajuste: LivroAjuste): ResultadoDaChamada<LivroDetalhe> = error("não usado")
    override suspend fun removerLivro(livroId: Int): ResultadoDaChamada<Unit> = error("não usado")
}

private val LIVRO = LivroDetalhe(id = 3, titulo = "A", nome_arquivo = "a.epub", data_importacao = "x", total_de_capitulos = 0, capitulos_ignorados = 0)
private val ARQUIVO = ArquivoEscolhido(uri = "content://x/c.jpg", nome = "c.jpg", tamanho = 5L, tipo = "image/jpeg")

/** "Definir capa…" no menu do livro, direto na biblioteca. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefinirCapaNaBibliotecaTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    @Test
    fun `define a capa do livro escolhido, avisa e recarrega a lista`() = runTest {
        val repositorio = RepositorioQueDefineCapa(ResultadoDaChamada.Sucesso(LIVRO))
        val vm = BibliotecaViewModel(repositorio)
        val avisos = mutableListOf<String>()

        vm.definirCapa(3, ARQUIVO) { avisos += it }; advanceUntilIdle()

        assertEquals(listOf(3 to "c.jpg"), repositorio.capas)
        assertEquals(listOf("Capa definida."), avisos)
        assertEquals(1, repositorio.listagens)
    }

    @Test
    fun `a recusa do servidor vira o aviso e nao recarrega`() = runTest {
        val repositorio = RepositorioQueDefineCapa(ResultadoDaChamada.Falha("Não achei a capa neste EPUB."))
        val vm = BibliotecaViewModel(repositorio)
        val avisos = mutableListOf<String>()

        vm.definirCapa(3, ARQUIVO) { avisos += it }; advanceUntilIdle()

        assertEquals(listOf("Não achei a capa neste EPUB."), avisos)
        assertEquals(0, repositorio.listagens)
    }

    @Test
    fun `arquivo que nao abriu avisa sem chamar o servidor`() = runTest {
        val repositorio = RepositorioQueDefineCapa(ResultadoDaChamada.Sucesso(LIVRO))
        val avisos = mutableListOf<String>()

        BibliotecaViewModel(repositorio).definirCapa(3, null) { avisos += it }; advanceUntilIdle()

        assertEquals(listOf("Não consegui abrir o arquivo escolhido."), avisos)
        assertEquals(emptyList<Pair<Int, String>>(), repositorio.capas)
    }
}
