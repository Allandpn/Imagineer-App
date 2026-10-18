package com.allan.imagineer.local

import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.ArtefatosDoCapitulo
import com.allan.imagineer.rede.jsonDoImagineer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Onde moram as **listas de artefatos** dos capítulos baixados (PL16): o que diz ao texto *onde* pôr cada imagem e cada ícone. Os arquivos das
 * imagens estão no [ArmazemDeImagens]; sem esta lista, o capítulo offline abriria sem elas.
 *
 * Qualquer função pode lançar exceção (disco): quem chama trata como "não tenho" (A10).
 */
interface ArmazemDeArtefatos {
    /** A lista guardada do capítulo, ou `null` se não há (ou se está ilegível). */
    suspend fun ler(chave: ChaveDoCache, capituloId: Int): List<Artefato>?

    /** O capítulo tem lista guardada? */
    suspend fun tem(chave: ChaveDoCache, capituloId: Int): Boolean

    /** Guarda (ou troca) a lista do capítulo [capituloId] do livro [livroId]. */
    suspend fun gravar(chave: ChaveDoCache, livroId: Int, capituloId: Int, artefatos: List<Artefato>)

    /** Troca a lista **só se o capítulo já tem uma** (ler de passagem não enche o aparelho). Devolve se trocou. */
    suspend fun atualizar(chave: ChaveDoCache, capituloId: Int, artefatos: List<Artefato>): Boolean

    /** Apaga as listas do livro. */
    suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int)
}

/** A versão de verdade: um JSON por capítulo em `<raiz>/<identificador da chave>/<livro>-<capítulo>.json`, na pasta sem backup (A12). */
class ArmazemDeArtefatosEmArquivos(
    private val raiz: File,
    private val contexto: kotlin.coroutines.CoroutineContext = Dispatchers.IO,
) : ArmazemDeArtefatos {

    private fun pasta(chave: ChaveDoCache) = File(raiz, chave.identificador)

    private fun doCapitulo(chave: ChaveDoCache, capituloId: Int): File? =
        pasta(chave).listFiles { arquivo -> arquivo.name.endsWith("-$capituloId.json") }?.firstOrNull { it.isFile && it.length() > 0 }

    override suspend fun ler(chave: ChaveDoCache, capituloId: Int): List<Artefato>? = withContext(contexto) {
        val arquivo = doCapitulo(chave, capituloId) ?: return@withContext null
        try {
            jsonDoImagineer.decodeFromString<ArtefatosDoCapitulo>(arquivo.readText()).artefatos
        } catch (erro: SerializationException) {
            null // formato que o app não entende mais: vale como "não tenho"
        }
    }

    override suspend fun tem(chave: ChaveDoCache, capituloId: Int): Boolean = withContext(contexto) { doCapitulo(chave, capituloId) != null }

    override suspend fun gravar(chave: ChaveDoCache, livroId: Int, capituloId: Int, artefatos: List<Artefato>) {
        escrever(File(pasta(chave), "$livroId-$capituloId.json"), artefatos)
    }

    override suspend fun atualizar(chave: ChaveDoCache, capituloId: Int, artefatos: List<Artefato>): Boolean {
        val existente = withContext(contexto) { doCapitulo(chave, capituloId) } ?: return false
        escrever(existente, artefatos)
        return true
    }

    override suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int) {
        withContext(contexto) {
            pasta(chave).listFiles { arquivo -> arquivo.name.startsWith("$livroId-") && arquivo.name.endsWith(".json") }?.forEach { it.delete() }
        }
    }

    /** Inteiro ou nada: escreve num temporário e só então troca o arquivo (A5). */
    private suspend fun escrever(destino: File, artefatos: List<Artefato>) = withContext(contexto) {
        destino.parentFile?.mkdirs()
        val temporario = File(destino.parentFile, "${destino.name}.tmp")
        try {
            temporario.writeText(jsonDoImagineer.encodeToString(ArtefatosDoCapitulo.serializer(), ArtefatosDoCapitulo(artefatos)))
            Files.move(temporario.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (erro: IOException) {
            temporario.delete()
            throw erro
        }
    }
}
