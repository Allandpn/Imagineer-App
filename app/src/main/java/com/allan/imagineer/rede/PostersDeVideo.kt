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
 * O nome do arquivo do primeiro quadro de um vídeo (PD7). O número do vídeo vale **por servidor**: o PC e o Raspberry Pi, por exemplo,
 * têm cada um o seu "vídeo 1". Guardar só pelo número devolvia o quadro de **outro** vídeo para quem já falou com mais de um servidor, e a
 * miniatura do vídeo novo saía igual à do primeiro importado em outro lugar. Por isso o nome leva o identificador do servidor.
 */
fun nomeDoPoster(identificadorDoServidor: String, videoId: Int): String = "poster_${identificadorDoServidor}_$videoId.jpg"

/** Um arquivo `poster_{número}.jpg`, do tempo em que o nome não dizia o servidor: não dá para saber de qual vídeo ele é, então se descarta (PD7). */
fun ehPosterSemServidor(nomeDoArquivo: String): Boolean = Regex("poster_\\d+\\.jpg").matches(nomeDoArquivo)

/**
 * Tira, guarda e entrega o **primeiro quadro** de cada vídeo. O servidor não gera miniatura de vídeo (não há `ffmpeg` no contêiner), então
 * o aparelho baixa o vídeo **uma vez**, tira o quadro, guarda como `poster_{servidor}_{id}.jpg` no cache e apaga o vídeo baixado: da segunda vez em
 * diante lê só o JPEG. Os que acabaram de ser lidos ficam também na memória.
 */
class PostersDeVideo(
    private val provedor: ProvedorDeApi,
    private val pasta: File,
) {
    private val memoria = LruCache<String, Bitmap>(12)

    /** Uma extração por vez: dois quadros do mesmo vídeo (ou vários vídeos) não baixam ao mesmo tempo. */
    private val trava = Mutex()

    /** Os quadros do tempo em que o nome não dizia o servidor só são apagados uma vez por abertura do app (PD7). */
    private var limpouOsAntigos = false

    /** O primeiro quadro do vídeo [videoId], ou `null` se não deu (sem conexão, vídeo sem imagem legível...). */
    suspend fun obter(videoId: Int): Bitmap? {
        val servidor = provedor.emUso() ?: return null
        val chave = nomeDoPoster(servidor.chave.identificador, videoId)
        memoria.get(chave)?.let { return it }
        return trava.withLock {
            memoria.get(chave)?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                limparOsAntigosUmaVez()
                val guardado = File(pasta, chave)
                lerDoDisco(guardado) ?: extrair(servidor.api, videoId, guardado)
            }?.also { memoria.put(chave, it) }
        }
    }

    private fun limparOsAntigosUmaVez() {
        if (limpouOsAntigos) return
        limpouOsAntigos = true
        pasta.listFiles()?.filter { ehPosterSemServidor(it.name) }?.forEach { it.delete() }
    }

    private fun lerDoDisco(arquivo: File): Bitmap? = if (arquivo.isFile) BitmapFactory.decodeFile(arquivo.path) else null

    private suspend fun extrair(api: ApiImagineer, videoId: Int, destino: File): Bitmap? {
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
