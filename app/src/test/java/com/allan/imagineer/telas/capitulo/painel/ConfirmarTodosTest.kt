package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.ParticipanteSugerido
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

private fun cenaPendente(id: Int) = CenaSugerida(id = id, titulo = "Cena $id", participantes = listOf(ParticipanteSugerido(10, "PERSONAGEM", "Jon")))

private fun sugestoes(vararg elementos: com.allan.imagineer.rede.ElementoSugerido, cenas: List<CenaSugerida> = emptyList()) =
    SugestoesDeCapitulo(gerado_em = "2026-10-01T10:00:00", elementos = elementos.toList(), cenas = cenas)

/** As contas e os textos do "Confirmar todos" (regras puras). */
class RegrasDoConfirmarTodosTest {

    private val casadoAutomatico = elemento(id = 1, elementoId = 3, automatico = true)
    private val casadoSemEstado = elemento(id = 2, elementoId = 4)
    private val confirmado = elemento(id = 3, elementoId = 5, estadoId = 9)
    private val novo = elemento(id = 4)

    @Test
    fun `L2 conta o que o lote faz e o que deixa para a pessoa`() {
        val resumo = resumoParaConfirmarTodos(
            sugestoes(
                casadoAutomatico, casadoSemEstado, confirmado, novo, elemento(id = 5, descartada = true),
                cenas = listOf(cenaPendente(1), cenaPendente(2), cenaPendente(3).copy(frame_id = 70), cenaPendente(4).copy(descartada = true)),
            ),
        )

        assertEquals(ResumoDoLote(casamentos = 1, estados = 1, cenas = 2, novos = 1), resumo) // descartados e confirmados fora
    }

    @Test
    fun `L1 so elementos novos nao bastam para habilitar o lote`() {
        assertFalse(resumoParaConfirmarTodos(sugestoes(novo, novo.copy(id = 9))).temAlgoParaConfirmar)
        assertFalse(resumoParaConfirmarTodos(sugestoes(confirmado)).temAlgoParaConfirmar)
        assertTrue(resumoParaConfirmarTodos(sugestoes(casadoAutomatico)).temAlgoParaConfirmar)
        assertTrue(resumoParaConfirmarTodos(sugestoes(cenas = listOf(cenaPendente(1)))).temAlgoParaConfirmar)
    }

    @Test
    fun `L2 o dialogo diz o que vai acontecer e o que nao vai`() {
        val texto = descreverLoteParaConfirmar(ResumoDoLote(casamentos = 2, estados = 1, cenas = 3, novos = 2))

        assertTrue(texto, "2 elementos casados automaticamente" in texto)
        assertTrue(texto, "registrar o estado de 1 elemento que ainda não tem" in texto)
        assertTrue(texto, "tentar confirmar 3 cenas" in texto)
        assertTrue(texto, "Não gasta IA e não descarta nada." in texto)
        assertTrue(texto, "2 elementos novos ficam para você" in texto)
    }

    @Test
    fun `L2 o dialogo concorda no singular e so fala do que existe`() {
        val texto = descreverLoteParaConfirmar(ResumoDoLote(casamentos = 1, estados = 0, cenas = 0, novos = 0))

        assertTrue(texto, "1 elemento casado automaticamente" in texto)
        assertFalse(texto, "estado" in texto)
        assertFalse(texto, "cena" in texto)
        assertFalse(texto, "novo" in texto)
    }

    @Test
    fun `L5 o resultado resume o que foi feito e o que ficou`() {
        assertEquals(
            "Confirmado: 1 casamento, 2 estados, 3 cenas. 1 elemento novo espera sua decisão.",
            descreverResultadoDoLote(1, 2, 3, emptyList(), null, novosRestantes = 1),
        )
        assertEquals("Nada foi confirmado.", descreverResultadoDoLote(0, 0, 0, emptyList(), null, 0))
        assertEquals(
            "Confirmado: 1 cena. 1 item ficou pendente: Cena 2: Confirme primeiro os elementos.",
            descreverResultadoDoLote(0, 0, 1, listOf("Cena 2: Confirme primeiro os elementos."), null, 0),
        )
        assertTrue(
            "Parou no meio: sem conexão" in descreverResultadoDoLote(1, 0, 0, emptyList(), "sem conexão", 0),
        )
    }
}

