package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.local.ArmazemEmMemoria
import com.allan.imagineer.local.IndiceEmMemoria
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

/**
 * O JSON abaixo foi gerado pelo backend de verdade (a resposta de `/capitulos/{id}`,
 * que é um `CapituloDetalhe`), não escrito à mão. O texto traz o `\n\n` que separa
 * parágrafos, escrito como escape JSON.
 */
class CapituloDetalheTest {

    private val jsonReal = """
        {"id":1,"ordem":1,"titulo":"Bran","ignorado":false,"tamanho_do_texto":203,"sugestoes_pendentes":0,"livro_id":1,"texto":"Bran\n\nEste é um parágrafo com texto suficiente para não ser descartado.\n\nSegundo parágrafo."}
    """.trimIndent()

    @Test
    fun `desserializa o capitulo real, com texto e livro`() {
        val capitulo = jsonDoImagineer.decodeFromString<CapituloDetalhe>(jsonReal)

        assertEquals(1, capitulo.id)
        assertEquals("Bran", capitulo.titulo)
        assertEquals(1, capitulo.livro_id)
        assertEquals(false, capitulo.ignorado)
        assertTrue(capitulo.texto.startsWith("Bran\n\nEste é um parágrafo"))
    }

    @Test
    fun `titulo nulo vira null`() {
        val semTitulo = jsonReal.replace(""""titulo":"Bran"""", """"titulo":null""")

        assertEquals(null, jsonDoImagineer.decodeFromString<CapituloDetalhe>(semTitulo).titulo)
    }
}

/** O repositório de verdade, contra um servidor HTTP falso. */
class RepositorioDeCapitulosLeituraPelaRedeTest {

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
        IndiceEmMemoria(),
        ArmazemEmMemoria(),
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job()),
    )

    @Test
    fun `abrir um capitulo chama GET no caminho certo e traz o texto`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":5,"ordem":2,"titulo":"Catelyn","ignorado":false,"tamanho_do_texto":9,"sugestoes_pendentes":0,"livro_id":1,"texto":"Um.\n\nDois."}""",
            ),
        )

        val resultado = repositorio().abrirCapitulo(5)

        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/capitulos/5", pedido.path)
        assertEquals("Um.\n\nDois.", (resultado as ResultadoDaChamada.Sucesso).dado.texto)
    }

    @Test
    fun `capitulo que nao existe mostra a mensagem do backend`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(404).setBody("""{"detail":"Não existe capítulo com id 999."}"""),
        )

        val resultado = repositorio().abrirCapitulo(999)

        assertEquals(
            ResultadoDaChamada.Falha("Não existe capítulo com id 999.", codigoHttp = 404),
            resultado,
        )
    }
}
