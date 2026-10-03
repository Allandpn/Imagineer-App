package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.capitulo.BlocoDoTexto
import com.allan.imagineer.telas.capitulo.FatiaDeParagrafo
import com.allan.imagineer.rede.Artefato
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** As regras puras de posicionar à mão (PM1, PM4). */
class RegrasDePosicionarTest {

    @Test
    fun `PM1 o aviso diz qual artefato e o que tocar`() {
        assertEquals("Toque no parágrafo onde «Foxen» deve ficar.", avisoDePosicionar("Foxen"))
    }

    @Test
    fun `PM4 o inicio do bloco e o primeiro paragrafo dele`() {
        val retrato = Artefato(tipo = "ELEMENTO", rotulo = "A", situacao = "ILUSTRADO")
        assertEquals(4, indiceInicialDoBloco(BlocoDoTexto.Comum(FatiaDeParagrafo(4))))
        assertEquals(7, indiceInicialDoBloco(BlocoDoTexto.ComRetrato(retrato, listOf(FatiaDeParagrafo(7), FatiaDeParagrafo(8)), null)))
        assertNull(indiceInicialDoBloco(BlocoDoTexto.ComRetrato(retrato, emptyList(), null)))
    }
}

/** O modo "toque no parágrafo" no ViewModel do painel (PM1 a PM4). */
@OptIn(ExperimentalCoroutinesApi::class)
class PosicionarAMaoNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun vm(repositorio: SugestoesFalso = SugestoesFalso()) = PainelDeIaViewModel(5, repositorio, ElementosFalso())

    @Test
    fun `PM1 iniciar liga o modo e fecha os modais para o texto ficar a vista`() = runTest {
        val vm = vm()
        vm.abrirModal(3)
        assertEquals(3, vm.estado.value.emModal)

        vm.iniciarPosicionamento(ehCena = false, sugestaoId = 3, rotulo = "Foxen")

        assertEquals(PosicionandoArtefato(false, 3, "Foxen"), vm.estado.value.posicionando)
        assertNull(vm.estado.value.emModal)
    }

    @Test
    fun `PM1 tocar no paragrafo grava a posicao, desliga o modo e manda reler os artefatos`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.iniciarPosicionamento(ehCena = true, sugestaoId = 8, rotulo = "A travessia")
        val versaoAntes = vm.estado.value.versaoDosFrames

        vm.escolherParagrafo(120); advanceUntilIdle()

        assertEquals(listOf(Triple(true, 8, 120 as Int?)), repositorio.posicoesPedidas)
        assertNull(vm.estado.value.posicionando)
        assertEquals(versaoAntes + 1, vm.estado.value.versaoDosFrames)
    }

    @Test
    fun `PM4 a recusa do servidor fica no aviso e o modo continua ligado`() = runTest {
        val repositorio = SugestoesFalso().also { it.resultadoDePosicionar = ResultadoDaChamada.Falha("A posição 9 passa do fim do capítulo.") }
        val vm = vm(repositorio)
        vm.iniciarPosicionamento(ehCena = false, sugestaoId = 3, rotulo = "Foxen")

        vm.escolherParagrafo(9); advanceUntilIdle()

        val modo = vm.estado.value.posicionando
        assertNotNull(modo)
        assertEquals("A posição 9 passa do fim do capítulo.", modo!!.erro)
    }

    @Test
    fun `PM1 cancelar desliga o modo sem gravar nada`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.iniciarPosicionamento(ehCena = false, sugestaoId = 3, rotulo = "Foxen")

        vm.cancelarPosicionamento()

        assertNull(vm.estado.value.posicionando)
        assertEquals(emptyList<Triple<Boolean, Int, Int?>>(), repositorio.posicoesPedidas)
    }

    @Test
    fun `PM1 sem o modo ligado tocar num paragrafo nao faz nada`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)

        vm.escolherParagrafo(10); advanceUntilIdle()

        assertEquals(emptyList<Triple<Boolean, Int, Int?>>(), repositorio.posicoesPedidas)
    }
}
