package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.DetalheDoElemento
import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.ElementoCriado
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.EstadoDoElemento
import com.allan.imagineer.rede.IdentidadeDoCapitulo
import com.allan.imagineer.rede.RepositorioDeElementos
import com.allan.imagineer.rede.ParticipanteSugerido
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.CompletableDeferred
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

/**
 * Elementos falsos: **registram cada chamada** (na ordem) e devolvem o que o teste combinar.
 * Nenhuma delas gasta IA (E10).
 */
internal class ElementosFalso : RepositorioDeElementos {
    val chamadas = mutableListOf<String>()
    var listagens = 0
    var detalhes = 0
    var lista: ResultadoDaChamada<List<ElementoDoLivro>> = ResultadoDaChamada.Sucesso(emptyList())
    var ficha: ResultadoDaChamada<DetalheDoElemento> = ResultadoDaChamada.Sucesso(fichaDeJon())
    var criacao: ResultadoDaChamada<ElementoCriado> = ResultadoDaChamada.Sucesso(ElementoCriado(9, "PERSONAGEM", "Jon"))
    var resposta: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)
    var respostaAoAjustarElemento: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)
    var respostaAoRemoverEstado: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)
    var trava: CompletableDeferred<Unit>? = null

    override suspend fun listar(livroId: Int): ResultadoDaChamada<List<ElementoDoLivro>> {
        listagens++
        chamadas += "listar($livroId)"
        return lista
    }

    override suspend fun detalhar(elementoId: Int): ResultadoDaChamada<DetalheDoElemento> {
        detalhes++
        chamadas += "detalhar($elementoId)"
        return ficha
    }

    override suspend fun criar(
        livroId: Int,
        tipo: String,
        nome: String,
        descricao: String?,
        sugestaoId: Int,
    ): ResultadoDaChamada<ElementoCriado> {
        chamadas += "criar($livroId,$tipo,$nome,$descricao,$sugestaoId)"
        trava?.await()
        return criacao
    }

    /** Uma resposta própria para uma sugestão (o que o "Confirmar todos" precisa para testar falhas isoladas). */
    val respostasPorSugestao = mutableMapOf<Int, ResultadoDaChamada<Unit>>()

    override suspend fun registrarEstado(elementoId: Int, sugestaoId: Int): ResultadoDaChamada<Unit> {
        chamadas += "registrarEstado($elementoId,$sugestaoId)"
        trava?.await()
        return respostasPorSugestao[sugestaoId] ?: resposta
    }

    override suspend fun ajustarCasamento(sugestaoId: Int, elementoId: Int?): ResultadoDaChamada<Unit> {
        chamadas += "ajustarCasamento($sugestaoId,$elementoId)"
        trava?.await()
        return respostasPorSugestao[sugestaoId] ?: resposta
    }

    override suspend fun descartar(sugestaoId: Int, descartada: Boolean): ResultadoDaChamada<Unit> {
        chamadas += "descartar($sugestaoId,$descartada)"
        trava?.await()
        return resposta
    }

    /** A galeria que a ficha lê (FI1) e quantas vezes foi lida; **não** entra em [chamadas], para os testes antigos não mudarem. */
    var galeriaDoElemento: ResultadoDaChamada<com.allan.imagineer.rede.GaleriaDoElemento> =
        ResultadoDaChamada.Sucesso(com.allan.imagineer.rede.GaleriaDoElemento())
    var leiturasDaGaleria = 0

    /** As âncoras padrão pedidas por `ajustarElemento` (FI6). */
    val ancorasPedidas = mutableListOf<Int>()

    /** As escolhas de imagem canônica pedidas (frame, imagem), e a resposta a dar (CAN3). */
    val canonicasPedidas = mutableListOf<Pair<Int, Int?>>()
    var respostaADefinirCanonica: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)

    override suspend fun definirImagemCanonica(frameId: Int, imagemId: Int?): ResultadoDaChamada<Unit> {
        canonicasPedidas += frameId to imagemId
        return respostaADefinirCanonica
    }

    override suspend fun galeria(elementoId: Int): ResultadoDaChamada<com.allan.imagineer.rede.GaleriaDoElemento> {
        leiturasDaGaleria++
        return galeriaDoElemento
    }

    override suspend fun ajustarElemento(
        elementoId: Int,
        tipo: String?,
        nome: String?,
        identidade: String?,
        ancoraPadraoId: Int?,
    ): ResultadoDaChamada<Unit> {
        if (ancoraPadraoId != null) {
            ancorasPedidas += ancoraPadraoId
        } else {
            chamadas += "ajustarElemento($elementoId,$tipo,$nome,$identidade)"
        }
        return respostaAoAjustarElemento
    }

    override suspend fun criarEstado(elementoId: Int, capituloId: Int, descricao: String): ResultadoDaChamada<Unit> {
        chamadas += "criarEstado($elementoId,$capituloId,$descricao)"
        return resposta
    }

    override suspend fun ajustarEstado(estadoId: Int, descricao: String): ResultadoDaChamada<Unit> {
        chamadas += "ajustarEstado($estadoId,$descricao)"
        return resposta
    }

    override suspend fun removerEstado(estadoId: Int): ResultadoDaChamada<Unit> {
        chamadas += "removerEstado($estadoId)"
        return respostaAoRemoverEstado
    }

    var respostaAoExcluir: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)
    var respostaAoMesclar: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)
    var respostaDosAcrescimos: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)

    override suspend fun criarAcrescimo(elementoId: Int, capituloId: Int, descricao: String): ResultadoDaChamada<Unit> {
        chamadas += "criarAcrescimo($elementoId,$capituloId,$descricao)"
        return respostaDosAcrescimos
    }

    override suspend fun ajustarAcrescimo(acrescimoId: Int, descricao: String): ResultadoDaChamada<Unit> {
        chamadas += "ajustarAcrescimo($acrescimoId,$descricao)"
        return respostaDosAcrescimos
    }

    override suspend fun removerAcrescimo(acrescimoId: Int): ResultadoDaChamada<Unit> {
        chamadas += "removerAcrescimo($acrescimoId)"
        return respostaDosAcrescimos
    }

    override suspend fun mesclar(origemId: Int, destinoId: Int): ResultadoDaChamada<Unit> {
        chamadas += "mesclar($origemId,$destinoId)"
        return respostaAoMesclar
    }

    override suspend fun excluir(elementoId: Int): ResultadoDaChamada<Unit> {
        chamadas += "excluir($elementoId)"
        return respostaAoExcluir
    }
}

