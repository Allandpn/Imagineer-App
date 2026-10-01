package com.allan.imagineer.navegacao

import kotlinx.serialization.Serializable

// Os destinos de navegação do app (item 7.0 da especificação).
//
// Cada destino é uma classe marcada com @Serializable, em vez de uma string de
// rota solta ("livro/3"). O compilador confere que quem navega para Livro passa
// mesmo um livroId inteiro — um erro de digitação vira erro de compilação, e não
// uma tela quebrada em produção.
//
// "object" é para telas sem parâmetro; "data class" é para telas que precisam
// saber de qual livro/capítulo/frame se trata.

/** Tela inicial: a lista de livros importados (item 7.2). */
@Serializable
object Biblioteca

/** Detalhe de um livro, com a lista de capítulos (item 7.4). */
@Serializable
data class Livro(val livroId: Int)

/** Um capítulo, com o texto e as sugestões (item 7.5). */
@Serializable
data class Capitulo(val capituloId: Int)

/** Um frame, retrato ou cena (item 7.6). */
@Serializable
data class Frame(val frameId: Int)

/**
 * O prompt de um frame (item 7.7). [promptId] é nulo ao gerar um prompt novo e
 * preenchido ao abrir um prompt já existente do histórico.
 */
@Serializable
data class Prompt(val frameId: Int, val promptId: Int? = null)

/** Os elementos de um livro (item 7.8). */
@Serializable
data class ElementosDoLivro(val livroId: Int)

/**
 * A ficha de um elemento (item 7.8). [capituloId] só vem quando a ficha foi aberta a partir de uma
 * sugestão de um capítulo: a tela oferece "Adicionar estado neste capítulo".
 */
@Serializable
data class FichaDoElemento(val elementoId: Int, val livroId: Int, val capituloId: Int? = null)

/** Perfis de renderização (item 7.9). */
@Serializable
object PerfisDeRenderizacao

/**
 * A área de capítulos arquivados de um livro (item 7.5a, revisão do incremento 6).
 * Como as conversas arquivadas do WhatsApp: os capítulos saem da lista principal e
 * ficam aqui, de onde podem ser restaurados.
 */
@Serializable
data class CapitulosArquivados(val livroId: Int)

/** Configuração do app: endereço do servidor, chave própria, modelos (item 7.10). */
@Serializable
object Configuracao
