package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.EstadoVigente
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Defeito D2 (as cenas apareciam em todos os filtros) e o aviso "confira" de `achado_no_texto` (item 6.7).
 * São regras puras: nada de rede nem de tela.
 */
class CenasPorFiltroTest {

    private fun cena(id: Int, descartada: Boolean = false, frameId: Int? = null) =
        CenaSugerida(id = id, titulo = "Cena $id", descartada = descartada, frame_id = frameId)

    /** [confirmado]: casado, revisado e com um estado que vale aqui (a regra de `situacaoDoElemento`). */
    private fun elemento(id: Int, achado: Boolean = true, confirmado: Boolean = false) =
        ElementoSugerido(
            id = id, tipo = "PERSONAGEM", nome = "Elemento $id",
            elemento_id = if (confirmado) 3 else null,
            estado_vigente = if (confirmado) EstadoVigente(1, 1, 1, null, "manto") else null,
            achado_no_texto = achado,
        )

    @Test
    fun `D2 a cena cai no filtro certo - pendente, confirmada ou descartada`() {
        assertEquals(FiltroDoPainel.PENDENTES, filtroDaCena(cena(1)))
        assertEquals(FiltroDoPainel.CONFIRMADOS, filtroDaCena(cena(2, frameId = 7)))
        assertEquals(FiltroDoPainel.DESCARTADOS, filtroDaCena(cena(3, descartada = true)))
    }

    @Test
    fun `D2 cada filtro mostra so as suas cenas, na ordem em que a IA as listou`() {
        val cenas = listOf(cena(1), cena(2, frameId = 7), cena(3, descartada = true), cena(4), cena(5, frameId = 8))

        assertEquals(listOf(1, 4), cenasDoFiltro(cenas, FiltroDoPainel.PENDENTES).map { it.id })
        assertEquals(listOf(2, 5), cenasDoFiltro(cenas, FiltroDoPainel.CONFIRMADOS).map { it.id })
        assertEquals(listOf(3), cenasDoFiltro(cenas, FiltroDoPainel.DESCARTADOS).map { it.id })
    }

    @Test
    fun `D2 os contadores somam elementos e cenas, e sem cenas continuam como antes`() {
        val elementos = listOf(elemento(1), elemento(2, confirmado = true))
        val cenas = listOf(cena(1), cena(2), cena(3, frameId = 7), cena(4, descartada = true))

        assertEquals(
            mapOf(FiltroDoPainel.PENDENTES to 3, FiltroDoPainel.CONFIRMADOS to 2, FiltroDoPainel.DESCARTADOS to 1),
            contagemPorFiltro(elementos, cenas),
        )
        assertEquals(
            mapOf(FiltroDoPainel.PENDENTES to 1, FiltroDoPainel.CONFIRMADOS to 1, FiltroDoPainel.DESCARTADOS to 0),
            contagemPorFiltro(elementos),
        )
    }

    @Test
    fun `quem o texto nao traz vai para o fim da lista, sem perder a ordem do resto`() {
        val lista = listOf(
            elemento(1, achado = false), // a IA listou, o capítulo não traz
            elemento(2),
            elemento(3, achado = false),
            elemento(4),
        )

        assertEquals(listOf(2, 4, 1, 3), elementosDoFiltro(lista, FiltroDoPainel.PENDENTES).map { it.id })
    }

    @Test
    fun `o fim da lista vale tambem para os confirmados`() {
        val lista = listOf(
            elemento(1, achado = false, confirmado = true),
            elemento(2, confirmado = true),
        )

        assertEquals(listOf(2, 1), elementosDoFiltro(lista, FiltroDoPainel.CONFIRMADOS).map { it.id })
    }

    // ---- o JSON do servidor ----

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `um servidor antigo sem os campos novos nao gera aviso falso`() {
        val resposta = json.decodeFromString<SugestoesDeCapitulo>(
            """{"gerado_em":"2026-10-01T10:00:00Z","elementos":[{"id":1,"tipo":"PERSONAGEM","nome":"Jon"}],""" +
                """"cenas":[{"id":1,"titulo":"A partida"}]}""",
        )

        assertTrue(resposta.elementos.single().achado_no_texto) // padrão: achado
        assertNull(resposta.cenas.single().frame_id) // padrão: pendente
        assertNull(resposta.orientacao)
    }

    @Test
    fun `le achado_no_texto, frame_id e a orientacao do servidor novo`() {
        val resposta = json.decodeFromString<SugestoesDeCapitulo>(
            """{"orientacao":"falta a cena do porto","elementos":[{"id":1,"tipo":"PERSONAGEM","nome":"Daenerys","achado_no_texto":false}],""" +
                """"cenas":[{"id":1,"titulo":"A partida","frame_id":42}]}""",
        )

        assertFalse(resposta.elementos.single().achado_no_texto)
        assertEquals(42, resposta.cenas.single().frame_id)
        assertEquals("falta a cena do porto", resposta.orientacao)
    }
}
