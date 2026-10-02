package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.ResultadoDaGeracao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun umPrompt(
    id: Int,
    situacao: String = "NAO_TENTADO",
    original: Int? = null,
    modelo: String? = null,
    motivo: String? = null,
    texto: String = "p$id",
) = PromptDeFrame(
    id = id, frame_id = 70, texto = texto, modelo_ia = modelo,
    situacao_da_geracao = situacao, motivo_da_recusa = motivo, prompt_original_id = original,
)

private fun gerada(prompt: PromptDeFrame, suavizado: Boolean = false) =
    ResultadoDaGeracao("GERADA", suavizado, prompt, ImagemDoPrompt(id = 700, prompt_id = prompt.id))

private fun recusada(prompt: PromptDeFrame) = ResultadoDaGeracao("RECUSADA", suavizado = true, prompt = prompt)

/** As regras puras de gerar a imagem (K3, K5). */
class RegrasDaGeracaoDeImagemTest {

    @Test
    fun `K3 o aviso diz se o servidor suavizou`() {
        assertEquals("Imagem gerada.", avisoDaGeracao(suavizado = false))
        assertTrue("suavizada" !in avisoDaGeracao(false))
        assertTrue("mais suave" in avisoDaGeracao(suavizado = true))
        assertTrue("outro prompt" in avisoDaGeracao(suavizado = true))
    }

    @Test
    fun `K5 prompt nunca tentado e sem origem nao leva etiqueta`() {
        assertTrue(etiquetasDoPrompt(umPrompt(1)).isEmpty())
        assertTrue("gerado com sucesso nao precisa de etiqueta", etiquetasDoPrompt(umPrompt(1, situacao = "COM_SUCESSO")).isEmpty())
    }

    @Test
    fun `K5 recusado leva a etiqueta de recusa`() {
        assertEquals(listOf("Recusado pelo provedor"), etiquetasDoPrompt(umPrompt(1, situacao = "RECUSADO")))
    }

    @Test
    fun `K5 suavizado tem modelo e editado nao`() {
        assertEquals(listOf("Versão suavizada"), etiquetasDoPrompt(umPrompt(2, original = 1, modelo = "barato/modelo")))
        assertEquals(listOf("Versão editada"), etiquetasDoPrompt(umPrompt(3, original = 1, modelo = null)))
    }

    @Test
    fun `K5 versao suavizada que o provedor recusou leva as duas etiquetas`() {
        val prompt = umPrompt(2, situacao = "RECUSADO", original = 1, modelo = "barato/modelo")

        assertEquals(listOf("Versão suavizada", "Recusado pelo provedor"), etiquetasDoPrompt(prompt))
    }
}

