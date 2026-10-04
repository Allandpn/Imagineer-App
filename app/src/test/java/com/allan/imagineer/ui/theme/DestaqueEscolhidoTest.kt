package com.allan.imagineer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun razao(a: Color, b: Color): Float =
    (max(a.luminance(), b.luminance()) + 0.05f) / (min(a.luminance(), b.luminance()) + 0.05f)

/** A cor de destaque que a pessoa escolhe (PA6): toda opção fica legível nos dois temas. */
class DestaqueEscolhidoTest {

    @Test
    fun `sem nada guardado ou com nome desconhecido o destaque e o ambar`() {
        assertEquals(DestaqueEscolhido.AMBAR, DestaqueEscolhido.deNome(null))
        assertEquals(DestaqueEscolhido.AMBAR, DestaqueEscolhido.deNome("COR_QUE_NAO_EXISTE"))
        assertEquals(DestaqueEscolhido.ROXO, DestaqueEscolhido.deNome("ROXO"))
    }

    @Test
    fun `todas as cores se veem na leitura, na barra e com o texto por cima, nos dois temas`() {
        for (opcao in DestaqueEscolhido.entries) {
            for (escuro in listOf(true, false)) {
                val base = if (escuro) EsquemaEscuro else EsquemaClaro
                val e = comDestaque(base, opcao.cor(escuro), escuro)
                val onde = "${opcao.name} (${if (escuro) "escuro" else "claro"})"
                assertTrue("$onde: destaque sobre a leitura", razao(e.primary, e.background) >= 4.5f)
                assertTrue("$onde: destaque sobre a barra", razao(e.primary, e.surface) >= 4.5f)
                assertTrue("$onde: texto sobre o destaque", razao(e.onPrimary, e.primary) >= 4.5f)
                assertTrue("$onde: texto sobre o tom suave", razao(e.onPrimaryContainer, e.primaryContainer) >= 4.5f)
            }
        }
    }

    @Test
    fun `trocar o destaque so muda o destaque, e a leitura e as barras ficam`() {
        val trocado = comDestaque(EsquemaEscuro, DestaqueEscolhido.AZUL.noEscuro, escuro = true)

        assertEquals(DestaqueEscolhido.AZUL.noEscuro, trocado.primary)
        assertEquals(EsquemaEscuro.background, trocado.background)
        assertEquals(EsquemaEscuro.surface, trocado.surface)
        assertEquals(EsquemaEscuro.onSurface, trocado.onSurface)
    }

    @Test
    fun `as cores de destaque sao todas diferentes`() {
        assertEquals(DestaqueEscolhido.entries.size, DestaqueEscolhido.entries.map { it.noEscuro }.toSet().size)
    }
}
