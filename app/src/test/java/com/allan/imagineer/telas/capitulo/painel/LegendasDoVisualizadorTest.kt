package com.allan.imagineer.telas.capitulo.painel

import org.junit.Assert.assertEquals
import org.junit.Test

/** A palavra sob cada ícone do visualizador (LV1). */
class LegendasDoVisualizadorTest {

    @Test
    fun `LV1 cada acao extra tem uma palavra so`() {
        assertEquals("Canônica", legendaDaAcaoDaImagem(rotuloDaAcaoCanonica(false)))
        assertEquals("Canônica", legendaDaAcaoDaImagem(rotuloDaAcaoCanonica(true)))
        assertEquals("Ocultar", legendaDaAcaoDaImagem(rotuloDaOcultacao(false)))
        assertEquals("Mostrar", legendaDaAcaoDaImagem(rotuloDaOcultacao(true)))
    }

    @Test
    fun `LV1 uma acao desconhecida usa a primeira palavra do rotulo`() {
        assertEquals("Refazer", legendaDaAcaoDaImagem("Refazer esta imagem"))
    }
}
