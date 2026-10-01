package com.allan.imagineer.telas.elementos

import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.DetalheDoElemento
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.EstadoDoElemento
import com.allan.imagineer.rede.IdentidadeDoCapitulo
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.capitulo.painel.ElementosFalso
import com.allan.imagineer.telas.capitulo.painel.TIPOS_DE_ELEMENTO
import com.allan.imagineer.telas.capitulo.painel.fichaDeJon
import com.allan.imagineer.telas.livro.LivrosFalso
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegrasDosElementosTest {

    @Test
    fun `E19 o capitulo e dito pelo titulo, e sem titulo por Capitulo e a posicao`() {
        assertEquals("Capítulo VI", rotuloDoCapitulo("Capítulo VI", 8))
        assertEquals("Capítulo 8", rotuloDoCapitulo(null, 8))
        assertEquals("Capítulo 8", rotuloDoCapitulo("   ", 8)) // título em branco vale como sem título
        assertEquals("Capítulo", rotuloDoCapitulo(null, null))
    }

    private val lista = listOf(
        ElementoDoLivro(1, "AMBIENTE", "Winterfell"),
        ElementoDoLivro(2, "PERSONAGEM", "Sextus Hospius"),
        ElementoDoLivro(3, "PERSONAGEM", "Ádria"),
        ElementoDoLivro(4, "VEICULO", "Navio Negro"),
    )

    @Test
    fun `E20 a lista vem em ordem alfabetica, sem ligar para acento`() {
        assertEquals(listOf(3, 4, 2, 1), filtrarElementos(lista, "", null).map { it.id })
    }

    @Test
    fun `E20 filtra por tipo e por busca, juntos`() {
        assertEquals(listOf(3, 2), filtrarElementos(lista, "", "PERSONAGEM").map { it.id })
        assertEquals(listOf(2), filtrarElementos(lista, "HOSPIUS", null).map { it.id })
        assertEquals(listOf(3), filtrarElementos(lista, "adria", "PERSONAGEM").map { it.id })
        assertTrue(filtrarElementos(lista, "adria", "AMBIENTE").isEmpty())
    }

    @Test
    fun `E20 os filtros so oferecem tipos que existem, na ordem canonica`() {
        assertEquals(listOf("PERSONAGEM", "AMBIENTE", "VEICULO"), tiposPresentes(lista, TIPOS_DE_ELEMENTO))
        // um tipo novo no servidor não some da tela
        assertEquals(listOf("PERSONAGEM", "PLANETA"), tiposPresentes(listOf(ElementoDoLivro(1, "PLANETA", "X"), ElementoDoLivro(2, "PERSONAGEM", "Y")), TIPOS_DE_ELEMENTO))
    }

    @Test
    fun `E21 os acrescimos de identidade e os estados vem em ordem narrativa, pelo titulo`() {
        val ficha = DetalheDoElemento(
            id = 1, tipo = "PERSONAGEM", nome = "Jon",
            estados = listOf(
                EstadoDoElemento(id = 2, capitulo_id = 14, ordem_do_capitulo = 4, titulo_do_capitulo = "Capítulo IV", descricao = "depois"),
                EstadoDoElemento(id = 1, capitulo_id = 11, ordem_do_capitulo = 1, descricao = "antes"),
            ),
            historico_identidade = listOf(
                IdentidadeDoCapitulo(id = 9, capitulo_id = 14, ordem_do_capitulo = 4, titulo_do_capitulo = "Capítulo IV", descricao = "mais tarde"),
                IdentidadeDoCapitulo(id = 8, capitulo_id = 12, ordem_do_capitulo = 2, descricao = "cedo"),
            ),
        )

        assertEquals(listOf(1, 2), estadosEmOrdem(ficha).map { it.id })
        assertEquals(
            listOf(AcrescimoDeIdentidade(8, "Capítulo 2", "cedo"), AcrescimoDeIdentidade(9, "Capítulo IV", "mais tarde")),
            acrescimosDeIdentidade(ficha),
        )
    }

    @Test
    fun `E39 a lista de capitulos vem na ordem do livro e a busca e pelo titulo`() {
        val capitulos = listOf(
            CapituloResumo(id = 3, ordem = 6, titulo = "Capítulo VI", ignorado = false, tamanho_do_texto = 1),
            CapituloResumo(id = 1, ordem = 1, titulo = null, ignorado = false, tamanho_do_texto = 1),
            CapituloResumo(id = 2, ordem = 2, titulo = "CLASSIFICAÇÕES", ignorado = true, tamanho_do_texto = 1),
        )

        assertEquals(listOf(1, 2, 3), filtrarCapitulos(capitulos, "").map { it.id })
        assertEquals(listOf(2), filtrarCapitulos(capitulos, "classificacoes").map { it.id }) // sem acento nem caixa
        assertEquals(listOf(3), filtrarCapitulos(capitulos, "vi").map { it.id })
        assertEquals(listOf(1), filtrarCapitulos(capitulos, "capitulo 1").map { it.id }) // sem título: "Capítulo 1"
        assertEquals("Capítulo VI", rotuloParaEscolherCapitulo("Capítulo VI", 6, arquivado = false))
        assertEquals("Capítulo 2 (arquivado)", rotuloParaEscolherCapitulo(null, 2, arquivado = true))
    }

    @Test
    fun `E21 sabe se ja ha estado num capitulo`() {
        val ficha = fichaDeJon() // estados nos capítulos 11 e 14

        assertTrue(temEstadoNoCapitulo(ficha, 11))
        assertFalse(temEstadoNoCapitulo(ficha, 99))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListaDeElementosViewModelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val elementos = listOf(ElementoDoLivro(1, "PERSONAGEM", "Jon"), ElementoDoLivro(2, "AMBIENTE", "Muralha"))

    @Test
    fun `carrega a lista do livro`() = runTest {
        val falso = ElementosFalso().apply { lista = ResultadoDaChamada.Sucesso(elementos) }
        val vm = ListaDeElementosViewModel(4, falso)
        assertEquals(CargaDaLista.Carregando, vm.estado.value.carga)

        vm.carregar()
        advanceUntilIdle()

        assertEquals(CargaDaLista.Pronta(elementos), vm.estado.value.carga)
        assertEquals(listOf("listar(4)"), falso.chamadas)
    }

    @Test
    fun `falha na primeira leitura mostra o motivo e deixa tentar de novo`() = runTest {
        val falso = ElementosFalso().apply { lista = ResultadoDaChamada.Falha("Não consegui falar com o servidor.") }
        val vm = ListaDeElementosViewModel(4, falso)

        vm.carregar()
        advanceUntilIdle()
        assertEquals(CargaDaLista.Erro("Não consegui falar com o servidor."), vm.estado.value.carga)

        falso.lista = ResultadoDaChamada.Sucesso(elementos)
        vm.carregar()
        advanceUntilIdle()
        assertTrue(vm.estado.value.carga is CargaDaLista.Pronta)
    }

    @Test
    fun `reler ao voltar da ficha nao pisca e, se falhar, mantem a lista`() = runTest {
        val falso = ElementosFalso().apply { lista = ResultadoDaChamada.Sucesso(elementos) }
        val vm = ListaDeElementosViewModel(4, falso)
        vm.carregar()
        advanceUntilIdle()

        falso.lista = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        vm.carregar()
        // durante a releitura a lista continua na tela
        assertEquals(CargaDaLista.Pronta(elementos), vm.estado.value.carga)
        advanceUntilIdle()

        assertEquals(CargaDaLista.Pronta(elementos), vm.estado.value.carga)
    }

    @Test
    fun `reler mostra o que mudou`() = runTest {
        val falso = ElementosFalso().apply { lista = ResultadoDaChamada.Sucesso(elementos) }
        val vm = ListaDeElementosViewModel(4, falso)
        vm.carregar()
        advanceUntilIdle()

        falso.lista = ResultadoDaChamada.Sucesso(elementos.take(1))
        vm.carregar()
        advanceUntilIdle()

        assertEquals(CargaDaLista.Pronta(elementos.take(1)), vm.estado.value.carga)
    }

    @Test
    fun `busca e tipo so filtram - nada vai ao servidor por causa deles`() = runTest {
        val falso = ElementosFalso().apply { lista = ResultadoDaChamada.Sucesso(elementos) }
        val vm = ListaDeElementosViewModel(4, falso)
        vm.carregar()
        advanceUntilIdle()
        falso.chamadas.clear()

        vm.buscar("jon")
        vm.filtrarPorTipo("PERSONAGEM")
        advanceUntilIdle()

        assertEquals("jon", vm.estado.value.busca)
        assertEquals("PERSONAGEM", vm.estado.value.tipo)
        assertTrue(falso.chamadas.isEmpty())
    }

    @Test
    fun `tocar no mesmo tipo de novo limpa o filtro`() = runTest {
        val vm = ListaDeElementosViewModel(4, ElementosFalso())

        vm.filtrarPorTipo("PERSONAGEM")
        vm.filtrarPorTipo("PERSONAGEM")

        assertNull(vm.estado.value.tipo)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FichaDoElementoViewModelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    /** O livro 4 de Jon, com três capítulos (o "Capítulo VI" tem a posição 6). */
    private fun livroDeJon() = LivroDetalhe(
        id = 4, titulo = "Livro", nome_arquivo = "l.epub", data_importacao = "2026-09-30T00:00:00",
        total_de_capitulos = 3, capitulos_ignorados = 1,
        capitulos = listOf(
            CapituloResumo(id = 11, ordem = 1, titulo = "Capítulo I", ignorado = false, tamanho_do_texto = 5),
            CapituloResumo(id = 14, ordem = 4, titulo = "Capítulo IV", ignorado = true, tamanho_do_texto = 5),
            CapituloResumo(id = 25, ordem = 6, titulo = "Capítulo VI", ignorado = false, tamanho_do_texto = 5),
        ),
    )

    /** Jon: elemento 30, estados 70 (capítulo 11) e 71 (capítulo 14); a ficha veio do capítulo [doCapitulo]. */
    private fun TestScope.abrir(
        doCapitulo: Int? = null,
        falso: ElementosFalso = ElementosFalso(),
        livros: LivrosFalso = LivrosFalso(ResultadoDaChamada.Sucesso(livroDeJon())),
    ): Pair<FichaDoElementoViewModel, ElementosFalso> {
        val vm = FichaDoElementoViewModel(30, doCapitulo, falso, livros)
        vm.carregar()
        advanceUntilIdle()
        falso.chamadas.clear()
        return vm to falso
    }

    @Test
    fun `carrega a ficha uma vez so - girar o tablet nao repete o pedido`() = runTest {
        val falso = ElementosFalso()
        val vm = FichaDoElementoViewModel(30, null, falso, LivrosFalso(ResultadoDaChamada.Sucesso(livroDeJon())))

        vm.carregar()
        advanceUntilIdle()
        vm.carregar()
        advanceUntilIdle()

        assertEquals(listOf("detalhar(30)"), falso.chamadas)
        assertEquals(CargaDaFicha.Pronta(fichaDeJon()), vm.estado.value.carga)
    }

    @Test
    fun `falha ao carregar mostra o motivo e deixa tentar de novo`() = runTest {
        val falso = ElementosFalso().apply { ficha = ResultadoDaChamada.Falha("Não consegui falar com o servidor.") }
        val vm = FichaDoElementoViewModel(30, null, falso, LivrosFalso(ResultadoDaChamada.Sucesso(livroDeJon())))
        vm.carregar()
        advanceUntilIdle()
        assertEquals(CargaDaFicha.Erro("Não consegui falar com o servidor."), vm.estado.value.carga)

        falso.ficha = ResultadoDaChamada.Sucesso(fichaDeJon())
        vm.tentarDeNovo()
        advanceUntilIdle()

        assertTrue(vm.estado.value.carga is CargaDaFicha.Pronta)
    }

    @Test
    fun `editar o elemento manda so o que mudou e rele a ficha`() = runTest {
        val (vm, falso) = abrir()
        vm.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ELEMENTO)

        vm.salvarElemento("PERSONAGEM", " Jon Snow ", "Bastardo de Winterfell.")
        advanceUntilIdle()

        assertEquals(listOf("ajustarElemento(30,null,Jon Snow,null)", "detalhar(30)"), falso.chamadas)
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `editar sem mudar nada so fecha o dialogo`() = runTest {
        val (vm, falso) = abrir()
        vm.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ELEMENTO)

        vm.salvarElemento("PERSONAGEM", "Jon", "Bastardo de Winterfell.")
        advanceUntilIdle()

        assertTrue(falso.chamadas.isEmpty())
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `o nome nao pode ficar vazio`() = runTest {
        val (vm, falso) = abrir()
        vm.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ELEMENTO)

        vm.salvarElemento("PERSONAGEM", "  ", "x")
        advanceUntilIdle()

        assertTrue(falso.chamadas.isEmpty())
        assertEquals(ERRO_FICHA_NOME_VAZIO, vm.estado.value.dialogo?.erro)
    }

    @Test
    fun `falha ao editar mantem o dialogo aberto com o motivo da API`() = runTest {
        val falso = ElementosFalso().apply {
            respostaAoAjustarElemento = ResultadoDaChamada.Falha("Já existe um elemento chamado 'Jon Snow'.", 409)
        }
        val (vm, _) = abrir(falso = falso)
        vm.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ELEMENTO)

        vm.salvarElemento("PERSONAGEM", "Jon Snow", "Bastardo de Winterfell.")
        advanceUntilIdle()

        val dialogo = vm.estado.value.dialogo!!
        assertEquals("Já existe um elemento chamado 'Jon Snow'.", dialogo.erro)
        assertFalse(dialogo.salvando)
        assertFalse(falso.chamadas.contains("detalhar(30)")) // nada foi relido
    }

    @Test
    fun `editar um estado ajusta so o texto`() = runTest {
        val (vm, falso) = abrir()
        vm.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ESTADO, estadoId = 71)

        vm.salvarEstado(71, " capa rasgada ")
        advanceUntilIdle()

        assertEquals(listOf("ajustarEstado(71,capa rasgada)", "detalhar(30)"), falso.chamadas)
    }

    @Test
    fun `o estado nao pode ficar vazio e texto igual so fecha`() = runTest {
        val (vm, falso) = abrir()
        vm.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ESTADO, estadoId = 71)

        vm.salvarEstado(71, " ")
        advanceUntilIdle()
        assertEquals(ERRO_FICHA_ESTADO_VAZIO, vm.estado.value.dialogo?.erro)

        vm.salvarEstado(71, "armadura de couro") // igual ao atual
        advanceUntilIdle()
        assertNull(vm.estado.value.dialogo)
        assertTrue(falso.chamadas.isEmpty())
    }

    @Test
    fun `apagar um estado pede confirmacao e depois apaga`() = runTest {
        val (vm, falso) = abrir()

        vm.abrirDialogo(TipoDeDialogoDaFicha.APAGAR_ESTADO, estadoId = 70)
        assertTrue(falso.chamadas.isEmpty()) // abrir o aviso não apaga nada
        vm.confirmarApagarEstado()
        advanceUntilIdle()

        assertEquals(listOf("removerEstado(70)", "detalhar(30)"), falso.chamadas)
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `E34 vindo de um capitulo, adicionar estado ja tem o capitulo escolhido`() = runTest {
        val (vm, falso) = abrir(doCapitulo = 25)

        vm.abrirAdicao(TipoDeDialogoDaFicha.ADICIONAR_ESTADO, noCapituloDaSugestao = true)
        val dialogo = vm.estado.value.dialogo!!
        assertEquals(TipoDeDialogoDaFicha.ADICIONAR_ESTADO, dialogo.tipo)
        assertEquals(25, dialogo.capituloId)
        vm.adicionarEstado(" armadura ")
        advanceUntilIdle()

        assertEquals(listOf("criarEstado(30,25,armadura)", "detalhar(30)"), falso.chamadas)
    }

    @Test
    fun `E40 em outro capitulo, pede o capitulo primeiro, pelo titulo, e depois o texto`() = runTest {
        val (vm, falso) = abrir(doCapitulo = 25)

        vm.abrirAdicao(TipoDeDialogoDaFicha.ADICIONAR_ESTADO, noCapituloDaSugestao = false)
        assertEquals(TipoDeDialogoDaFicha.ESCOLHER_CAPITULO, vm.estado.value.dialogo?.tipo)
        assertEquals(CargaDosCapitulos.Carregando, vm.estado.value.capitulos)
        advanceUntilIdle()
        val capitulos = (vm.estado.value.capitulos as CargaDosCapitulos.Pronta).capitulos
        assertEquals(listOf("Capítulo I", "Capítulo IV", "Capítulo VI"), capitulos.map { it.titulo })

        vm.escolherCapitulo(14)
        assertEquals(TipoDeDialogoDaFicha.ADICIONAR_ESTADO, vm.estado.value.dialogo?.tipo)
        vm.adicionarEstado("capa rasgada")
        advanceUntilIdle()

        assertEquals(listOf("criarEstado(30,14,capa rasgada)", "detalhar(30)"), falso.chamadas)
    }

    @Test
    fun `E40 sem a ficha ter vindo de um capitulo, a escolha e sempre pedida`() = runTest {
        val (vm, _) = abrir(doCapitulo = null)

        vm.abrirAdicao(TipoDeDialogoDaFicha.ADICIONAR_ESTADO, noCapituloDaSugestao = true)

        assertEquals(TipoDeDialogoDaFicha.ESCOLHER_CAPITULO, vm.estado.value.dialogo?.tipo)
    }

    @Test
    fun `E39 falha ao listar os capitulos mostra o motivo e deixa tentar de novo`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Falha("Não consegui falar com o servidor."))
        val (vm, _) = abrir(livros = livros)

        vm.abrirAdicao(TipoDeDialogoDaFicha.ADICIONAR_ACRESCIMO, noCapituloDaSugestao = false)
        advanceUntilIdle()
        assertEquals(CargaDosCapitulos.Erro("Não consegui falar com o servidor."), vm.estado.value.capitulos)

        livros.resposta = ResultadoDaChamada.Sucesso(livroDeJon())
        vm.recarregarCapitulos()
        advanceUntilIdle()
        assertTrue(vm.estado.value.capitulos is CargaDosCapitulos.Pronta)
    }

    @Test
    fun `E39 escolher capitulo fora do passo certo nao faz nada`() = runTest {
        val (vm, _) = abrir()

        vm.escolherCapitulo(14)

        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `adicionar estado vazio e recusado`() = runTest {
        val (vm, falso) = abrir(doCapitulo = 25)
        vm.abrirAdicao(TipoDeDialogoDaFicha.ADICIONAR_ESTADO, noCapituloDaSugestao = true)

        vm.adicionarEstado("  ")
        advanceUntilIdle()

        assertTrue(falso.chamadas.isEmpty())
        assertEquals(ERRO_FICHA_ESTADO_VAZIO, vm.estado.value.dialogo?.erro)
    }

    // --- acréscimos de identidade (E38, E39, E41) --------------------------------------------------

    @Test
    fun `E39 acrescentar identidade num capitulo escolhido manda capitulo e texto`() = runTest {
        val (vm, falso) = abrir()
        vm.abrirAdicao(TipoDeDialogoDaFicha.ADICIONAR_ACRESCIMO, noCapituloDaSugestao = true)
        advanceUntilIdle()

        vm.escolherCapitulo(25)
        vm.adicionarAcrescimo(" Agora lidera a Patrulha. ")
        advanceUntilIdle()

        assertEquals(listOf("criarAcrescimo(30,25,Agora lidera a Patrulha.)", "detalhar(30)"), falso.chamadas)
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `E39 vindo de um capitulo, o acrescimo ja tem o capitulo escolhido`() = runTest {
        val (vm, falso) = abrir(doCapitulo = 25)

        vm.abrirAdicao(TipoDeDialogoDaFicha.ADICIONAR_ACRESCIMO, noCapituloDaSugestao = true)
        vm.adicionarAcrescimo("Agora lidera.")
        advanceUntilIdle()

        assertEquals(listOf("criarAcrescimo(30,25,Agora lidera.)", "detalhar(30)"), falso.chamadas)
    }

    @Test
    fun `E41 acrescimo vazio e recusado, sem chamar o servidor`() = runTest {
        val (vm, falso) = abrir(doCapitulo = 25)
        vm.abrirAdicao(TipoDeDialogoDaFicha.ADICIONAR_ACRESCIMO, noCapituloDaSugestao = true)

        vm.adicionarAcrescimo("   ")
        advanceUntilIdle()

        assertTrue(falso.chamadas.isEmpty())
        assertEquals(ERRO_FICHA_ACRESCIMO_VAZIO, vm.estado.value.dialogo?.erro)
    }

    @Test
    fun `E41 falha do servidor ao acrescentar mostra a mensagem no dialogo`() = runTest {
        val falso = ElementosFalso().apply { respostaDosAcrescimos = ResultadoDaChamada.Falha("O servidor respondeu com erro 500.", 500) }
        val (vm, _) = abrir(doCapitulo = 25, falso = falso)
        vm.abrirAdicao(TipoDeDialogoDaFicha.ADICIONAR_ACRESCIMO, noCapituloDaSugestao = true)

        vm.adicionarAcrescimo("Agora lidera.")
        advanceUntilIdle()

        assertEquals("O servidor respondeu com erro 500.", vm.estado.value.dialogo?.erro)
        assertFalse(vm.estado.value.dialogo!!.salvando)
    }

    @Test
    fun `E38 corrigir um acrescimo manda so o texto novo`() = runTest {
        val (vm, falso) = abrir()
        vm.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ACRESCIMO, acrescimoId = 5)

        vm.salvarAcrescimo(5, " Lorde Comandante da Patrulha. ")
        advanceUntilIdle()

        assertEquals(listOf("ajustarAcrescimo(5,Lorde Comandante da Patrulha.)", "detalhar(30)"), falso.chamadas)
    }

    @Test
    fun `E38 corrigir sem mudar o texto so fecha, e vazio e recusado`() = runTest {
        val (vm, falso) = abrir()
        vm.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ACRESCIMO, acrescimoId = 5)

        vm.salvarAcrescimo(5, "  ")
        assertEquals(ERRO_FICHA_ACRESCIMO_VAZIO, vm.estado.value.dialogo?.erro)
        vm.salvarAcrescimo(5, "Agora é Lorde Comandante.") // igual ao atual
        advanceUntilIdle()

        assertNull(vm.estado.value.dialogo)
        assertTrue(falso.chamadas.isEmpty())
    }

    @Test
    fun `E38 apagar um acrescimo pede confirmacao e depois apaga`() = runTest {
        val (vm, falso) = abrir()

        vm.abrirDialogo(TipoDeDialogoDaFicha.APAGAR_ACRESCIMO, acrescimoId = 5)
        assertTrue(falso.chamadas.isEmpty())
        vm.confirmarApagarAcrescimo()
        advanceUntilIdle()

        assertEquals(listOf("removerAcrescimo(5)", "detalhar(30)"), falso.chamadas)
    }

    @Test
    fun `apagar o elemento pede confirmacao, apaga e avisa a tela para voltar`() = runTest {
        val (vm, falso) = abrir()
        vm.abrirDialogo(TipoDeDialogoDaFicha.APAGAR_ELEMENTO)
        assertTrue(falso.chamadas.isEmpty())

        vm.confirmarApagarElemento()
        advanceUntilIdle()

        assertEquals(listOf("excluir(30)"), falso.chamadas)
        assertTrue(vm.estado.value.apagado)
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `falha ao apagar o elemento mantem tudo e mostra o motivo`() = runTest {
        val falso = ElementosFalso().apply { respostaAoExcluir = ResultadoDaChamada.Falha("O servidor respondeu com erro 500.", 500) }
        val (vm, _) = abrir(falso = falso)
        vm.abrirDialogo(TipoDeDialogoDaFicha.APAGAR_ELEMENTO)

        vm.confirmarApagarElemento()
        advanceUntilIdle()

        assertFalse(vm.estado.value.apagado)
        assertEquals("O servidor respondeu com erro 500.", vm.estado.value.dialogo?.erro)
    }

    @Test
    fun `cancelar fecha qualquer dialogo sem chamar nada`() = runTest {
        val (vm, falso) = abrir()
        vm.abrirDialogo(TipoDeDialogoDaFicha.APAGAR_ELEMENTO)

        vm.cancelarDialogo()

        assertNull(vm.estado.value.dialogo)
        assertTrue(falso.chamadas.isEmpty())
    }

    // --- mesclar (E35, E36) ---------------------------------------------------------------------

    private val outros = listOf(
        ElementoDoLivro(30, "PERSONAGEM", "Jon"), // este mesmo: nao pode aparecer como destino
        ElementoDoLivro(31, "VEICULO", "Jon"),
        ElementoDoLivro(32, "AMBIENTE", "Muralha"),
    )

    @Test
    fun `E35 a lista de mesclar traz os outros elementos do livro da ficha, sem o proprio`() = runTest {
        val falso = ElementosFalso().apply { lista = ResultadoDaChamada.Sucesso(outros) }
        val (vm, _) = abrir(falso = falso)

        vm.abrirMesclagem()
        assertEquals(TipoDeDialogoDaFicha.ESCOLHER_PARA_MESCLAR, vm.estado.value.dialogo?.tipo)
        assertEquals(CargaDaLista.Carregando, vm.estado.value.candidatos)
        advanceUntilIdle()

        assertEquals(listOf("listar(4)"), falso.chamadas) // o livro 4 vem da própria ficha
        assertEquals(outros.drop(1), (vm.estado.value.candidatos as CargaDaLista.Pronta).elementos)
    }

    @Test
    fun `E35 falha ao listar mostra o motivo`() = runTest {
        val falso = ElementosFalso().apply { lista = ResultadoDaChamada.Falha("Não consegui falar com o servidor.") }
        val (vm, _) = abrir(falso = falso)

        vm.abrirMesclagem()
        advanceUntilIdle()

        assertEquals(CargaDaLista.Erro("Não consegui falar com o servidor."), vm.estado.value.candidatos)
    }

    @Test
    fun `E35 escolher o destino pede confirmacao e ainda nao junta nada`() = runTest {
        val falso = ElementosFalso().apply { lista = ResultadoDaChamada.Sucesso(outros) }
        val (vm, _) = abrir(falso = falso)
        vm.abrirMesclagem()
        advanceUntilIdle()
        falso.chamadas.clear()

        vm.escolherDestinoDaMesclagem(31)

        val dialogo = vm.estado.value.dialogo!!
        assertEquals(TipoDeDialogoDaFicha.CONFIRMAR_MESCLAR, dialogo.tipo)
        assertEquals(31, dialogo.destinoId)
        assertTrue(falso.chamadas.isEmpty())
    }

    @Test
    fun `E35 confirmar junta este elemento ao destino e a tela volta`() = runTest {
        val falso = ElementosFalso().apply { lista = ResultadoDaChamada.Sucesso(outros) }
        val (vm, _) = abrir(falso = falso)
        vm.abrirMesclagem()
        advanceUntilIdle()
        vm.escolherDestinoDaMesclagem(31)
        falso.chamadas.clear()

        vm.confirmarMesclagem()
        advanceUntilIdle()

        assertEquals(listOf("mesclar(30,31)"), falso.chamadas)
        assertTrue(vm.estado.value.apagado) // este elemento deixou de existir
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `E35 falha ao juntar mantem o dialogo e nao volta`() = runTest {
        val falso = ElementosFalso().apply {
            lista = ResultadoDaChamada.Sucesso(outros)
            respostaAoMesclar = ResultadoDaChamada.Falha("Os elementos são de livros diferentes.", 422)
        }
        val (vm, _) = abrir(falso = falso)
        vm.abrirMesclagem()
        advanceUntilIdle()
        vm.escolherDestinoDaMesclagem(31)

        vm.confirmarMesclagem()
        advanceUntilIdle()

        assertFalse(vm.estado.value.apagado)
        assertEquals("Os elementos são de livros diferentes.", vm.estado.value.dialogo?.erro)
    }

    @Test
    fun `E35 escolher destino fora do passo certo nao faz nada`() = runTest {
        val (vm, _) = abrir()

        vm.escolherDestinoDaMesclagem(31)

        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `E36 o 409 ao editar marca o conflito, para o dialogo apontar a saida`() = runTest {
        val falso = ElementosFalso().apply {
            respostaAoAjustarElemento = ResultadoDaChamada.Falha("Já existe um elemento do tipo VEICULO chamado 'Jon' (id 31).", 409)
        }
        val (vm, _) = abrir(falso = falso)
        vm.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ELEMENTO)

        vm.salvarElemento("VEICULO", "Jon", "Bastardo de Winterfell.")
        advanceUntilIdle()

        val dialogo = vm.estado.value.dialogo!!
        assertTrue(dialogo.conflito)
        assertEquals("Já existe um elemento do tipo VEICULO chamado 'Jon' (id 31).", dialogo.erro)
    }

    @Test
    fun `E36 outra falha qualquer nao marca conflito`() = runTest {
        val falso = ElementosFalso().apply {
            respostaAoAjustarElemento = ResultadoDaChamada.Falha("O servidor respondeu com erro 500.", 500)
        }
        val (vm, _) = abrir(falso = falso)
        vm.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ELEMENTO)

        vm.salvarElemento("VEICULO", "Jon", "Bastardo de Winterfell.")
        advanceUntilIdle()

        assertFalse(vm.estado.value.dialogo!!.conflito)
    }
}
