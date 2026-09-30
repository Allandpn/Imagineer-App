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

/**
 * O repositório de verdade, com Retrofit + OkHttp + serialização, conversando com um
 * servidor HTTP falso que roda dentro do teste (`MockWebServer`).
 *
 * É o único teste que atravessa a pilha de rede inteira. Cobre o que os outros não
 * alcançam: o formato exato do upload multipart (nome do campo, nome do arquivo,
 * bytes íntegros), o corpo do `PATCH` e o tratamento dos códigos HTTP.
 */
class RepositorioDeLivrosPelaRedeTest {

    private lateinit var servidor: MockWebServer

    private val conteudo = ByteArray(300_000) { (it % 251).toByte() }

    /** Devolve o arquivo combinado; `abre = false` simula permissão perdida. */
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

    private fun repositorio(leitor: LeitorFalso = LeitorFalso(conteudo)) = RepositorioDeLivrosPeloRetrofit(
        ArmazenamentoFalso(servidor.url("/").toString().trimEnd('/')),
        leitor,
    )

    private val livroJson = """{"id":2,"titulo":"segundo","autor":null,"idioma":"pt-BR","nome_arquivo":"livro.epub","data_importacao":"2026-09-30T01:25:34","total_de_capitulos":2,"capitulos_ignorados":0,"identificador_epub":null,"perfil_renderizacao_padrao_id":null,"metadados_pendentes":["autor"],"capitulos":[]}"""

    private val respostaDeImportacao = """{"livro":$livroJson,"livros_semelhantes":[]}"""

    private fun arquivo(tamanho: Long = conteudo.size.toLong()) =
        ArquivoEscolhido("content://falso", "livro.epub", tamanho)

    @Test
    fun `o upload e um multipart com o campo arquivo, o nome certo e os bytes intatos`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(201).setBody(respostaDeImportacao))

        val resultado = repositorio().importarLivro(arquivo()) { _, _ -> }

        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/livros", pedido.path)
        assertTrue(pedido.getHeader("Content-Type")!!.startsWith("multipart/form-data"))

        // ISO_8859_1 mapeia cada byte para um caractere, então dá para procurar os
        // bytes do arquivo dentro do corpo como se fosse texto, sem perder nenhum.
        val corpo = String(pedido.body.readByteArray(), Charsets.ISO_8859_1)
        assertTrue("campo 'arquivo' ausente", "name=\"arquivo\"" in corpo)
        assertTrue("nome do arquivo ausente", "filename=\"livro.epub\"" in corpo)
        assertTrue("tipo do arquivo ausente", "Content-Type: application/epub+zip" in corpo)
        assertTrue(
            "os bytes do arquivo não chegaram íntegros",
            corpo.contains(String(conteudo, Charsets.ISO_8859_1)),
        )

        val sucesso = resultado as ResultadoDaChamada.Sucesso
        assertEquals(2, sucesso.dado.livro.id)
        assertEquals(listOf("autor"), sucesso.dado.livro.metadados_pendentes)
    }

    @Test
    fun `o progresso termina no tamanho do arquivo`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(201).setBody(respostaDeImportacao))
        val avisos = mutableListOf<Long>()

        repositorio().importarLivro(arquivo()) { enviados, _ -> avisos += enviados }

        assertEquals(conteudo.size.toLong(), avisos.last())
    }

    @Test
    fun `422 mostra a mensagem que o backend escreveu`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(422)
                .setBody("""{"detail":"Não foi possível ler 'livro.epub' como EPUB: 'Bad Zip file'"}"""),
        )

        val resultado = repositorio().importarLivro(arquivo()) { _, _ -> }

        assertEquals(
            ResultadoDaChamada.Falha(
                "Não foi possível ler 'livro.epub' como EPUB: 'Bad Zip file'",
                codigoHttp = 422,
            ),
            resultado,
        )
    }

    @Test
    fun `arquivo que nao abre falha com a mensagem certa e sem chamar o servidor`() = runTest {
        val resultado = repositorio(LeitorFalso(conteudo, abre = false))
            .importarLivro(arquivo(10)) { _, _ -> }

        assertEquals(ResultadoDaChamada.Falha("Não consegui abrir o arquivo escolhido."), resultado)
        assertEquals(0, servidor.requestCount)
    }

    @Test
    fun `o PATCH manda so os campos pedidos`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(livroJson))

        val resultado = repositorio().ajustarLivro(2, LivroAjuste(autor = "Fulano"))

        val pedido = servidor.takeRequest()
        assertEquals("PATCH", pedido.method)
        assertEquals("/livros/2", pedido.path)
        assertEquals("""{"autor":"Fulano"}""", pedido.body.readUtf8())
        assertTrue(resultado is ResultadoDaChamada.Sucesso)
    }

    @Test
    fun `remover um livro chama DELETE e 204 e sucesso`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(204))

        val resultado = repositorio().removerLivro(5)

        val pedido = servidor.takeRequest()
        assertEquals("DELETE", pedido.method)
        assertEquals("/livros/5", pedido.path)
        assertEquals(ResultadoDaChamada.Sucesso(Unit), resultado)
    }

    @Test
    fun `remover um livro que ja nao existe (404) conta como sucesso`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(404).setBody("""{"detail":"Não existe livro com id 5."}"""),
        )

        assertEquals(ResultadoDaChamada.Sucesso(Unit), repositorio().removerLivro(5))
    }

    @Test
    fun `servidor fora do ar vira falha de conexao, sem estourar`() = runTest {
        val repositorioDeUmServidorMorto = repositorio()
        servidor.shutdown()

        val resultado = repositorioDeUmServidorMorto.listarLivros()

        assertEquals(ResultadoDaChamada.Falha("Não consegui falar com o servidor."), resultado)
    }
}
