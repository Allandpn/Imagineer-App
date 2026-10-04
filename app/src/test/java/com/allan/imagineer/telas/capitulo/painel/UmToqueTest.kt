package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.rede.FrameCriado
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.ResultadoDaGeracao
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun umPromptDoFrame(id: Int, frame: Int = 80) = PromptDeFrame(id = id, frame_id = frame, texto = "p$id")

private fun geradaPara(prompt: PromptDeFrame) =
    ResultadoDaChamada.Sucesso(ResultadoDaGeracao("GERADA", false, prompt, ImagemDoPrompt(id = 700, prompt_id = prompt.id)))

/** As regras puras do fluxo de um toque (Q3, Q4, Q7). */
class RegrasDoUmToqueTest {

    @Test
    fun `Q4 cada etapa tem o seu texto`() {
        assertEquals("Criando o retrato…", descreverEtapa(EtapaDaImagem.CRIANDO_O_RETRATO))
        assertTrue("Montando o prompt" in descreverEtapa(EtapaDaImagem.MONTANDO_O_PROMPT))
        assertEquals(AVISO_GERANDO_IMAGEM, descreverEtapa(EtapaDaImagem.GERANDO_A_IMAGEM))
    }

    @Test
    fun `Q5 a chave do retrato vale antes e depois de o frame existir, e nao colide com a do frame`() {
        assertEquals("retrato:7", chaveDoFluxoDoRetrato(7))
        assertEquals("frame:7", chaveDoFluxoDoFrame(7))
    }

    @Test
    fun `Q3 a linha sob o botao diz o que ele faz e que gasta IA`() {
        assertEquals("Gera o prompt e a imagem (gasta IA).", avisoDoBotaoPrincipal(jaTemPrompt = false))
        assertEquals("Gera a imagem (gasta IA).", avisoDoBotaoPrincipal(jaTemPrompt = true))
    }

    @Test
    fun `Q7 o botao dos prompts diz quantos ha e muda quando aberto`() {
        assertEquals("Ver prompts", rotuloDeVerPrompts(aberto = false, quantos = 0))
        assertEquals("Ver prompts (3)", rotuloDeVerPrompts(aberto = false, quantos = 3))
        assertEquals("Ocultar prompts", rotuloDeVerPrompts(aberto = true, quantos = 3))
    }
}

/** O fluxo de um toque no ViewModel do painel (Q1 a Q6). */
@OptIn(ExperimentalCoroutinesApi::class)
class UmToqueNoPainelTest {

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
    private val sugestoes = SugestoesDeCapitulo(gerado_em = "2026-10-02T10:00:00", elementos = listOf(jon))

    private fun vm(repositorio: SugestoesFalso, prompts: PromptsFalso) = PainelDeIaViewModel(
        5, repositorio, ElementosFalso(), prompts,
        ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
    )

