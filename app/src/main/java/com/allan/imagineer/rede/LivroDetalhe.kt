package com.allan.imagineer.rede

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
 * Um capítulo **com o texto** — espelha o `CapituloDetalhe` do backend (item 6.2).
 * É o que `GET /capitulos/{id}` devolve, quando o usuário abre um capítulo.
 *
 * O texto vem inteiro (o maior capítulo dos livros de validação tem ~110 KB), com
 * parágrafos separados por uma linha em branco.
 */
@Serializable
@Suppress("PropertyName")
data class CapituloDetalhe(
    val id: Int,
    val ordem: Int,
    val titulo: String? = null,
    val ignorado: Boolean,
    val tamanho_do_texto: Int,
    val sugestoes_pendentes: Int = 0,
    val livro_id: Int,
    val texto: String,
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
 * O que se quer mudar num livro (`PATCH /livros/{id}`).
 *
 * **Campo `null` = "não mexa"** (fica fora do JSON). No backend, campo ausente e
 * campo `null` são coisas diferentes (item 6.2): ausente não mexe, `null` **limpa**.
 * Para limpar de propósito, use as marcas [limparIdioma] e [limparPerfilPadrao] — e
 * por isso esta classe não é serializada direto, mas por [paraJson].
 *
 * Mandar [titulo] já o **confirma** no backend, mesmo com o valor igual ao anterior;
 * por isso o app só o envia quando o usuário de fato o mudou ou quando ele está
 * pendente (item 7.3a).
 */
data class LivroAjuste(
    val titulo: String? = null,
    val autor: String? = null,
    val idioma: String? = null,
    val perfil_renderizacao_padrao_id: Int? = null,
    /** Manda `"idioma": null`: o livro fica sem idioma. */
    val limparIdioma: Boolean = false,
    /** Manda `"perfil_renderizacao_padrao_id": null`: o livro fica sem perfil padrão. */
    val limparPerfilPadrao: Boolean = false,
) {
    /**
     * O corpo do `PATCH`: só os campos que mudam. `autor` **nunca** vai como `null`
     * — no backend isso faria o autor voltar a ficar pendente (é mandatório).
     */
    fun paraJson(): JsonObject = buildJsonObject {
        titulo?.let { put("titulo", it) }
        autor?.let { put("autor", it) }
        when {
            limparIdioma -> put("idioma", JsonNull)
            idioma != null -> put("idioma", idioma)
        }
        when {
            limparPerfilPadrao -> put("perfil_renderizacao_padrao_id", JsonNull)
            perfil_renderizacao_padrao_id != null ->
                put("perfil_renderizacao_padrao_id", perfil_renderizacao_padrao_id)
        }
    }
}

/**
 * O corpo de `PATCH /capitulos/{id}`. Os campos nulos não vão no JSON:
 * no backend, campo ausente é "não mexa".
 *
 * Marcar `ignorado` é o caso mais comum — é o que confirma ou desfaz a sugestão da
 * importação (item 2.2).
 */
@Serializable
data class CapituloAjuste(
    val titulo: String? = null,
    val ignorado: Boolean? = null,
)
