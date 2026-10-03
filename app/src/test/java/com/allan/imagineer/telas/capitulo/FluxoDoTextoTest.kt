package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.rede.Artefato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Linhas de 20 px de altura, `n` linhas, cada uma terminando 10 caracteres depois da outra. */
private fun linhas(n: Int, altura: Float = 20f, caracteres: Int = 10) =
    List(n) { LinhaMedida(fundo = (it + 1) * altura, fim = (it + 1) * caracteres) }

private fun comImagem(orientacao: String, id: Int) = Artefato(
    tipo = "ELEMENTO", tipo_do_elemento = "PERSONAGEM", rotulo = "x$id", posicao_no_texto = 0, situacao = "ILUSTRADO",
    imagem_id = id, imagem_orientacao = orientacao,
)

class DividirAoRedorDaImagemTest {

    @Test
    fun `um paragrafo mais longo que a imagem e cortado no fim da ultima linha que cabe`() {
        // Imagem de 100 px; o parágrafo tem 12 linhas de 20 px: cabem 5 (5 x 20 = 100).
        val divisao = dividirAoRedorDaImagem(100f, 10f, 1) { linhas(12) }

        assertEquals(DivisaoAoRedorDaImagem(paragrafosInteiros = 0, corte = 50), divisao)
    }

    @Test
    fun `um paragrafo curto deixa a coluna ao lado para os seguintes ate encher a altura`() {
        // Imagem de 200 px. Parágrafos de 3 linhas (60 px) + 10 de espaço: 1º até 60, 2º até 130, 3º até 200 (cabe e enche).
        val divisao = dividirAoRedorDaImagem(200f, 10f, 5) { linhas(3) }

        assertEquals(DivisaoAoRedorDaImagem(paragrafosInteiros = 3, corte = null), divisao)
    }

    @Test
    fun `o paragrafo que passa da altura e cortado, e os inteiros antes dele ficam ao lado`() {
        // Imagem de 150 px. 1º: 60 px (+10 = 70). 2º, de 10 linhas, começa em 70: cabem as linhas com fundo <= 150 - 70 = 80 -> 4.
        val divisao = dividirAoRedorDaImagem(150f, 10f, 5) { if (it == 0) linhas(3) else linhas(10) }

        assertEquals(DivisaoAoRedorDaImagem(paragrafosInteiros = 1, corte = 40), divisao)
    }

    @Test
    fun `se nenhuma linha do proximo cabe, ele nao e cortado e fica embaixo inteiro`() {
        // Imagem de 100 px. 1º: 90 px (+10 = 100) enche a altura: não sobra nada para o 2º.
        val divisao = dividirAoRedorDaImagem(100f, 10f, 5) { linhas(if (it == 0) 4 else 5, altura = 22.5f) }

        assertEquals(DivisaoAoRedorDaImagem(paragrafosInteiros = 1, corte = null), divisao)
    }

    @Test
    fun `sem parágrafos para consumir devolve o que coube`() {
        val divisao = dividirAoRedorDaImagem(500f, 10f, 2) { linhas(2) }

        assertEquals(DivisaoAoRedorDaImagem(paragrafosInteiros = 2, corte = null), divisao)
    }

    @Test
    fun `respeita o maximo de paragrafos que podem ser usados`() {
        val divisao = dividirAoRedorDaImagem(1000f, 10f, 2) { linhas(2) }

        assertEquals(2, divisao.paragrafosInteiros)
    }

    @Test
    fun `um paragrafo que cabe inteiro nao e cortado`() {
        val divisao = dividirAoRedorDaImagem(100f, 10f, 1) { linhas(5) } // 5 x 20 = 100: cabe exatamente

        assertEquals(1, divisao.paragrafosInteiros)
        assertNull(divisao.corte)
    }
}

class MontarBlocosTest {

    private fun montar(
        quantidade: Int,
        artefatos: Map<Int, List<Artefato>>,
        altura: Float = 100f,
        linhasDe: (Int) -> List<LinhaMedida> = { linhas(12) },
    ) = montarBlocos(quantidade, { artefatos[it].orEmpty() }, altura, 10f, linhasDe)

    @Test
    fun `sem imagem cada paragrafo e um bloco comum`() {
        val blocos = montar(3, emptyMap())

        assertEquals(List(3) { BlocoDoTexto.Comum(FatiaDeParagrafo(it)) }, blocos)
    }

    @Test
    fun `paisagem entra antes do paragrafo, em bloco comum`() {
        val paisagem = comImagem("PAISAGEM", 7)

        val blocos = montar(2, mapOf(1 to listOf(paisagem)))

        assertEquals(BlocoDoTexto.Comum(FatiaDeParagrafo(1), listOf(paisagem)), blocos[1])
    }

    @Test
    fun `retrato num paragrafo longo corta o paragrafo e manda o resto para baixo, na largura inteira`() {
        val retrato = comImagem("RETRATO", 5)

        val blocos = montar(3, mapOf(0 to listOf(retrato)))

        assertEquals(
            BlocoDoTexto.ComRetrato(retrato, listOf(FatiaDeParagrafo(0, 0, 50)), FatiaDeParagrafo(0, 50, null)),
            blocos[0],
        )
        // Os outros parágrafos seguem comuns, inteiros.
        assertEquals(listOf(BlocoDoTexto.Comum(FatiaDeParagrafo(1)), BlocoDoTexto.Comum(FatiaDeParagrafo(2))), blocos.drop(1))
    }

