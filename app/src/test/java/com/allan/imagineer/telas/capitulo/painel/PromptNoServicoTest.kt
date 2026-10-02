package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.EventoDeAnalise
import com.allan.imagineer.analise.LocalDoUsuario
import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.analise.TipoDeEvento
import com.allan.imagineer.analise.descreverAviso
import com.allan.imagineer.rede.CenaSugerida
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun promptEvento(sucesso: Boolean = true, motivo: String? = null, livroId: Int? = 1) = EventoDeAnalise(
    capituloId = 7, livroId = livroId, rotuloDoCapitulo = "capítulo 3", sucesso = sucesso, motivo = motivo,
    tipo = TipoDeEvento.PROMPT, frameId = 70, rotuloDaCena = "A partida",
)

/** O texto do aviso de prompt gerado (G13). */
class AvisoDePromptTest {

    @Test
    fun `dentro do livro diz so a cena, fora diz o livro e o capitulo`() {
        assertEquals(
            "Prompt gerado: «A partida».",
            descreverAviso(promptEvento(), LocalDoUsuario(livroId = 1), painelVisivel = null, tituloDoLivro = "O Alienista"),
        )
        assertEquals(
            "Prompt gerado em «O Alienista», capítulo 3: «A partida».",
            descreverAviso(promptEvento(), LocalDoUsuario(livroId = null), painelVisivel = null, tituloDoLivro = "O Alienista"),
        )
    }

    @Test
    fun `falha tambem avisa, com o motivo`() {
        assertEquals(
            "A geração do prompt de «A partida» falhou: sem perfil padrão",
            descreverAviso(promptEvento(sucesso = false, motivo = "sem perfil padrão"), LocalDoUsuario(1), null, null),
        )
    }

    @Test
    fun `com o modal daquela cena na tela o aviso global se cala, porque o modal mostra o proprio`() {
        assertNull(descreverAviso(promptEvento(), LocalDoUsuario(1), painelVisivel = null, tituloDoLivro = null, modalDoFrameVisivel = 70))
        // O modal de OUTRO frame não suprime.
        assertNotNull(descreverAviso(promptEvento(), LocalDoUsuario(1), painelVisivel = null, tituloDoLivro = null, modalDoFrameVisivel = 71))
    }

    @Test
    fun `o painel de IA aberto NAO cala o aviso de prompt - so o modal da cena cala`() {
        // O painel suprime a análise (o resultado aparece nele); o prompt aparece no modal, não no painel.
        assertNotNull(descreverAviso(promptEvento(), LocalDoUsuario(1), painelVisivel = 7, tituloDoLivro = null))
    }
}

