package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.EventoDeAnalise
import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.analise.TipoDeEvento
import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.ElementoCasado
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

private fun artefato(tipo: String = "ELEMENTO", sugestaoId: Int? = null, frameId: Int? = null) =
    Artefato(tipo = tipo, sugestao_id = sugestaoId, frame_id = frameId, rotulo = "x", situacao = "CONFIRMADO")

/** As regras puras do "Novo retrato" (N1, N4, N6). */
class RegrasDoRetratoTest {

    @Test
    fun `N1 so o elemento confirmado e nao descartado pode ter retrato`() {
        assertTrue(podeTerRetrato(elemento(id = 1, elementoId = 3, estadoId = 9))) // casado, revisado, com estado
        assertTrue(podeTerRetrato(elemento(id = 1, elementoId = 3, vigenteDoCapitulo = 2))) // estado vigente de outro capítulo
        assertFalse(podeTerRetrato(elemento(id = 1))) // novo
        assertFalse(podeTerRetrato(elemento(id = 1, elementoId = 3, automatico = true, estadoId = 9))) // casamento sem revisão
        assertFalse(podeTerRetrato(elemento(id = 1, elementoId = 3))) // casado, mas sem estado
        assertFalse(podeTerRetrato(elemento(id = 1, elementoId = 3, estadoId = 9, descartada = true)))
    }

    @Test
    fun `N6 o rotulo usa o nome do elemento cadastrado, e o da sugestao se ele nao veio`() {
        assertEquals("Retrato de Jon", rotuloDoRetrato(elemento(id = 1, nome = "Jon")))
        assertEquals(
            "Retrato de Jon Snow",
            rotuloDoRetrato(elemento(id = 1, nome = "Jon").copy(elemento_casado = ElementoCasado(3, "PERSONAGEM", "Jon Snow"))),
        )
    }

    @Test
    fun `N4 o retrato de cada sugestao vem do frame do artefato do elemento`() {
        val artefatos = listOf(
            artefato(sugestaoId = 1, frameId = 80), // elemento com retrato
            artefato(sugestaoId = 2, frameId = null), // sem retrato
            artefato(tipo = "CENA", sugestaoId = 3, frameId = 70), // cena: o id é de outra tabela, não entra
            artefato(sugestaoId = null, frameId = 81), // frame sem sugestão
        )

        assertEquals(mapOf(1 to 80), retratosPorSugestao(artefatos))
    }
}

/** O retrato no ViewModel do painel (N2 a N7). */
@OptIn(ExperimentalCoroutinesApi::class)
class NovoRetratoNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val jon = elemento(id = 1, nome = "Jon", elementoId = 3, estadoId = 9)

    private val sugestoes = SugestoesDeCapitulo(gerado_em = "2026-10-01T10:00:00", elementos = listOf(jon))

    private fun vm(repositorio: SugestoesFalso, prompts: PromptsFalso = PromptsFalso(), servico: ServicoDeAnalises? = null) =
        PainelDeIaViewModel(
            5, repositorio, ElementosFalso(), prompts,
            servico ?: ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
        )

    @Test
    fun `N2 cria o retrato com o estado que vale neste capitulo e guarda o frame novo`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes))
        val vm = vm(repositorio)
        vm.aoAbrirPainel(); advanceUntilIdle()

        vm.criarRetrato(jon)
        advanceUntilIdle()

        assertEquals(listOf(9), repositorio.retratosPedidos) // o id do ESTADO vigente, não o do elemento
        assertEquals(mapOf(1 to 80), vm.estado.value.retratosCriados)
        assertEquals(1, vm.estado.value.versaoDosFrames) // a tela relê os artefatos (N4)
        assertTrue(vm.estado.value.retratosOcupados.isEmpty())
        assertTrue("não gasta IA", repositorio.analises.isEmpty())
    }

    @Test
    fun `N1 elemento que nao e confirmado nao cria retrato`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)

        vm.criarRetrato(elemento(id = 2)) // novo
        vm.criarRetrato(elemento(id = 3, elementoId = 3, automatico = true, estadoId = 9)) // casamento a conferir
        advanceUntilIdle()

        assertTrue(repositorio.retratosPedidos.isEmpty())
        assertEquals(0, vm.estado.value.versaoDosFrames)
    }

    @Test
    fun `N5 uma criacao por elemento de cada vez`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val repositorio = SugestoesFalso().also { it.travaDoRetrato = trava }
        val vm = vm(repositorio)

        vm.criarRetrato(jon)
        runCurrent()
        assertTrue(1 in vm.estado.value.retratosOcupados)
        vm.criarRetrato(jon) // toque repetido
        trava.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repositorio.retratosPedidos.size)
    }

    @Test
    fun `N5 erro do servidor aparece no modal, nada e criado e da para tentar de novo`() = runTest {
        val repositorio = SugestoesFalso().also {
            it.resultadoDoRetrato = ResultadoDaChamada.Falha("O frame PERSONAGEM exige exatamente um estado.", codigoHttp = 422)
        }
        val vm = vm(repositorio)

        vm.criarRetrato(jon)
        advanceUntilIdle()

        assertEquals(MensagemDoElemento("O frame PERSONAGEM exige exatamente um estado.", ehErro = true), vm.estado.value.mensagensDeRetrato[1])
        assertTrue(vm.estado.value.retratosCriados.isEmpty())
        assertEquals(0, vm.estado.value.versaoDosFrames)
        assertTrue(vm.estado.value.retratosOcupados.isEmpty())

        repositorio.resultadoDoRetrato = ResultadoDaChamada.Sucesso(com.allan.imagineer.rede.FrameCriado(81, "Retrato de Jon"))
        vm.criarRetrato(jon)
        advanceUntilIdle()
        assertEquals(mapOf(1 to 81), vm.estado.value.retratosCriados)
        assertNull(vm.estado.value.mensagensDeRetrato[1]) // o erro antigo some ao tentar de novo
    }

    @Test
    fun `N6 o prompt do retrato gera o aviso com o nome do retrato`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes))
        val prompts = PromptsFalso()
        val servico = ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts)
        val eventos = mutableListOf<EventoDeAnalise>()
        CoroutineScope(SupervisorJob() + agendador).launch { servico.eventos.collect { eventos += it } }
        runCurrent()
        val vm = vm(repositorio, prompts, servico)
        vm.aoAbrirPainel(); advanceUntilIdle()
        vm.criarRetrato(jon)
        advanceUntilIdle()
        val frameDoRetrato = vm.estado.value.retratosCriados.getValue(1)

        vm.pedirGerarPrompt(frameDoRetrato, rotuloDoRetrato(jon))
        vm.gerarPrompt(frameDoRetrato, "")
        advanceUntilIdle()

        assertEquals("Retrato de Jon", eventos.single().rotuloDaCena)
        assertEquals(TipoDeEvento.PROMPT, eventos.single().tipo)
        assertEquals(frameDoRetrato, eventos.single().frameId)
    }

    @Test
    fun `N7 o unico gasto de IA e o gerar prompt, ja com confirmacao`() = runTest {
        val repositorio = SugestoesFalso()
        val prompts = PromptsFalso()
        val vm = vm(repositorio, prompts)

        vm.criarRetrato(jon)
        advanceUntilIdle()

        assertTrue(prompts.geracoes.isEmpty())
        assertTrue(repositorio.analises.isEmpty())
    }
}
