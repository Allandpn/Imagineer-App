package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/**
 * Um capítulo numa listagem, **sem** o texto — espelha o `CapituloResumo` do
 * backend (item 6.2). O texto só vem quando o usuário abre o capítulo.
 */
@Serializable
@Suppress("PropertyName")
data class CapituloResumo(
    val id: Int,
    val ordem: Int,
    val titulo: String? = null,
    val ignorado: Boolean,
    val tamanho_do_texto: Int,
    val sugestoes_pendentes: Int = 0,
)

/**
 * Um livro com a estrutura de capítulos — espelha o `LivroDetalhe` do backend.
 * É o que `POST /livros`, `GET /livros/{id}` e `PATCH /livros/{id}` devolvem.
 */
@Serializable
@Suppress("PropertyName")
data class LivroDetalhe(
    val id: Int,
    val titulo: String,
    val autor: String? = null,
    val idioma: String? = null,
    val nome_arquivo: String,
    val data_importacao: String,
    val total_de_capitulos: Int,
    val capitulos_ignorados: Int,
    val identificador_epub: String? = null,
    val perfil_renderizacao_padrao_id: Int? = null,
    /**
     * Campos mandatórios que a extração não conseguiu obter: `"titulo"` e/ou
     * `"autor"` (item 6.2). Lista vazia = nada pendente, a importação pode se
     * dar por concluída.
     */
    val metadados_pendentes: List<String> = emptyList(),
    val capitulos: List<CapituloResumo> = emptyList(),
)

/**
 * O que `POST /livros` devolve: o livro importado e os que já tinham o mesmo
 * identificador do EPUB (importar de novo é permitido — o app só avisa).
 */
@Serializable
@Suppress("PropertyName")
data class RespostaImportacao(
    val livro: LivroDetalhe,
    val livros_semelhantes: List<LivroResumo> = emptyList(),
)

/**
 * O corpo de `PATCH /livros/{id}`. Só os campos que o app quer mudar.
 *
 * Os campos nulos **não vão no JSON** (o `Json` do app não codifica valores
 * padrão), e é isso que se quer: no backend, mandar `titulo` já o confirma, e um
 * campo ausente significa "não mexa" — diferente de `null`.
 */
@Serializable
data class LivroAjuste(
    val titulo: String? = null,
    val autor: String? = null,
)
