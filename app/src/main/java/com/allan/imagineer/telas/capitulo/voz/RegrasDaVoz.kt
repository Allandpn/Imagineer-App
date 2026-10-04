package com.allan.imagineer.telas.capitulo.voz

// As regras da leitura em voz alta (RL18), em funções puras para serem testadas na JVM.

/** O tamanho máximo de um trecho entregue à voz do Android (o limite dela é 4000; fica abaixo, com folga). */
const val MAXIMO_POR_FALA = 3800

/**
 * Divide um parágrafo em trechos que a voz aceita: **o parágrafo inteiro** quando cabe; quando não cabe, cortado **no fim de uma frase**
 * (e, se uma frase sozinha é maior que o limite, no último espaço). Parágrafo só de espaços ou símbolos sem letra nem número dá lista vazia.
 */
fun dividirParaFalar(paragrafo: String, maximo: Int = MAXIMO_POR_FALA): List<String> {
    val texto = paragrafo.replace(Regex("\\s+"), " ").trim()
    if (texto.none { it.isLetterOrDigit() }) return emptyList()
    if (texto.length <= maximo) return listOf(texto)

    val partes = mutableListOf<String>()
    var resto = texto
    while (resto.length > maximo) {
        val janela = resto.substring(0, maximo)
        // O último fim de frase da janela; senão, o último espaço; senão, corta seco.
        val corte = Regex("[.!?…](?=\\s)").findAll(janela).lastOrNull()?.range?.last?.plus(1)
            ?: janela.lastIndexOf(' ').takeIf { it > 0 }
            ?: maximo
        partes += resto.substring(0, corte).trim()
        resto = resto.substring(corte).trim()
    }
    if (resto.isNotEmpty()) partes += resto
    return partes
}

/** A velocidade seguinte, de 0,25 em 0,25, dentro de 0,5 a 2,0. */
fun maisRapida(atual: Float): Float = (atual + NarradorDoCapitulo.PASSO_DA_VELOCIDADE).coerceAtMost(NarradorDoCapitulo.VELOCIDADE_MAXIMA)

fun maisLenta(atual: Float): Float = (atual - NarradorDoCapitulo.PASSO_DA_VELOCIDADE).coerceAtLeast(NarradorDoCapitulo.VELOCIDADE_MINIMA)

/** "1×", "1,25×", "0,5×". */
fun descreverVelocidade(velocidade: Float): String {
    val texto = if (velocidade % 1f == 0f) velocidade.toInt().toString() else "%s".format(velocidade).trimEnd('0').replace('.', ',')
    return "$texto×"
}
