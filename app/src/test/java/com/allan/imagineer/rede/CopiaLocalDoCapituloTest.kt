package com.allan.imagineer.rede

import com.allan.imagineer.local.ArmazemEmMemoria
import com.allan.imagineer.local.ChaveDoCache
import com.allan.imagineer.local.IndiceEmMemoria
import com.allan.imagineer.local.TextoGuardado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Abrir capítulo com o texto no aparelho e adiantar o seguinte (item 7.0a, passo 1,
 * regras L1 a L4 e L7). Servidor HTTP falso de verdade; índice e disco em memória.
 */
class CopiaLocalDoCapituloTest {

    private lateinit var servidor: MockWebServer
    private val indice = IndiceEmMemoria()
    private val textos = ArmazemEmMemoria()
    private val escopo = CoroutineScope(Job() + Dispatchers.IO)

    @Before
    fun subir() {
        servidor = MockWebServer().apply { start() }
    }

    @After
    fun derrubar() {
        escopo.cancel()
        servidor.shutdown()
    }

    private fun urlDoServidor() = servidor.url("/").toString().trimEnd('/')

    private fun chave(url: String = urlDoServidor()) = ChaveDoCache(url)

    private fun repositorio(url: String = urlDoServidor()) =
        RepositorioDeCapitulosPeloRetrofit(ProvedorDeApi(ArmazenamentoComUrl(url)), indice, textos, escopo)

    /** Deixa o capítulo [id] como "já lido": registro, arquivo e o livro dele guardado. */
    private suspend fun guardarCapitulo(id: Int, texto: String, chave: ChaveDoCache = chave()) {
        if (indice.livro(chave, 1) == null) indice.guardarLivro(chave, livroDeTeste())
        textos.gravar(chave, 1, id, texto)
        indice.registrarTexto(chave, TextoGuardado(id, 1, texto.length.toLong()))
    }

