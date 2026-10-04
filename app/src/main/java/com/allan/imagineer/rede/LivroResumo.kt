package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/**
 * Um livro na listagem da biblioteca — espelha o `LivroResumo` do backend
 * (item 6.2). Os nomes dos campos são os do JSON, de propósito (item 7.3a).
 *
 * `autor` e `idioma` são opcionais no backend (`str | None`), então aqui são
 * `String?`. `data_importacao` fica como texto ISO: nenhuma tela do Bloco A a
 * exibe, e converter para data sem uso seria trabalho especulativo.
 */
@Serializable
@Suppress("PropertyName")
data class LivroResumo(
    val id: Int,
    val titulo: String,
    val autor: String? = null,
    val idioma: String? = null,
    val nome_arquivo: String,
    val data_importacao: String,
    val total_de_capitulos: Int,
    val capitulos_ignorados: Int,
    /** O livro tem capa guardada no servidor (`GET /livros/{id}/capa`). */
    val tem_capa: Boolean = false,
    /** Quantos capítulos ativos estão lidos (LE6). */
    val capitulos_lidos: Int = 0,
    /** Sobe a cada mudança no livro: vai na URL da capa para o Coil buscar de novo quando ela muda. */
    val revisao: Int = 0,
)

/** O endereço da capa de um livro (CP3); a [revisao] na URL faz a capa nova aparecer quando é trocada. */
fun enderecoDaCapa(urlBase: String, livroId: Int, revisao: Int): String =
    "${urlBase.trimEnd('/')}/livros/$livroId/capa?v=$revisao"
