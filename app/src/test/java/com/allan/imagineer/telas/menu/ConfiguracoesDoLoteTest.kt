package com.allan.imagineer.telas.menu

import com.allan.imagineer.rede.ConfiguracaoAtual
import com.allan.imagineer.rede.DicionarioDoServidor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CF1 a CF3 (item 7.5g): o cartão do modelo de imagem, os modelos favoritos e os dicionários por arrastar. */
class ConfiguracoesDoLoteTest {

    // CF3 -----------------------------------------------------------------------------------------------------------

    @Test
    fun `CF3 os favoritos vem primeiro e cada grupo mantem a ordem de antes`() {
        val modelos = listOf("a", "b", "c", "d", "e")

        val ordenados = ordenarComFavoritos(modelos, setOf("d", "b")) { it }

        assertEquals(listOf("b", "d", "a", "c", "e"), ordenados)
    }

    @Test
    fun `CF3 sem favoritos a lista fica como estava`() {
        val modelos = listOf("x", "y", "z")

        assertEquals(modelos, ordenarComFavoritos(modelos, emptySet()) { it })
    }

    @Test
    fun `CF3 um favorito que nao esta na lista nao aparece nem atrapalha`() {
        assertEquals(listOf("a", "b"), ordenarComFavoritos(listOf("a", "b"), setOf("fantasma")) { it })
    }

    @Test
    fun `CF3 conta quantos sao favoritos, que e onde a lista vira Outros modelos`() {
        assertEquals(2, quantosSaoFavoritos(listOf("b", "d", "a"), setOf("b", "d")) { it })
        assertEquals(0, quantosSaoFavoritos(listOf("a"), setOf("b")) { it })
    }

    @Test
    fun `CF3 tocar na estrela marca e tocar de novo desmarca`() {
        val um = alternarFavorito(emptySet(), "openai/gpt-4o-mini")
        assertEquals(setOf("openai/gpt-4o-mini"), um)

        val dois = alternarFavorito(um, "google/gemini")
        assertEquals(setOf("openai/gpt-4o-mini", "google/gemini"), dois)

        assertEquals(setOf("google/gemini"), alternarFavorito(dois, "openai/gpt-4o-mini"))
    }

    @Test
    fun `CF3 os favoritos guardados voltam iguais, em um id por linha, mesmo com barra e dois pontos`() {
        val favoritos = linkedSetOf("fish-audio/s2.1-pro-free:free", "hexgrad/kokoro-82m")

        val texto = favoritosParaTexto(favoritos)

        assertEquals("fish-audio/s2.1-pro-free:free\nhexgrad/kokoro-82m", texto)
        assertEquals(favoritos.toList(), favoritosDeTexto(texto).toList())
    }

    @Test
    fun `CF3 texto vazio, nulo ou com linhas em branco nao gera favorito`() {
        assertTrue(favoritosDeTexto(null).isEmpty())
        assertTrue(favoritosDeTexto("").isEmpty())
        assertEquals(setOf("a", "b"), favoritosDeTexto("\n a \n\nb\n"))
    }

    @Test
    fun `CF3 as tres categorias guardam em chaves diferentes`() {
        assertEquals(3, setOf(CategoriaDeModelo.TEXTO, CategoriaDeModelo.IMAGEM, CategoriaDeModelo.NARRACAO).size)
    }

    // CF1 -----------------------------------------------------------------------------------------------------------

    @Test
    fun `CF1 o cartao da imagem mostra o modelo escolhido ou que falta escolher`() {
        assertEquals("bytedance-seed/seedream-5-0-flash", descricaoDoModeloDeImagem(ConfiguracaoAtual(tem_chave_api = true, origem_da_chave = "servidor", prioridade_ia = "ECONOMIA", modelo_imagem = "bytedance-seed/seedream-5-0-flash")))
        assertEquals("Nenhum escolhido", descricaoDoModeloDeImagem(ConfiguracaoAtual(tem_chave_api = true, origem_da_chave = "servidor", prioridade_ia = "ECONOMIA", modelo_imagem = null)))
        assertEquals("Nenhum escolhido", descricaoDoModeloDeImagem(ConfiguracaoAtual(tem_chave_api = true, origem_da_chave = "servidor", prioridade_ia = "ECONOMIA", modelo_imagem = "  ")))
    }

    // CF2 -----------------------------------------------------------------------------------------------------------

    private fun dic(id: String) = DicionarioDoServidor(id = id, nome = id.uppercase(), palavras = 10, ativo = true, padrao_para = emptyList())

    private val tres = listOf(dic("a"), dic("b"), dic("c"))

    @Test
    fun `CF2 levar um dicionario para outra posicao empurra os outros`() {
        assertEquals(listOf("c", "a", "b"), moverDicionarioPara(tres, "c", 0).map { it.id })
        assertEquals(listOf("b", "c", "a"), moverDicionarioPara(tres, "a", 2).map { it.id })
        assertEquals(listOf("a", "c", "b"), moverDicionarioPara(tres, "b", 2).map { it.id })
    }

    @Test
    fun `CF2 soltar no mesmo lugar, numa posicao invalida ou um id desconhecido nao muda nada`() {
        assertEquals(tres, moverDicionarioPara(tres, "b", 1))
        assertEquals(tres, moverDicionarioPara(tres, "b", 5))
        assertEquals(tres, moverDicionarioPara(tres, "b", -1))
        assertEquals(tres, moverDicionarioPara(tres, "x", 0))
    }

    private val linhas = listOf(LinhaNaTela("a", 0, 100), LinhaNaTela("b", 108, 100), LinhaNaTela("c", 216, 100))

    @Test
    fun `CF2 o alvo e a linha sobre a qual o meio da linha arrastada caiu`() {
        // O meio de "a" está em 50; descer 120 leva o ponto a 170, que está dentro de "b" (108 a 208).
        assertEquals("b", alvoDoArrasto(linhas, "a", 120f))
        // O meio de "c" está em 266; subir 130 leva a 136, dentro de "b".
        assertEquals("b", alvoDoArrasto(linhas, "c", -130f))
    }

    @Test
    fun `CF2 sem arrastar quase nada o alvo e a propria linha`() {
        assertEquals("b", alvoDoArrasto(linhas, "b", 4f))
    }

    @Test
    fun `CF2 arrastar alem das pontas vale a primeira ou a ultima linha`() {
        assertEquals("c", alvoDoArrasto(linhas, "a", 900f))
        assertEquals("a", alvoDoArrasto(linhas, "c", -900f))
    }

    @Test
    fun `CF2 se a linha arrastada nao esta na tela, nao ha alvo`() {
        assertNull(alvoDoArrasto(linhas, "z", 50f))
        assertNull(alvoDoArrasto(emptyList(), "a", 50f))
    }

    @Test
    fun `CF2 o alvo e a posicao final casam num arrastar de ponta a ponta`() {
        val alvo = alvoDoArrasto(linhas, "a", 230f)!!
        val posicao = tres.indexOfFirst { it.id == alvo }

        assertEquals(listOf("b", "c", "a"), moverDicionarioPara(tres, "a", posicao).map { it.id })
        assertFalse(alvo == "a")
    }
}
