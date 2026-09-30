package com.allan.imagineer.dados

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Item 7.3a: a normalização do endereço do servidor. */
class NormalizarUrlTest {

    @Test
    fun `sem esquema assume http`() {
        assertEquals("http://100.64.0.5:8000", normalizarUrl("100.64.0.5:8000"))
    }

    @Test
    fun `mantem http e https como digitados`() {
        assertEquals("http://servidor:8000", normalizarUrl("http://servidor:8000"))
        assertEquals("https://servidor.exemplo.com", normalizarUrl("https://servidor.exemplo.com"))
    }

    @Test
    fun `remove barra final, uma ou varias`() {
        assertEquals("http://servidor:8000", normalizarUrl("http://servidor:8000/"))
        assertEquals("http://servidor:8000", normalizarUrl("http://servidor:8000///"))
    }

    @Test
    fun `apara espacos`() {
        assertEquals("http://servidor:8000", normalizarUrl("  servidor:8000  "))
    }

    @Test
    fun `aceita nome de maquina do tailscale`() {
        assertEquals("http://raspberrypi:8000", normalizarUrl("raspberrypi:8000"))
    }

    @Test
    fun `recusa texto vazio ou so espacos`() {
        assertNull(normalizarUrl(""))
        assertNull(normalizarUrl("   "))
    }

    @Test
    fun `recusa esquema que nao seja http ou https`() {
        assertNull(normalizarUrl("ftp://servidor"))
        assertNull(normalizarUrl("file:///etc/passwd"))
    }

    @Test
    fun `recusa endereco sem host`() {
        assertNull(normalizarUrl("http://"))
        assertNull(normalizarUrl("http://:8000"))
    }

    @Test
    fun `recusa texto com espaco no meio`() {
        assertNull(normalizarUrl("isto nao e um endereco"))
    }
}