    @Test
    fun `retrato num paragrafo curto consome os seguintes ate encher a altura`() {
        val retrato = comImagem("RETRATO", 5)

        val blocos = montar(5, mapOf(0 to listOf(retrato)), altura = 200f) { linhas(3) }

        val primeiro = blocos[0] as BlocoDoTexto.ComRetrato
        assertEquals(listOf(0, 1, 2), primeiro.fatias.map { it.indice })
        assertNull(primeiro.resto)
        assertEquals(listOf(3, 4), blocos.drop(1).map { (it as BlocoDoTexto.Comum).fatia.indice })
    }

    @Test
    fun `o retrato nao consome um paragrafo que tem imagem propria`() {
        val retrato = comImagem("RETRATO", 5)
        val outra = comImagem("PAISAGEM", 6)

        val blocos = montar(4, mapOf(0 to listOf(retrato), 2 to listOf(outra)), altura = 500f) { linhas(2) }

        val primeiro = blocos[0] as BlocoDoTexto.ComRetrato
        assertEquals("só o 0 e o 1: o 2 tem imagem própria", listOf(0, 1), primeiro.fatias.map { it.indice })
        assertEquals(BlocoDoTexto.Comum(FatiaDeParagrafo(2), listOf(outra)), blocos[1])
    }

    @Test
    fun `dois retratos em sequencia com texto curto ao lado - o texto do segundo ocupa o vazio e o retrato dele desce`() {
        val a = comImagem("RETRATO", 5)
        val b = comImagem("RETRATO", 6)

        // Cada parágrafo tem 2 linhas (40 px) e o quadro tem 150 px: o parágrafo 0 sozinho deixaria um vazio.
        val blocos = montar(3, mapOf(0 to listOf(a), 1 to listOf(b)), altura = 150f) { linhas(2) }

        val bloco = blocos[0] as BlocoDoTexto.ComRetrato
        assertEquals(listOf(0, 1, 2), bloco.fatias.map { it.indice })
        assertEquals(listOf(b), bloco.retratosExtras)
        assertEquals(1, blocos.size)
    }

    @Test
    fun `o retrato do paragrafo seguinte so desce se o texto dele foi mesmo consumido`() {
        val a = comImagem("RETRATO", 5)
        val b = comImagem("RETRATO", 6)

        // O parágrafo 0 já enche o quadro: o 1 (com retrato) fica no seu próprio bloco.
        val blocos = montar(2, mapOf(0 to listOf(a), 1 to listOf(b)), altura = 100f) { linhas(5) }

        assertEquals(listOf(0), (blocos[0] as BlocoDoTexto.ComRetrato).fatias.map { it.indice })
        assertEquals(emptyList<Artefato>(), (blocos[0] as BlocoDoTexto.ComRetrato).retratosExtras)
        assertEquals(b, (blocos[1] as BlocoDoTexto.ComRetrato).retrato)
    }

    @Test
    fun `um segundo retrato no mesmo paragrafo vai como extra, e o resto da fatia nao se repete`() {
        val a = comImagem("RETRATO", 5)
        val b = comImagem("RETRATO", 6)

        val blocos = montar(1, mapOf(0 to listOf(a, b)))

        val bloco = blocos.single() as BlocoDoTexto.ComRetrato
        assertEquals(a, bloco.retrato)
        assertEquals(listOf(b), bloco.retratosExtras)
    }

    @Test
    fun `todo paragrafo aparece uma vez so, em ordem, sem perder nem repetir texto`() {
        val retrato = comImagem("RETRATO", 5)

        val blocos = montar(6, mapOf(1 to listOf(retrato)), altura = 150f) { linhas(4) }

        val fatias = blocos.flatMap {
            when (it) {
                is BlocoDoTexto.Comum -> listOf(it.fatia)
                is BlocoDoTexto.ComRetrato -> it.fatias + listOfNotNull(it.resto)
            }
        }
        // Cada índice aparece; um parágrafo cortado aparece em duas fatias que se emendam.
        assertEquals((0..5).toList(), fatias.map { it.indice }.distinct())
        val cortada = fatias.filter { it.ate != null || it.de > 0 }
        assertTrue(cortada.size % 2 == 0)
        if (cortada.isNotEmpty()) assertEquals(cortada[0].ate, cortada[1].de)
    }

    @Test
    fun `fatia recorta o texto sem espacos sobrando`() {
        assertEquals("um dois", FatiaDeParagrafo(0, 0, 8).recortar("um dois tres"))
        assertEquals("tres", FatiaDeParagrafo(0, 7, null).recortar("um dois tres"))
        assertEquals("tudo", FatiaDeParagrafo(0).recortar("  tudo "))
        assertEquals("", FatiaDeParagrafo(0, 50, null).recortar("curto"))
    }
}
