package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.EventoDeAnalise
import com.allan.imagineer.analise.LocalDoUsuario
import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.analise.TipoDeEvento
import com.allan.imagineer.analise.descreverAviso
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.ResultadoDaGeracao
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

private fun imagemEvento(sucesso: Boolean = true, recusada: Boolean = false, motivo: String? = null) = EventoDeAnalise(
    capituloId = 7, livroId = 1, rotuloDoCapitulo = "capítulo 3", sucesso = sucesso, motivo = motivo,
    tipo = TipoDeEvento.IMAGEM, frameId = 70, rotuloDaCena = "A partida", recusada = recusada,
)

/** O texto do aviso global de imagem gerada. */
class AvisoDeImagemTest {

    @Test
    fun `imagem gerada avisa dentro e fora do livro`() {
        assertEquals("Imagem gerada: «A partida».", descreverAviso(imagemEvento(), LocalDoUsuario(1), null, "O Alienista"))
        assertEquals(
            "Imagem gerada em «O Alienista», capítulo 3: «A partida».",
            descreverAviso(imagemEvento(), LocalDoUsuario(null), null, "O Alienista"),
        )
    }

    @Test
    fun `recusa do provedor pede para abrir e ajustar o prompt`() {
        assertEquals(
            "A imagem de «A partida» foi recusada pelo provedor; abra para ajustar o prompt.",
            descreverAviso(imagemEvento(sucesso = false, recusada = true), LocalDoUsuario(1), null, null),
        )
    }

    @Test
    fun `falha avisa com o motivo`() {
        assertEquals(
            "A geração da imagem de «A partida» falhou: sem crédito",
            descreverAviso(imagemEvento(sucesso = false, motivo = "sem crédito"), LocalDoUsuario(1), null, null),
        )
    }

    @Test
    fun `o modal da cena ou o painel do capitulo abertos calam o aviso, o modal de outra cena nao`() {
        assertNull(descreverAviso(imagemEvento(), LocalDoUsuario(1), null, null, modalDoFrameVisivel = 70))
        assertNull(descreverAviso(imagemEvento(), LocalDoUsuario(1), painelVisivel = 7, tituloDoLivro = null))
        assertNotNull(descreverAviso(imagemEvento(), LocalDoUsuario(1), painelVisivel = 8, tituloDoLivro = null, modalDoFrameVisivel = 71))
    }
}

/** A geração da imagem vive no serviço do app, como a do prompt: sobrevive à saída da tela e avisa ao terminar. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImagemNoServicoDoAppTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() = Dispatchers.setMain(agendador)

    @After
    fun limpar() = Dispatchers.resetMain()

    private fun escopo() = CoroutineScope(SupervisorJob() + agendador)

    @Test
    fun `gerar emite um evento de imagem com o frame e o nome da cena`() = runTest {
        val prompts = PromptsFalso()
        val servico = ServicoDeAnalises(SugestoesFalso(), escopo(), prompts)
        val eventos = mutableListOf<EventoDeAnalise>()
        escopo().launch { servico.eventos.collect { eventos += it } }
        runCurrent()

        servico.iniciarImagem(11, 70, 5, 2, "capítulo 3", "A partida", null, null, false, emptyList())
        advanceUntilIdle()

        assertEquals(listOf<Pair<Int, String?>>(11 to null), prompts.geracoesDeImagem)
        assertEquals(
            listOf(EventoDeAnalise(5, 2, "capítulo 3", sucesso = true, tipo = TipoDeEvento.IMAGEM, frameId = 70, rotuloDaCena = "A partida")),
            eventos,
        )
        assertNull(servico.imagemEmAndamento(11))
    }

    @Test
    fun `uma geracao por prompt de cada vez - pedir outra devolve a mesma espera`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also { it.travaDaGeracaoDeImagem = trava }
        val servico = ServicoDeAnalises(SugestoesFalso(), escopo(), prompts)

        val primeira = servico.iniciarImagem(11, 70, 5, 2, "capítulo 3", "A partida", null, null, false, emptyList())
        runCurrent()
        val segunda = servico.iniciarImagem(11, 70, 5, 2, "capítulo 3", "A partida", null, null, false, emptyList())

        assertSame(primeira, segunda)
        trava.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, prompts.geracoesDeImagem.size)
    }

    @Test
    fun `sem filtro e com referencias chegam ao repositorio`() = runTest {
        val prompts = PromptsFalso()
        val servico = ServicoDeAnalises(SugestoesFalso(), escopo(), prompts)

        servico.iniciarImagem(11, 70, 5, 2, "capítulo 3", "A partida", null, "modelo-x", true, listOf(3, 4))
        advanceUntilIdle()

        assertEquals(listOf(11), prompts.pedidosSemFiltro)
        assertEquals(listOf(listOf(3, 4)), prompts.referenciasPedidas)
        assertEquals(listOf<String?>("modelo-x"), prompts.modelosPedidos)
    }
}
