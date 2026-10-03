package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.rede.ElementoParaVincular
import com.allan.imagineer.rede.ElementosParaVincular
import com.allan.imagineer.rede.ImagemCandidata
import com.allan.imagineer.rede.ModelosDeImagem
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ProvedorDeApi
import com.allan.imagineer.rede.RepositorioDePromptsPeloRetrofit
import com.allan.imagineer.rede.RepositorioDeSugestoesPeloRetrofit
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import com.allan.imagineer.rede.VinculadoDoFrame
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

private const val SEEDREAM = "replicate:bytedance/seedream-4.5"
private const val MUSE = "meta/muse-image"

private fun img(id: Int, ancora: Boolean = false) = ImagemCandidata(id = id, ancora = ancora)

/** O Foxen (sujeito da cena, original), o Prato (acrescentado à mão), o Manto (fora) e a Auri (sem imagem). */
private fun el(estado: Int, nome: String, tipo: String, vararg imagens: Int, noFrame: Boolean = false, removivel: Boolean = false) =
    ElementoParaVincular(elemento_id = estado * 10, estado_id = estado, nome = nome, tipo = tipo, no_frame = noFrame, removivel = removivel, imagens = imagens.map { img(it) })

private val foxen = el(1, "Foxen", "CRIATURA", 11, 12, noFrame = true, removivel = false)
private val prato = el(2, "Prato", "OBJETO", 21, noFrame = true, removivel = true)
private val manto = el(3, "Manto", "AMBIENTE", 31, 32)
private val auri = el(4, "Auri", "PERSONAGEM")

private val dados = ElementosParaVincular(identificados = listOf(foxen, manto), outros = listOf(prato, auri))

/** As regras puras do seletor (EV3 a EV5, EV7, EV8). */
class RegrasDoSeletorTest {

    @Test
    fun `EV4 ao abrir ficam marcados os elementos que ja estao no frame e as imagens de antes que ainda existem`() {
        val selecao = selecaoInicial(dados, listOf(11, 99))

        assertEquals(setOf(1, 2), selecao.elementos) // Foxen e Prato já estão no frame
        assertEquals("a 99 sumiu e não volta", setOf(11), selecao.imagens)
    }

    @Test
    fun `EV4 as ancoras nao vem marcadas sozinhas`() {
        val comAncora = ElementosParaVincular(identificados = listOf(el(5, "Pena", "OBJETO", 51).copy(imagens = listOf(img(51, ancora = true)))))

        assertTrue(selecaoInicial(comAncora, emptyList()).imagens.isEmpty())
    }

    @Test
    fun `EV4 marcar uma imagem marca tambem o elemento dela`() {
        val depois = alternarImagemNoSeletor(dados, SelecaoNoSeletor(), 31, ehCena = true)

        assertEquals(SelecaoNoSeletor(elementos = setOf(3), imagens = setOf(31)), depois)
    }

    @Test
    fun `EV4 desmarcar a imagem nao tira o elemento`() {
        val antes = SelecaoNoSeletor(elementos = setOf(3), imagens = setOf(31))

        assertEquals(SelecaoNoSeletor(elementos = setOf(3)), alternarImagemNoSeletor(dados, antes, 31, ehCena = true))
    }

    @Test
    fun `EV4 o limite e de quatro imagens`() {
        val cheia = SelecaoNoSeletor(elementos = setOf(1), imagens = setOf(1, 2, 3, 4))

        assertEquals(cheia, alternarImagemNoSeletor(dados, cheia, 31, ehCena = true))
        assertEquals(setOf(1, 2, 3), alternarImagemNoSeletor(dados, cheia, 4, ehCena = true).imagens) // desmarcar sempre pode
    }

    @Test
    fun `EV4 a caixa do nome marca o elemento, e desmarcar tira tambem as imagens dele`() {
        val marcado = alternarElementoNoSeletor(dados, SelecaoNoSeletor(), 3, ehCena = true)
        assertEquals(setOf(3), marcado.elementos)

        val comImagens = SelecaoNoSeletor(elementos = setOf(3), imagens = setOf(31, 32, 11))
        val desmarcado = alternarElementoNoSeletor(dados, comImagens, 3, ehCena = true)
        assertEquals(SelecaoNoSeletor(elementos = emptySet(), imagens = setOf(11)), desmarcado) // só as do Manto saem
    }

