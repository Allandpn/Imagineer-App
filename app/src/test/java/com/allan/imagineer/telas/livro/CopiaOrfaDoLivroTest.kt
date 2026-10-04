package com.allan.imagineer.telas.livro

import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val LIVRO_ORFAO = LivroDetalhe(
    id = 1, titulo = "A Guerra", nome_arquivo = "a.epub", data_importacao = "2026-10-04",
    total_de_capitulos = 1, capitulos_ignorados = 0, capitulos = listOf(CapituloResumo(1, 1, "Um", false, 100)),
)

/** O livro que o servidor não tem mais, mas o aparelho ainda guarda (PL11, regra A9). */
@OptIn(ExperimentalCoroutinesApi::class)
class CopiaOrfaDoLivroTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private class Repositorio(val temCopia: Boolean, val codigo: Int?) : RepositorioDeLivros by LivrosFalso(ResultadoDaChamada.Sucesso(LIVRO_ORFAO)) {
        val copiasApagadas = mutableListOf<Int>()
        override suspend fun abrirLivro(livroId: Int): ResultadoDaChamada<LivroDetalhe> = ResultadoDaChamada.Falha("Não existe livro com id $livroId.", codigo)
        override suspend fun temCopiaLocal(livroId: Int) = temCopia
        override suspend fun apagarCopiaLocal(livroId: Int) { copiasApagadas += livroId }
    }

    private fun vm(repositorio: Repositorio) = LivroViewModel(1, repositorio, CapitulosFalso(), PerfisFalso())

    @Test
    fun `404 com copia no aparelho oferece apagar a copia`() = runTest {
        val vm = vm(Repositorio(temCopia = true, codigo = 404))

        vm.carregar(); advanceUntilIdle()

        val erro = vm.estado.value as EstadoDoLivro.Erro
        assertTrue(erro.ofereceApagarCopia)
    }

    @Test
    fun `404 sem copia, ou outro erro, nao oferece nada`() = runTest {
        val semCopia = vm(Repositorio(temCopia = false, codigo = 404))
        semCopia.carregar(); advanceUntilIdle()
        assertFalse((semCopia.estado.value as EstadoDoLivro.Erro).ofereceApagarCopia)

        val erroDeServidor = vm(Repositorio(temCopia = true, codigo = 500))
        erroDeServidor.carregar(); advanceUntilIdle()
        assertFalse((erroDeServidor.estado.value as EstadoDoLivro.Erro).ofereceApagarCopia)
    }

    @Test
    fun `apagar a copia pede ao repositorio e sai da tela, so quando a pessoa manda`() = runTest {
        val repositorio = Repositorio(temCopia = true, codigo = 404)
        val vm = vm(repositorio)
        vm.carregar(); advanceUntilIdle()
        assertTrue(repositorio.copiasApagadas.isEmpty())  // nunca apaga sozinho

        vm.apagarCopiaLocal(); advanceUntilIdle()

        assertEquals(listOf(1), repositorio.copiasApagadas)
        vm.livroRemovido.first()  // a tela é avisada para voltar
    }
}
