package com.allan.imagineer.telas.biblioteca

import com.allan.imagineer.rede.LivroResumo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A biblioteca ordenada pelo livro aberto por último (PD10). */
class OrdemDaBibliotecaTest {

    private fun livro(id: Int, importado: String = "2026-01-01T00:00:00+00:00", aberto: String? = null, favorito: Int? = null) = LivroResumo(
        id = id, titulo = "Livro $id", nome_arquivo = "$id.epub", data_importacao = importado,
        total_de_capitulos = 3, capitulos_ignorados = 0, favorito_id = favorito, ultima_abertura = aberto,
    )

    @Test
    fun o_aberto_por_ultimo_vem_primeiro() {
        val livros = listOf(
            livro(1, aberto = "2026-10-10T08:00:00+00:00"),
            livro(2, aberto = "2026-10-12T20:00:00+00:00"),
            livro(3, aberto = "2026-10-11T09:00:00+00:00"),
        )

        assertEquals(listOf(2, 3, 1), ordenarPeloAbertoPorUltimo(livros).map { it.id })
    }

    @Test
    fun o_nunca_lido_entra_pela_data_de_importacao() {
        val livros = listOf(
            livro(1, aberto = "2026-10-01T00:00:00+00:00"),
            livro(2, importado = "2026-10-05T00:00:00+00:00"), // importado depois que o 1 foi aberto
            livro(3, importado = "2026-09-01T00:00:00+00:00"),
        )

        assertEquals(listOf(2, 1, 3), ordenarPeloAbertoPorUltimo(livros).map { it.id })
    }

    @Test
    fun datas_com_e_sem_fuso_e_em_fusos_diferentes_se_comparam_pelo_instante() {
        val livros = listOf(
            livro(1, aberto = "2026-10-12T10:00:00"), // sem fuso: vale como UTC
            livro(2, aberto = "2026-10-12T09:30:00-03:00"), // 12:30 UTC: depois do 1
        )

        assertEquals(listOf(2, 1), ordenarPeloAbertoPorUltimo(livros).map { it.id })
    }

    @Test
    fun empate_e_data_ilegivel_mantem_a_ordem_do_servidor() {
        val livros = listOf(livro(1, importado = "lixo"), livro(2, importado = "lixo"), livro(3, importado = "lixo"))

        assertEquals(listOf(1, 2, 3), ordenarPeloAbertoPorUltimo(livros).map { it.id })
        assertNull(milissegundosDe("lixo"))
        assertNull(milissegundosDe(null))
    }

    @Test
    fun os_favoritos_continuam_no_topo_e_cada_secao_segue_a_ordem() {
        val livros = listOf(
            livro(1, aberto = "2026-10-12T00:00:00+00:00"),
            livro(2, aberto = "2026-10-01T00:00:00+00:00", favorito = 7),
            livro(3, aberto = "2026-10-10T00:00:00+00:00", favorito = 8),
        )

        val secoes = dividirEmSecoes(livros)

        assertEquals(listOf(3, 2), secoes.favoritos.map { it.id })
        assertEquals(listOf(1), secoes.outros.map { it.id })
    }
}