    @Test
    fun `EV5 o participante da sugestao da cena nao sai pela caixa do nome`() {
        assertTrue(elementoFixo(foxen))
        assertFalse("acrescentado à mão", elementoFixo(prato))
        assertFalse("fora do frame", elementoFixo(manto))
        val selecao = SelecaoNoSeletor(elementos = setOf(1))

        assertEquals(selecao, alternarElementoNoSeletor(dados, selecao, 1, ehCena = true))
    }

    @Test
    fun `V3 no retrato no maximo quatro elementos acrescentados, na cena nao ha limite`() {
        val muitos = ElementosParaVincular(outros = List(6) { el(100 + it, "E$it", "OBJETO", 200 + it) })
        var retrato = SelecaoNoSeletor()
        var cena = SelecaoNoSeletor()
        for (e in muitos.outros) {
            retrato = alternarElementoNoSeletor(muitos, retrato, e.estado_id, ehCena = false)
            cena = alternarElementoNoSeletor(muitos, cena, e.estado_id, ehCena = true)
        }

        assertEquals(4, retrato.elementos.size)
        assertEquals(6, cena.elementos.size)
    }

    @Test
    fun `V3 marcar uma imagem de um quinto elemento no retrato nao entra`() {
        val muitos = ElementosParaVincular(outros = List(6) { el(100 + it, "E$it", "OBJETO", 200 + it) })
        var selecao = SelecaoNoSeletor()
        for (e in muitos.outros.take(4)) selecao = alternarElementoNoSeletor(muitos, selecao, e.estado_id, ehCena = false)

        assertEquals(selecao, alternarImagemNoSeletor(muitos, selecao, 204, ehCena = false))
    }

    @Test
    fun `EV4 limpar tira as imagens e os elementos acrescentados, mas deixa os participantes da sugestao`() {
        val selecao = SelecaoNoSeletor(elementos = setOf(1, 2, 3), imagens = setOf(11, 21))

        assertEquals(SelecaoNoSeletor(elementos = setOf(1)), limparSeletor(dados))
        assertEquals(setOf(1), limparSeletor(dados).elementos)
        assertTrue(selecao.imagens.isNotEmpty())
    }

    @Test
    fun `EV7 so grava no servidor se os elementos mudaram`() {
        assertFalse(elementosMudaram(dados, SelecaoNoSeletor(elementos = setOf(1, 2), imagens = setOf(11))))
        assertTrue(elementosMudaram(dados, SelecaoNoSeletor(elementos = setOf(1, 2, 3))))
        assertTrue(elementosMudaram(dados, SelecaoNoSeletor(elementos = setOf(1))))
    }

    @Test
    fun `EV8 a linha diz os vinculados do retrato e as imagens, e avisa quando o modelo nao as usa`() {
        assertEquals("Elementos e imagens: vinculados: nenhum · imagens: nenhuma", descreverSelecao(false, emptyList(), 0, true))
        assertEquals("Elementos e imagens: vinculados: Prato, Manto · 2 imagens", descreverSelecao(false, listOf("Prato", "Manto"), 2, true))
        assertEquals("Elementos e imagens: imagens: nenhuma", descreverSelecao(true, emptyList(), 0, true))
        assertEquals("Elementos e imagens: 1 imagem", descreverSelecao(true, emptyList(), 1, true))
        assertEquals("Elementos e imagens: 3 imagens guardadas, este modelo não as usa", descreverSelecao(true, emptyList(), 3, false))
    }

    @Test
    fun `W1 so o modelo da lista aceita referencia`() {
        val modelos = ModelosDeImagem(MUSE, listOf(MUSE), comReferencia = listOf(SEEDREAM))

        assertTrue(modeloAceitaReferencia(SEEDREAM, modelos))
        assertFalse(modeloAceitaReferencia(MUSE, modelos))
        assertFalse(modeloAceitaReferencia(null, modelos))
        assertFalse(modeloAceitaReferencia(SEEDREAM, null))
    }

    @Test
    fun `W7 a etiqueta do prompt diz com quantas referencias foi gerado`() {
        fun prompt(ids: List<Int>) = PromptDeFrame(id = 1, frame_id = 70, texto = "p", situacao_da_geracao = "COM_SUCESSO", modelo_imagem = SEEDREAM, imagens_de_referencia = ids)

        assertEquals(listOf("Gerado com $SEEDREAM", "Com 1 referência"), etiquetasDoPrompt(prompt(listOf(5))))
        assertEquals(listOf("Gerado com $SEEDREAM", "Com 3 referências"), etiquetasDoPrompt(prompt(listOf(5, 6, 7))))
    }
}

