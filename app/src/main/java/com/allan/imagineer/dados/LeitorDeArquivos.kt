package com.allan.imagineer.dados

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Um arquivo que o usuário escolheu no seletor do sistema.
 *
 * @property uri o endereço do arquivo, como texto (o Android não entrega caminhos
 * de disco, e sim `content://...`). Texto e não `Uri` para o ViewModel não
 * depender de classes do Android e poder ser testado na JVM.
 * @property tamanho em bytes, ou `null` se o provedor de arquivos não informa.
 */
data class ArquivoEscolhido(
    val uri: String,
    val nome: String,
    val tamanho: Long?,
)

/**
 * O que o app precisa saber fazer com um arquivo escolhido. Interface, para
 * os testes trocarem o Android por uma versão falsa.
 */
interface LeitorDeArquivos {
    /** Nome e tamanho do arquivo, ou `null` se não foi possível consultá-lo. */
    fun descrever(uri: String): ArquivoEscolhido?

    /** O conteúdo, para ler aos poucos; `null` se não foi possível abrir (permissão perdida, arquivo movido). */
    fun abrir(uri: String): InputStream?
}

/** A implementação de verdade, sobre o `ContentResolver` do Android. */
class LeitorDeArquivosDoAndroid(private val contexto: Context) : LeitorDeArquivos {

    override fun descrever(uri: String): ArquivoEscolhido? {
        val endereco = Uri.parse(uri)
        var nome: String? = null
        var tamanho: Long? = null

        try {
            contexto.contentResolver.query(
                endereco,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val colunaNome = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val colunaTamanho = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (colunaNome >= 0 && !cursor.isNull(colunaNome)) nome = cursor.getString(colunaNome)
                    if (colunaTamanho >= 0 && !cursor.isNull(colunaTamanho)) tamanho = cursor.getLong(colunaTamanho)
                }
            }
        } catch (erro: SecurityException) {
            return null
        }

        // Sem nome do provedor, o último pedaço do endereço é o melhor palpite.
        val nomeFinal = nome ?: endereco.lastPathSegment ?: return null
        return ArquivoEscolhido(uri = uri, nome = nomeFinal, tamanho = tamanho)
    }

    override fun abrir(uri: String): InputStream? {
        return try {
            contexto.contentResolver.openInputStream(Uri.parse(uri))
        } catch (erro: FileNotFoundException) {
            null
        } catch (erro: SecurityException) {
            null
        }
    }
}
