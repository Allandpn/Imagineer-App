package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.ElementoCasado
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import com.allan.imagineer.telas.capitulo.ParagrafoDoTexto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** As regras puras do trecho selecionado (TR1 a TR4). */
class RegrasDoTrechoTest {

    private val paragrafos = listOf(
        ParagrafoDoTexto(0, "Abertura calma."),
        ParagrafoDoTexto(17, "Ned ergueu a espada.\nO vento soprou forte."),
        ParagrafoDoTexto(60, "Depois, no pátio, Jon chorou."),
    )

    @Test
    fun `TR4 o trecho vira o inicio do paragrafo em que esta, sem ligar para quebra de linha`() {
        assertEquals(17, paragrafoDoTrecho(paragrafos, "espada. O vento"))
        assertEquals(60, paragrafoDoTrecho(paragrafos, "  no pátio,   Jon "))
    }

    @Test
    fun `TR4 trecho que atravessa paragrafos vale o primeiro, onde ele comeca`() {
        assertEquals(0, paragrafoDoTrecho(paragrafos, "Abertura calma. Ned ergueu a espada."))
        assertEquals(17, paragrafoDoTrecho(paragrafos, "Ned ergueu a espada. O vento soprou forte. Depois, no pátio"))
    }

    @Test
    fun `TR4 trecho vazio, ausente ou com comeco curto demais nao tem posicao`() {
        assertNull(paragrafoDoTrecho(paragrafos, "   "))
        assertNull(paragrafoDoTrecho(paragrafos, "Arya correu pelo bosque"))
        assertNull(paragrafoDoTrecho(paragrafos, "Ned zzz yyy")) // "Ned" sozinho é curto demais para valer
    }

    @Test
    fun `TR3 o titulo vem do que a pessoa quer ver, ou do trecho, e e cortado`() {
        assertEquals("Foxen no espelho", tituloDaCenaDoTrecho("Foxen no espelho", "qualquer coisa"))
        assertEquals("Ned ergueu a espada.", tituloDaCenaDoTrecho("  ", "Ned ergueu\na espada."))
        val longo = tituloDaCenaDoTrecho("a".repeat(200), "x")
        assertEquals(60, longo.length)
        assertTrue(longo.endsWith("…"))
    }

    @Test
    fun `TR3 a descricao poe o que a pessoa quer ver primeiro e o trecho como contexto`() {
        assertEquals("Trecho do capítulo: «Ned ergueu a espada.»", descricaoDaCenaDoTrecho("", "Ned ergueu\na espada."))
        assertEquals("Em close\n\nTrecho do capítulo: «Ned»", descricaoDaCenaDoTrecho(" Em close ", "Ned"))
        assertEquals(LIMITE_DO_TRECHO_NA_DESCRICAO + "Trecho do capítulo: «»".length, descricaoDaCenaDoTrecho("", "z".repeat(5000)).length)
    }

    private fun el(id: Int, nome: String, estado: Int?, vigente: Int? = null, descartada: Boolean = false, casado: Boolean = true) =
        elemento(id = id, nome = nome, elementoId = if (casado) id + 100 else null, estadoId = estado, vigenteDoCapitulo = vigente, descartada = descartada)
            .copy(elemento_casado = if (casado) ElementoCasado(id = id + 100, tipo = "PERSONAGEM", nome = nome) else null)

    @Test
    fun `TR2 as opcoes sao os confirmados com estado, sem descartados nem repetidos, em ordem alfabetica`() {
        val opcoes = opcoesDeElementosDoTrecho(
            listOf(
                el(1, "Zeca", estado = 11),
                el(2, "Álvaro", estado = null, vigente = 12), // sem estado aqui: usa o vigente
                el(3, "Sem estado", estado = null),
                el(4, "Descartado", estado = 14, descartada = true),
                el(5, "Novo", estado = 15, casado = false), // ainda não é elemento
                el(6, "Zeca de novo", estado = 11), // mesmo estado: não repete
            ),
        )

        assertEquals(listOf("Álvaro", "Zeca"), opcoes.map { it.nome })
        assertEquals(listOf(912, 11), opcoes.map { it.estadoId })
    }

    @Test
    fun `TR2 ja vem marcados os elementos citados no trecho, sem ligar para acento`() {
        val opcoes = listOf(OpcaoDeElementoDoTrecho(11, "Álvaro"), OpcaoDeElementoDoTrecho(12, "Jon"))

        assertEquals(setOf(11), estadosCitadosNoTrecho(opcoes, "Alvaro correu pelo pátio."))
        assertEquals(emptySet<Int>(), estadosCitadosNoTrecho(opcoes, "Ninguém aqui."))
    }
}

