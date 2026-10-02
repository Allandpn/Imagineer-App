package com.allan.imagineer.telas.capitulo.painel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import android.util.Log
import java.io.File
import java.io.IOException

/** A etiqueta das mensagens de log destas ações (`adb logcat -s Imagineer`). */
const val ETIQUETA_DO_LOG = "Imagineer"

/** A pasta, dentro de Imagens da galeria, onde o Imagineer salva (U2). */
private const val PASTA_NA_GALERIA = "Imagineer"

/**
 * **Compartilhar** (U1): abre o seletor de apps do Android com a imagem. O arquivo está no cache do app; o `FileProvider`
 * dá aos outros apps permissão **só de leitura** para ele, sem expor o resto do aparelho.
 */
fun compartilharImagem(contexto: Context, arquivo: File, tipo: String): Boolean = try {
    val endereco = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", arquivo)
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = tipo
        putExtra(Intent.EXTRA_STREAM, endereco)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    contexto.startActivity(Intent.createChooser(envio, "Compartilhar a imagem").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (erro: Exception) {
    // Qualquer falha aqui (provedor mal configurado, nenhum app para receber) vira um aviso, nunca o fechamento do app.
    Log.e(ETIQUETA_DO_LOG, "Não consegui compartilhar a imagem", erro)
    false
}

/**
 * **Salvar na galeria** (U2): grava em `Pictures/Imagineer` pelo `MediaStore`. Do Android 10 em diante isso não pede
 * permissão (o app mínimo é o 12). Devolve `false` se o Android recusou.
 */
fun salvarNaGaleria(contexto: Context, arquivo: File, tipo: String): Boolean {
    val resolvedor = contexto.contentResolver
    var destino: android.net.Uri? = null
    return try {
        val dados = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, arquivo.name)
            put(MediaStore.Images.Media.MIME_TYPE, tipo)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$PASTA_NA_GALERIA")
            put(MediaStore.Images.Media.IS_PENDING, 1) // só aparece na galeria quando o arquivo estiver completo
        }
        destino = resolvedor.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, dados)
        if (destino == null) {
            Log.e(ETIQUETA_DO_LOG, "O MediaStore não criou o item da imagem")
            return false
        }
        val saida = resolvedor.openOutputStream(destino)
        if (saida == null) {
            resolvedor.delete(destino, null, null)
            return false
        }
        saida.use { arquivo.inputStream().use { entrada -> entrada.copyTo(it) } }
        resolvedor.update(destino, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    } catch (erro: Exception) {
        // IOException, SecurityException, IllegalArgumentException do MediaStore...: nenhuma deve fechar o app, e um
        // arquivo pela metade não fica na galeria. O erro vai para o log para dar para ver o que foi.
        Log.e(ETIQUETA_DO_LOG, "Não consegui salvar a imagem na galeria", erro)
        destino?.let { runCatching { resolvedor.delete(it, null, null) } }
        false
    }
}
