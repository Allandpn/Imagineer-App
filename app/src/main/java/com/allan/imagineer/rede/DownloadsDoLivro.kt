package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/** O texto de um capítulo, em `GET /livros/{id}/textos` (para baixar o livro inteiro de uma vez; PL3). */
@Serializable
@Suppress("PropertyName")
data class TextoDoCapitulo(val capitulo_id: Int, val ordem: Int, val texto: String)

/** Uma imagem do manifesto de mídias do livro (`GET /livros/{id}/midias`): o tamanho do original, para mostrar "Baixar — 240 MB" (PL3). */
@Serializable
@Suppress("PropertyName")
data class MidiaDoLivro(
    val imagem_id: Int,
    val frame_id: Int = 0,
    val tamanho_em_bytes: Long = 0,
    val tipo_do_arquivo: String = "",
)

/** O manifesto de mídias do livro. */
@Serializable
@Suppress("PropertyName")
data class MidiasDoLivro(val total_em_bytes: Long = 0, val imagens: List<MidiaDoLivro> = emptyList())
