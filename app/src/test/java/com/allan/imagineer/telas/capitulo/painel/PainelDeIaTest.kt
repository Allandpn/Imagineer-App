package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.FrameCriado
import com.allan.imagineer.rede.EstadoVigente
import com.allan.imagineer.rede.ParticipanteSugerido
import com.allan.imagineer.rede.RepositorioDeSugestoes
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ---------------------------------------------------------------------- //
// Falso e construtores
// ---------------------------------------------------------------------- //

internal fun elemento(
    id: Int = 1,
    tipo: String = "PERSONAGEM",
    nome: String = "Jon",
    elementoId: Int? = null,
    automatico: Boolean = false,
    estadoId: Int? = null,
    manter: Boolean = false,
    /** Um estado vigente vindo de OUTRO capítulo (nenhum estado neste). */
    vigenteDoCapitulo: Int? = null,
    descartada: Boolean = false,
) = ElementoSugerido(
    id = id, tipo = tipo, nome = nome, elemento_id = elementoId,
    casamento_automatico = automatico, estado_id = estadoId, manter_estado_atual = manter,
    // O estado vigente é o deste capítulo quando há estado_id; senão, o de outro capítulo, se houver.
    estado_vigente = when {
        estadoId != null -> EstadoVigente(id = estadoId, capitulo_id = 1, ordem_do_capitulo = 1, descricao = "aparência deste capítulo")
        vigenteDoCapitulo != null -> EstadoVigente(id = 900 + vigenteDoCapitulo, capitulo_id = 1, ordem_do_capitulo = vigenteDoCapitulo, descricao = "aparência do capítulo $vigenteDoCapitulo")
        else -> null
    },
    descartada = descartada,
)

internal val nuncaAnalisado = SugestoesDeCapitulo(gerado_em = null, sugestoes_pendentes_anteriores = 2)
internal val analisado = SugestoesDeCapitulo(gerado_em = "2026-09-30T20:40:38", elementos = listOf(elemento()))
internal val analisadoSemNada = SugestoesDeCapitulo(gerado_em = "2026-09-30T20:40:38")

/**
 * Sugestões falsas. **Conta cada chamada ao `analisar`**, que é o único ponto que gasta IA:
 * várias regras do painel existem para garantir que ele só é chamado quando o usuário pede.
 */
