package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.ParticipanteSugerido
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun participante(id: Int, nome: String, elementoId: Int? = null, automatico: Boolean = false) =
    ParticipanteSugerido(
        sugestao_elemento_id = id, tipo = "PERSONAGEM", nome = nome,
        elemento_id = elementoId, casamento_automatico = automatico,
    )

private fun cena(
    id: Int = 1,
    descartada: Boolean = false,
    frameId: Int? = null,
    vararg participantes: ParticipanteSugerido,
) = CenaSugerida(id = id, titulo = "A partida $id", descartada = descartada, frame_id = frameId, participantes = participantes.toList())

/** As regras puras da cena (10b, primeira fatia). */
class RegrasDaCenaTest {

    @Test
    fun `C4 pendente confirma ou descarta, confirmada nao decide nada, descartada restaura`() {
        assertEquals(listOf(AcaoDaCena.CONFIRMAR, AcaoDaCena.DESCARTAR), acoesDaCena(cena()))
        assertEquals(emptyList<AcaoDaCena>(), acoesDaCena(cena(frameId = 7)))
        assertEquals(listOf(AcaoDaCena.RESTAURAR), acoesDaCena(cena(descartada = true)))
    }

    @Test
    fun `C4 confirmar e a acao principal, a primeira da lista`() {
        assertEquals(AcaoDaCena.CONFIRMAR, acoesDaCena(cena()).first())
    }

    @Test
    fun `C9 a etiqueta diz a situacao da cena`() {
        assertEquals("Pendente", etiquetaDaCena(cena()))
        assertEquals("Confirmada", etiquetaDaCena(cena(frameId = 7)))
        assertEquals("Descartada", etiquetaDaCena(cena(descartada = true)))
    }

    @Test
    fun `C3 participante sem elemento pede revisao, automatico pede conferir, o resto esta confirmado`() {
        assertEquals(SituacaoDoParticipante("Sem elemento — revise", precisaRevisar = true), situacaoDoParticipante(participante(1, "Jon")))
        assertEquals(
            SituacaoDoParticipante("Casamento automático — confira", precisaRevisar = false),
            situacaoDoParticipante(participante(1, "Jon", elementoId = 3, automatico = true)),
        )
        assertEquals(
            SituacaoDoParticipante("Elemento confirmado", precisaRevisar = false),
            situacaoDoParticipante(participante(1, "Jon", elementoId = 3)),
        )
    }
}

/** C3 e C11: a situação do participante, vista pelo elemento dele. */
class SituacaoDoParticipanteTest {

    private val jon = participante(10, "Jon", elementoId = 3)

    @Test
    fun `C11 casamento automatico oferece Confirmar ali mesmo`() {
        val s = situacaoDoParticipante(jon, elemento(id = 10, elementoId = 3, automatico = true))

        assertEquals("Casamento automático — confira", s.texto)
        assertEquals(AcaoDoElemento.CONFIRMAR, s.acaoRapida)
        assertFalse(s.precisaRevisar)
    }

    @Test
    fun `C11 casado sem estado neste capitulo oferece Registrar estado`() {
        val s = situacaoDoParticipante(jon, elemento(id = 10, elementoId = 3)) // casado, revisado, sem estado

        assertEquals("Sem estado neste capítulo", s.texto)
        assertEquals(AcaoDoElemento.REGISTRAR_ESTADO, s.acaoRapida)
    }

    @Test
    fun `C11 um elemento novo pede revisar, porque criar ou vincular exige escolher`() {
        val s = situacaoDoParticipante(participante(10, "Jon"), elemento(id = 10))

        assertTrue(s.precisaRevisar)
        assertNull(s.acaoRapida)
    }

    @Test
    fun `C11 elemento confirmado nao tem o que fazer`() {
        val s = situacaoDoParticipante(jon, elemento(id = 10, elementoId = 3, estadoId = 9))

        assertEquals(SituacaoDoParticipante("Elemento confirmado", precisaRevisar = false), s)
    }
}

