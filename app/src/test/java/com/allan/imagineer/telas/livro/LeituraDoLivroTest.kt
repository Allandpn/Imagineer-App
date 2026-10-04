package com.allan.imagineer.telas.livro

import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.Marcador
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun capLeitura(id: Int, lido: Boolean = false, ignorado: Boolean = false) =
    CapituloResumo(id = id, ordem = id, titulo = "C$id", ignorado = ignorado, tamanho_do_texto = 100, lido = lido)

private fun livroDe(vararg capitulos: CapituloResumo) = LivroDetalhe(
    id = 1, titulo = "A", nome_arquivo = "a.epub", data_importacao = "x",
    total_de_capitulos = capitulos.size, capitulos_ignorados = capitulos.count { it.ignorado }, capitulos = capitulos.toList(),
)

/** Continuar lendo e o progresso (LE3, LE6). */
class LeituraDoLivroTest {

    @Test
    fun `LE3 com marcador continua no capitulo e na posicao dele`() {
        val alvo = continuarLendo(livroDe(capLeitura(1), capLeitura(2), capLeitura(3)), Marcador(capitulo_id = 2, posicao_no_texto = 480))

        assertEquals(ContinuarLendo(2, 480, "Continuar lendo · Cap. 2"), alvo)
    }

    @Test
    fun `LE3 sem marcador comeca pelo primeiro capitulo nao lido`() {
        assertEquals(ContinuarLendo(2, null, "Começar a ler"), continuarLendo(livroDe(capLeitura(1, lido = true), capLeitura(2), capLeitura(3)), null))
    }

    @Test
    fun `LE3 com tudo lido e sem marcador volta ao primeiro`() {
        assertEquals(1, continuarLendo(livroDe(capLeitura(1, lido = true), capLeitura(2, lido = true)), null)?.capituloId)
    }

    @Test
    fun `LE3 se o capitulo do marcador foi arquivado cai no comeco, e livro sem capitulo ativo nao tem botao`() {
        val arquivado = livroDe(capLeitura(1), capLeitura(2, ignorado = true))
        assertEquals(ContinuarLendo(1, null, "Começar a ler"), continuarLendo(arquivado, Marcador(2, 10)))
        assertNull(continuarLendo(livroDe(capLeitura(1, ignorado = true)), null))
    }

    @Test
    fun `LE6 o progresso e lidos sobre ativos, e some sem leitura`() {
        fun resumo(lidos: Int, total: Int = 10, ignorados: Int = 0) = LivroResumo(
            id = 1, titulo = "A", nome_arquivo = "a", data_importacao = "x",
            total_de_capitulos = total, capitulos_ignorados = ignorados, capitulos_lidos = lidos,
        )
        assertNull(progressoDoLivro(resumo(0)))
        assertEquals(0.25f, progressoDoLivro(resumo(2, total = 10, ignorados = 2))!!, 0.0001f) // 2 de 8 ativos
        assertEquals(1f, progressoDoLivro(resumo(12, total = 10))!!, 0f) // nunca passa de 100%
    }

    @Test
    fun `LE6 o texto do progresso`() {
        assertEquals("3 de 12 capítulos lidos", descreverProgresso(3, 12))
        assertEquals("0 de 1 capítulo lido", descreverProgresso(0, 1))
    }
}
