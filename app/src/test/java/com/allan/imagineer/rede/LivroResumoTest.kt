package com.allan.imagineer.rede

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O JSON abaixo foi gerado pelo `GET /livros` do backend de verdade (dois EPUBs
 * importados pelo `POST /livros` num `TestClient`), não escrito à mão. O segundo
 * livro cobre o caso de autor e idioma nulos.
 */
class LivroResumoTest {

    private val jsonReal = """
        [{"id":1,"titulo":"A Guerra dos Tronos","autor":"George R. R. Martin","idioma":"pt-BR","nome_arquivo":"guerra.epub","data_importacao":"2026-09-30T01:11:16","total_de_capitulos":2,"capitulos_ignorados":0},{"id":2,"titulo":"Um Livro Sem Autor","autor":null,"idioma":null,"nome_arquivo":"sem-autor.epub","data_importacao":"2026-09-30T01:11:16","total_de_capitulos":2,"capitulos_ignorados":0}]
    """.trimIndent()

    @Test
    fun `desserializa a lista real do backend`() {
        val livros = jsonDoImagineer.decodeFromString<List<LivroResumo>>(jsonReal)

        assertEquals(2, livros.size)
        assertEquals("A Guerra dos Tronos", livros[0].titulo)
        assertEquals("George R. R. Martin", livros[0].autor)
        assertEquals(2, livros[0].total_de_capitulos)
        assertEquals(0, livros[0].capitulos_ignorados)
    }

    @Test
    fun `autor e idioma nulos viram null`() {
        val livro = jsonDoImagineer.decodeFromString<List<LivroResumo>>(jsonReal)[1]

        assertNull(livro.autor)
        assertNull(livro.idioma)
    }

    @Test
    fun `lista vazia do servidor e uma lista vazia`() {
        assertEquals(emptyList<LivroResumo>(), jsonDoImagineer.decodeFromString<List<LivroResumo>>("[]"))
    }
}
