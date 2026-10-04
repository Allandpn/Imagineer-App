package com.allan.imagineer.telas.livro

import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.LivroDetalhe
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
}