internal class SugestoesFalso(
    var leitura: ResultadoDaChamada<SugestoesDeCapitulo> = ResultadoDaChamada.Sucesso(nuncaAnalisado),
    var analise: ResultadoDaChamada<SugestoesDeCapitulo> = ResultadoDaChamada.Sucesso(analisado),
) : RepositorioDeSugestoes {
    var leituras = 0
    val analises = mutableListOf<Boolean>() // o "forcar" de cada chamada
    val orientacoes = mutableListOf<String?>() // a orientação enviada em cada chamada (item 6.7, M1)

    /** As cenas descartadas (`true`) ou restauradas (`false`), na ordem: o que o painel pediu (C6). */
    val descartesDeCena = mutableListOf<Pair<Int, Boolean>>()
    var resultadoDoDescarteDeCena: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)

    /** As cenas que o painel pediu para confirmar (C5). */
    val confirmacoesDeCena = mutableListOf<Int>()
    var resultadoDaConfirmacaoDeCena: ResultadoDaChamada<FrameCriado> = ResultadoDaChamada.Sucesso(FrameCriado(id = 70, titulo = "Cena"))
    var travaDaConfirmacaoDeCena: CompletableDeferred<Unit>? = null

    /** Os estados dos quais o painel pediu um retrato, na ordem (N2). */
    val retratosPedidos = mutableListOf<Int>()
    var resultadoDoRetrato: ResultadoDaChamada<FrameCriado> = ResultadoDaChamada.Sucesso(FrameCriado(id = 80, titulo = "Retrato de Jon"))
    var travaDoRetrato: CompletableDeferred<Unit>? = null

    /** Os vinculados pedidos na criação de cada retrato (V4), na ordem das criações. */
    val vinculadosNaCriacao = mutableListOf<List<Int>>()

    override suspend fun criarRetrato(capituloId: Int, estadoId: Int, vinculadosIds: List<Int>): ResultadoDaChamada<FrameCriado> {
        retratosPedidos += estadoId
        vinculadosNaCriacao += vinculadosIds
        travaDoRetrato?.await()
        return resultadoDoRetrato
    }

    /** O que o servidor diz que o frame já tem (V4), e as substituições pedidas por `PUT`. */
    var vinculosNoServidor: ResultadoDaChamada<List<com.allan.imagineer.rede.VinculadoDoFrame>> = ResultadoDaChamada.Sucesso(emptyList())
    val leiturasDeVinculos = mutableListOf<Int>()
    val vinculosDefinidos = mutableListOf<Pair<Int, List<Int>>>()
    var resultadoDeDefinirVinculos: ResultadoDaChamada<List<com.allan.imagineer.rede.VinculadoDoFrame>>? = null

    /** As substituições de participantes pedidas por `PUT .../estados` (EV7). */
    val estadosDefinidos = mutableListOf<Pair<Int, List<Int>>>()
    var resultadoDeDefinirEstados: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)

    override suspend fun definirEstados(frameId: Int, estadosIds: List<Int>): ResultadoDaChamada<Unit> {
        estadosDefinidos += frameId to estadosIds
        return resultadoDeDefinirEstados
    }

    /** As posições de frame pedidas (frame, posição), os frames apagados e as respostas a dar. */
    val posicoesDeFramePedidas = mutableListOf<Pair<Int, Int?>>()
    val framesApagados = mutableListOf<Int>()
    var resultadoDePosicionarFrame: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)
    var resultadoDeApagarFrame: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)

    override suspend fun posicionarFrame(frameId: Int, posicao: Int?): ResultadoDaChamada<Unit> {
        posicoesDeFramePedidas += frameId to posicao
        return resultadoDePosicionarFrame
    }

    override suspend fun apagarFrame(frameId: Int): ResultadoDaChamada<Unit> {
        framesApagados += frameId
        return resultadoDeApagarFrame
    }

    /** As edições de cena pedidas: (sugestão ou frame, título, descrição); e as respostas a dar (LV6). */
    val edicoesPedidas = mutableListOf<Triple<String, String, String>>()
    var resultadoDeEditar: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)
    var textoDoFrameADar: ResultadoDaChamada<Pair<String, String>> = ResultadoDaChamada.Sucesso("Título lido" to "Descrição lida")

    override suspend fun editarCena(sugestaoCenaId: Int, titulo: String, descricao: String): ResultadoDaChamada<Unit> {
        edicoesPedidas += Triple("sugestao$sugestaoCenaId", titulo, descricao)
        return resultadoDeEditar
    }

    override suspend fun editarFrame(frameId: Int, titulo: String, descricao: String): ResultadoDaChamada<Unit> {
        edicoesPedidas += Triple("frame$frameId", titulo, descricao)
        return resultadoDeEditar
    }

    override suspend fun textoDoFrame(frameId: Int): ResultadoDaChamada<Pair<String, String>> = textoDoFrameADar

    /** As cenas de trecho pedidas e a resposta a dar (TR3). */
    class CenaDeTrechoPedida(val titulo: String, val descricao: String, val posicao: Int?, val estadosIds: List<Int>, val trecho: String? = null)
    val cenasDeTrechoPedidas = mutableListOf<CenaDeTrechoPedida>()
    var resultadoDaCenaDeTrecho: ResultadoDaChamada<com.allan.imagineer.rede.FrameCriado> =
        ResultadoDaChamada.Sucesso(com.allan.imagineer.rede.FrameCriado(id = 500, titulo = "cena"))

    override suspend fun criarCenaDoTrecho(
        capituloId: Int, titulo: String, descricao: String, posicao: Int?, estadosIds: List<Int>, trecho: String?,
    ): ResultadoDaChamada<com.allan.imagineer.rede.FrameCriado> {
        cenasDeTrechoPedidas += CenaDeTrechoPedida(titulo, descricao, posicao, estadosIds, trecho)
        return resultadoDaCenaDeTrecho
    }

    /** As posições postas à mão (cena?, sugestão, posição) e a resposta a dar (PM1). */
    val posicoesPedidas = mutableListOf<Triple<Boolean, Int, Int?>>()
    var resultadoDePosicionar: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)

    override suspend fun posicionarArtefato(ehCena: Boolean, sugestaoId: Int, posicao: Int?): ResultadoDaChamada<Unit> {
        posicoesPedidas += Triple(ehCena, sugestaoId, posicao)
        return resultadoDePosicionar
    }

    /** As referências que o "servidor" guarda por frame (RS1), as leituras e as gravações pedidas. */
    val referenciasNoServidor = mutableMapOf<Int, List<Int>>()
    val leiturasDeReferencias = mutableListOf<Int>()
    val referenciasGuardadas = mutableListOf<Pair<Int, List<Int>>>()
    var resultadoDeGuardarReferencias: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)

    /** A canônica que `GET /frames/{id}` devolve (VM5); pode ser de outro frame. */
    var canonicaDoFrame: Int? = null

    override suspend fun imagemCanonicaDoFrame(frameId: Int): ResultadoDaChamada<Int?> = ResultadoDaChamada.Sucesso(canonicaDoFrame)

    override suspend fun referenciasDoFrame(frameId: Int): ResultadoDaChamada<List<Int>> {
        leiturasDeReferencias += frameId
        return ResultadoDaChamada.Sucesso(referenciasNoServidor[frameId].orEmpty())
    }

    override suspend fun guardarReferencias(frameId: Int, imagensIds: List<Int>): ResultadoDaChamada<Unit> {
        referenciasGuardadas += frameId to imagensIds
        if (resultadoDeGuardarReferencias is ResultadoDaChamada.Sucesso) referenciasNoServidor[frameId] = imagensIds
        return resultadoDeGuardarReferencias
    }

    override suspend fun vinculosDoFrame(frameId: Int): ResultadoDaChamada<List<com.allan.imagineer.rede.VinculadoDoFrame>> {
        leiturasDeVinculos += frameId
        return vinculosNoServidor
    }

    override suspend fun definirVinculos(frameId: Int, estadosIds: List<Int>): ResultadoDaChamada<List<com.allan.imagineer.rede.VinculadoDoFrame>> {
        vinculosDefinidos += frameId to estadosIds
        return resultadoDeDefinirVinculos
            ?: ResultadoDaChamada.Sucesso(estadosIds.map { com.allan.imagineer.rede.VinculadoDoFrame(estado_id = it, nome = "E$it") })
    }

    override suspend fun descartarCena(sugestaoCenaId: Int, descartada: Boolean): ResultadoDaChamada<Unit> {
        descartesDeCena += sugestaoCenaId to descartada
        return resultadoDoDescarteDeCena
    }

    override suspend fun confirmarCena(capituloId: Int, sugestaoCenaId: Int): ResultadoDaChamada<FrameCriado> {
        confirmacoesDeCena += sugestaoCenaId
        travaDaConfirmacaoDeCena?.await()
        return resultadoDaConfirmacaoDeCena
    }
    var travaDaAnalise: CompletableDeferred<Unit>? = null

    /** Leituras para as próximas chamadas a `ler`, uma por chamada: simula o que muda no servidor entre os passos. */
    val proximasLeituras = ArrayDeque<SugestoesDeCapitulo>()

    override suspend fun ler(capituloId: Int): ResultadoDaChamada<SugestoesDeCapitulo> {
        leituras++
        proximasLeituras.removeFirstOrNull()?.let { leitura = ResultadoDaChamada.Sucesso(it) }
        return leitura
    }

    override suspend fun analisar(
        capituloId: Int,
        forcar: Boolean,
        orientacao: String?,
    ): ResultadoDaChamada<SugestoesDeCapitulo> {
        analises += forcar
        orientacoes += orientacao
        travaDaAnalise?.await()
        return analise
    }
}

