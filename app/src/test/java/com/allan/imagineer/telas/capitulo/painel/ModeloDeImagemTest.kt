package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.ModelosDeImagem
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.ResultadoDaGeracao
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val MUSE = "meta/muse-image"
private const val SEEDREAM = "bytedance-seed/seedream-5-0-flash"
private const val GEMINI = "google/gemini-2.5-flash-image"

private fun promptComModelo(id: Int, situacao: String, modelo: String?, original: Int? = null, modeloTexto: String? = null) =
    PromptDeFrame(
        id = id, frame_id = 70, texto = "p$id", situacao_da_geracao = situacao, modelo_imagem = modelo,
        prompt_original_id = original, modelo_ia = modeloTexto,
    )

/** As regras puras do modelo de imagem (Z6 a Z9). */
class RegrasDoModeloDeImagemTest {

    @Test
    fun `Z2 o padrao aparece primeiro, mesmo fora da lista, sem repetir nem itens vazios`() {
        assertEquals(listOf(MUSE, SEEDREAM, GEMINI), modelosParaEscolher(ModelosDeImagem(MUSE, listOf(MUSE, SEEDREAM, GEMINI))))
        assertEquals(listOf(MUSE, SEEDREAM), modelosParaEscolher(ModelosDeImagem(MUSE, listOf(SEEDREAM)))) // o padrão fora da lista
        assertEquals(listOf(MUSE, SEEDREAM), modelosParaEscolher(ModelosDeImagem(MUSE, listOf(" ", SEEDREAM, SEEDREAM, MUSE))))
        assertEquals(listOf(MUSE), modelosParaEscolher(ModelosDeImagem(MUSE, emptyList()))) // lista vazia: só o padrão
    }

    @Test
    fun `Z6 o modelo em uso e o escolhido, ou o padrao do servidor`() {
        val modelos = ModelosDeImagem(MUSE, listOf(MUSE, SEEDREAM))

        assertEquals(SEEDREAM, modeloEmUso(SEEDREAM, modelos))
        assertEquals(MUSE, modeloEmUso(null, modelos))
        assertEquals(MUSE, modeloEmUso("  ", modelos)) // escolha em branco não vale
        assertNull("sem a lista lida ainda, não se sabe", modeloEmUso(null, null))
        assertEquals(SEEDREAM, modeloEmUso(SEEDREAM, null))
    }

    @Test
    fun `Z9 a alternativa depois de uma recusa e o primeiro modelo diferente do que recusou`() {
        val opcoes = listOf(MUSE, SEEDREAM, GEMINI)

        assertEquals(SEEDREAM, alternativaAoModelo(MUSE, opcoes))
        assertEquals(MUSE, alternativaAoModelo(SEEDREAM, opcoes))
        assertEquals(MUSE, alternativaAoModelo(null, opcoes)) // modelo desconhecido (prompt antigo)
        assertEquals(MUSE, alternativaAoModelo(MUSE, listOf(MUSE))) // só há um: a sugestão é ele mesmo
        assertNull(alternativaAoModelo(MUSE, emptyList()))
    }

    @Test
    fun `Z7 a etiqueta diz qual modelo recusou ou gerou`() {
        assertEquals(listOf("Recusado por $MUSE"), etiquetasDoPrompt(promptComModelo(1, "RECUSADO", MUSE)))
        assertEquals(listOf("Gerado com $SEEDREAM"), etiquetasDoPrompt(promptComModelo(1, "COM_SUCESSO", SEEDREAM)))
    }

    @Test
    fun `Z7 prompt antigo recusado sem modelo continua com a etiqueta de antes`() {
        assertEquals(listOf("Recusado pelo provedor"), etiquetasDoPrompt(promptComModelo(1, "RECUSADO", null)))
        assertTrue(etiquetasDoPrompt(promptComModelo(1, "COM_SUCESSO", null)).isEmpty())
    }

