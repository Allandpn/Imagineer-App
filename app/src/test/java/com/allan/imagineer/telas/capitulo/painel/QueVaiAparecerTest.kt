package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.rede.ConferenciaDaImagem
import com.allan.imagineer.rede.DossieDaCena
import com.allan.imagineer.rede.DossieLido
import com.allan.imagineer.rede.ElementoParaVincular
import com.allan.imagineer.rede.ElementosParaVincular
import com.allan.imagineer.rede.FichaDoPrompt
import com.allan.imagineer.rede.ImagemCandidata
import com.allan.imagineer.rede.ModelosDeImagem
import com.allan.imagineer.rede.PresenteDaCena
import com.allan.imagineer.rede.PresenteDaFicha
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ReferenciasCandidatas
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import com.allan.imagineer.rede.corpoDaGeracaoDeImagem
import com.allan.imagineer.rede.dossieDoJson
import com.allan.imagineer.rede.jsonDoImagineer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val auri = PresenteDaCena(nome = "Auri", tipo = "PESSOA", elemento = "Auri", caracteristicas = "manto azul, cabelo preso")
private val prato = PresenteDaCena(nome = "um prato", tipo = "OBJETO", caracteristicas = "de barro, rachado", incerto = true)
private val dossieDeTeste = DossieDaCena(presentes = listOf(auri, prato), onde = "a cozinha", luz_e_clima = "vela", acao = "Auri serve o caldo")

/** As regras puras da lista "O que vai aparecer" (item 4.9, FL9; especificação 7.5d, AP1 a AP5, AP7). */
class RegrasDoDossieTest {

    @Test
    fun `AP3 o rascunho parte do que o servidor guardou, ou de uma lista vazia`() {
        val rascunho = rascunhoDe(dossieDeTeste)
        assertEquals(listOf(auri, prato), rascunho.presentes)
        assertEquals("a cozinha", rascunho.onde)
        assertEquals(RascunhoDoDossie(), rascunhoDe(null))
    }

    @Test
    fun `AP3 tirar da cena e desmarcar a caixa e o item continua na lista`() {
        val depois = rascunhoDe(dossieDeTeste).comPresenteAlternado(1)

        assertEquals(2, depois.presentes.size)
        assertFalse(depois.presentes[1].incluir)
        assertTrue("voltar atrás", depois.comPresenteAlternado(1).presentes[1].incluir)
        assertEquals("índice fora da lista não faz nada", depois, depois.comPresenteAlternado(9))
    }

    @Test
    fun `AP3 editar as caracteristicas troca so as do item`() {
        val depois = rascunhoDe(dossieDeTeste).comCaracteristicas(0, "manto verde")

        assertEquals("manto verde", depois.presentes[0].caracteristicas)
        assertEquals(prato, depois.presentes[1])
    }

    @Test
    fun `AP3 acrescentar entra sem elemento, incluido e no fim, sem nome nao entra e tipo estranho vira objeto`() {
        val depois = rascunhoDe(dossieDeTeste).comPresenteNovo("  um cão ", "CRIATURA", " pelo preto ")

        val novo = depois.presentes.last()
        assertEquals(PresenteDaCena(nome = "um cão", tipo = "CRIATURA", elemento = null, caracteristicas = "pelo preto", incluir = true), novo)
        assertEquals(rascunhoDe(dossieDeTeste), rascunhoDe(dossieDeTeste).comPresenteNovo("   ", "OBJETO", "x"))
        assertEquals("OBJETO", rascunhoDe(null).comPresenteNovo("vela", "COISA", "").presentes.single().tipo)
    }

