package com.allan.imagineer.telas.elementos

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.CenaDoElemento
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.GaleriaDoElemento
import com.allan.imagineer.rede.ImagemDoElemento
import com.allan.imagineer.rede.ProvedorDeApi
import com.allan.imagineer.rede.RepositorioDeElementosPeloRetrofit
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.capitulo.painel.ElementosFalso
import com.allan.imagineer.telas.livro.LivrosFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private fun imagem(id: Int, ancora: Boolean = false, titulo: String? = "Capítulo VI", ordem: Int? = 6) =
    ImagemDoElemento(id = id, titulo_do_capitulo = titulo, ordem_do_capitulo = ordem, ancora = ancora)

private fun livroQualquer() = LivroDetalhe(
    id = 4, titulo = "Livro", nome_arquivo = "l.epub", data_importacao = "2026-09-30T00:00:00",
    total_de_capitulos = 1, capitulos_ignorados = 0,
    capitulos = listOf(CapituloResumo(id = 11, ordem = 1, titulo = "Capítulo I", ignorado = false, tamanho_do_texto = 5)),
)

private fun cena(imagemId: Int? = 9, total: Int = 1, participantes: List<String> = listOf("Prato")) = CenaDoElemento(
    frame_id = 3, titulo = "No pátio", titulo_do_capitulo = null, ordem_do_capitulo = 4,
    participantes = participantes, total_de_imagens = total, imagem_id = imagemId,
)

/** As regras puras da galeria da ficha (FI5, FI6). */
class RegrasDaGaleriaTest {

    @Test
    fun `FI5 a legenda diz o capitulo, e a principal leva o selo`() {
        assertEquals("Capítulo VI", legendaDaImagemDoElemento(imagem(1)))
        assertEquals("Capítulo VI · referência principal", legendaDaImagemDoElemento(imagem(1, ancora = true)))
        assertEquals("Capítulo 4", legendaDaImagemDoElemento(imagem(1, titulo = null, ordem = 4)))
    }

    @Test
    fun `FI5 a linha de participantes so existe quando ha outros`() {
        assertEquals("Com Prato, Manto", linhaDeParticipantes(cena(participantes = listOf("Prato", "Manto"))))
        assertNull(linhaDeParticipantes(cena(participantes = emptyList())))
    }

    @Test
    fun `FI3 FI5 a situacao da imagem da cena`() {
        assertEquals("ainda sem imagem", situacaoDaImagemDaCena(cena(imagemId = null, total = 0)))
        assertEquals("1 imagem", situacaoDaImagemDaCena(cena(total = 1)))
        assertEquals("3 imagens", situacaoDaImagemDaCena(cena(total = 3)))
    }

    @Test
    fun `FI5 o resumo da cena junta o capitulo e a situacao`() {
        assertEquals("Capítulo 4 · 2 imagens", resumoDaCena(cena(total = 2)))
        assertEquals("Capítulo 4 · ainda sem imagem", resumoDaCena(cena(imagemId = null, total = 0)))
    }

    @Test
    fun `FI6 so a que ainda nao e a principal pode virar a principal`() {
        assertTrue(podeSerReferenciaPrincipal(imagem(1)))
        assertFalse(podeSerReferenciaPrincipal(imagem(1, ancora = true)))
    }
}

