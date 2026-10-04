package com.allan.imagineer.navegacao

import com.allan.imagineer.analise.EventoDeAnalise
import com.allan.imagineer.analise.TipoDeEvento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O botão "Abrir" dos avisos de IA leva à tela de IA do que terminou. */
class DestinoDoAvisoTest {

    @Test
    fun `aviso de prompt gerado abre o modal da cena daquele frame`() {
        val evento = EventoDeAnalise(5, 2, "capítulo 5", true, tipo = TipoDeEvento.PROMPT, frameId = 70, rotuloDaCena = "O vento")

        val destino = destinoDoAviso(evento)

        assertEquals(5, destino.capituloId)
        assertEquals(70, destino.abrirFrameId)
        assertEquals("O vento", destino.abrirRotulo)
        assertFalse(destino.abrirPainel)
    }

    @Test
    fun `aviso de analise concluida abre o painel de IA do capitulo`() {
        val destino = destinoDoAviso(EventoDeAnalise(5, 2, "capítulo 5", true))

        assertTrue(destino.abrirPainel)
        assertNull(destino.abrirFrameId)
    }
}