// ---------------------------------------------------------------------- //
// Funções puras
// ---------------------------------------------------------------------- //

class RegrasDoPainelTest {

    @Test
    fun `rotulo dos sete tipos de elemento`() {
        assertEquals("Personagem", rotuloDoTipo("PERSONAGEM"))
        assertEquals("Ambiente", rotuloDoTipo("AMBIENTE"))
        assertEquals("Objeto", rotuloDoTipo("OBJETO"))
        assertEquals("Criatura", rotuloDoTipo("CRIATURA"))
        assertEquals("Grupo", rotuloDoTipo("GRUPO"))
        assertEquals("Veículo", rotuloDoTipo("VEICULO"))
        assertEquals("Edificação", rotuloDoTipo("EDIFICACAO"))
    }

    @Test
    fun `um tipo novo no backend nao quebra o rotulo`() {
        assertEquals("Planeta", rotuloDoTipo("PLANETA"))
    }

    // --- E11 e E19: o estado, dito pelo título do capítulo -----------------

    @Test
    fun `E11 a linha do estado diz de qual capitulo ele e, pelo titulo`() {
        assertEquals(
            LinhaDoEstado("Estado neste capítulo", "aparência deste capítulo"),
            linhaDoEstado(elemento(elementoId = 7, estadoId = 3)),
        )
        // O número é a posição no livro; o que o usuário vê na lista de capítulos é o título.
        val deOutro = elemento(elementoId = 7).copy(
            estado_vigente = EstadoVigente(id = 900, capitulo_id = 11, ordem_do_capitulo = 8, titulo_do_capitulo = "Capítulo VI", descricao = "manto preto"),
        )
        assertEquals(
            LinhaDoEstado("Usa o estado de «Capítulo VI»", "manto preto"),
            linhaDoEstado(deOutro),
        )
        assertNull(linhaDoEstado(elemento(elementoId = 7)))
        assertNull(linhaDoEstado(elemento()))
    }

