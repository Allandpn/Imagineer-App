package com.allan.imagineer.telas.livro

import org.junit.Assert.assertEquals
import org.junit.Test

/** O tempo estimado de leitura (LV7): ~1.300 caracteres por minuto. */
class TempoDeLeituraTest {

    @Test
    fun `LV7 texto curtissimo ou vazio vale menos de um minuto`() {
        assertEquals("menos de 1 min", descreverTempoDeLeitura(0))
        assertEquals("menos de 1 min", descreverTempoDeLeitura(500))
    }

    @Test
    fun `LV7 minutos arredondam para cima`() {
        assertEquals("1 min", descreverTempoDeLeitura(1300))
        assertEquals("2 min", descreverTempoDeLeitura(1301))
        assertEquals("12 min", descreverTempoDeLeitura(15_000))
    }

    @Test
    fun `LV7 uma hora ou mais vira horas e minutos`() {
        assertEquals("1 h", descreverTempoDeLeitura(78_000))
        assertEquals("1 h 20 min", descreverTempoDeLeitura(104_000))
    }
}
