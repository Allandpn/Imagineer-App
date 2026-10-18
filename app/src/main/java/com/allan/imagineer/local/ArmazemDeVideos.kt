package com.allan.imagineer.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Onde moram os **vídeos** das cenas dos livros baixados (PL18): um arquivo por vídeo, para o player tocá-lo sem rede e para tirar dele o
 * quadro que o capítulo mostra. Os vídeos são importados pela própria pessoa (até 50 MB cada), então costumam ser poucos.
 */
interface ArmazemDeVideos {
    /** O arquivo do vídeo, ou `null` se não está guardado. Síncrono: só olha o disco, e o player o usa. */
    fun arquivo(chave: ChaveDoCache, videoId: Int): File?

    /**
     * Grava o vídeo **inteiro ou nada** (A5): [escrever] recebe um arquivo temporário e devolve `true` se o encheu direito; só então ele vira o
     * definitivo. Devolve se gravou.
     */
    suspend fun gravar(chave: ChaveDoCache, livroId: Int, videoId: Int, escrever: suspend (File) -> Boolean): Boolean

    /** Quantos bytes os vídeos do livro ocupam. */
    suspend fun tamanhoDoLivro(chave: ChaveDoCache, livroId: Int): Long

    /** Apaga os vídeos do livro. */
    suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int)
}

/** A versão de verdade: `<raiz>/<identificador da chave>/<livro>-<vídeo>.mp4`, na pasta sem backup (A12). */
class ArmazemDeVideosEmArquivos(
    private val raiz: File,
    private val contexto: kotlin.coroutines.CoroutineContext = Dispatchers.IO,
) : ArmazemDeVideos {

    private fun pasta(chave: ChaveDoCache) = File(raiz, chave.identificador)

    override fun arquivo(chave: ChaveDoCache, videoId: Int): File? =
        pasta(chave).listFiles { arquivo -> arquivo.name.endsWith("-$videoId.mp4") }?.firstOrNull { it.isFile && it.length() > 0 }

    override suspend fun gravar(chave: ChaveDoCache, livroId: Int, videoId: Int, escrever: suspend (File) -> Boolean): Boolean =
        withContext(contexto) {
            val destino = File(pasta(chave), "$livroId-$videoId.mp4")
            destino.parentFile?.mkdirs()
            val temporario = File(destino.parentFile, "${destino.name}.tmp")
            try {
                if (!escrever(temporario) || !temporario.isFile || temporario.length() == 0L) {
                    temporario.delete()
                    return@withContext false
                }
                Files.move(temporario.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
                true
            } catch (erro: IOException) {
                temporario.delete()
                false
            }
        }

    override suspend fun tamanhoDoLivro(chave: ChaveDoCache, livroId: Int): Long = withContext(contexto) {
        pasta(chave).listFiles { arquivo -> arquivo.name.startsWith("$livroId-") && arquivo.name.endsWith(".mp4") }?.sumOf { it.length() } ?: 0L
    }

    override suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int) {
        withContext(contexto) {
            pasta(chave).listFiles { arquivo -> arquivo.name.startsWith("$livroId-") && arquivo.name.endsWith(".mp4") }?.forEach { it.delete() }
        }
    }
}
