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
