package com.allan.imagineer.telas.capitulo.painel

import org.junit.Assert.assertEquals
import org.junit.Test

/** Deslizar o dedo troca o filtro do painel de IA: Pendentes, Confirmados e Descartados, nessa ordem. */
class FiltroPorDeslizeTest {

    @Test
    fun deslizar_para_a_esquerda_vai_ao_proximo_e_para_a_direita_ao_anterior() {
        assertEquals(FiltroDoPainel.CONFIRMADOS, filtroVizinho(FiltroDoPainel.PENDENTES, +1))
        assertEquals(FiltroDoPainel.DESCARTADOS, filtroVizinho(FiltroDoPainel.CONFIRMADOS, +1))
        assertEquals(FiltroDoPainel.CONFIRMADOS, filtroVizinho(FiltroDoPainel.DESCARTADOS, -1))
        assertEquals(FiltroDoPainel.PENDENTES, filtroVizinho(FiltroDoPainel.CONFIRMADOS, -1))
    }

    @Test
    fun nas_pontas_nao_da_a_volta() {
        assertEquals(FiltroDoPainel.PENDENTES, filtroVizinho(FiltroDoPainel.PENDENTES, -1))
        assertEquals(FiltroDoPainel.DESCARTADOS, filtroVizinho(FiltroDoPainel.DESCARTADOS, +1))
    }

    @Test
    fun a_ordem_dos_filtros_e_a_das_etiquetas_na_tela() {
        assertEquals(listOf("Pendentes", "Confirmados", "Descartados"), FiltroDoPainel.entries.map { it.rotulo })
    }
}