private val criatura = elemento(id = 1, tipo = "CRIATURA", nome = "Foxen", elementoId = 10, estadoId = 100)
private val personagem = elemento(id = 4, tipo = "PERSONAGEM", nome = "Auri", elementoId = 40, estadoId = 400)

/** O ViewModel do painel e o seletor (EV1, EV7 a EV10). */
@OptIn(ExperimentalCoroutinesApi::class)
class SeletorNoPainelTest {

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

    private fun montar(): Triple<PainelDeIaViewModel, SugestoesFalso, PromptsFalso> {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(SugestoesDeCapitulo(gerado_em = "2026-10-02T10:00:00", elementos = listOf(criatura, personagem))))
        val prompts = PromptsFalso().also {
            it.resultadoDosModelos = ResultadoDaChamada.Sucesso(modelos)
            it.paraVincular = ResultadoDaChamada.Sucesso(dados)
        }
        val vm = PainelDeIaViewModel(
            5, repositorio, ElementosFalso(), prompts,
            ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
        )
        return Triple(vm, repositorio, prompts)
    }

    @Test
    fun `EV1 abrir o seletor da cena le os elementos e marca os que ja estao no frame`() = runTest {
        val (vm, _, prompts) = montar()

        vm.abrirSeletorDaCena(70)
        assertEquals(CandidatosDoSeletor.Carregando, vm.estado.value.escolhaDeElementos?.candidatos)
        advanceUntilIdle()

        val escolha = vm.estado.value.escolhaDeElementos!!
        assertEquals(listOf(70), prompts.frameDoSeletor)
        assertEquals(CandidatosDoSeletor.Prontos(dados), escolha.candidatos)
        assertTrue(escolha.ehCena)
        assertEquals(setOf(1, 2), escolha.selecao.elementos)
    }

    @Test
    fun `EV1 uma falha ao ler aparece no seletor`() = runTest {
        val (vm, _, prompts) = montar()
        prompts.paraVincular = ResultadoDaChamada.Falha("fora do ar")

        vm.abrirSeletorDaCena(70); advanceUntilIdle()

        assertEquals(CandidatosDoSeletor.Erro("fora do ar"), vm.estado.value.escolhaDeElementos?.candidatos)
    }

    @Test
    fun `V2 o seletor do retrato nao abre para personagem`() = runTest {
        val (vm, _, _) = montar()
        vm.aoAbrirPainel(); advanceUntilIdle()

        vm.abrirSeletorDoRetrato(personagem, 80)

        assertNull(vm.estado.value.escolhaDeElementos)
    }

    @Test
    fun `EV9 abrir o seletor do retrato sem frame cria o frame antes`() = runTest {
        val (vm, repositorio, prompts) = montar()
        vm.aoAbrirPainel(); advanceUntilIdle()

        vm.abrirSeletorDoRetrato(criatura, null); advanceUntilIdle()

        assertEquals("o retrato foi criado a partir do estado do elemento", listOf(100), repositorio.retratosPedidos)
        assertEquals("e o seletor leu o frame novo", listOf(80), prompts.frameDoSeletor)
        assertFalse(vm.estado.value.escolhaDeElementos!!.ehCena)
        assertEquals(80, vm.estado.value.retratosCriados[criatura.id])
    }

    @Test
    fun `EV9 com o frame existente nao cria outro`() = runTest {
        val (vm, repositorio, _) = montar()
        vm.aoAbrirPainel(); advanceUntilIdle()

        vm.abrirSeletorDoRetrato(criatura, 80); advanceUntilIdle()

        assertTrue(repositorio.retratosPedidos.isEmpty())
    }

    @Test
    fun `EV7 usar na cena com elementos novos manda o PUT dos estados com o conjunto inteiro`() = runTest {
        val (vm, repositorio, _) = montar()
        vm.abrirSeletorDaCena(70); advanceUntilIdle()

        vm.alternarImagemDoSeletor(31)
        vm.usarSeletor(); advanceUntilIdle()

        assertEquals(listOf(70 to listOf(1, 3, 2)), repositorio.estadosDefinidos.map { (f, ids) -> f to ids.sortedBy { if (it == 1) 0 else if (it == 3) 1 else 2 } })
        assertNull(vm.estado.value.escolhaDeElementos)
        assertEquals(mapOf(70 to listOf(31)), vm.estado.value.referenciasEscolhidas)
    }

    @Test
    fun `EV7 usar no retrato manda o PUT dos vinculos e guarda os nomes`() = runTest {
        val (vm, repositorio, _) = montar()
        vm.abrirSeletorDoRetrato(criatura, 80); advanceUntilIdle()
        vm.alternarElementoDoSeletor(3) // acrescenta o Manto aos que já estavam (Foxen original e Prato)

        vm.usarSeletor(); advanceUntilIdle()

        assertEquals(1, repositorio.vinculosDefinidos.size)
        assertEquals(80, repositorio.vinculosDefinidos[0].first)
        assertEquals(setOf(1, 2, 3), repositorio.vinculosDefinidos[0].second.toSet())
        assertEquals(listOf("Foxen", "Prato", "Manto").toSet(), vm.estado.value.vinculadosPorFrame[80]?.toSet())
    }

    @Test
    fun `EV7 sem mudar os elementos so as imagens ficam guardadas e nada vai ao servidor`() = runTest {
        val (vm, repositorio, _) = montar()
        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        vm.alternarImagemDoSeletor(11) // do Foxen, que já está na cena

        vm.usarSeletor(); advanceUntilIdle()

        assertTrue(repositorio.estadosDefinidos.isEmpty())
        assertEquals(mapOf(70 to listOf(11)), vm.estado.value.referenciasEscolhidas)
        assertTrue("sem mudar elementos o prompt não fica velho", vm.estado.value.mudancasPendentesDePrompt.isEmpty())
    }

    @Test
    fun `EV10 mudar os elementos avisa que e preciso um novo prompt`() = runTest {
        val (vm, _, _) = montar()
        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        vm.alternarElementoDoSeletor(3)

        vm.usarSeletor(); advanceUntilIdle()

        assertTrue(70 in vm.estado.value.mudancasPendentesDePrompt)
    }

    @Test
    fun `EV7 a recusa do servidor aparece no seletor, que fica aberto, e nada muda`() = runTest {
        val (vm, repositorio, _) = montar()
        repositorio.resultadoDeDefinirVinculos = ResultadoDaChamada.Falha("Auri é um personagem e fica individual")
        vm.abrirSeletorDoRetrato(criatura, 80); advanceUntilIdle()
        vm.alternarElementoDoSeletor(3)

        vm.usarSeletor(); advanceUntilIdle()

        val escolha = vm.estado.value.escolhaDeElementos!!
        assertEquals("Auri é um personagem e fica individual", escolha.erro)
        assertFalse(escolha.salvando)
        assertTrue(vm.estado.value.referenciasEscolhidas.isEmpty())
        assertTrue(vm.estado.value.mudancasPendentesDePrompt.isEmpty())
    }

    @Test
    fun `EV1 cancelar nao guarda nada`() = runTest {
        val (vm, _, _) = montar()
        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        vm.alternarImagemDoSeletor(31)

        vm.fecharSeletor()

        assertNull(vm.estado.value.escolhaDeElementos)
        assertTrue(vm.estado.value.referenciasEscolhidas.isEmpty())
    }

    @Test
    fun `EV4 limpar deixa so os participantes da sugestao e usar sem imagens tira a escolha do frame`() = runTest {
        val (vm, _, _) = montar()
        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        vm.alternarImagemDoSeletor(11)
        vm.usarSeletor(); advanceUntilIdle()
        assertEquals(mapOf(70 to listOf(11)), vm.estado.value.referenciasEscolhidas)

        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        assertEquals(setOf(11), vm.estado.value.escolhaDeElementos?.selecao?.imagens) // a escolha de antes volta marcada
        vm.limparSeletor()
        assertEquals(setOf(1), vm.estado.value.escolhaDeElementos?.selecao?.elementos)
        vm.usarSeletor(); advanceUntilIdle()

        assertTrue(vm.estado.value.referenciasEscolhidas.isEmpty())
    }

    @Test
    fun `RS1 usar com imagens guarda a escolha no servidor, e usar de novo sem mudar nao regrava`() = runTest {
        val (vm, repositorio, _) = montar()
        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        vm.alternarImagemDoSeletor(31)
        vm.usarSeletor(); advanceUntilIdle()
        assertEquals(listOf(70 to listOf(31)), repositorio.referenciasGuardadas)

        vm.abrirSeletorDaCena(70); advanceUntilIdle() // a escolha volta marcada
        vm.usarSeletor(); advanceUntilIdle()

        assertEquals("a mesma escolha não vai de novo", 1, repositorio.referenciasGuardadas.size)
    }

    @Test
    fun `RS1 tirar todas as imagens guarda a lista vazia no servidor`() = runTest {
        val (vm, repositorio, _) = montar()
        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        vm.alternarImagemDoSeletor(11)
        vm.usarSeletor(); advanceUntilIdle()
        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        vm.alternarImagemDoSeletor(11)
        vm.usarSeletor(); advanceUntilIdle()

        assertEquals(listOf(70 to listOf(11), 70 to emptyList<Int>()), repositorio.referenciasGuardadas)
    }

    @Test
    fun `RS1 ao abrir o frame le uma vez as referencias guardadas no servidor`() = runTest {
        val (vm, repositorio, _) = montar()
        repositorio.referenciasNoServidor[70] = listOf(31, 11)

        vm.carregarReferenciasGuardadas(70); advanceUntilIdle()
        vm.carregarReferenciasGuardadas(70); advanceUntilIdle()

        assertEquals(mapOf(70 to listOf(31, 11)), vm.estado.value.referenciasEscolhidas)
        assertEquals("lê uma vez só", listOf(70), repositorio.leiturasDeReferencias)
    }

    @Test
    fun `RS1 o que o servidor guardou nao sobrescreve a escolha que o usuario acabou de fazer`() = runTest {
        val (vm, repositorio, _) = montar()
        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        vm.alternarImagemDoSeletor(11)
        vm.usarSeletor(); advanceUntilIdle()
        repositorio.referenciasNoServidor[70] = listOf(99)

        vm.carregarReferenciasGuardadas(70); advanceUntilIdle()

        assertEquals(mapOf(70 to listOf(11)), vm.estado.value.referenciasEscolhidas)
    }

    @Test
    fun `RS1 sem nada guardado no servidor a escolha continua vazia`() = runTest {
        val (vm, _, _) = montar()

        vm.carregarReferenciasGuardadas(70); advanceUntilIdle()

        assertTrue(vm.estado.value.referenciasEscolhidas.isEmpty())
    }

    @Test
    fun `EV8 le uma vez os nomes dos vinculados de um retrato que ja existe`() = runTest {
        val (vm, repositorio, _) = montar()
        repositorio.vinculosNoServidor = ResultadoDaChamada.Sucesso(listOf(VinculadoDoFrame(estado_id = 200, nome = "Prato")))

        vm.carregarVinculados(80); advanceUntilIdle()
        vm.carregarVinculados(80); advanceUntilIdle()

        assertEquals(listOf(80), repositorio.leiturasDeVinculos)
        assertEquals(listOf("Prato"), vm.estado.value.vinculadosPorFrame[80])
    }

    @Test
    fun `W3 a geracao leva as imagens do frame quando o modelo as aceita, e so entao`() = runTest {
        val (vm, _, prompts) = montar()
        vm.carregarModelosDeImagem(); advanceUntilIdle()
        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        vm.alternarImagemDoSeletor(11)
        vm.usarSeletor(); advanceUntilIdle()

        vm.escolherModelo(MUSE)
        vm.gerarImagem(70, 1); advanceUntilIdle()
        vm.escolherModelo(SEEDREAM)
        vm.gerarImagem(70, 2); advanceUntilIdle()

        assertEquals(listOf(emptyList(), listOf(11)), prompts.referenciasPedidas)
    }

    @Test
    fun `W10 a escolha de um frame nao vai para outro`() = runTest {
        val (vm, _, prompts) = montar()
        vm.carregarModelosDeImagem(); advanceUntilIdle()
        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        vm.alternarImagemDoSeletor(11)
        vm.usarSeletor(); advanceUntilIdle()
        vm.escolherModelo(SEEDREAM)

        vm.gerarImagem(99, 5); advanceUntilIdle()

        assertEquals(listOf(emptyList<Int>()), prompts.referenciasPedidas)
    }
}

