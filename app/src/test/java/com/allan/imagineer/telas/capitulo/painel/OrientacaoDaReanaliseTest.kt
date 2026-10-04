package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** O que mandar ao servidor na reanálise com a orientação do usuário (item 6.7, M1). Regra pura. */
class OrientacaoAEnviarTest {

    @Test
    fun `campo nao mostrado ou texto igual ao que ja vale nao manda nada de novo`() {
        assertNull(orientacaoAEnviar(digitada = null, vigente = "falta o porto"))
        assertNull(orientacaoAEnviar(digitada = "falta o porto", vigente = "falta o porto"))
        assertNull(orientacaoAEnviar(digitada = "  falta o porto  ", vigente = "falta o porto")) // aparado
        assertNull(orientacaoAEnviar(digitada = "", vigente = null)) // nada antes, nada agora
        assertNull(orientacaoAEnviar(digitada = "   ", vigente = null))
    }

    @Test
    fun `texto novo ou alterado vai aparado`() {
        assertEquals("falta o porto", orientacaoAEnviar(digitada = "  falta o porto ", vigente = null))
        assertEquals("agora o castelo", orientacaoAEnviar(digitada = "agora o castelo", vigente = "falta o porto"))
    }

    @Test
    fun `apagar o que havia manda vazio - e o pedido de tirar a orientacao`() {
        assertEquals("", orientacaoAEnviar(digitada = "", vigente = "falta o porto"))
        assertEquals("", orientacaoAEnviar(digitada = "   ", vigente = "falta o porto"))
    }
}

/** O ViewModel entrega ao repositório exatamente o que a regra manda. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrientacaoNoPainelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val comOrientacao = SugestoesDeCapitulo(
        gerado_em = "2026-10-01T10:00:00", orientacao = "falta o porto", elementos = listOf(elemento()),
    )

    private fun vm(repositorio: SugestoesFalso) = PainelDeIaViewModel(5, repositorio, ElementosFalso())

    @Test
    fun `reanalisar sem mexer no campo nao envia orientacao, e o servidor reaproveita a guardada`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comOrientacao))
        val vm = vm(repositorio)
        vm.aoAbrirPainel(); advanceUntilIdle()
        vm.pedirReanalise()

        vm.confirmarReanalise("falta o porto") // o campo veio preenchido com a vigente e ficou igual
        advanceUntilIdle()

        assertEquals(listOf(true), repositorio.analises)
        assertEquals(listOf<String?>(null), repositorio.orientacoes)
    }

    @Test
    fun `texto novo no campo e enviado`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(analisado))
        val vm = vm(repositorio)
        vm.aoAbrirPainel(); advanceUntilIdle()
        vm.pedirReanalise()

        vm.confirmarReanalise("o objeto Y também aparece")
        advanceUntilIdle()

        assertEquals(listOf<String?>("o objeto Y também aparece"), repositorio.orientacoes)
    }

    @Test
    fun `apagar o campo manda vazio para o servidor apagar a orientacao`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(comOrientacao))
        val vm = vm(repositorio)
        vm.aoAbrirPainel(); advanceUntilIdle()
        vm.pedirReanalise()

        vm.confirmarReanalise("")
        advanceUntilIdle()

        assertEquals(listOf<String?>(""), repositorio.orientacoes)
    }

    @Test
    fun `sem passar o campo o comportamento de antes continua`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(analisado))
        val vm = vm(repositorio)
        vm.aoAbrirPainel(); advanceUntilIdle()
        vm.pedirReanalise()

        vm.confirmarReanalise()
        advanceUntilIdle()

        assertEquals(listOf(true), repositorio.analises)
        assertEquals(listOf<String?>(null), repositorio.orientacoes)
    }

    @Test
    fun `a analise em andamento de outro aparelho mostra a mensagem do servidor (409)`() = runTest {
        val mensagem = "Já há uma análise deste capítulo em andamento. Aguarde terminar."
        val repositorio = SugestoesFalso(
            leitura = ResultadoDaChamada.Sucesso(analisado),
            analise = ResultadoDaChamada.Falha(motivo = mensagem, codigoHttp = 409),
        )
        val vm = vm(repositorio)
        vm.aoAbrirPainel(); advanceUntilIdle()
        vm.pedirReanalise()

        vm.confirmarReanalise("falta o porto")
        advanceUntilIdle()

        assertEquals(mensagem, vm.estado.value.erroDaAnalise)
        assertTrue(!vm.estado.value.analisando)
    }
}
