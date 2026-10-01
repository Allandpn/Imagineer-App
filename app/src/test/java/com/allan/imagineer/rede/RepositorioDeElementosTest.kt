package com.allan.imagineer.rede

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Método, caminho e corpo de cada rota do incremento 10a, contra um servidor HTTP falso. */
class RepositorioDeElementosTest {

    private lateinit var servidor: MockWebServer

    @Before
    fun subir() {
        servidor = MockWebServer().apply { start() }
    }

    @After
    fun derrubar() {
        servidor.shutdown()
    }

    private fun url() = servidor.url("/").toString().trimEnd('/')

    private fun repositorio() = RepositorioDeElementosPeloRetrofit(ProvedorDeApi(ArmazenamentoComUrl(url())))

    @Test
    fun `listar e um GET dos elementos do livro`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"id":30,"livro_id":4,"tipo":"PERSONAGEM","nome":"Jon","descricao":"Um bastardo","total_de_estados":2,"estado_vigente":null}]""",
            ),
        )

        val resultado = repositorio().listar(4)

        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/livros/4/elementos", pedido.path)
        assertEquals(
            listOf(ElementoDoLivro(30, "PERSONAGEM", "Jon", "Um bastardo", 2)),
            (resultado as ResultadoDaChamada.Sucesso).dado,
        )
    }

    @Test
    fun `criar manda tipo, nome, descricao e a sugestao`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"id":9,"livro_id":4,"tipo":"PERSONAGEM","nome":"Jon","estados":[]}"""),
        )

        val resultado = repositorio().criar(4, "PERSONAGEM", "Jon", "Um bastardo", sugestaoId = 12)

        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/livros/4/elementos", pedido.path)
        assertEquals(
            """{"tipo":"PERSONAGEM","nome":"Jon","descricao":"Um bastardo","sugestoes_elemento_ids":[12]}""",
            pedido.body.readUtf8(),
        )
        assertEquals(9, (resultado as ResultadoDaChamada.Sucesso).dado.id)
    }

    @Test
    fun `criar sem descricao nem manda o campo`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":9,"tipo":"PERSONAGEM","nome":"Jon"}"""))

        repositorio().criar(4, "PERSONAGEM", "Jon", descricao = "  ", sugestaoId = 12)

        assertEquals(
            """{"tipo":"PERSONAGEM","nome":"Jon","sugestoes_elemento_ids":[12]}""",
            servidor.takeRequest().body.readUtf8(),
        )
    }

    @Test
    fun `criar repetido traz o 409 com a mensagem da API`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(409).setBody("""{"detail":"Já existe um elemento do tipo PERSONAGEM chamado 'Jon' neste livro (id 9)."}"""),
        )

        val resultado = repositorio().criar(4, "PERSONAGEM", "Jon", null, 12)

        assertEquals(
            ResultadoDaChamada.Falha("Já existe um elemento do tipo PERSONAGEM chamado 'Jon' neste livro (id 9).", 409),
            resultado,
        )
    }

    @Test
    fun `registrar estado e um POST em estados-de-sugestoes`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """[{"id":70,"elemento_id":30,"capitulo_id":5,"descricao":"x","imagem_ancora_id":null,"data_criacao":"2026-09-30T20:00:00"}]""",
            ),
        )

        val resultado = repositorio().registrarEstado(30, 12)

        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/elementos/30/estados-de-sugestoes", pedido.path)
        assertEquals("""{"sugestoes_elemento_ids":[12]}""", pedido.body.readUtf8())
        assertTrue(resultado is ResultadoDaChamada.Sucesso)
    }

    private val elementoSugeridoJson =
        """{"id":12,"tipo":"PERSONAGEM","nome":"Jon","descricao":null,"manter_estado_atual":false,"elemento_id":30,"casamento_automatico":false,"estado_id":null,"modelo":"m"}"""

    @Test
    fun `ajustar casamento com um id manda o elemento_id`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(elementoSugeridoJson))

        val resultado = repositorio().ajustarCasamento(12, 30)

        val pedido = servidor.takeRequest()
        assertEquals("PATCH", pedido.method)
        assertEquals("/sugestoes-elemento/12", pedido.path)
        assertEquals("""{"elemento_id":30}""", pedido.body.readUtf8())
        assertTrue(resultado is ResultadoDaChamada.Sucesso)
    }

    @Test
    fun `desfazer manda elemento_id null explicito - o campo e obrigatorio no servidor`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(elementoSugeridoJson.replace("\"elemento_id\":30", "\"elemento_id\":null")))

        repositorio().ajustarCasamento(12, null)

        assertEquals("""{"elemento_id":null}""", servidor.takeRequest().body.readUtf8())
    }

    @Test
    fun `descartar manda so descartada - nunca junto de elemento_id`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(elementoSugeridoJson))

        repositorio().descartar(12, true)

        val pedido = servidor.takeRequest()
        assertEquals("PATCH", pedido.method)
        assertEquals("/sugestoes-elemento/12", pedido.path)
        assertEquals("""{"descartada":true}""", pedido.body.readUtf8())
    }

    @Test
    fun `restaurar manda descartada false`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(elementoSugeridoJson))

        repositorio().descartar(12, false)

        assertEquals("""{"descartada":false}""", servidor.takeRequest().body.readUtf8())
    }

    @Test
    fun `descartar uma sugestao ligada traz o 409 com a mensagem da API`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(409).setBody("""{"detail":"Esta sugestão está ligada a um elemento. Desfaça o casamento antes de descartá-la."}"""),
        )

        val resultado = repositorio().descartar(12, true)

        assertEquals(409, (resultado as ResultadoDaChamada.Falha).codigoHttp)
    }

    private val detalheJson = """
        {"id":30,"livro_id":4,"tipo":"PERSONAGEM","nome":"Jon","descricao":"Bastardo.","imagem_ancora_padrao_id":null,
         "estados":[{"id":70,"elemento_id":30,"capitulo_id":11,"ordem_do_capitulo":1,"titulo_do_capitulo":"Capítulo I","descricao":"manto preto","imagem_ancora_id":null,"data_criacao":"2026-09-30T20:00:00"}],
         "historico_identidade":[{"id":5,"elemento_id":30,"capitulo_id":13,"ordem_do_capitulo":3,"titulo_do_capitulo":"Capítulo III","descricao":"Lorde Comandante.","data_criacao":"2026-09-30T20:00:00"}]}
    """.trimIndent()

    @Test
    fun `detalhar le a ficha com estados e historico de identidade`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(detalheJson))

        val resultado = repositorio().detalhar(30)

        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/elementos/30", pedido.path)
        val ficha = (resultado as ResultadoDaChamada.Sucesso).dado
        assertEquals("Bastardo.", ficha.descricao)
        assertEquals(listOf(EstadoDoElemento(id = 70, capitulo_id = 11, ordem_do_capitulo = 1, titulo_do_capitulo = "Capítulo I", descricao = "manto preto")), ficha.estados)
        assertEquals(listOf(IdentidadeDoCapitulo(id = 5, capitulo_id = 13, ordem_do_capitulo = 3, titulo_do_capitulo = "Capítulo III", descricao = "Lorde Comandante.")), ficha.historico_identidade)
    }

    @Test
    fun `a ficha sem ordem do capitulo (servidor antigo) ainda le`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":30,"tipo":"PERSONAGEM","nome":"Jon","descricao":null,"estados":[{"id":70,"capitulo_id":11,"descricao":"x"}],"historico_identidade":[]}""",
            ),
        )

        val ficha = (repositorio().detalhar(30) as ResultadoDaChamada.Sucesso).dado

        assertEquals(null, ficha.estados.single().ordem_do_capitulo)
    }

    @Test
    fun `ajustar elemento manda so os campos que mudam`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(detalheJson))

        repositorio().ajustarElemento(30, nome = "Jon Snow")

        val pedido = servidor.takeRequest()
        assertEquals("PATCH", pedido.method)
        assertEquals("/elementos/30", pedido.path)
        assertEquals("""{"nome":"Jon Snow"}""", pedido.body.readUtf8())
    }

    @Test
    fun `ajustar elemento com tipo e identidade`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(detalheJson))

        repositorio().ajustarElemento(30, tipo = "CRIATURA", identidade = "Um lobo.")

        assertEquals("""{"tipo":"CRIATURA","descricao":"Um lobo."}""", servidor.takeRequest().body.readUtf8())
    }

    private val estadoJson = """{"id":71,"elemento_id":30,"capitulo_id":14,"descricao":"x","imagem_ancora_id":null,"data_criacao":"2026-09-30T20:00:00"}"""

    @Test
    fun `criar estado manda capitulo e descricao`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(201).setBody(estadoJson))

        repositorio().criarEstado(30, 14, "armadura")

        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/elementos/30/estados", pedido.path)
        assertEquals("""{"capitulo_id":14,"descricao":"armadura"}""", pedido.body.readUtf8())
    }

    @Test
    fun `ajustar estado e um PATCH da descricao`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(estadoJson))

        repositorio().ajustarEstado(71, "capa rasgada")

        val pedido = servidor.takeRequest()
        assertEquals("PATCH", pedido.method)
        assertEquals("/estados/71", pedido.path)
        assertEquals("""{"descricao":"capa rasgada"}""", pedido.body.readUtf8())
    }

    @Test
    fun `remover estado e um DELETE e 204 conta como sucesso`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(204))

        val resultado = repositorio().removerEstado(71)

        val pedido = servidor.takeRequest()
        assertEquals("DELETE", pedido.method)
        assertEquals("/estados/71", pedido.path)
        assertTrue(resultado is ResultadoDaChamada.Sucesso)
    }

    @Test
    fun `a sugestao casada le elemento_casado, estado_vigente e descartada do JSON do servidor`() {
        val json = """
            {"id":12,"tipo":"PERSONAGEM","nome":"Jon","descricao":"x","manter_estado_atual":false,"elemento_id":30,
             "casamento_automatico":false,"estado_id":null,
             "elemento_casado":{"id":30,"tipo":"PERSONAGEM","nome":"Jon Snow","identidade":"Bastardo."},
             "estado_vigente":{"id":70,"capitulo_id":11,"ordem_do_capitulo":1,"titulo_do_capitulo":"Capítulo I","descricao":"manto preto"},
             "descartada":false,"modelo":"m"}
        """.trimIndent()

        val sugestao = jsonDoImagineer.decodeFromString<ElementoSugerido>(json)

        assertEquals(ElementoCasado(30, "PERSONAGEM", "Jon Snow", "Bastardo."), sugestao.elemento_casado)
        assertEquals(EstadoVigente(id = 70, capitulo_id = 11, ordem_do_capitulo = 1, titulo_do_capitulo = "Capítulo I", descricao = "manto preto"), sugestao.estado_vigente)
        assertFalse(sugestao.descartada)
    }

    @Test
    fun `uma sugestao de servidor antigo, sem os campos novos, ainda le`() {
        val json = """{"id":12,"tipo":"PERSONAGEM","nome":"Jon","descricao":null,"manter_estado_atual":true,"modelo":"m"}"""

        val sugestao = jsonDoImagineer.decodeFromString<ElementoSugerido>(json)

        assertEquals(null, sugestao.elemento_casado)
        assertEquals(null, sugestao.estado_vigente)
        assertFalse(sugestao.descartada)
    }

    @Test
    fun `excluir o elemento e um DELETE e 204 conta como sucesso`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(204))

        val resultado = repositorio().excluir(30)

        val pedido = servidor.takeRequest()
        assertEquals("DELETE", pedido.method)
        assertEquals("/elementos/30", pedido.path)
        assertTrue(resultado is ResultadoDaChamada.Sucesso)
    }

    @Test
    fun `a lista de elementos le o estado vigente de cada um`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"id":30,"livro_id":4,"tipo":"PERSONAGEM","nome":"Jon","descricao":"Bastardo.","total_de_estados":2,
                    "estado_vigente":{"id":71,"elemento_id":30,"capitulo_id":14,"descricao":"armadura","imagem_ancora_id":null,"data_criacao":"2026-09-30T20:00:00"}}]""",
            ),
        )

        val lista = (repositorio().listar(4) as ResultadoDaChamada.Sucesso).dado

        assertEquals("armadura", lista.single().estado_vigente?.descricao)
    }

    @Test
    fun `mesclar e um POST com o destino e 200 conta como sucesso`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(detalheJson))

        val resultado = repositorio().mesclar(30, 31)

        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/elementos/30/mesclar", pedido.path)
        assertEquals("""{"destino_id":31}""", pedido.body.readUtf8())
        assertTrue(resultado is ResultadoDaChamada.Sucesso)
    }

    @Test
    fun `mesclar com elemento de outro livro traz o 422 com a mensagem da API`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(422).setBody("""{"detail":"O elemento 31 é do livro 2, não do livro 1 do elemento 30."}"""),
        )

        val resultado = repositorio().mesclar(30, 31)

        assertEquals(
            ResultadoDaChamada.Falha("O elemento 31 é do livro 2, não do livro 1 do elemento 30.", 422),
            resultado,
        )
    }

    private val acrescimoJson =
        """{"id":5,"elemento_id":30,"capitulo_id":13,"ordem_do_capitulo":3,"titulo_do_capitulo":"Capítulo III","descricao":"x","data_criacao":"2026-09-30T20:00:00"}"""

    @Test
    fun `criar acrescimo de identidade manda capitulo e descricao`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(201).setBody(acrescimoJson))

        val resultado = repositorio().criarAcrescimo(30, 13, "Agora lidera.")

        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/elementos/30/historico-identidade", pedido.path)
        assertEquals("""{"capitulo_id":13,"descricao":"Agora lidera."}""", pedido.body.readUtf8())
        assertTrue(resultado is ResultadoDaChamada.Sucesso)
    }

    @Test
    fun `corrigir acrescimo e um PATCH da descricao`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(acrescimoJson))

        repositorio().ajustarAcrescimo(5, "Certo.")

        val pedido = servidor.takeRequest()
        assertEquals("PATCH", pedido.method)
        assertEquals("/historico-identidade/5", pedido.path)
        assertEquals("""{"descricao":"Certo."}""", pedido.body.readUtf8())
    }

    @Test
    fun `apagar acrescimo e um DELETE e 204 conta como sucesso`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(204))

        val resultado = repositorio().removerAcrescimo(5)

        val pedido = servidor.takeRequest()
        assertEquals("DELETE", pedido.method)
        assertEquals("/historico-identidade/5", pedido.path)
        assertTrue(resultado is ResultadoDaChamada.Sucesso)
    }

    @Test
    fun `acrescimo de capitulo de outro livro traz o 422 da API`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(422).setBody("""{"detail":"O capítulo 9 é do livro 2, não do livro 1."}"""),
        )

        val resultado = repositorio().criarAcrescimo(30, 9, "x")

        assertEquals(ResultadoDaChamada.Falha("O capítulo 9 é do livro 2, não do livro 1.", 422), resultado)
    }

    @Test
    fun `marcadores e um GET so de leitura em capitulos-id-marcadores`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"marcadores":[{"tipo":"ELEMENTO","tipo_do_elemento":"PERSONAGEM","sugestao_id":3,"frame_id":null,"rotulo":"Jon","posicao_no_texto":42,"situacao":"SUGERIDO","imagem_id":null}]}""",
            ),
        )

        val resultado = RepositorioDeMarcadoresPeloRetrofit(ProvedorDeApi(ArmazenamentoComUrl(url()))).ler(5)

        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/capitulos/5/marcadores", pedido.path)
        val marcador = (resultado as ResultadoDaChamada.Sucesso).dado.single()
        assertEquals("Jon", marcador.rotulo)
        assertEquals(42, marcador.posicao_no_texto)
    }
}