    @Test
    fun `AP3 o PUT leva a lista inteira e o campo em branco so limpa o que o servidor ja tinha`() {
        val rascunho = rascunhoDe(dossieDeTeste).comPresenteAlternado(1).copy(onde = "  ", luzEClima = "luz de vela", acao = "")

        val corpo = rascunho.paraGravar(dossieDeTeste.copy(acao = null))

        assertEquals(2, corpo.presentes.size)
        assertFalse(corpo.presentes[1].incluir)
        assertEquals("tinha lugar e a pessoa apagou: limpa", "", corpo.onde)
        assertEquals("luz de vela", corpo.luz_e_clima)
        assertNull("não tinha ação e continua sem: o servidor deixa como estava", corpo.acao)
    }

    @Test
    fun `AP1 a linha do cartao recolhido diz em que pe a lista esta`() {
        assertEquals("ainda não lida", resumoDoDossie(null, comRascunho = false))
        assertEquals("2 itens · lida", resumoDoDossie(dossieDeTeste, comRascunho = false))
        assertEquals("2 itens · confirmada", resumoDoDossie(dossieDeTeste.copy(confirmado = true), comRascunho = false))
        assertEquals("1 item · lida · a cena mudou", resumoDoDossie(dossieDeTeste.copy(presentes = listOf(auri), desatualizado = true), comRascunho = false))
        val editado = rascunhoDe(dossieDeTeste).comPresenteAlternado(0)
        assertEquals("1 item · lida · alterações não confirmadas", resumoDoDossie(dossieDeTeste, comRascunho = true, rascunho = editado))
    }

    @Test
    fun `AP5 a ficha mostra so o que valeu para o prompt, e nada em prompt antigo`() {
        val ficha = FichaDoPrompt(listOf(PresenteDaFicha("Auri"), PresenteDaFicha("um prato")))

        assertEquals("Entrou: Auri, um prato", linhaDaFicha(ficha))
        assertNull(linhaDaFicha(null))
        assertNull(linhaDaFicha(FichaDoPrompt()))
    }

    @Test
    fun `AP4 o que faltou vira um aviso por frase`() {
        assertEquals(listOf("O texto não deixa claro: a cor do manto."), avisosDoQueFaltou(dossieDeTeste.copy(faltou = listOf("a cor do manto.", " "))))
        assertTrue(avisosDoQueFaltou(null).isEmpty())
    }

    @Test
    fun `AP7 so se confere a imagem de um prompt que guardou a lista`() {
        val comLista = PromptDeFrame(id = 1, frame_id = 70, texto = "t", ficha = FichaDoPrompt(listOf(PresenteDaFicha("Auri"))))
        val semLista = PromptDeFrame(id = 2, frame_id = 70, texto = "t", ficha = FichaDoPrompt())
        val antigo = PromptDeFrame(id = 3, frame_id = 70, texto = "t")

        assertEquals(setOf(1), promptsConferiveis(listOf(comLista, semLista, antigo)))
    }

    @Test
    fun `AP7 o custo vem em dolares como texto`() {
        assertEquals("US$ 0,012", custoDaChamada("0.0123"))
        assertEquals("US$ 0,0031", custoDaChamada("0.0031"))
        assertNull(custoDaChamada(null))
        assertNull(custoDaChamada("abc"))
    }

    @Test
    fun `AP6 numa cena sem escolha a linha diz que as referencias sao automaticas`() {
        assertEquals("Elementos e imagens: imagens: automáticas (a âncora de cada elemento da cena)", descreverSelecao(true, emptyList(), 0, true, automaticas = true))
        assertEquals("sem automáticas, continua nenhuma", "Elementos e imagens: imagens: nenhuma", descreverSelecao(true, emptyList(), 0, true))
        assertEquals("o modelo não usa referências: nenhuma", "Elementos e imagens: imagens: nenhuma", descreverSelecao(true, emptyList(), 0, false, automaticas = true))
        assertEquals("Elementos e imagens: 2 imagens", descreverSelecao(true, emptyList(), 2, true, automaticas = true))
    }
}

/** O que viaja pela rede: o JSON do servidor e o corpo de `gerar-imagem` (AP6). */
class DossiePelaRedeTest {

