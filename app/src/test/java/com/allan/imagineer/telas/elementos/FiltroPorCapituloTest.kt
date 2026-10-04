package com.allan.imagineer.telas.elementos

import com.allan.imagineer.rede.ElementoDoLivro
import org.junit.Assert.assertEquals
import org.junit.Test

/** O filtro por capítulo da lista de elementos (LV3b). */
class FiltroPorCapituloTest {

    private val jon = ElementoDoLivro(1, "PERSONAGEM", "Jon")
    private val muralha = ElementoDoLivro(2, "AMBIENTE", "Muralha")
    private val mapa = mapOf(
        1 to listOf(CapituloDoElemento(30, 3, null), CapituloDoElemento(50, 5, "A queda")),
        2 to listOf(CapituloDoElemento(50, 5, "A queda")),
    )

    @Test
    fun `LV3b sem capitulo escolhido mostra todos`() {
        assertEquals(listOf("Jon", "Muralha"), filtrarElementos(listOf(jon, muralha), "", null, null, mapa).map { it.nome })
    }

    @Test
    fun `LV3b com capitulo mostra so os que aparecem nele`() {
        assertEquals(listOf("Jon"), filtrarElementos(listOf(jon, muralha), "", null, 30, mapa).map { it.nome })
        assertEquals(listOf("Jon", "Muralha"), filtrarElementos(listOf(jon, muralha), "", null, 50, mapa).map { it.nome })
    }

    @Test
    fun `LV3b o filtro combina com tipo e busca`() {
        assertEquals(listOf("Muralha"), filtrarElementos(listOf(jon, muralha), "", "AMBIENTE", 50, mapa).map { it.nome })
        assertEquals(emptyList<String>(), filtrarElementos(listOf(jon, muralha), "mura", null, 30, mapa).map { it.nome })
    }

    @Test
    fun `LV3b elemento sem capitulos conhecidos some quando ha filtro`() {
        assertEquals(emptyList<String>(), filtrarElementos(listOf(jon), "", null, 99, mapa).map { it.nome })
    }

    @Test
    fun `LV3b os capitulos oferecidos vem sem repeticao e na ordem do livro`() {
        assertEquals(listOf(30, 50), capitulosComElementos(mapa).map { it.capituloId })
    }
}
