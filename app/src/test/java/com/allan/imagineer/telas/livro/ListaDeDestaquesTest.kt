package com.allan.imagineer.telas.livro

import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.Destaque
import org.junit.Assert.assertEquals
import org.junit.Test

private fun capitulo(id: Int, ordem: Int, titulo: String? = null) = CapituloResumo(id, ordem, titulo, ignorado = false, tamanho_do_texto = 100)
private fun destaque(id: Int, capituloId: Int, inicio: Int) = Destaque(id, 1, capituloId, inicio, inicio + 5, "t$id")

/** A lista "Destaques e notas" do livro (RL12). */
class ListaDeDestaquesTest {

    @Test
    fun agrupa_por_capitulo_na_ordem_do_livro_e_do_texto() {
        val capitulos = listOf(capitulo(10, 1, "A chegada"), capitulo(20, 2))
        val grupos = agruparDestaques(listOf(destaque(1, 20, 0), destaque(2, 10, 50), destaque(3, 10, 5)), capitulos)

        assertEquals(listOf(10, 20), grupos.map { it.capituloId })
        assertEquals(listOf(3, 2), grupos[0].destaques.map { it.id })  // dentro do capítulo, na ordem do texto
        assertEquals("A chegada", grupos[0].titulo)
    }

    @Test
    fun capitulo_que_o_livro_nao_lista_mais_vai_para_o_fim_em_vez_de_sumir() {
        val grupos = agruparDestaques(listOf(destaque(1, 99, 0), destaque(2, 10, 0)), listOf(capitulo(10, 1)))

        assertEquals(listOf(10, 99), grupos.map { it.capituloId })
        assertEquals(Int.MAX_VALUE, grupos.last().ordem)
    }

    @Test
    fun sem_destaques_nao_ha_grupos() {
        assertEquals(emptyList<GrupoDeDestaques>(), agruparDestaques(emptyList(), listOf(capitulo(10, 1))))
    }
}
