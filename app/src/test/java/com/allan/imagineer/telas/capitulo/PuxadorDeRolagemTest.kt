package com.allan.imagineer.telas.capitulo

import org.junit.Assert.assertEquals
import org.junit.Test

/** A conta do puxador da rolagem (AJ2): da posição da lista ao puxador, e do puxador de volta à lista. */
class PuxadorDeRolagemTest {

    @Test
    fun no_primeiro_item_o_puxador_fica_no_topo_e_no_ultimo_fica_no_fim() {
        assertEquals(0f, fracaoDoPuxador(0, 11), 0.001f)
        assertEquals(1f, fracaoDoPuxador(10, 11), 0.001f)
        assertEquals(0.5f, fracaoDoPuxador(5, 11), 0.001f)
    }

    @Test
    fun lista_com_um_item_ou_vazia_nao_quebra() {
        assertEquals(0f, fracaoDoPuxador(0, 1), 0.001f)
        assertEquals(0f, fracaoDoPuxador(0, 0), 0.001f)
        assertEquals(0, indiceDoPuxador(0.7f, 0))
    }

    @Test
    fun arrastar_ate_o_meio_leva_ao_item_do_meio() {
        assertEquals(0, indiceDoPuxador(0f, 11))
        assertEquals(5, indiceDoPuxador(0.5f, 11))
        assertEquals(10, indiceDoPuxador(1f, 11))
    }

    @Test
    fun a_fracao_fora_do_intervalo_e_contida() {
        assertEquals(0, indiceDoPuxador(-0.3f, 11))
        assertEquals(10, indiceDoPuxador(1.7f, 11))
    }
}
