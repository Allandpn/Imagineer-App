package com.allan.imagineer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A razão de contraste (WCAG) entre duas cores: 1 (iguais) a 21 (preto e branco). */
private fun contraste(a: Color, b: Color): Float {
    val claro = max(a.luminance(), b.luminance())
    val escuro = min(a.luminance(), b.luminance())
    return (claro + 0.05f) / (escuro + 0.05f)
}

/** A paleta do Imagineer: contraste legível nos dois temas e as camadas na ordem certa. */
class PaletaTest {

    private val esquemas = listOf("escuro" to EsquemaEscuro, "claro" to EsquemaClaro)

    @Test
    fun `o texto e legivel sobre a leitura e sobre as barras nos dois temas`() {
        for ((nome, e) in esquemas) {
            assertTrue("$nome: texto na leitura", contraste(e.onBackground, e.background) >= 7f)
            assertTrue("$nome: texto na barra", contraste(e.onSurface, e.surface) >= 7f)
            assertTrue("$nome: texto secundário na barra", contraste(e.onSurfaceVariant, e.surface) >= 4.5f)
            assertTrue("$nome: texto secundário na leitura", contraste(e.onSurfaceVariant, e.background) >= 4.5f)
        }
    }

    @Test
    fun `o destaque se ve na leitura e o texto sobre o destaque tambem`() {
        for ((nome, e) in esquemas) {
            assertTrue("$nome: destaque sobre a leitura", contraste(e.primary, e.background) >= 4.5f)
            assertTrue("$nome: destaque sobre a barra", contraste(e.primary, e.surface) >= 4.5f)
            assertTrue("$nome: texto sobre o destaque", contraste(e.onPrimary, e.primary) >= 4.5f)
            assertTrue("$nome: texto sobre o destaque suave", contraste(e.onPrimaryContainer, e.primaryContainer) >= 4.5f)
        }
    }

    @Test
    fun `a leitura e preta no escuro e as barras sao um cinza levemente mais claro`() {
        assertEquals(Color.Black, EsquemaEscuro.background)
        assertTrue(EsquemaEscuro.surface.luminance() > EsquemaEscuro.background.luminance())
        // as camadas sobem em ordem: leitura < baixa < normal < alta < mais alta
        val camadas = with(EsquemaEscuro) { listOf(surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest) }
        assertEquals(camadas.sortedBy { it.luminance() }, camadas)
    }

    @Test
    fun `no claro a leitura e branca e as barras um cinza leve`() {
        assertEquals(Color.White, EsquemaClaro.background)
        assertTrue(EsquemaClaro.surface.luminance() < EsquemaClaro.background.luminance())
    }

    @Test
    fun `as formas sao retas`() {
        assertEquals(0.dp, CANTO_DAS_FORMAS)
    }
}
