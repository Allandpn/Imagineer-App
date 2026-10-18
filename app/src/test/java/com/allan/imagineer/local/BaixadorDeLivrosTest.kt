package com.allan.imagineer.local

import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.MidiaDoLivro
import com.allan.imagineer.rede.MidiasDoLivro
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.TextoDoCapitulo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private val CHAVE = ChaveDoCache("http://100.1.2.3:8000")

private class TextosEmMemoria : ArmazemDeTextos {
    val guardados = mutableMapOf<Int, String>()
    override suspend fun ler(chave: ChaveDoCache, livroId: Int, capituloId: Int) = guardados[capituloId]
    override suspend fun gravar(chave: ChaveDoCache, livroId: Int, capituloId: Int, texto: String): Long {
        guardados[capituloId] = texto
        return texto.length.toLong()
    }
    override suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int) = guardados.clear()
}

private class IndiceVazio : IndiceLocal {
    override suspend fun livro(chave: ChaveDoCache, livroId: Int): LivroGuardado? = null
    override suspend fun livros(chave: ChaveDoCache): List<LivroGuardado> = emptyList()
    override suspend fun guardarLivro(chave: ChaveDoCache, detalhe: LivroDetalhe) {}
    override suspend fun texto(chave: ChaveDoCache, capituloId: Int): TextoGuardado? = null
    override suspend fun registrarTexto(chave: ChaveDoCache, texto: TextoGuardado) {}
    override suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int) {}
}

private class RegistroEmMemoria : RegistroDeDownloads {
    val linhas = mutableMapOf<Int, DownloadGuardado>()
    override suspend fun baixado(chave: ChaveDoCache, livroId: Int) = linhas[livroId]
    override suspend fun guardar(chave: ChaveDoCache, download: DownloadGuardado) { linhas[download.livroId] = download }
    override suspend fun apagar(chave: ChaveDoCache, livroId: Int) { linhas.remove(livroId) }
    override suspend fun todos(chave: ChaveDoCache) = linhas.values.toList()
}

private class FonteFalsa : FonteDoDownload {
    var imagens = mutableListOf(1, 2)
    var falhaNaImagem: Int? = null
    val baixadas = mutableListOf<Pair<Int, String>>()
    var trava: CompletableDeferred<Unit>? = null

    /** A revisão que o servidor diz ter agora (DL2) e o texto do primeiro capítulo, que um teste muda para provar que a atualização o regrava. */
    var revisaoDoLivro: Int? = 0
    var textoDoPrimeiro = "Primeiro."

    /** A ficha do livro que o servidor devolve (PL13). */
    var ficha: LivroDetalhe? = null

    override suspend fun chave() = CHAVE
    override suspend fun livro(livroId: Int): LivroDetalhe? = ficha
    override suspend fun revisao(livroId: Int): Int? = revisaoDoLivro
    override suspend fun textos(livroId: Int) = ResultadoDaChamada.Sucesso(listOf(TextoDoCapitulo(10, 1, textoDoPrimeiro), TextoDoCapitulo(11, 2, "Segundo.")))
    override suspend fun midias(livroId: Int) =
        ResultadoDaChamada.Sucesso(MidiasDoLivro(imagens.size * 1000L, imagens.map { MidiaDoLivro(it, 5, 1000, "image/png") }))

    override suspend fun baixarImagem(imagemId: Int, tamanho: String, destino: File): ResultadoDaChamada<Unit> {
        trava?.await()
        if (imagemId == falhaNaImagem) return ResultadoDaChamada.Falha("Servidor fora do ar.")
        destino.writeBytes(ByteArray(100) { 7 })
        baixadas += imagemId to tamanho
        return ResultadoDaChamada.Sucesso(Unit)
    }
}

