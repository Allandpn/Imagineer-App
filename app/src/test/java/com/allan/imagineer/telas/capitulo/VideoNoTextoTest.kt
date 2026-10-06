package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.rede.Artefato
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun comVideo(rotulo: String, videoId: Int, orientacao: String?, imagemId: Int? = null) = Artefato(
    tipo = "CENA", rotulo = rotulo, situacao = "ILUSTRADO", imagem_id = imagemId, video_id = videoId, video_orientacao = orientacao,
)

/** O vídeo posicionado no texto como uma imagem (VD17): entra no fluxo, com o formato do vídeo. */
class VideoNoTextoTest {

    @Test
    fun o_video_entra_no_texto_mesmo_sem_imagem() {
        val lista = listOf(comVideo("A", 5, "PAISAGEM"), Artefato(tipo = "CENA", rotulo = "B", situacao = "ILUSTRADO"))

        assertEquals(listOf("A"), imagensDoParagrafo(lista).map { it.rotulo })
    }

    @Test
    fun o_mesmo_video_aparece_uma_vez_e_o_video_nao_se_confunde_com_a_imagem_de_mesmo_id() {
        val lista = listOf(
            comVideo("A", 10, "PAISAGEM"),
            comVideo("A de novo", 10, "PAISAGEM"),
            Artefato(tipo = "CENA", rotulo = "B", situacao = "ILUSTRADO", imagem_id = 10),  // imagem 10, outra coisa que o vídeo 10
        )

        assertEquals(listOf("A", "B"), imagensDoParagrafo(lista).map { it.rotulo })
    }

    @Test
    fun o_formato_do_quadro_e_o_do_video_quando_ha_video() {
        val retrato = comVideo("A", 5, "RETRATO")
        val imagemPaisagem = Artefato(tipo = "CENA", rotulo = "I", situacao = "ILUSTRADO", imagem_id = 3, imagem_orientacao = "PAISAGEM")

        assertEquals("RETRATO", orientacaoDoQuadro(retrato))
        assertEquals("PAISAGEM", orientacaoDoQuadro(imagemPaisagem))
        assertNull(orientacaoDoQuadro(comVideo("W", 6, null)))  // WebM, sem tamanho
        assertEquals(QuadroDaImagem.PAISAGEM, quadroDaImagem(orientacaoDoQuadro(comVideo("W", 6, null))))  // e vale paisagem
    }

    @Test
    fun video_retrato_abre_um_bloco_com_texto_ao_lado_e_paisagem_fica_em_largura_inteira() {
        val retrato = comVideo("R", 5, "RETRATO")
        val paisagem = comVideo("P", 6, "PAISAGEM")

        val comRetrato = montarBlocos(3, { if (it == 1) listOf(retrato) else emptyList() }, 100f, 10f) { List(12) { LinhaMedida(fundo = (it + 1) * 20f, fim = (it + 1) * 10) } }
        val comPaisagem = montarBlocos(3, { if (it == 1) listOf(paisagem) else emptyList() }, 100f, 10f) { List(12) { LinhaMedida(fundo = (it + 1) * 20f, fim = (it + 1) * 10) } }

        assertTrue(comRetrato.any { it is BlocoDoTexto.ComRetrato && it.retrato == retrato })
        assertEquals(BlocoDoTexto.Comum(FatiaDeParagrafo(1), listOf(paisagem)), comPaisagem[1])
    }

    @Test
    fun o_artefato_do_servidor_traz_o_formato_do_video() {
        val json = Json { ignoreUnknownKeys = true }

        val artefato = json.decodeFromString<Artefato>("""{"tipo":"CENA","rotulo":"A","situacao":"ILUSTRADO","video_id":4,"video_largura":720,"video_altura":1280,"video_orientacao":"RETRATO"}""")

        assertEquals(4, artefato.video_id)
        assertEquals("RETRATO", artefato.video_orientacao)
        assertNull(artefato.imagem_id)
        assertEquals(720, artefato.video_largura)
    }
}
