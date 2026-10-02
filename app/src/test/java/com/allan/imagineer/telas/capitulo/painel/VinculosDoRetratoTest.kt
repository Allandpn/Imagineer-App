package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.rede.ProvedorDeApi
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

// Os elementos do capítulo: a criatura (sujeito), um objeto, um ambiente, um personagem e um objeto ainda não confirmado.
private val criatura = elemento(id = 1, tipo = "CRIATURA", nome = "Foxen", elementoId = 10, estadoId = 100)
private val objeto = elemento(id = 2, tipo = "OBJETO", nome = "Prato", elementoId = 20, estadoId = 200)
private val ambiente = elemento(id = 3, tipo = "AMBIENTE", nome = "Manto", elementoId = 30, estadoId = 300)
private val personagem = elemento(id = 4, tipo = "PERSONAGEM", nome = "Auri", elementoId = 40, estadoId = 400)
private val naoConfirmado = elemento(id = 5, tipo = "OBJETO", nome = "Xícara")
private val descartado = elemento(id = 6, tipo = "OBJETO", nome = "Colher", elementoId = 60, estadoId = 600, descartada = true)

private val todos = listOf(criatura, objeto, ambiente, personagem, naoConfirmado, descartado)

/** As regras puras dos vínculos (V2, V3, V8). */
class RegrasDosVinculosTest {

    @Test
    fun `V2 personagem e individual e nao aceita vinculos, os outros tipos aceitam`() {
        assertFalse(aceitaVinculos(personagem))
        for (tipo in listOf("AMBIENTE", "OBJETO", "CRIATURA", "GRUPO", "VEICULO", "EDIFICACAO")) {
            assertTrue(tipo, aceitaVinculos(elemento(id = 9, tipo = tipo, elementoId = 90, estadoId = 900)))
        }
    }

    @Test
    fun `V2 sem confirmacao ou descartado nao aceita vinculos`() {
        assertFalse(aceitaVinculos(naoConfirmado))
        assertFalse(aceitaVinculos(descartado))
    }

    @Test
    fun `V8 os candidatos sao os outros confirmados que nao sao personagem`() {
        assertEquals(listOf("Prato", "Manto"), candidatosAoVinculo(todos, criatura).map { it.nome })
    }

    @Test
    fun `V8 o proprio sujeito e um elemento repetido nao entram`() {
        val repetido = elemento(id = 7, tipo = "OBJETO", nome = "Prato (de novo)", elementoId = 20, estadoId = 200)

        val candidatos = candidatosAoVinculo(todos + repetido, criatura)

        assertEquals("o mesmo elemento cadastrado aparece uma vez", listOf("Prato", "Manto"), candidatos.map { it.nome })
        assertTrue(candidatosAoVinculo(listOf(criatura), criatura).isEmpty())
    }

    @Test
    fun `V8 a linha dos vinculados`() {
        assertEquals("Vinculados: nenhum", descreverVinculados(emptyList()))
        assertEquals("Vinculados: Prato", descreverVinculados(listOf("Prato")))
        assertEquals("Vinculados: Prato, Manto", descreverVinculados(listOf("Prato", "Manto")))
    }

    @Test
    fun `V3 o limite e de quatro, como o das referencias`() {
        assertEquals(4, MAXIMO_DE_VINCULADOS)
        assertEquals(setOf(1, 2, 3, 4), alternarMarcacao(setOf(1, 2, 3, 4), 5, MAXIMO_DE_VINCULADOS))
    }
}

