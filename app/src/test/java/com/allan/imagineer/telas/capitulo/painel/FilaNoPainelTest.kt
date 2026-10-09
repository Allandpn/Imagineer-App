package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.ResultadoDaGeracao
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A geração em fila no ViewModel do painel (item 7.5e, GF1 a GF6): marcar, confirmar, rodar um por vez, falhar sem derrubar, parar. */
@OptIn(ExperimentalCoroutinesApi::class)
class FilaNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun vm(repositorio: SugestoesFalso, prompts: PromptsFalso) = PainelDeIaViewModel(
        5, repositorio, ElementosFalso(), prompts,
        ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
    )

    /** Cada frame gera um prompt de id `frame * 10`: assim as chamadas de imagem dizem a que frame pertencem. */
    private fun PromptsFalso.cadaFrameTemOPromptDeIdDezVezes() {
        aoGerarPrompt = { frame -> ResultadoDaChamada.Sucesso(PromptDeFrame(id = frame * 10, frame_id = frame, texto = "p$frame")) }
    }

    private fun item(frame: Int, rotulo: String = "Cena $frame", posicao: Int? = frame, situacao: String = "CONFIRMADO") = ItemDaFila(
        chave = chaveDoFluxoDoFrame(frame), rotulo = rotulo, posicao = posicao, frameId = frame, elemento = null, situacao = situacao,
    )

    private fun PainelDeIaViewModel.marcarEPedir(acao: AcaoDaFila, itens: List<ItemDaFila>, vararg frames: Int) {
        alternarSelecaoDaFila()
        frames.forEach { alternarMarcaDaFila(chaveDoFluxoDoFrame(it)) }
        pedirFila(acao, itens)
    }

    private val falhaComum = ResultadoDaChamada.Falha("o servidor da IA não respondeu", codigoHttp = 502)

    // GF2: marcar -------------------------------------------------------------------------------------------------

    @Test
    fun `GF2 o modo Selecionar liga e desliga, e desligar limpa as caixas`() = runTest {
        val vm = vm(SugestoesFalso(), PromptsFalso())

        vm.alternarSelecaoDaFila()
        assertTrue(vm.estado.value.fila.selecionando)
        vm.alternarMarcaDaFila("frame:70")
        assertEquals(setOf("frame:70"), vm.estado.value.fila.marcados)

        vm.alternarSelecaoDaFila()
        assertFalse(vm.estado.value.fila.selecionando)
        assertTrue(vm.estado.value.fila.marcados.isEmpty())
    }

    @Test
    fun `GF2 fora do modo Selecionar as caixas nao marcam`() = runTest {
        val vm = vm(SugestoesFalso(), PromptsFalso())

        vm.alternarMarcaDaFila("frame:70")

        assertTrue(vm.estado.value.fila.marcados.isEmpty())
    }

    @Test
    fun `GF2 tocar de novo desmarca, marcar os que faltam e limpar funcionam`() = runTest {
        val vm = vm(SugestoesFalso(), PromptsFalso())
        val itens = listOf(item(70), item(71, situacao = "ILUSTRADO"), item(72, situacao = "PROMPT_PRONTO"))
        vm.alternarSelecaoDaFila()

        vm.alternarMarcaDaFila("frame:70"); vm.alternarMarcaDaFila("frame:70")
        assertTrue(vm.estado.value.fila.marcados.isEmpty())

        vm.marcarOsQueFaltamNaFila(itens)
        assertEquals(setOf("frame:70", "frame:72"), vm.estado.value.fila.marcados)

        vm.limparMarcasDaFila()
        assertTrue(vm.estado.value.fila.marcados.isEmpty())
        assertTrue(vm.estado.value.fila.selecionando) // limpar não sai do modo
    }

    // GF3: a confirmação ------------------------------------------------------------------------------------------

    @Test
    fun `GF3 pedir abre a confirmacao sem gastar nada`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(SugestoesFalso(), prompts)

        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(item(70), item(71)), 70, 71)
        advanceUntilIdle()

        val pedido = vm.estado.value.fila.confirmando
        assertNotNull(pedido)
        assertEquals(listOf(70, 71), pedido!!.aFazer.map { it.frameId })
        assertTrue(prompts.geracoes.isEmpty() && prompts.geracoesDeImagem.isEmpty())
    }

    @Test
    fun `GF3 sem nada marcado nao abre a confirmacao, e cancelar fecha`() = runTest {
        val vm = vm(SugestoesFalso(), PromptsFalso())
        vm.alternarSelecaoDaFila()
        vm.pedirFila(AcaoDaFila.SO_PROMPTS, listOf(item(70)))
        assertNull(vm.estado.value.fila.confirmando)

        vm.alternarMarcaDaFila("frame:70")
        vm.pedirFila(AcaoDaFila.SO_PROMPTS, listOf(item(70)))
        assertNotNull(vm.estado.value.fila.confirmando)
        vm.cancelarPedidoDaFila()
        assertNull(vm.estado.value.fila.confirmando)
    }

    @Test
    fun `GF3 confirmar um pedido vazio nao inicia nada`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(SugestoesFalso(), prompts)
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(item(70, situacao = "ILUSTRADO")), 70)

        vm.confirmarFila()
        advanceUntilIdle()

        assertTrue(vm.estado.value.fila.resumo == null && !vm.estado.value.fila.rodando)
        assertTrue(prompts.geracoes.isEmpty() && prompts.geracoesDeImagem.isEmpty())
    }

    // GF1, GF4: a fila --------------------------------------------------------------------------------------------

    @Test
    fun `GF4 a fila roda em ordem do texto, um item por vez, com prompt e imagem de cada`() = runTest {
        val prompts = PromptsFalso().also { it.cadaFrameTemOPromptDeIdDezVezes() }
        val vm = vm(SugestoesFalso(), prompts)
        val itens = listOf(item(71, posicao = 500), item(70, posicao = 100), item(72, posicao = 300))
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, itens, 71, 70, 72)

        vm.confirmarFila()
        advanceUntilIdle()

        assertEquals(listOf(70, 72, 71), prompts.geracoes.map { it.first }) // em ordem do texto
        assertEquals(listOf(700, 720, 710), prompts.geracoesDeImagem.map { it.first })
        val resumo = vm.estado.value.fila.resumo!!
        assertEquals(3, resumo.gerados)
        assertEquals("3 itens: 3 gerados", descreverResumoDaFila(resumo))
        assertFalse(vm.estado.value.fila.rodando)
        assertTrue(vm.estado.value.etapasDeImagem.isEmpty())
        assertFalse(vm.estado.value.fila.selecionando) // a seleção acaba quando a fila começa
    }

    @Test
    fun `GF3 so os prompts nao gasta imagem`() = runTest {
        val prompts = PromptsFalso().also { it.cadaFrameTemOPromptDeIdDezVezes() }
        val vm = vm(SugestoesFalso(), prompts)
        vm.marcarEPedir(AcaoDaFila.SO_PROMPTS, listOf(item(70), item(71)), 70, 71)

        vm.confirmarFila()
        advanceUntilIdle()

        assertEquals(listOf(70, 71), prompts.geracoes.map { it.first })
        assertTrue("nenhuma imagem", prompts.geracoesDeImagem.isEmpty())
        assertEquals(2, vm.estado.value.fila.resumo!!.gerados)
    }

    @Test
    fun `GF3 pula quem ja tem imagem e conta o pulo no resumo`() = runTest {
        val prompts = PromptsFalso().also { it.cadaFrameTemOPromptDeIdDezVezes() }
        val vm = vm(SugestoesFalso(), prompts)
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(item(70, situacao = "ILUSTRADO"), item(71)), 70, 71)

        vm.confirmarFila()
        advanceUntilIdle()

        assertEquals(listOf(71), prompts.geracoes.map { it.first })
        val resumo = vm.estado.value.fila.resumo!!
        assertEquals("2 itens: 1 gerado, 1 pulado", descreverResumoDaFila(resumo))
    }

    @Test
    fun `GF1 um retrato sem frame e criado pela fila antes do prompt e da imagem`() = runTest {
        val jon = elemento(id = 1, nome = "Jon", elementoId = 3, estadoId = 9)
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(SugestoesDeCapitulo(gerado_em = "x", elementos = listOf(jon))))
        val prompts = PromptsFalso().also { it.cadaFrameTemOPromptDeIdDezVezes() }
        val vm = vm(repositorio, prompts)
        val retrato = ItemDaFila(chave = chaveDoFluxoDoRetrato(1), rotulo = "Retrato de Jon", posicao = 10, frameId = null, elemento = jon, situacao = "CONFIRMADO")
        vm.alternarSelecaoDaFila(); vm.alternarMarcaDaFila(retrato.chave)
        vm.pedirFila(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(retrato))

        vm.confirmarFila()
        advanceUntilIdle()

        assertEquals(listOf(9), repositorio.retratosPedidos) // o frame, com o estado vigente
        assertEquals(listOf(80), prompts.geracoes.map { it.first }) // o frame novo do fake tem o id 80
        assertEquals(listOf(800), prompts.geracoesDeImagem.map { it.first })
        assertEquals(1, vm.estado.value.fila.resumo!!.gerados)
        assertEquals(mapOf(1 to 80), vm.estado.value.retratosCriados)
    }

    // GF5: falhas -------------------------------------------------------------------------------------------------

    @Test
    fun `GF5 um item que falha nao derruba os outros e vai para o resumo com a mensagem`() = runTest {
        val prompts = PromptsFalso().also {
            it.aoGerarPrompt = { frame ->
                if (frame == 70) falhaComum else ResultadoDaChamada.Sucesso(PromptDeFrame(id = frame * 10, frame_id = frame, texto = "p"))
            }
        }
        val vm = vm(SugestoesFalso(), prompts)
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(item(70, "A partida"), item(71, "O poço")), 70, 71)

        vm.confirmarFila()
        advanceUntilIdle()

        assertEquals(listOf(710), prompts.geracoesDeImagem.map { it.first }) // o 71 seguiu
        val resumo = vm.estado.value.fila.resumo!!
        assertEquals("2 itens: 1 gerado, 1 falhou", descreverResumoDaFila(resumo))
        assertEquals("A partida", resumo.falhas.single().first.rotulo)
        assertEquals("o servidor da IA não respondeu", resumo.falhas.single().second)
        assertNull(resumo.motivoDaParada)
    }

    @Test
    fun `GF5 tres falhas seguidas param a fila e dizem quantos nem comecaram`() = runTest {
        val prompts = PromptsFalso().also { it.aoGerarPrompt = { falhaComum } }
        val vm = vm(SugestoesFalso(), prompts)
        val itens = (70..74).map { item(it) }
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, itens, 70, 71, 72, 73, 74)

        vm.confirmarFila()
        advanceUntilIdle()

        assertEquals(3, prompts.geracoes.size) // parou na terceira
        val resumo = vm.estado.value.fila.resumo!!
        assertEquals(3, resumo.falhas.size)
        assertEquals(2, resumo.naoComecaram.size)
        assertTrue("3 falhas seguidas" in resumo.motivoDaParada.orEmpty())
        assertEquals(5, resumo.paraRepetir.size) // as 3 que falharam e as 2 que nem começaram
    }

    @Test
    fun `GF5 um sucesso no meio zera a contagem das falhas seguidas`() = runTest {
        val prompts = PromptsFalso().also {
            it.aoGerarPrompt = { frame ->
                if (frame in setOf(70, 71, 73, 74)) falhaComum else ResultadoDaChamada.Sucesso(PromptDeFrame(id = frame * 10, frame_id = frame, texto = "p"))
            }
        }
        val vm = vm(SugestoesFalso(), prompts)
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, (70..74).map { item(it) }, 70, 71, 72, 73, 74)

        vm.confirmarFila()
        advanceUntilIdle()

        assertEquals(5, prompts.geracoes.size) // foi até o fim: 2 falhas, 1 sucesso, 2 falhas
        assertNull(vm.estado.value.fila.resumo!!.motivoDaParada)
    }

    @Test
    fun `GF5 sem conexao com o servidor a fila para na hora`() = runTest {
        val prompts = PromptsFalso().also { it.aoGerarPrompt = { ResultadoDaChamada.Falha("Sem conexão com o servidor.") } }
        val vm = vm(SugestoesFalso(), prompts)
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(item(70), item(71), item(72)), 70, 71, 72)

        vm.confirmarFila()
        advanceUntilIdle()

        assertEquals(1, prompts.geracoes.size)
        val resumo = vm.estado.value.fila.resumo!!
        assertEquals("Sem conexão com o servidor.", resumo.motivoDaParada)
        assertEquals(2, resumo.naoComecaram.size)
    }

    @Test
    fun `GF5 o provedor que recusa o prompt vira falha e nao deixa o dialogo da recusa aberto`() = runTest {
        val prompts = PromptsFalso().also {
            it.cadaFrameTemOPromptDeIdDezVezes()
            it.resultadoDaGeracaoDeImagem = ResultadoDaChamada.Sucesso(
                ResultadoDaGeracao("RECUSADA", false, PromptDeFrame(id = 700, frame_id = 70, texto = "p", motivo_da_recusa = "moderação"), null),
            )
        }
        val vm = vm(SugestoesFalso(), prompts)
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(item(70, "A partida")), 70)

        vm.confirmarFila()
        advanceUntilIdle()

        val resumo = vm.estado.value.fila.resumo!!
        assertEquals(1, resumo.falhas.size)
        assertTrue("recusou" in resumo.falhas.single().second)
        assertNull("nenhum diálogo no meio da fila", vm.estado.value.recusaDeImagem)
    }

    @Test
    fun `GF5 tentar de novo monta um pedido novo so com os que falharam e pede confirmacao`() = runTest {
        var funcionando = false
        val prompts = PromptsFalso().also {
            it.aoGerarPrompt = { frame ->
                if (frame == 70 && !funcionando) falhaComum else ResultadoDaChamada.Sucesso(PromptDeFrame(id = frame * 10, frame_id = frame, texto = "p"))
            }
        }
        val vm = vm(SugestoesFalso(), prompts)
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(item(70), item(71)), 70, 71)
        vm.confirmarFila(); advanceUntilIdle()
        assertEquals(1, vm.estado.value.fila.resumo!!.falhas.size)

        funcionando = true
        vm.tentarDeNovoNaFila()

        val pedido = vm.estado.value.fila.confirmando!!
        assertEquals(listOf(70), pedido.aFazer.map { it.frameId }) // só o que falhou
        assertNull("o resumo antigo sai", vm.estado.value.fila.resumo)

        vm.confirmarFila(); advanceUntilIdle()
        assertEquals("1 item: 1 gerado", descreverResumoDaFila(vm.estado.value.fila.resumo!!))
    }

    // GF4: parar --------------------------------------------------------------------------------------------------

    @Test
    fun `GF4 parar termina o item em andamento e nao comeca os outros`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also {
            it.cadaFrameTemOPromptDeIdDezVezes()
            it.travaDaGeracaoDeImagem = trava
        }
        val vm = vm(SugestoesFalso(), prompts)
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(item(70), item(71), item(72)), 70, 71, 72)
        vm.confirmarFila(); runCurrent()
        assertTrue(vm.estado.value.fila.rodando)
        assertEquals(0, vm.estado.value.fila.andamento!!.indice)

        vm.pararFila()
        assertTrue(vm.estado.value.fila.andamento!!.parando)
        trava.complete(Unit); advanceUntilIdle()

        assertEquals(listOf(70), prompts.geracoes.map { it.first }) // só o primeiro
        val resumo = vm.estado.value.fila.resumo!!
        assertTrue(resumo.parouAPedido)
        assertEquals(1, resumo.gerados)
        assertEquals(2, resumo.naoComecaram.size)
        assertFalse(vm.estado.value.fila.rodando)
    }

    @Test
    fun `GF4 enquanto roda, o andamento diz qual item e a etapa dele`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also {
            it.cadaFrameTemOPromptDeIdDezVezes()
            it.travaDaGeracaoDeImagem = trava
        }
        val vm = vm(SugestoesFalso(), prompts)
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(item(70, "A partida"), item(71)), 70, 71)
        vm.confirmarFila(); runCurrent()

        val andamento = vm.estado.value.fila.andamento!!
        assertEquals("A partida", andamento.itemAtual!!.rotulo)
        assertEquals("Gerando 1 de 2 — A partida", descreverProgressoDaFila(andamento.indice, andamento.pedido.aFazer.size, andamento.itemAtual!!.rotulo))
        assertEquals(EtapaDaImagem.GERANDO_A_IMAGEM, vm.estado.value.etapasDeImagem[chaveDoFluxoDoFrame(70)])
        trava.complete(Unit); advanceUntilIdle()
    }

    @Test
    fun `GF4 com a fila rodando nao se mexe na selecao nem se comeca outra`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also {
            it.cadaFrameTemOPromptDeIdDezVezes()
            it.travaDaGeracaoDeImagem = trava
        }
        val vm = vm(SugestoesFalso(), prompts)
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(item(70), item(71)), 70)
        vm.confirmarFila(); runCurrent()

        vm.alternarSelecaoDaFila()
        vm.alternarMarcaDaFila("frame:71")
        vm.pedirFila(AcaoDaFila.SO_PROMPTS, listOf(item(70), item(71)))
        vm.confirmarFila()

        assertFalse(vm.estado.value.fila.selecionando)
        assertTrue(vm.estado.value.fila.marcados.isEmpty())
        assertNull(vm.estado.value.fila.confirmando)
        trava.complete(Unit); advanceUntilIdle()
        assertEquals(1, prompts.geracoes.size) // só o primeiro pedido rodou
    }

    @Test
    fun `GF4 um item ja em andamento por outro toque e pulado, nao duplicado`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also {
            it.cadaFrameTemOPromptDeIdDezVezes()
            it.travaDaGeracaoDeImagem = trava
        }
        val vm = vm(SugestoesFalso(), prompts)
        vm.gerarImagemDoFrame(chaveDoFluxoDoFrame(70), 70, "A partida"); runCurrent() // a pessoa tocou no botão do cartão
        vm.marcarEPedir(AcaoDaFila.PROMPTS_E_IMAGENS, listOf(item(70)), 70)

        vm.confirmarFila(); runCurrent()
        trava.complete(Unit); advanceUntilIdle()

        assertEquals(1, prompts.geracoes.size) // só a do toque
        assertEquals("1 item: 0 gerados, 1 pulado", descreverResumoDaFila(vm.estado.value.fila.resumo!!))
    }

    @Test
    fun `fechar o resumo o tira da tela`() = runTest {
        val prompts = PromptsFalso().also { it.cadaFrameTemOPromptDeIdDezVezes() }
        val vm = vm(SugestoesFalso(), prompts)
        vm.marcarEPedir(AcaoDaFila.SO_PROMPTS, listOf(item(70)), 70)
        vm.confirmarFila(); advanceUntilIdle()
        assertNotNull(vm.estado.value.fila.resumo)

        vm.fecharResumoDaFila()

        assertNull(vm.estado.value.fila.resumo)
    }

    @Test
    fun `o fluxo de um toque de sempre continua devolvendo o mesmo resultado a tela`() = runTest {
        // A refatoração do fluxo (para a fila reaproveitá-lo) não pode mudar o botão principal do cartão.
        val prompts = PromptsFalso().also { it.cadaFrameTemOPromptDeIdDezVezes() }
        val vm = vm(SugestoesFalso(), prompts)

        vm.gerarImagemDoFrame(chaveDoFluxoDoFrame(70), 70, "A partida")
        advanceUntilIdle()

        assertEquals(listOf(70), prompts.geracoes.map { it.first })
        assertEquals(listOf(700), prompts.geracoesDeImagem.map { it.first })
        assertTrue(vm.estado.value.etapasDeImagem.isEmpty())
        assertNull(vm.estado.value.fila.resumo) // um toque sozinho não é uma fila
    }
}
