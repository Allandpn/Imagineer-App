package com.allan.imagineer.telas.livro

import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

private val PORTUGUES_DO_BRASIL = Locale("pt", "BR")

/**
 * O título de um capítulo para exibir. Cerca de 11% dos capítulos não têm título
 * (item 2.2), e a tela mostra "Capítulo N" no lugar — uma reserva **de exibição**,
 * só na tela, não um dado novo da API (item 7.4).
 */
fun tituloDoCapitulo(titulo: String?, ordem: Int): String =
    titulo?.takeIf { it.isNotBlank() } ?: "Capítulo $ordem"

/**
 * O texto das sugestões da IA que ainda esperam confirmação num capítulo — ou `null`
 * se não há nenhuma (e então nada é mostrado).
 *
 * Substitui um balão numérico sem rótulo: quem via "13" ao lado do capítulo não tinha
 * como saber o que era (achado testando no tablet). O número são as sugestões de
 * elemento e de cena que a IA encontrou e o usuário ainda não confirmou nem descartou
 * (item 4.6).
 */
fun descreverSugestoes(pendentes: Int): String? = when {
    pendentes <= 0 -> null
    pendentes == 1 -> "1 sugestão a confirmar"
    else -> "$pendentes sugestões a confirmar"
}

/** Quantos caracteres, em média, uma pessoa lê por minuto em português (~230 palavras de ~5,7 letras). Estimativa, não medida. */
const val CARACTERES_LIDOS_POR_MINUTO = 1300

/** O tempo estimado de leitura: "menos de 1 min", "~12 min", "~1 h 20 min". */
fun descreverTempoDeLeitura(caracteres: Int): String {
    if (caracteres <= 0) return "menos de 1 min"
    val minutos = (caracteres + CARACTERES_LIDOS_POR_MINUTO - 1) / CARACTERES_LIDOS_POR_MINUTO
    return when {
        minutos < 2 && caracteres < CARACTERES_LIDOS_POR_MINUTO / 2 -> "menos de 1 min"
        minutos < 60 -> "~$minutos min"
        minutos % 60 == 0 -> "~${minutos / 60} h"
        else -> "~${minutos / 60} h ${minutos % 60} min"
    }
}

/**
 * O tamanho de um capítulo em linguagem legível: "850 caracteres", "3,4 mil
 * caracteres", "112 mil caracteres". O servidor só devolve o número; formatar é
 * trabalho do cliente (item 7.5a).
 *
 * @param locale só para os testes fixarem a vírgula decimal; o app usa o português do Brasil.
 */
fun descreverTamanho(caracteres: Int, locale: Locale = PORTUGUES_DO_BRASIL): String {
    if (caracteres == 1) return "1 caractere"
    if (caracteres < 1000) return "$caracteres caracteres"

    val mil = (caracteres / 100.0).roundToInt() / 10.0
    val inteiro = mil == floor(mil)
    return when {
        // "1 mil" e não "1,0 mil"; e acima de 100 mil a casa decimal é ruído.
        inteiro || mil >= 100 -> "${mil.roundToInt()} mil caracteres"
        else -> String.format(locale, "%.1f mil caracteres", mil)
    }
}
