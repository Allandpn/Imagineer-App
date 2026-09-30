package com.allan.imagineer.rede

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Garante que a classe Kotlin espelha mesmo o backend (item 7.3a). O JSON abaixo
 * foi gerado pelo `GET /configuracao` do backend de verdade (via `TestClient`),
 * não escrito à mão — o erro típico é um campo renomeado de um lado só.
 */
class ConfiguracaoAtualTest {

    private val jsonReal = """
        {"tem_chave_api":false,"origem_da_chave":"ausente","modelo_extracao":"openai/gpt-4o-mini","modelo_prompt":null,"modelo_perfil":null,"prioridade_ia":"QUALIDADE"}
    """.trimIndent()

    @Test
    fun `desserializa a resposta real do backend`() {
        val configuracao = jsonDoImagineer.decodeFromString<ConfiguracaoAtual>(jsonReal)

        assertFalse(configuracao.tem_chave_api)
        assertEquals("ausente", configuracao.origem_da_chave)
        assertEquals("openai/gpt-4o-mini", configuracao.modelo_extracao)
        assertNull(configuracao.modelo_prompt)
        assertEquals("QUALIDADE", configuracao.prioridade_ia)
    }

    @Test
    fun `ignora campo novo que o backend passe a mandar`() {
        val comCampoNovo = jsonReal.replace("{", """{"campo_do_futuro":123,""")

        val configuracao = jsonDoImagineer.decodeFromString<ConfiguracaoAtual>(comCampoNovo)

        assertEquals("QUALIDADE", configuracao.prioridade_ia)
    }
}
