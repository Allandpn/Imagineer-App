package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.CenaSugerida
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O layout do painel de IA (item 7.5f): o que fica à vista, o que vai para o ⋮ e como os títulos se escrevem. */
class RegrasDoLayoutDoPainelTest {

    private val novo = elemento(id = 1, nome = "Jon") // sem casamento: ainda não confirmado
    private val casadoAutomatico = elemento(id = 2, nome = "Auri", elementoId = 3, automatico = true, estadoId = 9)
    private val casadoSemEstado = elemento(id = 3, nome = "Hospius", elementoId = 4)
    private val confirmado = elemento(id = 4, nome = "Foxen", elementoId = 5, estadoId = 10)

    // PT3 -----------------------------------------------------------------------------------------------------------

    @Test
    fun `PT3 o item de selecionar muda de texto enquanto o modo esta ligado`() {
        assertEquals("Selecionar vários", rotuloDeSelecionar(selecionando = false))
        assertEquals("Sair da seleção", rotuloDeSelecionar(selecionando = true))
    }

    @Test
    fun `PT3 o menu Mais do topo tem Reanalisar e Selecionar, e Confirmar todos fica fora`() {
        assertEquals(listOf("Reanalisar", "Selecionar vários"), ItemDoMenuDoTopo.entries.map { it.rotulo })
    }

    // PC2 -----------------------------------------------------------------------------------------------------------

    @Test
    fun `PC2 o menu do elemento tem posicionar e voltar a posicao automatica`() {
        assertEquals(
            listOf(ItemDoMenuDoCartao.POSICIONAR, ItemDoMenuDoCartao.VOLTAR_A_POSICAO_AUTOMATICA),
            itensDoMenuDoElemento(novo),
        )
    }

    @Test
    fun `PC2 o elemento descartado nao tem menu, porque nao tem posicao`() {
        assertTrue(itensDoMenuDoElemento(elemento(id = 9, descartada = true)).isEmpty())
    }

    @Test
    fun `PC2 o menu da cena sem frame nao oferece apagar`() {
        val itens = itensDoMenuDaCena(temFrame = false)

        assertEquals(
            listOf(ItemDoMenuDoCartao.POSICIONAR, ItemDoMenuDoCartao.VOLTAR_A_POSICAO_AUTOMATICA, ItemDoMenuDoCartao.EDITAR_A_CENA),
            itens,
        )
    }

    @Test
    fun `PC2 o menu da cena com frame termina em apagar, que e o unico perigoso`() {
        val itens = itensDoMenuDaCena(temFrame = true)

        assertEquals(ItemDoMenuDoCartao.APAGAR_A_CENA, itens.last())
        assertEquals(listOf(ItemDoMenuDoCartao.APAGAR_A_CENA), itens.filter { it.perigoso })
    }

    @Test
    fun `PC2 os textos do menu sao os rotulos de sempre`() {
        assertEquals("Posicionar no texto", ItemDoMenuDoCartao.POSICIONAR.rotulo)
        assertEquals("Voltar à posição automática", ItemDoMenuDoCartao.VOLTAR_A_POSICAO_AUTOMATICA.rotulo)
        assertEquals("Editar a cena", ItemDoMenuDoCartao.EDITAR_A_CENA.rotulo)
        assertEquals("Apagar a cena", ItemDoMenuDoCartao.APAGAR_A_CENA.rotulo)
    }

    // PC3 -----------------------------------------------------------------------------------------------------------

    @Test
    fun `PC3 o elemento novo mostra Criar elemento a vista`() {
        assertEquals(AcaoDoElemento.CRIAR, acaoPrincipalDoElemento(novo))
    }

    @Test
    fun `PC3 o casado automaticamente mostra Confirmar, e o sem estado mostra Registrar estado`() {
        assertEquals(AcaoDoElemento.CONFIRMAR, acaoPrincipalDoElemento(casadoAutomatico))
        assertEquals(AcaoDoElemento.REGISTRAR_ESTADO, acaoPrincipalDoElemento(casadoSemEstado))
    }

    @Test
    fun `PC3 elemento confirmado ou descartado nao ganha botao a vista`() {
        assertNull(acaoPrincipalDoElemento(confirmado))
        assertNull(acaoPrincipalDoElemento(elemento(id = 8, descartada = true)))
    }

    @Test
    fun `PC3 a acao a vista e sempre a primeira da lista da situacao, a mesma do cartao aberto`() {
        listOf(novo, casadoAutomatico, casadoSemEstado).forEach {
            assertEquals(acoesDoElemento(situacaoDoElemento(it)).first(), acaoPrincipalDoElemento(it))
        }
    }

    @Test
    fun `PC3 a cena pendente mostra Confirmar cena, e a confirmada ou descartada nao`() {
        assertEquals(AcaoDaCena.CONFIRMAR, acaoPrincipalDaCena(CenaSugerida(id = 1, titulo = "A partida")))
        assertNull(acaoPrincipalDaCena(CenaSugerida(id = 2, titulo = "A partida", frame_id = 70)))
        assertNull(acaoPrincipalDaCena(CenaSugerida(id = 3, titulo = "A partida", descartada = true)))
    }

    // PM1 -----------------------------------------------------------------------------------------------------------

    @Test
    fun `PM1 e PD5 os titulos das secoes de prompt dizem o tipo e quantos ha`() {
        assertEquals("Prompts de imagem", tituloDaSecaoDePrompts(0))
        assertEquals("Prompts de imagem (1)", tituloDaSecaoDePrompts(1))
        assertEquals("Prompts de imagem (3)", tituloDaSecaoDePrompts(3))
        assertEquals("Prompts de vídeo", tituloDaSecaoDePromptsDeVideo(0))
        assertEquals("Prompts de vídeo (2)", tituloDaSecaoDePromptsDeVideo(2))
    }

    @Test
    fun `PM1 as secoes do frame tem titulo`() {
        assertEquals("Imagens", TITULO_DA_SECAO_DE_IMAGENS)
    }
}
