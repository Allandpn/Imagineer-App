package com.allan.imagineer.rede

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

// O primeiro quadro de um vídeo importado (item 4.8, VD17): é o que o capítulo mostra no lugar do vídeo, com o botão de tocar por cima.

/** A proporção (largura por altura) de um quadro; `null` se não se sabe o tamanho. */
fun proporcaoDoQuadro(largura: Int, altura: Int): Float? = if (largura > 0 && altura > 0) largura.toFloat() / altura else null

/**
 * Tira, guarda e entrega o **primeiro quadro** de cada vídeo. O servidor não gera miniatura de vídeo (não há `ffmpeg` no contêiner), então
 * o aparelho baixa o vídeo **uma vez**, tira o quadro, guarda como `poster_{id}.jpg` no cache e apaga o vídeo baixado: da segunda vez em
 * diante lê só o JPEG. Os que acabaram de ser lidos ficam também na memória.
 */
class PostersDeVideo(
    private val provedor: ProvedorDeApi,
    private val pasta: File,
) {
    private val memoria = LruCache<Int, Bitmap>(12)

    /** Uma extração por vez: dois quadros do mesmo vídeo (ou vários vídeos) não baixam ao mesmo tempo. */
    private val trava = Mutex()

    /** O primeiro quadro do vídeo [videoId], ou `null` se não deu (sem conexão, vídeo sem imagem legível...). */
    suspend fun obter(videoId: Int): Bitmap? {
        memoria.get(videoId)?.let { return it }
        return trava.withLock {
            memoria.get(videoId)?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val guardado = File(pasta, "poster_$videoId.jpg")
                lerDoDisco(guardado) ?: extrair(videoId, guardado)
            }?.also { memoria.put(videoId, it) }
        }
    }

    private fun lerDoDisco(arquivo: File): Bitmap? = if (arquivo.isFile) BitmapFactory.decodeFile(arquivo.path) else null

    private suspend fun extrair(videoId: Int, destino: File): Bitmap? {
        val api = provedor.obter() ?: return null
        pasta.mkdirs()
        val baixado = File(pasta, "baixando_$videoId.mp4")
        return try {
            val resposta = chamarApi { api.baixarVideo(videoId) } as? ResultadoDaChamada.Sucesso ?: return null
            resposta.dado.use { corpo -> corpo.byteStream().use { entrada -> baixado.outputStream().use { saida -> entrada.copyTo(saida) } } }
            val leitor = MediaMetadataRetriever()
            try {
                leitor.setDataSource(baixado.path)
                val quadro = leitor.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
                destino.outputStream().use { quadro.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                quadro
            } finally {
                leitor.release()
            }
        } catch (erro: IOException) {
            null
        } catch (erro: RuntimeException) {
            null // o Android lança IllegalArgumentException/RuntimeException para um arquivo que não consegue abrir
        } finally {
            baixado.delete()
        }
    }
}
