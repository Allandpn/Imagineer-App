package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.dados.LeitorDeArquivos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/** A importação de imagem atravessando Retrofit + OkHttp de verdade, contra um servidor falso (J3, J5). */
class ImportarImagemPelaRedeTest {

    private lateinit var servidor: MockWebServer
    private val bytes = ByteArray(120_000) { (it % 251).toByte() }

    private class LeitorFalso(private val bytes: ByteArray, private val abre: Boolean = true) : LeitorDeArquivos {
        override fun descrever(uri: String): ArquivoEscolhido? = null
        override fun abrir(uri: String): InputStream? = if (abre) ByteArrayInputStream(bytes) else null
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

    private fun repositorio(leitor: LeitorFalso = LeitorFalso(bytes)) =
        RepositorioDePromptsPeloRetrofit(ProvedorDeApi(ArmazenamentoFalso(servidor.url("/").toString().trimEnd('/'))), leitor)

    private val arquivo = ArquivoEscolhido("content://x/a.png", "a.png", 120_000)

    @Test
    fun `J3 manda o multipart com o campo arquivo, o nome e os bytes inteiros, e le a resposta`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json")
                .setBody("""{"id":9,"prompt_id":4,"tamanho_em_bytes":120000,"largura":1024,"altura":1536,"orientacao":"RETRATO","data_importacao":"2026-10-01T10:00:00"}"""),
        )
        var ultimoProgresso = 0L

        val resultado = repositorio().importarImagem(4, arquivo) { enviados, _ -> ultimoProgresso = enviados }

        val imagem = (resultado as ResultadoDaChamada.Sucesso).dado
        assertEquals(9, imagem.id)
        assertEquals("RETRATO", imagem.orientacao)
        assertEquals(120_000L, ultimoProgresso)

        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/prompts/4/imagens", pedido.path)
        val corpo = pedido.body.readUtf8()
        assertTrue(corpo.contains("""name="arquivo"; filename="a.png""""))
        assertTrue(corpo.contains("Content-Type: image/png"))
    }

    @Test
    fun `J3 a mensagem do servidor chega como esta`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(422).setHeader("Content-Type", "application/json")
                .setBody("""{"detail":"Extensão .bmp não suportada."}"""),
        )

        val resultado = repositorio().importarImagem(4, arquivo) { _, _ -> }

