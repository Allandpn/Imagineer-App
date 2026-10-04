package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.RepositorioDePrompts
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.Traducao
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

private class PromptsQueTraduzem(
    var paraPortugues: ResultadoDaChamada<Traducao> = ResultadoDaChamada.Sucesso(Traducao("um cavaleiro", modelo = "barato", custo = "0.0004")),
    var paraIngles: ResultadoDaChamada<Traducao> = ResultadoDaChamada.Sucesso(Traducao("a knight", modelo = "barato", custo = "0.0003")),
    val base: PromptsFalso = PromptsFalso(),
) : RepositorioDePrompts by base {
    val pedidosEmPortugues = mutableListOf<Int>()
    val pedidosEmIngles = mutableListOf<Pair<Int, String>>()

    override suspend fun traduzirParaPortugues(promptId: Int): ResultadoDaChamada<Traducao> {
        pedidosEmPortugues += promptId
        return paraPortugues
    }

    override suspend fun traduzirParaIngles(promptId: Int, texto: String): ResultadoDaChamada<Traducao> {
        pedidosEmIngles += promptId to texto
        return paraIngles
    }
}

/** A camada em português do diálogo de edição (PT2, PT3, PT4, PT6). */
@OptIn(ExperimentalCoroutinesApi::class)
class TraducaoDoPromptTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private fun vm(prompts: RepositorioDePrompts) = PainelDeIaViewModel(5, SugestoesFalso(), ElementosFalso(), prompts)

    @Test
    fun `ver em portugues entrega a traducao de quem a pediu`() = runTest {
        val prompts = PromptsQueTraduzem()
        var recebida: ResultadoDaChamada<Traducao>? = null

        vm(prompts).verEmPortugues(11) { recebida = it }; advanceUntilIdle()

        assertEquals(listOf(11), prompts.pedidosEmPortugues)
        assertEquals(ResultadoDaChamada.Sucesso(Traducao("um cavaleiro", "barato", "0.0004")), recebida)
    }

    @Test
    fun `traduzir para o ingles manda o portugues escrito e entrega a previa`() = runTest {
        val prompts = PromptsQueTraduzem()
        var recebida: ResultadoDaChamada<Traducao>? = null

        vm(prompts).traduzirParaIngles(11, "um cavaleiro de capuz") { recebida = it }; advanceUntilIdle()

        assertEquals(listOf(11 to "um cavaleiro de capuz"), prompts.pedidosEmIngles)
        assertEquals("a knight", (recebida as ResultadoDaChamada.Sucesso).dado.texto)
    }

    @Test
    fun `a falha da traducao chega a quem pediu com o motivo`() = runTest {
        val prompts = PromptsQueTraduzem(paraPortugues = ResultadoDaChamada.Falha("Sem modelo."))
        var recebida: ResultadoDaChamada<Traducao>? = null

        vm(prompts).verEmPortugues(11) { recebida = it }; advanceUntilIdle()

        assertEquals(ResultadoDaChamada.Falha("Sem modelo."), recebida)
    }

    @Test
    fun `gerar a imagem leva o portugues escrito junto do texto editado`() = runTest {
        val prompts = PromptsQueTraduzem()
        val vm = vm(prompts)

        vm.gerarImagem(70, 11, textoEditado = "a knight", modelo = null, textoPt = "um cavaleiro"); advanceUntilIdle()

        assertEquals(listOf<String?>("um cavaleiro"), prompts.base.portuguesesPedidos)
    }

    @Test
    fun `gerar sem o portugues nao manda portugues`() = runTest {
        val prompts = PromptsQueTraduzem()
        val vm = vm(prompts)

        vm.gerarImagem(70, 11); advanceUntilIdle()

        assertEquals(listOf<String?>(null), prompts.base.portuguesesPedidos)
    }

    @Test
    fun `o texto do custo diz se ja estava guardada, quanto custou ou que nao foi informado`() {
        assertEquals("Tradução já guardada (sem custo).", textoDoCustoDaTraducao(Traducao("x", reaproveitada = true)))
        assertEquals("Tradução: US\$ 0,0004", textoDoCustoDaTraducao(Traducao("x", custo = "0.0004")))
        assertEquals("Tradução feita (custo não informado).", textoDoCustoDaTraducao(Traducao("x")))
    }
}
