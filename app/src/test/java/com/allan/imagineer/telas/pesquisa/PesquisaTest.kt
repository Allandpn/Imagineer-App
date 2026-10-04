package com.allan.imagineer.telas.pesquisa

import com.allan.imagineer.rede.OcorrenciaNoTexto
import com.allan.imagineer.rede.RepositorioDeBusca
import com.allan.imagineer.rede.ResultadoDaBusca
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun ocorrencia(capitulo: Int = 1) = OcorrenciaNoTexto(
    livro_id = 4, livro_titulo = "A Guerra", capitulo_id = capitulo, capitulo_ordem = 1,
    posicao_no_texto = 10, inicio_do_paragrafo = 0, trecho = "o lobo uivou", inicio_no_trecho = 2, fim_no_trecho = 6,
)

private class BuscaFalsa : RepositorioDeBusca {
    /** Os pedidos feitos: (termo, livro, capítulo). */
    val pedidos = mutableListOf<Triple<String, Int?, Int?>>()
    var resposta: ResultadoDaChamada<ResultadoDaBusca> =
        ResultadoDaChamada.Sucesso(ResultadoDaBusca("lobo", 1, listOf(ocorrencia())))

    override suspend fun buscar(termo: String, livroId: Int?, capituloId: Int?): ResultadoDaChamada<ResultadoDaBusca> {
        pedidos += Triple(termo, livroId, capituloId)
        return resposta
    }
}

/** A pesquisa no texto (LV5). */
@OptIn(ExperimentalCoroutinesApi::class)
class PesquisaTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    @Test
    fun `LV5 de dentro de um capitulo ha tres abas, da tela do livro so duas`() {
        assertEquals(listOf(AbaDaPesquisa.CAPITULO, AbaDaPesquisa.LIVRO, AbaDaPesquisa.BIBLIOTECA), abasDaPesquisa(true))
        assertEquals(listOf(AbaDaPesquisa.LIVRO, AbaDaPesquisa.BIBLIOTECA), abasDaPesquisa(false))
    }

    @Test
    fun `LV5 procura em cada aba com o escopo dela`() = runTest {
        val repo = BuscaFalsa()
        val vm = PesquisaViewModel(livroId = 4, capituloId = 9, repositorio = repo)

        vm.alterarTermo("lobo"); advanceUntilIdle()

        assertEquals(
            setOf(Triple("lobo", null, 9), Triple("lobo", 4, null), Triple("lobo", null, null)),
            repo.pedidos.toSet(),
        )
        assertTrue(vm.estado.value.resultados.values.all { it is ResultadoDaAba.Pronto })
    }

    @Test
    fun `LV5 espera a digitacao parar e so procura a ultima`() = runTest {
        val repo = BuscaFalsa()
        val vm = PesquisaViewModel(4, null, repo, atrasoMs = 400)

        vm.alterarTermo("lo"); advanceTimeBy(200)
        vm.alterarTermo("lob"); advanceTimeBy(200)
        vm.alterarTermo("lobo"); advanceUntilIdle()

        assertEquals(setOf("lobo"), repo.pedidos.map { it.first }.toSet())
    }

    @Test
    fun `LV5 termo curto demais nao procura e limpa os resultados`() = runTest {
        val repo = BuscaFalsa()
        val vm = PesquisaViewModel(4, null, repo)
        vm.alterarTermo("lobo"); advanceUntilIdle()

        vm.alterarTermo("l"); advanceUntilIdle()

        assertTrue(vm.estado.value.resultados.isEmpty())
        assertEquals(2, repo.pedidos.size) // só as duas abas da primeira busca
    }

    @Test
    fun `LV5 a falha de uma busca vira o erro da aba`() = runTest {
        val repo = BuscaFalsa().also { it.resposta = ResultadoDaChamada.Falha("Sem conexão.") }
        val vm = PesquisaViewModel(4, null, repo)

        vm.alterarTermo("lobo"); advanceUntilIdle()

        assertEquals(ResultadoDaAba.Erro("Sem conexão."), vm.estado.value.resultados[AbaDaPesquisa.LIVRO])
    }

    @Test
    fun `LV5 o rotulo da aba mostra quantas ocorrencias ha`() {
        val pronto = ResultadoDaAba.Pronto(ResultadoDaBusca("lobo", total = 12))
        assertEquals("Livro (12)", rotuloDaAba(AbaDaPesquisa.LIVRO, pronto))
        assertEquals("Livro", rotuloDaAba(AbaDaPesquisa.LIVRO, ResultadoDaAba.Buscando))
        assertEquals("Capítulo", rotuloDaAba(AbaDaPesquisa.CAPITULO, null))
    }

    @Test
    fun `LV5 o trecho destaca o achado e aguenta limites fora do texto`() {
        val destacado = trechoDestacado("o lobo uivou", 2, 6)
        assertEquals("o lobo uivou", destacado.text)
        assertEquals(1, destacado.spanStyles.size)
        assertEquals(2, destacado.spanStyles[0].start)
        assertEquals(6, destacado.spanStyles[0].end)

        assertEquals("abc", trechoDestacado("abc", -5, 99).text) // não estoura
    }
}
