package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.dados.LeitorDeArquivos
import com.allan.imagineer.rede.ElementoComImagens
import com.allan.imagineer.rede.ImagemCandidata
import com.allan.imagineer.rede.ModelosDeImagem
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ProvedorDeApi
import com.allan.imagineer.rede.ReferenciasCandidatas
import com.allan.imagineer.rede.RepositorioDePromptsPeloRetrofit
import com.allan.imagineer.rede.ResultadoDaChamada
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

private const val SEEDREAM = "replicate:bytedance/seedream-4.5"
private const val MUSE = "meta/muse-image"

private fun imagem(id: Int, ancora: Boolean = false) = ImagemCandidata(id = id, ancora = ancora)

private val candidatas = ReferenciasCandidatas(
    listOf(
        ElementoComImagens(1, "Auri", "PERSONAGEM", listOf(imagem(11), imagem(10, ancora = true))),
        ElementoComImagens(2, "Foxen", "CRIATURA", listOf(imagem(21))),
        ElementoComImagens(3, "Manto", "AMBIENTE", emptyList()),
    ),
)

/** As regras puras das referências (W3, W7 a W10). */
class RegrasDasReferenciasTest {

    private val modelos = ModelosDeImagem(MUSE, listOf(MUSE), comReferencia = listOf(SEEDREAM))

    @Test
    fun `W1 so o modelo da lista aceita referencia`() {
        assertTrue(modeloAceitaReferencia(SEEDREAM, modelos))
        assertTrue(modeloAceitaReferencia(" $SEEDREAM ", modelos))
        assertFalse(modeloAceitaReferencia(MUSE, modelos))
        assertFalse("sem modelo (o padrão do servidor)", modeloAceitaReferencia(null, modelos))
        assertFalse("lista ainda não lida", modeloAceitaReferencia(SEEDREAM, null))
        assertFalse("servidor sem a lista", modeloAceitaReferencia(SEEDREAM, ModelosDeImagem(MUSE, listOf(MUSE))))
    }

    @Test
    fun `W9 marcar e desmarcar, com o limite de quatro`() {
        assertEquals(setOf(1), alternarMarcacao(emptySet(), 1))
        assertEquals(emptySet<Int>(), alternarMarcacao(setOf(1), 1))
        assertEquals("no limite as demais não marcam", setOf(1, 2, 3, 4), alternarMarcacao(setOf(1, 2, 3, 4), 5))
        assertEquals("mas desmarcar sempre pode", setOf(1, 2, 3), alternarMarcacao(setOf(1, 2, 3, 4), 4))
    }

    @Test
    fun `W9 as ancoras vem marcadas, uma por elemento, ate o maximo`() {
        assertEquals(setOf(10), ancorasMarcadas(candidatas))
        val muitos = ReferenciasCandidatas(List(6) { ElementoComImagens(it, "E$it", "PERSONAGEM", listOf(imagem(100 + it, ancora = true))) })
        assertEquals(4, ancorasMarcadas(muitos).size)
        assertTrue(ancorasMarcadas(ReferenciasCandidatas()).isEmpty())
    }

    @Test
    fun `W8 W10 a linha de referencias diz quantas e se o modelo as usa`() {
        assertEquals("Referências: nenhuma", descreverReferencias(0, true))
        assertEquals("Referências: 1 imagem", descreverReferencias(1, true))
        assertEquals("Referências: 3 imagens", descreverReferencias(3, true))
        assertEquals("Referências: este modelo não usa referências", descreverReferencias(2, false))
    }

    @Test
    fun `W7 a etiqueta do prompt diz com quantas referencias foi gerado`() {
        fun prompt(ids: List<Int>) = PromptDeFrame(id = 1, frame_id = 70, texto = "p", situacao_da_geracao = "COM_SUCESSO", modelo_imagem = SEEDREAM, imagens_de_referencia = ids)

        assertEquals(listOf("Gerado com $SEEDREAM", "Com 1 referência"), etiquetasDoPrompt(prompt(listOf(5))))
        assertEquals(listOf("Gerado com $SEEDREAM", "Com 3 referências"), etiquetasDoPrompt(prompt(listOf(5, 6, 7))))
        assertEquals(listOf("Gerado com $SEEDREAM"), etiquetasDoPrompt(prompt(emptyList())))
    }
}

