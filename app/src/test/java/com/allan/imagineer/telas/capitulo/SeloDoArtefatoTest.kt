package com.allan.imagineer.telas.capitulo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.allan.imagineer.rede.Artefato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** O selo do artefato (item 7.5e, SE1 a SE5): a cor vem da situação, o glifo do tipo, e o tamanho acompanha a fonte. */
class SeloDoArtefatoTest {

    private fun elemento(situacao: String, tipo: String = "PERSONAGEM") =
        Artefato(tipo = "ELEMENTO", tipo_do_elemento = tipo, rotulo = "Jon", situacao = situacao)

    private fun cena(situacao: String) = Artefato(tipo = "CENA", rotulo = "O jantar", situacao = situacao)

    /** A luminância relativa de uma cor (a fórmula da WCAG), para conferir o contraste do glifo branco. */
    private fun luminancia(cor: Color): Double {
        fun canal(c: Float): Double = c.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * canal(cor.red) + 0.7152 * canal(cor.green) + 0.0722 * canal(cor.blue)
    }

    private fun contrasteComBranco(fundo: Color): Double = (luminancia(Color.White) + 0.05) / (luminancia(fundo) + 0.05)

    // SE2 ----------------------------------------------------------------------------------------------------------

    @Test
    fun `cada situacao tem a sua cor de fundo`() {
        assertEquals(CoresDoSelo.SUGERIDO, CoresDoSelo.da("SUGERIDO"))
        assertEquals(CoresDoSelo.CONFIRMADO, CoresDoSelo.da("CONFIRMADO"))
        assertEquals(CoresDoSelo.PROMPT_PRONTO, CoresDoSelo.da("PROMPT_PRONTO"))
        assertEquals(CoresDoSelo.ILUSTRADO, CoresDoSelo.da("ILUSTRADO"))
    }

    @Test
    fun `as quatro cores sao todas diferentes entre si`() {
        val cores = listOf(CoresDoSelo.SUGERIDO, CoresDoSelo.CONFIRMADO, CoresDoSelo.PROMPT_PRONTO, CoresDoSelo.ILUSTRADO)
        assertEquals(4, cores.toSet().size)
    }

    @Test
    fun `uma situacao que o app nao conhece cai no cinza do sugerido`() {
        assertEquals(CoresDoSelo.SUGERIDO, CoresDoSelo.da("ARQUIVADO"))
        assertEquals(CoresDoSelo.SUGERIDO, CoresDoSelo.da(""))
    }

    @Test
    fun `o glifo branco tem contraste de pelo menos 4,5 para 1 sobre cada fundo`() {
        assertEquals(Color.White, CoresDoSelo.GLIFO)
        listOf("SUGERIDO", "CONFIRMADO", "PROMPT_PRONTO", "ILUSTRADO").forEach { situacao ->
            val contraste = contrasteComBranco(CoresDoSelo.da(situacao))
            assertTrue("$situacao: contraste $contraste", contraste >= 4.5)
        }
    }

    // SE1 ----------------------------------------------------------------------------------------------------------

    @Test
    fun `o sugerido mantem o glifo vazado e os outros o glifo cheio`() {
        assertSame(Icons.Outlined.Person, iconeDoArtefato(elemento("SUGERIDO")))
        assertSame(Icons.Filled.Person, iconeDoArtefato(elemento("CONFIRMADO")))
        assertSame(Icons.Filled.Person, iconeDoArtefato(elemento("PROMPT_PRONTO")))
        assertSame(Icons.Filled.Person, iconeDoArtefato(elemento("ILUSTRADO")))
        assertSame(Icons.Outlined.Movie, iconeDoArtefato(cena("SUGERIDO")))
        assertSame(Icons.Filled.Movie, iconeDoArtefato(cena("ILUSTRADO")))
    }

    @Test
    fun `o tipo escolhe o glifo e nao a cor`() {
        assertNotEquals(iconeDoArtefato(elemento("CONFIRMADO", "PERSONAGEM")), iconeDoArtefato(elemento("CONFIRMADO", "AMBIENTE")))
        assertNotEquals(iconeDoArtefato(elemento("CONFIRMADO")), iconeDoArtefato(cena("CONFIRMADO")))
        // Um tipo novo no servidor cai no glifo genérico, sem quebrar.
        assertSame(iconeDoArtefato(elemento("CONFIRMADO", "TIPO_NOVO")), iconeDoArtefato(elemento("ILUSTRADO", "OUTRO_NOVO")))
    }

    // SE3 ----------------------------------------------------------------------------------------------------------

    @Test
    fun `o selo mede uma vez e meia o tamanho da fonte`() {
        assertEquals(30.sp, tamanhoDoSelo(20.sp))
        assertEquals(36.sp, tamanhoDoSelo(24.sp))
        assertEquals(45.sp, tamanhoDoSelo(30.sp))
    }

    @Test
    fun `o selo nunca fica menor que 24 sp`() {
        assertEquals(24.sp, tamanhoDoSelo(12.sp))
        assertEquals(24.sp, tamanhoDoSelo(16.sp)) // 1,5 x 16 = 24: justo no mínimo
        assertEquals(24.sp, tamanhoDoSelo(8.sp))
    }

    @Test
    fun `fonte sem tamanho em sp usa o minimo`() {
        assertEquals(24.sp, tamanhoDoSelo(TextUnit.Unspecified))
    }

    @Test
    fun `o espaco reservado no texto tem o tamanho do selo`() {
        val montado = textoComIcones("Jon chegou.", 2, tamanhoDoSelo(20.sp))
        assertEquals(2, montado.marcadores.size)
        montado.marcadores.forEach {
            assertEquals(30.sp, it.item.width)
            assertEquals(30.sp, it.item.height)
        }
        // O prefixo não muda com o tamanho: cada selo ocupa o caractere do ícone e um espaço.
        assertEquals(4, montado.prefixo)
    }

    @Test
    fun `sem tamanho dado o espaco reservado e o padrao de antes`() {
        val montado = textoComIcones("Jon chegou.", 1)
        assertEquals(TAMANHO_DO_ICONE_NA_LINHA, montado.marcadores.single().item.width)
    }
}
