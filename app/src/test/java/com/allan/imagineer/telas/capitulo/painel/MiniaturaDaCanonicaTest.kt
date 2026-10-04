package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.ImagemDoPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** As miniaturas de um frame com a canônica de outro frame (VM5). */
class MiniaturaDaCanonicaTest {

    @Test
    fun sem_canonica_ou_com_ela_ja_entre_as_do_frame_nada_muda() {
        val doFrame = listOf(ImagemDoPrompt(id = 1), ImagemDoPrompt(id = 2, canonica = true))

        assertSame(doFrame, imagensDoFrameComACanonica(doFrame, null))
        assertSame(doFrame, imagensDoFrameComACanonica(doFrame, 2))
    }

    @Test
    fun canonica_de_outro_frame_entra_marcada_como_canonica() {
        val doFrame = listOf(ImagemDoPrompt(id = 1))

        val miniaturas = imagensDoFrameComACanonica(doFrame, 99)

        assertEquals(listOf(1, 99), miniaturas.map { it.id })
        assertTrue(miniaturas.last().canonica)
    }

    @Test
    fun frame_sem_imagem_nenhuma_mostra_so_a_canonica_vinda_de_fora() {
        assertEquals(listOf(7), imagensDoFrameComACanonica(emptyList(), 7).map { it.id })
    }

    @Test
    fun o_mapa_guarda_e_tira_a_canonica() {
        val mapa = emptyMap<Int, Int>().com(80, 100)
        assertEquals(mapOf(80 to 100), mapa)
        assertEquals(emptyMap<Int, Int>(), mapa.com(80, null))
    }
}