/** O ViewModel do painel e o modal de referências (W9, W10). */
@OptIn(ExperimentalCoroutinesApi::class)
class ReferenciasNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val modelos = ModelosDeImagem(MUSE, listOf(MUSE, SEEDREAM), comReferencia = listOf(SEEDREAM))

    private fun fake() = PromptsFalso().also {
        it.resultadoDosModelos = ResultadoDaChamada.Sucesso(modelos)
        it.candidatas = ResultadoDaChamada.Sucesso(candidatas)
    }

    private fun vm(prompts: PromptsFalso): PainelDeIaViewModel {
        val repositorio = SugestoesFalso()
        return PainelDeIaViewModel(
            5, repositorio, ElementosFalso(), prompts,
            ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
        )
    }

    @Test
    fun `W9 abrir le as candidatas do frame e marca as ancoras na primeira vez`() = runTest {
        val prompts = fake()
        val vm = vm(prompts)

        vm.abrirEscolhaDeReferencias(70)
        assertEquals("carregando enquanto não chega", CandidatasDasReferencias.Carregando, vm.estado.value.escolhaDeReferencias?.candidatas)
        advanceUntilIdle()

        val escolha = vm.estado.value.escolhaDeReferencias!!
        assertEquals(listOf(70), prompts.frameDasCandidatas)
        assertEquals(CandidatasDasReferencias.Prontas(candidatas), escolha.candidatas)
        assertEquals(setOf(10), escolha.marcadas)
    }

    @Test
    fun `W9 marcar, limpar e usar guardam a escolha deste frame`() = runTest {
        val vm = vm(fake())
        vm.abrirEscolhaDeReferencias(70); advanceUntilIdle()

        vm.alternarReferencia(11)
        vm.alternarReferencia(21)
        assertEquals(setOf(10, 11, 21), vm.estado.value.escolhaDeReferencias?.marcadas)
        vm.alternarReferencia(10)
        vm.usarReferencias()

        assertNull(vm.estado.value.escolhaDeReferencias)
        assertEquals(mapOf(70 to listOf(11, 21)), vm.estado.value.referenciasEscolhidas)
    }

    @Test
    fun `W10 reabrir mostra a escolha anterior, nao as ancoras de novo`() = runTest {
        val vm = vm(fake())
        vm.abrirEscolhaDeReferencias(70); advanceUntilIdle()
        vm.alternarReferencia(10) // desmarca a âncora
        vm.alternarReferencia(21)
        vm.usarReferencias()

        vm.abrirEscolhaDeReferencias(70); advanceUntilIdle()

        assertEquals(setOf(21), vm.estado.value.escolhaDeReferencias?.marcadas)
    }

    @Test
    fun `W10 usar com nada marcado tira a escolha do frame`() = runTest {
        val vm = vm(fake())
        vm.abrirEscolhaDeReferencias(70); advanceUntilIdle()
        vm.usarReferencias()
        assertEquals(mapOf(70 to listOf(10)), vm.estado.value.referenciasEscolhidas)

        vm.abrirEscolhaDeReferencias(70); advanceUntilIdle()
        vm.limparReferencias()
        vm.usarReferencias()

        assertTrue(vm.estado.value.referenciasEscolhidas.isEmpty())
    }

    @Test
    fun `W9 cancelar nao guarda nada`() = runTest {
        val vm = vm(fake())
        vm.abrirEscolhaDeReferencias(70); advanceUntilIdle()
        vm.alternarReferencia(11)

        vm.fecharEscolhaDeReferencias()

        assertNull(vm.estado.value.escolhaDeReferencias)
        assertTrue(vm.estado.value.referenciasEscolhidas.isEmpty())
    }

    @Test
    fun `W9 falha ao ler as candidatas aparece no modal, sem guardar nada`() = runTest {
        val prompts = fake().also { it.candidatas = ResultadoDaChamada.Falha("servidor fora do ar") }
        val vm = vm(prompts)

        vm.abrirEscolhaDeReferencias(70); advanceUntilIdle()

        assertEquals(CandidatasDasReferencias.Erro("servidor fora do ar"), vm.estado.value.escolhaDeReferencias?.candidatas)
    }

    @Test
    fun `W3 a geracao leva as referencias do frame quando o modelo as aceita`() = runTest {
        val prompts = fake()
        val vm = vm(prompts)
        vm.carregarModelosDeImagem(); advanceUntilIdle()
        vm.abrirEscolhaDeReferencias(70); advanceUntilIdle()
        vm.usarReferencias()
        vm.escolherModelo(SEEDREAM)

        vm.gerarImagem(70, 1); advanceUntilIdle()

        assertEquals(listOf(listOf(10)), prompts.referenciasPedidas)
    }

    @Test
    fun `W10 com um modelo que nao aceita, as referencias ficam guardadas mas nao vao`() = runTest {
        val prompts = fake()
        val vm = vm(prompts)
        vm.carregarModelosDeImagem(); advanceUntilIdle()
        vm.abrirEscolhaDeReferencias(70); advanceUntilIdle()
        vm.usarReferencias()

        vm.escolherModelo(MUSE)
        vm.gerarImagem(70, 1); advanceUntilIdle()
        vm.escolherModelo(SEEDREAM)
        vm.gerarImagem(70, 2); advanceUntilIdle()

        assertEquals("a primeira sem, a segunda com", listOf(emptyList(), listOf(10)), prompts.referenciasPedidas)
        assertEquals(mapOf(70 to listOf(10)), vm.estado.value.referenciasEscolhidas)
    }

    @Test
    fun `W10 a escolha de um frame nao vai para outro`() = runTest {
        val prompts = fake()
        val vm = vm(prompts)
        vm.carregarModelosDeImagem(); advanceUntilIdle()
        vm.abrirEscolhaDeReferencias(70); advanceUntilIdle()
        vm.usarReferencias()
        vm.escolherModelo(SEEDREAM)

        vm.gerarImagem(99, 5); advanceUntilIdle()

        assertEquals(listOf(emptyList<Int>()), prompts.referenciasPedidas)
    }

    @Test
    fun `W3 sem escolha nenhuma referencia vai`() = runTest {
        val prompts = fake()
        val vm = vm(prompts)
        vm.carregarModelosDeImagem(); advanceUntilIdle()
        vm.escolherModelo(SEEDREAM)

        vm.gerarImagem(70, 1); advanceUntilIdle()

        assertEquals(listOf(emptyList<Int>()), prompts.referenciasPedidas)
    }
}

