package com.allan.imagineer.rede

import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** O corpo do upload: lê aos poucos e avisa o progresso (item 7.3a, incremento 5). */
class CorpoComProgressoTest {

    private fun bytes(quantidade: Int) = ByteArray(quantidade) { (it % 251).toByte() }

    @Test
    fun `envia o conteudo inteiro, byte a byte igual`() {
        val original = bytes(1_000_000)
        val corpo = CorpoComProgresso(ByteArrayInputStream(original), null, original.size.toLong()) { _, _ -> }
        val destino = Buffer()

        corpo.writeTo(destino)

        assertArrayEquals(original, destino.readByteArray())
    }

    @Test
    fun `o progresso nunca recua e termina no total`() {
        val original = bytes(1_000_000)
        val avisos = mutableListOf<Long>()
        val corpo = CorpoComProgresso(ByteArrayInputStream(original), null, original.size.toLong()) { enviados, total ->
            assertEquals(original.size.toLong(), total)
            avisos += enviados
        }

        corpo.writeTo(Buffer())

        assertEquals(avisos.sorted(), avisos)
        assertEquals(original.size.toLong(), avisos.last())
    }

    @Test
    fun `nao avisa a cada leitura de 8 KB`() {
        // 1 MB em blocos de 8 KB seriam ~122 leituras; o aviso é limitado por passo.
        val original = bytes(1_000_000)
        var chamadas = 0
        val corpo = CorpoComProgresso(ByteArrayInputStream(original), null, original.size.toLong()) { _, _ -> chamadas++ }

        corpo.writeTo(Buffer())

        assertTrue("avisou $chamadas vezes", chamadas <= 6)
    }

    @Test
    fun `tamanho desconhecido vira envio em pedacos e progresso sem total`() {
        val original = bytes(600_000)
        var ultimoTotal: Long? = -1L
        val corpo = CorpoComProgresso(ByteArrayInputStream(original), null, null) { _, total -> ultimoTotal = total }

        corpo.writeTo(Buffer())

        assertEquals(-1L, corpo.contentLength())
        assertNull(ultimoTotal)
    }

    @Test
    fun `contentLength informa o tamanho conhecido`() {
        val corpo = CorpoComProgresso(ByteArrayInputStream(bytes(10)), null, 10L) { _, _ -> }

        assertEquals(10L, corpo.contentLength())
    }

    @Test
    fun `e de uso unico, para o OkHttp nao reenviar um fluxo ja consumido`() {
        val corpo = CorpoComProgresso(ByteArrayInputStream(bytes(10)), null, 10L) { _, _ -> }

        assertTrue(corpo.isOneShot())
    }

    @Test
    fun `arquivo vazio nao quebra`() {
        val corpo = CorpoComProgresso(ByteArrayInputStream(ByteArray(0)), null, 0L) { _, _ -> }
        val destino = Buffer()

        corpo.writeTo(destino)

        assertEquals(0L, destino.size)
    }
}
