package com.allan.imagineer.rede

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Os JSONs abaixo foram gerados pelo backend de verdade (`POST /livros` e `PATCH
 * /livros/{id}` num `TestClient`), não escritos à mão. A importação é de um EPUB
 * **sem título nem autor** e com o mesmo identificador de um livro que já existia:
 * o caso que exercita `livros_semelhantes` e `metadados_pendentes` de uma vez.
 */
class LivroDetalheTest {

    private val importacaoReal = """
        {"livro":{"id":2,"titulo":"segundo","autor":null,"idioma":"pt-BR","nome_arquivo":"segundo.epub","data_importacao":"2026-09-30T01:25:34","total_de_capitulos":2,"capitulos_ignorados":0,"identificador_epub":"urn:isbn:9788580410150","perfil_renderizacao_padrao_id":null,"metadados_pendentes":["titulo","autor"],"capitulos":[{"id":3,"ordem":1,"titulo":"Bran","ignorado":false,"tamanho_do_texto":203,"sugestoes_pendentes":0},{"id":4,"ordem":2,"titulo":"Catelyn","ignorado":false,"tamanho_do_texto":206,"sugestoes_pendentes":0}]},"livros_semelhantes":[{"id":1,"titulo":"A Guerra dos Tronos","autor":"George R. R. Martin","idioma":"pt-BR","nome_arquivo":"primeiro.epub","data_importacao":"2026-09-30T01:25:34","total_de_capitulos":2,"capitulos_ignorados":0}]}
    """.trimIndent()

    private val ajusteReal = """
        {"id":2,"titulo":"Titulo Corrigido","autor":null,"idioma":"pt-BR","nome_arquivo":"segundo.epub","data_importacao":"2026-09-30T01:25:34","total_de_capitulos":2,"capitulos_ignorados":0,"identificador_epub":"urn:isbn:9788580410150","perfil_renderizacao_padrao_id":null,"metadados_pendentes":["autor"],"capitulos":[{"id":3,"ordem":1,"titulo":"Bran","ignorado":false,"tamanho_do_texto":203,"sugestoes_pendentes":0},{"id":4,"ordem":2,"titulo":"Catelyn","ignorado":false,"tamanho_do_texto":206,"sugestoes_pendentes":0}]}
    """.trimIndent()

    @Test
    fun `desserializa a resposta real de POST livros`() {
        val resposta = jsonDoImagineer.decodeFromString<RespostaImportacao>(importacaoReal)

        assertEquals(2, resposta.livro.id)
        assertNull(resposta.livro.autor)
        assertEquals(listOf("titulo", "autor"), resposta.livro.metadados_pendentes)
        assertEquals(2, resposta.livro.capitulos.size)
        assertEquals("Bran", resposta.livro.capitulos[0].titulo)
        assertEquals(203, resposta.livro.capitulos[0].tamanho_do_texto)
    }

    @Test
    fun `livros semelhantes vem como resumos`() {
        val resposta = jsonDoImagineer.decodeFromString<RespostaImportacao>(importacaoReal)

        assertEquals(1, resposta.livros_semelhantes.size)
        assertEquals("A Guerra dos Tronos", resposta.livros_semelhantes[0].titulo)
        assertEquals("primeiro.epub", resposta.livros_semelhantes[0].nome_arquivo)
    }

    @Test
    fun `desserializa a resposta real de PATCH livros`() {
        val livro = jsonDoImagineer.decodeFromString<LivroDetalhe>(ajusteReal)

        assertEquals("Titulo Corrigido", livro.titulo)
        // Só o autor continua pendente: mandar o título o confirmou.
        assertEquals(listOf("autor"), livro.metadados_pendentes)
    }

    @Test
    fun `o ajuste omite os campos nulos do JSON`() {
        // No backend, campo ausente = "não mexa"; campo null = "limpe". Mandar
        // `"autor": null` sem querer apagaria o autor — por isso omitir importa.
        val soTitulo = jsonDoImagineer.encodeToString(LivroAjuste(titulo = "Novo"))
        val soAutor = jsonDoImagineer.encodeToString(LivroAjuste(autor = "Fulano"))

        assertEquals("""{"titulo":"Novo"}""", soTitulo)
        assertEquals("""{"autor":"Fulano"}""", soAutor)
        assertFalse("autor" in soTitulo)
    }
}
