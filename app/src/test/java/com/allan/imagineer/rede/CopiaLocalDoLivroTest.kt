package com.allan.imagineer.rede

import com.allan.imagineer.local.ArmazemEmMemoria
import com.allan.imagineer.local.ChaveDoCache
import com.allan.imagineer.local.DownloadsEmMemoria
import com.allan.imagineer.local.IndiceEmMemoria
import com.allan.imagineer.local.TextoGuardado
import com.allan.imagineer.dados.LeitorDeArquivos
import com.allan.imagineer.dados.ArquivoEscolhido
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream

/**
 * Abrir e remover livro com a cópia do aparelho (item 7.0a, passo 1, regras L5 e L6).
 * Servidor HTTP falso de verdade; índice e disco em memória.
 */
class CopiaLocalDoLivroTest {

    private lateinit var servidor: MockWebServer
    private val indice = IndiceEmMemoria()
    private val textos = ArmazemEmMemoria()
    private val downloads = DownloadsEmMemoria()

    private class LeitorNulo : LeitorDeArquivos {
        override fun descrever(uri: String): ArquivoEscolhido? = null
        override fun abrir(uri: String): InputStream? = null
    }

    @Before
    fun subir() {
        servidor = MockWebServer().apply { start() }
    }

    @After
    fun derrubar() {
        servidor.shutdown()
    }

    private fun urlDoServidor() = servidor.url("/").toString().trimEnd('/')

    private fun repositorio(url: String = urlDoServidor()) =
        RepositorioDeLivrosPeloRetrofit(ProvedorDeApi(ArmazenamentoComUrl(url)), LeitorNulo(), indice, textos, downloads)

    private fun chave(url: String = urlDoServidor()) = ChaveDoCache(url)

