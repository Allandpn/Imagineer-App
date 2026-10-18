package com.allan.imagineer.local

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.ProvedorDeApi
import com.allan.imagineer.rede.RepositorioDeArtefatosPeloRetrofit
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.TextoDoCapitulo
import com.allan.imagineer.rede.MidiasDoLivro
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private val CHAVE_DOS_ARTEFATOS = ChaveDoCache("http://100.1.2.3:8000")

private fun cena(rotulo: String, imagemId: Int? = 9) =
    Artefato(tipo = "CENA", frame_id = 1, rotulo = rotulo, situacao = "ILUSTRADO", imagem_id = imagemId, posicao_no_texto = 10)

/** As listas de artefatos dos capítulos baixados (PL16): o armazém, o download e a leitura sem o servidor. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtefatosBaixadosTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val servidor = MockWebServer()

    @After
    fun derrubar() = servidor.shutdown()

    private fun armazem() = ArmazemDeArtefatosEmArquivos(pasta.newFolder("artefatos"), kotlin.coroutines.EmptyCoroutineContext)

    // --- o armazém ---

    @Test
    fun o_armazem_guarda_le_atualiza_so_o_que_existe_e_apaga_o_livro() = runTest {
        val armazem = armazem()
        assertNull(armazem.ler(CHAVE_DOS_ARTEFATOS, 10))

        armazem.gravar(CHAVE_DOS_ARTEFATOS, 1, 10, listOf(cena("Antiga")))
        armazem.gravar(CHAVE_DOS_ARTEFATOS, 2, 20, listOf(cena("De outro livro")))
        assertEquals(listOf("Antiga"), armazem.ler(CHAVE_DOS_ARTEFATOS, 10)?.map { it.rotulo })

        assertTrue(armazem.atualizar(CHAVE_DOS_ARTEFATOS, 10, listOf(cena("Nova"))))
        assertEquals(listOf("Nova"), armazem.ler(CHAVE_DOS_ARTEFATOS, 10)?.map { it.rotulo })
        assertFalse(armazem.atualizar(CHAVE_DOS_ARTEFATOS, 99, listOf(cena("Sem arquivo")))) // capítulo que nunca foi baixado
        assertFalse(armazem.tem(CHAVE_DOS_ARTEFATOS, 99))

        armazem.apagarLivro(CHAVE_DOS_ARTEFATOS, 1)
        assertNull(armazem.ler(CHAVE_DOS_ARTEFATOS, 10))
        assertNotNull(armazem.ler(CHAVE_DOS_ARTEFATOS, 20)) // o outro livro fica
    }

    // --- o download ---

    private class FonteComArtefatos : FonteDoDownload {
        var listas: Map<Int, List<Artefato>> = mapOf(10 to listOf(cena("Um")), 11 to listOf(cena("Dois")))
        val pedidos = mutableListOf<Int>()
        var falharApartirDe: Int? = null
        override suspend fun chave() = CHAVE_DOS_ARTEFATOS
        override suspend fun textos(livroId: Int) =
            ResultadoDaChamada.Sucesso(listOf(TextoDoCapitulo(10, 1, "Primeiro."), TextoDoCapitulo(11, 2, "Segundo.")))
        override suspend fun midias(livroId: Int) = ResultadoDaChamada.Sucesso(MidiasDoLivro(0, emptyList()))
        override suspend fun baixarImagem(imagemId: Int, tamanho: String, destino: File): ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)
        override suspend fun artefatos(capituloId: Int): List<Artefato>? {
            pedidos += capituloId
            return if (capituloId == falharApartirDe) null else listas[capituloId]
        }
    }

    private class TextosFalsos : ArmazemDeTextos {
        val guardados = mutableMapOf<Int, String>()
        override suspend fun ler(chave: ChaveDoCache, livroId: Int, capituloId: Int) = guardados[capituloId]
        override suspend fun gravar(chave: ChaveDoCache, livroId: Int, capituloId: Int, texto: String): Long { guardados[capituloId] = texto; return 1L }
        override suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int) = guardados.clear()
    }

    private fun TestScopeComBaixador(
        escopo: kotlinx.coroutines.CoroutineScope,
        fonte: FonteComArtefatos,
        artefatos: ArmazemDeArtefatos,
    ) = BaixadorDeLivros(
        fonte, TextosFalsos(), IndiceEmMemoria(), ArmazemDeImagensEmArquivos(pasta.newFolder(), kotlin.coroutines.EmptyCoroutineContext),
        DownloadsEmMemoria(), { TipoDeRede.WIFI }, escopo, agora = { 1L }, artefatos = artefatos,
    )

    @Test
    fun baixar_guarda_a_lista_de_artefatos_de_cada_capitulo_e_remover_apaga() = runTest {
        val armazem = armazem()
        val baixador = TestScopeComBaixador(this, FonteComArtefatos(), armazem)

        baixador.baixar(5)
        advanceUntilIdle()

        assertEquals(listOf("Um"), armazem.ler(CHAVE_DOS_ARTEFATOS, 10)?.map { it.rotulo })
        assertEquals(listOf("Dois"), armazem.ler(CHAVE_DOS_ARTEFATOS, 11)?.map { it.rotulo })

        baixador.remover(5)
        assertNull(armazem.ler(CHAVE_DOS_ARTEFATOS, 10))
    }

    @Test
    fun a_lista_que_falha_nao_derruba_o_download_e_a_conferencia_seguinte_completa() = runTest {
        val armazem = armazem()
        val fonte = FonteComArtefatos().also { it.falharApartirDe = 11 }
        val baixador = TestScopeComBaixador(this, fonte, armazem)

        baixador.baixar(5)
        advanceUntilIdle()

        assertTrue(baixador.estadoDe(5) is EstadoDoDownload.Baixado)
        assertTrue(armazem.tem(CHAVE_DOS_ARTEFATOS, 10))
        assertFalse(armazem.tem(CHAVE_DOS_ARTEFATOS, 11))

        fonte.falharApartirDe = null
        fonte.pedidos.clear()
        baixador.completarSeBaixado(5)
        advanceUntilIdle()

        assertEquals(listOf(11), fonte.pedidos) // só pede a que faltava
        assertTrue(armazem.tem(CHAVE_DOS_ARTEFATOS, 11))
    }

    // --- a leitura ---

    private fun repositorio(url: String, guardados: ArmazemDeArtefatos) =
        RepositorioDeArtefatosPeloRetrofit(
            ProvedorDeApi(object : ArmazenamentoDeConfiguracao {
                override val urlDoServidor: Flow<String?> = flowOf(url)
                override suspend fun salvarUrlDoServidor(url: String) = Unit
            }),
            guardados,
        )

    @Test
    fun sem_resposta_do_servidor_vale_a_lista_guardada() = runTest {
        val armazem = armazem()
        val offline = ChaveDoCache("http://127.0.0.1:1")
        armazem.gravar(offline, 1, 10, listOf(cena("Guardada")))

        val resultado = repositorio("http://127.0.0.1:1", armazem).ler(10)

        assertEquals(listOf("Guardada"), (resultado as ResultadoDaChamada.Sucesso).dado.map { it.rotulo })
    }

    @Test
    fun sem_resposta_e_sem_lista_guardada_a_falha_de_sempre() = runTest {
        assertTrue(repositorio("http://127.0.0.1:1", armazem()).ler(10) is ResultadoDaChamada.Falha)
    }

    @Test
    fun com_resposta_a_lista_nova_atualiza_a_guardada_mas_so_de_capitulo_que_ja_tem() = runTest {
        servidor.start()
        val url = servidor.url("/").toString().trimEnd('/')
        val chave = ChaveDoCache(url)
        val armazem = armazem()
        armazem.gravar(chave, 1, 10, listOf(cena("Velha")))
        val corpo = """{"artefatos":[{"tipo":"CENA","rotulo":"Nova","situacao":"ILUSTRADO"}]}"""
        servidor.enqueue(MockResponse().setBody(corpo))
        servidor.enqueue(MockResponse().setBody(corpo))
        val repositorio = repositorio(url, armazem)

        repositorio.ler(10)
        repositorio.ler(30) // capítulo lido de passagem

        assertEquals(listOf("Nova"), armazem.ler(chave, 10)?.map { it.rotulo })
        assertFalse(armazem.tem(chave, 30))
    }

    @Test
    fun erro_do_servidor_nao_cai_na_lista_guardada() = runTest {
        servidor.start()
        val url = servidor.url("/").toString().trimEnd('/')
        val armazem = armazem()
        armazem.gravar(ChaveDoCache(url), 1, 10, listOf(cena("Guardada")))
        servidor.enqueue(MockResponse().setResponseCode(500))

        assertTrue(repositorio(url, armazem).ler(10) is ResultadoDaChamada.Falha)
    }
}