        val falha = resultado as ResultadoDaChamada.Falha
        assertEquals(422, falha.codigoHttp)
        assertEquals("Extensão .bmp não suportada.", falha.motivo)
    }

    @Test
    fun `arquivo que nao abre falha antes de qualquer pedido`() = runTest {
        val resultado = repositorio(LeitorFalso(bytes, abre = false)).importarImagem(4, arquivo) { _, _ -> }

        assertEquals("Não consegui abrir o arquivo escolhido.", (resultado as ResultadoDaChamada.Falha).motivo)
        assertEquals(0, servidor.requestCount)
    }

    @Test
    fun `J5 o detalhe do prompt traz as imagens`() = runTest {
        servidor.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":4,"frame_id":70,"texto":"t","total_de_imagens":1,"referencias_visuais":[],"imagens":[{"id":9,"prompt_id":4,"largura":10,"altura":20,"orientacao":"RETRATO","data_importacao":"x"}]}""",
            ),
        )

        val resultado = repositorio().detalhar(4)

        assertEquals(listOf(9), (resultado as ResultadoDaChamada.Sucesso).dado.imagens.map { it.id })
        assertEquals("/prompts/4", servidor.takeRequest().path)
    }

    private val respostaGerada = """{"resultado":"GERADA","suavizado":true,"prompt":{"id":8,"frame_id":70,"texto":"suave","modelo_ia":"m","situacao_da_geracao":"COM_SUCESSO","prompt_original_id":4,"total_de_imagens":1},"imagem":{"id":21,"prompt_id":8,"largura":1024,"altura":1536,"orientacao":"RETRATO","data_importacao":"x"}}"""

    @Test
    fun `K1 gerar a imagem manda corpo vazio e le o desfecho`() = runTest {
        servidor.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(respostaGerada))

        val resultado = repositorio().gerarImagem(4)

        val geracao = (resultado as ResultadoDaChamada.Sucesso).dado
        assertTrue(geracao.gerada)
        assertTrue(geracao.suavizado)
        assertEquals(8, geracao.prompt.id)
        assertEquals(4, geracao.prompt.prompt_original_id)
        assertEquals("COM_SUCESSO", geracao.prompt.situacao_da_geracao)
        assertEquals(21, geracao.imagem?.id)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/prompts/4/gerar-imagem", pedido.path)
        assertEquals("{}", pedido.body.readUtf8()) // sem texto editado: o servidor segue o fluxo normal
    }

    @Test
    fun `K4 o texto editado vai no corpo`() = runTest {
        servidor.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(respostaGerada))

        repositorio().gerarImagem(4, "close-up, Auri, wrapped in linen")

        assertEquals("""{"texto":"close-up, Auri, wrapped in linen"}""", servidor.takeRequest().body.readUtf8())
    }

    @Test
    fun `K4 recusa e um desfecho de 200, com o motivo e sem imagem`() = runTest {
        servidor.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"resultado":"RECUSADA","suavizado":true,"prompt":{"id":8,"frame_id":70,"texto":"suave","situacao_da_geracao":"RECUSADO","motivo_da_recusa":"content management policy","prompt_original_id":4},"imagem":null}""",
            ),
        )

        val geracao = (repositorio().gerarImagem(4) as ResultadoDaChamada.Sucesso).dado

        assertTrue(!geracao.gerada)
        assertEquals("RECUSADO", geracao.prompt.situacao_da_geracao)
        assertEquals("content management policy", geracao.prompt.motivo_da_recusa)
        assertEquals(null, geracao.imagem)
    }

    @Test
    fun `K7 a mensagem do servidor chega como esta`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(422).setHeader("Content-Type", "application/json")
                .setBody("""{"detail":"Nenhum modelo de imagem foi escolhido."}"""),
        )

        val falha = repositorio().gerarImagem(4) as ResultadoDaChamada.Falha

        assertEquals(422, falha.codigoHttp)
        assertEquals("Nenhum modelo de imagem foi escolhido.", falha.motivo)
    }

    @Test
    fun `T3 a origem da imagem vem do servidor, e importada e o padrao`() = runTest {
        servidor.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":4,"frame_id":70,"texto":"t","total_de_imagens":2,"imagens":[{"id":9,"prompt_id":4,"origem":"GERADA"},{"id":10,"prompt_id":4}]}""",
            ),
        )

        val imagens = (repositorio().detalhar(4) as ResultadoDaChamada.Sucesso).dado.imagens

        assertEquals(listOf("GERADA", "IMPORTADA"), imagens.map { it.origem })
    }

    @Test
    fun `U4 baixar a imagem grava o arquivo inteiro e devolve o tipo que o servidor informou`() = runTest {
        val bytes = ByteArray(50_000) { (it % 251).toByte() }
        servidor.enqueue(MockResponse().setHeader("Content-Type", "image/webp").setBody(okio.Buffer().write(bytes)))
        val destino = java.io.File.createTempFile("imagem", ".baixando")

        val resultado = repositorio().baixarImagem(21, destino)

        assertEquals("image/webp", (resultado as ResultadoDaChamada.Sucesso).dado)
        assertTrue(bytes.contentEquals(destino.readBytes()))
        assertEquals("/imagens/21/arquivo?tamanho=original", servidor.takeRequest().path)
        destino.delete()
    }

    @Test
    fun `U4 baixar imagem que nao existe e falha, e nao deixa arquivo`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody("""{"detail":"Imagem 21 não encontrada."}"""))
        val destino = java.io.File.createTempFile("imagem", ".baixando").also { it.delete() }

        val falha = repositorio().baixarImagem(21, destino) as ResultadoDaChamada.Falha

        assertEquals(404, falha.codigoHttp)
        assertTrue(!destino.exists())
    }

    @Test
    fun `U3 remover manda DELETE e aceita 204`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(204))

        val resultado = repositorio().removerImagem(21)

        assertTrue(resultado is ResultadoDaChamada.Sucesso)
        val pedido = servidor.takeRequest()
        assertEquals("DELETE", pedido.method)
        assertEquals("/imagens/21", pedido.path)
    }

    @Test
    fun `U3 imagem que ja nao existe conta como apagada`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody("""{"detail":"Imagem 21 não encontrada."}"""))

        assertTrue(repositorio().removerImagem(21) is ResultadoDaChamada.Sucesso)
    }

    @Test
    fun `U3 outro erro do servidor e falha`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(500))

        assertTrue(repositorio().removerImagem(21) is ResultadoDaChamada.Falha)
    }
}
