package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ReferenciaVisual
import com.allan.imagineer.rede.RepositorioDePrompts
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun prompt(id: Int, texto: String = "prompt $id", referencias: Int = 0) =
    PromptDeFrame(id = id, frame_id = 70, texto = texto, referencias_visuais = List(referencias) { ReferenciaVisual(it) })

/** Prompts falsos: **registram cada chamada** e devolvem o que o teste combinar. Quem gasta IA é só o `gerar` (G9). */
internal class PromptsFalso : RepositorioDePrompts {
    var listagens = 0
    val geracoes = mutableListOf<Pair<Int, String?>>() // frame e comentário de cada geração
    var lista: ResultadoDaChamada<List<PromptDeFrame>> = ResultadoDaChamada.Sucesso(emptyList())
    var geracao: ResultadoDaChamada<PromptDeFrame> = ResultadoDaChamada.Sucesso(prompt(99))
    var travaDaGeracao: CompletableDeferred<Unit>? = null

    override suspend fun listar(frameId: Int): ResultadoDaChamada<List<PromptDeFrame>> {
        listagens++
        return lista
    }

    override suspend fun gerar(frameId: Int, comentario: String?): ResultadoDaChamada<PromptDeFrame> {
        geracoes += frameId to comentario
        travaDaGeracao?.await()
        return geracao
    }
}

/** As regras puras de gerar e copiar o prompt. */
class RegrasDoPromptTest {

    @Test
    fun `G3 o botao diz Gerar na primeira vez e Gerar outro depois`() {
        assertEquals("Gerar prompt", rotuloDoBotaoDePrompt(jaTemPrompts = false))
        assertEquals("Gerar outro prompt", rotuloDoBotaoDePrompt(jaTemPrompts = true))
    }

    @Test
    fun `G3 o dialogo avisa que gasta IA e explica o campo de ajuste`() {
        assertTrue("gasta IA" in TEXTO_DO_DIALOGO_DE_PROMPT)
        assertTrue("em branco" in TEXTO_DO_DIALOGO_DE_PROMPT)
    }

    @Test
    fun `G5 o aviso das imagens de referencia concorda e some quando nao ha`() {
        assertNull(descreverReferenciasVisuais(0))
        assertTrue(descreverReferenciasVisuais(1)!!.contains("a imagem de referência"))
        assertTrue(descreverReferenciasVisuais(3)!!.contains("as 3 imagens de referência"))
    }
}