    @Test
    fun `Z7 prompt nunca tentado nao leva etiqueta de modelo`() {
        assertTrue(etiquetasDoPrompt(promptComModelo(1, "NAO_TENTADO", null)).isEmpty())
    }

    @Test
    fun `Z7 a versao suavizada recusada mostra a origem e o modelo`() {
        val prompt = promptComModelo(2, "RECUSADO", MUSE, original = 1, modeloTexto = "google/gemini-2.5-flash")

        assertEquals(listOf("Versão suavizada", "Recusado por $MUSE"), etiquetasDoPrompt(prompt))
    }

    @Test
    fun `Z8 a tela cheia diz quem gerou a imagem, ou que foi importada`() {
        assertEquals("Gerada por $SEEDREAM", descreverOrigemDaImagem(ImagemDoPrompt(id = 1, modelo = SEEDREAM)))
        assertEquals("Importada", descreverOrigemDaImagem(ImagemDoPrompt(id = 1, modelo = null)))
    }
}

/** O modelo de imagem no ViewModel do painel (Z6 a Z10). */
@OptIn(ExperimentalCoroutinesApi::class)
class ModeloDeImagemNoPainelTest {

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

    private fun recusada(prompt: PromptDeFrame) = ResultadoDaChamada.Sucesso(ResultadoDaGeracao("RECUSADA", true, prompt, null))

    private fun gerada(prompt: PromptDeFrame) =
        ResultadoDaChamada.Sucesso(ResultadoDaGeracao("GERADA", false, prompt, ImagemDoPrompt(id = 700, prompt_id = prompt.id, modelo = SEEDREAM)))

    @Test
    fun `Z6 a lista de modelos e lida do servidor uma vez so`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.carregarModelosDeImagem(); vm.carregarModelosDeImagem(); advanceUntilIdle()
        vm.carregarModelosDeImagem(); advanceUntilIdle()