/** O ViewModel do painel e o modal de vinculados (V8, V7). */
@OptIn(ExperimentalCoroutinesApi::class)
class VinculosNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val sugestoes = SugestoesDeCapitulo(gerado_em = "2026-10-02T10:00:00", elementos = todos)

    private fun montar(): Pair<PainelDeIaViewModel, SugestoesFalso> {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes))
        val prompts = PromptsFalso()
        val vm = PainelDeIaViewModel(
            5, repositorio, ElementosFalso(), prompts,
            ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
        )
        return vm to repositorio
    }


    @Test
    fun `V2 o modal nao abre para personagem`() = runTest {
        val (vm, _) = montar()
        vm.aoAbrirPainel(); advanceUntilIdle()

        vm.abrirEscolhaDeVinculos(personagem, null)

        assertNull(vm.estado.value.escolhendoVinculos)
    }

    @Test
    fun `V8 abrir, marcar ate o limite, limpar e cancelar`() = runTest {
        val (vm, _) = montar()
        vm.aoAbrirPainel(); advanceUntilIdle()

        vm.abrirEscolhaDeVinculos(criatura, null)
        assertEquals(EscolhaDeVinculos(1, null, emptySet()), vm.estado.value.escolhendoVinculos)
        vm.alternarVinculo(200); vm.alternarVinculo(300)
        assertEquals(setOf(200, 300), vm.estado.value.escolhendoVinculos?.marcados)
        vm.alternarVinculo(200)
        assertEquals(setOf(300), vm.estado.value.escolhendoVinculos?.marcados)
        vm.limparVinculos()
        assertTrue(vm.estado.value.escolhendoVinculos!!.marcados.isEmpty())
        vm.fecharEscolhaDeVinculos()
        assertNull(vm.estado.value.escolhendoVinculos)
        assertTrue("cancelar não guarda nada", vm.estado.value.vinculosDoRetrato.isEmpty())
    }

    @Test
    fun `V8 sem o frame, usar so guarda a escolha, e ela vai junto da criacao do retrato`() = runTest {
        val (vm, repositorio) = montar()
        vm.aoAbrirPainel(); advanceUntilIdle()
        vm.abrirEscolhaDeVinculos(criatura, null)
        vm.alternarVinculo(200); vm.alternarVinculo(300)

        vm.usarVinculos(criatura); advanceUntilIdle()

        assertNull(vm.estado.value.escolhendoVinculos)
        assertEquals(listOf(200, 300), vm.estado.value.vinculosDoRetrato[1]?.map { it.estado_id })
        assertEquals(listOf("Prato", "Manto"), vm.estado.value.vinculosDoRetrato[1]?.map { it.nome })
        assertTrue("nada foi ao servidor ainda", repositorio.vinculosDefinidos.isEmpty())

        vm.gerarRetrato(criatura); advanceUntilIdle()

        assertEquals(listOf(listOf(200, 300)), repositorio.vinculadosNaCriacao)
    }

    @Test
    fun `V3 sem escolha o retrato e criado sem vinculados`() = runTest {
        val (vm, repositorio) = montar()
        vm.aoAbrirPainel(); advanceUntilIdle()

        vm.gerarRetrato(criatura); advanceUntilIdle()

        assertEquals(listOf(emptyList<Int>()), repositorio.vinculadosNaCriacao)
    }

    @Test
    fun `V4 V7 com o frame existente usar manda o PUT e avisa que e preciso um novo prompt`() = runTest {
        val (vm, repositorio) = montar()
        vm.aoAbrirPainel(); advanceUntilIdle()
        vm.abrirEscolhaDeVinculos(criatura, frameId = 80)
        vm.alternarVinculo(200)

        vm.usarVinculos(criatura); advanceUntilIdle()

        assertEquals(listOf(80 to listOf(200)), repositorio.vinculosDefinidos)
        assertEquals(listOf(200), vm.estado.value.vinculosDoRetrato[1]?.map { it.estado_id })
        assertTrue(1 in vm.estado.value.vinculosPendentesDePrompt)
    }

    @Test
    fun `V4 a recusa do servidor aparece no recado e nada muda`() = runTest {
        val (vm, repositorio) = montar()
        repositorio.resultadoDeDefinirVinculos = ResultadoDaChamada.Falha("Auri é um personagem e fica individual")
        vm.aoAbrirPainel(); advanceUntilIdle()
        vm.abrirEscolhaDeVinculos(criatura, frameId = 80)
        vm.alternarVinculo(200)

        vm.usarVinculos(criatura); advanceUntilIdle()

        assertTrue(vm.estado.value.vinculosDoRetrato.isEmpty())
        assertTrue(vm.estado.value.vinculosPendentesDePrompt.isEmpty())
        assertTrue(vm.estado.value.mensagensDeRetrato[1]?.ehErro == true)
    }

    @Test
    fun `V8 le do servidor uma vez os vinculados de um retrato que ja existe`() = runTest {
        val (vm, repositorio) = montar()
        repositorio.vinculosNoServidor = ResultadoDaChamada.Sucesso(listOf(VinculadoDoFrame(estado_id = 200, nome = "Prato")))
        vm.aoAbrirPainel(); advanceUntilIdle()

        vm.carregarVinculosDoRetrato(1, 80); advanceUntilIdle()
        vm.carregarVinculosDoRetrato(1, 80); advanceUntilIdle()

        assertEquals(listOf(80), repositorio.leiturasDeVinculos)
        assertEquals(listOf("Prato"), vm.estado.value.vinculosDoRetrato[1]?.map { it.nome })
        assertTrue("só ler não pede um novo prompt", vm.estado.value.vinculosPendentesDePrompt.isEmpty())
    }

    @Test
    fun `V8 reabrir o modal mostra os vinculados atuais marcados`() = runTest {
        val (vm, _) = montar()
        vm.aoAbrirPainel(); advanceUntilIdle()
        vm.abrirEscolhaDeVinculos(criatura, null)
        vm.alternarVinculo(300)
        vm.usarVinculos(criatura); advanceUntilIdle()

        vm.abrirEscolhaDeVinculos(criatura, null)

        assertEquals(setOf(300), vm.estado.value.escolhendoVinculos?.marcados)
    }
}

