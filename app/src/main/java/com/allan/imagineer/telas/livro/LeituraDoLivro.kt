package com.allan.imagineer.telas.livro

import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.Marcador

/**
 * O botão de leitura da tela do livro (LE3): [posicao] nula = abre o capítulo do começo ("Começar a ler"); com valor, abre onde a pessoa
 * parou ("Continuar lendo").
 */
data class ContinuarLendo(val capituloId: Int, val posicao: Int?, val rotulo: String)

/**
 * O que o botão de leitura faz: com **marcador** num capítulo que ainda está na lista (ativo), "Continuar lendo · Cap. N" abre ali, no
 * parágrafo; sem marcador (ou com o capítulo dele arquivado), "Começar a ler" abre o **primeiro capítulo não lido** — ou o primeiro, se
 * todos estão lidos. `null` quando o livro não tem capítulo ativo.
 */
fun continuarLendo(livro: LivroDetalhe, marcador: Marcador?): ContinuarLendo? {
    val ativos = capitulosAtivos(livro)
    if (ativos.isEmpty()) return null
    val doMarcador = marcador?.let { m -> ativos.firstOrNull { it.id == m.capitulo_id } }
    if (doMarcador != null && marcador != null) {
        return ContinuarLendo(doMarcador.id, marcador.posicao_no_texto, "Continuar lendo · Cap. ${doMarcador.ordem}")
    }
    val primeiro = ativos.firstOrNull { !it.lido } ?: ativos.first()
    return ContinuarLendo(primeiro.id, null, "Começar a ler")
}

/** O progresso do livro para a barrinha da capa (LE6): lidos ÷ ativos, de 0 a 1; `null` sem nada lido (a barra nem aparece). */
fun progressoDoLivro(livro: LivroResumo): Float? {
    val ativos = livro.total_de_capitulos - livro.capitulos_ignorados
    if (livro.capitulos_lidos <= 0 || ativos <= 0) return null
    return (livro.capitulos_lidos.toFloat() / ativos).coerceAtMost(1f)
}

/** "3 de 12 capítulos lidos", para os Metadados do livro (LE6). */
fun descreverProgresso(lidos: Int, ativos: Int): String =
    if (ativos == 1) "$lidos de 1 capítulo lido" else "$lidos de $ativos capítulos lidos"