/** O endereço de imagem, o armazém e o baixador (PL1 a PL5). */
@OptIn(ExperimentalCoroutinesApi::class)
class BaixadorDeLivrosTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private var tipoDeRede = TipoDeRede.WIFI

    private fun armazem() = ArmazemDeImagensEmArquivos(pasta.newFolder("imagens"), kotlin.coroutines.EmptyCoroutineContext)

    @Test
    fun o_endereco_da_imagem_diz_servidor_imagem_e_tamanho() {
        assertEquals(EnderecoDeImagem("http://100.1.2.3:8000", 7, "miniatura"), analisarEnderecoDeImagem("http://100.1.2.3:8000/imagens/7/arquivo?tamanho=miniatura"))
        assertEquals(EnderecoDeImagem("http://h:8000", 12, "original"), analisarEnderecoDeImagem("http://h:8000/imagens/12/arquivo"))
        assertNull(analisarEnderecoDeImagem("http://h:8000/capitulos/3"))
        assertNull(analisarEnderecoDeImagem("https://exemplo.com/foto.png"))
    }

    @Test
    fun o_armazem_grava_inteiro_ou_nada_e_nao_deixa_arquivo_temporario() = runTest {
        val armazem = armazem()

        assertFalse(armazem.gravar(CHAVE, 1, "leitura") { it.writeBytes(byteArrayOf(1, 2)); false })
        assertNull(armazem.arquivo(CHAVE, 1, "leitura"))
        assertTrue(armazem.gravar(CHAVE, 1, "leitura") { it.writeBytes(byteArrayOf(1, 2, 3)); true })
        assertEquals(3L, armazem.arquivo(CHAVE, 1, "leitura")!!.length())
        assertEquals(3L, armazem.tamanhoEmBytes(CHAVE, listOf(1)))
        assertTrue(armazem.arquivo(CHAVE, 1, "leitura")!!.parentFile!!.listFiles()!!.none { it.name.endsWith(".tmp") })

        armazem.apagar(CHAVE, listOf(1))
        assertNull(armazem.arquivo(CHAVE, 1, "leitura"))
    }

    private fun baixador(escopo: kotlinx.coroutines.CoroutineScope, fonte: FonteFalsa, textos: TextosEmMemoria, armazem: ArmazemDeImagens, registro: RegistroEmMemoria) =
        BaixadorDeLivros(fonte, textos, IndiceVazio(), armazem, registro, { tipoDeRede }, escopo, agora = { 1234L })

    @Test
    fun baixar_traz_o_texto_e_os_tres_tamanhos_de_cada_imagem_e_marca_como_baixado() = runTest {
        val fonte = FonteFalsa(); val textos = TextosEmMemoria(); val armazem = armazem(); val registro = RegistroEmMemoria()
        val baixador = baixador(this, fonte, textos, armazem, registro)

        baixador.baixar(5)
        advanceUntilIdle()

        assertEquals(mapOf(10 to "Primeiro.", 11 to "Segundo."), textos.guardados)
        assertEquals(6, fonte.baixadas.size)  // 2 imagens × 3 tamanhos
        TAMANHOS_DE_IMAGEM.forEach { assertNotNull(armazem.arquivo(CHAVE, 2, it)) }
        val estado = baixador.estadoDe(5) as EstadoDoDownload.Baixado
        assertEquals(1234L, estado.em)
        assertEquals(listOf(1, 2), registro.linhas[5]!!.imagensIds)
    }

    @Test
    fun baixar_guarda_a_ficha_do_livro_no_indice_para_a_biblioteca_sem_conexao() = runTest {
        val fonte = FonteFalsa().also { it.ficha = com.allan.imagineer.rede.livroDeTeste(id = 5, revisao = 7) }
        val indice = IndiceEmMemoria()
        val baixador = BaixadorDeLivros(fonte, TextosEmMemoria(), indice, armazem(), RegistroEmMemoria(), { tipoDeRede }, this, agora = { 1234L })

        baixador.baixar(5)
        advanceUntilIdle()

        assertEquals(7, indice.livro(CHAVE, 5)?.revisao)
        assertEquals(7, (baixador.estadoDe(5) as EstadoDoDownload.Baixado).revisao)
    }

    @Test
    fun retomar_pula_o_que_ja_esta_gravado() = runTest {
        val fonte = FonteFalsa(); val armazem = armazem()
        val baixador = baixador(this, fonte, TextosEmMemoria(), armazem, RegistroEmMemoria())
        fonte.falhaNaImagem = 2
        baixador.baixar(5)
        advanceUntilIdle()
        assertTrue(baixador.estadoDe(5) is EstadoDoDownload.Falhou)
        assertEquals(3, fonte.baixadas.size)  // a imagem 1 inteira ficou gravada

        fonte.falhaNaImagem = null
        fonte.baixadas.clear()
        baixador.baixar(5)
        advanceUntilIdle()

        assertEquals(listOf(2 to "miniatura", 2 to "leitura", 2 to "original"), fonte.baixadas)  // só o que faltava
        assertTrue(baixador.estadoDe(5) is EstadoDoDownload.Baixado)
    }

    @Test
    fun em_dados_moveis_com_so_wifi_para_dizendo_isso_e_sem_conexao_tambem() = runTest {
        val fonte = FonteFalsa()
        val baixador = baixador(this, fonte, TextosEmMemoria(), armazem(), RegistroEmMemoria())

        tipoDeRede = TipoDeRede.MOVEL
        baixador.baixar(5, somenteWifi = true)
        advanceUntilIdle()
        assertEquals("Aguardando Wi-Fi", (baixador.estadoDe(5) as EstadoDoDownload.Pausado).motivo)
        assertTrue(fonte.baixadas.isEmpty())

        tipoDeRede = TipoDeRede.NENHUMA
        baixador.baixar(5)
        advanceUntilIdle()
        assertEquals("Sem conexão", (baixador.estadoDe(5) as EstadoDoDownload.Pausado).motivo)

        tipoDeRede = TipoDeRede.MOVEL
        baixador.baixar(5, somenteWifi = false)  // a pessoa desmarcou "Só em Wi-Fi"
        advanceUntilIdle()
        assertTrue(baixador.estadoDe(5) is EstadoDoDownload.Baixado)
    }

    @Test
    fun a_estimativa_desconta_os_originais_que_ja_estao_no_aparelho() = runTest {
        val fonte = FonteFalsa(); val armazem = armazem()
        val baixador = baixador(this, fonte, TextosEmMemoria(), armazem, RegistroEmMemoria())

        val antes = (baixador.estimar(5) as ResultadoDaChamada.Sucesso).dado
        assertEquals(2000L, antes.bytesRestantes)

        armazem.gravar(CHAVE, 1, "original") { it.writeBytes(ByteArray(1000)); true }
        val depois = (baixador.estimar(5) as ResultadoDaChamada.Sucesso).dado
        assertEquals(1000L, depois.bytesRestantes)
        assertEquals(2, depois.imagens)
    }

    @Test
    fun remover_apaga_imagens_textos_e_a_marca() = runTest {
        val fonte = FonteFalsa(); val textos = TextosEmMemoria(); val armazem = armazem(); val registro = RegistroEmMemoria()
        val baixador = baixador(this, fonte, textos, armazem, registro)
        baixador.baixar(5)
        advanceUntilIdle()

        baixador.remover(5)

        assertEquals(EstadoDoDownload.NaoBaixado, baixador.estadoDe(5))
        assertNull(armazem.arquivo(CHAVE, 1, "original"))
        assertTrue(textos.guardados.isEmpty())
        assertTrue(registro.linhas.isEmpty())
    }

    @Test
    fun carregar_le_do_registro_se_o_livro_ja_esta_baixado() = runTest {
        val registro = RegistroEmMemoria().also { it.linhas[5] = DownloadGuardado(5, 99L, 4096L, listOf(1)) }
        val baixador = baixador(this, FonteFalsa(), TextosEmMemoria(), armazem(), registro)

        baixador.carregar(5)

        assertEquals(EstadoDoDownload.Baixado(99L, 4096L), baixador.estadoDe(5))
    }

    @Test
    fun livro_baixado_completa_so_o_que_o_servidor_ganhou_e_so_em_wifi() = runTest {
        val fonte = FonteFalsa(); val armazem = armazem(); val registro = RegistroEmMemoria()
        val baixador = baixador(this, fonte, TextosEmMemoria(), armazem, registro)
        baixador.baixar(5)
        advanceUntilIdle()
        fonte.baixadas.clear()
        fonte.imagens.add(3)  // o servidor ganhou uma imagem

        tipoDeRede = TipoDeRede.MOVEL
        baixador.completarSeBaixado(5)
        advanceUntilIdle()
        assertTrue(fonte.baixadas.isEmpty())  // em dados móveis não completa

        tipoDeRede = TipoDeRede.WIFI
        baixador.completarSeBaixado(5)
        advanceUntilIdle()
        assertEquals(listOf(3 to "miniatura", 3 to "leitura", 3 to "original"), fonte.baixadas)
        assertEquals(listOf(1, 2, 3), registro.linhas[5]!!.imagensIds)
        assertTrue(baixador.estadoDe(5) is EstadoDoDownload.Baixado)
    }

    @Test
    fun livro_nao_baixado_nao_completa() = runTest {
        val fonte = FonteFalsa()
        val baixador = baixador(this, fonte, TextosEmMemoria(), armazem(), RegistroEmMemoria())

        baixador.completarSeBaixado(5)
        advanceUntilIdle()

        assertTrue(fonte.baixadas.isEmpty())
    }

    // ---- DL2, DL3: a revisão baixada e o "Atualizar" (item 7.5g) ----

    @Test
    fun DL2_o_download_guarda_a_revisao_que_o_servidor_tinha_e_o_estado_a_carrega() = runTest {
        val fonte = FonteFalsa().also { it.revisaoDoLivro = 7 }; val registro = RegistroEmMemoria()
        val baixador = baixador(this, fonte, TextosEmMemoria(), armazem(), registro)

        baixador.baixar(5)
        advanceUntilIdle()

        assertEquals(7, registro.linhas[5]!!.revisao)
        assertEquals(7, (baixador.estadoDe(5) as EstadoDoDownload.Baixado).revisao)
    }

    @Test
    fun DL2_sem_saber_a_revisao_o_download_segue_e_guarda_zero() = runTest {
        val fonte = FonteFalsa().also { it.revisaoDoLivro = null }; val registro = RegistroEmMemoria()
        val baixador = baixador(this, fonte, TextosEmMemoria(), armazem(), registro)

        baixador.baixar(5)
        advanceUntilIdle()

        assertEquals(0, registro.linhas[5]!!.revisao)
    }

    @Test
    fun DL2_ao_carregar_a_revisao_volta_do_registro() = runTest {
        val registro = RegistroEmMemoria().also { it.linhas[5] = DownloadGuardado(5, 99L, 50L, listOf(1, 2), revisao = 4) }
        val baixador = baixador(this, FonteFalsa(), TextosEmMemoria(), armazem(), registro)

        baixador.carregar(5)

        assertEquals(EstadoDoDownload.Baixado(99L, 50L, 4), baixador.estadoDe(5))
    }

    @Test
    fun DL3_atualizar_regrava_o_texto_e_guarda_a_revisao_nova() = runTest {
        val fonte = FonteFalsa().also { it.revisaoDoLivro = 1 }; val textos = TextosEmMemoria(); val registro = RegistroEmMemoria()
        val baixador = baixador(this, fonte, textos, armazem(), registro)
        baixador.baixar(5); advanceUntilIdle()
        assertEquals("Primeiro.", textos.guardados[10])

        fonte.revisaoDoLivro = 2
        fonte.textoDoPrimeiro = "Primeiro, corrigido."
        baixador.atualizar(5); advanceUntilIdle()

        assertEquals("Primeiro, corrigido.", textos.guardados[10]) // um download comum pularia o texto que já estava gravado
        assertEquals(2, (baixador.estadoDe(5) as EstadoDoDownload.Baixado).revisao)
        assertEquals(2, registro.linhas[5]!!.revisao)
    }

    @Test
    fun DL3_atualizar_nao_baixa_de_novo_as_imagens_que_ja_estao_no_aparelho() = runTest {
        val fonte = FonteFalsa(); val baixador = baixador(this, fonte, TextosEmMemoria(), armazem(), RegistroEmMemoria())
        baixador.baixar(5); advanceUntilIdle()
        fonte.baixadas.clear()
        fonte.imagens.add(3) // o servidor ganhou uma imagem

        baixador.atualizar(5); advanceUntilIdle()

        assertEquals(listOf(3 to "miniatura", 3 to "leitura", 3 to "original"), fonte.baixadas)
    }

    @Test
    fun DL3_baixar_sem_atualizar_nao_regrava_o_texto() = runTest {
        val fonte = FonteFalsa(); val textos = TextosEmMemoria()
        val baixador = baixador(this, fonte, textos, armazem(), RegistroEmMemoria())
        baixador.baixar(5); advanceUntilIdle()
        fonte.textoDoPrimeiro = "Mudou."

        baixador.baixar(5); advanceUntilIdle()

        assertEquals("Primeiro.", textos.guardados[10])
    }

    @Test
    fun DL3_o_completar_silencioso_nao_diz_que_o_livro_esta_em_dia() = runTest {
        val fonte = FonteFalsa().also { it.revisaoDoLivro = 1 }; val registro = RegistroEmMemoria()
        val baixador = baixador(this, fonte, TextosEmMemoria(), armazem(), registro)
        baixador.baixar(5); advanceUntilIdle()
        fonte.revisaoDoLivro = 5 // o livro mudou; o texto não foi regravado

        baixador.completarSeBaixado(5); advanceUntilIdle()

        assertEquals("o completar silencioso mantém a revisão de antes", 1, registro.linhas[5]!!.revisao)
        assertEquals(1, (baixador.estadoDe(5) as EstadoDoDownload.Baixado).revisao)
    }

    @Test
    fun DL3_cancelar_uma_atualizacao_nao_apaga_o_livro_que_ja_estava_baixado() = runTest {
        val fonte = FonteFalsa().also { it.revisaoDoLivro = 1 }; val textos = TextosEmMemoria(); val registro = RegistroEmMemoria()
        val armazem = armazem()
        val baixador = baixador(this, fonte, textos, armazem, registro)
        baixador.baixar(5); advanceUntilIdle()
        fonte.revisaoDoLivro = 2
        fonte.trava = CompletableDeferred()
        fonte.imagens.add(3)
        baixador.atualizar(5); runCurrent()
        assertTrue(baixador.estadoDe(5) is EstadoDoDownload.Baixando)

        baixador.cancelar(5)
        fonte.trava!!.complete(Unit); advanceUntilIdle()

        assertTrue("o livro continua baixado", baixador.estadoDe(5) is EstadoDoDownload.Baixado)
        assertEquals(1, (baixador.estadoDe(5) as EstadoDoDownload.Baixado).revisao)
        assertEquals(mapOf(10 to "Primeiro.", 11 to "Segundo."), textos.guardados)
        assertNotNull(registro.linhas[5])
    }
}
