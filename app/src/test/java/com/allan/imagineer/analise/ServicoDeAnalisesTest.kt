package com.allan.imagineer.analise

import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.capitulo.painel.SugestoesFalso
import com.allan.imagineer.telas.capitulo.painel.analisado
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** O texto do aviso de análise concluída (defeito D1): depende de onde o usuário está. */
class DescreverAvisoTest {

    private fun evento(sucesso: Boolean = true, motivo: String? = null, livroId: Int? = 1) = EventoDeAnalise(
        capituloId = 7, livroId = livroId, rotuloDoCapitulo = "capítulo 3", sucesso = sucesso, motivo = motivo,
    )

    @Test
    fun `dentro do livro diz so o capitulo`() {
        assertEquals(
            "Análise do capítulo 3 concluída.",
            descreverAviso(evento(), LocalDoUsuario(livroId = 1), painelVisivel = null, tituloDoLivro = "O Alienista"),
        )
    }

    @Test
    fun `fora do livro diz o livro e o capitulo`() {
        assertEquals(
            "Análise concluída em «O Alienista», capítulo 3.",
            descreverAviso(evento(), LocalDoUsuario(livroId = null), painelVisivel = null, tituloDoLivro = "O Alienista"),
        )
        // Em outro livro conta como fora.
        assertEquals(
            "Análise concluída em «O Alienista», capítulo 3.",
            descreverAviso(evento(), LocalDoUsuario(livroId = 99), painelVisivel = null, tituloDoLivro = "O Alienista"),
        )
    }

    @Test
    fun `fora do livro sem conseguir o titulo ainda avisa, so com o capitulo`() {
        assertEquals(
            "Análise concluída em capítulo 3.",
            descreverAviso(evento(), LocalDoUsuario(livroId = null), painelVisivel = null, tituloDoLivro = null),
        )
    }

    @Test
    fun `quem esta olhando o painel daquele capitulo nao recebe aviso`() {
        assertNull(descreverAviso(evento(), LocalDoUsuario(livroId = 1), painelVisivel = 7, tituloDoLivro = null))
        // O painel de OUTRO capítulo aberto não suprime.
        assertNotNull(descreverAviso(evento(), LocalDoUsuario(livroId = 1), painelVisivel = 8, tituloDoLivro = null))
    }

    @Test
    fun `falha tambem avisa, com o motivo, dentro e fora do livro`() {
        assertEquals(
            "A análise do capítulo 3 falhou: Já há uma análise deste capítulo em andamento.",
            descreverAviso(
                evento(sucesso = false, motivo = "Já há uma análise deste capítulo em andamento."),
                LocalDoUsuario(livroId = 1), painelVisivel = null, tituloDoLivro = null,
            ),
        )
        assertEquals(
            "A análise de «O Alienista», capítulo 3 falhou: sem rede",
            descreverAviso(
                evento(sucesso = false, motivo = "sem rede"),
                LocalDoUsuario(livroId = null), painelVisivel = null, tituloDoLivro = "O Alienista",
            ),
        )
    }

    @Test
    fun `o rotulo do capitulo usa o titulo quando ha, e o numero quando nao`() {
        assertEquals("capítulo «O muro»", rotuloDoCapituloNoAviso(3, "O muro"))
        assertEquals("capítulo 3", rotuloDoCapituloNoAviso(3, null))
        assertEquals("capítulo 3", rotuloDoCapituloNoAviso(3, "  "))
    }
}

/**
 * Um escopo "do app" para o teste, no **mesmo agendador** do `runTest`: assim `advanceUntilIdle` e `runCurrent` o
 * fazem andar. (O `backgroundScope` do `runTest` não avança com `advanceUntilIdle`.)
 */
private val escoposDoTeste = mutableMapOf<TestScope, CoroutineScope>()

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.escopoDeTeste(): CoroutineScope =
    escoposDoTeste.getOrPut(this) { CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)) }

/** O serviço: a análise é do app, não da tela. */
@OptIn(ExperimentalCoroutinesApi::class)
class ServicoDeAnalisesTest {

    @Test
    fun `termina com sucesso e emite um evento com o capitulo e o livro`() = runTest {
        val repositorio = SugestoesFalso()
        val servico = ServicoDeAnalises(repositorio, escopoDeTeste())
        val eventos = mutableListOf<EventoDeAnalise>()
        escopoDeTeste().launch { servico.eventos.collect { eventos += it } }
        runCurrent()

        val trabalho = servico.iniciar(5, livroId = 2, rotuloDoCapitulo = "capítulo 3", forcar = false, orientacao = null)
        advanceUntilIdle()

        assertTrue(trabalho.await() is ResultadoDaChamada.Sucesso)
        assertEquals(listOf(EventoDeAnalise(5, 2, "capítulo 3", sucesso = true)), eventos)
        assertNull(servico.emAndamento(5)) // acabou: já não está em andamento
    }

