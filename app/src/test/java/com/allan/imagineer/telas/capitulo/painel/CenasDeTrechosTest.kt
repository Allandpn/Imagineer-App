package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.Artefato
import org.junit.Assert.assertEquals
import org.junit.Test

private fun artefato(
    tipo: String = "CENA",
    sugestaoId: Int? = null,
    frameId: Int? = null,
    rotulo: String = "x",
    situacao: String = "CONFIRMADO",
) = Artefato(tipo = tipo, sugestao_id = sugestaoId, frame_id = frameId, rotulo = rotulo, situacao = situacao)

/** As cenas criadas de um texto selecionado aparecem na lista do painel de IA, junto das confirmadas. */
class CenasDeTrechosTest {

    @Test
    fun so_entra_cena_com_frame_e_sem_sugestao_da_ia() {
        val artefatos = listOf(
            artefato(tipo = "CENA", frameId = 70, rotulo = "Do trecho"),
            artefato(tipo = "CENA", sugestaoId = 4, frameId = 71, rotulo = "Sugerida e confirmada"),
            artefato(tipo = "CENA", sugestaoId = 5, rotulo = "Sugerida pendente"),
            artefato(tipo = "ELEMENTO", sugestaoId = 6, frameId = 72, rotulo = "Retrato de Jon"),
            artefato(tipo = "ELEMENTO", frameId = 73, rotulo = "Elemento sem sugestão"),
        )

        assertEquals(listOf("Do trecho"), cenasDeTrechos(artefatos).map { it.rotulo })
    }

    @Test
    fun uma_cena_por_frame_e_na_ordem_do_capitulo() {
        val artefatos = listOf(
            artefato(frameId = 80, rotulo = "Primeira"),
            artefato(frameId = 81, rotulo = "Segunda"),
            artefato(frameId = 80, rotulo = "Repetida"),
        )

        assertEquals(listOf("Primeira", "Segunda"), cenasDeTrechos(artefatos).map { it.rotulo })
    }

    @Test
    fun sem_cenas_de_trechos_a_lista_e_vazia() {
        assertEquals(emptyList<Artefato>(), cenasDeTrechos(emptyList()))
    }

    @Test
    fun a_situacao_diz_o_que_a_cena_ja_tem() {
        assertEquals("Com imagem", descreverSituacaoDaCenaDeTrecho("ILUSTRADO"))
        assertEquals("Prompt pronto", descreverSituacaoDaCenaDeTrecho("PROMPT_PRONTO"))
        assertEquals("Sem prompt ainda", descreverSituacaoDaCenaDeTrecho("CONFIRMADO"))
    }
}
