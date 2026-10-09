package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** As regras puras da geração em fila (item 7.5e, GF2 a GF5): quem entra, a ordem, o que se pula, quando para e o resumo. */
class RegrasDaFilaTest {

    private fun item(
        frame: Int?,
        rotulo: String = "Item $frame",
        posicao: Int? = null,
        situacao: String = "CONFIRMADO",
        motivo: String? = null,
    ) = ItemDaFila(
        chave = chaveDoFluxoDoFrame(frame ?: 0), rotulo = rotulo, posicao = posicao, frameId = frame,
        elemento = null, situacao = situacao, motivoDeNaoEntrar = motivo,
    )

    private fun cena(id: Int, titulo: String = "Cena $id", frameId: Int? = null, descartada: Boolean = false) =
        CenaSugerida(id = id, titulo = titulo, frame_id = frameId, descartada = descartada)

    private fun artefatoDeElemento(sugestaoId: Int, posicao: Int?, situacao: String, frameId: Int? = null) =
        Artefato(tipo = "ELEMENTO", tipo_do_elemento = "PERSONAGEM", sugestao_id = sugestaoId, frame_id = frameId, rotulo = "Jon", posicao_no_texto = posicao, situacao = situacao)

    private fun artefatoDeCena(sugestaoId: Int?, frameId: Int?, posicao: Int?, situacao: String, rotulo: String = "Cena") =
        Artefato(tipo = "CENA", sugestao_id = sugestaoId, frame_id = frameId, rotulo = rotulo, posicao_no_texto = posicao, situacao = situacao)

    // GF2: quem pode entrar ---------------------------------------------------------------------------------------

    @Test
    fun `GF2 o elemento confirmado entra e o ainda nao confirmado fica de fora com o motivo`() {
        val confirmado = elemento(id = 1, nome = "Jon", elementoId = 3, estadoId = 9)
        val novo = elemento(id = 2, nome = "Ygritte")
        val sugestoes = SugestoesDeCapitulo(gerado_em = "x", elementos = listOf(confirmado, novo))

        val itens = itensDaFila(sugestoes, emptyList(), emptyMap())

        assertTrue(itens[0].podeEntrar)
        assertEquals("retrato:1", itens[0].chave)
        assertFalse(itens[1].podeEntrar)
        assertEquals(MOTIVO_ELEMENTO_NAO_CONFIRMADO, itens[1].motivoDeNaoEntrar)
    }

