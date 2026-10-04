package com.allan.imagineer.telas.capitulo.painel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A barra superior do capítulo e o fim do texto: ela não reaparece lá (encolheria a área do texto e cortaria as últimas linhas). */
class BarraNoFimDoCapituloTest {

    @Test
    fun `rolando para baixo a barra some e no fim ela continua escondida, mas o botao de IA reaparece`() {
        val regra = VisibilidadeDoBotao(limiar = 24f)
        regra.aoRolar(40f, noTopo = false, noFim = false)
        assertFalse(regra.barraVisivel)
        assertFalse(regra.visivel)

        regra.aoRolar(10f, noTopo = false, noFim = true) // chegou ao fim

        assertTrue(regra.visivel) // o botão flutua e volta
        assertFalse(regra.barraVisivel) // a barra não volta: mudaria o tamanho da área do texto
    }

    @Test
    fun `no topo a barra volta, e rolar para cima tambem a traz de volta`() {
        val regra = VisibilidadeDoBotao(limiar = 24f)
        regra.aoRolar(40f, noTopo = false, noFim = false)

        regra.aoRolar(-40f, noTopo = false, noFim = false)
        assertTrue(regra.barraVisivel)

        regra.aoRolar(40f, noTopo = false, noFim = false)
        regra.aoRolar(0f, noTopo = true, noFim = false)
        assertTrue(regra.barraVisivel)
    }
}