/** O ViewModel da ficha e a galeria (FI1, FI5 a FI7). */
@OptIn(ExperimentalCoroutinesApi::class)
class GaleriaNaFichaTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val galeria = GaleriaDoElemento(imagens = listOf(imagem(2, ancora = true), imagem(1)), cenas = listOf(cena()))

    private fun montar(falso: ElementosFalso = ElementosFalso()) =
        FichaDoElementoViewModel(30, null, falso, LivrosFalso(ResultadoDaChamada.Sucesso(livroQualquer()))) to falso

    @Test
    fun `FI1 abrir a ficha le tambem a galeria`() = runTest {
        val (vm, falso) = montar(ElementosFalso().also { it.galeriaDoElemento = ResultadoDaChamada.Sucesso(galeria) })

        vm.carregar(); advanceUntilIdle()

        assertEquals(CargaDaGaleria.Pronta(galeria), vm.estado.value.galeria)
        assertEquals(1, falso.leiturasDaGaleria)
    }

    @Test
    fun `FI5 uma falha da galeria aparece so na secao e a ficha continua`() = runTest {
        val (vm, _) = montar(ElementosFalso().also { it.galeriaDoElemento = ResultadoDaChamada.Falha("sem conexão") })

        vm.carregar(); advanceUntilIdle()

        assertEquals(CargaDaGaleria.Erro("sem conexão"), vm.estado.value.galeria)
        assertTrue("a ficha em si carregou", vm.estado.value.carga is CargaDaFicha.Pronta)
    }

    @Test
    fun `FI5 tentar de novo le a galeria outra vez`() = runTest {
        val falso = ElementosFalso().also { it.galeriaDoElemento = ResultadoDaChamada.Falha("sem conexão") }
        val (vm, _) = montar(falso)
        vm.carregar(); advanceUntilIdle()

        falso.galeriaDoElemento = ResultadoDaChamada.Sucesso(galeria)
        vm.tentarDeNovoAGaleria(); advanceUntilIdle()

        assertEquals(CargaDaGaleria.Pronta(galeria), vm.estado.value.galeria)
    }

    @Test
    fun `FI7 uma releitura que falha nao apaga a galeria que ja estava na tela`() = runTest {
        val falso = ElementosFalso().also { it.galeriaDoElemento = ResultadoDaChamada.Sucesso(galeria) }
        val (vm, _) = montar(falso)
        vm.carregar(); advanceUntilIdle()

        falso.galeriaDoElemento = ResultadoDaChamada.Falha("fora do ar")
        vm.definirReferenciaPrincipal(1); advanceUntilIdle()

        assertEquals(CargaDaGaleria.Pronta(galeria), vm.estado.value.galeria)
    }

    @Test
    fun `FI6 usar como referencia principal faz o PATCH e relê a galeria para o selo mudar`() = runTest {
        val falso = ElementosFalso().also { it.galeriaDoElemento = ResultadoDaChamada.Sucesso(galeria) }
        val (vm, _) = montar(falso)
        vm.carregar(); advanceUntilIdle()
        val leiturasAntes = falso.leiturasDaGaleria

        falso.galeriaDoElemento = ResultadoDaChamada.Sucesso(GaleriaDoElemento(imagens = listOf(imagem(2), imagem(1, ancora = true))))
        vm.definirReferenciaPrincipal(1); advanceUntilIdle()

        assertEquals(listOf(1), falso.ancorasPedidas)
        assertEquals(leiturasAntes + 1, falso.leiturasDaGaleria)
        val atual = (vm.estado.value.galeria as CargaDaGaleria.Pronta).galeria
        assertEquals(listOf(1), atual.imagens.filter { it.ancora }.map { it.id })
        assertNull(vm.estado.value.recadoDaGaleria)
    }

    @Test
    fun `FI6 a recusa do servidor aparece como recado, sem mudar a galeria`() = runTest {
        val falso = ElementosFalso().also {
            it.galeriaDoElemento = ResultadoDaChamada.Sucesso(galeria)
            it.respostaAoAjustarElemento = ResultadoDaChamada.Falha("Não existe imagem com id 1")
        }
        val (vm, _) = montar(falso)
        vm.carregar(); advanceUntilIdle()

        vm.definirReferenciaPrincipal(1); advanceUntilIdle()

        assertEquals("Não existe imagem com id 1", vm.estado.value.recadoDaGaleria)
        assertEquals(CargaDaGaleria.Pronta(galeria), vm.estado.value.galeria)
    }
}

/** A galeria e a capa atravessando Retrofit + OkHttp de verdade (FI1, FI4, FI6). */
class GaleriaPelaRedeTest {

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
        RepositorioDeElementosPeloRetrofit(ProvedorDeApi(ArmazenamentoFalso(servidor.url("/").toString().trimEnd('/'))))

    private fun json(corpo: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(corpo)

    @Test
    fun `FI1 le as imagens e as cenas`() = runTest {
        servidor.enqueue(
            json(
                """{"imagens":[{"id":12,"prompt_id":4,"frame_id":3,"capitulo_id":5,"titulo_do_capitulo":"VI","ordem_do_capitulo":6,"largura":1024,"altura":1536,"orientacao":"RETRATO","modelo":"x","origem":"GERADA","sem_filtro_de_seguranca":false,"data_importacao":"2026-10-02T10:00:00","ancora":true}],""" +
                    """"cenas":[{"frame_id":7,"titulo":"No pátio","descricao":null,"capitulo_id":5,"titulo_do_capitulo":null,"ordem_do_capitulo":4,"participantes":["Prato"],"total_de_imagens":2,"imagem_id":15,"imagem_orientacao":"PAISAGEM"}]}""",
            ),
        )

        val galeria = (repositorio().galeria(30) as ResultadoDaChamada.Sucesso).dado

        assertEquals("/elementos/30/galeria", servidor.takeRequest().path)
        assertEquals(listOf(12), galeria.imagens.map { it.id })
        assertTrue(galeria.imagens[0].ancora)
        assertEquals("RETRATO", galeria.imagens[0].orientacao)
        assertEquals(listOf("Prato"), galeria.cenas[0].participantes)
        assertEquals(15, galeria.cenas[0].imagem_id)
    }

    @Test
    fun `FI1 um servidor sem a rota nova responde 404 e vira falha, nao quebra`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody("""{"detail":"Not Found"}"""))

        assertTrue(repositorio().galeria(30) is ResultadoDaChamada.Falha)
    }

    @Test
    fun `FI4 a lista traz a capa, e servidor antigo sem o campo traz nulo`() = runTest {
        servidor.enqueue(
            json("""[{"id":1,"tipo":"CRIATURA","nome":"Foxen","total_de_estados":1,"imagem_de_capa_id":12},{"id":2,"tipo":"OBJETO","nome":"Prato","total_de_estados":0}]"""),
        )

        val lista = (repositorio().listar(4) as ResultadoDaChamada.Sucesso).dado

        assertEquals(12, lista[0].imagem_de_capa_id)
        assertNull(lista[1].imagem_de_capa_id)
    }

    @Test
    fun `FI6 definir a referencia principal manda so imagem_ancora_padrao_id`() = runTest {
        servidor.enqueue(json("""{"id":30,"tipo":"CRIATURA","nome":"Foxen","estados":[],"historico_identidade":[]}"""))

        repositorio().ajustarElemento(30, ancoraPadraoId = 12)

        val pedido = servidor.takeRequest()
        assertEquals("PATCH", pedido.method)
        assertEquals("/elementos/30", pedido.path)
        assertEquals("""{"imagem_ancora_padrao_id":12}""", pedido.body.readUtf8())
    }
}