    @Test
    fun `E19 sem titulo usa Capitulo e a posicao no livro`() {
        val semTitulo = elemento(elementoId = 7).copy(estado_vigente = EstadoVigente(id = 900, capitulo_id = 11, ordem_do_capitulo = 8, descricao = "x"))

        assertEquals("Usa o estado de «Capítulo 8»", linhaDoEstado(semTitulo)?.rotulo)
    }

    // --- P13: participantes ----------------------------------------------

    @Test
    fun `P13 participante so destaca o casamento automatico`() {
        val auto = ParticipanteSugerido(1, "PERSONAGEM", "Jon", elemento_id = 7, casamento_automatico = true)
        val revisado = ParticipanteSugerido(1, "PERSONAGEM", "Jon", elemento_id = 7, casamento_automatico = false)
        val solto = ParticipanteSugerido(1, "PERSONAGEM", "Jon")

        assertEquals(CASAMENTO_AUTOMATICO, destaqueDoParticipante(auto))
        assertNull(destaqueDoParticipante(revisado))
        assertNull(destaqueDoParticipante(solto))
    }

    // --- P11: pendentes anteriores ---------------------------------------

    @Test
    fun `P11 sem pendentes nao mostra aviso`() {
        assertNull(descreverPendentesAnteriores(0))
        assertNull(descreverPendentesAnteriores(-1)) // dado estranho do servidor
    }

    @Test
    fun `P11 aviso no singular e no plural, sem bloquear`() {
        assertEquals(
            "Você tem 1 sugestão não confirmada em capítulos anteriores — confirmar primeiro deixa esta análise mais precisa.",
            descreverPendentesAnteriores(1),
        )
        assertEquals(
            "Você tem 13 sugestões não confirmadas em capítulos anteriores — confirmar primeiro deixa esta análise mais precisa.",
            descreverPendentesAnteriores(13),
        )
    }

    // --- P4: aside x tela cheia ------------------------------------------

    @Test
    fun `P4 aside so a partir de 840 dp`() {
        assertFalse(usarAside(411)) // celular
        assertFalse(usarAside(839))
        assertTrue(usarAside(840))
        assertTrue(usarAside(1280)) // tablet na horizontal
    }

    // --- P3: o botão some ao rolar para baixo ----------------------------

