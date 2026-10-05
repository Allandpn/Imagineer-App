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

/**
 * Um capítulo, com o texto e as sugestões (item 7.5). É o capítulo em que o leitor **começa**: dali se passa a
 * página para os vizinhos sem sair da tela (item 7.5c).
 */
@Serializable
data class Capitulo(
    val capituloId: Int,
    /** Quando vem da lista de elementos (LV3): abre a área de IA deste elemento ao entrar no capítulo. */
    val abrirElementoId: Int? = null,
    /** Quando vem da pesquisa (LV5): o início do parágrafo para onde rolar, que fica destacado por um instante. */
    val irParaPosicao: Int? = null,
    /** Quando vem de um aviso de prompt gerado: abre o modal deste frame (a cena) ao entrar; [abrirRotulo] é o nome dela. */
    val abrirFrameId: Int? = null,
    val abrirRotulo: String? = null,
    /** Quando vem do aviso de análise concluída: abre o painel de IA do capítulo. */
    val abrirPainel: Boolean = false,
)

/** A pesquisa no texto (LV5): [capituloId] só vem quando se pesquisa de dentro de um capítulo (então há a aba Capítulo). */
@Serializable
data class Pesquisa(val livroId: Int, val capituloId: Int? = null)

/** Um frame, retrato ou cena (item 7.6). */
@Serializable
data class Frame(val frameId: Int)

/**
 * O prompt de um frame (item 7.7). [promptId] é nulo ao gerar um prompt novo e
 * preenchido ao abrir um prompt já existente do histórico.
 */
@Serializable
data class Prompt(val frameId: Int, val promptId: Int? = null)

/** As cenas de um livro, por capítulo (LY8). */
@Serializable
data class CenasDoLivro(val livroId: Int)

/** As pendências de um livro (sugestões ainda não confirmadas), por capítulo (LY7). */
@Serializable
data class PendenciasDoLivro(val livroId: Int)

/** As estatísticas de leitura (RL17). */
@Serializable
object Estatisticas

/** Os destaques e notas de um livro, por capítulo (RL12). */
@Serializable
data class DestaquesDoLivro(val livroId: Int)

/** Os favoritos de um livro: parágrafos, elementos, cenas e imagens (RL36). */
@Serializable
data class FavoritosDoLivro(val livroId: Int)

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

/** A lixeira de imagens (item 7.5b, LX8): o que foi apagado e ainda não foi apagado de vez. */
@Serializable
object Lixeira

/** A lixeira **de um livro** (imagens e cenas dele), aberta pelo menu ⋮ do livro (AJ3). */
@Serializable
data class LixeiraDoLivro(val livroId: Int)

/** Configuração do app: endereço do servidor, chave própria, modelos (item 7.10). */
@Serializable
object Configuracao

/** O perfil da conta, aberto pelo menu da biblioteca (MN3). */
@Serializable
object Perfil

/** As configurações (servidor, perfis de renderização, modelos, exibição), abertas pelo menu da biblioteca (MN4). */
@Serializable
object Configuracoes

/** Os modelos de IA (MN4): só interface. */
@Serializable
object ModelosDeIa

/** O catálogo dos modelos de imagem, com preço, moderação e teste de resolução (MI6). */
@Serializable
object ModelosDeImagem

/** Os dicionários: liga, desliga e ordem de preferência (RL28). */
@Serializable
object Dicionarios

/** A configuração da narração (RL26). */
@Serializable
object Narracao

/** O espaço que cada livro ocupa no aparelho (PL10). */
@Serializable
object Armazenamento

/** Os custos de IA (MN6): só interface. */
@Serializable
object Custos