/** O lote no ViewModel do painel: ordem, falhas e limites (L3 a L7). */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmarTodosNoPainelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val casado = elemento(id = 1, elementoId = 3, automatico = true)
    private val novo = elemento(id = 2)

    /** O que o servidor mostra a cada passo do lote: o casamento confirmado, o estado registrado, a cena confirmada. */
    private val inicio = sugestoes(casado, novo, cenas = listOf(cenaPendente(1)))
    private val aposCasamento = sugestoes(elemento(id = 1, elementoId = 3), novo, cenas = listOf(cenaPendente(1)))
    private val aposEstado = sugestoes(elemento(id = 1, elementoId = 3, estadoId = 9), novo, cenas = listOf(cenaPendente(1)))
    private val aposCena = sugestoes(elemento(id = 1, elementoId = 3, estadoId = 9), novo, cenas = listOf(cenaPendente(1).copy(frame_id = 70)))

    private suspend fun TestScope.painelCom(
        repositorio: SugestoesFalso,
        elementos: ElementosFalso = ElementosFalso(),
    ): PainelDeIaViewModel {
        val vm = PainelDeIaViewModel(5, repositorio, elementos)
        vm.aoAbrirPainel()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `L1 e L2 pedir abre o dialogo com a conta, e cancelar nao faz nada`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(inicio))
        val elementos = ElementosFalso()
        val vm = painelCom(repositorio, elementos)

        vm.pedirConfirmarTodos()
        assertEquals(ResumoDoLote(casamentos = 1, estados = 0, cenas = 1, novos = 1), vm.estado.value.confirmandoTodos)

        vm.cancelarConfirmarTodos()
        assertNull(vm.estado.value.confirmandoTodos)
        assertTrue(elementos.chamadas.none { it.startsWith("ajustar") || it.startsWith("registrar") })
        assertTrue(repositorio.confirmacoesDeCena.isEmpty())
    }

    @Test
    fun `L1 sem nada a confirmar o dialogo nem abre`() = runTest {
        val vm = painelCom(SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes(novo))))

        vm.pedirConfirmarTodos()

        assertNull(vm.estado.value.confirmandoTodos)
    }

    @Test
    fun `L3 roda em ordem - casamentos, depois estados, depois cenas - uma chamada por vez`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(inicio))
        val elementos = ElementosFalso()
        val vm = painelCom(repositorio, elementos)
        repositorio.proximasLeituras.addAll(listOf(aposCasamento, aposEstado, aposCena))
        vm.pedirConfirmarTodos()

        vm.confirmarTodos()
        advanceUntilIdle()

        assertEquals(listOf("ajustarCasamento(1,3)", "registrarEstado(3,1)"), elementos.chamadas.filter { it.startsWith("ajustar") || it.startsWith("registrar") })
        assertEquals(listOf(1), repositorio.confirmacoesDeCena)
        assertFalse(vm.estado.value.executandoLote)
        assertEquals(
            "Confirmado: 1 casamento, 1 estado, 1 cena. 1 elemento novo espera sua decisão.",
            vm.estado.value.resultadoDoLote,
        )
    }

    @Test
    fun `L6 nunca cria elemento novo, nunca descarta e nunca gasta IA`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(inicio))
        val elementos = ElementosFalso()
        val vm = painelCom(repositorio, elementos)
        repositorio.proximasLeituras.addAll(listOf(aposCasamento, aposEstado, aposCena))
        vm.pedirConfirmarTodos()

        vm.confirmarTodos()
        advanceUntilIdle()

        assertTrue(elementos.chamadas.none { it.startsWith("criar(") || it.startsWith("descartar") })
        assertTrue(repositorio.descartesDeCena.isEmpty())
        assertTrue("o POST que gasta IA nunca foi chamado", repositorio.analises.isEmpty())
    }

    @Test
    fun `L4 uma recusa do servidor deixa o item pendente e o lote continua`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(inicio)).also {
            it.resultadoDaConfirmacaoDeCena = ResultadoDaChamada.Falha("Confirme primeiro os elementos desta cena.", codigoHttp = 422)
        }
        val elementos = ElementosFalso()
        val vm = painelCom(repositorio, elementos)
        repositorio.proximasLeituras.addAll(listOf(aposCasamento, aposEstado, aposEstado))
        vm.pedirConfirmarTodos()

        vm.confirmarTodos()
        advanceUntilIdle()

        val resultado = vm.estado.value.resultadoDoLote
        assertNotNull(resultado)
        assertTrue(resultado, "Confirmado: 1 casamento, 1 estado." in resultado!!) // os dois primeiros passos deram certo
        assertTrue(resultado, "1 item ficou pendente: Cena 1: Confirme primeiro os elementos desta cena." in resultado)
    }

    @Test
    fun `L4 falha de conexao interrompe o lote - nao espera N tempos limites seguidos`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(inicio))
        val elementos = ElementosFalso().also { it.resposta = ResultadoDaChamada.Falha("Sem conexão com o servidor.") } // sem código HTTP
        val vm = painelCom(repositorio, elementos)
        vm.pedirConfirmarTodos()

        vm.confirmarTodos()
        advanceUntilIdle()

        assertEquals(listOf("ajustarCasamento(1,3)"), elementos.chamadas.filter { it.startsWith("ajustar") || it.startsWith("registrar") })
        assertTrue(repositorio.confirmacoesDeCena.isEmpty()) // nem chegou às cenas
        assertTrue(vm.estado.value.resultadoDoLote!!, "Parou no meio: Sem conexão com o servidor." in vm.estado.value.resultadoDoLote!!)
        assertFalse(vm.estado.value.executandoLote)
    }

    @Test
    fun `cena que ja estava confirmada em outro aparelho (409) conta como feita`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes(cenas = listOf(cenaPendente(1))))).also {
            it.resultadoDaConfirmacaoDeCena = ResultadoDaChamada.Falha("Esta cena já virou o frame 9.", codigoHttp = 409)
        }
        val vm = painelCom(repositorio)
        vm.pedirConfirmarTodos()

        vm.confirmarTodos()
        advanceUntilIdle()

        assertTrue(vm.estado.value.resultadoDoLote!!, "1 cena" in vm.estado.value.resultadoDoLote!!)
    }

    @Test
    fun `confirmar sem ter pedido, ou com um lote rodando, nao faz nada`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(inicio))
        val elementos = ElementosFalso()
        val vm = painelCom(repositorio, elementos)

        vm.confirmarTodos() // sem o diálogo aberto
        advanceUntilIdle()

        assertTrue(elementos.chamadas.none { it.startsWith("ajustar") })
        assertNull(vm.estado.value.resultadoDoLote)
    }

    @Test
    fun `L5 o resumo some quando a pessoa o dispensa`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes(cenas = listOf(cenaPendente(1)))))
        val vm = painelCom(repositorio)
        vm.pedirConfirmarTodos()
        vm.confirmarTodos()
        advanceUntilIdle()
        assertNotNull(vm.estado.value.resultadoDoLote)

        vm.dispensarResultadoDoLote()

        assertNull(vm.estado.value.resultadoDoLote)
    }
}