    @Test
    fun `P3 comeca visivel`() {
        assertTrue(VisibilidadeDoBotao().visivel)
    }

    @Test
    fun `P3 some ao rolar para baixo depois do limiar`() {
        val botao = VisibilidadeDoBotao(limiar = 24f)

        botao.aoRolar(10f, noTopo = false, noFim = false)
        assertTrue(botao.visivel) // ainda abaixo do limiar: um tremor não esconde
        botao.aoRolar(20f, noTopo = false, noFim = false)

        assertFalse(botao.visivel)
    }

    @Test
    fun `P3 reaparece ao rolar para cima`() {
        val botao = VisibilidadeDoBotao(limiar = 24f)
        botao.aoRolar(50f, noTopo = false, noFim = false)
        assertFalse(botao.visivel)

        botao.aoRolar(-30f, noTopo = false, noFim = false)

        assertTrue(botao.visivel)
    }

    @Test
    fun `P3 mudar de direcao recomeca a contagem`() {
        val botao = VisibilidadeDoBotao(limiar = 24f)
        botao.aoRolar(20f, noTopo = false, noFim = false) // quase some...
        botao.aoRolar(-5f, noTopo = false, noFim = false) // ...mas o dedo voltou
        botao.aoRolar(10f, noTopo = false, noFim = false) // e desceu de novo

        // 20, depois -5 (recomeça em -5), depois +10 (recomeça em +10): nunca chegou a 24.
        assertTrue(botao.visivel)
    }

    @Test
    fun `P3 no topo sempre visivel, mesmo rolando para baixo`() {
        val botao = VisibilidadeDoBotao(limiar = 24f)
        botao.aoRolar(100f, noTopo = false, noFim = false)
        assertFalse(botao.visivel)

        botao.aoRolar(5f, noTopo = true, noFim = false)

        assertTrue(botao.visivel)
    }

    @Test
    fun `P3 no fim do texto sempre visivel`() {
        val botao = VisibilidadeDoBotao(limiar = 24f)
        botao.aoRolar(100f, noTopo = false, noFim = false)
        assertFalse(botao.visivel)

        botao.aoRolar(5f, noTopo = false, noFim = true)

        assertTrue(botao.visivel)
    }

    @Test
    fun `P3 depois de sair do topo a contagem recomeca do zero`() {
        val botao = VisibilidadeDoBotao(limiar = 24f)
        botao.aoRolar(100f, noTopo = true, noFim = false) // parado no topo: acumula nada
        botao.aoRolar(10f, noTopo = false, noFim = false)

        assertTrue(botao.visivel) // só 10 de 24
    }
}

// ---------------------------------------------------------------------- //
// O ViewModel: P1, P2, P6 a P10, P14
// ---------------------------------------------------------------------- //

