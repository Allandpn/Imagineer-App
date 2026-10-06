package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArquivoEscolhido
import kotlinx.serialization.Serializable

// O vídeo importado de um frame (item 4.8, VD16 a VD19). Os nomes dos campos são os do JSON do backend (Etapa 6.11).

/** Um vídeo que a pessoa gerou fora (no Gemini) e importou para a cena. */
@Serializable
@Suppress("PropertyName")
data class VideoImportado(
    val id: Int,
    val frame_id: Int,
    /** O prompt de vídeo de onde veio, se a pessoa disse; nulo = importado sem ligar a um prompt. */
    val prompt_id: Int? = null,
    val nome_original: String = "",
    val tamanho_em_bytes: Long = 0,
    val data_importacao: String = "",
    /** O texto do capítulo mostra **este** vídeo no lugar da imagem (VD17). */
    val no_texto: Boolean = false,
)

const val TAMANHO_MAXIMO_DO_VIDEO_EM_BYTES = 50L * 1024 * 1024

/** As extensões que o servidor aceita e o tipo de cada uma (VD16). */
val TIPOS_DE_VIDEO = mapOf(
    "mp4" to "video/mp4",
    "m4v" to "video/mp4",
    "mov" to "video/quicktime",
    "webm" to "video/webm",
)

private val EXTENSOES_DE_VIDEO_POR_TIPO = mapOf(
    "video/mp4" to "mp4",
    "video/quicktime" to "mov",
    "video/webm" to "webm",
)

/** O tipo de mídia pelo nome do arquivo; o mais comum (`video/mp4`) se a extensão não é conhecida. */
fun tipoDoVideo(nome: String): String = TIPOS_DE_VIDEO[nome.substringAfterLast('.', "").lowercase()] ?: "video/mp4"

/** A extensão do vídeo escolhido, ou `null` se não dá para saber: a do **nome**, se o servidor a aceita; senão a do tipo MIME que o seletor informou. */
fun extensaoDoVideo(arquivo: ArquivoEscolhido): String? {
    val doNome = arquivo.nome.substringAfterLast('.', "").lowercase()
    if (doNome in TIPOS_DE_VIDEO) return doNome
    return arquivo.tipo?.lowercase()?.let { EXTENSOES_DE_VIDEO_POR_TIPO[it] }
}

/** O nome que vai ao servidor: o original se já tem extensão aceita; senão o nome com a extensão achada pelo tipo. */
fun nomeDoVideoParaEnviar(arquivo: ArquivoEscolhido): String {
    val extensao = extensaoDoVideo(arquivo) ?: return arquivo.nome
    val temExtensaoAceita = arquivo.nome.substringAfterLast('.', "").lowercase() in TIPOS_DE_VIDEO
    return if (temExtensaoAceita) arquivo.nome else "${arquivo.nome}.$extensao"
}

/** Confere, **antes de enviar**, o que o servidor recusaria: a extensão e o tamanho. Devolve o motivo, ou `null` se pode enviar. */
fun motivoParaNaoImportarVideo(arquivo: ArquivoEscolhido): String? = when {
    extensaoDoVideo(arquivo) == null -> "Escolha um vídeo MP4, MOV ou WEBM."
    (arquivo.tamanho ?: 0L) > TAMANHO_MAXIMO_DO_VIDEO_EM_BYTES -> "O vídeo passa de 50 MB, o limite do servidor."
    else -> null
}

/** O endereço de onde o player lê o vídeo, em fluxo (`GET /videos/{id}/arquivo`, que aceita pedaços). */
fun enderecoDoVideo(urlBase: String, videoId: Int): String = "${urlBase.trimEnd('/')}/videos/$videoId/arquivo"

/** O tamanho como a pessoa lê: "12,4 MB". */
fun tamanhoDoVideoParaLer(bytes: Long): String =
    if (bytes >= 1024 * 1024) String.format(java.util.Locale("pt", "BR"), "%.1f MB", bytes / (1024.0 * 1024.0))
    else String.format(java.util.Locale("pt", "BR"), "%.0f KB", bytes / 1024.0)
