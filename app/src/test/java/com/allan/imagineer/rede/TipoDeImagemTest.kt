package com.allan.imagineer.rede

import org.junit.Assert.assertEquals
import org.junit.Test

/** O tipo de imagem pelos primeiros bytes: a rede de segurança do "Salvar na galeria" (o Android recusa octet-stream). */
class TipoDeImagemTest {

    private fun bytes(vararg valores: Int) = ByteArray(valores.size) { valores[it].toByte() }

    @Test
    fun `reconhece png, jpeg, gif e webp pelo cabecalho`() {
        assertEquals("image/png", tipoDeImagemPeloCabecalho(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)))
        assertEquals("image/jpeg", tipoDeImagemPeloCabecalho(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals("image/gif", tipoDeImagemPeloCabecalho(bytes(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0, 0, 0, 0, 0)))
        val webp = "RIFF".toByteArray() + bytes(0x10, 0, 0, 0) + "WEBP".toByteArray()
        assertEquals("image/webp", tipoDeImagemPeloCabecalho(webp))
    }

    @Test
    fun `RIFF que nao e webp e cabecalho curto ou desconhecido viram png`() {
        val wav = "RIFF".toByteArray() + bytes(0x10, 0, 0, 0) + "WAVE".toByteArray()
        assertEquals("image/png", tipoDeImagemPeloCabecalho(wav))
        assertEquals("image/png", tipoDeImagemPeloCabecalho(ByteArray(0)))
        assertEquals("image/png", tipoDeImagemPeloCabecalho(bytes(0xFF, 0xD8)))
    }
}