/** O fluxo do trecho selecionado no ViewModel do painel (TR1 a TR5). */
@OptIn(ExperimentalCoroutinesApi::class)
class TrechoParaImagemNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val jon = elemento(id = 1, nome = "Jon", elementoId = 3, estadoId = 9).copy(
        elemento_casado = ElementoCasado(id = 3, tipo = "PERSONAGEM", nome = "Jon"),
    )

    private suspend fun kotlinx.coroutines.test.TestScope.vmComSugestoes(repositorio: SugestoesFalso): PainelDeIaViewModel {
        val vm = PainelDeIaViewModel(5, repositorio, ElementosFalso())
        vm.aoAbrirPainel(); advanceUntilIdle()
        return vm
    }

    private fun repositorio() = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(SugestoesDeCapitulo(gerado_em = "2026-10-03T10:00:00", elementos = listOf(jon))))

    @Test
    fun `TR2 abrir o trecho ja marca os elementos citados e guarda a posicao`() = runTest {
        val vm = vmComSugestoes(repositorio())

        vm.abrirTrecho("  Jon entrou no pátio.  ", posicao = 120)

        val trecho = vm.estado.value.trechoParaImagem
        assertNotNull(trecho)
        assertEquals("Jon entrou no pátio.", trecho!!.trecho)
        assertEquals(120, trecho.posicao)
        assertEquals(setOf(9), trecho.estadosEscolhidos)
    }

    @Test
    fun `TR1 trecho em branco nao abre nada`() = runTest {
        val vm = vmComSugestoes(repositorio())

        vm.abrirTrecho("   ", posicao = 3)

        assertNull(vm.estado.value.trechoParaImagem)
    }

    @Test
    fun `TR2 descricao e elementos se editam, e a descricao tem limite`() = runTest {
        val vm = vmComSugestoes(repositorio())
        vm.abrirTrecho("Um trecho.", posicao = null)

        vm.alterarDescricaoDoTrecho("x".repeat(LIMITE_DA_DESCRICAO_DO_TRECHO + 50))
        vm.alternarElementoDoTrecho(9)
        vm.alternarElementoDoTrecho(9)
        vm.alternarElementoDoTrecho(7)

        val trecho = vm.estado.value.trechoParaImagem!!
        assertEquals(LIMITE_DA_DESCRICAO_DO_TRECHO, trecho.descricao.length)
        assertEquals(setOf(7), trecho.estadosEscolhidos)
    }

    @Test
    fun `TR3 criar a cena manda titulo, descricao, posicao e elementos, fecha o dialogo e abre o modal do frame`() = runTest {
        val repositorio = repositorio()
        val vm = vmComSugestoes(repositorio)
        vm.abrirTrecho("Jon entrou no pátio.", posicao = 120)
        vm.alterarDescricaoDoTrecho("Jon de costas")
        val versaoAntes = vm.estado.value.versaoDosFrames

        vm.criarCenaDoTrecho(); advanceUntilIdle()

        val pedida = repositorio.cenasDeTrechoPedidas.single()
        assertEquals("Jon de costas", pedida.titulo)
        assertEquals("Jon de costas\n\nTrecho do capítulo: «Jon entrou no pátio.»", pedida.descricao)
        assertEquals(120, pedida.posicao)
        assertEquals(listOf(9), pedida.estadosIds)
        assertEquals("Jon entrou no pátio.", pedida.trecho)  // FD7: o trecho selecionado também vai separado, para o servidor guardar
        assertNull(vm.estado.value.trechoParaImagem)
        assertEquals(versaoAntes + 1, vm.estado.value.versaoDosFrames)
        assertEquals(listOf<ModalAberto>(ModalAberto.DeFrame(500, "Jon de costas")), vm.estado.value.modais)
    }

    @Test
    fun `TR3 a recusa do servidor fica no dialogo, que continua aberto`() = runTest {
        val repositorio = repositorio().also { it.resultadoDaCenaDeTrecho = ResultadoDaChamada.Falha("Capítulo não encontrado.") }
        val vm = vmComSugestoes(repositorio)
        vm.abrirTrecho("Um trecho.", posicao = 5)

        vm.criarCenaDoTrecho(); advanceUntilIdle()

        val trecho = vm.estado.value.trechoParaImagem
        assertNotNull(trecho)
        assertEquals("Capítulo não encontrado.", trecho!!.erro)
        assertFalse(trecho.criando)
        assertTrue(vm.estado.value.modais.isEmpty())
    }

    @Test
    fun `TR4 tocar no icone de um frame sem sugestao abre e fecha o modal do frame`() = runTest {
        val vm = vmComSugestoes(repositorio())

        vm.abrirModalDeFrame(70, "A partida")
        assertEquals(listOf<ModalAberto>(ModalAberto.DeFrame(70, "A partida")), vm.estado.value.modais)

        vm.fecharModalDoFrame()
        assertTrue(vm.estado.value.modais.isEmpty())
    }

    @Test
    fun `TR1 cancelar fecha o dialogo sem criar nada`() = runTest {
        val repositorio = repositorio()
        val vm = vmComSugestoes(repositorio)
        vm.abrirTrecho("Um trecho.", posicao = 5)

        vm.fecharTrecho()

        assertNull(vm.estado.value.trechoParaImagem)
        assertTrue(repositorio.cenasDeTrechoPedidas.isEmpty())
    }
}
