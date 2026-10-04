package com.allan.imagineer.telas.livro

import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.LivroDetalhe
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

private fun capitulo(id: Int, tamanho: Int, pendentes: Int = 0, ignorado: Boolean = false) = CapituloResumo(
    id = id, ordem = id, titulo = "Cap $id", ignorado = ignorado, tamanho_do_texto = tamanho, sugestoes_pendentes = pendentes,
)

private fun livro(vararg capitulos: CapituloResumo, faltam: List<String> = emptyList()) = LivroDetalhe(
    id = 1, titulo = "A Guerra", nome_arquivo = "a.epub", data_importacao = "2026-10-03",
    total_de_capitulos = capitulos.size, capitulos_ignorados = capitulos.count { it.ignorado },
    metadados_pendentes = faltam, capitulos = capitulos.toList(),
)

/** A barra de ícones da tela do livro (LV2): os números dos selos. */
class BarraDoLivroTest {

    @Test
    fun `LV2 os caracteres contam so os capitulos ativos`() {
        val l = livro(capitulo(1, 1000), capitulo(2, 500), capitulo(3, 9000, ignorado = true))
        assertEquals(1500, totalDeCaracteres(l))
    }

    @Test
    fun `LV2 as pendencias somam campos que faltam e sugestoes dos capitulos ativos`() {
        val l = livro(capitulo(1, 10, pendentes = 3), capitulo(2, 10), capitulo(3, 10, pendentes = 9, ignorado = true), faltam = listOf("autor"))
        assertEquals(4, totalDePendencias(l))
        assertEquals(listOf(1), capitulosComPendencias(l).map { it.id })
    }

    @Test
    fun `LV2 livro sem pendencia tem zero`() {
        assertEquals(0, totalDePendencias(livro(capitulo(1, 10))))
    }

    @Test
    fun `LV2 a quantidade abreviada cabe no selo`() {
        val br = Locale("pt", "BR")
        assertEquals("850", abreviarQuantidade(850, br))
        assertEquals("3,4 mil", abreviarQuantidade(3400, br))
        assertEquals("5 mil", abreviarQuantidade(5000, br))
        assertEquals("112 mil", abreviarQuantidade(112_000, br))
        assertEquals("1,2 mi", abreviarQuantidade(1_200_000, br))
    }
}
