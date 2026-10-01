package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.EventoDeAnalise
import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Defeito D1: quem sai do capítulo no meio de uma análise não a perde, e quem volta a reencontra.
 * O ViewModel do painel apenas **espera** o resultado; quem roda a análise é o serviço do app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnaliseSobreviveATelaTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    @Test
    fun `um painel novo reencontra a analise em andamento e recebe o resultado quando termina`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(analisado)).also { it.travaDaAnalise = trava }
        val servico = ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador))

        // A primeira tela pede a reanálise...
        val primeiro = PainelDeIaViewModel(5, repositorio, ElementosFalso(), servico)
        primeiro.aoAbrirPainel(); advanceUntilIdle()
        primeiro.pedirReanalise()
        primeiro.confirmarReanalise()
        runCurrent()
        assertTrue(primeiro.estado.value.analisando)

        // ...o usuário sai e volta: um painel NOVO (o ViewModel antigo morreu com a tela).
        val segundo = PainelDeIaViewModel(5, repositorio, ElementosFalso(), servico)
        runCurrent()
        assertTrue("o painel novo deve mostrar a análise em andamento", segundo.estado.value.analisando)

        trava.complete(Unit)
        advanceUntilIdle()

        assertFalse(segundo.estado.value.analisando)
        assertTrue(segundo.estado.value.conteudo is ConteudoDoPainel.Pronto)
        assertEquals(1, repositorio.analises.size) // uma só chamada ao POST: voltar à tela não cobra de novo
    }

    @Test
    fun `um painel sem analise em andamento comeca normal`() = runTest {
        val repositorio = SugestoesFalso()
        val servico = ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador))

        val vm = PainelDeIaViewModel(5, repositorio, ElementosFalso(), servico)
        advanceUntilIdle()

        assertEquals(EstadoDoPainel(), vm.estado.value)
    }

    @Test
    fun `pedir a analise pelo painel passa pelo servico e gera o evento do aviso, com livro e capitulo`() = runTest {
        val repositorio = SugestoesFalso()
        val servico = ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador))
        val eventos = mutableListOf<EventoDeAnalise>()
        CoroutineScope(SupervisorJob() + agendador).launch { servico.eventos.collect { eventos += it } }
        runCurrent()

        val vm = PainelDeIaViewModel(5, repositorio, ElementosFalso(), servico)
        vm.definirLivro(2)
        vm.definirRotuloDoCapitulo(ordem = 3, titulo = "O muro")
        vm.aoAbrirPainel(); advanceUntilIdle()
        vm.analisar()
        advanceUntilIdle()

        assertEquals(listOf(EventoDeAnalise(5, 2, "capítulo «O muro»", sucesso = true)), eventos)
        assertEquals(2, servico.livroDoCapitulo(5))
    }
}