@OptIn(ExperimentalCoroutinesApi::class)
class PainelDeIaViewModelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun vm(repositorio: SugestoesFalso = SugestoesFalso()) = PainelDeIaViewModel(5, repositorio, ElementosFalso())

    // --- P1 e P2: nada é pedido até o painel abrir; abrir é só ler --------

    @Test
    fun `P1 criar o viewmodel nao chama o servidor`() = runTest {
        val repositorio = SugestoesFalso()

        val vm = vm(repositorio)
        advanceUntilIdle()

        assertEquals(EstadoDoPainel(capituloAtualId = 5), vm.estado.value)
        assertEquals(0, repositorio.leituras)
        assertTrue(repositorio.analises.isEmpty())
    }

    @Test
    fun `P2 abrir o painel le e nunca gera`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)

        vm.aoAbrirPainel()
        advanceUntilIdle()

        assertEquals(1, repositorio.leituras)
        assertTrue(repositorio.analises.isEmpty()) // o ponto central: abrir nunca gasta IA
    }

    @Test
    fun `P2 nunca analisado mostra Analisar e guarda as pendentes anteriores`() = runTest {
        val vm = vm()

        vm.aoAbrirPainel()
        advanceUntilIdle()

        assertEquals(ConteudoDoPainel.NuncaAnalisado(pendentesAnteriores = 2), vm.estado.value.conteudo)
    }

    @Test
    fun `P2 ja analisado mostra o resultado`() = runTest {
        val vm = vm(SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(analisado)))

        vm.aoAbrirPainel()
        advanceUntilIdle()

        assertEquals(ConteudoDoPainel.Pronto(analisado), vm.estado.value.conteudo)
    }

    @Test
    fun `P1 abrir e fechar e abrir de novo nao rele`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.aoAbrirPainel()
        vm.aoAbrirPainel()
        advanceUntilIdle()

        assertEquals(1, repositorio.leituras)
    }

    @Test
    fun `P2 falha na leitura mostra o erro, e tentar de novo recupera`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Falha("Não consegui falar com o servidor."))
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()
        assertEquals(ConteudoDoPainel.Erro("Não consegui falar com o servidor."), vm.estado.value.conteudo)

        repositorio.leitura = ResultadoDaChamada.Sucesso(analisado)
        vm.tentarDeNovo()
        advanceUntilIdle()

        assertEquals(ConteudoDoPainel.Pronto(analisado), vm.estado.value.conteudo)
    }

    @Test
    fun `um erro de leitura nao se refaz sozinho ao reabrir o painel`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Falha("erro"))
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.aoAbrirPainel()
        advanceUntilIdle()

        assertEquals(1, repositorio.leituras) // só o "Tentar de novo" relê
    }

    @Test
    fun `tentar de novo sem erro nao faz nada`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.tentarDeNovo()
        advanceUntilIdle()

        assertEquals(1, repositorio.leituras)
    }

    // --- P6: Analisar -------------------------------------------------------

    @Test
    fun `P6 analisar chama o POST sem forcar e mostra o resultado`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.analisar()
        advanceUntilIdle()

        assertEquals(listOf(false), repositorio.analises)
        assertEquals(ConteudoDoPainel.Pronto(analisado), vm.estado.value.conteudo)
        assertFalse(vm.estado.value.analisando)
    }

    @Test
    fun `P6 enquanto analisa, marca analisando e nao aceita outra analise`() = runTest {
        val repositorio = SugestoesFalso().apply { travaDaAnalise = CompletableDeferred() }
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.analisar()
        vm.analisar() // segundo toque, com o primeiro ainda no ar
        advanceUntilIdle()

        assertTrue(vm.estado.value.analisando)
        assertEquals(1, repositorio.analises.size) // uma única cobrança
    }

    @Test
    fun `P6 analisar so vale quando nunca foi analisado`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(analisado))
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.analisar() // já está analisado: quem quiser refazer usa Reanalisar
        advanceUntilIdle()

        assertTrue(repositorio.analises.isEmpty())
    }

    @Test
    fun `P6 analisar antes de abrir o painel nao faz nada`() = runTest {
        val repositorio = SugestoesFalso()
        val vm = vm(repositorio)

        vm.analisar()
        advanceUntilIdle()

        assertTrue(repositorio.analises.isEmpty())
    }

    // --- P7: Reanalisar pede confirmação ------------------------------------

    @Test
    fun `P7 reanalisar pede confirmacao e nao gasta nada ate o sim`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(analisado))
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.pedirReanalise()
        advanceUntilIdle()

        assertTrue(vm.estado.value.confirmandoReanalise)
        assertTrue(repositorio.analises.isEmpty()) // pedir não cobra
    }

    @Test
    fun `P7 cancelar a confirmacao nao gasta nada`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(analisado))
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()
        vm.pedirReanalise()

        vm.cancelarReanalise()
        advanceUntilIdle()

        assertFalse(vm.estado.value.confirmandoReanalise)
        assertTrue(repositorio.analises.isEmpty())
    }

    @Test
    fun `P7 confirmar chama o POST com forcar verdadeiro`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(analisado))
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()
        vm.pedirReanalise()

        vm.confirmarReanalise()
        advanceUntilIdle()

        assertEquals(listOf(true), repositorio.analises)
        assertFalse(vm.estado.value.confirmandoReanalise)
    }

    @Test
    fun `P7 reanalisar so existe depois de analisado`() = runTest {
        val vm = vm() // nunca analisado
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.pedirReanalise()

        assertFalse(vm.estado.value.confirmandoReanalise)
    }

    @Test
    fun `P7 confirmar sem ter pedido nao faz nada`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(analisado))
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.confirmarReanalise()
        advanceUntilIdle()

        assertTrue(repositorio.analises.isEmpty())
    }

    // --- P8: falha na análise não perde nada --------------------------------

    @Test
    fun `P8 falha na analise volta ao estado de antes e mostra a mensagem da API`() = runTest {
        val repositorio = SugestoesFalso(
            analise = ResultadoDaChamada.Falha("Não há chave de API do OpenRouter configurada.", 422),
        )
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.analisar()
        advanceUntilIdle()

        val estado = vm.estado.value
        assertEquals(ConteudoDoPainel.NuncaAnalisado(2), estado.conteudo) // "Analisar" continua ali
        assertEquals("Não há chave de API do OpenRouter configurada.", estado.erroDaAnalise)
        assertFalse(estado.analisando)
    }

    @Test
    fun `P8 falha ao reanalisar mantem a lista antiga`() = runTest {
        val repositorio = SugestoesFalso(
            leitura = ResultadoDaChamada.Sucesso(analisado),
            analise = ResultadoDaChamada.Falha("O provedor falhou.", 502),
        )
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()
        vm.pedirReanalise()
        vm.confirmarReanalise()
        advanceUntilIdle()

        assertEquals(ConteudoDoPainel.Pronto(analisado), vm.estado.value.conteudo) // nada se perdeu
        assertEquals("O provedor falhou.", vm.estado.value.erroDaAnalise)
    }

    @Test
    fun `P8 nunca repete sozinho depois de uma falha`() = runTest {
        val repositorio = SugestoesFalso(analise = ResultadoDaChamada.Falha("erro"))
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.analisar()
        advanceUntilIdle()
        advanceUntilIdle()

        // Repetir sozinho cobraria duas vezes sem ninguém pedir.
        assertEquals(1, repositorio.analises.size)
    }

    @Test
    fun `P8 o erro some quando o usuario tenta de novo`() = runTest {
        val repositorio = SugestoesFalso(analise = ResultadoDaChamada.Falha("erro"))
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()
        vm.analisar()
        advanceUntilIdle()
        assertEquals("erro", vm.estado.value.erroDaAnalise)

        repositorio.analise = ResultadoDaChamada.Sucesso(analisado)
        vm.analisar()
        advanceUntilIdle()

        assertNull(vm.estado.value.erroDaAnalise)
        assertEquals(2, repositorio.analises.size)
    }

    // --- P14: nada achado não é erro ----------------------------------------

    @Test
    fun `P14 analise que nao achou nada e um resultado e nao um erro`() = runTest {
        val repositorio = SugestoesFalso(analise = ResultadoDaChamada.Sucesso(analisadoSemNada))
        val vm = vm(repositorio)
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.analisar()
        advanceUntilIdle()

        val conteudo = vm.estado.value.conteudo
        assertTrue(conteudo is ConteudoDoPainel.Pronto)
        assertTrue((conteudo as ConteudoDoPainel.Pronto).sugestoes.elementos.isEmpty())
        assertNull(vm.estado.value.erroDaAnalise)
        // E com isso Reanalisar passa a existir, porque agora está "analisado".
        vm.pedirReanalise()
        assertTrue(vm.estado.value.confirmandoReanalise)
    }

    // --- P9: sair no meio da análise ----------------------------------------

    @Test
    fun `P9 um viewmodel novo le o que o servidor ja salvou, sem gerar`() = runTest {
        // O usuário saiu no meio da análise; o servidor terminou e salvou. Ao voltar, é um
        // viewmodel novo — e ele só LÊ.
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(analisado))

        val vmNovo = vm(repositorio)
        vmNovo.aoAbrirPainel()
        advanceUntilIdle()

        assertEquals(ConteudoDoPainel.Pronto(analisado), vmNovo.estado.value.conteudo)
        assertTrue(repositorio.analises.isEmpty()) // não gerou de novo: sem cobrança dupla
    }
}