/** Jon (elemento 30): identidade inicial, um acréscimo no capítulo 3 e dois estados (capítulos 1 e 4). */
internal fun fichaDeJon() = DetalheDoElemento(
    id = 30,
    livro_id = 4,
    tipo = "PERSONAGEM",
    nome = "Jon",
    descricao = "Bastardo de Winterfell.",
    estados = listOf(
        EstadoDoElemento(id = 70, capitulo_id = 11, ordem_do_capitulo = 1, descricao = "manto preto"),
        EstadoDoElemento(id = 71, capitulo_id = 14, ordem_do_capitulo = 4, descricao = "armadura de couro"),
    ),
    historico_identidade = listOf(
        IdentidadeDoCapitulo(id = 5, capitulo_id = 13, ordem_do_capitulo = 3, descricao = "Agora é Lorde Comandante."),
    ),
)

class SituacoesDoElementoTest {

    @Test
    fun `E1 cada situacao vem so dos campos da API`() {
        assertEquals(SituacaoDoElemento.NOVA, situacaoDoElemento(elemento()))
        assertEquals(
            SituacaoDoElemento.CASADA_AUTOMATICAMENTE,
            situacaoDoElemento(elemento(elementoId = 3, automatico = true)),
        )
        // Automático ganha de "sem estado": primeiro se confere o casamento.
        assertEquals(
            SituacaoDoElemento.CASADA_AUTOMATICAMENTE,
            situacaoDoElemento(elemento(elementoId = 3, automatico = true, estadoId = 7)),
        )
        assertEquals(SituacaoDoElemento.CASADA_SEM_ESTADO, situacaoDoElemento(elemento(elementoId = 3)))
        assertEquals(SituacaoDoElemento.CONFIRMADA, situacaoDoElemento(elemento(elementoId = 3, estadoId = 7)))
    }

    @Test
    fun `E12 casada com estado vigente de outro capitulo ja e confirmada`() {
        assertEquals(
            SituacaoDoElemento.CONFIRMADA,
            situacaoDoElemento(elemento(elementoId = 3, vigenteDoCapitulo = 1)),
        )
    }

    @Test
    fun `E1 e E22 as acoes de cada situacao - confirmar nunca e permanente`() {
        assertEquals(
            listOf(AcaoDoElemento.CRIAR, AcaoDoElemento.VINCULAR, AcaoDoElemento.DESCARTAR),
            acoesDoElemento(SituacaoDoElemento.NOVA),
        )
        assertEquals(
            listOf(AcaoDoElemento.CONFIRMAR, AcaoDoElemento.ABRIR_FICHA, AcaoDoElemento.TROCAR, AcaoDoElemento.DESFAZER),
            acoesDoElemento(SituacaoDoElemento.CASADA_AUTOMATICAMENTE),
        )
        assertEquals(
            listOf(AcaoDoElemento.REGISTRAR_ESTADO, AcaoDoElemento.ABRIR_FICHA, AcaoDoElemento.TROCAR, AcaoDoElemento.DESFAZER),
            acoesDoElemento(SituacaoDoElemento.CASADA_SEM_ESTADO),
        )
        // Mesmo confirmada, dá para abrir a ficha, trocar e desfazer.
        assertEquals(
            listOf(AcaoDoElemento.ABRIR_FICHA, AcaoDoElemento.TROCAR, AcaoDoElemento.DESFAZER),
            acoesDoElemento(SituacaoDoElemento.CONFIRMADA),
        )
    }