/** Ler, gerar e copiar no ViewModel do painel. */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptsNoPainelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun vm(prompts: PromptsFalso) = PainelDeIaViewModel(5, SugestoesFalso(), ElementosFalso(), prompts = prompts)

    private fun lista(vm: PainelDeIaViewModel, frameId: Int = 70) =
        (vm.estado.value.prompts[frameId] as? PromptsDoFrame.Pronto)?.lista

    // ---- G2: ler não custa ----

    @Test
    fun `G2 abrir lista os prompts do mais novo para o mais antigo, sem gastar IA`() = runTest {
        val prompts = PromptsFalso().also { it.lista = ResultadoDaChamada.Sucesso(listOf(prompt(1), prompt(2), prompt(3))) }
        val vm = vm(prompts)

        vm.carregarPrompts(70)
        advanceUntilIdle()

        assertEquals(listOf(3, 2, 1), lista(vm)?.map { it.id }) // o servidor entrega do mais antigo ao mais novo
        assertTrue("só leu: nada foi gerado", prompts.geracoes.isEmpty())
    }

    @Test
    fun `G2 le uma vez so - abrir de novo nao pede de novo`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.carregarPrompts(70)
        advanceUntilIdle()
        vm.carregarPrompts(70)
        vm.carregarPrompts(70)
        advanceUntilIdle()

        assertEquals(1, prompts.listagens)
    }

    @Test
    fun `G2 erro de leitura mostra o motivo e so se refaz pelo tentar de novo`() = runTest {
        val prompts = PromptsFalso().also { it.lista = ResultadoDaChamada.Falha("sem conexão") }
        val vm = vm(prompts)
        vm.carregarPrompts(70)
        advanceUntilIdle()
        assertEquals(PromptsDoFrame.Erro("sem conexão"), vm.estado.value.prompts[70])

        vm.carregarPrompts(70) // abrir de novo não refaz o erro sozinho
        advanceUntilIdle()
        assertEquals(1, prompts.listagens)

        prompts.lista = ResultadoDaChamada.Sucesso(listOf(prompt(1)))
        vm.recarregarPrompts(70)
        advanceUntilIdle()
        assertEquals(listOf(1), lista(vm)?.map { it.id })
    }

    // ---- G3: gerar pede confirmação ----

    @Test
    fun `G3 pedir abre o dialogo e cancelar nao gera nada`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.pedirGerarPrompt(70)
        assertEquals(70, vm.estado.value.confirmandoPrompt)

        vm.cancelarGerarPrompt()
        advanceUntilIdle()

        assertNull(vm.estado.value.confirmandoPrompt)
        assertTrue(prompts.geracoes.isEmpty())
    }

    @Test
    fun `G3 gerar sem ter pedido nao faz nada - nao ha atalho para gastar IA`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.gerarPrompt(70, "ajuste")
        advanceUntilIdle()

        assertTrue(prompts.geracoes.isEmpty())
    }

    // ---- G4 e G5: gerar ----

    @Test
    fun `G4 gera com o ajuste aparado, e em branco vai sem comentario`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.pedirGerarPrompt(70)
        vm.gerarPrompt(70, "  ela está de costas  ")
        advanceUntilIdle()
        vm.pedirGerarPrompt(70)
        vm.gerarPrompt(70, "   ")
        advanceUntilIdle()

        assertEquals(listOf<Pair<Int, String?>>(70 to "ela está de costas", 70 to null), prompts.geracoes)
    }

    @Test
    fun `G5 o prompt novo vai para o topo da lista e o dialogo fecha`() = runTest {
        val prompts = PromptsFalso().also {
            it.lista = ResultadoDaChamada.Sucesso(listOf(prompt(1)))
            it.geracao = ResultadoDaChamada.Sucesso(prompt(2, referencias = 2))
        }
        val vm = vm(prompts)
        vm.carregarPrompts(70)
        advanceUntilIdle()

        vm.pedirGerarPrompt(70)
        vm.gerarPrompt(70, "")
        advanceUntilIdle()

        assertEquals(listOf(2, 1), lista(vm)?.map { it.id })
        assertEquals(2, lista(vm)?.first()?.referencias_visuais?.size)
        assertNull(vm.estado.value.confirmandoPrompt)
        assertTrue(vm.estado.value.gerandoPrompt.isEmpty())
    }

    @Test
    fun `G5 gerar o primeiro prompt de um frame que nunca foi listado tambem funciona`() = runTest {
        val vm = vm(PromptsFalso())

        vm.pedirGerarPrompt(70)
        vm.gerarPrompt(70, "")
        advanceUntilIdle()

        assertEquals(listOf(99), lista(vm)?.map { it.id })
    }

    @Test
    fun `G4 uma geracao por frame de cada vez`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also { it.travaDaGeracao = trava }
        val vm = vm(prompts)
        vm.pedirGerarPrompt(70)
        vm.gerarPrompt(70, "")
        runCurrent()
        assertTrue(70 in vm.estado.value.gerandoPrompt)

        vm.pedirGerarPrompt(70) // com uma rodando, nem abre o diálogo
        vm.gerarPrompt(70, "outra")
        trava.complete(Unit)
        advanceUntilIdle()

        assertNull(vm.estado.value.confirmandoPrompt)
        assertEquals(1, prompts.geracoes.size)
    }

    // ---- G7: erros ----

    @Test
    fun `G7 erro do servidor aparece no modal, a lista de antes fica e da para tentar de novo`() = runTest {
        val mensagem = "Nenhum perfil de renderização foi informado, e o livro não tem um perfil padrão definido."
        val prompts = PromptsFalso().also {
            it.lista = ResultadoDaChamada.Sucesso(listOf(prompt(1)))
            it.geracao = ResultadoDaChamada.Falha(mensagem, codigoHttp = 422)
        }
        val vm = vm(prompts)
        vm.carregarPrompts(70)
        advanceUntilIdle()

        vm.pedirGerarPrompt(70)
        vm.gerarPrompt(70, "")
        advanceUntilIdle()

        assertEquals(MensagemDoElemento(mensagem, ehErro = true), vm.estado.value.mensagensDePrompt[70])
        assertEquals(listOf(1), lista(vm)?.map { it.id }) // nada se perdeu
        assertFalse(70 in vm.estado.value.gerandoPrompt) // e dá para tentar de novo

        prompts.geracao = ResultadoDaChamada.Sucesso(prompt(2))
        vm.pedirGerarPrompt(70) // pedir de novo limpa o erro antigo
        assertNull(vm.estado.value.mensagensDePrompt[70])
        vm.gerarPrompt(70, "")
        advanceUntilIdle()
        assertEquals(listOf(2, 1), lista(vm)?.map { it.id })
    }

    @Test
    fun `G9 so gerar gasta IA - ler, pedir e cancelar nao`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.carregarPrompts(70)
        vm.pedirGerarPrompt(70)
        vm.cancelarGerarPrompt()
        advanceUntilIdle()

        assertTrue(prompts.geracoes.isEmpty())
    }
}
