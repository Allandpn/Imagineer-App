package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.dados.LeitorDeArquivos
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.ModelosDeImagem
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ProvedorDeApi
import com.allan.imagineer.rede.RepositorioDePromptsPeloRetrofit
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.ResultadoDaGeracao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream

private const val MUSE = "meta/muse-image"
private const val SEEDREAM = "bytedance-seed/seedream-5-0-flash"
private const val FLUX = "replicate:black-forest-labs/flux-schnell"
private const val KLEIN = "replicate:black-forest-labs/flux-2-klein-4b"

private fun umPrompt(situacao: String, semFiltro: Boolean = false, modelo: String? = MUSE) =
    PromptDeFrame(
        id = 1, frame_id = 70, texto = "p1", situacao_da_geracao = situacao, modelo_imagem = modelo,
        sem_filtro_de_seguranca = semFiltro,
    )

/** As regras puras de gerar sem o filtro de segurança (F12, F13, F16). */
class RegrasDoSemFiltroTest {

    private val comLista = ModelosDeImagem(MUSE, listOf(MUSE, SEEDREAM), listOf(FLUX, KLEIN))
    private val semLista = ModelosDeImagem(MUSE, listOf(MUSE, SEEDREAM))

    @Test
    fun `F19 escolher um modelo da lista sem filtro e pedir a geracao sem o filtro, e so ele`() {
        assertTrue(modeloEstaSemFiltro(FLUX, comLista))
        assertTrue(modeloEstaSemFiltro(" $KLEIN ", comLista))
        assertFalse("modelo moderado", modeloEstaSemFiltro(MUSE, comLista))
        assertFalse("o mesmo id sem o prefixo não é o da lista", modeloEstaSemFiltro("black-forest-labs/flux-schnell", comLista))
        assertFalse("servidor sem a lista", modeloEstaSemFiltro(FLUX, semLista))
        assertFalse("sem modelo (o padrão do servidor)", modeloEstaSemFiltro(null, comLista))
        assertFalse(modeloEstaSemFiltro("  ", comLista))
        assertFalse("a lista ainda não foi lida", modeloEstaSemFiltro(FLUX, null))
    }

    @Test
    fun `F13 o seletor so tem os modelos sem filtro, sem repetir, e nunca o padrao`() {
        assertEquals(listOf(FLUX, KLEIN), modelosSemFiltroParaEscolher(comLista))
        assertEquals(listOf(FLUX), modelosSemFiltroParaEscolher(ModelosDeImagem(MUSE, listOf(MUSE), listOf(" ", FLUX, FLUX))))
        assertTrue(modelosSemFiltroParaEscolher(semLista).isEmpty())
        assertFalse(MUSE in modelosSemFiltroParaEscolher(comLista))
    }

    @Test
    fun `F16 a etiqueta Sem filtro aparece no prompt tentado sem o filtro, e so nele`() {
        assertEquals(listOf("Gerado com $FLUX", "Sem filtro"), etiquetasDoPrompt(umPrompt("COM_SUCESSO", semFiltro = true, modelo = FLUX)))
        assertEquals(listOf("Recusado por $FLUX", "Sem filtro"), etiquetasDoPrompt(umPrompt("RECUSADO", semFiltro = true, modelo = FLUX)))
        assertEquals(listOf("Gerado com $MUSE"), etiquetasDoPrompt(umPrompt("COM_SUCESSO")))
    }

    @Test
    fun `F16 a tela cheia diz que a imagem foi gerada sem o filtro`() {
        assertEquals("Gerada por $FLUX (sem filtro)", descreverOrigemDaImagem(ImagemDoPrompt(id = 1, modelo = FLUX, sem_filtro_de_seguranca = true)))
        assertEquals("Gerada por $MUSE", descreverOrigemDaImagem(ImagemDoPrompt(id = 1, modelo = MUSE)))
        assertEquals("Importada", descreverOrigemDaImagem(ImagemDoPrompt(id = 1)))
    }
}

