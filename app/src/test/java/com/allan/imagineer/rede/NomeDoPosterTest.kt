package com.allan.imagineer.rede

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** O nome do primeiro quadro guardado de cada vídeo (PD7): o número do vídeo só vale dentro de um servidor. */
class NomeDoPosterTest {

    @Test
    fun o_mesmo_numero_de_video_em_servidores_diferentes_nao_divide_o_arquivo() {
        // O "vídeo 1" do PC e o "vídeo 1" do Pi são vídeos diferentes: guardados pelo número, um mostrava o quadro do outro.
        assertNotEquals(nomeDoPoster("a1b2c3d4e5f6", 1), nomeDoPoster("0f9e8d7c6b5a", 1))
    }

    @Test
    fun no_mesmo_servidor_cada_video_tem_o_seu_arquivo() {
        assertNotEquals(nomeDoPoster("a1b2c3d4e5f6", 1), nomeDoPoster("a1b2c3d4e5f6", 2))
        assertEquals("poster_a1b2c3d4e5f6_7.jpg", nomeDoPoster("a1b2c3d4e5f6", 7))
    }

    @Test
    fun so_o_nome_do_tempo_sem_servidor_e_descartado() {
        assertTrue(ehPosterSemServidor("poster_3.jpg"))
        assertTrue(ehPosterSemServidor("poster_120.jpg"))
        assertFalse(ehPosterSemServidor(nomeDoPoster("a1b2c3d4e5f6", 3)))
        assertFalse(ehPosterSemServidor("baixando_3.mp4"))
    }
}
