package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.rede.DossieDaCena
import com.allan.imagineer.rede.PresenteDaCena
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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

/** AP8 (item 7.5d): depois de editar e confirmar a lista, os prompts que a cena já tinha não a levam em conta, e o app avisa. */
class RegraDaListaMudouTest {

    @Test
    fun `AP8 o aviso aparece enquanto o numero de prompts e o mesmo de quando a lista foi confirmada`() {
        assertTrue(listaMudouSemPromptNovo(avisadoCom = 2, promptsAgora = 2))
    }

    @Test
    fun `AP8 nasceu um prompt novo - o aviso some`() {
        assertFalse(listaMudouSemPromptNovo(avisadoCom = 2, promptsAgora = 3))
    }

    @Test
    fun `AP8 sem prompt ou sem aviso anotado nao ha o que avisar`() {
        assertFalse(listaMudouSemPromptNovo(avisadoCom = null, promptsAgora = 2))
        assertFalse(listaMudouSemPromptNovo(avisadoCom = 0, promptsAgora = 0))
        assertFalse(listaMudouSemPromptNovo(avisadoCom = 2, promptsAgora = null)) // a lista de prompts ainda não foi lida
    }

    @Test
    fun `AP8 o texto diz o que mudou e oferece o prompt novo`() {
        assertEquals("A lista mudou. Os prompts antigos não a levam em conta. Gerar um prompt novo?", AVISO_LISTA_MUDOU)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListaMudouNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val auri = PresenteDaCena(nome = "Auri", tipo = "PESSOA", elemento = "Auri", caracteristicas = "manto azul")
    private val dossie = DossieDaCena(presentes = listOf(auri), onde = "a cozinha", luz_e_clima = "vela", acao = "Auri serve o caldo")

    private fun montar(promptsDaCena: Int): Pair<PainelDeIaViewModel, PromptsFalso> {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(SugestoesDeCapitulo(gerado_em = "2026-10-09T10:00:00")))
        val prompts = PromptsFalso().also {
            it.dossieGuardado = ResultadoDaChamada.Sucesso(dossie)
            it.lista = ResultadoDaChamada.Sucesso(List(promptsDaCena) { n -> PromptDeFrame(id = 100 + n, frame_id = 70, texto = "p$n") })
        }
        val vm = PainelDeIaViewModel(
            5, repositorio, ElementosFalso(), prompts,
            ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
        )
        return vm to prompts
    }

    /** Abre a cena como o modal faz: lê os prompts e o dossiê guardados. */
    private fun PainelDeIaViewModel.abrir() {
        carregarPrompts(70)
        carregarDossie(70)
    }

    private fun promptsDaCena(vm: PainelDeIaViewModel) = (vm.estado.value.prompts[70] as PromptsDoFrame.Pronto).lista.size

    private fun avisoLigado(vm: PainelDeIaViewModel) =
        listaMudouSemPromptNovo(vm.estado.value.listaMudouComPrompts[70], (vm.estado.value.prompts[70] as? PromptsDoFrame.Pronto)?.lista?.size)

    @Test
    fun `AP8 editar e confirmar com prompts ja feitos liga o aviso`() = runTest {
        val (vm, _) = montar(promptsDaCena = 2)
        vm.abrir(); advanceUntilIdle()
        vm.mudarCampoDoDossie(70, CampoDoDossie.ONDE, "o pátio")

        vm.confirmarDossie(70); advanceUntilIdle()

        assertEquals(mapOf(70 to 2), vm.estado.value.listaMudouComPrompts)
        assertTrue(avisoLigado(vm))
    }

    @Test
    fun `AP8 confirmar sem editar nada nao avisa`() = runTest {
        val (vm, _) = montar(promptsDaCena = 2)
        vm.abrir(); advanceUntilIdle()

        vm.confirmarDossie(70); advanceUntilIdle()

        assertTrue(vm.estado.value.listaMudouComPrompts.isEmpty())
        assertFalse(avisoLigado(vm))
    }

    @Test
    fun `AP8 cena sem prompt nao avisa porque o proximo ja nasce com a lista`() = runTest {
        val (vm, _) = montar(promptsDaCena = 0)
        vm.abrir(); advanceUntilIdle()
        vm.alternarPresenteDoDossie(70, 0)

        vm.confirmarDossie(70); advanceUntilIdle()

        assertTrue(vm.estado.value.listaMudouComPrompts.isEmpty())
    }

    @Test
    fun `AP8 se o servidor recusar a confirmacao nao avisa`() = runTest {
        val (vm, prompts) = montar(promptsDaCena = 2)
        prompts.dossieConfirmado = { ResultadoDaChamada.Falha("O servidor recusou a lista.") }
        vm.abrir(); advanceUntilIdle()
        vm.mudarCampoDoDossie(70, CampoDoDossie.ONDE, "o pátio")

        vm.confirmarDossie(70); advanceUntilIdle()

        assertTrue(vm.estado.value.listaMudouComPrompts.isEmpty())
    }

    @Test
    fun `AP8 gerar um prompt novo apaga o aviso`() = runTest {
        val (vm, prompts) = montar(promptsDaCena = 2)
        vm.abrir(); advanceUntilIdle()
        vm.mudarCampoDoDossie(70, CampoDoDossie.ONDE, "o pátio")
        vm.confirmarDossie(70); advanceUntilIdle()
        assertTrue(avisoLigado(vm))

        // O botão do aviso abre o mesmo diálogo do novo prompt; o "sim" gera.
        vm.pedirGerarPrompt(70, "A partida")
        assertEquals(70, vm.estado.value.confirmandoPrompt)
        vm.gerarPrompt(70, "")
        advanceUntilIdle()

        assertEquals(1, prompts.geracoes.size)
        assertEquals(3, promptsDaCena(vm))
        assertFalse("nasceu um prompt: o aviso some", avisoLigado(vm))
    }
}
