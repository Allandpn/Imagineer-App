package com.allan.imagineer.rede

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.InputStream

/**
 * O corpo de um upload que lê o arquivo **aos poucos** (não o carrega inteiro na
 * memória — um EPUB pode ter 46 MB) e avisa quantos bytes já foram (item 7.3a,
 * incremento 5).
 *
 * @param tamanho em bytes, se conhecido. Sem ele o envio é "em pedaços"
 * (`chunked`) e o progresso fica sem porcentagem.
 * @param aoProgredir chamada com (bytes enviados, tamanho total ou nulo). É
 * chamada de uma thread de rede, não da tela.
 */
class CorpoComProgresso(
    private val entrada: InputStream,
    private val tipo: MediaType?,
    private val tamanho: Long?,
    private val aoProgredir: (enviados: Long, total: Long?) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = tipo

    override fun contentLength(): Long = tamanho ?: -1L

    /**
     * Uso único: o fluxo do arquivo só pode ser lido uma vez. Sem isto, o OkHttp
     * reenviaria sozinho depois de uma falha de conexão — e mandaria um fluxo já
     * consumido, isto é, um arquivo truncado, sem avisar ninguém.
     */
    override fun isOneShot(): Boolean = true

    override fun writeTo(sink: BufferedSink) {
        // Só avisa a cada ~1% (mínimo 256 KB): para 46 MB, avisar a cada leitura de
        // 8 KB seriam milhares de atualizações de tela sem ninguém notar.
        val passo = maxOf(PASSO_MINIMO, (tamanho ?: 0L) / 100)
        val bloco = ByteArray(TAMANHO_DO_BLOCO)
        var enviados = 0L
        var ultimoAviso = 0L

        entrada.use { fonte ->
            while (true) {
                val lidos = fonte.read(bloco)
                if (lidos == -1) break
                sink.write(bloco, 0, lidos)
                enviados += lidos
                if (enviados - ultimoAviso >= passo) {
                    aoProgredir(enviados, tamanho)
                    ultimoAviso = enviados
                }
            }
        }
        // O aviso final, para a tela ver o 100% mesmo que o último trecho tenha sido curto.
        aoProgredir(enviados, tamanho)
    }

    private companion object {
        const val TAMANHO_DO_BLOCO = 8 * 1024
        const val PASSO_MINIMO = 256L * 1024
    }
}
