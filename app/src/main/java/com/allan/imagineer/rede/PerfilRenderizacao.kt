package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/**
 * Um perfil de renderização como a API o devolve (item 6.5): o "estilo visual" que
 * vale para os prompts de um livro. Os perfis são compartilhados entre livros.
 *
 * Só o [nome] é obrigatório; cada ferramenta de imagem entende um subconjunto
 * diferente dos outros campos.
 */
@Serializable
@Suppress("PropertyName")
data class PerfilRenderizacao(
    val id: Int,
    val nome: String,
    val estilo: String? = null,
    val artista_referencia: String? = null,
    val iluminacao: String? = null,
    val paleta: String? = null,
    val formato: String? = null,
    val modelo_alvo: String? = null,
)