/** A geração do prompt vive no serviço do app, como a análise (G8, revisto). */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptNoServicoDoAppTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun escopo() = CoroutineScope(SupervisorJob() + agendador)

    private val comCenaConfirmada = SugestoesDeCapitulo(
        gerado_em = "2026-10-01T10:00:00",
        cenas = listOf(CenaSugerida(id = 1, titulo = "A partida", frame_id = 70)),
    )

    @Test
    fun `gerar emite um evento de prompt com o frame e o nome da cena`() = runTest {
        val prompts = PromptsFalso()
        val servico = ServicoDeAnalises(SugestoesFalso(), escopo(), prompts)
        val eventos = mutableListOf<EventoDeAnalise>()
        escopo().launch { servico.eventos.collect { eventos += it } }
        runCurrent()

        servico.iniciarPrompt(70, capituloId = 5, livroId = 2, rotuloDoCapitulo = "capítulo 3", rotuloDaCena = "A partida", comentario = "de costas")
        advanceUntilIdle()

        assertEquals(listOf<Pair<Int, String?>>(70 to "de costas"), prompts.geracoes)
        assertEquals(
            listOf(EventoDeAnalise(5, 2, "capítulo 3", sucesso = true, tipo = TipoDeEvento.PROMPT, frameId = 70, rotuloDaCena = "A partida")),
            eventos,
        )
        assertNull(servico.promptEmAndamento(70))
    }

    @Test
    fun `uma geracao por frame de cada vez - pedir outra devolve a mesma espera`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also { it.travaDaGeracao = trava }
        val servico = ServicoDeAnalises(SugestoesFalso(), escopo(), prompts)

        val primeira = servico.iniciarPrompt(70, 5, 2, "capítulo 3", "A partida", null)
        runCurrent()
        val segunda = servico.iniciarPrompt(70, 5, 2, "capítulo 3", "A partida", "outra")
        trava.complete(Unit)
        advanceUntilIdle()

        assertSame(primeira, segunda)
        assertEquals(1, prompts.geracoes.size)
    }

    @Test
    fun `a geracao sobrevive a quem saiu da tela e o aviso sai mesmo assim`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also { it.travaDaGeracao = trava }
        val servico = ServicoDeAnalises(SugestoesFalso(), escopo(), prompts)
        val eventos = mutableListOf<EventoDeAnalise>()
        escopo().launch { servico.eventos.collect { eventos += it } }
        runCurrent()

        val trabalho = servico.iniciarPrompt(70, 5, 2, "capítulo 3", "A partida", null)
        val espera = escopo().launch { trabalho.await() }
        runCurrent()
        espera.cancel() // o ViewModel morreu com a tela
        runCurrent()
        assertFalse(trabalho.isCancelled)

        trava.complete(Unit)
        advanceUntilIdle()

        assertTrue(trabalho.isCompleted)
        assertEquals(1, eventos.size)
    }

    // ---- no ViewModel ----

    private fun vm(prompts: PromptsFalso, servico: ServicoDeAnalises) =
        PainelDeIaViewModel(5, SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCenaConfirmada)), ElementosFalso(), prompts, servico)

    @Test
    fun `gerar pelo painel passa pelo servico, com o nome da cena no aviso, e conta um prompt gerado`() = runTest {
        val prompts = PromptsFalso()
        val servico = ServicoDeAnalises(SugestoesFalso(), escopo(), prompts)
        val eventos = mutableListOf<EventoDeAnalise>()
        escopo().launch { servico.eventos.collect { eventos += it } }
        runCurrent()
        val vm = vm(prompts, servico)
        vm.definirLivro(2)
        vm.definirRotuloDoCapitulo(ordem = 3, titulo = null)
        vm.aoAbrirPainel(); advanceUntilIdle()

        vm.pedirGerarPrompt(70)
        vm.gerarPrompt(70, "")
        advanceUntilIdle()

        assertEquals("A partida", eventos.single().rotuloDaCena)
        assertEquals(1, vm.estado.value.promptsGerados[70]) // é isto que faz o modal mostrar o aviso translúcido
        assertEquals(listOf(99), (vm.estado.value.prompts[70] as PromptsDoFrame.Pronto).lista.map { it.id })
    }

    @Test
    fun `um painel novo reencontra a geracao em andamento e recebe o resultado, sem duplicar`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also { it.travaDaGeracao = trava }
        val servico = ServicoDeAnalises(SugestoesFalso(), escopo(), prompts)

        val primeiro = vm(prompts, servico)
        primeiro.aoAbrirPainel(); advanceUntilIdle()
        primeiro.pedirGerarPrompt(70)
        primeiro.gerarPrompt(70, "")
        runCurrent()
        assertTrue(70 in primeiro.estado.value.gerandoPrompt)

        // O usuário sai do capítulo e volta: um ViewModel NOVO abre o modal da cena.
        val segundo = vm(prompts, servico)
        segundo.carregarPrompts(70)
        runCurrent()
        assertTrue("reencontra o gerando", 70 in segundo.estado.value.gerandoPrompt)

        // O servidor já gravou o prompt: a leitura o traz, e a espera também. Só pode aparecer uma vez.
        prompts.lista = ResultadoDaChamada.Sucesso(listOf(promptDoServidor()))
        trava.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, prompts.geracoes.size) // voltar ao modal não cobra de novo
        assertFalse(70 in segundo.estado.value.gerandoPrompt)
        val lista = (segundo.estado.value.prompts[70] as PromptsDoFrame.Pronto).lista
        assertEquals(1, lista.count { it.id == 99 })
    }

    private fun promptDoServidor() = com.allan.imagineer.rede.PromptDeFrame(id = 99, frame_id = 70, texto = "prompt 99")

    @Test
    fun `o painel avisa o servico de qual modal esta na tela`() = runTest {
        val prompts = PromptsFalso()
        val servico = ServicoDeAnalises(SugestoesFalso(), escopo(), prompts)
        val vm = vm(prompts, servico)

        vm.definirModalDoFrameVisivel(70)
        assertEquals(70, servico.modalDoFrameVisivel.value)
        vm.definirModalDoFrameVisivel(null)
        assertNull(servico.modalDoFrameVisivel.value)
    }

    @Test
    fun `falha na geracao nao conta como prompt gerado e mostra o erro`() = runTest {
        val prompts = PromptsFalso().also { it.geracao = ResultadoDaChamada.Falha("sem perfil", codigoHttp = 422) }
        val servico = ServicoDeAnalises(SugestoesFalso(), escopo(), prompts)
        val vm = vm(prompts, servico)
        vm.aoAbrirPainel(); advanceUntilIdle()

        vm.pedirGerarPrompt(70)
        vm.gerarPrompt(70, "")
        advanceUntilIdle()

        assertNull(vm.estado.value.promptsGerados[70])
        assertEquals(MensagemDoElemento("sem perfil", ehErro = true), vm.estado.value.mensagensDePrompt[70])
    }
}
