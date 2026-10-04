package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.CapituloComElementos
import com.allan.imagineer.rede.ElementoDoCapitulo
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.ImagemCandidata
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.analise.ServicoDeAnalises
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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

private fun item(id: Int, nome: String, tipo: String = "CRIATURA", vararg imagens: Int) =
    ElementoDoCapitulo(id, nome, tipo, imagens.map { ImagemCandidata(it) })

private val capitulo1 = CapituloComElementos(10, 1, "O começo", listOf(item(1, "Foxen", "CRIATURA", 100), item(2, "Prato", "OBJETO")))
private val capitulo2 = CapituloComElementos(11, 2, null, listOf(item(1, "Foxen", "CRIATURA", 200, 201), item(3, "Árvore", "AMBIENTE")))

/** A regra pura do seletor por capítulo (VM1, VM2). */
class OrganizarPorCapituloTest {

    @Test
    fun `VM2 este capitulo vem primeiro, os outros na ordem do livro`() {
        val secoes = organizarPorCapitulo(listOf(capitulo1, capitulo2), capituloAtualId = 11, elementosDoLivro = emptyList())

        assertEquals(listOf("Capítulo 2 (este capítulo)", "Capítulo 1 · O começo"), secoes.map { it.titulo })
        assertEquals(listOf(true, false), secoes.map { it.ehAtual })
    }

    @Test
    fun `VM2 o mesmo elemento aparece em cada capitulo com as imagens dele ali`() {
        val secoes = organizarPorCapitulo(listOf(capitulo1, capitulo2), capituloAtualId = 10, elementosDoLivro = emptyList())

        val foxen = secoes.flatMap { it.elementos }.filter { it.nome == "Foxen" }
        assertEquals(listOf(listOf(100), listOf(200, 201)), foxen.map { e -> e.imagens.map { it.id } })
    }

    @Test
    fun `VM5 filtra por tipo, com a chave de outros tipos, e pela busca sem ligar para acento`() {
        val so = organizarPorCapitulo(listOf(capitulo1, capitulo2), 10, emptyList(), tipoDaSugestao = "AMBIENTE")
        assertEquals(listOf("Árvore"), so.flatMap { it.elementos }.map { it.nome })

        val todos = organizarPorCapitulo(listOf(capitulo1, capitulo2), 10, emptyList(), tipoDaSugestao = "AMBIENTE", incluirOutrosTipos = true)
        assertTrue(todos.flatMap { it.elementos }.map { it.nome }.containsAll(listOf("Foxen", "Prato", "Árvore")))

        val busca = organizarPorCapitulo(listOf(capitulo1, capitulo2), 10, emptyList(), busca = "arvore", incluirOutrosTipos = true)
        assertEquals(listOf("Árvore"), busca.flatMap { it.elementos }.map { it.nome })
    }

    @Test
    fun `VM3 apenasElementoId deixa so o elemento escolhido`() {
        val secoes = organizarPorCapitulo(listOf(capitulo1, capitulo2), 10, emptyList(), apenasElementoId = 1)

        assertEquals(listOf(1), secoes.flatMap { it.elementos }.map { it.elemento_id }.distinct())
        assertEquals(2, secoes.size)
    }

    @Test
    fun `VM2 elemento do livro sem capitulo nenhum cai na secao Sem capitulo e secao vazia some`() {
        val soltos = listOf(ElementoDoLivro(id = 9, tipo = "OBJETO", nome = "Mapa"), ElementoDoLivro(id = 1, tipo = "CRIATURA", nome = "Foxen"))

        val secoes = organizarPorCapitulo(listOf(capitulo1), 10, soltos, tipoDaSugestao = "OBJETO")

        assertEquals(listOf("Capítulo 1 · O começo (este capítulo)", "Sem capítulo"), secoes.map { it.titulo })
        assertEquals(listOf("Mapa"), secoes.last().elementos.map { it.nome }) // o Foxen já aparece no capítulo 1, não duplica
    }
}

