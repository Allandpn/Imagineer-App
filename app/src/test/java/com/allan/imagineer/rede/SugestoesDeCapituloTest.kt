package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Os JSONs abaixo foram gerados pelo backend de verdade (`GET` e `POST
 * /capitulos/{id}/sugestoes` num `TestClient`), não escritos à mão: um capítulo nunca
 * analisado, o resultado de uma análise, e a leitura seguinte depois de cadastrar o
 * personagem "Jon" (que produz o casamento automático).
 */
class SugestoesDeCapituloTest {

    private val nuncaAnalisado =
        """{"gerado_em":null,"sugestoes_pendentes_anteriores":0,"elementos":[],"cenas":[]}"""

    private val analisado = """{"gerado_em":"2026-09-30T20:40:38.377443","sugestoes_pendentes_anteriores":0,"elementos":[{"id":1,"tipo":"PERSONAGEM","nome":"Jon","descricao":"Um bastardo do norte.","manter_estado_atual":false,"elemento_id":null,"casamento_automatico":false,"estado_id":null,"modelo":"falso/modelo"},{"id":2,"tipo":"AMBIENTE","nome":"Winterfell","descricao":"A fortaleza dos Stark.","manter_estado_atual":true,"elemento_id":null,"casamento_automatico":false,"estado_id":null,"modelo":"falso/modelo"}],"cenas":[{"id":1,"titulo":"A partida no pátio","descricao":"Jon observa o pátio coberto de neve.","horario":"fim de tarde","clima":"nevasca leve","humor":"tenso","participantes":[{"sugestao_elemento_id":1,"tipo":"PERSONAGEM","nome":"Jon","elemento_id":null,"casamento_automatico":false,"estado_id":null},{"sugestao_elemento_id":2,"tipo":"AMBIENTE","nome":"Winterfell","elemento_id":null,"casamento_automatico":false,"estado_id":null}],"modelo":"falso/modelo"}]}"""

    private val casado = """{"gerado_em":"2026-09-30T20:40:38.377443","sugestoes_pendentes_anteriores":0,"elementos":[{"id":1,"tipo":"PERSONAGEM","nome":"Jon","descricao":"Um bastardo do norte.","manter_estado_atual":false,"elemento_id":1,"casamento_automatico":true,"estado_id":null,"modelo":"falso/modelo"}],"cenas":[{"id":1,"titulo":"A partida no pátio","descricao":"Jon observa o pátio coberto de neve.","horario":"fim de tarde","clima":"nevasca leve","humor":"tenso","participantes":[{"sugestao_elemento_id":1,"tipo":"PERSONAGEM","nome":"Jon","elemento_id":1,"casamento_automatico":true,"estado_id":null}],"modelo":"falso/modelo"}]}"""

    @Test
    fun `capitulo nunca analisado tem gerado_em nulo e listas vazias`() {
        val sugestoes = jsonDoImagineer.decodeFromString<SugestoesDeCapitulo>(nuncaAnalisado)

        assertNull(sugestoes.gerado_em)
        assertTrue(sugestoes.elementos.isEmpty())
        assertTrue(sugestoes.cenas.isEmpty())
    }

    @Test
    fun `desserializa o resultado de uma analise`() {
        val sugestoes = jsonDoImagineer.decodeFromString<SugestoesDeCapitulo>(analisado)

        assertEquals("2026-09-30T20:40:38.377443", sugestoes.gerado_em)
        assertEquals(2, sugestoes.elementos.size)
        assertEquals("PERSONAGEM", sugestoes.elementos[0].tipo)
        assertEquals("Um bastardo do norte.", sugestoes.elementos[0].descricao)
        assertEquals(true, sugestoes.elementos[1].manter_estado_atual)
        assertNull(sugestoes.elementos[0].elemento_id)
    }

    @Test
    fun `desserializa a cena com horario, clima, humor e participantes`() {
        val cena = jsonDoImagineer.decodeFromString<SugestoesDeCapitulo>(analisado).cenas.single()

        assertEquals("A partida no pátio", cena.titulo)
        assertEquals("fim de tarde", cena.horario)
        assertEquals("nevasca leve", cena.clima)
        assertEquals("tenso", cena.humor)
        assertEquals(listOf("Jon", "Winterfell"), cena.participantes.map { it.nome })
        assertEquals(listOf("PERSONAGEM", "AMBIENTE"), cena.participantes.map { it.tipo })
    }

    @Test
    fun `o casamento automatico chega ao elemento e ao participante`() {
        val sugestoes = jsonDoImagineer.decodeFromString<SugestoesDeCapitulo>(casado)

        val jon = sugestoes.elementos.single()
        assertEquals(1, jon.elemento_id)
        assertEquals(true, jon.casamento_automatico)
        assertNull(jon.estado_id) // casado, mas ainda sem Estado neste capítulo
        assertEquals(true, sugestoes.cenas.single().participantes.single().casamento_automatico)
    }