    @Test
    fun `Q2 sem frame - cria o retrato, gera o prompt e gera a imagem do prompt novo`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes))
        val prompts = PromptsFalso().also { it.geracao = ResultadoDaChamada.Sucesso(umPromptDoFrame(99)) }
        val vm = vm(repositorio, prompts)

        vm.gerarRetrato(jon)
        advanceUntilIdle()

        assertEquals(listOf(9), repositorio.retratosPedidos) // (a) o frame, com o estado vigente
        assertEquals(listOf(80 to null), prompts.geracoes) // (b) o prompt do frame novo, sem comentário
        assertEquals(listOf(99 to null), prompts.geracoesDeImagem) // (c) a imagem do prompt que acabou de sair
        assertTrue(vm.estado.value.etapasDeImagem.isEmpty()) // terminou: o botão volta
        assertEquals(mapOf(1 to 80), vm.estado.value.retratosCriados)
        assertTrue(vm.estado.value.versaoDosFrames >= 1) // o ícone no texto se atualiza
    }

    @Test
    fun `Q2 retrato ja criado nesta sessao nao cria outro frame`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes))
        val prompts = PromptsFalso()
        val vm = vm(repositorio, prompts)
        vm.gerarRetrato(jon); advanceUntilIdle()

        vm.gerarRetrato(jon); advanceUntilIdle()

        assertEquals(1, repositorio.retratosPedidos.size)
    }

    @Test
    fun `Q2 frame sem prompt - gera o prompt e a imagem, sem criar frame`() = runTest {
        val repositorio = SugestoesFalso()
        val prompts = PromptsFalso().also { it.geracao = ResultadoDaChamada.Sucesso(umPromptDoFrame(99, frame = 70)) }
        val vm = vm(repositorio, prompts)

        vm.gerarImagemDoFrame(chaveDoFluxoDoFrame(70), 70, "A partida")
        advanceUntilIdle()

        assertTrue(repositorio.retratosPedidos.isEmpty())
        assertEquals(listOf(70 to null), prompts.geracoes)
        assertEquals(listOf(99 to null), prompts.geracoesDeImagem)
    }

    @Test
    fun `Q2 Q6 frame com prompt - so gera a imagem do mais recente e nao gasta IA com prompt`() = runTest {
        val prompts = PromptsFalso().also {
            // O servidor entrega do mais antigo ao mais recente: o prompt 8 é o que vale.
            it.lista = ResultadoDaChamada.Sucesso(listOf(umPromptDoFrame(7, 70), umPromptDoFrame(8, 70)))
        }
        val vm = vm(SugestoesFalso(), prompts)

        vm.gerarImagemDoFrame(chaveDoFluxoDoFrame(70), 70, "A partida")
        advanceUntilIdle()

        assertTrue("não gera prompt novo", prompts.geracoes.isEmpty())
        assertEquals(listOf(8 to null), prompts.geracoesDeImagem)
    }

    @Test
    fun `GP2 so o prompt do frame gera o prompt e nao gasta imagem`() = runTest {
        val prompts = PromptsFalso().also { it.geracao = ResultadoDaChamada.Sucesso(umPromptDoFrame(99, frame = 70)) }
        val vm = vm(SugestoesFalso(), prompts)

        vm.gerarSoOPromptDoFrame(chaveDoFluxoDoFrame(70), 70, "A partida")
        advanceUntilIdle()

        assertEquals(listOf(70 to null), prompts.geracoes)
        assertTrue("não gera imagem", prompts.geracoesDeImagem.isEmpty())
        assertTrue("a etapa termina", vm.estado.value.etapasDeImagem.isEmpty())
    }

    @Test
    fun `GP2 so o prompt do retrato cria o frame e o prompt, sem imagem`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes))
        val prompts = PromptsFalso()
        val vm = vm(repositorio, prompts)

        vm.gerarSoOPromptDoRetrato(jon)
        advanceUntilIdle()

        assertEquals(1, repositorio.retratosPedidos.size)
        assertEquals(1, prompts.geracoes.size)
        assertTrue("não gera imagem", prompts.geracoesDeImagem.isEmpty())
    }

    @Test
    fun `GP2 so o prompt, com prompt ja existente, nao gasta IA nenhuma`() = runTest {
        val prompts = PromptsFalso().also { it.lista = ResultadoDaChamada.Sucesso(listOf(umPromptDoFrame(8, 70))) }
        val vm = vm(SugestoesFalso(), prompts)

        vm.gerarSoOPromptDoFrame(chaveDoFluxoDoFrame(70), 70, "A partida")
        advanceUntilIdle()

        assertTrue(prompts.geracoes.isEmpty())
        assertTrue(prompts.geracoesDeImagem.isEmpty())
    }

    @Test
    fun `K6 ao final a lista de prompts e relida`() = runTest {
        val prompts = PromptsFalso().also { it.lista = ResultadoDaChamada.Sucesso(listOf(umPromptDoFrame(8, 70))) }
        val vm = vm(SugestoesFalso(), prompts)

        vm.gerarImagemDoFrame(chaveDoFluxoDoFrame(70), 70, "A partida")
        advanceUntilIdle()

        assertTrue("leu para decidir e releu no fim", prompts.listagens >= 2)
    }

    @Test
    fun `Q5 falha ao criar o frame mostra a mensagem e nao gera prompt nem imagem`() = runTest {
        val repositorio = SugestoesFalso().also {
            it.resultadoDoRetrato = ResultadoDaChamada.Falha("O frame PERSONAGEM exige exatamente um estado.", codigoHttp = 422)
        }
        val prompts = PromptsFalso()
        val vm = vm(repositorio, prompts)

        vm.gerarRetrato(jon)
        advanceUntilIdle()

        assertEquals("O frame PERSONAGEM exige exatamente um estado.", vm.estado.value.mensagensDeRetrato[1]?.texto)
        assertTrue(prompts.geracoes.isEmpty())
        assertTrue(prompts.geracoesDeImagem.isEmpty())
        assertTrue(vm.estado.value.etapasDeImagem.isEmpty())
    }

    @Test
    fun `Q5 falha ao gerar o prompt - nao gera imagem, e tocar de novo continua sem recriar o frame`() = runTest {
        val repositorio = SugestoesFalso()
        val prompts = PromptsFalso().also { it.geracao = ResultadoDaChamada.Falha("fora do ar") }
        val vm = vm(repositorio, prompts)

        vm.gerarRetrato(jon); advanceUntilIdle()

        assertEquals("fora do ar", vm.estado.value.mensagensDePrompt[80]?.texto)
        assertTrue(prompts.geracoesDeImagem.isEmpty())
        assertTrue(vm.estado.value.etapasDeImagem.isEmpty())

        prompts.geracao = ResultadoDaChamada.Sucesso(umPromptDoFrame(99))
        vm.gerarRetrato(jon); advanceUntilIdle()

        assertEquals(1, repositorio.retratosPedidos.size) // o frame que já existe não é recriado
        assertEquals(2, prompts.geracoes.size) // o prompt é tentado de novo
        assertEquals(listOf(99 to null), prompts.geracoesDeImagem)
    }

    @Test
    fun `Q5 falha na imagem - o prompt fica, e tocar de novo so gera a imagem`() = runTest {
        val prompts = PromptsFalso().also {
            it.geracao = ResultadoDaChamada.Sucesso(umPromptDoFrame(99, 70))
            it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Falha("Não há chave de API do OpenRouter configurada.", codigoHttp = 422)
        }
        val vm = vm(SugestoesFalso(), prompts)

        vm.gerarImagemDoFrame(chaveDoFluxoDoFrame(70), 70, "A partida"); advanceUntilIdle()

        assertEquals("Não há chave de API do OpenRouter configurada.", vm.estado.value.mensagensDeImagem[99]?.texto)
        assertEquals(1, prompts.geracoes.size)

        // O servidor agora lista o prompt que ficou gravado.
        prompts.lista = ResultadoDaChamada.Sucesso(listOf(umPromptDoFrame(99, 70)))
        prompts.resultadoDaGeracaoDeImagem = geradaPara(umPromptDoFrame(99, 70))
        vm.gerarImagemDoFrame(chaveDoFluxoDoFrame(70), 70, "A partida"); advanceUntilIdle()

        assertEquals(1, prompts.geracoes.size) // não refez o prompt
        assertEquals(2, prompts.geracoesDeImagem.size)
        assertEquals("Imagem gerada.", vm.estado.value.mensagensDeImagem.getValue(99).texto)
    }

    @Test
    fun `Q4 as etapas aparecem enquanto o toque trabalha`() = runTest {
        val travaDoRetrato = CompletableDeferred<Unit>()
        val travaDaImagem = CompletableDeferred<Unit>()
        val repositorio = SugestoesFalso().also { it.travaDoRetrato = travaDoRetrato }
        val prompts = PromptsFalso().also {
            it.geracao = ResultadoDaChamada.Sucesso(umPromptDoFrame(99))
            it.travaDaGeracaoDeImagem = travaDaImagem
        }
        val vm = vm(repositorio, prompts)
        val chave = chaveDoFluxoDoRetrato(1)

        vm.gerarRetrato(jon); runCurrent()
        assertEquals(EtapaDaImagem.CRIANDO_O_RETRATO, vm.estado.value.etapasDeImagem[chave])

        travaDoRetrato.complete(Unit); runCurrent()
        assertEquals(EtapaDaImagem.GERANDO_A_IMAGEM, vm.estado.value.etapasDeImagem[chave])

        travaDaImagem.complete(Unit); advanceUntilIdle()
        assertNull(vm.estado.value.etapasDeImagem[chave])
    }

    @Test
    fun `Q4 sem prompt a etapa do meio e montar o prompt`() = runTest {
        val travaDoPrompt = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also { it.travaDaGeracao = travaDoPrompt }
        val vm = vm(SugestoesFalso(), prompts)
        val chave = chaveDoFluxoDoFrame(70)

        vm.gerarImagemDoFrame(chave, 70, "A partida"); runCurrent()

        assertEquals(EtapaDaImagem.MONTANDO_O_PROMPT, vm.estado.value.etapasDeImagem[chave])
        assertTrue(70 in vm.estado.value.gerandoPrompt)
        travaDoPrompt.complete(Unit); advanceUntilIdle()
        assertTrue(vm.estado.value.gerandoPrompt.isEmpty())
    }

    @Test
    fun `Q4 um toque por chave de cada vez`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also { it.travaDaGeracaoDeImagem = trava }
        val vm = vm(SugestoesFalso(), prompts)
        val chave = chaveDoFluxoDoFrame(70)

        vm.gerarImagemDoFrame(chave, 70, "A partida"); runCurrent()
        vm.gerarImagemDoFrame(chave, 70, "A partida") // toque repetido
        trava.complete(Unit); advanceUntilIdle()

        assertEquals(1, prompts.geracoes.size)
        assertEquals(1, prompts.geracoesDeImagem.size)
    }

    @Test
    fun `Q2 elemento sem retrato possivel nao inicia nada`() = runTest {
        val repositorio = SugestoesFalso()
        val prompts = PromptsFalso()
        val vm = vm(repositorio, prompts)

        vm.gerarRetrato(elemento(id = 2)) // novo, ainda não confirmado (N1)
        advanceUntilIdle()

        assertTrue(repositorio.retratosPedidos.isEmpty())
        assertTrue(vm.estado.value.etapasDeImagem.isEmpty())
    }

    @Test
    fun `K4 recusa no fluxo abre o dialogo e o botao volta`() = runTest {
        val devolvido = umPromptDoFrame(100, 70).copy(situacao_da_geracao = "RECUSADO", motivo_da_recusa = "x", prompt_original_id = 99)
        val prompts = PromptsFalso().also {
            it.lista = ResultadoDaChamada.Sucesso(listOf(umPromptDoFrame(99, 70)))
            it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Sucesso(ResultadoDaGeracao("RECUSADA", true, devolvido, null))
        }
        val vm = vm(SugestoesFalso(), prompts)

        vm.gerarImagemDoFrame(chaveDoFluxoDoFrame(70), 70, "A partida"); advanceUntilIdle()

        assertEquals(100, vm.estado.value.recusaDeImagem?.promptId)
        assertTrue(vm.estado.value.etapasDeImagem.isEmpty())
    }
}
