package com.allan.imagineer.telas.biblioteca

import com.allan.imagineer.rede.LivroResumo
import org.junit.Assert.assertEquals
import org.junit.Test

private fun livro(id: Int, favorito: Int? = null) = LivroResumo(
    id = id, titulo = "Livro $id", nome_arquivo = "l$id.epub", data_importacao = "2026-10-05T00:00:00",
    total_de_capitulos = 3, capitulos_ignorados = 0, favorito_id = favorito,
)

/** O coração da biblioteca: só os livros favoritos (RL36). */
class FiltroDeFavoritosTest {

    @Test
    fun sem_o_filtro_mostra_todos_na_ordem_do_servidor() {
        val lista = listOf(livro(1), livro(2, favorito = 9), livro(3))

        assertEquals(listOf(1, 2, 3), livrosParaMostrar(lista, soFavoritos = false).map { it.id })
    }

    @Test
    fun com_o_filtro_so_os_que_tem_favorito() {
        val lista = listOf(livro(1), livro(2, favorito = 9), livro(3, favorito = 10))

        assertEquals(listOf(2, 3), livrosParaMostrar(lista, soFavoritos = true).map { it.id })
    }

    @Test
    fun com_o_filtro_e_nenhum_favorito_a_lista_fica_vazia() {
        assertEquals(emptyList<LivroResumo>(), livrosParaMostrar(listOf(livro(1), livro(2)), soFavoritos = true))
    }
}
