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
    fun `C1 e C2 abrir a cena fecha o modal do elemento e vice-versa`() = runTest {
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
    fun `C3 revisar um participante fecha o modal da cena e abre o do elemento dele`() = runTest {
        val vm = painelAberto(SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comCena)))
        vm.abrirModalDeCena(1)

        vm.revisarParticipante(sugestaoElementoId = 10)

        assertNull(vm.estado.value.emModalCena)
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
}
