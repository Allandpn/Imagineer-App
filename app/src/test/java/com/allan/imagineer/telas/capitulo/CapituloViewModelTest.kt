package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.rede.CapituloAjuste
import com.allan.imagineer.rede.CapituloDetalhe
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.RepositorioDeCapitulos
import com.allan.imagineer.rede.RepositorioDeArtefatos
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun capituloComTexto(texto: String) = CapituloDetalhe(
    id = 5, ordem = 2, titulo = "Catelyn", ignorado = false, tamanho_do_texto = texto.length,
    livro_id = 1, texto = texto,
)

/** Só a leitura do texto; o resto o ViewModel de Capítulo não usa. */
/** Artefatos falsos: devolvem o que o teste combinar e contam as leituras. */
internal class ArtefatosFalso(
    var resposta: ResultadoDaChamada<List<Artefato>> = ResultadoDaChamada.Sucesso(emptyList()),
) : RepositorioDeArtefatos {
    var leituras = 0

    override suspend fun ler(capituloId: Int): ResultadoDaChamada<List<Artefato>> {
        leituras++
        return resposta
    }
}

private class CapitulosParaLeitura(var resposta: ResultadoDaChamada<CapituloDetalhe>) : RepositorioDeCapitulos {
    var chamadas = 0
    var trava: CompletableDeferred<Unit>? = null

    override suspend fun abrirCapitulo(capituloId: Int): ResultadoDaChamada<CapituloDetalhe> {
        chamadas++
        trava?.await()
        return resposta
    }

    override suspend fun ajustarCapitulo(capituloId: Int, ajuste: CapituloAjuste): ResultadoDaChamada<CapituloResumo> =
        error("não usado pela leitura")
}

/** A divisão do texto em parágrafos (item 7.5a, incremento 8). */
class DividirEmParagrafosTest {

    @Test
    fun `uma linha em branco separa os paragrafos`() {
        assertEquals(listOf("Primeiro.", "Segundo."), dividirEmParagrafos("Primeiro.\n\nSegundo."))
    }

    @Test
    fun `quebras a mais tambem separam, sem gerar paragrafos vazios`() {
        assertEquals(listOf("A", "B"), dividirEmParagrafos("A\n\n\n\nB"))
    }

    @Test
    fun `quebra simples dentro do paragrafo e preservada`() {
        // O backend usa "\n" sozinho para o <br>: o Text do Compose o desenha como quebra de linha.
        assertEquals(listOf("Linha um\nLinha dois", "Outro"), dividirEmParagrafos("Linha um\nLinha dois\n\nOutro"))
    }

    @Test
    fun `apara as pontas e descarta pedacos so de espaco`() {
        assertEquals(listOf("A", "B"), dividirEmParagrafos("\n\n  A  \n\n   \n\nB\n\n"))
    }

    @Test
    fun `texto vazio ou so espacos nao tem paragrafos`() {
        assertEquals(emptyList<String>(), dividirEmParagrafos(""))
        assertEquals(emptyList<String>(), dividirEmParagrafos("   \n\n  "))
    }

    @Test
    fun `um paragrafo so`() {
        assertEquals(listOf("Só isto."), dividirEmParagrafos("Só isto."))
    }

    @Test
    fun `um capitulo grande vira muitos itens, e nao um texto gigante`() {
        val texto = (1..2000).joinToString("\n\n") { "Parágrafo número $it com algum texto." }

        val paragrafos = dividirEmParagrafos(texto)

        assertEquals(2000, paragrafos.size)
        assertEquals("Parágrafo número 1 com algum texto.", paragrafos.first())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CapituloViewModelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val capitulo = capituloComTexto("Um.\n\nDois.\n\nTrês.")

    @Test
    fun `comeca carregando`() = runTest {
        val vm = CapituloViewModel(5, CapitulosParaLeitura(ResultadoDaChamada.Sucesso(capitulo)), ArtefatosFalso())

        assertEquals(EstadoDoCapitulo.Carregando, vm.estado.value)
    }

    @Test
    fun `carregar entrega o capitulo com o texto ja dividido`() = runTest {
        val vm = CapituloViewModel(5, CapitulosParaLeitura(ResultadoDaChamada.Sucesso(capitulo)), ArtefatosFalso())

        vm.carregar()
        advanceUntilIdle()

        assertEquals(
            EstadoDoCapitulo.Pronto(capitulo, listOf("Um.", "Dois.", "Três.")),
            vm.estado.value,
        )
    }

    @Test
    fun `falha mostra o motivo e tentar de novo recupera`() = runTest {
        val repositorio = CapitulosParaLeitura(ResultadoDaChamada.Falha("Não existe capítulo com id 5.", 404))
        val vm = CapituloViewModel(5, repositorio, ArtefatosFalso())
        vm.carregar()
        advanceUntilIdle()
        assertEquals(EstadoDoCapitulo.Erro("Não existe capítulo com id 5."), vm.estado.value)

        repositorio.resposta = ResultadoDaChamada.Sucesso(capitulo)
        vm.tentarDeNovo()
        advanceUntilIdle()

        assertTrue(vm.estado.value is EstadoDoCapitulo.Pronto)
    }

    @Test
    fun `carregar de novo com o texto ja na tela nao gasta outra chamada`() = runTest {
        // Girar o tablet recomeça a composição e chama carregar() outra vez.
        val repositorio = CapitulosParaLeitura(ResultadoDaChamada.Sucesso(capitulo))
        val vm = CapituloViewModel(5, repositorio, ArtefatosFalso())
        vm.carregar()
        advanceUntilIdle()

        vm.carregar()
        vm.carregar()
        advanceUntilIdle()

        assertEquals(1, repositorio.chamadas)
    }

    @Test
    fun `carregar durante um carregamento nao duplica a chamada`() = runTest {
        val repositorio = CapitulosParaLeitura(ResultadoDaChamada.Sucesso(capitulo))
        repositorio.trava = CompletableDeferred()
        val vm = CapituloViewModel(5, repositorio, ArtefatosFalso())

        vm.carregar()
        advanceUntilIdle()
        vm.carregar()
        advanceUntilIdle()

        assertEquals(1, repositorio.chamadas)
        assertEquals(EstadoDoCapitulo.Carregando, vm.estado.value)
    }

    @Test
    fun `tentar de novo sem erro nao faz nada`() = runTest {
        val repositorio = CapitulosParaLeitura(ResultadoDaChamada.Sucesso(capitulo))
        val vm = CapituloViewModel(5, repositorio, ArtefatosFalso())
        vm.carregar()
        advanceUntilIdle()

        vm.tentarDeNovo()
        advanceUntilIdle()

        assertEquals(1, repositorio.chamadas)
    }

    @Test
    fun `capitulo sem texto fica pronto, com zero paragrafos`() = runTest {
        val vazio = capituloComTexto("")
        val vm = CapituloViewModel(5, CapitulosParaLeitura(ResultadoDaChamada.Sucesso(vazio)), ArtefatosFalso())

        vm.carregar()
        advanceUntilIdle()

        assertEquals(EstadoDoCapitulo.Pronto(vazio, emptyList()), vm.estado.value)
    }
}