/** O modal da cena no ViewModel do painel: C1, C2 e C5 a C8. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModalDaCenaTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val jon = participante(10, "Jon", elementoId = 3)
    private val comCena = SugestoesDeCapitulo(
        gerado_em = "2026-10-01T10:00:00",
        elementos = listOf(elemento()),
        cenas = listOf(cena(1, participantes = arrayOf(jon))),
    )

    private fun vm(repositorio: SugestoesFalso) = PainelDeIaViewModel(5, repositorio, ElementosFalso())

    private suspend fun kotlinx.coroutines.test.TestScope.painelAberto(repositorio: SugestoesFalso): PainelDeIaViewModel {
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()
        return vm
    }

    // ---- C1 e C2 ----

    @Test
    fun `C1 e C12 tocar num icone do texto comeca uma pilha nova, no lugar da anterior`() = runTest {
        val vm = painelAberto(SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena)))

        vm.abrirModal(1)
        assertEquals(1, vm.estado.value.emModal)

        vm.abrirModalDeCena(1)
        assertEquals(1, vm.estado.value.emModalCena)
        assertNull(vm.estado.value.emModal)

        vm.abrirModal(1)
        assertEquals(1, vm.estado.value.emModal)
        assertNull(vm.estado.value.emModalCena)
    }

    @Test
    fun `C1 fechar o modal da cena so fecha o da cena`() = runTest {
        val vm = painelAberto(SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena)))
        vm.abrirModalDeCena(1)

        vm.fecharModalDaCena()

        assertNull(vm.estado.value.emModalCena)
    }

    @Test
    fun `C12 revisar um participante empilha o modal do elemento por cima do da cena`() = runTest {
        val vm = painelAberto(SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena)))
        vm.abrirModalDeCena(1)

        vm.revisarParticipante(sugestaoElementoId = 10)

        assertEquals(listOf(ModalAberto.DeCena(1), ModalAberto.DeElemento(10)), vm.estado.value.modais)
        assertEquals(1, vm.estado.value.emModalCena) // o de baixo continua lá
        assertEquals(10, vm.estado.value.emModal)
    }

    // ---- C5: confirmar ----

    @Test
    fun `C5 confirmar manda a cena ao servidor, rele, fecha o modal e avisa`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena))
        val vm = painelAberto(repositorio)
        val leiturasAntes = repositorio.leituras
        vm.abrirModalDeCena(1)

        vm.executarCena(AcaoDaCena.CONFIRMAR, comCena.cenas.single())
        advanceUntilIdle()

        assertEquals(listOf(1), repositorio.confirmacoesDeCena)
        assertTrue("relê as sugestões", repositorio.leituras > leiturasAntes)
        assertNull(vm.estado.value.emModalCena)
        assertEquals(MensagemDoElemento(AVISO_CENA_CONFIRMADA, ehErro = false), vm.estado.value.mensagensDeCena[1])
        assertTrue(vm.estado.value.cenasOcupadas.isEmpty())
    }

    @Test
    fun `C5 falta confirmar um elemento (422) mostra a mensagem do servidor e nao fecha o modal`() = runTest {
        val mensagem = "Confirme primeiro os elementos desta cena, antes de criar o frame a partir dela: ainda faltam Jon."
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena)).also {
            it.resultadoDaConfirmacaoDeCena = ResultadoDaChamada.Falha(mensagem, codigoHttp = 422)
        }
        val vm = painelAberto(repositorio)
        val leiturasAntes = repositorio.leituras
        vm.abrirModalDeCena(1)

        vm.executarCena(AcaoDaCena.CONFIRMAR, comCena.cenas.single())
        advanceUntilIdle()

        assertEquals(1, vm.estado.value.emModalCena) // continua aberto, para a pessoa agir
        assertEquals(MensagemDoElemento(mensagem, ehErro = true), vm.estado.value.mensagensDeCena[1])
        assertEquals(leiturasAntes, repositorio.leituras) // nada mudou: não relê
        assertTrue(vm.estado.value.cenasOcupadas.isEmpty()) // e dá para tentar de novo depois de revisar
    }

    @Test
    fun `C5 cena ja confirmada em outro aparelho (409) rele, fecha e avisa`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena)).also {
            it.resultadoDaConfirmacaoDeCena = ResultadoDaChamada.Falha("Esta cena já virou o frame 9.", codigoHttp = 409)
        }
        val vm = painelAberto(repositorio)
        val leiturasAntes = repositorio.leituras
        vm.abrirModalDeCena(1)

        vm.executarCena(AcaoDaCena.CONFIRMAR, comCena.cenas.single())
        advanceUntilIdle()

        assertTrue(repositorio.leituras > leiturasAntes)
        assertNull(vm.estado.value.emModalCena)
        assertEquals(MensagemDoElemento(AVISO_CENA_JA_CONFIRMADA, ehErro = false), vm.estado.value.mensagensDeCena[1])
    }

    // ---- C6: descartar e restaurar ----

    @Test
    fun `C6 descartar e restaurar sao imediatos, sem confirmacao, e releem`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena))
        val vm = painelAberto(repositorio)
        val leiturasAntes = repositorio.leituras

        vm.executarCena(AcaoDaCena.DESCARTAR, comCena.cenas.single())
        advanceUntilIdle()
        vm.executarCena(AcaoDaCena.RESTAURAR, comCena.cenas.single().copy(descartada = true))
        advanceUntilIdle()

        assertEquals(listOf(1 to true, 1 to false), repositorio.descartesDeCena)
        assertEquals(leiturasAntes + 2, repositorio.leituras)
    }

    @Test
    fun `C6 falha ao descartar mostra o erro e nao relê`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena)).also {
            it.resultadoDoDescarteDeCena = ResultadoDaChamada.Falha("sem conexão")
        }
        val vm = painelAberto(repositorio)
        val leiturasAntes = repositorio.leituras
        vm.abrirModalDeCena(1)

        vm.executarCena(AcaoDaCena.DESCARTAR, comCena.cenas.single())
        advanceUntilIdle()

        assertEquals(MensagemDoElemento("sem conexão", ehErro = true), vm.estado.value.mensagensDeCena[1])
        assertEquals(1, vm.estado.value.emModalCena)
        assertEquals(leiturasAntes, repositorio.leituras)
    }

    // ---- C7 e C8 ----

    @Test
    fun `C7 uma acao por cena de cada vez - a segunda e ignorada enquanto a primeira roda`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena)).also { it.travaDaConfirmacaoDeCena = trava }
        val vm = painelAberto(repositorio)

        vm.executarCena(AcaoDaCena.CONFIRMAR, comCena.cenas.single())
        runCurrent()
        assertTrue(1 in vm.estado.value.cenasOcupadas)
        vm.executarCena(AcaoDaCena.CONFIRMAR, comCena.cenas.single()) // toque repetido
        vm.executarCena(AcaoDaCena.DESCARTAR, comCena.cenas.single())
        trava.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(1), repositorio.confirmacoesDeCena) // uma só chamada
        assertTrue(repositorio.descartesDeCena.isEmpty())
    }

    @Test
    fun `C8 nenhuma acao da cena gasta IA`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena))
        val vm = painelAberto(repositorio)

        vm.executarCena(AcaoDaCena.CONFIRMAR, comCena.cenas.single())
        advanceUntilIdle()
        vm.executarCena(AcaoDaCena.DESCARTAR, comCena.cenas.single())
        advanceUntilIdle()

        assertTrue("o POST que gasta IA nunca foi chamado", repositorio.analises.isEmpty())
        assertFalse(vm.estado.value.analisando)
    }

    @Test
    fun `uma cena que sumiu da lista depois de reanalisar nao quebra o modal`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena))
        val vm = painelAberto(repositorio)
        vm.abrirModalDeCena(99) // id que não existe mais

        // O estado guarda o id; quem desenha mostra "não existe mais". Aqui basta não quebrar nem fechar sozinho.
        assertEquals(99, vm.estado.value.emModalCena)
    }

    // ---- C12: a pilha de modais ----

    private val comCasadoAutomatico = SugestoesDeCapitulo(
        gerado_em = "2026-10-01T10:00:00",
        elementos = listOf(elemento(id = 10, elementoId = 3, automatico = true)), // casado sozinho, ninguém revisou
        cenas = listOf(cena(1, participantes = arrayOf(participante(10, "Jon", elementoId = 3, automatico = true)))),
    )

    @Test
    fun `C12 fechar o modal de cima revela o de baixo`() = runTest {
        val vm = painelAberto(SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena)))
        vm.abrirModalDeCena(1)
        vm.revisarParticipante(10)

        vm.fecharModal() // o "voltar" ou arrastar o modal do elemento

        assertEquals(listOf<ModalAberto>(ModalAberto.DeCena(1)), vm.estado.value.modais)
        assertEquals(1, vm.estado.value.emModalCena)
        assertNull(vm.estado.value.emModal)
    }

    @Test
    fun `C12 decidir o elemento no modal de cima fecha so ele e a cena de baixo continua`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCasadoAutomatico))
        val vm = painelAberto(repositorio)
        vm.abrirModalDeCena(1)
        vm.revisarParticipante(10)

        vm.executar(AcaoDoElemento.CONFIRMAR, comCasadoAutomatico.elementos.single())
        advanceUntilIdle()

        assertEquals(listOf<ModalAberto>(ModalAberto.DeCena(1)), vm.estado.value.modais) // E44 fecha só o de cima
    }

    @Test
    fun `C12 revisar o mesmo participante duas vezes nao duplica o modal`() = runTest {
        val vm = painelAberto(SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena)))
        vm.abrirModalDeCena(1)

        vm.revisarParticipante(10)
        vm.revisarParticipante(10)

        assertEquals(listOf(ModalAberto.DeCena(1), ModalAberto.DeElemento(10)), vm.estado.value.modais)
    }

    @Test
    fun `ver a ficha fecha toda a pilha`() = runTest {
        val vm = painelAberto(SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena)))
        vm.abrirModalDeCena(1)
        vm.revisarParticipante(10)

        vm.fecharModalAoAbrirFicha()

        assertTrue(vm.estado.value.modais.isEmpty())
    }

    @Test
    fun `C11 confirmar o casamento de um participante dentro da cena nao fecha a cena`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCasadoAutomatico))
        val vm = painelAberto(repositorio)
        vm.abrirModalDeCena(1)

        // o botão "Confirmar" do participante usa a mesma ação do cartão do elemento
        vm.executar(AcaoDoElemento.CONFIRMAR, comCasadoAutomatico.elementos.single())
        advanceUntilIdle()

        assertEquals(1, vm.estado.value.emModalCena) // a cena continua aberta, agora com o participante em dia
        assertTrue(repositorio.analises.isEmpty()) // e nada gastou IA
    }
}