/** Os pedidos atravessando Retrofit + OkHttp de verdade (EV6, EV7, W1, W3). */
class SeletorPelaRedeTest {

    private lateinit var servidor: MockWebServer

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

    private fun provedor() = ProvedorDeApi(ArmazenamentoFalso(servidor.url("/").toString().trimEnd('/')))

    private fun json(corpo: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(corpo)

    @Test
    fun `EV6 le as duas secoes com os estados, as marcas e as imagens`() = runTest {
        servidor.enqueue(
            json(
                """{"identificados":[{"elemento_id":10,"estado_id":100,"nome":"Foxen","tipo":"CRIATURA","no_frame":true,"removivel":false,"imagens":[{"id":11,"prompt_id":4,"orientacao":"RETRATO","modelo":"x","origem":"GERADA","ancora":true,"data_importacao":"2026-10-02T10:00:00"}]}],""" +
                    """"outros":[{"elemento_id":30,"estado_id":300,"nome":"Manto","tipo":"AMBIENTE","no_frame":false,"removivel":false,"imagens":[]}]}""",
            ),
        )

        val dados = (RepositorioDePromptsPeloRetrofit(provedor(), leitorFalso()).elementosParaVincular(70) as ResultadoDaChamada.Sucesso).dado

        assertEquals("/frames/70/elementos-para-vincular", servidor.takeRequest().path)
        assertEquals(100, dados.identificados[0].estado_id)
        assertTrue(dados.identificados[0].no_frame && !dados.identificados[0].removivel)
        assertTrue(dados.identificados[0].imagens[0].ancora)
        assertEquals("Manto", dados.outros[0].nome)
    }

    @Test
    fun `EV7 o PUT dos estados da cena leva o conjunto inteiro`() = runTest {
        servidor.enqueue(json("""{"id":70,"vinculados":[]}"""))

        RepositorioDeSugestoesPeloRetrofit(provedor()).definirEstados(70, listOf(100, 200, 300))

        val pedido = servidor.takeRequest()
        assertEquals("PUT", pedido.method)
        assertEquals("/frames/70/estados", pedido.path)
        assertEquals("""{"estados_ids":[100,200,300]}""", pedido.body.readUtf8())
    }

    @Test
    fun `W3 o corpo de gerar leva imagens_de_referencia so quando ha escolha`() = runTest {
        val resposta = """{"resultado":"GERADA","suavizado":false,"prompt":{"id":4,"frame_id":70,"texto":"t","imagens_de_referencia":[10,11]},"imagem":{"id":9,"prompt_id":4}}"""
        servidor.enqueue(json(resposta))
        servidor.enqueue(json(resposta))
        val repositorio = RepositorioDePromptsPeloRetrofit(provedor(), leitorFalso())

        val geracao = (repositorio.gerarImagem(4, null, SEEDREAM, referencias = listOf(10, 11)) as ResultadoDaChamada.Sucesso).dado
        repositorio.gerarImagem(4, null, SEEDREAM)

        assertEquals("""{"modelo":"$SEEDREAM","imagens_de_referencia":[10,11]}""", servidor.takeRequest().body.readUtf8())
        assertEquals("""{"modelo":"$SEEDREAM"}""", servidor.takeRequest().body.readUtf8())
        assertEquals(listOf(10, 11), geracao.prompt.imagens_de_referencia)
    }

    @Test
    fun `W1 a configuracao entrega os modelos que aceitam referencia`() = runTest {
        servidor.enqueue(
            json("""{"tem_chave_api":true,"origem_da_chave":"ambiente","modelo_imagem":"meta/muse-image","modelos_com_referencia":{"$SEEDREAM":"image_input"},"prioridade_ia":"ECONOMIA"}"""),
        )

        val modelos = (RepositorioDePromptsPeloRetrofit(provedor(), leitorFalso()).modelosDeImagem() as ResultadoDaChamada.Sucesso).dado

        assertEquals(listOf(SEEDREAM), modelos.comReferencia)
    }

    private fun leitorFalso() = object : com.allan.imagineer.dados.LeitorDeArquivos {
        override fun descrever(uri: String): com.allan.imagineer.dados.ArquivoEscolhido? = null
        override fun abrir(uri: String): java.io.InputStream? = null
    }
}