    @Test
    fun `sem copia, pede sem If-None-Match e guarda o livro`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(livroDeTeste(revisao = 4).comoJson()))

        val resultado = repositorio().abrirLivro(1)

        assertNull(servidor.takeRequest().getHeader("If-None-Match"))
        assertEquals("Livro", (resultado as ResultadoDaChamada.Sucesso).dado.titulo)
        assertEquals(4, indice.livro(chave(), 1)?.revisao)
    }

    @Test
    fun `com copia, manda a revisao entre aspas e, no 304, entrega a copia do aparelho`() = runTest {
        indice.guardarLivro(chave(), livroDeTeste(revisao = 3, titulo = "Do aparelho"))
        servidor.enqueue(MockResponse().setResponseCode(304))

        val resultado = repositorio().abrirLivro(1)

        assertEquals("\"3\"", servidor.takeRequest().getHeader("If-None-Match"))
        assertEquals("Do aparelho", (resultado as ResultadoDaChamada.Sucesso).dado.titulo)
    }

    @Test
    fun `com copia, se o livro mudou (200), guarda a nova versao e entrega ela`() = runTest {
        indice.guardarLivro(chave(), livroDeTeste(revisao = 3, titulo = "Antigo"))
        servidor.enqueue(
            MockResponse().setResponseCode(200).setBody(livroDeTeste(revisao = 5, titulo = "Novo").comoJson()),
        )

        val resultado = repositorio().abrirLivro(1)

        assertEquals("Novo", (resultado as ResultadoDaChamada.Sucesso).dado.titulo)
        assertEquals(5, indice.livro(chave(), 1)?.revisao)
        assertEquals("Novo", indice.livro(chave(), 1)?.detalhe?.titulo)
    }

    @Test
    fun `sem conexao, com copia, entrega a copia`() = runTest {
        indice.guardarLivro(chave(URL_SEM_SERVIDOR), livroDeTeste(titulo = "Do aparelho"))

        val resultado = repositorio(URL_SEM_SERVIDOR).abrirLivro(1)

        assertEquals("Do aparelho", (resultado as ResultadoDaChamada.Sucesso).dado.titulo)
    }

    @Test
    fun `sem conexao e sem copia, a falha de sempre`() = runTest {
        val resultado = repositorio(URL_SEM_SERVIDOR).abrirLivro(1)

        assertEquals(ResultadoDaChamada.Falha("Não consegui falar com o servidor."), resultado)
    }

    @Test
    fun `o servidor respondendo 404 e falha, e a copia nao e apagada sozinha`() = runTest {
        indice.guardarLivro(chave(), livroDeTeste())
        servidor.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"Não existe livro com id 1."}"""))

        val resultado = repositorio().abrirLivro(1)

        assertEquals(404, (resultado as ResultadoDaChamada.Falha).codigoHttp)
        assertTrue(indice.livro(chave(), 1) != null)
    }

    @Test
    fun `a copia de um servidor nao aparece para outro`() = runTest {
        indice.guardarLivro(chave("http://127.0.0.1:2"), livroDeTeste(titulo = "Do servidor A"))

        val resultado = repositorio(URL_SEM_SERVIDOR).abrirLivro(1) // outro endereço, sem rede

        assertTrue(resultado is ResultadoDaChamada.Falha) // não entregou o livro do A
    }

    @Test
    fun `indice quebrado nao impede abrir o livro pela rede`() = runTest {
        indice.quebrado = true
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(livroDeTeste(titulo = "Da rede").comoJson()))

        val resultado = repositorio().abrirLivro(1)

        assertEquals("Da rede", (resultado as ResultadoDaChamada.Sucesso).dado.titulo)
    }

    @Test
    fun `remover o livro apaga a copia e os textos guardados`() = runTest {
        indice.guardarLivro(chave(), livroDeTeste())
        indice.registrarTexto(chave(), TextoGuardado(capituloId = 10, livroId = 1, bytes = 5))
        textos.gravar(chave(), 1, 10, "texto")
        servidor.enqueue(MockResponse().setResponseCode(204))

        val resultado = repositorio().removerLivro(1)

        assertTrue(resultado is ResultadoDaChamada.Sucesso)
        assertNull(indice.livro(chave(), 1))
        assertNull(indice.texto(chave(), 10))
        assertNull(textos.ler(chave(), 1, 10))
    }

    @Test
    fun `remover um livro que ja nao existe no servidor (404) tambem limpa a copia`() = runTest {
        indice.guardarLivro(chave(), livroDeTeste())
        servidor.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"Não existe livro com id 1."}"""))

        repositorio().removerLivro(1)

        assertNull(indice.livro(chave(), 1))
    }

    @Test
    fun `remover que falha no servidor mantem a copia`() = runTest {
        indice.guardarLivro(chave(), livroDeTeste())
        servidor.enqueue(MockResponse().setResponseCode(500))

        val resultado = repositorio().removerLivro(1)

        assertTrue(resultado is ResultadoDaChamada.Falha)
        assertTrue(indice.livro(chave(), 1) != null)
    }

    // --- PL13: a Biblioteca sem conexão ---

    @Test
    fun `sem conexao, a biblioteca lista so os livros baixados`() = runTest {
        indice.guardarLivro(chave(URL_SEM_SERVIDOR), livroDeTeste(id = 1, titulo = "Baixado"))
        indice.guardarLivro(chave(URL_SEM_SERVIDOR), livroDeTeste(id = 2, titulo = "So de passagem"))
        downloads.guardar(chave(URL_SEM_SERVIDOR), com.allan.imagineer.local.DownloadGuardado(1, 0L, 0L, emptyList()))

        val resultado = repositorio(URL_SEM_SERVIDOR).listarLivros()

        val livros = (resultado as ResultadoDaChamada.Sucesso).dado
        assertEquals(listOf("Baixado"), livros.map { it.titulo })
        assertEquals(10, livros.single().total_de_caracteres) // só os capítulos ativos
        assertEquals(false, livros.single().tem_capa)
    }

    @Test
    fun `sem conexao, o cartao do baixado tem capa so se a capa foi baixada`() = runTest {
        val imagens = com.allan.imagineer.local.ArmazemDeImagensEmArquivos(kotlin.io.path.createTempDirectory("capas").toFile(), kotlin.coroutines.EmptyCoroutineContext)
        val chaveOffline = chave(URL_SEM_SERVIDOR)
        indice.guardarLivro(chaveOffline, livroDeTeste(id = 1, revisao = 4, titulo = "Com capa"))
        indice.guardarLivro(chaveOffline, livroDeTeste(id = 2, revisao = 1, titulo = "Sem capa"))
        downloads.guardar(chaveOffline, com.allan.imagineer.local.DownloadGuardado(1, 0L, 0L, emptyList()))
        downloads.guardar(chaveOffline, com.allan.imagineer.local.DownloadGuardado(2, 0L, 0L, emptyList()))
        imagens.gravar(chaveOffline, 1, com.allan.imagineer.local.tamanhoDaCapa(4)) { it.writeBytes(byteArrayOf(1)); true }
        val repositorio = RepositorioDeLivrosPeloRetrofit(ProvedorDeApi(ArmazenamentoComUrl(URL_SEM_SERVIDOR)), LeitorNulo(), indice, textos, downloads, imagens)

        val livros = (repositorio.listarLivros() as ResultadoDaChamada.Sucesso).dado.associateBy { it.titulo }

        assertTrue(livros.getValue("Com capa").tem_capa)
        assertEquals(false, livros.getValue("Sem capa").tem_capa)
    }

    @Test
    fun `sem conexao e sem nenhum baixado, a falha de sempre`() = runTest {
        indice.guardarLivro(chave(URL_SEM_SERVIDOR), livroDeTeste())

        assertTrue(repositorio(URL_SEM_SERVIDOR).listarLivros() is ResultadoDaChamada.Falha)
    }

    @Test
    fun `servidor que responde com erro nao cai na copia do aparelho`() = runTest {
        indice.guardarLivro(chave(), livroDeTeste())
        downloads.guardar(chave(), com.allan.imagineer.local.DownloadGuardado(1, 0L, 0L, emptyList()))
        servidor.enqueue(MockResponse().setResponseCode(500))

        assertTrue(repositorio().listarLivros() is ResultadoDaChamada.Falha)
    }

    @Test
    fun `sem conexao e banco quebrado, a falha de sempre`() = runTest {
        downloads.guardar(chave(URL_SEM_SERVIDOR), com.allan.imagineer.local.DownloadGuardado(1, 0L, 0L, emptyList()))
        indice.quebrado = true

        assertTrue(repositorio(URL_SEM_SERVIDOR).listarLivros() is ResultadoDaChamada.Falha)
    }
}
