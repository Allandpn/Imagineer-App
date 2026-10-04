package com.allan.imagineer.local

import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.MidiasDoLivro
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.TextoDoCapitulo
import com.allan.imagineer.telas.menu.EspacoNaTela
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private val CHAVE = ChaveDoCache("http://100.1.2.3:8000")

private class TextosFalsos : ArmazemDeTextos {
    val apagados = mutableListOf<Int>()
    override suspend fun ler(chave: ChaveDoCache, livroId: Int, capituloId: Int): String? = null
    override suspend fun gravar(chave: ChaveDoCache, livroId: Int, capituloId: Int, texto: String) = 0L
    override suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int) { apagados += livroId }
}

private class EspacoFalso(var textos: MutableMap<Int, Long>, val titulos: Map<Int, String>) : EspacoLocal {
    override suspend fun livros(chave: ChaveDoCache) = titulos.map { it.key to it.value }
    override suspend fun bytesDeTextos(chave: ChaveDoCache): Map<Int, Long> = textos.toMap()
    override suspend fun apagarRegistrosDeTexto(chave: ChaveDoCache, livroId: Int) { textos.remove(livroId) }
}

private class RegistroFalso(val linhas: MutableMap<Int, DownloadGuardado> = mutableMapOf()) : RegistroDeDownloads {
    override suspend fun baixado(chave: ChaveDoCache, livroId: Int) = linhas[livroId]
    override suspend fun guardar(chave: ChaveDoCache, download: DownloadGuardado) { linhas[download.livroId] = download }
    override suspend fun apagar(chave: ChaveDoCache, livroId: Int) { linhas.remove(livroId) }
    override suspend fun todos(chave: ChaveDoCache) = linhas.values.toList()
}

private class FonteSemRede : FonteDoDownload {
    override suspend fun chave() = CHAVE
    override suspend fun textos(livroId: Int) = ResultadoDaChamada.Falha("x")
    override suspend fun midias(livroId: Int) = ResultadoDaChamada.Falha("x")
    override suspend fun baixarImagem(imagemId: Int, tamanho: String, destino: File) = ResultadoDaChamada.Falha("x")
}

private class ImagensFalsas : ArmazemDeImagens {
    val apagadas = mutableListOf<Int>()
    override fun arquivo(chave: ChaveDoCache, imagemId: Int, tamanho: String): File? = null
    override suspend fun gravar(chave: ChaveDoCache, imagemId: Int, tamanho: String, escrever: suspend (File) -> Boolean) = false
    override suspend fun tamanhoEmBytes(chave: ChaveDoCache, imagemIds: Collection<Int>) = 0L
    override suspend fun apagar(chave: ChaveDoCache, imagemIds: Collection<Int>) { apagadas += imagemIds }
}

private class IndiceNulo : IndiceLocal {
    override suspend fun livro(chave: ChaveDoCache, livroId: Int): LivroGuardado? = null
    override suspend fun guardarLivro(chave: ChaveDoCache, detalhe: LivroDetalhe) {}
    override suspend fun texto(chave: ChaveDoCache, capituloId: Int): TextoGuardado? = null
    override suspend fun registrarTexto(chave: ChaveDoCache, texto: TextoGuardado) {}
    override suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int) {}
}

/** A tela Armazenamento: o espaço por livro e como liberar (PL10, regra A11). */
@OptIn(ExperimentalCoroutinesApi::class)
class GerenteDeArmazenamentoTest {

    private val registro = RegistroFalso(mutableMapOf(2 to DownloadGuardado(2, 1L, 5_000L, listOf(1, 2))))
    private val espaco = EspacoFalso(mutableMapOf(1 to 700L, 2 to 300L, 3 to 0L), mapOf(1 to "Leve", 2 to "Baixado", 3 to "Vazio"))
    private val textos = TextosFalsos()
    private val imagens = ImagensFalsas()

    private fun gerente(escopo: kotlinx.coroutines.CoroutineScope): GerenteDeArmazenamento {
        val baixador = BaixadorDeLivros(FonteSemRede(), textos, IndiceNulo(), imagens, registro, { TipoDeRede.WIFI }, escopo)
        return GerenteDeArmazenamento(FonteSemRede(), espaco, textos, registro, baixador)
    }

    @Test
    fun lista_separa_leve_e_baixado_ordena_pelo_maior_e_esconde_o_que_nao_ocupa() = runTest {
        val lista = gerente(this).listar()

        assertEquals(listOf("Baixado", "Leve"), lista.map { it.titulo })  // "Vazio" não ocupa nada
        val baixado = lista[0]
        assertEquals(true to 0L, baixado.baixado to baixado.bytesLeve)  // o texto de um livro Baixado conta no Baixado
        assertEquals(5_000L, baixado.bytesBaixado)
        assertEquals(700L, lista[1].bytesLeve)
    }

    @Test
    fun limpar_o_cache_de_um_livro_leve_apaga_o_texto_e_diz_quanto_liberou() = runTest {
        val liberado = gerente(this).limparCache(1)

        assertEquals(700L, liberado)
        assertEquals(listOf(1), textos.apagados)
    }

    @Test
    fun limpar_o_cache_de_um_livro_baixado_nao_apaga_nada() = runTest {
        val liberado = gerente(this).limparCache(2)

        assertEquals(0L, liberado)
        assertTrue(textos.apagados.isEmpty())
        assertTrue(registro.linhas.containsKey(2))
    }

    @Test
    fun limpar_todo_o_cache_nunca_toca_nos_baixados() = runTest {
        val liberado = gerente(this).limparTodoOCache()

        assertEquals(700L, liberado)
        assertEquals(listOf(1), textos.apagados)
        assertTrue(registro.linhas.containsKey(2))
    }

    @Test
    fun remover_o_download_apaga_as_imagens_e_a_marca_e_diz_quanto_liberou() = runTest {
        val liberado = gerente(this).removerDownload(2)

        assertEquals(5_000L, liberado)
        assertEquals(listOf(1, 2), imagens.apagadas)
        assertTrue(registro.linhas.isEmpty())
    }

    @Test
    fun a_conta_da_tela_soma_tudo_e_so_o_leve_conta_como_limpavel() {
        val livros = listOf(EspacoPorLivro(1, "A", 700, 0, false), EspacoPorLivro(2, "B", 0, 5_000, true))

        assertEquals(5_700L, EspacoNaTela.total(livros))
        assertEquals(700L, EspacoNaTela.limpavel(livros))
    }
}