    @Test
    fun `E23 a lista de vincular e so do mesmo tipo, a menos que se peca os outros`() {
        val todos = listOf(
            ElementoDoLivro(1, "AMBIENTE", "Winterfell"),
            ElementoDoLivro(2, "PERSONAGEM", "Sextus Hospius"),
            ElementoDoLivro(3, "PERSONAGEM", "Ádria"),
            ElementoDoLivro(4, "OBJETO", "Espada"),
        )

        assertEquals(listOf(3, 2), filtrarParaVincular(todos, "PERSONAGEM", "").map { it.id })
        assertEquals(listOf(3, 2, 4, 1), filtrarParaVincular(todos, "PERSONAGEM", "", incluirOutrosTipos = true).map { it.id })
        assertEquals(listOf(1), filtrarParaVincular(todos, "AMBIENTE", "").map { it.id }) // veículo, lugar... também evoluem
        assertEquals(listOf(2), filtrarParaVincular(todos, "PERSONAGEM", "HOSPIUS").map { it.id })
        assertEquals(listOf(3), filtrarParaVincular(todos, "PERSONAGEM", "adria").map { it.id })
        assertTrue(filtrarParaVincular(todos, "PERSONAGEM", "zzz").isEmpty())
        assertEquals(listOf(4), filtrarParaVincular(todos, "PERSONAGEM", "espada", incluirOutrosTipos = true).map { it.id })
    }

    @Test
    fun `E13 a identidade vigente soma a inicial com o que cada capitulo acrescentou, em ordem`() {
        assertEquals("Bastardo de Winterfell. Agora é Lorde Comandante.", identidadeVigente(fichaDeJon()))
        assertNull(identidadeVigente(DetalheDoElemento(id = 1, tipo = "OBJETO", nome = "Espada")))
        assertEquals(
            "Só o acréscimo.",
            identidadeVigente(
                DetalheDoElemento(id = 1, tipo = "OBJETO", nome = "Espada", historico_identidade = listOf(IdentidadeDoCapitulo(id = 1, capitulo_id = 2, ordem_do_capitulo = 2, descricao = "Só o acréscimo."))),
            ),
        )
    }

    @Test
    fun `E24 cada elemento cai em um filtro, e os contadores somam`() {
        val lista = listOf(
            elemento(id = 1), // nova
            elemento(id = 2, elementoId = 3, automatico = true), // casada - confira
            elemento(id = 3, elementoId = 3), // casada sem estado
            elemento(id = 4, elementoId = 3, estadoId = 9), // confirmada
            elemento(id = 5, vigenteDoCapitulo = 1, elementoId = 3), // confirmada (estado vigente de outro capitulo)
            elemento(id = 6, descartada = true),
        )

        assertEquals(
            mapOf(FiltroDoPainel.PENDENTES to 3, FiltroDoPainel.CONFIRMADOS to 2, FiltroDoPainel.DESCARTADOS to 1),
            contagemPorFiltro(lista),
        )
        assertEquals(FiltroDoPainel.DESCARTADOS, filtroDoElemento(elemento(elementoId = 3, estadoId = 9, descartada = true)))
    }

    @Test
    fun `E29 nos pendentes o que pede mais acao vem primeiro, e a ordem da IA desempata`() {
        val lista = listOf(
            elemento(id = 1, elementoId = 3), // sem estado
            elemento(id = 2), // nova
            elemento(id = 3, elementoId = 3, automatico = true), // casada - confira
            elemento(id = 4), // nova
            elemento(id = 5, elementoId = 3, estadoId = 9), // confirmada: fora dos pendentes
        )

        assertEquals(listOf(2, 4, 3, 1), elementosDoFiltro(lista, FiltroDoPainel.PENDENTES).map { it.id })
        assertEquals(listOf(5), elementosDoFiltro(lista, FiltroDoPainel.CONFIRMADOS).map { it.id })
        assertTrue(elementosDoFiltro(lista, FiltroDoPainel.DESCARTADOS).isEmpty())
    }

    @Test
    fun `E33 um cartao que muda de filtro deixa de ser o mesmo e volta compacto`() {
        val pendente = elemento(id = 7, elementoId = 3, automatico = true)
        val confirmado = elemento(id = 7, elementoId = 3, estadoId = 9) // o mesmo elemento, depois de confirmado
        val descartado = elemento(id = 7, descartada = true)

        assertEquals("PENDENTES:7", chaveDoCartao(pendente))
        assertEquals("CONFIRMADOS:7", chaveDoCartao(confirmado))
        assertEquals("DESCARTADOS:7", chaveDoCartao(descartado))
        // Abrir o pendente não abre o mesmo elemento já confirmado.
        assertFalse(chaveDoCartao(confirmado) in setOf(chaveDoCartao(pendente)))
        // E ids diferentes no mesmo filtro têm chaves diferentes.
        assertFalse(chaveDoCartao(elemento(id = 8)) == chaveDoCartao(elemento(id = 7)))
    }