    @Test
    fun `o JSON null do GET quer dizer que a cena ainda nao foi lida`() {
        assertNull(dossieDoJson(JsonNull))
    }

    @Test
    fun `o dossie do servidor e lido com todos os campos, e campo novo e ignorado`() {
        val json = jsonDoImagineer.parseToJsonElement(
            """{"momento_incerto": true, "presentes": [{"nome": "Auri", "tipo": "PESSOA", "elemento": "Auri",
                "caracteristicas": "manto", "incerto": false, "incluir": true}], "onde": "a cozinha", "luz_e_clima": null,
                "acao": "serve", "faltou": ["a hora"], "confirmado": true, "desatualizado": true, "campo_novo": 1}""",
        )

        val dossie = dossieDoJson(json)!!

        assertTrue(dossie.momento_incerto)
        assertEquals("Auri", dossie.presentes.single().elemento)
        assertEquals(listOf("a hora"), dossie.faltou)
        assertTrue(dossie.confirmado && dossie.desatualizado)
        assertNull(dossie.luz_e_clima)
    }

    @Test
    fun `a ficha e a conferencia e as marcadas sao lidas do JSON`() {
        val prompt = jsonDoImagineer.decodeFromString(
            PromptDeFrame.serializer(),
            """{"id": 1, "frame_id": 2, "texto": "t", "ficha": {"dossie": null, "presentes": [{"nome": "Auri", "tipo": "PESSOA"}], "momentos": [], "referencias": []}}""",
        )
        assertEquals("Auri", prompt.ficha!!.presentes.single().nome)

        val conferencia = jsonDoImagineer.decodeFromString(
            ConferenciaDaImagem.serializer(),
            """{"conforme": false, "divergencias": ["Deveria: duas pessoas. A imagem mostra: três."], "itens_conferidos": 2, "modelo": "m", "custo": "0.0031"}""",
        )
        assertFalse(conferencia.conforme)
        assertEquals("0.0031", conferencia.custo)

        val candidatas = jsonDoImagineer.decodeFromString(ReferenciasCandidatas.serializer(), """{"elementos": [], "marcadas": [11, 31]}""")
        assertEquals(listOf(11, 31), candidatas.marcadas)
        assertEquals("servidor antigo, sem o campo", emptyList<Int>(), jsonDoImagineer.decodeFromString(ReferenciasCandidatas.serializer(), """{"elementos": []}""").marcadas)
    }

    @Test
    fun `AP6 sem escolha o corpo da geracao nem leva as referencias, e a lista vazia vai como nenhuma`() {
        assertFalse("imagens_de_referencia" in corpoDaGeracaoDeImagem(null, null, false, null, null))
        assertEquals(JsonArray(emptyList()), corpoDaGeracaoDeImagem(null, null, false, emptyList(), null)["imagens_de_referencia"])
        assertEquals(JsonArray(listOf(JsonPrimitive(3), JsonPrimitive(4))), corpoDaGeracaoDeImagem(null, null, false, listOf(3, 4), null)["imagens_de_referencia"])
    }

    @Test
    fun `o corpo da geracao continua levando so o que a pessoa decidiu`() {
        val corpo = corpoDaGeracaoDeImagem("texto", "m/x", true, null, "em português")

        assertEquals(JsonPrimitive("texto"), corpo["texto"])
        assertEquals(JsonPrimitive("em português"), corpo["texto_pt"])
        assertEquals(JsonPrimitive("m/x"), corpo["modelo"])
        assertEquals(JsonPrimitive(true), corpo["sem_filtro_de_seguranca"])
        assertEquals(setOf("texto", "texto_pt", "modelo", "sem_filtro_de_seguranca"), corpo.keys)
    }
}

