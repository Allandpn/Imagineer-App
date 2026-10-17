package com.allan.imagineer.telas.capitulo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarraDeBaixoDoCapituloTest {

    @Test
    fun `aparece quando a barra do topo aparece e nada a cobre`() {
        assertTrue(barraDeBaixoVisivel(barraDoTopoVisivel = true, painelCheio = false, marcandoParagrafos = false, posicionando = false))
    }

    @Test
    fun `some junto com a barra do topo ao rolar`() {
        assertFalse(barraDeBaixoVisivel(barraDoTopoVisivel = false, painelCheio = false, marcandoParagrafos = false, posicionando = false))
    }

    @Test
    fun `some com o painel de IA em tela cheia`() {
        assertFalse(barraDeBaixoVisivel(true, painelCheio = true, marcandoParagrafos = false, posicionando = false))
    }

    @Test
    fun `some enquanto se marcam paragrafos`() {
        assertFalse(barraDeBaixoVisivel(true, painelCheio = false, marcandoParagrafos = true, posicionando = false))
    }

    @Test
    fun `some no modo de posicionar`() {
        assertFalse(barraDeBaixoVisivel(true, painelCheio = false, marcandoParagrafos = false, posicionando = true))
    }

    @Test
    fun `quatro destinos, so Elementos e Cenas precisam do livro`() {
        assertEquals(
            listOf("Biblioteca", "Configurações", "Elementos", "Cenas"),
            DestinoDaBarraDoCapitulo.entries.map { it.descricao },
        )
        assertEquals(
            listOf(DestinoDaBarraDoCapitulo.ELEMENTOS, DestinoDaBarraDoCapitulo.CENAS),
            DestinoDaBarraDoCapitulo.entries.filter { it.precisaDoLivro },
        )
    }
}
