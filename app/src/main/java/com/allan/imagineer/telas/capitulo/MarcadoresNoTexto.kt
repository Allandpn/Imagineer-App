package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.rede.Marcador

// Funções puras dos ícones sobre o texto (item 7.5b, incremento 11). Ficam fora do Compose e do
// ViewModel para serem testadas na JVM.

/** Um parágrafo e **onde ele começa** no texto do capítulo, em unidades UTF-16 (os índices de uma String Kotlin). */
data class ParagrafoDoTexto(val inicio: Int, val texto: String)

/**
 * Divide o texto em parágrafos **sabendo onde cada um começa** (item 3.4g): o servidor diz a posição de
 * um marcador como o início do parágrafo, e o app precisa achar de qual parágrafo é.
 *
 * Mesma regra de [dividirEmParagrafos] — divide em `\n{2,}`, apara e descarta o vazio —, mas guarda o
 * começo do texto **depois** do aparo. A unidade é UTF-16 de propósito: é a de um índice de `String` em
 * Kotlin, e a que o contrato do servidor usa.
 */
fun dividirEmParagrafosComInicio(texto: String): List<ParagrafoDoTexto> {
    val resultado = mutableListOf<ParagrafoDoTexto>()
    var inicioDoPedaco = 0
    fun acrescentar(fim: Int) {
        val pedaco = texto.substring(inicioDoPedaco, fim)
        val aparado = pedaco.trim()
        if (aparado.isNotEmpty()) {
            val folga = pedaco.length - pedaco.trimStart().length
            resultado += ParagrafoDoTexto(inicioDoPedaco + folga, aparado)
        }
    }
    for (separador in Regex("\n{2,}").findAll(texto)) {
        acrescentar(separador.range.first)
        inicioDoPedaco = separador.range.last + 1
    }
    acrescentar(texto.length)
    return resultado
}

/** Os marcadores de um capítulo, já distribuídos: um grupo por parágrafo e os que não têm posição. */
data class MarcadoresDistribuidos(
    /** O índice do parágrafo (na lista de [ParagrafoDoTexto]) → os marcadores dele, na ordem recebida. */
    val porParagrafo: Map<Int, List<Marcador>>,
    /** Os de posição nula, ou que não cabem em nenhum parágrafo: vão para a faixa "sem posição". */
    val semPosicao: List<Marcador>,
)

/**
 * Distribui cada marcador no parágrafo a que pertence: o **último** que começa na posição dele ou antes.
 * Isso tolera uma pequena diferença de aparo entre servidor e app. Marcador sem posição, ou com uma
 * posição antes do primeiro parágrafo, vai para [MarcadoresDistribuidos.semPosicao].
 */
fun distribuirMarcadores(marcadores: List<Marcador>, paragrafos: List<ParagrafoDoTexto>): MarcadoresDistribuidos {
    val porParagrafo = linkedMapOf<Int, MutableList<Marcador>>()
    val semPosicao = mutableListOf<Marcador>()
    for (marcador in marcadores) {
        val posicao = marcador.posicao_no_texto
        val indice = if (posicao == null) -1 else paragrafos.indexOfLast { it.inicio <= posicao }
        if (indice < 0) semPosicao += marcador else porParagrafo.getOrPut(indice) { mutableListOf() } += marcador
    }
    return MarcadoresDistribuidos(porParagrafo, semPosicao)
}

/** O texto de acessibilidade de um marcador: o nome e onde o usuário parou. */
fun descreverMarcador(marcador: Marcador): String {
    val situacao = when (marcador.situacao) {
        "SUGERIDO" -> "sugerido, ainda não confirmado"
        "CONFIRMADO" -> "confirmado"
        "PROMPT_PRONTO" -> "com prompt pronto"
        "ILUSTRADO" -> "ilustrado"
        else -> marcador.situacao.lowercase()
    }
    return "${marcador.rotulo}, $situacao"
}
