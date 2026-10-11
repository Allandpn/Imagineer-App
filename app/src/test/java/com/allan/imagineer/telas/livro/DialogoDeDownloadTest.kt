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
    fun DL3_o_item_do_menu_se_chama_Download_e_o_estado_vai_na_linha_de_baixo() {
        assertEquals("Download", ROTULO_DO_DOWNLOAD)
        assertEquals("não baixado", detalheDoDownload(SituacaoDoDownload.NAO_BAIXADO))
        assertEquals("baixando…", detalheDoDownload(SituacaoDoDownload.BAIXANDO))
        assertEquals("parado", detalheDoDownload(SituacaoDoDownload.PARADO))
        assertEquals("baixado", detalheDoDownload(SituacaoDoDownload.EM_DIA))
        assertEquals("há uma atualização", detalheDoDownload(SituacaoDoDownload.DESATUALIZADO))
        assertEquals("falhou", detalheDoDownload(SituacaoDoDownload.FALHOU))
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

/** A situação do download de um livro e o "atualizar" (item 7.5g, DL1 a DL3). */
class SituacaoDoDownloadTest {

    private val baixadoNaRevisao3 = EstadoDoDownload.Baixado(em = 1L, bytes = 2L, revisao = 3)

    @Test
    fun DL2_sem_download_baixando_parado_e_falha_nao_dependem_da_revisao() {
        assertEquals(SituacaoDoDownload.NAO_BAIXADO, situacaoDoDownload(EstadoDoDownload.NaoBaixado, 9))
        assertEquals(SituacaoDoDownload.BAIXANDO, situacaoDoDownload(EstadoDoDownload.Baixando(Progresso(1, 6, 100)), 9))
        assertEquals(SituacaoDoDownload.PARADO, situacaoDoDownload(EstadoDoDownload.Pausado(Progresso(1, 6, 100), "Pausado"), 9))
        assertEquals(SituacaoDoDownload.FALHOU, situacaoDoDownload(EstadoDoDownload.Falhou("x"), 9))
    }

    @Test
    fun DL2_baixado_com_a_mesma_revisao_do_servidor_esta_em_dia() {
        assertEquals(SituacaoDoDownload.EM_DIA, situacaoDoDownload(baixadoNaRevisao3, 3))
    }

    @Test
    fun DL2_o_servidor_com_revisao_maior_deixa_o_download_desatualizado() {
        assertEquals(SituacaoDoDownload.DESATUALIZADO, situacaoDoDownload(baixadoNaRevisao3, 4))
        assertEquals(SituacaoDoDownload.DESATUALIZADO, situacaoDoDownload(EstadoDoDownload.Baixado(1L, 2L), 1)) // um download antigo, de antes da coluna (0)
    }

    @Test
    fun DL2_sem_saber_a_revisao_do_servidor_ou_com_ela_menor_nao_acusa_atualizacao() {
        assertEquals(SituacaoDoDownload.EM_DIA, situacaoDoDownload(baixadoNaRevisao3, null))
        assertEquals(SituacaoDoDownload.EM_DIA, situacaoDoDownload(baixadoNaRevisao3, 2))
    }

    @Test
    fun DL3_baixado_continua_valendo_sem_rede_para_remover_mas_baixar_e_atualizar_nao() {
        assertTrue(downloadValeSemRede(SituacaoDoDownload.EM_DIA))
        assertTrue(downloadValeSemRede(SituacaoDoDownload.DESATUALIZADO))
        assertEquals(false, downloadValeSemRede(SituacaoDoDownload.NAO_BAIXADO))
        assertEquals(false, downloadValeSemRede(SituacaoDoDownload.BAIXANDO))
        assertEquals(false, downloadValeSemRede(SituacaoDoDownload.FALHOU))
    }

    @Test
    fun DL1_cada_situacao_tem_a_sua_descricao_para_o_leitor_de_tela_e_nenhuma_se_repete() {
        val descricoes = SituacaoDoDownload.entries.map { descricaoDoIconeDeDownload(it) }

        assertEquals(SituacaoDoDownload.entries.size, descricoes.toSet().size)
        assertTrue(descricaoDoIconeDeDownload(SituacaoDoDownload.DESATUALIZADO).contains("atualização"))
        assertTrue(descricaoDoIconeDeDownload(SituacaoDoDownload.NAO_BAIXADO).contains("Baixar"))
    }
}