/** "Usar imagem existente" e a lista por capítulo no ViewModel (VM3, VM4). */
@OptIn(ExperimentalCoroutinesApi::class)
class ImagemExistenteNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val jon = elemento(id = 1, nome = "Jon", elementoId = 3, estadoId = 9)
    private val sugestoes = com.allan.imagineer.rede.SugestoesDeCapitulo(gerado_em = "2026-10-03T10:00:00", elementos = listOf(jon))

    private fun vm(elementos: ElementosFalso, repositorio: SugestoesFalso = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes))): PainelDeIaViewModel {
        val prompts = PromptsFalso()
        return PainelDeIaViewModel(
            5, repositorio, elementos, prompts,
            ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
        ).also { it.definirLivro(1) }
    }

    @Test
    fun `VM3 com o frame existente so aponta a canonica, sem criar retrato nem gastar IA`() = runTest {
        val elementos = ElementosFalso().also { it.porCapitulo = ResultadoDaChamada.Sucesso(listOf(capitulo1)) }
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes))
        val vm = vm(elementos, repositorio)

        vm.abrirImagemExistente(jon, frameId = 80); advanceUntilIdle()
        assertEquals(CargaPorCapitulo.Pronta(listOf(capitulo1)), vm.estado.value.usandoImagemExistente?.carga)
        vm.usarImagemExistente(100); advanceUntilIdle()

        assertEquals(listOf(80 to 100), elementos.canonicasPedidas)
        assertTrue(repositorio.retratosPedidos.isEmpty())
        assertNull(vm.estado.value.usandoImagemExistente) // fechou
        assertTrue(vm.estado.value.versaoDosFrames >= 1) // o capítulo relê os artefatos
    }

    @Test
    fun `VM5 a imagem usada, que e de outro frame, aparece como miniatura canonica do frame`() = runTest {
        val elementos = ElementosFalso().also { it.porCapitulo = ResultadoDaChamada.Sucesso(listOf(capitulo1)) }
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes)).also { it.canonicaDoFrame = 100 }
        val vm = vm(elementos, repositorio)

        vm.abrirImagemExistente(jon, frameId = 80); advanceUntilIdle()
        vm.usarImagemExistente(100); advanceUntilIdle()

        assertEquals(100, vm.estado.value.canonicasDosFrames[80])
        // Os prompts deste frame não têm essa imagem (ela é de outro frame): a miniatura vem da canônica.
        val miniaturas = imagensDoFrameComACanonica(emptyList(), vm.estado.value.canonicasDosFrames[80])
        assertEquals(listOf(100), miniaturas.map { it.id })
        assertTrue(miniaturas.single().canonica)
    }

    @Test
    fun `VM5 ao abrir o frame a canonica que ja estava la tambem e lida`() = runTest {
        val elementos = ElementosFalso()
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes)).also { it.canonicaDoFrame = 555 }
        val vm = vm(elementos, repositorio)

        vm.carregarPrompts(80); advanceUntilIdle()

        assertEquals(555, vm.estado.value.canonicasDosFrames[80])
    }

    @Test
    fun `VM4 sem frame cria o retrato do elemento e depois aponta a imagem`() = runTest {
        val elementos = ElementosFalso().also { it.porCapitulo = ResultadoDaChamada.Sucesso(listOf(capitulo1)) }
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes))
        val vm = vm(elementos, repositorio)

        vm.abrirImagemExistente(jon, frameId = null); advanceUntilIdle()
        vm.usarImagemExistente(100); advanceUntilIdle()

        assertEquals(listOf(9), repositorio.retratosPedidos) // o retrato com o estado vigente
        assertEquals(1, elementos.canonicasPedidas.size)
        assertEquals(100, elementos.canonicasPedidas.single().second)
        assertNull(vm.estado.value.usandoImagemExistente)
    }

    @Test
    fun `VM3 a recusa do servidor fica no proprio seletor, que continua aberto`() = runTest {
        val elementos = ElementosFalso().also {
            it.porCapitulo = ResultadoDaChamada.Sucesso(listOf(capitulo1))
            it.respostaADefinirCanonica = ResultadoDaChamada.Falha("Essa imagem não é de um prompt deste frame.")
        }
        val vm = vm(elementos)

        vm.abrirImagemExistente(jon, frameId = 80); advanceUntilIdle()
        vm.usarImagemExistente(100); advanceUntilIdle()

        val uso = vm.estado.value.usandoImagemExistente
        assertNotNull(uso)
        assertEquals("Essa imagem não é de um prompt deste frame.", uso!!.erro)
        assertEquals(false, uso.aplicando)
    }

    @Test
    fun `VM2 o livro por capitulo e lido uma vez e reaproveitado`() = runTest {
        val elementos = ElementosFalso().also { it.porCapitulo = ResultadoDaChamada.Sucesso(listOf(capitulo1)) }
        val vm = vm(elementos)

        vm.abrirImagemExistente(jon, 80); advanceUntilIdle()
        vm.fecharImagemExistente()
        vm.abrirImagemExistente(jon, 80); advanceUntilIdle()

        assertEquals(1, elementos.leiturasPorCapitulo)
    }

    @Test
    fun `VM2 falha ao ler o livro por capitulo mostra o motivo no seletor`() = runTest {
        val elementos = ElementosFalso().also { it.porCapitulo = ResultadoDaChamada.Falha("Sem conexão.") }
        val vm = vm(elementos)

        vm.abrirImagemExistente(jon, 80); advanceUntilIdle()

        assertEquals(CargaPorCapitulo.Erro("Sem conexão."), vm.estado.value.usandoImagemExistente?.carga)
    }

    @Test
    fun `VM1 a lista de vincular traz os elementos por capitulo`() = runTest {
        val elementos = ElementosFalso().also {
            it.lista = ResultadoDaChamada.Sucesso(listOf(ElementoDoLivro(id = 1, tipo = "CRIATURA", nome = "Foxen")))
            it.porCapitulo = ResultadoDaChamada.Sucesso(listOf(capitulo1))
        }
        val vm = vm(elementos)

        vm.executar(AcaoDoElemento.VINCULAR, jon); advanceUntilIdle()

        val lista = (vm.estado.value.dialogo as com.allan.imagineer.telas.capitulo.painel.DialogoDeElemento.Vinculando).lista
        assertEquals(listOf(capitulo1), (lista as ListaParaVincular.Pronta).capitulos)
    }
}