    @Test
    fun `sem nada no aparelho, vai a rede, entrega e guarda o texto`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(capituloJson(30, 3, "Texto três")))

        val resultado = repositorio().abrirCapitulo(30)

        assertEquals("Texto três", (resultado as ResultadoDaChamada.Sucesso).dado.texto)
        assertEquals("Texto três", textos.ler(chave(), 1, 30))
        // 11 bytes, não 10 caracteres: o "ê" ocupa dois bytes em UTF-8.
        assertEquals(TextoGuardado(30, 1, 11), indice.texto(chave(), 30))
    }

    @Test
    fun `com o texto no aparelho, abre sem nenhum pedido a rede, com os metadados do livro`() = runTest {
        guardarCapitulo(10, "Texto guardado")

        val resultado = repositorio().abrirCapitulo(10)

        val capitulo = (resultado as ResultadoDaChamada.Sucesso).dado
        assertEquals("Texto guardado", capitulo.texto)
        assertEquals("Um", capitulo.titulo) // vem da lista do livro guardado
        assertEquals(1, capitulo.ordem)
        assertEquals(1, capitulo.livro_id)
        assertEquals(0, servidor.requestCount)
    }

    @Test
    fun `abre do aparelho mesmo sem conexao`() = runTest {
        val semRede = ChaveDoCache(URL_SEM_SERVIDOR)
        guardarCapitulo(10, "Texto guardado", semRede)

        val resultado = repositorio(URL_SEM_SERVIDOR).abrirCapitulo(10)

        assertEquals("Texto guardado", (resultado as ResultadoDaChamada.Sucesso).dado.texto)
    }

    @Test
    fun `o registro existe mas o arquivo sumiu - vai a rede`() = runTest {
        guardarCapitulo(10, "Texto guardado")
        textos.arquivos.clear()
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(capituloJson(10, 1, "Da rede")))

        val resultado = repositorio().abrirCapitulo(10)

        assertEquals("Da rede", (resultado as ResultadoDaChamada.Sucesso).dado.texto)
        assertEquals("Da rede", textos.ler(chave(), 1, 10)) // e guardou de novo
    }

    @Test
    fun `o texto esta la mas o livro nao esta guardado - vai a rede`() = runTest {
        textos.gravar(chave(), 1, 10, "Texto solto")
        indice.registrarTexto(chave(), TextoGuardado(10, 1, 11))
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(capituloJson(10, 1, "Da rede")))

        val resultado = repositorio().abrirCapitulo(10)

        assertEquals("Da rede", (resultado as ResultadoDaChamada.Sucesso).dado.texto)
    }

    @Test
    fun `disco cheio nao impede a leitura`() = runTest {
        textos.discoCheio = true
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(capituloJson(30, 3, "Texto três")))

        val resultado = repositorio().abrirCapitulo(30)

        assertEquals("Texto três", (resultado as ResultadoDaChamada.Sucesso).dado.texto)
        assertNull(indice.texto(chave(), 30)) // sem arquivo, sem registro
    }

    @Test
    fun `indice quebrado nao impede a leitura pela rede`() = runTest {
        indice.quebrado = true
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(capituloJson(30, 3, "Texto três")))

        val resultado = repositorio().abrirCapitulo(30)

        assertEquals("Texto três", (resultado as ResultadoDaChamada.Sucesso).dado.texto)
    }

    @Test
    fun `falha de rede sem nada guardado e a falha de sempre`() = runTest {
        val resultado = repositorio(URL_SEM_SERVIDOR).abrirCapitulo(10)

        assertEquals(ResultadoDaChamada.Falha("Não consegui falar com o servidor."), resultado)
    }

    @Test
    fun `ao abrir o capitulo 1 baixa o 3 em segundo plano, pulando o 2 que esta arquivado`() = runTest {
        indice.guardarLivro(chave(), livroDeTeste())
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(capituloJson(10, 1, "Um", titulo = "Um")))
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(capituloJson(30, 3, "Três")))

        repositorio().abrirCapitulo(10)

        assertTrue(esperarAte { indice.textos.containsKey(chave().identificador to 30) })
        assertEquals("/capitulos/10", servidor.takeRequest().path)
        assertEquals("/capitulos/30", servidor.takeRequest().path)
        assertEquals("Três", textos.ler(chave(), 1, 30))
        assertFalse(indice.textos.containsKey(chave().identificador to 20)) // o arquivado não foi baixado
    }

    @Test
    fun `abrir do aparelho tambem adianta o seguinte`() = runTest {
        guardarCapitulo(10, "Um")
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(capituloJson(30, 3, "Três")))

        repositorio().abrirCapitulo(10)

        assertTrue(esperarAte { indice.textos.containsKey(chave().identificador to 30) })
    }

    @Test
    fun `nao baixa o seguinte se ele ja esta no aparelho`() = runTest {
        guardarCapitulo(10, "Um")
        guardarCapitulo(30, "Três")

        repositorio().abrirCapitulo(10)
        esperarAte { false } // dá ~5 s para um pedido indevido aparecer

        assertEquals(0, servidor.requestCount)
    }

    @Test
    fun `no ultimo capitulo nao ha o que adiantar`() = runTest {
        guardarCapitulo(30, "Três")

        repositorio().abrirCapitulo(30)
        esperarAte { false }

        assertEquals(0, servidor.requestCount)
    }

    @Test
    fun `falha ao adiantar e silenciosa e nao atrapalha o capitulo aberto`() = runTest {
        guardarCapitulo(10, "Um")
        servidor.enqueue(MockResponse().setResponseCode(500))

        val resultado = repositorio().abrirCapitulo(10)

        assertEquals("Um", (resultado as ResultadoDaChamada.Sucesso).dado.texto)
        assertTrue(esperarAte { servidor.requestCount == 1 })
        assertNull(indice.texto(chave(), 30))
    }

    @Test
    fun `sem o livro guardado nao ha como saber o seguinte, e nao adianta`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(capituloJson(10, 1, "Um")))

        repositorio().abrirCapitulo(10)
        esperarAte { false }

        assertEquals(1, servidor.requestCount) // só o capítulo pedido
        assertNotNull(indice.texto(chave(), 10))
    }
}
