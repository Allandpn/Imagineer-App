package com.allan.imagineer.telas.biblioteca

import com.allan.imagineer.rede.LivroResumo
import org.junit.Assert.assertEquals
import org.junit.Test

private fun livro(total: Int, arquivados: Int, caracteres: Int) = LivroResumo(
    id = 1, titulo = "A", nome_arquivo = "a.epub", data_importacao = "x",
    total_de_capitulos = total, capitulos_ignorados = arquivados, total_de_caracteres = caracteres,
)

/** A linha de metadados do livro na lista da biblioteca: capítulos (sem arquivados) e tempo total de leitura. */
class ResumoDoLivroNaListaTest {

    @Test
    fun `mostra os capitulos ativos e o tempo de leitura, sem falar dos arquivados`() {
        assertEquals("10 capítulos · 1 h 20 min", resumoDoLivroNaLista(livro(total = 12, arquivados = 2, caracteres = 104_000)))
    }

    @Test
    fun `um capitulo so, no singular`() {
        assertEquals("1 capítulo · 3 min", resumoDoLivroNaLista(livro(total = 1, arquivados = 0, caracteres = 3_900)))
    }

    @Test
    fun `servidor sem o total de caracteres deixa so os capitulos`() {
        assertEquals("5 capítulos", resumoDoLivroNaLista(livro(total = 5, arquivados = 0, caracteres = 0)))
    }
}
