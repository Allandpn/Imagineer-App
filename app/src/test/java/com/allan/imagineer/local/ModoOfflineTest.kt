package com.allan.imagineer.local

import com.allan.imagineer.rede.interceptadorDoModoOffline
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** O Modo offline (PL14): oferta, entrada, saída pela sonda, conexão efetiva e o interceptador. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModoOfflineTest {

    private class SondaFalsa(var responde: Boolean = false) : SondaDoServidor {
        var chamadas = 0
        override suspend fun responde(): Boolean { chamadas++; return responde }
    }

    private class RedeFalsa(ligada: Boolean = true) : Conexao {
        val estado = MutableStateFlow(ligada)
        override val online: StateFlow<Boolean> = estado
    }

    private var relogio = 0L

    private fun TestScope.modo(sonda: SondaFalsa) =
        ModoOffline(sonda, backgroundScope, agora = { relogio }, intervaloDaSondaEmMs = 1_000, silencioDaRecusaEmMs = 60_000)

    @Test
    fun uma_falha_sem_resposta_oferece_o_modo_offline() = runTest {
        val modo = modo(SondaFalsa())

        modo.servidorSemResposta()

        assertTrue(modo.oferta.value)
        assertFalse(modo.ativo.value)
    }

    @Test
    fun agora_nao_cala_a_oferta_por_um_minuto() = runTest {
        val modo = modo(SondaFalsa())
        modo.servidorSemResposta()
        modo.recusarOferta()

        relogio = 59_000
        modo.servidorSemResposta()
        assertFalse(modo.oferta.value)

        relogio = 61_000
        modo.servidorSemResposta()
        assertTrue(modo.oferta.value)
    }

    @Test
    fun entrar_liga_o_modo_e_fecha_a_oferta_e_nao_se_oferece_estando_ligado() = runTest {
        val modo = modo(SondaFalsa())
        modo.servidorSemResposta()

        modo.entrar()
        modo.servidorSemResposta()

        assertTrue(modo.ativo.value)
        assertFalse(modo.oferta.value)
    }

    @Test
    fun a_sonda_tira_do_modo_offline_quando_o_servidor_responde_e_avisa() = runTest {
        val sonda = SondaFalsa(responde = false)
        val modo = modo(sonda)
        var avisos = 0
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { modo.voltou.collect { avisos++ } }
        modo.entrar()

        advanceTimeBy(3_500); runCurrent()
        assertTrue(modo.ativo.value) // o servidor ainda não responde
        assertTrue(sonda.chamadas >= 3)

        sonda.responde = true
        advanceTimeBy(1_000); runCurrent()

        assertFalse(modo.ativo.value)
        assertEquals(1, avisos)
        val chamadas = sonda.chamadas
        advanceUntilIdle()
        assertEquals(chamadas, sonda.chamadas) // parou de sondar
    }

    @Test
    fun verificar_agora_sonda_na_hora_e_sai_se_o_servidor_voltou() = runTest {
        val sonda = SondaFalsa(responde = true)
        val modo = modo(sonda)
        modo.entrar()

        modo.verificarAgora(); runCurrent()

        assertFalse(modo.ativo.value)
    }

    @Test
    fun a_conexao_efetiva_exige_rede_e_modo_desligado() = runTest {
        val rede = RedeFalsa()
        val modo = modo(SondaFalsa())
        val conexao = ConexaoEfetiva(rede, modo, backgroundScope)
        runCurrent()
        assertTrue(conexao.online.value)

        modo.entrar(); runCurrent()
        assertFalse(conexao.online.value)

        rede.estado.value = false
        modo.verificarAgora(); runCurrent()
        assertFalse(conexao.online.value) // sem rede do Android, continua sem conexão
    }

    // --- o interceptador ---

    private fun cliente(offline: () -> Boolean, aoFalhar: () -> Unit) =
        OkHttpClient.Builder().addInterceptor(interceptadorDoModoOffline(offline, aoFalhar)).build()

    @Test
    fun em_modo_offline_a_chamada_falha_sem_tocar_a_rede() {
        MockWebServer().use { servidor ->
            servidor.start()
            val cliente = cliente(offline = { true }, aoFalhar = {})

            val falhou = try { cliente.newCall(Request.Builder().url(servidor.url("/")).build()).execute(); false } catch (e: IOException) { true }

            assertTrue(falhou)
            assertEquals(0, servidor.requestCount)
        }
    }

    @Test
    fun falha_de_conexao_avisa_e_chamada_longa_de_ia_nao() {
        var avisos = 0
        val cliente = cliente(offline = { false }, aoFalhar = { avisos++ })
        val semServidor = Request.Builder().url("http://127.0.0.1:1/")

        try { cliente.newCall(semServidor.build()).execute() } catch (e: IOException) { }
        assertEquals(1, avisos)

        try { cliente.newCall(semServidor.header("X-Timeout-Leitura", "180").build()).execute() } catch (e: IOException) { }
        assertEquals(1, avisos)
    }

    @Test
    fun resposta_do_servidor_nao_avisa_nada() {
        MockWebServer().use { servidor ->
            servidor.enqueue(MockResponse().setResponseCode(500))
            servidor.start()
            var avisos = 0

            cliente(offline = { false }, aoFalhar = { avisos++ }).newCall(Request.Builder().url(servidor.url("/")).build()).execute().close()

            assertEquals(0, avisos)
        }
    }
}
