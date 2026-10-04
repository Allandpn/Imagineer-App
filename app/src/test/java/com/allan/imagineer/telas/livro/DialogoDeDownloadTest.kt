package com.allan.imagineer.telas.livro

import com.allan.imagineer.local.EstadoDoDownload
import com.allan.imagineer.local.Estimativa
import com.allan.imagineer.local.Progresso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Os textos do "Baixar para ler offline" (PL3, PL4). */
class DialogoDeDownloadTest {

    @Test
    fun o_item_do_menu_muda_com_o_estado_do_download() {
        assertEquals("Baixar", rotuloDoOffline(EstadoDoDownload.NaoBaixado))
        assertEquals("Baixar", rotuloDoOffline(EstadoDoDownload.Falhou("x")))
        assertEquals("Baixando", rotuloDoOffline(EstadoDoDownload.Baixando(Progresso(1, 6, 100))))
        assertEquals("Baixando", rotuloDoOffline(EstadoDoDownload.Pausado(Progresso(1, 6, 100), "Pausado")))
        assertEquals("Baixado", rotuloDoOffline(EstadoDoDownload.Baixado(1L, 2L)))
    }

    @Test
    fun o_progresso_diz_arquivos_e_espaco_e_a_fracao_nunca_passa_de_um() {
        assertEquals("2 de 6 arquivos · 0 B", descreverProgressoDoDownload(Progresso(2, 6, 0)).replace("0,0 B", "0 B").replace("0 bytes", "0 B"))
        assertEquals(0.5f, Progresso(3, 6, 10).fracao, 0.001f)
        assertEquals(0f, Progresso(0, 0, 0).fracao, 0.001f)
        assertEquals(1f, Progresso(9, 6, 0).fracao, 0.001f)
    }

    @Test
    fun a_confirmacao_diz_quanto_vai_baixar_ou_que_ja_esta_no_aparelho() {
        assertTrue(textoDaEstimativa(Estimativa(imagens = 10, bytesDosOriginais = 5_000_000, bytesJaBaixados = 0)).startsWith("Baixar — "))
        assertTrue(textoDaEstimativa(Estimativa(imagens = 10, bytesDosOriginais = 5_000_000, bytesJaBaixados = 5_000_000)).contains("já estão no aparelho"))
        assertTrue(textoDaEstimativa(Estimativa(imagens = 0, bytesDosOriginais = 0, bytesJaBaixados = 0)).contains("não tem imagens"))
    }

    @Test
    fun a_estimativa_nunca_fica_negativa() {
        assertEquals(0L, Estimativa(1, 100, 500).bytesRestantes)
        assertEquals(60L, Estimativa(1, 100, 40).bytesRestantes)
    }
}
