package com.allan.imagineer.telas.biblioteca

import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.enderecoDaCapa
import com.allan.imagineer.rede.jsonDoImagineer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** As capas da biblioteca (CP3, CP4). */
class CapasDaBibliotecaTest {

    @Test
    fun `o endereco da capa leva a revisao para a capa nova aparecer`() {
        assertEquals("http://100.77.35.58:8000/livros/4/capa?v=7", enderecoDaCapa("http://100.77.35.58:8000/", 4, 7))
    }

    @Test
    fun `o livro do servidor traz tem_capa e revisao, e um servidor antigo sem eles vale sem capa`() {
        val novo = jsonDoImagineer.decodeFromString(
            LivroResumo.serializer(),
            """{"id":1,"titulo":"A","nome_arquivo":"a.epub","data_importacao":"x","total_de_capitulos":3,"capitulos_ignorados":0,"tem_capa":true,"revisao":9}""",
        )
        assertTrue(novo.tem_capa)
        assertEquals(9, novo.revisao)

        val antigo = jsonDoImagineer.decodeFromString(
            LivroResumo.serializer(),
            """{"id":1,"titulo":"A","nome_arquivo":"a.epub","data_importacao":"x","total_de_capitulos":3,"capitulos_ignorados":0}""",
        )
        assertFalse(antigo.tem_capa)
    }

    @Test
    fun `a cor do cartao sem capa e sempre a mesma para o mesmo titulo e se espalha entre titulos`() {
        assertEquals(matizDoTitulo("O Alienista"), matizDoTitulo("O Alienista"), 0f)
        assertEquals(corDaCapaSemImagem("O Alienista"), corDaCapaSemImagem("O Alienista"))
        val matizes = listOf("O Alienista", "O Processo", "Odisseia", "Perdido em Marte", "Mistborn").map { matizDoTitulo(it) }
        assertTrue(matizes.all { it in 0f..359f })
        assertTrue(matizes.toSet().size >= 4) // livros diferentes, cores diferentes (quase sempre)
        assertNotEquals(matizDoTitulo("A"), matizDoTitulo("B"), 0f)
    }
}

/** A grade tem sempre três capas por linha (pedido do Allan). */
class CapasPorLinhaTest {
    @Test
    fun `tres capas por linha`() = assertEquals(3, CAPAS_POR_LINHA)
}
