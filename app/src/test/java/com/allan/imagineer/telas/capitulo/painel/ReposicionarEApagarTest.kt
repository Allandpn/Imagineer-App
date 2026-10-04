package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.allan.imagineer.rede.ElementoParaVincular
import com.allan.imagineer.rede.ElementosParaVincular
import com.allan.imagineer.rede.ResultadoDaChamada
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun candidato(id: Int, nome: String) = ElementoParaVincular(elemento_id = id, estado_id = id * 10, nome = nome)

/** O seletor de elementos e imagens com os de outros capítulos (VM7). */
class SeletorComOutrosCapitulosTest {

    @Test
    fun `VM7 todos os elementos inclui os de outros capitulos, para a selecao e as imagens existirem`() {
        val dados = ElementosParaVincular(
            identificados = listOf(candidato(1, "Jon")),
            outros = listOf(candidato(2, "Prato")),
            de_outros_capitulos = listOf(candidato(3, "Escudo")),
        )

        assertEquals(listOf("Jon", "Prato", "Escudo"), todosOsElementos(dados).map { it.nome })
    }

    @Test
    fun `VM7 a selecao inicial reconhece as imagens dos elementos de outros capitulos`() {
        val dados = ElementosParaVincular(
            de_outros_capitulos = listOf(candidato(3, "Escudo").copy(imagens = listOf(com.allan.imagineer.rede.ImagemCandidata(id = 77)))),
        )

        assertEquals(listOf(77), selecaoInicial(dados, imagensEscolhidas = listOf(77, 99)).imagens.toList())
    }
}

/** Reposicionar, tirar a posição e apagar a cena de um trecho (PM3). */
@OptIn(ExperimentalCoroutinesApi::class)
class ReposicionarEApagarNoPainelTest {

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
    fun `PM3 um frame sem sugestao se posiciona pelo proprio frame`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.iniciarPosicionamentoDeFrame(ehCena = true, frameId = 70, rotulo = "O vento")

        vm.escolherParagrafo(120); advanceUntilIdle()

        assertEquals(listOf(70 to (120 as Int?)), repositorio.posicoesDeFramePedidas)
        assertTrue(repositorio.posicoesPedidas.isEmpty())
        assertNull(vm.estado.value.posicionando)
    }

    @Test
    fun `PM3 tirar a posicao de uma sugestao manda nulo e manda reler os artefatos`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        val versaoAntes = vm.estado.value.versaoDosFrames

        vm.tirarPosicao(ehCena = false, sugestaoId = 3, frameId = null); advanceUntilIdle()

        assertEquals(listOf(Triple(false, 3, null as Int?)), repositorio.posicoesPedidas)
        assertEquals(versaoAntes + 1, vm.estado.value.versaoDosFrames)
    }

    @Test
    fun `PM3 tirar a posicao de um frame sem sugestao usa o frame`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)

        vm.tirarPosicao(ehCena = true, sugestaoId = null, frameId = 70); advanceUntilIdle()

        assertEquals(listOf(70 to (null as Int?)), repositorio.posicoesDeFramePedidas)
    }

    @Test
    fun `PM3 a recusa de tirar a posicao vira recado do item`() = runTest {
        val repositorio = SugestoesFalso().also { it.resultadoDePosicionar = ResultadoDaChamada.Falha("Sem conexão.") }
        val vm = vm(repositorio)

        vm.tirarPosicao(ehCena = true, sugestaoId = 8, frameId = null); advanceUntilIdle()

        assertEquals("Sem conexão.", vm.estado.value.mensagensDeCena[8]?.texto)
    }

    @Test
    fun `apagar a cena pede confirmacao, apaga o frame, fecha o modal dele e manda reler os artefatos`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.abrirModalDeFrame(70, "O vento")
        val versaoAntes = vm.estado.value.versaoDosFrames

        vm.pedirApagarFrame(70, "O vento")
        assertEquals(ApagandoFrame(70, "O vento"), vm.estado.value.apagandoFrame)
        assertTrue(repositorio.framesApagados.isEmpty()) // pedir não apaga

        vm.confirmarApagarFrame(); advanceUntilIdle()

        assertEquals(listOf(70), repositorio.framesApagados)
        assertNull(vm.estado.value.apagandoFrame)
        assertTrue(vm.estado.value.modais.isEmpty())
        assertEquals(versaoAntes + 1, vm.estado.value.versaoDosFrames)
    }

    @Test
    fun `apagar a cena cancelar nao apaga nada`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.pedirApagarFrame(70, "O vento")

        vm.cancelarApagarFrame()

        assertNull(vm.estado.value.apagandoFrame)
        assertTrue(repositorio.framesApagados.isEmpty())
    }

    @Test
    fun `apagar a cena a recusa do servidor fica no dialogo, que continua aberto`() = runTest {
        val repositorio = SugestoesFalso().also { it.resultadoDeApagarFrame = ResultadoDaChamada.Falha("Frame não encontrado.") }
        val vm = vm(repositorio)
        vm.abrirModalDeFrame(70, "O vento")
        vm.pedirApagarFrame(70, "O vento")

        vm.confirmarApagarFrame(); advanceUntilIdle()

        val alvo = vm.estado.value.apagandoFrame
        assertNotNull(alvo)
        assertEquals("Frame não encontrado.", alvo!!.erro)
        assertEquals(false, alvo.apagando)
        assertEquals(1, vm.estado.value.modais.size) // o modal continua
    }

    @Test
    fun `apagar a cena de uma sugestao relê a lista para o modal refletir que ela voltou a pendente`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.aoAbrirPainel(); advanceUntilIdle()
        val leiturasAntes = repositorio.leituras

        vm.pedirApagarFrame(70, "A partida", deSugestao = true)
        assertEquals(true, vm.estado.value.apagandoFrame?.deSugestao)
        vm.confirmarApagarFrame(); advanceUntilIdle()

        assertEquals(listOf(70), repositorio.framesApagados)
        assertTrue("releu as sugestões", repositorio.leituras > leiturasAntes)
    }

    @Test
    fun `apagar o frame de um trecho nao relê as sugestoes`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.aoAbrirPainel(); advanceUntilIdle()
        val leiturasAntes = repositorio.leituras

        vm.pedirApagarFrame(70, "O vento")
        vm.confirmarApagarFrame(); advanceUntilIdle()

        assertEquals(leiturasAntes, repositorio.leituras)
    }
}

