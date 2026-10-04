package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.dados.PreferenciasDeLeitura
import com.allan.imagineer.rede.ElementoDoLivro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun elemento(id: Int, nome: String, tipo: String = "PERSONAGEM") = ElementoDoLivro(id, tipo, nome)

private fun nomesAchados(texto: String, vararg elementos: ElementoDoLivro): List<String> {
    val localizador = LocalizadorDeNomes(elementos.toList())
    return localizador.acharEm(texto).map { texto.substring(it.de, it.ate) }
}

/** Os nomes de elementos tocáveis no texto (RL14, RL15). */
class NomesNoTextoTest {

    @Test
    fun marca_o_nome_inteiro_sem_diferenciar_maiusculas() {
        assertEquals(listOf("Arya", "arya"), nomesAchados("Arya viu que ARYA não... arya!".replace("ARYA", "x"), elemento(1, "Arya")))
    }

    @Test
    fun so_palavra_inteira() {
        assertTrue(nomesAchados("Era visto de longe, e a Vision sumiu.", elemento(1, "Vis")).isEmpty())
        assertEquals(listOf("Vis"), nomesAchados("O Vis chegou.", elemento(1, "Vis")))
    }

    @Test
    fun acentos_contam_como_letra_e_nao_quebram_a_palavra() {
        assertEquals(listOf("Zoé"), nomesAchados("A Zoé riu.", elemento(1, "Zoé")))
        assertTrue(nomesAchados("A Zoéa riu.", elemento(1, "Zoé")).isEmpty())
    }

    @Test
    fun nome_composto_de_personagem_vale_inteiro_e_pelas_partes() {
        val achados = nomesAchados("Harry Potter chegou. Harry sorriu e Potter também.", elemento(1, "Harry Potter"))

        assertEquals(listOf("Harry Potter", "Harry", "Potter"), achados)
    }

    @Test
    fun parte_que_serve_a_dois_elementos_nao_e_marcada() {
        val achados = nomesAchados(
            "Potter olhou. Harry Potter e Lily Potter. Harry riu.",
            elemento(1, "Harry Potter"),
            elemento(2, "Lily Potter"),
        )

        // "Potter" sozinho é de dois: fica de fora. "Harry" é de um só; "Lily" não aparece sozinho.
        assertEquals(listOf("Harry Potter", "Lily Potter", "Harry"), achados)
    }

    @Test
    fun parte_de_nome_so_vale_para_personagem_e_com_inicial_maiuscula() {
        assertTrue(nomesAchados("O castelo de Gelo", elemento(1, "Castelo de Gelo", "AMBIENTE")).none { it == "Gelo" })
        assertTrue(nomesAchados("a velha ana", elemento(1, "Casa velha de Ana", "PERSONAGEM")).isEmpty())
    }

    @Test
    fun parte_que_e_o_nome_de_outro_elemento_fica_com_o_outro() {
        val localizador = LocalizadorDeNomes(listOf(elemento(1, "Harry Potter"), elemento(2, "Harry")))

        assertEquals(listOf(2), localizador.acharEm("Harry").map { it.elementoId })
    }

    @Test
    fun nomes_curtos_demais_nao_sao_marcados() {
        assertTrue(nomesAchados("Ed e Al foram", elemento(1, "Ed"), elemento(2, "Al")).isEmpty())
    }

    @Test
    fun o_nome_mais_longo_vence_a_sobreposicao() {
        val localizador = LocalizadorDeNomes(listOf(elemento(1, "Winterfell", "AMBIENTE"), elemento(2, "Winterfell Alto", "AMBIENTE")))

        val achados = localizador.acharEm("Em Winterfell Alto havia neve.")

        assertEquals(listOf(2), achados.map { it.elementoId })
    }

    @Test
    fun sem_elementos_o_localizador_esta_vazio_e_nao_acha_nada() {
        val localizador = LocalizadorDeNomes(emptyList())

        assertTrue(localizador.vazio)
        assertTrue(localizador.acharEm("qualquer coisa").isEmpty())
    }

    @Test
    fun caracteres_especiais_no_nome_nao_quebram_a_busca() {
        assertEquals(listOf("C++ (v2)"), nomesAchados("Usou C++ (v2) ontem", elemento(1, "C++ (v2)", "OBJETO")))
    }

    @Test
    fun o_destaque_vence_o_nome_na_sobreposicao() {
        val acertos = listOf(AcertoDeNome(0, 5, 1), AcertoDeNome(10, 15, 2))

        assertEquals(listOf(AcertoDeNome(10, 15, 2)), semSobreposicao(acertos, listOf(3..7)))
        assertEquals(acertos, semSobreposicao(acertos, listOf(5..9)))  // encostar sem cobrir não conta
    }

    @Test
    fun nomes_tocaveis_vem_ligado_por_padrao() {
        assertTrue(PreferenciasDeLeitura().nomesTocaveis)
    }
}
