package com.allan.imagineer.local

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** O armazém de verdade, em arquivos, numa pasta temporária. */
class ArmazemDeTextosEmArquivosTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val chave = ChaveDoCache("http://servidor:8000")

    private fun armazem() = ArmazemDeTextosEmArquivos(pasta.root)

    @Test
    fun `grava e le o texto de volta, com acentos e quebras`() = runTest {
        val texto = "Não há título.\n\nSegundo parágrafo — com travessão."

        val bytes = armazem().gravar(chave, 1, 10, texto)

        assertEquals(texto, armazem().ler(chave, 1, 10))
        assertEquals(texto.toByteArray(Charsets.UTF_8).size.toLong(), bytes) // bytes, não caracteres
    }

    @Test
    fun `ler o que nunca foi gravado devolve null`() = runTest {
        assertNull(armazem().ler(chave, 1, 10))
    }

    @Test
    fun `regravar substitui o texto anterior`() = runTest {
        armazem().gravar(chave, 1, 10, "antigo")
        armazem().gravar(chave, 1, 10, "novo")

        assertEquals("novo", armazem().ler(chave, 1, 10))
    }

    @Test
    fun `nao deixa arquivo temporario para tras`() = runTest {
        armazem().gravar(chave, 1, 10, "texto")

        val sobras = pasta.root.walkTopDown().filter { it.name.endsWith(".tmp") }.toList()
        assertTrue(sobras.isEmpty())
    }

    @Test
    fun `apagar o livro remove so os textos dele`() = runTest {
        armazem().gravar(chave, 1, 10, "do livro 1")
        armazem().gravar(chave, 2, 20, "do livro 2")

        armazem().apagarLivro(chave, 1)

        assertNull(armazem().ler(chave, 1, 10))
        assertEquals("do livro 2", armazem().ler(chave, 2, 20))
    }

    @Test
    fun `servidores diferentes usam pastas diferentes`() = runTest {
        val outra = ChaveDoCache("http://outro:8000")
        armazem().gravar(chave, 1, 10, "do servidor A")

        assertNull(armazem().ler(outra, 1, 10))
        assertFalse(java.io.File(pasta.root, "${outra.identificador}").exists())
    }

    @Test
    fun `a chave gera um identificador estavel, curto e seguro para nome de pasta`() {
        val a = ChaveDoCache("http://100.64.0.1:8000")

        assertEquals(a.identificador, ChaveDoCache("http://100.64.0.1:8000").identificador)
        assertEquals(12, a.identificador.length)
        assertTrue(a.identificador.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(a.identificador, ChaveDoCache("http://100.64.0.2:8000").identificador)
        assertNotEquals(a.identificador, ChaveDoCache("http://100.64.0.1:8000", conta = "maria").identificador)
    }
}