/** Os rótulos e os ícones do visualizador de imagem (tela cheia). */
class IconesDoVisualizadorTest {

    @Test
    fun `cada acao conhecida tem o seu icone, e a canonica e a ocultacao mudam com o estado`() {
        assertEquals(Icons.Filled.StarBorder, iconeDaAcaoDaImagem(rotuloDaAcaoCanonica(false)))
        assertEquals(Icons.Filled.Star, iconeDaAcaoDaImagem(rotuloDaAcaoCanonica(true)))
        assertEquals(Icons.Filled.VisibilityOff, iconeDaAcaoDaImagem(rotuloDaOcultacao(false)))
        assertEquals(Icons.Filled.Visibility, iconeDaAcaoDaImagem(rotuloDaOcultacao(true)))
        assertEquals(Icons.Filled.MoreHoriz, iconeDaAcaoDaImagem("outra coisa"))
    }

    @Test
    fun `o botao que tira a posicao diz o que faz`() {
        assertEquals("Voltar à posição automática", ROTULO_TIRAR_POSICAO)
    }
}

/** Editar o título e a descrição de uma cena (LV6). */
@OptIn(ExperimentalCoroutinesApi::class)
class EditarCenaNoPainelTest {

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
    fun `LV6 editar uma cena sugerida abre com o que ela tem e grava pelo servidor`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.abrirEdicaoDaCena(8, "A espada", "Ned ergue a espada.")
        assertEquals("Ned ergue a espada.", vm.estado.value.editandoCena?.descricao)

        vm.salvarEdicaoDaCena("  A espada de Ned ", "Nova descrição"); advanceUntilIdle()

        assertEquals(listOf(Triple("sugestao8", "A espada de Ned", "Nova descrição")), repositorio.edicoesPedidas)
        assertNull(vm.estado.value.editandoCena)
    }

    @Test
    fun `LV6 editar a cena de um trecho le o texto do frame e grava no frame`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.abrirModalDeFrame(70, "O vento")
        val versaoAntes = vm.estado.value.versaoDosFrames

        vm.abrirEdicaoDeFrame(70, "O vento")
        assertTrue(vm.estado.value.editandoCena!!.carregando)
        advanceUntilIdle()
        assertEquals("Título lido", vm.estado.value.editandoCena?.titulo)
        assertEquals("Descrição lida", vm.estado.value.editandoCena?.descricao)

        vm.salvarEdicaoDaCena("O vento forte", ""); advanceUntilIdle()

        assertEquals(listOf(Triple("frame70", "O vento forte", "")), repositorio.edicoesPedidas)
        assertEquals("O vento forte", (vm.estado.value.modais.single() as ModalAberto.DeFrame).rotulo)
        assertEquals(versaoAntes + 1, vm.estado.value.versaoDosFrames)
    }

    @Test
    fun `LV6 titulo vazio nao grava e avisa`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.abrirEdicaoDaCena(8, "A espada", null)

        vm.salvarEdicaoDaCena("   ", "x"); advanceUntilIdle()

        assertTrue(repositorio.edicoesPedidas.isEmpty())
        assertEquals("O título não pode ficar vazio.", vm.estado.value.editandoCena?.erro)
    }

    @Test
    fun `LV6 a recusa do servidor fica no dialogo, que continua aberto`() = runTest {
        val repositorio = SugestoesFalso().also { it.resultadoDeEditar = ResultadoDaChamada.Falha("Sem conexão.") }
        val vm = vm(repositorio)
        vm.abrirEdicaoDaCena(8, "A espada", null)

        vm.salvarEdicaoDaCena("Novo", ""); advanceUntilIdle()

        assertEquals("Sem conexão.", vm.estado.value.editandoCena?.erro)
        assertEquals(false, vm.estado.value.editandoCena?.salvando)
    }

    @Test
    fun `LV6 cancelar fecha sem gravar`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.abrirEdicaoDaCena(8, "A espada", null)

        vm.fecharEdicaoDaCena()

        assertNull(vm.estado.value.editandoCena)
        assertTrue(repositorio.edicoesPedidas.isEmpty())
    }
}
