package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.PromptDeFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mostrar qual modelo escreveu o prompt e de onde veio cada imagem (item 7.5b, ML1 e ML2). */
class MostrarModeloTest {

    private fun prompt(modelo: String?, original: Int? = null, soImagem: Boolean = false) =
        PromptDeFrame(id = 1, frame_id = 70, texto = "p", modelo_ia = modelo, prompt_original_id = original, so_imagem = soImagem)

    @Test
    fun `o nome curto tira o fornecedor antes da barra`() {
        assertEquals("gemini-3.1-flash-lite", nomeCurtoDoModelo("google/gemini-3.1-flash-lite"))
        assertEquals("sem-barra", nomeCurtoDoModelo("sem-barra"))
    }

    @Test
    fun `os modelos da assinatura do Claude ganham o nome amigavel`() {
        assertEquals("Claude Sonnet (assinatura)", nomeCurtoDoModelo("claude-code/sonnet"))
        assertEquals("Claude Haiku (assinatura)", nomeCurtoDoModelo("claude-code/haiku"))
    }

    @Test
    fun `o prompt original diz quem o escreveu`() {
        assertEquals(listOf("Escrito por gemini-3.1-flash-lite"), etiquetasDoPrompt(prompt("google/gemini-3.1-flash-lite")))
        assertEquals(listOf("Escrito por Claude Sonnet (assinatura)"), etiquetasDoPrompt(prompt("claude-code/sonnet")))
    }

    @Test
    fun `sem modelo gravado, prompt antigo, nao ha etiqueta`() {
        assertTrue(etiquetasDoPrompt(prompt(null)).isEmpty())
        assertTrue(etiquetasDoPrompt(prompt("  ")).isEmpty())
    }

    @Test
    fun `o prompt so da imagem nao diz quem o escreveu`() {
        assertTrue(etiquetasDoPrompt(prompt("google/x", soImagem = true)).isEmpty())
    }

    @Test
    fun `a versao suavizada ou editada nao repete o modelo`() {
        assertEquals(listOf("Versão suavizada"), etiquetasDoPrompt(prompt("barato/modelo", original = 1)))
        assertEquals(listOf("Versão editada"), etiquetasDoPrompt(prompt(null, original = 1)))
    }

    @Test
    fun `a miniatura diz se a imagem foi gerada e por qual modelo ou se foi importada`() {
        assertEquals("Gerada · muse-spark", legendaDaMiniatura(ImagemDoPrompt(id = 1, modelo = "meta/muse-spark")))
        assertEquals("Importada", legendaDaMiniatura(ImagemDoPrompt(id = 2, modelo = null)))
        assertEquals("Importada", legendaDaMiniatura(ImagemDoPrompt(id = 3, modelo = "")))
    }
}