    @Test
    fun `E24 a etiqueta do cartao fechado e uma so`() {
        assertEquals("Nova", etiquetaDaSituacao(elemento()))
        assertEquals("Casada — confira", etiquetaDaSituacao(elemento(elementoId = 3, automatico = true)))
        assertEquals("Casada, sem estado", etiquetaDaSituacao(elemento(elementoId = 3)))
        assertEquals("Confirmada", etiquetaDaSituacao(elemento(elementoId = 3, estadoId = 9)))
        assertEquals("Descartada", etiquetaDaSituacao(elemento(descartada = true)))
    }

    private fun cena(id: Int, titulo: String, vararg participantes: Int, descartada: Boolean = false) =
        CenaSugerida(
            id = id,
            titulo = titulo,
            participantes = participantes.map { ParticipanteSugerido(it, "PERSONAGEM", "P$it") },
            descartada = descartada,
        )

    @Test
    fun `E26 cada elemento sabe em quais cenas aparece, ignorando as descartadas`() {
        val sugestoes = SugestoesDeCapitulo(
            gerado_em = "x",
            elementos = listOf(elemento(id = 1), elemento(id = 2), elemento(id = 3)),
            cenas = listOf(
                cena(10, "A chegada", 1, 2),
                cena(11, "O duelo", 1),
                cena(12, "Cena descartada", 3, descartada = true),
            ),
        )

        val mapa = cenasDoElemento(sugestoes)

        assertEquals(listOf("A chegada", "O duelo"), mapa[1])
        assertEquals(listOf("A chegada"), mapa[2])
        assertNull(mapa[3]) // só aparecia numa cena descartada
        assertEquals("Aparece em 2 cenas", descreverCenasDoElemento(mapa[1].orEmpty()))
        assertEquals("Aparece em 1 cena", descreverCenasDoElemento(mapa[2].orEmpty()))
        assertNull(descreverCenasDoElemento(emptyList()))
    }

}

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmarElementosViewModelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val nova = elemento(id = 1, nome = "Hospius")
    private val automatica = elemento(id = 2, nome = "Jon", elementoId = 30, automatico = true, estadoId = 70)
    private val semEstado = elemento(id = 3, nome = "Arya", elementoId = 31)
    private val confirmada = elemento(id = 4, nome = "Jon", elementoId = 30, estadoId = 71)
    private val usaOVigente = elemento(id = 5, nome = "Jon", elementoId = 30, vigenteDoCapitulo = 1)

    private fun sugestoes(vararg elementos: ElementoSugerido) = SugestoesFalso(
        leitura = ResultadoDaChamada.Sucesso(
            SugestoesDeCapitulo(gerado_em = "2026-09-30T20:40:38", elementos = elementos.toList()),
        ),
    )

    /** O painel aberto e lido, com o livro 4 informado (o capítulo é o 5). */
    private fun TestScope.abrir(
        sug: SugestoesFalso,
        els: ElementosFalso = ElementosFalso(),
    ): Pair<PainelDeIaViewModel, ElementosFalso> {
        val vm = PainelDeIaViewModel(5, sug, els)
        vm.definirLivro(4)
        vm.aoAbrirPainel()
        advanceUntilIdle()
        els.chamadas.clear()
        return vm to els
    }

    // ------------------------------------------------------------------ //
    // Criar e vincular (E2, E3, E5, E23)
    // ------------------------------------------------------------------ //

    @Test
    fun `E2 criar envia livro, tipo, nome, descricao e o id da sugestao, e depois rele`() = runTest {
        val sug = sugestoes(nova)
        val (vm, els) = abrir(sug)
        val leiturasAntes = sug.leituras

        vm.executar(AcaoDoElemento.CRIAR, nova)
        assertTrue(vm.estado.value.dialogo is DialogoDeElemento.Criando)
        vm.confirmarCriacao("PERSONAGEM", " Sextus Hospius ", " um romano ")
        advanceUntilIdle()

        assertEquals(listOf("criar(4,PERSONAGEM,Sextus Hospius,um romano,1)"), els.chamadas)
        assertNull(vm.estado.value.dialogo)
        assertEquals(leiturasAntes + 1, sug.leituras) // E8: relê depois
    }

    @Test
    fun `E2 sem nome nao chama o servidor e avisa no dialogo`() = runTest {
        val (vm, els) = abrir(sugestoes(nova))
        vm.executar(AcaoDoElemento.CRIAR, nova)

        vm.confirmarCriacao("PERSONAGEM", "   ", "")
        advanceUntilIdle()

        assertTrue(els.chamadas.isEmpty())
        assertEquals(ERRO_NOME_VAZIO, (vm.estado.value.dialogo as DialogoDeElemento.Criando).erro)
    }

    @Test
    fun `E2 o 409 mostra a mensagem da API e oferece vincular a um existente`() = runTest {
        val els = ElementosFalso().apply {
            criacao = ResultadoDaChamada.Falha("Já existe um elemento chamado 'Jon' (id 9).", 409)
        }
        val (vm, _) = abrir(sugestoes(nova), els)
        vm.executar(AcaoDoElemento.CRIAR, nova)

        vm.confirmarCriacao("PERSONAGEM", "Jon", "")
        advanceUntilIdle()

        val dialogo = vm.estado.value.dialogo as DialogoDeElemento.Criando
        assertEquals("Já existe um elemento chamado 'Jon' (id 9).", dialogo.erro)
        assertTrue(dialogo.conflito)
        assertFalse(dialogo.salvando)

        vm.trocarCriacaoPorVinculo()
        assertTrue(vm.estado.value.dialogo is DialogoDeElemento.Vinculando)
    }

    @Test
    fun `E2 uma falha qualquer deixa tudo como estava, sem repetir sozinho`() = runTest {
        val els = ElementosFalso().apply { criacao = ResultadoDaChamada.Falha("Não consegui falar com o servidor.") }
        val sug = sugestoes(nova)
        val (vm, _) = abrir(sug, els)
        vm.executar(AcaoDoElemento.CRIAR, nova)
        val leituras = sug.leituras

        vm.confirmarCriacao("PERSONAGEM", "Jon", "")
        advanceUntilIdle()

        assertEquals(1, els.chamadas.count { it.startsWith("criar") })
        assertEquals(leituras, sug.leituras) // nada foi relido
        assertFalse((vm.estado.value.dialogo as DialogoDeElemento.Criando).conflito)
    }

    @Test
    fun `sem o livro informado nao ha como criar`() = runTest {
        val sug = sugestoes(nova)
        val vm = PainelDeIaViewModel(5, sug, ElementosFalso())
        vm.aoAbrirPainel()
        advanceUntilIdle()
        vm.executar(AcaoDoElemento.CRIAR, nova)

        vm.confirmarCriacao("PERSONAGEM", "Jon", "")
        advanceUntilIdle()

        assertEquals(ERRO_LIVRO_NAO_CARREGADO, (vm.estado.value.dialogo as DialogoDeElemento.Criando).erro)
    }

    @Test
    fun `E3 vincular busca os elementos uma vez so e escolher registra o estado`() = runTest {
        val els = ElementosFalso().apply {
            lista = ResultadoDaChamada.Sucesso(listOf(ElementoDoLivro(30, "PERSONAGEM", "Sextus Hospius")))
        }
        val (vm, _) = abrir(sugestoes(nova), els)

        vm.executar(AcaoDoElemento.VINCULAR, nova)
        assertTrue((vm.estado.value.dialogo as DialogoDeElemento.Vinculando).lista is ListaParaVincular.Carregando)
        advanceUntilIdle()
        assertTrue((vm.estado.value.dialogo as DialogoDeElemento.Vinculando).lista is ListaParaVincular.Pronta)

        vm.escolherElemento(30)
        advanceUntilIdle()
        assertEquals(listOf("listar(4)", "registrarEstado(30,1)"), els.chamadas)
        assertNull(vm.estado.value.dialogo)

        // Reabrir não busca de novo... a não ser que algo tenha mudado (a escolha invalida a lista).
        vm.executar(AcaoDoElemento.VINCULAR, nova)
        advanceUntilIdle()
        assertEquals(2, els.listagens)
    }

    @Test
    fun `E3 falha ao listar oferece tentar de novo`() = runTest {
        val els = ElementosFalso().apply { lista = ResultadoDaChamada.Falha("Não consegui falar com o servidor.") }
        val (vm, _) = abrir(sugestoes(nova), els)

        vm.executar(AcaoDoElemento.VINCULAR, nova)
        advanceUntilIdle()
        assertTrue((vm.estado.value.dialogo as DialogoDeElemento.Vinculando).lista is ListaParaVincular.Erro)

        els.lista = ResultadoDaChamada.Sucesso(listOf(ElementoDoLivro(30, "PERSONAGEM", "Jon")))
        vm.recarregarLista()
        advanceUntilIdle()
        assertTrue((vm.estado.value.dialogo as DialogoDeElemento.Vinculando).lista is ListaParaVincular.Pronta)
    }

    @Test
    fun `E3 falha ao vincular mantem o dialogo aberto com a mensagem`() = runTest {
        val els = ElementosFalso().apply {
            lista = ResultadoDaChamada.Sucesso(listOf(ElementoDoLivro(30, "PERSONAGEM", "Jon")))
            resposta = ResultadoDaChamada.Falha("O servidor respondeu com erro 500.", 500)
        }
        val (vm, _) = abrir(sugestoes(nova), els)
        vm.executar(AcaoDoElemento.VINCULAR, nova)
        advanceUntilIdle()

        vm.escolherElemento(30)
        advanceUntilIdle()

        val dialogo = vm.estado.value.dialogo as DialogoDeElemento.Vinculando
        assertEquals("O servidor respondeu com erro 500.", dialogo.erro)
        assertFalse(dialogo.salvando)
    }

    // ------------------------------------------------------------------ //
    // Confirmar, trocar, desfazer, registrar (E4 a E6, E15)
    // ------------------------------------------------------------------ //

    @Test
    fun `E4 confirmar manda o MESMO elemento_id`() = runTest {
        val (vm, els) = abrir(sugestoes(automatica))

        vm.executar(AcaoDoElemento.CONFIRMAR, automatica)
        advanceUntilIdle()

        assertEquals(listOf("ajustarCasamento(2,30)"), els.chamadas)
    }

    @Test
    fun `E15 desfazer sem estado neste capitulo desliga direto, mandando null`() = runTest {
        val sem = elemento(id = 2, elementoId = 30, automatico = true)
        val (vm, els) = abrir(sugestoes(sem))

        vm.executar(AcaoDoElemento.DESFAZER, sem)
        advanceUntilIdle()

        assertEquals(listOf("ajustarCasamento(2,null)"), els.chamadas)
        assertNull(vm.estado.value.dialogo)
        assertNull(vm.estado.value.mensagens[2])
    }

    @Test
    fun `E15 desfazer com estado neste capitulo pergunta antes, e nao chama nada ainda`() = runTest {
        val (vm, els) = abrir(sugestoes(automatica))

        vm.executar(AcaoDoElemento.DESFAZER, automatica)
        advanceUntilIdle()

        assertTrue(els.chamadas.isEmpty())
        val dialogo = vm.estado.value.dialogo as DialogoDeElemento.Desfazendo
        assertFalse(dialogo.apagarEstado) // nunca apaga por padrão
    }

    @Test
    fun `E15 desfazer sem apagar o estado avisa que ele ficou`() = runTest {
        val (vm, els) = abrir(sugestoes(automatica))
        vm.executar(AcaoDoElemento.DESFAZER, automatica)

        vm.confirmarDesfazer()
        advanceUntilIdle()

        assertEquals(listOf("ajustarCasamento(2,null)"), els.chamadas)
        assertEquals(AVISO_ESTADO_ANTERIOR_FICOU, vm.estado.value.mensagens[2]?.texto)
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `E15 desfazer e apagar o estado chama as duas, nessa ordem`() = runTest {
        val (vm, els) = abrir(sugestoes(automatica))
        vm.executar(AcaoDoElemento.DESFAZER, automatica)
        vm.alternarApagarEstado()

        vm.confirmarDesfazer()
        advanceUntilIdle()

        assertEquals(listOf("ajustarCasamento(2,null)", "removerEstado(70)"), els.chamadas)
        assertNull(vm.estado.value.mensagens[2]) // deu tudo certo: nada a avisar
    }

    @Test
    fun `E15 se nao conseguir apagar o estado, diz que desfez mas o estado ficou`() = runTest {
        val els = ElementosFalso().apply {
            respostaAoRemoverEstado = ResultadoDaChamada.Falha("O servidor respondeu com erro 500.", 500)
        }
        val (vm, _) = abrir(sugestoes(automatica), els)
        vm.executar(AcaoDoElemento.DESFAZER, automatica)
        vm.alternarApagarEstado()

        vm.confirmarDesfazer()
        advanceUntilIdle()

        val mensagem = vm.estado.value.mensagens[2]!!
        assertTrue(mensagem.ehErro)
        assertTrue(mensagem.texto.startsWith("Desfeito, mas não consegui apagar o estado"))
    }

    @Test
    fun `E15 se nao conseguir desligar, nada mais e feito e o dialogo mostra o motivo`() = runTest {
        val els = ElementosFalso().apply { resposta = ResultadoDaChamada.Falha("Não consegui falar com o servidor.") }
        val (vm, _) = abrir(sugestoes(automatica), els)
        vm.executar(AcaoDoElemento.DESFAZER, automatica)
        vm.alternarApagarEstado()

        vm.confirmarDesfazer()
        advanceUntilIdle()

        assertEquals(listOf("ajustarCasamento(2,null)"), els.chamadas) // não tentou apagar
        assertEquals("Não consegui falar com o servidor.", (vm.estado.value.dialogo as DialogoDeElemento.Desfazendo).erro)
    }

    @Test
    fun `E5 trocar so corrige o casamento, sem criar estado`() = runTest {
        val els = ElementosFalso().apply {
            lista = ResultadoDaChamada.Sucesso(listOf(ElementoDoLivro(31, "PERSONAGEM", "Outro")))
        }
        val (vm, _) = abrir(sugestoes(automatica), els)

        vm.executar(AcaoDoElemento.TROCAR, automatica)
        advanceUntilIdle()
        vm.escolherElemento(31)
        advanceUntilIdle()

        assertEquals(listOf("listar(4)", "ajustarCasamento(2,31)"), els.chamadas)
        assertEquals(AVISO_ESTADO_ANTERIOR_FICOU, vm.estado.value.mensagens[2]?.texto) // havia estado
    }

    @Test
    fun `E6 registrar estado usa o elemento ja ligado e avisa que e rascunho`() = runTest {
        val (vm, els) = abrir(sugestoes(semEstado))

        vm.executar(AcaoDoElemento.REGISTRAR_ESTADO, semEstado)
        advanceUntilIdle()

        assertEquals(listOf("registrarEstado(31,3)"), els.chamadas)
        assertEquals(AVISO_ESTADO_RASCUNHO, vm.estado.value.mensagens[3]?.texto)
    }

    @Test
    fun `E9 uma acao por vez em cada elemento, e o recado de erro aparece no cartao`() = runTest {
        val els = ElementosFalso().apply { trava = CompletableDeferred() }
        val (vm, _) = abrir(sugestoes(semEstado), els)

        vm.executar(AcaoDoElemento.REGISTRAR_ESTADO, semEstado)
        advanceUntilIdle()
        assertTrue(3 in vm.estado.value.ocupados)
        vm.executar(AcaoDoElemento.REGISTRAR_ESTADO, semEstado) // segundo toque, ignorado
        advanceUntilIdle()
        assertEquals(1, els.chamadas.size)

        els.resposta = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        els.trava!!.complete(Unit)
        advanceUntilIdle()

        assertFalse(3 in vm.estado.value.ocupados)
        assertEquals(MensagemDoElemento("Não consegui falar com o servidor.", true), vm.estado.value.mensagens[3])
    }

    @Test
    fun `E8 a releitura nao passa por Lendo e mostra o que o servidor devolveu`() = runTest {
        val sug = sugestoes(semEstado)
        val (vm, _) = abrir(sug)
        sug.leitura = ResultadoDaChamada.Sucesso(
            SugestoesDeCapitulo(
                gerado_em = "2026-09-30T20:40:38",
                elementos = listOf(elemento(id = 3, nome = "Arya", elementoId = 31, estadoId = 88)),
            ),
        )

        vm.executar(AcaoDoElemento.REGISTRAR_ESTADO, semEstado)
        advanceUntilIdle()

        val pronto = vm.estado.value.conteudo as ConteudoDoPainel.Pronto
        assertEquals(88, pronto.sugestoes.elementos.single().estado_id)
    }

    // ------------------------------------------------------------------ //
    // Descartar e restaurar (E16)
    // ------------------------------------------------------------------ //

    @Test
    fun `E16 descartar chama o servidor, sem dialogo, e rele`() = runTest {
        val sug = sugestoes(nova)
        val (vm, els) = abrir(sug)
        val leituras = sug.leituras

        vm.executar(AcaoDoElemento.DESCARTAR, nova)
        assertNull(vm.estado.value.dialogo) // imediato, sem pedir confirmação
        advanceUntilIdle()

        assertEquals(listOf("descartar(1,true)"), els.chamadas)
        assertEquals(leituras + 1, sug.leituras)
    }

    @Test
    fun `E16 restaurar manda descartada false`() = runTest {
        val descartada = elemento(id = 1, descartada = true)
        val (vm, els) = abrir(sugestoes(descartada))

        vm.restaurar(descartada)
        advanceUntilIdle()

        assertEquals(listOf("descartar(1,false)"), els.chamadas)
    }

    @Test
    fun `E16 falha ao descartar mostra o motivo no cartao e nada muda`() = runTest {
        val els = ElementosFalso().apply { resposta = ResultadoDaChamada.Falha("Não consegui falar com o servidor.") }
        val sug = sugestoes(nova)
        val (vm, _) = abrir(sug, els)
        val leituras = sug.leituras

        vm.executar(AcaoDoElemento.DESCARTAR, nova)
        advanceUntilIdle()

        assertEquals(MensagemDoElemento("Não consegui falar com o servidor.", true), vm.estado.value.mensagens[1])
        assertEquals(leituras, sug.leituras)
    }

    // ------------------------------------------------------------------ //
    // Descartar quem aparece em cenas (E27), filtros (E24), voltar da ficha (E31)
    // ------------------------------------------------------------------ //

    private fun comCena(vararg elementos: ElementoSugerido) = SugestoesFalso(
        leitura = ResultadoDaChamada.Sucesso(
            SugestoesDeCapitulo(
                gerado_em = "x",
                elementos = elementos.toList(),
                cenas = listOf(
                    CenaSugerida(id = 10, titulo = "A chegada", participantes = listOf(ParticipanteSugerido(1, "PERSONAGEM", "Hospius"))),
                    CenaSugerida(id = 11, titulo = "O duelo", participantes = listOf(ParticipanteSugerido(1, "PERSONAGEM", "Hospius"))),
                ),
            ),
        ),
    )

    @Test
    fun `E27 descartar quem aparece em cenas abre o aviso e nao chama o servidor ainda`() = runTest {
        val (vm, els) = abrir(comCena(nova))

        vm.executar(AcaoDoElemento.DESCARTAR, nova)
        advanceUntilIdle()

        val dialogo = vm.estado.value.dialogo as DialogoDeElemento.DescartandoEmCenas
        assertEquals(listOf("A chegada", "O duelo"), dialogo.cenas)
        assertTrue(els.chamadas.isEmpty())
    }

    @Test
    fun `E27 confirmar o aviso descarta`() = runTest {
        val (vm, els) = abrir(comCena(nova))
        vm.executar(AcaoDoElemento.DESCARTAR, nova)

        vm.confirmarDescarte()
        advanceUntilIdle()

        assertEquals(listOf("descartar(1,true)"), els.chamadas)
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `E27 cancelar o aviso nao descarta nada`() = runTest {
        val (vm, els) = abrir(comCena(nova))
        vm.executar(AcaoDoElemento.DESCARTAR, nova)

        vm.cancelarDialogo()
        advanceUntilIdle()

        assertTrue(els.chamadas.isEmpty())
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `E27 quem nao aparece em cena descarta direto, sem aviso`() = runTest {
        val outra = elemento(id = 7, nome = "Sozinho")
        val (vm, els) = abrir(comCena(nova, outra))

        vm.executar(AcaoDoElemento.DESCARTAR, outra)
        advanceUntilIdle()

        assertEquals(listOf("descartar(7,true)"), els.chamadas)
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `E24 o filtro comeca nos pendentes e pode ser trocado`() = runTest {
        val (vm, _) = abrir(sugestoes(nova))

        assertEquals(FiltroDoPainel.PENDENTES, vm.estado.value.filtro)
        vm.escolherFiltro(FiltroDoPainel.CONFIRMADOS)
        assertEquals(FiltroDoPainel.CONFIRMADOS, vm.estado.value.filtro)
    }

    @Test
    fun `E22 abrir a ficha nao e coisa do viewmodel - nada e chamado`() = runTest {
        val (vm, els) = abrir(sugestoes(confirmada))

        vm.executar(AcaoDoElemento.ABRIR_FICHA, confirmada)
        advanceUntilIdle()

        assertTrue(els.chamadas.isEmpty())
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `E31 ao voltar da ficha o painel rele no lugar, sem passar por Lendo`() = runTest {
        val sug = sugestoes(confirmada)
        val (vm, _) = abrir(sug)
        val leituras = sug.leituras
        sug.leitura = ResultadoDaChamada.Sucesso(
            SugestoesDeCapitulo(gerado_em = "x", elementos = listOf(elemento(id = 4, nome = "Jon Snow", elementoId = 30, estadoId = 71))),
        )

        vm.aoVoltarDaFicha()
        advanceUntilIdle()

        assertEquals(leituras + 1, sug.leituras)
        val pronto = vm.estado.value.conteudo as ConteudoDoPainel.Pronto
        assertEquals("Jon Snow", pronto.sugestoes.elementos.single().nome)
    }

    @Test
    fun `E31 voltar da ficha sem o painel ter sido lido nao faz nada`() = runTest {
        val sug = sugestoes(confirmada)
        val vm = PainelDeIaViewModel(5, sug, ElementosFalso())

        vm.aoVoltarDaFicha()
        advanceUntilIdle()

        assertEquals(0, sug.leituras)
    }

    @Test
    fun `trocar esquece a lista de elementos - a proxima abertura busca de novo`() = runTest {
        val els = ElementosFalso().apply {
            lista = ResultadoDaChamada.Sucesso(listOf(ElementoDoLivro(31, "PERSONAGEM", "Outro")))
        }
        val (vm, _) = abrir(sugestoes(automatica), els)
        vm.executar(AcaoDoElemento.TROCAR, automatica)
        advanceUntilIdle()
        vm.cancelarDialogo()

        vm.aoVoltarDaFicha() // o que foi editado na ficha pode ter mudado nomes
        vm.executar(AcaoDoElemento.TROCAR, automatica)
        advanceUntilIdle()

        assertEquals(2, els.listagens)
    }

    @Test
    fun `E10 nenhuma acao de elemento chama a analise por IA`() = runTest {
        val sug = sugestoes(nova, automatica, semEstado, confirmada)
        val (vm, _) = abrir(sug)

        vm.executar(AcaoDoElemento.CONFIRMAR, automatica)
        vm.executar(AcaoDoElemento.REGISTRAR_ESTADO, semEstado)
        vm.executar(AcaoDoElemento.DESCARTAR, nova)
        vm.executar(AcaoDoElemento.ABRIR_FICHA, confirmada)
        advanceUntilIdle()

        assertTrue(sug.analises.isEmpty())
    }
}