        assertEquals(ModelosDeImagem(MUSE, listOf(MUSE, SEEDREAM, GEMINI)), vm.estado.value.modelosDeImagem)
        assertEquals(1, prompts.leiturasDosModelos)
    }

    @Test
    fun `Z6 falha ao ler os modelos so deixa a escolha escondida e da para tentar de novo`() = runTest {
        val prompts = PromptsFalso().also { it.resultadoDosModelos = ResultadoDaChamada.Falha("fora do ar") }
        val vm = vm(prompts)
        vm.carregarModelosDeImagem(); advanceUntilIdle()

        assertNull(vm.estado.value.modelosDeImagem)
        vm.abrirEscolhaDeModelo()
        assertFalse("sem a lista, não há o que escolher", vm.estado.value.escolhendoModelo)

        prompts.resultadoDosModelos = ResultadoDaChamada.Sucesso(ModelosDeImagem(MUSE, listOf(MUSE)))
        vm.carregarModelosDeImagem(); advanceUntilIdle()
        assertEquals(MUSE, vm.estado.value.modelosDeImagem?.padrao)
    }

    @Test
    fun `Z6 abrir e fechar a escolha de modelo`() = runTest {
        val vm = vm(PromptsFalso())
        vm.carregarModelosDeImagem(); advanceUntilIdle()

        vm.abrirEscolhaDeModelo()
        assertTrue(vm.estado.value.escolhendoModelo)
        vm.fecharEscolhaDeModelo()
        assertFalse(vm.estado.value.escolhendoModelo)
        assertNull("fechar não escolhe nada", vm.estado.value.modeloEscolhido)
    }

    @Test
    fun `Z6 sem escolha o pedido vai sem modelo, e o servidor usa o padrao`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.gerarImagem(70, 1)
        advanceUntilIdle()

        assertEquals(listOf<String?>(null), prompts.modelosPedidos)
    }

    @Test
    fun `Z6 o modelo escolhido vale para as proximas geracoes ate trocar`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)
        vm.carregarModelosDeImagem(); advanceUntilIdle()

        vm.escolherModelo(SEEDREAM)
        vm.gerarImagem(70, 1); advanceUntilIdle()
        vm.gerarImagem(70, 2); advanceUntilIdle()
        vm.escolherModelo(GEMINI)
        vm.gerarImagem(70, 3); advanceUntilIdle()

        assertEquals(listOf<String?>(SEEDREAM, SEEDREAM, GEMINI), prompts.modelosPedidos)
        assertEquals(GEMINI, modeloEmUso(vm.estado.value.modeloEscolhido, vm.estado.value.modelosDeImagem))
        assertFalse(vm.estado.value.escolhendoModelo) // escolher fecha o diálogo
    }

    @Test
    fun `Z6 escolher o modelo nao mexe no padrao do servidor lido`() = runTest {
        val vm = vm(PromptsFalso())
        vm.carregarModelosDeImagem(); advanceUntilIdle()

        vm.escolherModelo(SEEDREAM)

        assertEquals(MUSE, vm.estado.value.modelosDeImagem?.padrao)
    }

    @Test
    fun `Z9 a recusa traz o modelo que recusou, para o dialogo dizer qual foi`() = runTest {
        val prompts = PromptsFalso().also { it.resultadoDaGeracaoDeImagem = recusada(promptComModelo(2, "RECUSADO", MUSE, original = 1)) }
        val vm = vm(prompts)

        vm.gerarImagem(70, 1); advanceUntilIdle()

        assertEquals(MUSE, vm.estado.value.recusaDeImagem?.modelo)
    }

    @Test
    fun `Z9 tentar com outro modelo manda o modelo e o torna o ativo`() = runTest {
        val prompts = PromptsFalso().also { it.resultadoDaGeracaoDeImagem = recusada(promptComModelo(2, "RECUSADO", MUSE, original = 1)) }
        val vm = vm(prompts)
        vm.gerarImagem(70, 1); advanceUntilIdle()
        prompts.resultadoDaGeracaoDeImagem = gerada(promptComModelo(2, "COM_SUCESSO", SEEDREAM, original = 1))

        vm.gerarImagem(70, 2, "texto editado", SEEDREAM)
        advanceUntilIdle()

        assertEquals(listOf<String?>(null, SEEDREAM), prompts.modelosPedidos)
        assertEquals(listOf(1 to null, 2 to "texto editado"), prompts.geracoesDeImagem)
        assertEquals(SEEDREAM, vm.estado.value.modeloEscolhido)
        assertNull(vm.estado.value.recusaDeImagem)
        // E as próximas gerações continuam nesse modelo.
        vm.gerarImagem(70, 3); advanceUntilIdle()
        assertEquals(SEEDREAM, prompts.modelosPedidos.last())
    }

    @Test
    fun `Z10 o original recusado pode ser reenviado a outro modelo pelo botao do cartao`() = runTest {
        val prompts = PromptsFalso().also { it.resultadoDaGeracaoDeImagem = gerada(promptComModelo(1, "COM_SUCESSO", GEMINI)) }
        val vm = vm(prompts)
        vm.escolherModelo(GEMINI)

        vm.gerarImagem(70, 1, null) // o botão do cartão do prompt 1, sem texto novo
        advanceUntilIdle()

        assertEquals(listOf(1 to null), prompts.geracoesDeImagem) // o mesmo prompt, texto não editado
        assertEquals(listOf<String?>(GEMINI), prompts.modelosPedidos)
    }

    @Test
    fun `Z6 o fluxo de um toque tambem usa o modelo escolhido`() = runTest {
        val prompts = PromptsFalso().also {
            it.lista = ResultadoDaChamada.Sucesso(listOf(PromptDeFrame(id = 8, frame_id = 70, texto = "p8")))
        }
        val vm = vm(prompts)
        vm.escolherModelo(SEEDREAM)

        vm.gerarImagemDoFrame(chaveDoFluxoDoFrame(70), 70, "A partida")
        advanceUntilIdle()

        assertEquals(listOf(8 to null), prompts.geracoesDeImagem)
        assertEquals(listOf<String?>(SEEDREAM), prompts.modelosPedidos)
    }
}
