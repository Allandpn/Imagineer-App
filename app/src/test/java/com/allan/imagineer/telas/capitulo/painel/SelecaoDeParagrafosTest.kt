package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.telas.capitulo.BlocoDoTexto
import com.allan.imagineer.telas.capitulo.FatiaDeParagrafo
import com.allan.imagineer.telas.capitulo.ParagrafoDoTexto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A seleção por parágrafo (LV4): as regras, sem Compose. */
class SelecaoDeParagrafosTest {

    private val paragrafos = listOf(
        ParagrafoDoTexto(0, "Primeiro."),
        ParagrafoDoTexto(11, "Segundo."),
        ParagrafoDoTexto(21, "Terceiro."),
    )

    @Test
    fun `LV4 um bloco comum cobre um paragrafo e o de retrato cobre os que consumiu`() {
        assertEquals(listOf(2), indicesDoBloco(BlocoDoTexto.Comum(FatiaDeParagrafo(2))))
        val retrato = Artefato(tipo = "ELEMENTO", rotulo = "Jon", situacao = "ILUSTRADO")
        val bloco = BlocoDoTexto.ComRetrato(
            retrato = retrato,
            fatias = listOf(FatiaDeParagrafo(0), FatiaDeParagrafo(1, 0, 4)),
            resto = FatiaDeParagrafo(1, 4, null),
        )
        assertEquals(listOf(0, 1), indicesDoBloco(bloco)) // o corte do parágrafo 1 não o duplica
    }

    @Test
    fun `LV4 tocar soma o bloco e tocar de novo tira`() {
        val somado = alternarBloco(emptySet(), listOf(1))
        assertEquals(setOf(1), somado)
        assertTrue(blocoMarcado(somado, listOf(1)))
        assertEquals(emptySet<Int>(), alternarBloco(somado, listOf(1)))
    }

    @Test
    fun `LV4 um bloco de varios paragrafos so esta marcado com todos`() {
        assertFalse(blocoMarcado(setOf(0), listOf(0, 1)))
        assertTrue(blocoMarcado(setOf(0, 1), listOf(0, 1)))
        assertFalse(blocoMarcado(emptySet(), emptyList()))
    }

    @Test
    fun `LV4 o trecho junta os marcados na ordem do texto e a posicao e a do primeiro`() {
        val marcados = setOf(2, 0)
        assertEquals("Primeiro.\n\nTerceiro.", trechoDosParagrafos(paragrafos, marcados))
        assertEquals(0, posicaoDosParagrafos(paragrafos, marcados))
        assertEquals(21, posicaoDosParagrafos(paragrafos, setOf(2)))
    }

    @Test
    fun `LV4 sem marcados nao ha trecho nem posicao`() {
        assertEquals("", trechoDosParagrafos(paragrafos, emptySet()))
        assertNull(posicaoDosParagrafos(paragrafos, emptySet()))
    }
}
