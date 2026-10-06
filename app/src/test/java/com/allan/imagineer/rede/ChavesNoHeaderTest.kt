package com.allan.imagineer.rede

import com.allan.imagineer.dados.CofreEmMemoria
import com.allan.imagineer.dados.mascararChave
import com.allan.imagineer.dados.motivoParaNaoGuardarAChave
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** As chaves de IA no header de cada chamada (AP2, AP3) e o 401 do Tailscale (AP6). */
class ChavesNoHeaderTest {

    private val servidor = MockWebServer()
    private val eu = """{"id":1,"login":"allan@exemplo.com","nome":"Allan","dono":true,"usa_chaves_do_servidor":true}"""

    @Before
    fun subir() = servidor.start()

    @After
    fun descer() = servidor.shutdown()

    private fun url() = servidor.url("/").toString().trimEnd('/')

    @Test
    fun cada_chave_cadastrada_vai_no_header_do_proprio_provedor() = runTest {
        val cofre = CofreEmMemoria(mapOf("X-Chave-API-OpenRouter" to "sk-or-abc123", "X-Chave-API-Fal" to "fal-xyz"))
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(eu))

        criarApi(url(), chaves = cofre::chaves).eu()

        val pedido = servidor.takeRequest()
        assertEquals("sk-or-abc123", pedido.getHeader("X-Chave-API-OpenRouter"))
        assertEquals("fal-xyz", pedido.getHeader("X-Chave-API-Fal"))
        assertNull(pedido.getHeader("X-Chave-API-Replicate"))  // sem chave, sem header
    }

    @Test
    fun sem_nenhuma_chave_nenhum_header_vai() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(eu))

        criarApi(url()).eu()

        val pedido = servidor.takeRequest()
        assertTrue(pedido.headers.names().none { it.startsWith("X-Chave-API", ignoreCase = true) })
    }

    @Test
    fun a_chave_trocada_no_cofre_vale_na_chamada_seguinte_sem_refazer_o_cliente() = runTest {
        val cofre = CofreEmMemoria()
        val api = criarApi(url(), chaves = cofre::chaves)
        repeat(3) { servidor.enqueue(MockResponse().setResponseCode(200).setBody(eu)) }

        api.eu()
        cofre.gravar("X-Chave-API-Fal", "primeira")
        api.eu()
        cofre.remover("X-Chave-API-Fal")
        api.eu()

        assertNull(servidor.takeRequest().getHeader("X-Chave-API-Fal"))
        assertEquals("primeira", servidor.takeRequest().getHeader("X-Chave-API-Fal"))
        assertNull(servidor.takeRequest().getHeader("X-Chave-API-Fal"))
    }

    @Test
    fun o_401_vira_a_mensagem_do_tailscale() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Not authenticated"}"""))

        val resultado = chamarApi { criarApi(url()).eu() }

        assertEquals(MOTIVO_DO_401, (resultado as ResultadoDaChamada.Falha).motivo)
        assertEquals(401, resultado.codigoHttp)
    }

    @Test
    fun o_507_mostra_a_mensagem_do_servidor() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(507).setBody("""{"detail":"A sua cota de 5 GB acabou."}"""))

        val resultado = chamarApi { criarApi(url()).eu() }

        assertEquals("A sua cota de 5 GB acabou.", (resultado as ResultadoDaChamada.Falha).motivo)
    }

    @Test
    fun o_cofre_em_memoria_guarda_troca_e_remove() {
        val cofre = CofreEmMemoria()
        assertFalse(cofre.tem("X-Chave-API-Fal"))

        cofre.gravar("X-Chave-API-Fal", "um")
        cofre.gravar("X-Chave-API-Fal", "dois")
        assertEquals(mapOf("X-Chave-API-Fal" to "dois"), cofre.chaves())

        cofre.remover("X-Chave-API-Fal")
        assertTrue(cofre.chaves().isEmpty())
    }

    @Test
    fun so_cabe_no_header_o_ascii_visivel() {
        assertNull(motivoParaNaoGuardarAChave("sk-or-v1-a1B2c3D4"))
        assertEquals("Cole a chave.", motivoParaNaoGuardarAChave("   "))
        assertTrue(motivoParaNaoGuardarAChave("sk or v1")!!.contains("espaço"))      // espaço no meio
        assertTrue(motivoParaNaoGuardarAChave("sk-ór-v1")!!.contains("espaço"))      // acento
        assertTrue(motivoParaNaoGuardarAChave("sk-“abc")!!.contains("espaço"))  // aspa tipográfica colada
    }

    @Test
    fun a_mascara_nao_revela_a_chave() {
        val chave = "sk-or-v1-abcdef1234567890"

        val mascara = mascararChave(chave)

        assertEquals("sk-or…7890", mascara)
        assertFalse(mascara.contains("abcdef"))
        assertEquals("••••••••", mascararChave("curta"))  // curta demais: nada dela
    }
}
