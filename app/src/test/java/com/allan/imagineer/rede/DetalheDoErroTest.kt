package com.allan.imagineer.rede

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tirar a mensagem de um erro do backend (item 7.3a, incremento 5). */
class DetalheDoErroTest {

    @Test
    fun `detail em texto vira a mensagem`() {
        // Corpo real do backend para um EPUB inválido.
        val corpo = """{"detail":"Não foi possível ler 'lixo.epub' como EPUB: 'Bad Zip file'"}"""

        assertEquals(
            "Não foi possível ler 'lixo.epub' como EPUB: 'Bad Zip file'",
            extrairDetalhe(corpo),
        )
    }

    @Test
    fun `detail de validacao do FastAPI e uma lista tecnica e e ignorado`() {
        val corpo = """{"detail":[{"type":"missing","loc":["body","arquivo"],"msg":"Field required"}]}"""

        assertNull(extrairDetalhe(corpo))
    }

    @Test
    fun `texto puro, comum num erro 500, nao quebra`() {
        assertNull(extrairDetalhe("Internal Server Error"))
    }

    @Test
    fun `corpo vazio ou nulo`() {
        assertNull(extrairDetalhe(null))
        assertNull(extrairDetalhe(""))
        assertNull(extrairDetalhe("   "))
    }

    @Test
    fun `JSON sem detail`() {
        assertNull(extrairDetalhe("""{"erro":"algo"}"""))
        assertNull(extrairDetalhe("[1, 2, 3]"))
    }

    @Test
    fun `detail que nao e texto nao e uma mensagem`() {
        assertNull(extrairDetalhe("""{"detail":404}"""))
        assertNull(extrairDetalhe("""{"detail":""}"""))
    }
}