    @Test
    fun `falha tambem emite um evento, com o motivo`() = runTest {
        val repositorio = SugestoesFalso(analise = ResultadoDaChamada.Falha("o servidor caiu"))
        val servico = ServicoDeAnalises(repositorio, escopoDeTeste())
        val eventos = mutableListOf<EventoDeAnalise>()
        escopoDeTeste().launch { servico.eventos.collect { eventos += it } }
        runCurrent()

        servico.iniciar(5, 2, "capítulo 3", forcar = true, orientacao = null)
        advanceUntilIdle()

        assertEquals(listOf(EventoDeAnalise(5, 2, "capítulo 3", sucesso = false, motivo = "o servidor caiu")), eventos)
    }

    @Test
    fun `so uma analise por capitulo - pedir outra enquanto roda devolve a mesma espera e nao cobra duas vezes`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val repositorio = SugestoesFalso().also { it.travaDaAnalise = trava }
        val servico = ServicoDeAnalises(repositorio, escopoDeTeste())

        val primeira = servico.iniciar(5, 2, "capítulo 3", forcar = false, orientacao = null)
        runCurrent()
        val segunda = servico.iniciar(5, 2, "capítulo 3", forcar = true, orientacao = "outra")
        trava.complete(Unit)
        advanceUntilIdle()

        assertSame(primeira, segunda)
        assertEquals(listOf(false), repositorio.analises) // uma só chamada ao POST
    }

    @Test
    fun `capitulos diferentes rodam cada um a sua`() = runTest {
        val repositorio = SugestoesFalso()
        val servico = ServicoDeAnalises(repositorio, escopoDeTeste())

        servico.iniciar(5, 2, "capítulo 3", forcar = false, orientacao = null)
        servico.iniciar(6, 2, "capítulo 4", forcar = false, orientacao = null)
        advanceUntilIdle()

        assertEquals(2, repositorio.analises.size)
    }

    @Test
    fun `a analise sobrevive a quem desistiu de esperar - e o aviso sai mesmo assim`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val repositorio = SugestoesFalso().also { it.travaDaAnalise = trava }
        val servico = ServicoDeAnalises(repositorio, escopoDeTeste())
        val eventos = mutableListOf<EventoDeAnalise>()
        escopoDeTeste().launch { servico.eventos.collect { eventos += it } }
        runCurrent()

        // A tela espera o resultado...
        val trabalho = servico.iniciar(5, 2, "capítulo 3", forcar = false, orientacao = null)
        val espera: Job = escopoDeTeste().launch { trabalho.await() }
        runCurrent()
        // ...e o usuário sai dela (o ViewModel é destruído e a espera cancelada).
        espera.cancel()
        runCurrent()
        assertFalse(trabalho.isCancelled)
        assertNotNull(servico.emAndamento(5)) // continua rodando

        trava.complete(Unit)
        advanceUntilIdle()

        assertTrue(trabalho.isCompleted)
        assertEquals(1, eventos.size) // o aviso sai, e o resultado está salvo no servidor
        assertEquals(1, repositorio.analises.size)
    }

    @Test
    fun `lembra de qual livro e o capitulo, para a navegacao saber onde o usuario esta`() = runTest {
        val servico = ServicoDeAnalises(SugestoesFalso(), escopoDeTeste())

        servico.registrarLivro(capituloId = 5, livroId = 2)

        assertEquals(2, servico.livroDoCapitulo(5))
        assertNull(servico.livroDoCapitulo(99))
    }

    @Test
    fun `guarda qual painel esta na tela`() = runTest {
        val servico = ServicoDeAnalises(SugestoesFalso(), escopoDeTeste())
        assertNull(servico.painelVisivel.value)

        servico.definirPainelVisivel(5)
        assertEquals(5, servico.painelVisivel.value)
        servico.definirPainelVisivel(null)
        assertNull(servico.painelVisivel.value)
    }

    @Test
    fun `o resultado e o mesmo que o repositorio devolveu`() = runTest {
        val servico = ServicoDeAnalises(SugestoesFalso(), escopoDeTeste())

        val resultado = servico.iniciar(5, 2, "capítulo 3", forcar = false, orientacao = null)
        advanceUntilIdle()

        assertEquals(ResultadoDaChamada.Sucesso(analisado), resultado.await())
    }
}