/** O pedido e a configuração atravessando Retrofit + OkHttp de verdade (W1 a W3). */
class ReferenciasPelaRedeTest {

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

    private fun json(corpo: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(corpo)

    @Test
    fun `W3 o corpo leva imagens_de_referencia so quando ha escolha`() = runTest {
        val resposta = """{"resultado":"GERADA","suavizado":false,"prompt":{"id":4,"frame_id":70,"texto":"t","imagens_de_referencia":[10,11]},"imagem":{"id":9,"prompt_id":4}}"""
        servidor.enqueue(json(resposta))
        servidor.enqueue(json(resposta))

        val geracao = (repositorio().gerarImagem(4, null, SEEDREAM, referencias = listOf(10, 11)) as ResultadoDaChamada.Sucesso).dado
        repositorio().gerarImagem(4, null, SEEDREAM)

        assertEquals("""{"modelo":"$SEEDREAM","imagens_de_referencia":[10,11]}""", servidor.takeRequest().body.readUtf8())
        assertEquals("""{"modelo":"$SEEDREAM"}""", servidor.takeRequest().body.readUtf8())
        assertEquals(listOf(10, 11), geracao.prompt.imagens_de_referencia)
    }

    @Test
    fun `W1 a configuracao entrega os modelos que aceitam referencia`() = runTest {
        servidor.enqueue(
            json("""{"tem_chave_api":true,"origem_da_chave":"ambiente","modelo_imagem":"meta/muse-image","modelos_com_referencia":{"$SEEDREAM":"image_input"},"prioridade_ia":"ECONOMIA"}"""),
        )

        val modelos = (repositorio().modelosDeImagem() as ResultadoDaChamada.Sucesso).dado

        assertEquals(listOf(SEEDREAM), modelos.comReferencia)
    }

    @Test
    fun `W1 servidor sem o campo novo responde com a lista vazia`() = runTest {
        servidor.enqueue(json("""{"tem_chave_api":true,"origem_da_chave":"ambiente","prioridade_ia":"ECONOMIA"}"""))

        assertTrue((repositorio().modelosDeImagem() as ResultadoDaChamada.Sucesso).dado.comReferencia.isEmpty())
    }

    @Test
    fun `W2 le as candidatas do frame`() = runTest {
        servidor.enqueue(
            json("""{"elementos":[{"elemento_id":1,"nome":"Auri","tipo":"PERSONAGEM","imagens":[{"id":10,"prompt_id":4,"orientacao":"RETRATO","modelo":"x","origem":"GERADA","ancora":true,"data_importacao":"2026-10-02T10:00:00"}]},{"elemento_id":2,"nome":"Manto","tipo":"AMBIENTE","imagens":[]}]}"""),
        )

        val dado = (repositorio().referenciasCandidatas(70) as ResultadoDaChamada.Sucesso).dado

        assertEquals("/frames/70/referencias-candidatas", servidor.takeRequest().path)
        assertEquals(listOf("Auri", "Manto"), dado.elementos.map { it.nome })
        assertTrue(dado.elementos[0].imagens[0].ancora)
        assertTrue(dado.elementos[1].imagens.isEmpty())
    }
}
