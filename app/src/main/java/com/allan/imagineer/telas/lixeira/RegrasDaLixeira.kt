package com.allan.imagineer.telas.lixeira

import com.allan.imagineer.rede.ImagemNaLixeira
import java.util.Locale

// As regras da Lixeira, sem Compose, para testar na JVM (item 7.5b, LX8).

/** O espaço em texto: "0 B", "512 B", "1,5 KB", "3,2 MB". Vírgula decimal, como no resto do app. */
fun descreverTamanho(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale("pt", "BR"), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale("pt", "BR"), "%.1f MB", mb)
    return String.format(Locale("pt", "BR"), "%.1f GB", mb / 1024.0)
}

/** "Retrato de Foxen" ou o título da cena: o que identifica de onde a imagem veio. */
fun nomeDoFrameNaLixeira(imagem: ImagemNaLixeira): String =
    if (imagem.frame_tipo == "PERSONAGEM") "Retrato de ${imagem.nome_do_elemento ?: imagem.frame_titulo}" else imagem.frame_titulo

/** "Livro · Capítulo 3": onde a imagem estava. */
fun origemDaImagemNaLixeira(imagem: ImagemNaLixeira): String {
    val capitulo = imagem.titulo_do_capitulo?.takeIf { it.isNotBlank() } ?: imagem.ordem_do_capitulo?.let { "Capítulo $it" } ?: "Capítulo"
    return "${imagem.titulo_do_livro} · $capitulo"
}

/** A data de "2026-10-03T14:05:00..." como "03/10/2026"; o que não for data volta como veio. */
fun dataDaLixeira(iso: String): String {
    val dia = iso.take(10).split("-")
    return if (dia.size == 3 && dia[0].length == 4) "${dia[2]}/${dia[1]}/${dia[0]}" else iso
}

/** A legenda de uma imagem da lixeira: o frame, de onde veio, o modelo (se gerada) e quando foi apagada. */
fun legendaDaLixeira(imagem: ImagemNaLixeira): String = buildString {
    append(nomeDoFrameNaLixeira(imagem))
    append("\n").append(origemDaImagemNaLixeira(imagem))
    imagem.modelo?.let { append("\n").append(it) }
    append("\nApagada em ").append(dataDaLixeira(imagem.apagada_em))
    imagem.tamanho_em_bytes?.let { append(" · ").append(descreverTamanho(it)) }
}

/** O que o diálogo de **apagar de vez** uma imagem diz (LX8). */
const val AVISO_APAGAR_DE_VEZ = "A imagem será apagada de vez, inclusive o arquivo no servidor. Não tem volta."

/** O que o diálogo de **esvaziar** diz: quantas imagens e quanto espaço vão embora (LX8). */
fun avisoDeEsvaziar(quantas: Int, bytes: Long): String {
    val imagens = if (quantas == 1) "1 imagem" else "$quantas imagens"
    return "$imagens (${descreverTamanho(bytes)}) serão apagadas de vez, inclusive os arquivos no servidor. Não tem volta."
}

/** O recado depois de esvaziar. */
fun recadoDeEsvaziada(removidas: Int, bytes: Long): String {
    val imagens = if (removidas == 1) "1 imagem apagada" else "$removidas imagens apagadas"
    return "$imagens de vez; ${descreverTamanho(bytes)} liberados."
}

/** O texto da lixeira vazia (LX8): uma frase, e não uma tela em branco. */
const val TEXTO_DA_LIXEIRA_VAZIA = "A lixeira está vazia. As imagens que você apagar ficam aqui até você decidir apagá-las de vez."

/** O resumo no alto da tela: quantas imagens e quanto espaço (LX6). */
fun resumoDaLixeira(quantas: Int, bytes: Long): String =
    (if (quantas == 1) "1 imagem" else "$quantas imagens") + " · " + descreverTamanho(bytes)
