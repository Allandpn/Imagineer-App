package com.allan.imagineer.telas.livro

import com.allan.imagineer.dados.ArquivoEscolhido
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
import org.junit.Before
import org.junit.Test

private val LIVRO = LivroDetalhe(
    id = 1, titulo = "A Guerra", nome_arquivo = "a.epub", data_importacao = "2026-10-04",
    total_de_capitulos = 1, capitulos_ignorados = 0, capitulos = listOf(CapituloResumo(1, 1, "Um", false, 100)),
)

/** "Definir capa…" no menu do livro (CP5). */
@OptIn(ExperimentalCoroutinesApi::class)
class DefinirCapaTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private val arquivo = ArquivoEscolhido(uri = "content://x/capa.jpg", nome = "capa.jpg", tamanho = 10L, tipo = "image/jpeg")

    private fun vm(resposta: ResultadoDaChamada<LivroDetalhe>, enviados: MutableList<Pair<Int, String>>): LivroViewModel {
        val repositorio = object : RepositorioDeLivros by LivrosFalso(ResultadoDaChamada.Sucesso(LIVRO)) {
            override suspend fun definirCapa(livroId: Int, arquivo: ArquivoEscolhido): ResultadoDaChamada<LivroDetalhe> {
                enviados += livroId to arquivo.nome
                return resposta
            }
        }
        return LivroViewModel(1, repositorio, CapitulosFalso(), PerfisFalso())
    }

    @Test
    fun `definir a capa manda o arquivo ao servidor e avisa`() = runTest {
        val enviados = mutableListOf<Pair<Int, String>>()
        val vm = vm(ResultadoDaChamada.Sucesso(LIVRO), enviados)

        vm.definirCapa(arquivo); advanceUntilIdle()

        assertEquals(listOf(1 to "capa.jpg"), enviados)
        assertEquals("Capa definida.", vm.avisos.first().texto)
    }

    @Test
    fun `a recusa do servidor vira o aviso`() = runTest {
        val vm = vm(ResultadoDaChamada.Falha("Não achei a capa neste EPUB."), mutableListOf())

        vm.definirCapa(arquivo); advanceUntilIdle()

        assertEquals("Não achei a capa neste EPUB.", vm.avisos.first().texto)
    }

    @Test
    fun `arquivo que nao abriu avisa e nao chama o servidor`() = runTest {
        val enviados = mutableListOf<Pair<Int, String>>()
        val vm = vm(ResultadoDaChamada.Sucesso(LIVRO), enviados)

        vm.definirCapa(null); advanceUntilIdle()

        assertEquals("Não consegui abrir o arquivo escolhido.", vm.avisos.first().texto)
        assertEquals(emptyList<Pair<Int, String>>(), enviados)
    }
}
