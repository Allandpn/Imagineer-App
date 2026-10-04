package com.allan.imagineer.telas.capitulo

import androidx.compose.ui.graphics.Color
import com.allan.imagineer.rede.CorDeDestaque
import com.allan.imagineer.rede.Destaque

// As regras dos destaques (RL9 a RL13), em funções puras para serem testadas na JVM.

/** Onde um trecho selecionado fica no texto do capítulo: UTF-16 desde o início, [fim] exclusivo. */
data class LugarDoTrecho(val inicio: Int, val fim: Int)

/**
 * Acha o [trecho] selecionado **dentro do parágrafo** onde ele está. A seleção do Android não atravessa parágrafos, então basta
 * procurar em cada um. Procura primeiro igual; se não achar (a seleção pode trazer um espaço no lugar de uma quebra de linha, ou o
 * contrário), procura aceitando qualquer espaço entre as palavras. Se o trecho aparece duas vezes, vale a **primeira**.
 */
fun localizarTrecho(paragrafos: List<com.allan.imagineer.telas.capitulo.ParagrafoDoTexto>, trecho: String): LugarDoTrecho? {
    val procurado = trecho.trim()
    if (procurado.isEmpty()) return null
    for (paragrafo in paragrafos) {
        val exato = paragrafo.texto.indexOf(procurado)
        if (exato >= 0) return LugarDoTrecho(paragrafo.inicio + exato, paragrafo.inicio + exato + procurado.length)
    }
    val tolerante = Regex(procurado.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) })
    for (paragrafo in paragrafos) {
        val achado = tolerante.find(paragrafo.texto) ?: continue
        return LugarDoTrecho(paragrafo.inicio + achado.range.first, paragrafo.inicio + achado.range.last + 1)
    }
    return null
}

/** Um destaque já recortado para o pedaço de parágrafo que está na tela: [de] e [ate] contam a partir do começo **desse texto**. */
data class DestaqueNoTexto(val id: Int, val de: Int, val ate: Int, val cor: CorDeDestaque)

/**
 * Os destaques que caem num pedaço do parágrafo. [inicioDoParagrafo] é onde o parágrafo começa no capítulo; [de] e [ate] são o corte
 * dele (a [FatiaDeParagrafo]); [esquerdaCortada] é quantos caracteres o `trim()` da fatia tirou do começo. O resultado conta a partir
 * do primeiro caractere **mostrado** (tamanho [mostrado]); um destaque que só encosta no pedaço é cortado nele.
 */
fun destaquesDaFatia(
    destaques: List<Destaque>,
    inicioDoParagrafo: Int,
    de: Int,
    esquerdaCortada: Int,
    mostrado: Int,
): List<DestaqueNoTexto> {
    val origem = inicioDoParagrafo + de + esquerdaCortada
    return destaques.mapNotNull { d ->
        val a = maxOf(d.inicio, origem) - origem
        val b = minOf(d.fim, origem + mostrado) - origem
        if (b > a) DestaqueNoTexto(d.id, a, b, d.corDoDestaque) else null
    }
}

/** A cor de fundo de um destaque; uma só tonalidade serve ao claro e ao escuro porque o texto por cima é opaco o bastante. */
fun corDeFundoDoDestaque(cor: CorDeDestaque): Color = when (cor) {
    CorDeDestaque.AMARELO -> Color(0x66FFD600)
    CorDeDestaque.VERDE -> Color(0x6669C46D)
    CorDeDestaque.AZUL -> Color(0x6642A5F5)
    CorDeDestaque.ROSA -> Color(0x66F06292)
}
