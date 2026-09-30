package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Os JSONs abaixo foram gerados pelo backend de verdade (`GET /livros/{id}` e
 * `PATCH /capitulos/{id}` num `TestClient`), não escritos à mão. O do `PATCH`
 * inclui `livro_id` e o `texto` completo, que o app não usa e precisa ignorar.
 */
class CapituloResumoTest {

    private val patchReal = """
        {"id":1,"ordem":1,"titulo":"Bran","ignorado":true,"tamanho_do_texto":203,"sugestoes_pendentes":0,"livro_id":1,"texto":"Bran\n\nEste é um parágrafo com texto suficiente para não ser descartado."}
    """.trimIndent()

    private val livroReal = """
        {"id":1,"titulo":"A Guerra dos Tronos","autor":"George R. R. Martin","idioma":"pt-BR","nome_arquivo":"primeiro.epub","data_importacao":"2026-09-30T01:41:08","total_de_capitulos":2,"capitulos_ignorados":1,"identificador_epub":"urn:isbn:9788580410150","perfil_renderizacao_padrao_id":null,"metadados_pendentes":[],"capitulos":[{"id":1,"ordem":1,"titulo":"Bran","ignorado":true,"tamanho_do_texto":203,"sugestoes_pendentes":0},{"id":2,"ordem":2,"titulo":"Catelyn","ignorado":false,"tamanho_do_texto":206,"sugestoes_pendentes":0}]}
    """.trimIndent()

    @Test
    fun `o PATCH de capitulo, com texto e tudo, vira um resumo`() {
        val capitulo = jsonDoImagineer.decodeFromString<CapituloResumo>(patchReal)

        assertEquals(1, capitulo.id)
        assertEquals("Bran", capitulo.titulo)
        assertEquals(true, capitulo.ignorado)
        assertEquals(203, capitulo.tamanho_do_texto)
    }

    @Test
    fun `GET livros por id traz os capitulos com o ignorado de cada um`() {
        val livro = jsonDoImagineer.decodeFromString<LivroDetalhe>(livroReal)

        assertEquals(1, livro.capitulos_ignorados)
        assertEquals(listOf(true, false), livro.capitulos.map { it.ignorado })
        assertNull(livro.perfil_renderizacao_padrao_id)
        assertEquals(emptyList<String>(), livro.metadados_pendentes)
    }

    @Test
    fun `o ajuste de capitulo omite o titulo nulo`() {
        assertEquals(
            """{"ignorado":true}""",
            jsonDoImagineer.encodeToString(CapituloAjuste(ignorado = true)),
        )
    }
}

/** O repositório de capítulos de verdade, contra um servidor HTTP falso. */
class RepositorioDeCapitulosPelaRedeTest {

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

    private fun repositorio() = RepositorioDeCapitulosPeloRetrofit(
        ProvedorDeApi(ArmazenamentoFalso(servidor.url("/").toString().trimEnd('/'))),
    )

    @Test
    fun `o PATCH vai no caminho certo com so o campo ignorado`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":1,"ordem":1,"titulo":"Bran","ignorado":true,"tamanho_do_texto":203,"sugestoes_pendentes":0,"livro_id":1,"texto":"..."}""",
            ),
        )

        val resultado = repositorio().ajustarCapitulo(1, CapituloAjuste(ignorado = true))

        val pedido = servidor.takeRequest()
        assertEquals("PATCH", pedido.method)
        assertEquals("/capitulos/1", pedido.path)
        assertEquals("""{"ignorado":true}""", pedido.body.readUtf8())
        assertEquals(true, (resultado as ResultadoDaChamada.Sucesso).dado.ignorado)
    }

    @Test
    fun `404 mostra a mensagem do backend`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(404).setBody("""{"detail":"Não existe capítulo com id 999."}"""),
        )

        val resultado = repositorio().ajustarCapitulo(999, CapituloAjuste(ignorado = true))

        assertEquals(
            ResultadoDaChamada.Falha("Não existe capítulo com id 999.", codigoHttp = 404),
            resultado,
        )
    }
}