/** Gerar a imagem no ViewModel do painel (K1 a K8). */
@OptIn(ExperimentalCoroutinesApi::class)
class GerarImagemNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun vm(prompts: PromptsFalso): PainelDeIaViewModel {
        val repositorio = SugestoesFalso()
        return PainelDeIaViewModel(
            5, repositorio, ElementosFalso(), prompts,
            ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
        )
    }

    @Test
    fun `K1 K3 gera sem confirmacao, avisa, pede os artefatos de novo e rele a lista`() = runTest {
        val prompts = PromptsFalso().also {
            it.lista = ResultadoDaChamada.Sucesso(listOf(umPrompt(1)))
            it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Sucesso(gerada(umPrompt(1, situacao = "COM_SUCESSO")))
        }
        val vm = vm(prompts)
        vm.carregarPrompts(70); advanceUntilIdle()
        val leiturasAntes = prompts.listagens

        vm.gerarImagem(70, 1)
        advanceUntilIdle()

        assertEquals(listOf(1 to null), prompts.geracoesDeImagem) // direto: nenhuma confirmação no meio
        assertEquals(MensagemDoElemento("Imagem gerada.", ehErro = false), vm.estado.value.mensagensDeImagem[1])
        assertEquals(1, vm.estado.value.versaoDosFrames) // J6: o ícone no texto vira ILUSTRADO
        assertEquals(leiturasAntes + 1, prompts.listagens) // K6: a lista foi relida
        assertTrue(vm.estado.value.gerandoImagem.isEmpty())
        assertNull(vm.estado.value.recusaDeImagem)
    }

    @Test
    fun `K3 se o servidor suavizou, o aviso diz isso`() = runTest {
        val prompts = PromptsFalso().also {
            it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Sucesso(gerada(umPrompt(2, "COM_SUCESSO", original = 1, modelo = "m"), suavizado = true))
        }
        val vm = vm(prompts)

        vm.gerarImagem(70, 1)
        advanceUntilIdle()

        assertTrue("mais suave" in vm.estado.value.mensagensDeImagem.getValue(1).texto)
    }

    @Test
    fun `K6 a lista relida nao volta ao Lendo, entao a tela nao pisca`() = runTest {
        val prompts = PromptsFalso().also { it.lista = ResultadoDaChamada.Sucesso(listOf(umPrompt(1))) }
        val vm = vm(prompts)
        vm.carregarPrompts(70); advanceUntilIdle()
        val estadosVistos = mutableListOf<PromptsDoFrame?>()
        val coleta = CoroutineScope(SupervisorJob() + agendador).also { escopo ->
            escopo.launch { vm.estado.collect { estadosVistos += it.prompts[70] } }
        }
        runCurrent()
        estadosVistos.clear()

        vm.gerarImagem(70, 1)
        advanceUntilIdle()

        assertTrue("nunca passou por Lendo", estadosVistos.none { it == PromptsDoFrame.Lendo })
        coleta.coroutineContext[Job]?.cancel()
    }

    @Test
    fun `K6 depois da geracao aparece o prompt novo que o servidor criou`() = runTest {
        val prompts = PromptsFalso().also {
            it.lista = ResultadoDaChamada.Sucesso(listOf(umPrompt(1)))
            it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Sucesso(gerada(umPrompt(2, "COM_SUCESSO", original = 1, modelo = "m"), suavizado = true))
        }
        val vm = vm(prompts)
        vm.carregarPrompts(70); advanceUntilIdle()
        // O servidor agora devolve o original recusado e o suavizado.
        prompts.lista = ResultadoDaChamada.Sucesso(listOf(umPrompt(1, "RECUSADO"), umPrompt(2, "COM_SUCESSO", original = 1, modelo = "m")))

        vm.gerarImagem(70, 1)
        advanceUntilIdle()

        val lista = (vm.estado.value.prompts.getValue(70) as PromptsDoFrame.Pronto).lista
        assertEquals(listOf(2, 1), lista.map { it.id }) // o mais novo primeiro
        assertEquals("RECUSADO", lista.last().situacao_da_geracao)
    }

    @Test
    fun `K4 recusou de novo abre o dialogo com o motivo e o prompt devolvido`() = runTest {
        val devolvido = umPrompt(2, "RECUSADO", original = 1, modelo = "m", motivo = "content management policy", texto = "versão suave")
        val prompts = PromptsFalso().also { it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Sucesso(recusada(devolvido)) }
        val vm = vm(prompts)

        vm.gerarImagem(70, 1)
        advanceUntilIdle()

        assertEquals(RecusaDeImagem(frameId = 70, promptId = 2, texto = "versão suave", motivo = "content management policy"), vm.estado.value.recusaDeImagem)
        assertEquals(0, vm.estado.value.versaoDosFrames) // nenhuma imagem nova: os artefatos não mudam
        assertNull(vm.estado.value.mensagensDeImagem[1])
        assertTrue(vm.estado.value.gerandoImagem.isEmpty())
    }

    @Test
    fun `K4 recusa sem motivo usa o motivo padrao`() = runTest {
        val prompts = PromptsFalso().also { it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Sucesso(recusada(umPrompt(2, "RECUSADO", motivo = null))) }
        val vm = vm(prompts)

        vm.gerarImagem(70, 1)
        advanceUntilIdle()

        assertEquals("O provedor recusou o conteúdo do prompt.", vm.estado.value.recusaDeImagem?.motivo)
    }

    @Test
    fun `K4 tentar de novo manda o texto editado, fecha o dialogo e gera`() = runTest {
        val prompts = PromptsFalso().also {
            it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Sucesso(recusada(umPrompt(2, "RECUSADO", original = 1, modelo = "m", motivo = "x")))
        }
        val vm = vm(prompts)
        vm.gerarImagem(70, 1); advanceUntilIdle()
        assertTrue(vm.estado.value.recusaDeImagem != null)
        prompts.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Sucesso(gerada(umPrompt(3, "COM_SUCESSO", original = 2)))

        vm.gerarImagem(70, 2, "close-up, Auri, wrapped in linen")
        advanceUntilIdle()

        assertEquals(listOf(1 to null, 2 to "close-up, Auri, wrapped in linen"), prompts.geracoesDeImagem)
        assertNull(vm.estado.value.recusaDeImagem)
        assertEquals("Imagem gerada.", vm.estado.value.mensagensDeImagem.getValue(2).texto)
    }

    @Test
    fun `K4 fechar o dialogo deixa o prompt na lista`() = runTest {
        val prompts = PromptsFalso().also { it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Sucesso(recusada(umPrompt(2, "RECUSADO", motivo = "x"))) }
        val vm = vm(prompts)
        vm.gerarImagem(70, 1); advanceUntilIdle()

        vm.fecharRecusaDeImagem()

        assertNull(vm.estado.value.recusaDeImagem)
    }

    @Test
    fun `K2 um pedido por prompt de cada vez, com a barra enquanto roda`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also { it.travaDaGeracaoDeImagem = trava }
        val vm = vm(prompts)

        vm.gerarImagem(70, 1)
        runCurrent()
        assertTrue(1 in vm.estado.value.gerandoImagem)
        vm.gerarImagem(70, 1) // toque repetido
        trava.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, prompts.geracoesDeImagem.size)
        assertTrue(vm.estado.value.gerandoImagem.isEmpty())
    }

    @Test
    fun `K2 enquanto gera, importar imagem no mesmo prompt e ignorado, e o contrario tambem`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also { it.travaDaGeracaoDeImagem = trava }
        val vm = vm(prompts)

        vm.gerarImagem(70, 1)
        runCurrent()
        vm.importarImagem(70, 1, com.allan.imagineer.dados.ArquivoEscolhido("content://x/a.png", "a.png", 1000))
        trava.complete(Unit)
        advanceUntilIdle()

        assertTrue(prompts.importacoes.isEmpty())

        val outra = CompletableDeferred<Unit>()
        prompts.travaDaImportacao = outra
        vm.importarImagem(70, 2, com.allan.imagineer.dados.ArquivoEscolhido("content://x/b.png", "b.png", 1000))
        runCurrent()
        vm.gerarImagem(70, 2)
        outra.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(1 to null), prompts.geracoesDeImagem) // o do prompt 2 foi ignorado
    }

    @Test
    fun `K7 erro do servidor aparece no cartao, nada mais muda e da para tentar de novo`() = runTest {
        val prompts = PromptsFalso().also {
            it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Falha("Não há chave de API do OpenRouter configurada.", codigoHttp = 422)
        }
        val vm = vm(prompts)

        vm.gerarImagem(70, 1)
        advanceUntilIdle()

        assertEquals(MensagemDoElemento("Não há chave de API do OpenRouter configurada.", ehErro = true), vm.estado.value.mensagensDeImagem[1])
        assertEquals(0, vm.estado.value.versaoDosFrames)
        assertNull(vm.estado.value.recusaDeImagem)
        assertTrue(vm.estado.value.gerandoImagem.isEmpty())

        prompts.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Sucesso(gerada(umPrompt(1, "COM_SUCESSO")))
        vm.gerarImagem(70, 1)
        advanceUntilIdle()
        assertEquals(2, prompts.geracoesDeImagem.size)
        assertEquals("Imagem gerada.", vm.estado.value.mensagensDeImagem.getValue(1).texto) // o erro antigo some
    }

    @Test
    fun `K7 falha nao rele a lista`() = runTest {
        val prompts = PromptsFalso().also { it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Falha("fora do ar") }
        val vm = vm(prompts)

        vm.gerarImagem(70, 1)
        advanceUntilIdle()

        assertEquals(0, prompts.listagens)
    }
}