    @Test
    fun `um tipo de elemento novo no backend nao quebra a leitura`() {
        // O tipo é texto, e não enum: o backend pode ganhar um tipo sem o app antigo quebrar.
        val comTipoNovo = analisado.replace("\"tipo\":\"AMBIENTE\"", "\"tipo\":\"PLANETA\"")

        val sugestoes = jsonDoImagineer.decodeFromString<SugestoesDeCapitulo>(comTipoNovo)

        assertEquals("PLANETA", sugestoes.elementos[1].tipo)
    }
}

/** O repositório de verdade, contra um servidor HTTP falso — incluindo o tempo de espera da análise. */
class RepositorioDeSugestoesPelaRedeTest {

    private lateinit var servidor: MockWebServer

    private class ArmazenamentoFalso(url: String) : ArmazenamentoDeConfiguracao {
        override val urlDoServidor: Flow<String?> = flowOf(url)
        override suspend fun salvarUrlDoServidor(url: String) = Unit
    }

    private val vazio = """{"gerado_em":null,"sugestoes_pendentes_anteriores":0,"elementos":[],"cenas":[]}"""
    private val pronto = """{"gerado_em":"2026-09-30T20:40:38","sugestoes_pendentes_anteriores":0,"elementos":[],"cenas":[]}"""

    @Before
    fun subir() {
        servidor = MockWebServer().apply { start() }
    }

    @After
    fun derrubar() {
        servidor.shutdown()
    }

    private fun url() = servidor.url("/").toString().trimEnd('/')

    private fun repositorio() = RepositorioDeSugestoesPeloRetrofit(ProvedorDeApi(ArmazenamentoFalso(url())))

    @Test
    fun `ler e um GET, no caminho certo, e nunca pede tempo de espera maior`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(vazio))

        val resultado = repositorio().ler(5)

        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/capitulos/5/sugestoes", pedido.path)
        assertTrue(resultado is ResultadoDaChamada.Sucesso)
    }

    @Test
    fun `analisar e um POST com forcar falso`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(pronto))

        repositorio().analisar(5, forcar = false)

        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/capitulos/5/sugestoes?forcar=false", pedido.path)
    }

    @Test
    fun `reanalisar manda forcar verdadeiro`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(pronto))

        repositorio().analisar(5, forcar = true)

        assertEquals("/capitulos/5/sugestoes?forcar=true", servidor.takeRequest().path)
    }

    @Test
    fun `o cabecalho do tempo de espera nao chega ao servidor`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(pronto))

        repositorio().analisar(5, forcar = false)

        // O interceptador o lê e o remove: é um detalhe do app, que o servidor nunca deve ver.
        assertNull(servidor.takeRequest().getHeader(CABECALHO_TEMPO_DE_ESPERA))
    }

    @Test
    fun `a analise espera mais que a leitura comum, que continua falhando depressa`() = runTest {
        // Tempo de espera comum de 1 s; o servidor demora 2 s para responder.
        val api = criarApi(url(), leituraPadraoEmSegundos = 1)

        servidor.enqueue(MockResponse().setResponseCode(200).setBody(pronto).setBodyDelay(2, TimeUnit.SECONDS))
        val analise = api.analisar(5, forcar = false) // pediu 180 s: espera e recebe
        assertEquals("2026-09-30T20:40:38", analise.gerado_em)

        servidor.enqueue(MockResponse().setResponseCode(200).setBody(vazio).setBodyDelay(2, TimeUnit.SECONDS))
        try {
            api.sugestoes(5) // leitura comum: 1 s, e o servidor demora 2 s
            fail("a leitura comum deveria ter estourado o tempo de espera")
        } catch (esperado: IOException) {
            // Falha depressa, como todas as outras chamadas do app.
        }
    }

    @Test
    fun `erro 422 de configuracao mostra a mensagem da API`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(422)
                .setBody("""{"detail":"Não há chave de API do OpenRouter configurada."}"""),
        )

        val resultado = repositorio().analisar(5, forcar = false)

        assertEquals(
            ResultadoDaChamada.Falha("Não há chave de API do OpenRouter configurada.", codigoHttp = 422),
            resultado,
        )
    }

    @Test
    fun `erro 502 do provedor mostra a mensagem da API`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(502).setBody("""{"detail":"O OpenRouter não respondeu no tempo esperado."}"""),
        )

        val resultado = repositorio().analisar(5, forcar = false)

        assertEquals(
            ResultadoDaChamada.Falha("O OpenRouter não respondeu no tempo esperado.", codigoHttp = 502),
            resultado,
        )
    }

    @Test
    fun `capitulo que nao existe responde 404 com a mensagem`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"Não existe capítulo com id 999."}"""))

        val resultado = repositorio().ler(999)

        assertEquals(
            ResultadoDaChamada.Falha("Não existe capítulo com id 999.", codigoHttp = 404),
            resultado,
        )
    }
}
