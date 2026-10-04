package com.allan.imagineer.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Os tamanhos de imagem que o servidor entrega e que o download guarda: as telas pedem os três (PL3). */
val TAMANHOS_DE_IMAGEM = listOf("miniatura", "leitura", "original")

/** Onde moram as **imagens** baixadas, um arquivo por imagem e tamanho (item 7.0a, passos 3 e 4; PL1). */
interface ArmazemDeImagens {

    /** O arquivo da imagem no [tamanho], ou `null` se não está guardado. Síncrono: só olha o disco, e o carregador de imagens o usa. */
    fun arquivo(chave: ChaveDoCache, imagemId: Int, tamanho: String): File?

    /**
     * Grava a imagem **inteira ou nada** (A5): [escrever] recebe um arquivo temporário e devolve `true` se o encheu direito; só então ele
     * vira o arquivo definitivo. Devolve se gravou.
     */
    suspend fun gravar(chave: ChaveDoCache, imagemId: Int, tamanho: String, escrever: suspend (File) -> Boolean): Boolean

    /** Quantos bytes as imagens [imagemIds] ocupam (todos os tamanhos). */
    suspend fun tamanhoEmBytes(chave: ChaveDoCache, imagemIds: Collection<Int>): Long

    /** Apaga as imagens [imagemIds], em todos os tamanhos. */
    suspend fun apagar(chave: ChaveDoCache, imagemIds: Collection<Int>)
}

/**
 * A versão de verdade, em arquivos sob [raiz] (em produção, `noBackupFilesDir/imagens`: fora do backup, A12, e privada do app, A13).
 * Estrutura: `<raiz>/<identificador da chave>/<imagem>-<tamanho>`.
 */
class ArmazemDeImagensEmArquivos(
    private val raiz: File,
    /** Onde o disco é lido e escrito; os testes passam um contexto sem threads (para o tempo virtual do teste esperar o disco). */
    private val contexto: kotlin.coroutines.CoroutineContext = Dispatchers.IO,
) : ArmazemDeImagens {

    private fun pasta(chave: ChaveDoCache) = File(raiz, chave.identificador)

    private fun nome(imagemId: Int, tamanho: String) = "$imagemId-$tamanho"

    override fun arquivo(chave: ChaveDoCache, imagemId: Int, tamanho: String): File? =
        File(pasta(chave), nome(imagemId, tamanho)).takeIf { it.isFile && it.length() > 0 }

    override suspend fun gravar(chave: ChaveDoCache, imagemId: Int, tamanho: String, escrever: suspend (File) -> Boolean): Boolean =
        withContext(contexto) {
            val destino = File(pasta(chave), nome(imagemId, tamanho))
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

    override suspend fun tamanhoEmBytes(chave: ChaveDoCache, imagemIds: Collection<Int>): Long = withContext(contexto) {
        imagemIds.sumOf { id -> TAMANHOS_DE_IMAGEM.sumOf { tamanho -> arquivo(chave, id, tamanho)?.length() ?: 0L } }
    }

    override suspend fun apagar(chave: ChaveDoCache, imagemIds: Collection<Int>) {
        withContext(contexto) {
            imagemIds.forEach { id -> TAMANHOS_DE_IMAGEM.forEach { tamanho -> File(pasta(chave), nome(id, tamanho)).delete() } }
        }
    }
}

/** O que um endereço de imagem do servidor diz: de qual servidor, qual imagem e qual tamanho. */
data class EnderecoDeImagem(val servidor: String, val imagemId: Int, val tamanho: String)

private val PADRAO_DO_ENDERECO = Regex("""^(https?://[^/?#]+)/imagens/(\d+)/arquivo(?:\?tamanho=([A-Za-z]+))?$""")

/**
 * Lê um endereço como `http://100.1.2.3:8000/imagens/7/arquivo?tamanho=miniatura` (item 6.9). `null` se não é de imagem do servidor.
 * Sem `?tamanho=`, o servidor entrega o original, então vale `original`. É o que o carregador de imagens usa para achar a cópia local (PL2).
 */
fun analisarEnderecoDeImagem(endereco: String): EnderecoDeImagem? {
    val achado = PADRAO_DO_ENDERECO.matchEntire(endereco.trim()) ?: return null
    val (servidor, id, tamanho) = achado.destructured
    return EnderecoDeImagem(servidor, id.toIntOrNull() ?: return null, tamanho.ifEmpty { "original" })
}
