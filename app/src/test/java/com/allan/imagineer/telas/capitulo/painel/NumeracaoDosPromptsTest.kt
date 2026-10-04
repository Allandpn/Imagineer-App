package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.PromptDeFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A numeração dos prompts de um frame e o botão de gerar que diz de qual prompt gera (PN1, PN2). */
class NumeracaoDosPromptsTest {

    private fun prompt(id: Int) = PromptDeFrame(id = id, frame_id = 70, texto = "p$id")

    // O painel guarda do mais novo para o mais antigo.
    private val lista = listOf(prompt(31), prompt(8), prompt(5))

    @Test
    fun `PN1 o mais antigo e o prompt 1 e o mais novo tem o maior numero`() {
        assertEquals(1, numeroDoPrompt(lista, 5))
        assertEquals(2, numeroDoPrompt(lista, 8))
        assertEquals(3, numeroDoPrompt(lista, 31))
    }

    @Test
    fun `PN1 o numero nao depende do id, so da ordem no frame`() {
        assertEquals(1, numeroDoPrompt(listOf(prompt(900)), 900))
    }

    @Test
    fun `PN1 um prompt que nao esta na lista nao tem numero`() {
        assertNull(numeroDoPrompt(lista, 99))
        assertNull(numeroDoPrompt(emptyList(), 1))
    }

    @Test
    fun `PN2 o botao de gerar cita o prompt, e sem numero fica o rotulo puro`() {
        assertEquals("Gerar imagem (prompt 3)", rotuloDeGerarComNumero("Gerar imagem", 3))
        assertEquals("Gerar retrato (prompt 1)", rotuloDeGerarComNumero("Gerar retrato", 1))
        assertEquals("Gerar imagem", rotuloDeGerarComNumero("Gerar imagem", null))
    }
}
