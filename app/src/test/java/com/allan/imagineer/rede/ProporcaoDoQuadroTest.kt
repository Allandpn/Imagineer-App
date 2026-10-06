package com.allan.imagineer.rede

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A proporção do primeiro quadro do vídeo, que dá a forma do quadro no texto (VD17). */
class ProporcaoDoQuadroTest {

    @Test
    fun a_proporcao_e_largura_por_altura() {
        assertEquals(16f / 9f, proporcaoDoQuadro(1920, 1080)!!, 0.001f)
        assertEquals(9f / 16f, proporcaoDoQuadro(1080, 1920)!!, 0.001f)
    }

    @Test
    fun sem_tamanho_nao_ha_proporcao() {
        assertNull(proporcaoDoQuadro(0, 1080))
        assertNull(proporcaoDoQuadro(1920, 0))
    }
}