/** O ViewModel do painel: o modelo escolhido no modal decide se o filtro vai desligado (F19). */
@OptIn(ExperimentalCoroutinesApi::class)
class SemFiltroNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun prompts(modelos: ModelosDeImagem) = PromptsFalso().also { it.resultadoDosModelos = ResultadoDaChamada.Sucesso(modelos) }

    private fun vm(prompts: PromptsFalso): PainelDeIaViewModel {
        val repositorio = SugestoesFalso()
        return PainelDeIaViewModel(
            5, repositorio, ElementosFalso(), prompts,
            ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
        )
    }

    private val modelos = ModelosDeImagem(MUSE, listOf(MUSE, SEEDREAM), listOf(FLUX, KLEIN))

    @Test
    fun `F19 escolher um modelo sem filtro no modal faz a geracao ir com o filtro desligado`() = runTest {
        val fake = prompts(modelos)
        val vm = vm(fake)
        vm.carregarModelosDeImagem(); advanceUntilIdle()

        vm.escolherModelo(KLEIN)
        vm.gerarImagem(70, 1); advanceUntilIdle()

        assertEquals(listOf(1), fake.pedidosSemFiltro)
        assertEquals(listOf<String?>(KLEIN), fake.modelosPedidos)
    }

    @Test
    fun `F19 com um modelo moderado ou sem escolha nada vai com o filtro desligado`() = runTest {
        val fake = prompts(modelos)
        val vm = vm(fake)
        vm.carregarModelosDeImagem(); advanceUntilIdle()

        vm.gerarImagem(70, 1); advanceUntilIdle() // sem escolha: o padrão do servidor
        vm.escolherModelo(SEEDREAM)
        vm.gerarImagem(70, 2); advanceUntilIdle()

        assertTrue(fake.pedidosSemFiltro.isEmpty())
    }

    @Test
    fun `F19 a escolha vale ate trocar, e voltar a um modelo moderado volta ao pedido normal`() = runTest {
        val fake = prompts(modelos)
        val vm = vm(fake)
        vm.carregarModelosDeImagem(); advanceUntilIdle()

        vm.escolherModelo(FLUX)
        vm.gerarImagem(70, 1); advanceUntilIdle()
        vm.gerarImagem(70, 2); advanceUntilIdle()
        vm.escolherModelo(MUSE)
        vm.gerarImagem(70, 3); advanceUntilIdle()

        assertEquals(listOf(1, 2), fake.pedidosSemFiltro)
    }

    @Test
    fun `F19 o fluxo de um toque do frame tambem respeita o modelo sem filtro`() = runTest {
        val fake = prompts(modelos).also { it.lista = ResultadoDaChamada.Sucesso(listOf(PromptDeFrame(id = 9, frame_id = 70, texto = "p9"))) }
        val vm = vm(fake)
        vm.carregarModelosDeImagem(); advanceUntilIdle()
        vm.escolherModelo(KLEIN)

        vm.gerarImagemDoFrame("frame70", 70, "A cena"); advanceUntilIdle()

        assertEquals(listOf(9), fake.pedidosSemFiltro)
    }

    @Test
    fun `F19 o dialogo da recusa e o de editar tambem mandam o filtro desligado se o modelo for da lista`() = runTest {
        val fake = prompts(modelos)
        val vm = vm(fake)
        vm.carregarModelosDeImagem(); advanceUntilIdle()

        vm.gerarImagem(70, 1, "texto editado", KLEIN); advanceUntilIdle() // é o que o diálogo da recusa chama

        assertEquals(listOf(1), fake.pedidosSemFiltro)
        assertEquals(KLEIN, vm.estado.value.modeloEscolhido)
    }

    @Test
    fun `F17 recusando mesmo sem o filtro abre o dialogo da recusa, sem subir de nivel`() = runTest {
        val fake = prompts(modelos).also {
            it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Sucesso(
                ResultadoDaGeracao("RECUSADA", false, umPrompt("RECUSADO", semFiltro = true, modelo = FLUX), null),
            )
        }
        val vm = vm(fake)
        vm.carregarModelosDeImagem(); advanceUntilIdle()
        vm.escolherModelo(FLUX)

        vm.gerarImagem(70, 1); advanceUntilIdle()

        assertEquals(FLUX, vm.estado.value.recusaDeImagem?.modelo)
        assertEquals("uma só chamada", listOf(1), fake.pedidosSemFiltro)
    }

    @Test
    fun `F15 uma falha do servidor, como o 422 da trava de menores, aparece como recado do prompt`() = runTest {
        val fake = prompts(modelos).also {
            it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Falha("O prompt fala de uma pessoa menor de idade")
        }
        val vm = vm(fake)
        vm.carregarModelosDeImagem(); advanceUntilIdle()
        vm.escolherModelo(FLUX)

        vm.gerarImagem(70, 1); advanceUntilIdle()

        val recado = vm.estado.value.mensagensDeImagem[1]
        assertTrue(recado?.ehErro == true)
        assertTrue(recado!!.texto.contains("menor de idade"))
        assertFalse(1 in vm.estado.value.gerandoImagem)
    }
}

