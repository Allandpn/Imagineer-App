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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * O corpo do `PATCH /livros/{id}` (item 7.5a, incremento 7). No backend, campo
 * **ausente** não mexe e campo **`null`** limpa — então o app precisa controlar os
 * dois casos, e não só omitir nulos.
 */
class LivroAjusteTest {

    @Test
    fun `sem nada para mudar o corpo e vazio`() {
        assertEquals("{}", LivroAjuste().paraJson().toString())
    }

    @Test
    fun `so os campos com valor vao no JSON`() {
        assertEquals("""{"titulo":"Novo"}""", LivroAjuste(titulo = "Novo").paraJson().toString())
        assertEquals("""{"autor":"Fulano"}""", LivroAjuste(autor = "Fulano").paraJson().toString())
        assertEquals("""{"idioma":"en"}""", LivroAjuste(idioma = "en").paraJson().toString())
        assertEquals(
            """{"perfil_renderizacao_padrao_id":7}""",
            LivroAjuste(perfil_renderizacao_padrao_id = 7).paraJson().toString(),
        )
    }

    @Test
    fun `limpar o idioma manda null explicito`() {
        assertEquals("""{"idioma":null}""", LivroAjuste(limparIdioma = true).paraJson().toString())
    }

    @Test
    fun `limpar o perfil padrao manda null explicito`() {
        assertEquals(
            """{"perfil_renderizacao_padrao_id":null}""",
            LivroAjuste(limparPerfilPadrao = true).paraJson().toString(),
        )
    }

    @Test
    fun `autor nunca vai como null, senao voltaria a ficar pendente no backend`() {
        // Não existe marca "limparAutor" de propósito; sem valor, o autor fica fora.
        assertEquals("{}", LivroAjuste(autor = null).paraJson().toString())
    }

    @Test
    fun `varios campos juntos`() {
        val corpo = LivroAjuste(titulo = "T", autor = "A", idioma = "en", perfil_renderizacao_padrao_id = 3)

        assertEquals(
            """{"titulo":"T","autor":"A","idioma":"en","perfil_renderizacao_padrao_id":3}""",
            corpo.paraJson().toString(),
        )
    }

    @Test
    fun `a marca de limpar vence um valor que sobrou`() {
        // Não deveria acontecer, mas se acontecer o resultado é previsível: limpa.
        assertEquals(
            """{"idioma":null}""",
            LivroAjuste(idioma = "en", limparIdioma = true).paraJson().toString(),
        )
    }
}

/** JSON **real** do backend (`GET /perfis-renderizacao` e `/{id}`, num `TestClient`). */
class PerfilRenderizacaoTest {

    private val listaReal = """
        [{"estilo":"aquarela, tons frios","artista_referencia":null,"iluminacao":null,"paleta":"azul e cinza","formato":null,"modelo_alvo":null,"id":1,"nome":"Aquarela sombria"},{"estilo":null,"artista_referencia":null,"iluminacao":null,"paleta":null,"formato":null,"modelo_alvo":null,"id":2,"nome":"Minimo"}]
    """.trimIndent()

    @Test
    fun `desserializa a lista real de perfis`() {
        val perfis = jsonDoImagineer.decodeFromString<List<PerfilRenderizacao>>(listaReal)

        assertEquals(2, perfis.size)
        assertEquals("Aquarela sombria", perfis[0].nome)
        assertEquals("aquarela, tons frios", perfis[0].estilo)
        assertEquals("azul e cinza", perfis[0].paleta)
    }

    @Test
    fun `so o nome e obrigatorio, o resto pode ser nulo`() {
        val minimo = jsonDoImagineer.decodeFromString<List<PerfilRenderizacao>>(listaReal)[1]

        assertEquals("Minimo", minimo.nome)
        assertNull(minimo.estilo)
        assertNull(minimo.paleta)
        assertNull(minimo.modelo_alvo)
    }
}

/** Os repositórios de verdade (Retrofit + serialização), contra um servidor HTTP falso. */
class RepositoriosDoIncremento7PelaRedeTest {

    private lateinit var servidor: MockWebServer

    private class ArmazenamentoFalso(url: String) : ArmazenamentoDeConfiguracao {
        override val urlDoServidor: Flow<String?> = flowOf(url)
        override suspend fun salvarUrlDoServidor(url: String) = Unit
    }

    private class LeitorNulo : com.allan.imagineer.dados.LeitorDeArquivos {
        override fun descrever(uri: String) = null
        override fun abrir(uri: String) = null
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

    private val livroJson = """{"id":1,"titulo":"Um Livro","autor":"Fulano","idioma":null,"nome_arquivo":"livro.epub","data_importacao":"2026-09-30T01:41:08","total_de_capitulos":0,"capitulos_ignorados":0,"identificador_epub":null,"perfil_renderizacao_padrao_id":null,"metadados_pendentes":[],"capitulos":[]}"""

    @Test
    fun `limpar o idioma chega ao servidor como null, e nao como campo ausente`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(livroJson))

        RepositorioDeLivrosPeloRetrofit(provedor(), LeitorNulo(), IndiceEmMemoria(), ArmazemEmMemoria())
            .ajustarLivro(1, LivroAjuste(limparIdioma = true))

        val pedido = servidor.takeRequest()
        assertEquals("PATCH", pedido.method)
        assertEquals("/livros/1", pedido.path)
        assertEquals("""{"idioma":null}""", pedido.body.readUtf8())
    }

    @Test
    fun `limpar o perfil padrao chega ao servidor como null`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(livroJson))

        RepositorioDeLivrosPeloRetrofit(provedor(), LeitorNulo(), IndiceEmMemoria(), ArmazemEmMemoria())
            .ajustarLivro(1, LivroAjuste(limparPerfilPadrao = true))

        assertEquals("""{"perfil_renderizacao_padrao_id":null}""", servidor.takeRequest().body.readUtf8())
    }

    @Test
    fun `listar perfis chama o caminho certo`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"estilo":null,"artista_referencia":null,"iluminacao":null,"paleta":null,"formato":null,"modelo_alvo":null,"id":1,"nome":"Minimo"}]""",
            ),
        )

        val resultado = RepositorioDePerfisPeloRetrofit(provedor()).listarPerfis()

        assertEquals("/perfis-renderizacao", servidor.takeRequest().path)
        assertEquals("Minimo", (resultado as ResultadoDaChamada.Sucesso).dado.single().nome)
    }

    @Test
    fun `abrir um perfil que nao existe mostra a mensagem do backend`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"detail":"Não existe perfil de renderização com id 99."}"""),
        )

        val resultado = RepositorioDePerfisPeloRetrofit(provedor()).abrirPerfil(99)

        assertEquals("/perfis-renderizacao/99", servidor.takeRequest().path)
        assertEquals(
            ResultadoDaChamada.Falha("Não existe perfil de renderização com id 99.", codigoHttp = 404),
            resultado,
        )
    }

    @Test
    fun `perfil que nao existe ao definir o padrao vem como 422 com a mensagem`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(422)
                .setBody("""{"detail":"Não existe perfil de renderização com id 99."}"""),
        )

        val resultado = RepositorioDeLivrosPeloRetrofit(provedor(), LeitorNulo(), IndiceEmMemoria(), ArmazemEmMemoria())
            .ajustarLivro(1, LivroAjuste(perfil_renderizacao_padrao_id = 99))

        assertEquals(
            ResultadoDaChamada.Falha("Não existe perfil de renderização com id 99.", codigoHttp = 422),
            resultado,
        )
    }
}
