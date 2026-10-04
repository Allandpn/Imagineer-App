package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.rede.ElementoDoLivro

// Os nomes catalogados tocáveis no texto (RL14, RL15), em funções puras para serem testadas na JVM.

/** Um pedaço do texto que é o nome de um elemento: [de] e [ate] contam a partir do começo do texto procurado. */
data class AcertoDeNome(val de: Int, val ate: Int, val elementoId: Int)

private const val MENOR_NOME = 3
private const val MENOR_PARTE_DE_NOME = 4

/**
 * Acha, num texto, os nomes dos elementos do livro (RL15). Monta **uma vez** a lista de nomes procurados (nome inteiro e partes de
 * nome de personagem que só servem a um elemento) e uma só expressão regular, para varrer cada parágrafo sem refazer o trabalho.
 */
class LocalizadorDeNomes(elementos: List<ElementoDoLivro>) {
    private val porNome: Map<String, Int>
    private val expressao: Regex?

    init {
        val inteiros = elementos.filter { it.nome.trim().length >= MENOR_NOME }.associate { it.nome.trim().lowercase() to it.id }
        // As partes: cada palavra de um nome composto de personagem, em quantos elementos ela aparece.
        val donosDaParte = mutableMapOf<String, MutableSet<Int>>()
        elementos.filter { it.tipo == "PERSONAGEM" && it.nome.trim().contains(' ') }.forEach { e ->
            e.nome.trim().split(Regex("\\s+"))
                .filter { it.length >= MENOR_PARTE_DE_NOME && it.first().isUpperCase() }
                .forEach { donosDaParte.getOrPut(it.lowercase()) { mutableSetOf() }.add(e.id) }
        }
        val mapa = inteiros.toMutableMap()
        // Uma parte só vale se tem um dono só e não é, ela mesma, o nome de outro elemento.
        donosDaParte.filter { (parte, donos) -> donos.size == 1 && parte !in inteiros }.forEach { (parte, donos) -> mapa[parte] = donos.first() }
        porNome = mapa
        expressao = if (mapa.isEmpty()) null else {
            // Do mais longo para o mais curto: "Harry Potter" é tentado antes de "Harry".
            val alternativas = mapa.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            Regex("(?<![\\p{L}\\p{N}_])($alternativas)(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
        }
    }

    val vazio: Boolean get() = expressao == null

    /** Os nomes achados em [texto], na ordem em que aparecem e sem sobreposição (a regex já fica com o mais longo). */
    fun acharEm(texto: String): List<AcertoDeNome> {
        val regex = expressao ?: return emptyList()
        return regex.findAll(texto).mapNotNull { achado ->
            val id = porNome[achado.value.lowercase()] ?: return@mapNotNull null
            AcertoDeNome(achado.range.first, achado.range.last + 1, id)
        }.toList()
    }
}

/** Tira dos [acertos] os que se sobrepõem a algum dos intervalos [ocupados] (os destaques vencem os nomes, RL15e). */
fun semSobreposicao(acertos: List<AcertoDeNome>, ocupados: List<IntRange>): List<AcertoDeNome> =
    acertos.filter { a -> ocupados.none { o -> a.de <= o.last && a.ate - 1 >= o.first } }