/** O pedido e a configuração atravessando Retrofit + OkHttp de verdade (F12, F13). */
class SemFiltroPelaRedeTest {

    private lateinit var servidor: MockWebServer

    private class LeitorFalso : LeitorDeArquivos {
        override fun descrever(uri: String): ArquivoEscolhido? = null
        override fun abrir(uri: String): InputStream? = null
    }

    private class ArmazenamentoFalso(url: String) : ArmazenamentoDeConfiguracao {
        override val urlDoServidor: Flow<String?> = flowOf(url)
        override suspend fun salvarUrlDoServidor(url: String) = Unit
    }

    @Before
    fun subir() {
        servidor = MockWebServer().apply { start() }
    }

    @After
    fun derrubar() {
        servidor.shutdown()
    }

    private fun repositorio() =
        RepositorioDePromptsPeloRetrofit(ProvedorDeApi(ArmazenamentoFalso(servidor.url("/").toString().trimEnd('/'))), LeitorFalso())

    private val resposta =
        """{"resultado":"GERADA","suavizado":false,"prompt":{"id":4,"frame_id":70,"texto":"t","sem_filtro_de_seguranca":true},"imagem":{"id":9,"prompt_id":4,"sem_filtro_de_seguranca":true}}"""

    @Test
    fun `F12 o corpo leva sem_filtro_de_seguranca so quando a pessoa pediu, junto do modelo`() = runTest {
        servidor.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(resposta))
        servidor.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(resposta))

        val geracao = (repositorio().gerarImagem(4, null, FLUX, semFiltro = true) as ResultadoDaChamada.Sucesso).dado
        repositorio().gerarImagem(4, null, FLUX)

        assertEquals("""{"modelo":"$FLUX","sem_filtro_de_seguranca":true}""", servidor.takeRequest().body.readUtf8())
        assertEquals("""{"modelo":"$FLUX"}""", servidor.takeRequest().body.readUtf8())
        assertTrue(geracao.prompt.sem_filtro_de_seguranca)
        assertTrue(geracao.imagem!!.sem_filtro_de_seguranca)
    }

    @Test
    fun `F13 a configuracao entrega a lista de modelos sem filtro`() = runTest {
        servidor.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"tem_chave_api":true,"origem_da_chave":"ambiente","modelo_imagem":"meta/muse-image","modelos_de_imagem":["meta/muse-image"],"modelos_sem_filtro":["$FLUX"],"prioridade_ia":"ECONOMIA"}""",
            ),
        )

        val modelos = (repositorio().modelosDeImagem() as ResultadoDaChamada.Sucesso).dado

        assertEquals(listOf(FLUX), modelos.semFiltro)
    }

    @Test
    fun `F13 servidor sem a lista nova responde com a lista vazia`() = runTest {
        servidor.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"tem_chave_api":true,"origem_da_chave":"ambiente","prioridade_ia":"ECONOMIA"}"""),
        )

        assertTrue((repositorio().modelosDeImagem() as ResultadoDaChamada.Sucesso).dado.semFiltro.isEmpty())
    }
}
