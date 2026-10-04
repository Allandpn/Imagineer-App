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
    /** A categoria de estilo (BT1): escolhe o bloco técnico fixo que o servidor cola ao fim do prompt. Nula = sem bloco. */
    val categoria_estilo: String? = null,
    /** Um dos 10 perfis que já vêm com o Imagineer (PF1): travado, só se pode escolher ou copiar. */
    val de_fabrica: Boolean = false,
    /** O bloco técnico da categoria, em inglês, só para a tela mostrar o perfil em detalhes (PF2). */
    val bloco_tecnico: String? = null,
) {
    val categoria: CategoriaDeEstilo? get() = CategoriaDeEstilo.de(categoria_estilo)
}

/**
 * As categorias de estilo do servidor (BT2). [nome] é o identificador exato que a API usa; [rotulo] é o que a pessoa lê e [dica] diz,
 * em uma linha, a técnica que o bloco fixo impõe.
 */
enum class CategoriaDeEstilo(val rotulo: String, val dica: String) {
    FOTORREALISTA_CINEMATOGRAFICO("Fotorrealista cinematográfico", "still de cinema: lente, profundidade de campo, grão de filme"),
    PINTURA_A_OLEO("Pintura a óleo", "pincelada grossa, relevo de tinta, tela de linho"),
    AQUARELA("Aquarela", "traços soltos, transparência, bordas que sangram"),
    ARTE_DIGITAL_CONCEITUAL("Arte digital conceitual", "pintura digital limpa, luz dramática, sem textura de tela"),
    QUADRINHOS("Quadrinhos", "contorno de tinta forte, cores chapadas ou tramadas"),
    CARTOON_ANIMACAO("Cartoon / animação 2D", "formas simples, cores vivas, sombreado chapado"),
    ANIME("Anime", "contorno fino, cel-shading, olhos estilizados"),
    PIXEL_ART("Pixel art", "grade de pixels visível, paleta curta, dithering"),
    GRAVURA_CLASSICA("Gravura clássica", "hachura cruzada em tinta, monocromática, papel envelhecido"),
    ANIMACAO_3D("Animação 3D", "longa-metragem 3D: formas esculpidas, olhos grandes, luz global"),
    ;

    companion object {
        /** Nome da API → categoria; `null` (ou um nome que este app não conhece, de um servidor mais novo) = sem categoria. */
        fun de(nome: String?): CategoriaDeEstilo? = entries.firstOrNull { it.name == nome }
    }
}

/** O que a pessoa preenche ao criar ou editar um perfil (BT5). Texto em branco é "sem valor" e vai como nulo ao servidor. */
data class PerfilEdicao(
    val nome: String,
    val estilo: String = "",
    val artistaDeReferencia: String = "",
    val iluminacao: String = "",
    val paleta: String = "",
    val formato: String = "",
    val categoria: CategoriaDeEstilo? = null,
)

/** O formulário que parte de um perfil que já existe. */
fun PerfilRenderizacao.paraEdicao() = PerfilEdicao(
    nome = nome,
    estilo = estilo.orEmpty(),
    artistaDeReferencia = artista_referencia.orEmpty(),
    iluminacao = iluminacao.orEmpty(),
    paleta = paleta.orEmpty(),
    formato = formato.orEmpty(),
    categoria = categoria,
)
