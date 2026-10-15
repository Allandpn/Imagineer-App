package com.allan.imagineer.telas.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Os modelos `:batch` aparecem, mas no fim da lista e sem contar como favoritos (eles ficam desabilitados na tela). */
class ModelosEmLoteTest {
    @Test
    fun reconheceOSufixoDeLote() {
        assertTrue(ehModeloEmLote("google/gemini-4-argon:batch"))
        assertTrue(ehModeloEmLote("google/gemini-4-argon:BATCH"))
        assertFalse(ehModeloEmLote("google/gemini-4-argon"))
        assertFalse(ehModeloEmLote("google/batch-modelo"))
    }

    @Test
    fun oLoteVaiParaOFimMesmoFavoritado() {
        val modelos = listOf("a:batch", "b", "c", "d:batch", "e")
        val ordenados = ordenarComFavoritos(modelos, setOf("d:batch", "e")) { it }
        assertEquals(listOf("e", "b", "c", "a:batch", "d:batch"), ordenados)
        // O lote favoritado não conta: o título "Outros" cai no lugar certo.
        assertEquals(1, quantosSaoFavoritos(ordenados, setOf("d:batch", "e")) { it })
    }
}
