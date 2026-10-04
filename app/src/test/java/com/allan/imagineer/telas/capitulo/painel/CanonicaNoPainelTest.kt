package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.PromptDeFrame
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A imagem canônica no painel do capítulo (CAN6). */
@OptIn(ExperimentalCoroutinesApi::class)
class CanonicaNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun prompt(canonicaId: Int?) = PromptDeFrame(
        id = 1, frame_id = 70, texto = "p", total_de_imagens = 2,
    ) to listOf(ImagemDoPrompt(id = 10, prompt_id = 1, canonica = canonicaId == 10), ImagemDoPrompt(id = 11, prompt_id = 1, canonica = canonicaId == 11))

    @Test
    fun `CAN6 o rotulo da acao diz escolher, ou tirar a escolha se ja e a canonica`() {
        assertEquals("Definir como canônica", rotuloDaAcaoCanonica(false))
        assertEquals("Tirar a escolha de canônica", rotuloDaAcaoCanonica(true))
    }

    @Test
    fun `CAN6 escolher faz o PUT, relê os prompts do frame sem piscar e manda reler os artefatos`() = runTest {
        val (resumo, sem) = prompt(null)
        val (_, com) = prompt(11)
        val prompts = PromptsFalso().also {
            it.lista = ResultadoDaChamada.Sucesso(listOf(resumo))
            it.detalhes[1] = resumo.copy(imagens = sem)
        }
        val elementos = ElementosFalso()
        val vm = PainelDeIaViewModel(5, SugestoesFalso(), elementos, prompts = prompts)
        vm.carregarPrompts(70); advanceUntilIdle()
        val versaoAntes = vm.estado.value.versaoDosFrames

        prompts.detalhes[1] = resumo.copy(imagens = com)
        vm.definirImagemCanonica(70, 11); advanceUntilIdle()

        assertEquals(listOf(70 to 11), elementos.canonicasPedidas)
        val imagens = (vm.estado.value.prompts[70] as PromptsDoFrame.Pronto).lista.single().imagens
        assertEquals(listOf(11), imagens.filter { it.canonica }.map { it.id })
        assertEquals(versaoAntes + 1, vm.estado.value.versaoDosFrames)
        assertNull(vm.estado.value.mensagensDePrompt[70])
    }

    @Test
    fun `CAN3 tirar a escolha manda a imagem nula`() = runTest {
        val elementos = ElementosFalso()
        val vm = PainelDeIaViewModel(5, SugestoesFalso(), elementos, prompts = PromptsFalso())

        vm.definirImagemCanonica(70, null); advanceUntilIdle()

        assertEquals(listOf<Pair<Int, Int?>>(70 to null), elementos.canonicasPedidas)
    }

    @Test
    fun `CAN6 a recusa do servidor vira recado do frame e nada muda`() = runTest {
        val elementos = ElementosFalso().also { it.respostaADefinirCanonica = ResultadoDaChamada.Falha("Essa imagem não é de um prompt deste frame.") }
        val vm = PainelDeIaViewModel(5, SugestoesFalso(), elementos, prompts = PromptsFalso())
        val versaoAntes = vm.estado.value.versaoDosFrames

        vm.definirImagemCanonica(70, 99); advanceUntilIdle()

        assertEquals("Essa imagem não é de um prompt deste frame.", vm.estado.value.mensagensDePrompt[70]?.texto)
        assertTrue(vm.estado.value.mensagensDePrompt[70]!!.ehErro)
        assertEquals(versaoAntes, vm.estado.value.versaoDosFrames)
    }

    @Test
    fun `OC1 o rotulo diz ocultar, ou mostrar de novo se ja esta oculta`() {
        assertEquals("Ocultar do capítulo", rotuloDaOcultacao(false))
        assertEquals("Mostrar no capítulo", rotuloDaOcultacao(true))
    }

    @Test
    fun `OC1 ocultar faz o PUT, relê os prompts e manda reler os artefatos`() = runTest {
        val elementos = ElementosFalso()
        val vm = PainelDeIaViewModel(5, SugestoesFalso(), elementos, prompts = PromptsFalso())
        val versaoAntes = vm.estado.value.versaoDosFrames

        vm.definirImagemOculta(70, true); advanceUntilIdle()

        assertEquals(listOf(70 to true), elementos.ocultacoesPedidas)
        assertEquals(versaoAntes + 1, vm.estado.value.versaoDosFrames)
    }

    @Test
    fun `OC3 mostrar de novo manda oculta falso`() = runTest {
        val elementos = ElementosFalso()
        val vm = PainelDeIaViewModel(5, SugestoesFalso(), elementos, prompts = PromptsFalso())

        vm.definirImagemOculta(70, false); advanceUntilIdle()

        assertEquals(listOf(70 to false), elementos.ocultacoesPedidas)
    }

    @Test
    fun `OC1 a recusa do servidor vira recado do frame e nada muda`() = runTest {
        val elementos = ElementosFalso().also { it.respostaADefinirOculta = ResultadoDaChamada.Falha("Frame não encontrado.") }
        val vm = PainelDeIaViewModel(5, SugestoesFalso(), elementos, prompts = PromptsFalso())
        val versaoAntes = vm.estado.value.versaoDosFrames

        vm.definirImagemOculta(70, true); advanceUntilIdle()

        assertEquals("Frame não encontrado.", vm.estado.value.mensagensDePrompt[70]?.texto)
        assertEquals(versaoAntes, vm.estado.value.versaoDosFrames)
    }
}