/** O ViewModel da lista "O que vai aparecer", da conferência e das referências pré-marcadas (AP2 a AP4, AP6, AP7). */
@OptIn(ExperimentalCoroutinesApi::class)
class DossieNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val semente = ElementoParaVincular(elemento_id = 10, estado_id = 1, nome = "Foxen", tipo = "CRIATURA", no_frame = true, imagens = listOf(ImagemCandidata(id = 11), ImagemCandidata(id = 12)))
    private val modelos = ModelosDeImagem("meta/muse-image", listOf("meta/muse-image", "seed"), comReferencia = listOf("seed"))

    private fun montar(): Pair<PainelDeIaViewModel, PromptsFalso> {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(SugestoesDeCapitulo(gerado_em = "2026-10-08T10:00:00")))
        val prompts = PromptsFalso().also {
            it.resultadoDosModelos = ResultadoDaChamada.Sucesso(modelos)
            it.paraVincular = ResultadoDaChamada.Sucesso(ElementosParaVincular(identificados = listOf(semente)))
        }
        val vm = PainelDeIaViewModel(
            5, repositorio, ElementosFalso(), prompts,
            ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
        )
        return vm to prompts
    }

    private fun dossieDoEstado(vm: PainelDeIaViewModel, frameId: Int = 70) = vm.estado.value.dossies[frameId] as DossieDoFrame.Pronto

    @Test
    fun `AP2 abrir le o dossie guardado uma vez, sem gastar IA`() = runTest {
        val (vm, prompts) = montar()
        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste)

        vm.carregarDossie(70)
        assertEquals(DossieDoFrame.Lendo, vm.estado.value.dossies[70])
        advanceUntilIdle()
        vm.carregarDossie(70); advanceUntilIdle()

        assertEquals(dossieDeTeste, dossieDoEstado(vm).guardado)
        assertEquals("lê uma vez só", listOf(70), prompts.leiturasDoDossie)
        assertTrue("o GET não gasta IA", prompts.dossiesLidosDeNovo.isEmpty())
    }

    @Test
    fun `AP2 sem dossie guardado a lista fica vazia e uma falha de leitura se refaz`() = runTest {
        val (vm, prompts) = montar()
        vm.carregarDossie(70); advanceUntilIdle()
        assertNull(dossieDoEstado(vm).guardado)

        prompts.dossieGuardado = ResultadoDaChamada.Falha("sem rede")
        vm.recarregarDossie(70); advanceUntilIdle()
        assertEquals(DossieDoFrame.Erro("sem rede"), vm.estado.value.dossies[70])

        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste)
        vm.recarregarDossie(70); advanceUntilIdle()
        assertEquals(dossieDeTeste, dossieDoEstado(vm).guardado)
    }

    @Test
    fun `AP3 editar so mexe no rascunho, o guardado continua como o servidor o tem`() = runTest {
        val (vm, prompts) = montar()
        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste)
        vm.carregarDossie(70); advanceUntilIdle()

        vm.alternarPresenteDoDossie(70, 1)
        vm.mudarCaracteristicasDoDossie(70, 0, "manto verde")
        vm.mudarCampoDoDossie(70, CampoDoDossie.ONDE, "o pátio")

        val atual = dossieDoEstado(vm)
        assertEquals(dossieDeTeste, atual.guardado)
        assertFalse(atual.rascunho!!.presentes[1].incluir)
        assertEquals("manto verde", atual.rascunho!!.presentes[0].caracteristicas)
        assertEquals("o pátio", atual.rascunho!!.onde)
        assertTrue(prompts.dossiesConfirmados.isEmpty())
    }

    @Test
    fun `AP3 acrescentar um item abre e fecha o dialogo e o item entra no fim`() = runTest {
        val (vm, prompts) = montar()
        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste)
        vm.carregarDossie(70); advanceUntilIdle()

        vm.pedirAcrescentarAoDossie(70)
        assertEquals(70, vm.estado.value.acrescentandoAoDossie)
        vm.acrescentarAoDossie(70, "  ", "OBJETO", "x")
        assertEquals("sem nome o diálogo continua", 70, vm.estado.value.acrescentandoAoDossie)
        vm.acrescentarAoDossie(70, "um cão", "CRIATURA", "pelo preto")

        assertNull(vm.estado.value.acrescentandoAoDossie)
        assertEquals("um cão", dossieDoEstado(vm).rascunho!!.presentes.last().nome)
    }

    @Test
    fun `AP3 descartar larga o rascunho`() = runTest {
        val (vm, prompts) = montar()
        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste)
        vm.carregarDossie(70); advanceUntilIdle()
        vm.alternarPresenteDoDossie(70, 0)

        vm.descartarRascunhoDoDossie(70)

        assertNull(dossieDoEstado(vm).rascunho)
    }

    @Test
    fun `AP3 confirmar manda a lista inteira como esta na tela, nao gasta IA e limpa o rascunho`() = runTest {
        val (vm, prompts) = montar()
        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste)
        vm.carregarDossie(70); advanceUntilIdle()
        vm.alternarPresenteDoDossie(70, 1)

        vm.confirmarDossie(70); advanceUntilIdle()

        val (frame, corpo) = prompts.dossiesConfirmados.single()
        assertEquals(70, frame)
        assertEquals(2, corpo.presentes.size)
        assertFalse(corpo.presentes[1].incluir)
        assertTrue(prompts.dossiesLidosDeNovo.isEmpty())
        val depois = dossieDoEstado(vm)
        assertNull(depois.rascunho)
        assertTrue(depois.guardado!!.confirmado)
        assertEquals(AVISO_LISTA_CONFIRMADA, depois.recado?.texto)
    }

    @Test
    fun `AP3 confirmar sem editar confirma a lista como o servidor a tem`() = runTest {
        val (vm, prompts) = montar()
        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste)
        vm.carregarDossie(70); advanceUntilIdle()

        vm.confirmarDossie(70); advanceUntilIdle()

        assertEquals(dossieDeTeste.presentes, prompts.dossiesConfirmados.single().second.presentes)
    }

    @Test
    fun `AP3 se o servidor recusar a confirmacao o rascunho fica e o motivo aparece`() = runTest {
        val (vm, prompts) = montar()
        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste)
        prompts.dossieConfirmado = { ResultadoDaChamada.Falha("O servidor recusou a lista.") }
        vm.carregarDossie(70); advanceUntilIdle()
        vm.alternarPresenteDoDossie(70, 0)

        vm.confirmarDossie(70); advanceUntilIdle()

        val atual = dossieDoEstado(vm)
        assertNotNull("a edição não se perde", atual.rascunho)
        assertFalse(atual.gravando)
        assertEquals(MensagemDoElemento("O servidor recusou a lista.", ehErro = true), atual.recado)
    }

    @Test
    fun `AP2 ler de novo pede confirmacao e so gasta IA depois do sim`() = runTest {
        val (vm, prompts) = montar()
        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste.copy(confirmado = true))
        prompts.dossieLidoDeNovo = ResultadoDaChamada.Sucesso(DossieLido(presentes = listOf(auri), modelo = "m/x", custo = "0.0123"))
        vm.carregarDossie(70); advanceUntilIdle()
        vm.alternarPresenteDoDossie(70, 0)

        vm.pedirLerDossie(70); advanceUntilIdle()
        assertEquals(70, vm.estado.value.confirmandoLeituraDoDossie)
        assertTrue("só pediu: ainda não gastou", prompts.dossiesLidosDeNovo.isEmpty())

        vm.cancelarLerDossie(); advanceUntilIdle()
        assertNull(vm.estado.value.confirmandoLeituraDoDossie)
        assertTrue(prompts.dossiesLidosDeNovo.isEmpty())

        vm.pedirLerDossie(70)
        vm.lerDossie(70); advanceUntilIdle()

        assertEquals(listOf(70), prompts.dossiesLidosDeNovo)
        val depois = dossieDoEstado(vm)
        assertEquals(listOf(auri), depois.guardado!!.presentes)
        assertNull("a edição e a confirmação se perdem", depois.rascunho)
        assertFalse(depois.guardado!!.confirmado)
        assertFalse(depois.lendoDeNovo)
        assertEquals("Cena lida por m/x (US$ 0,012).", depois.recado?.texto)
    }

    @Test
    fun `AP2 sem o sim do dialogo a leitura nao acontece`() = runTest {
        val (vm, prompts) = montar()
        vm.carregarDossie(70); advanceUntilIdle()

        vm.lerDossie(70); advanceUntilIdle()

        assertTrue(prompts.dossiesLidosDeNovo.isEmpty())
    }

    @Test
    fun `AP2 uma falha ao ler de novo mostra o motivo e deixa a lista de antes`() = runTest {
        val (vm, prompts) = montar()
        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste)
        prompts.dossieLidoDeNovo = ResultadoDaChamada.Falha("O provedor de IA falhou.")
        vm.carregarDossie(70); advanceUntilIdle()

        vm.pedirLerDossie(70)
        vm.lerDossie(70); advanceUntilIdle()

        val atual = dossieDoEstado(vm)
        assertEquals(dossieDeTeste, atual.guardado)
        assertFalse(atual.lendoDeNovo)
        assertEquals(MensagemDoElemento("O provedor de IA falhou.", ehErro = true), atual.recado)
    }

    @Test
    fun `AP4 depois de gerar um prompt a lista e relida porque o servidor pode te-la criado`() = runTest {
        val (vm, prompts) = montar()
        vm.carregarDossie(70); advanceUntilIdle()
        assertNull(dossieDoEstado(vm).guardado)
        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste)

        vm.pedirGerarPrompt(70, "Cena")
        vm.gerarPrompt(70, ""); advanceUntilIdle()

        assertEquals(dossieDeTeste, dossieDoEstado(vm).guardado)
        assertEquals("o GET extra, sem gastar IA", listOf(70, 70), prompts.leiturasDoDossie)
    }

    @Test
    fun `AP4 se a lista mudou no servidor o rascunho velho e largado`() = runTest {
        val (vm, prompts) = montar()
        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste)
        vm.carregarDossie(70); advanceUntilIdle()
        vm.alternarPresenteDoDossie(70, 0)
        prompts.dossieGuardado = ResultadoDaChamada.Sucesso(dossieDeTeste.copy(presentes = listOf(prato)))

        vm.pedirGerarPrompt(70, "Cena")
        vm.gerarPrompt(70, ""); advanceUntilIdle()

        val atual = dossieDoEstado(vm)
        assertEquals(listOf(prato), atual.guardado!!.presentes)
        assertNull(atual.rascunho)
    }

    @Test
    fun `AP4 uma lista que nunca foi aberta nao e lida so porque saiu um prompt`() = runTest {
        val (vm, prompts) = montar()

        vm.pedirGerarPrompt(70, "Cena")
        vm.gerarPrompt(70, ""); advanceUntilIdle()

        assertTrue(prompts.leiturasDoDossie.isEmpty())
    }

    @Test
    fun `AP7 conferir pede confirmacao, so gasta depois do sim e mostra as divergencias`() = runTest {
        val (vm, prompts) = montar()
        prompts.conferencia = ResultadoDaChamada.Sucesso(ConferenciaDaImagem(false, listOf("Deveria: duas pessoas. A imagem mostra: três."), 2, "m/v", "0.0031"))

        vm.pedirConferirImagem(700); advanceUntilIdle()
        assertEquals(ConferenciaEmCurso.Confirmando(700), vm.estado.value.conferencia)
        assertTrue("só pediu", prompts.conferencias.isEmpty())

        vm.conferirImagem(); advanceUntilIdle()

        assertEquals(listOf(700), prompts.conferencias)
        val pronta = vm.estado.value.conferencia as ConferenciaEmCurso.Pronta
        assertEquals(listOf("Deveria: duas pessoas. A imagem mostra: três."), pronta.resultado.divergencias)
        vm.fecharConferencia()
        assertNull(vm.estado.value.conferencia)
    }

    @Test
    fun `AP7 cancelar o dialogo nao gasta, e a recusa do servidor aparece com a mensagem dele`() = runTest {
        val (vm, prompts) = montar()
        vm.pedirConferirImagem(700)
        vm.fecharConferencia()
        vm.conferirImagem(); advanceUntilIdle()
        assertTrue(prompts.conferencias.isEmpty())

        prompts.conferencia = ResultadoDaChamada.Falha("Escolha um modelo de conferência que leia imagens.")
        vm.pedirConferirImagem(700)
        vm.conferirImagem(); advanceUntilIdle()

        assertEquals(ConferenciaEmCurso.Falhou(700, "Escolha um modelo de conferência que leia imagens."), vm.estado.value.conferencia)
    }

    @Test
    fun `AP6 o seletor da cena abre com as imagens que o servidor mandaria sozinho`() = runTest {
        val (vm, prompts) = montar()
        prompts.candidatas = ResultadoDaChamada.Sucesso(ReferenciasCandidatas(marcadas = listOf(11)))

        vm.abrirSeletorDaCena(70); advanceUntilIdle()

        assertEquals(setOf(11), vm.estado.value.escolhaDeElementos?.selecao?.imagens)
        assertEquals(listOf(70), prompts.frameDasCandidatas)
    }

    @Test
    fun `AP6 se a leitura das marcadas falhar o seletor abre sem nenhuma marcada`() = runTest {
        val (vm, prompts) = montar()
        prompts.candidatas = ResultadoDaChamada.Falha("sem rede")

        vm.abrirSeletorDaCena(70); advanceUntilIdle()

        assertTrue(vm.estado.value.escolhaDeElementos?.selecao?.imagens.orEmpty().isEmpty())
        assertTrue(vm.estado.value.escolhaDeElementos?.candidatos is CandidatosDoSeletor.Prontos)
    }

    @Test
    fun `AP6 depois de uma escolha explicita as marcadas nao voltam nem sao perguntadas de novo`() = runTest {
        val (vm, prompts) = montar()
        prompts.candidatas = ResultadoDaChamada.Sucesso(ReferenciasCandidatas(marcadas = listOf(11)))

        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        vm.alternarImagemDoSeletor(11)
        vm.usarSeletor(); advanceUntilIdle()
        vm.abrirSeletorDaCena(70); advanceUntilIdle()

        assertTrue("a escolha explícita (vazia) vale, não as marcadas", vm.estado.value.escolhaDeElementos?.selecao?.imagens.orEmpty().isEmpty())
        assertEquals("só a primeira abertura perguntou", listOf(70), prompts.frameDasCandidatas)
    }

    @Test
    fun `AP6 sem escolha da pessoa a geracao nao leva o campo, e com tudo desmarcado leva a lista vazia`() = runTest {
        val (vm, prompts) = montar()
        prompts.candidatas = ResultadoDaChamada.Sucesso(ReferenciasCandidatas(marcadas = listOf(11)))
        vm.carregarModelosDeImagem(); advanceUntilIdle()
        vm.escolherModelo("seed")

        vm.gerarImagem(70, 1); advanceUntilIdle()
        assertEquals("a pessoa não escolheu: vale o padrão do servidor", listOf<List<Int>?>(null), prompts.referenciasPedidas)

        vm.abrirSeletorDaCena(70); advanceUntilIdle()
        vm.alternarImagemDoSeletor(11) // tira a que vinha marcada
        vm.usarSeletor(); advanceUntilIdle()
        vm.gerarImagem(70, 2); advanceUntilIdle()

        assertEquals(listOf<List<Int>?>(null, emptyList()), prompts.referenciasPedidas)
    }
}