/** O pedido atravessando Retrofit + OkHttp de verdade (V4). */
class VinculosPelaRedeTest {

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

    private fun repositorio() =
        RepositorioDeSugestoesPeloRetrofit(ProvedorDeApi(ArmazenamentoFalso(servidor.url("/").toString().trimEnd('/'))))

    private fun json(corpo: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(corpo)

    @Test
    fun `V4 criar o retrato leva estados_vinculados_ids so quando ha escolha`() = runTest {
        servidor.enqueue(json("""{"id":80,"titulo":"Retrato de Foxen"}"""))
        servidor.enqueue(json("""{"id":81,"titulo":"Retrato de Foxen"}"""))

        repositorio().criarRetrato(5, 100, listOf(200, 300))
        repositorio().criarRetrato(5, 100)

        assertEquals("""{"tipo":"PERSONAGEM","estados_ids":[100],"estados_vinculados_ids":[200,300]}""", servidor.takeRequest().body.readUtf8())
        assertEquals("""{"tipo":"PERSONAGEM","estados_ids":[100]}""", servidor.takeRequest().body.readUtf8())
    }

    @Test
    fun `V4 o PUT substitui a lista e le os vinculados da resposta`() = runTest {
        servidor.enqueue(json("""{"id":80,"vinculados":[{"estado_id":200,"elemento_id":20,"nome":"Prato","tipo":"OBJETO","descricao":"x","capitulo_id":5}]}"""))

        val resultado = (repositorio().definirVinculos(80, listOf(200)) as ResultadoDaChamada.Sucesso).dado

        val pedido = servidor.takeRequest()
        assertEquals("PUT", pedido.method)
        assertEquals("/frames/80/vinculos", pedido.path)
        assertEquals("""{"estados_ids":[200]}""", pedido.body.readUtf8())
        assertEquals(listOf("Prato"), resultado.map { it.nome })
    }

    @Test
    fun `V4 ler o frame traz os vinculados, e um servidor antigo sem o campo traz a lista vazia`() = runTest {
        servidor.enqueue(json("""{"id":80,"tipo":"PERSONAGEM","titulo":"x","elementos":[],"vinculados":[{"estado_id":300,"nome":"Manto","tipo":"AMBIENTE"}]}"""))
        servidor.enqueue(json("""{"id":81,"tipo":"PERSONAGEM","titulo":"x","elementos":[]}"""))

        assertEquals(listOf("Manto"), (repositorio().vinculosDoFrame(80) as ResultadoDaChamada.Sucesso).dado.map { it.nome })
        assertTrue((repositorio().vinculosDoFrame(81) as ResultadoDaChamada.Sucesso).dado.isEmpty())
        assertEquals("/frames/80", servidor.takeRequest().path)
    }

    @Test
    fun `V4 a mensagem do 422 do servidor chega como esta`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(422).setHeader("Content-Type", "application/json").setBody("""{"detail":"Auri é um personagem e fica individual"}"""))

        val falha = repositorio().definirVinculos(80, listOf(400)) as ResultadoDaChamada.Falha

        assertTrue(falha.motivo.contains("individual"))
    }
}