    @Test
    fun `GF2 o elemento ou a cena descartada nem aparece`() {
        val sugestoes = SugestoesDeCapitulo(
            gerado_em = "x",
            elementos = listOf(elemento(id = 1, elementoId = 3, estadoId = 9, descartada = true)),
            cenas = listOf(cena(1, frameId = 70, descartada = true)),
        )

        assertTrue(itensDaFila(sugestoes, emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun `GF2 a cena com frame entra e a sem frame fica de fora`() {
        val sugestoes = SugestoesDeCapitulo(gerado_em = "x", cenas = listOf(cena(1, "A partida", frameId = 70), cena(2, "O poço")))

        val itens = itensDaFila(sugestoes, emptyList(), emptyMap())

        assertEquals("frame:70", itens[0].chave)
        assertTrue(itens[0].podeEntrar)
        assertEquals("cena:2", itens[1].chave)
        assertEquals(MOTIVO_CENA_NAO_CONFIRMADA, itens[1].motivoDeNaoEntrar)
        assertNull(itens[1].frameId)
    }

    @Test
    fun `GF2 a cena criada de um trecho entra, com a posicao e a situacao do artefato`() {
        val artefatos = listOf(artefatoDeCena(sugestaoId = null, frameId = 88, posicao = 300, situacao = "PROMPT_PRONTO", rotulo = "O banquete"))

        val itens = itensDaFila(SugestoesDeCapitulo(gerado_em = "x"), artefatos, emptyMap())

        assertEquals(1, itens.size)
        assertEquals("frame:88", itens[0].chave)
        assertEquals("O banquete", itens[0].rotulo)
        assertEquals(300, itens[0].posicao)
        assertEquals("PROMPT_PRONTO", itens[0].situacao)
        assertTrue(itens[0].podeEntrar)
    }

    @Test
    fun `GF2 a posicao e a situacao vem do artefato e o frame do retrato tambem`() {
        val jon = elemento(id = 1, nome = "Jon", elementoId = 3, estadoId = 9)
        val sugestoes = SugestoesDeCapitulo(gerado_em = "x", elementos = listOf(jon))
        val artefatos = listOf(artefatoDeElemento(1, posicao = 120, situacao = "PROMPT_PRONTO", frameId = 80))

        val item = itensDaFila(sugestoes, artefatos, emptyMap()).single()

        assertEquals(120, item.posicao)
        assertEquals("PROMPT_PRONTO", item.situacao)
        assertEquals(80, item.frameId)
        assertEquals(jon, item.elemento)
    }

    @Test
    fun `GF2 um retrato criado nesta sessao ja tem frame mesmo antes de reler os artefatos`() {
        val sugestoes = SugestoesDeCapitulo(gerado_em = "x", elementos = listOf(elemento(id = 1, elementoId = 3, estadoId = 9)))

        val item = itensDaFila(sugestoes, emptyList(), mapOf(1 to 81)).single()

        assertEquals(81, item.frameId)
    }

    // GF3: o que se pula ------------------------------------------------------------------------------------------

    @Test
    fun `GF3 so os prompts pula quem ja tem prompt ou imagem`() {
        assertTrue(precisaDaAcao(item(1, situacao = "SUGERIDO"), AcaoDaFila.SO_PROMPTS))
        assertTrue(precisaDaAcao(item(1, situacao = "CONFIRMADO"), AcaoDaFila.SO_PROMPTS))
        assertFalse(precisaDaAcao(item(1, situacao = "PROMPT_PRONTO"), AcaoDaFila.SO_PROMPTS))
        assertFalse(precisaDaAcao(item(1, situacao = "ILUSTRADO"), AcaoDaFila.SO_PROMPTS))
    }

    @Test
    fun `GF3 prompts e imagens so pula quem ja tem imagem`() {
        assertTrue(precisaDaAcao(item(1, situacao = "CONFIRMADO"), AcaoDaFila.PROMPTS_E_IMAGENS))
        assertTrue(precisaDaAcao(item(1, situacao = "PROMPT_PRONTO"), AcaoDaFila.PROMPTS_E_IMAGENS))
        assertFalse(precisaDaAcao(item(1, situacao = "ILUSTRADO"), AcaoDaFila.PROMPTS_E_IMAGENS))
    }

    @Test
    fun `GF2 marcar os que faltam pega quem pode entrar e ainda nao tem imagem`() {
        val itens = listOf(
            item(1, situacao = "CONFIRMADO"),
            item(2, situacao = "PROMPT_PRONTO"),
            item(3, situacao = "ILUSTRADO"),
            item(4, situacao = "SUGERIDO", motivo = "Confirme a cena antes."),
        )

        assertEquals(setOf("frame:1", "frame:2"), chavesQueFaltam(itens))
    }

    // GF4: a ordem ------------------------------------------------------------------------------------------------

    @Test
    fun `GF4 a ordem e a do texto e quem nao tem posicao vem depois, na ordem em que estava`() {
        val itens = listOf(item(1, posicao = null), item(2, posicao = 500), item(3, posicao = 40), item(4, posicao = null), item(5, posicao = 40))

        assertEquals(listOf(3, 5, 2, 1, 4), emOrdemDoTexto(itens).map { it.frameId })
    }

    @Test
    fun `GF3 o pedido leva so os marcados que podem entrar, em ordem, e separa os que serao pulados`() {
        val itens = listOf(
            item(1, posicao = 300, situacao = "CONFIRMADO"),
            item(2, posicao = 100, situacao = "ILUSTRADO"),
            item(3, posicao = 200, situacao = "PROMPT_PRONTO"),
            item(4, posicao = 50, motivo = "Confirme a cena antes."),
            item(5, posicao = 10, situacao = "CONFIRMADO"), // não marcado
        )
        val marcados = setOf("frame:1", "frame:2", "frame:3", "frame:4")

        val pedido = prepararPedido(AcaoDaFila.PROMPTS_E_IMAGENS, itens, marcados)

        assertEquals(listOf(3, 1), pedido.aFazer.map { it.frameId }) // 3 (pos 200) antes de 1 (pos 300)
        assertEquals(listOf(2), pedido.pulados.map { it.frameId })
        assertFalse(pedido.vazio)
    }

    @Test
    fun `GF3 se tudo o que foi marcado ja esta pronto, o pedido fica vazio`() {
        val itens = listOf(item(1, situacao = "ILUSTRADO"))

        val pedido = prepararPedido(AcaoDaFila.PROMPTS_E_IMAGENS, itens, setOf("frame:1"))

        assertTrue(pedido.vazio)
        assertEquals(1, pedido.pulados.size)
    }

    @Test
    fun `GF3 a pergunta diz quantos itens, o custo e quantos serao pulados`() {
        val itens = listOf(item(1), item(2), item(3, situacao = "ILUSTRADO"))

        val completa = perguntaDaFila(prepararPedido(AcaoDaFila.PROMPTS_E_IMAGENS, itens, setOf("frame:1", "frame:2", "frame:3")))
        val soPrompts = perguntaDaFila(prepararPedido(AcaoDaFila.SO_PROMPTS, itens, setOf("frame:1")))

        assertTrue("2 itens" in completa)
        assertTrue("Cada imagem gasta crédito" in completa)
        assertTrue("1 marcado já está pronto e será pulado" in completa)
        assertTrue("1 item" in soPrompts && "2 itens" !in soPrompts)
        assertTrue("Não gasta imagem" in soPrompts)
    }

    // GF5: falhas e o fim -----------------------------------------------------------------------------------------

    @Test
    fun `GF5 sem resposta do servidor ou 401 a falha vale para todos, as outras nao`() {
        assertTrue(ehFalhaGeral(ResultadoDaChamada.Falha("sem conexão", codigoHttp = null)))
        assertTrue(ehFalhaGeral(ResultadoDaChamada.Falha("não reconheceu", codigoHttp = 401)))
        assertFalse(ehFalhaGeral(ResultadoDaChamada.Falha("fora do ar", codigoHttp = 502)))
        assertFalse(ehFalhaGeral(ResultadoDaChamada.Falha("sem modelo", codigoHttp = 422)))
        assertEquals(3, FALHAS_SEGUIDAS_PARA_PARAR)
    }

    @Test
    fun `GF5 o resumo conta gerados, pulados, falhas e os que nao comecaram`() {
        val a = item(1, "A"); val b = item(2, "B"); val c = item(3, "C"); val d = item(4, "D")
        val pulado = item(5, "E", situacao = "ILUSTRADO")
        val pedido = PedidoDaFila(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(a, b, c, d), listOf(pulado))
        val desfechos = mapOf(
            a.chave to DesfechoDoItem.Gerado,
            b.chave to DesfechoDoItem.Falhou("fora do ar"),
            c.chave to DesfechoDoItem.Pulado("já estava em andamento"),
        )

        val resumo = resumirFila(pedido, desfechos, motivoDaParada = null, parouAPedido = true)

        assertEquals(5, resumo.total)
        assertEquals(1, resumo.gerados)
        assertEquals(2, resumo.pulados) // o pulado de antes e o que já estava em andamento
        assertEquals(listOf(b to "fora do ar"), resumo.falhas)
        assertEquals(listOf(d), resumo.naoComecaram)
        assertEquals(listOf(b, d), resumo.paraRepetir) // o que falhou e o que nem começou
        assertEquals("5 itens: 1 gerado, 2 pulados, 1 falhou, 1 não começou", descreverResumoDaFila(resumo))
    }

    @Test
    fun `GF5 o resumo de uma fila perfeita diz so os gerados`() {
        val a = item(1); val b = item(2)
        val pedido = PedidoDaFila(AcaoDaFila.SO_PROMPTS, listOf(a, b), emptyList())
        val resumo = resumirFila(pedido, mapOf(a.chave to DesfechoDoItem.Gerado, b.chave to DesfechoDoItem.Gerado), null, false)

        assertEquals("2 itens: 2 gerados", descreverResumoDaFila(resumo))
        assertTrue(resumo.paraRepetir.isEmpty())
    }

    @Test
    fun `GF4 a linha de progresso conta a partir de um`() {
        assertEquals("Gerando 1 de 8 — Auri", descreverProgressoDaFila(0, 8, "Auri"))
        assertEquals("Gerando 3 de 8 — Retrato de Jon", descreverProgressoDaFila(2, 8, "Retrato de Jon"))
    }
}
