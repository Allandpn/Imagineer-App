package com.allan.imagineer.telas.capitulo.painel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** A pasta, dentro de Imagens da galeria, onde o Imagineer salva (U2). */
private const val PASTA_NA_GALERIA = "Imagineer"

/**
 * **Compartilhar** (U1): abre o seletor de apps do Android com a imagem. O arquivo está no cache do app; o `FileProvider`
 * dá aos outros apps permissão **só de leitura** para ele, sem expor o resto do aparelho.
 */
fun compartilharImagem(contexto: Context, arquivo: File, tipo: String) {
    val endereco = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", arquivo)
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = tipo
        putExtra(Intent.EXTRA_STREAM, endereco)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    contexto.startActivity(Intent.createChooser(envio, "Compartilhar a imagem").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * **Salvar na galeria** (U2): grava em `Pictures/Imagineer` pelo `MediaStore`. Do Android 10 em diante isso não pede
 * permissão (o app mínimo é o 12). Devolve `false` se o Android recusou.
 */
fun salvarNaGaleria(contexto: Context, arquivo: File, tipo: String): Boolean {
    val resolvedor = contexto.contentResolver
    val dados = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, arquivo.name)
        put(MediaStore.Images.Media.MIME_TYPE, tipo)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$PASTA_NA_GALERIA")
        put(MediaStore.Images.Media.IS_PENDING, 1) // só aparece na galeria quando o arquivo estiver completo
    }
    val destino = resolvedor.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, dados) ?: return false
    return try {
        resolvedor.openOutputStream(destino)?.use { saida -> arquivo.inputStream().use { it.copyTo(saida) } } ?: return false
        resolvedor.update(destino, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    } catch (erro: IOException) {
        resolvedor.delete(destino, null, null) // não deixa um arquivo pela metade na galeria
        false
    }
}
