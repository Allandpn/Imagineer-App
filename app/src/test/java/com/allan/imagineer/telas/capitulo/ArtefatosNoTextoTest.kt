package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.rede.CapituloDetalhe
import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import com.allan.imagineer.rede.jsonDoImagineer
import com.allan.imagineer.telas.capitulo.painel.ElementosFalso
import com.allan.imagineer.telas.capitulo.painel.PainelDeIaViewModel
import com.allan.imagineer.telas.capitulo.painel.AcaoDoElemento
import com.allan.imagineer.telas.capitulo.painel.ConteudoDoPainel
import com.allan.imagineer.telas.capitulo.painel.DialogoDeElemento
import com.allan.imagineer.telas.capitulo.painel.SugestoesFalso
import com.allan.imagineer.telas.capitulo.painel.elemento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun artefato(
    rotulo: String,
    posicao: Int?,
    tipo: String = "PERSONAGEM",
    situacao: String = "SUGERIDO",
    sugestaoId: Int? = null,
) = Artefato(
    tipo = "ELEMENTO", tipo_do_elemento = tipo, sugestao_id = sugestaoId, rotulo = rotulo,
    posicao_no_texto = posicao, situacao = situacao,
)

class ArtefatosNoTextoTest {

    private val texto = "Primeiro parágrafo.\n\nJon chegou ao muro.\nOutra linha.\n\n\n\n  Terceiro, com Ned.  "

    @Test
    fun `cada paragrafo traz onde comeca no texto, depois do aparo`() {
        val paragrafos = dividirEmParagrafosComInicio(texto)

        assertEquals(
            listOf("Primeiro parágrafo.", "Jon chegou ao muro.\nOutra linha.", "Terceiro, com Ned."),
            paragrafos.map { it.texto },
        )
        // O início aponta para a primeira letra, e não para os espaços que o aparo tirou.
        assertEquals(listOf(0, texto.indexOf("Jon chegou"), texto.indexOf("Terceiro")), paragrafos.map { it.inicio })
        paragrafos.forEach { assertTrue(texto.startsWith(it.texto, it.inicio)) }
    }

    @Test
    fun `a divisao concorda com a que a tela ja usava`() {
        assertEquals(dividirEmParagrafos(texto), dividirEmParagrafosComInicio(texto).map { it.texto })
        assertTrue(dividirEmParagrafosComInicio("").isEmpty())
        assertTrue(dividirEmParagrafosComInicio("\n\n  \n\n").isEmpty())
    }

    @Test
    fun `a posicao do servidor, em UTF-16, bate com o indice de uma String Kotlin - emoji incluso`() {
        // O servidor conta o emoji como 2 unidades (UTF-16); a String do Kotlin também.
        val comEmoji = "Começo \uD83D\uDE00.\n\nO Muro."

        val paragrafos = dividirEmParagrafosComInicio(comEmoji)

        assertEquals(12, paragrafos[1].inicio) // a mesma posição que o servidor devolve para "Muro"
        assertEquals(12, comEmoji.indexOf("O Muro"))
    }

    @Test
    fun `cada artefato vai para o paragrafo em que a posicao cai`() {
        val paragrafos = dividirEmParagrafosComInicio(texto)
        val jon = artefato("Jon", paragrafos[1].inicio)
        val ned = artefato("Ned", paragrafos[2].inicio)
        val muro = artefato("Muro", paragrafos[1].inicio, tipo = "AMBIENTE")

        val distribuidos = distribuirArtefatos(listOf(jon, ned, muro), paragrafos)

        assertEquals(listOf(jon, muro), distribuidos.porParagrafo[1])
        assertEquals(listOf(ned), distribuidos.porParagrafo[2])
        assertNull(distribuidos.porParagrafo[0])
        assertTrue(distribuidos.semPosicao.isEmpty())
    }

    @Test
    fun `uma posicao no meio de um paragrafo cai nele, e uma na folga entre dois cai no anterior`() {
        val paragrafos = dividirEmParagrafosComInicio(texto)
        val noMeio = artefato("Jon", paragrafos[1].inicio + 5)
        // uma unidade antes do início do terceiro: está nas quebras de linha que o aparo tirou, ainda do segundo
        val naFolga = artefato("Ned", paragrafos[2].inicio - 1)

        val distribuidos = distribuirArtefatos(listOf(noMeio, naFolga), paragrafos)

        assertEquals(listOf(noMeio, naFolga), distribuidos.porParagrafo[1])
        assertEquals(setOf(1), distribuidos.porParagrafo.keys)
    }

    @Test
    fun `sem posicao, ou antes do primeiro paragrafo, vai para a faixa sem posicao`() {
        val paragrafos = dividirEmParagrafosComInicio("\n\n   Começa tarde.\n\nFim.")
        val sem = artefato("Arya", null)
        val antes = artefato("Cedo", 0)

        val distribuidos = distribuirArtefatos(listOf(sem, antes), paragrafos)

        assertEquals(listOf(sem, antes), distribuidos.semPosicao)
        assertTrue(distribuidos.porParagrafo.isEmpty())
    }

    @Test
    fun `sem paragrafos todos os artefatos ficam sem posicao`() {
        val distribuidos = distribuirArtefatos(listOf(artefato("Jon", 10)), emptyList())

        assertEquals(1, distribuidos.semPosicao.size)
    }

    @Test
    fun `a descricao de acessibilidade diz o nome e onde o usuario parou`() {
        assertEquals("Jon, sugerido, ainda não confirmado", descreverArtefato(artefato("Jon", 0)))
        assertEquals("Jon, confirmado", descreverArtefato(artefato("Jon", 0, situacao = "CONFIRMADO")))
        assertEquals("Jon, com prompt pronto", descreverArtefato(artefato("Jon", 0, situacao = "PROMPT_PRONTO")))
        assertEquals("Jon, ilustrado", descreverArtefato(artefato("Jon", 0, situacao = "ILUSTRADO")))
    }

    @Test
    fun `o JSON real do servidor desserializa, com e sem os campos opcionais`() {
        val json = """{"artefatos":[
            {"tipo":"ELEMENTO","tipo_do_elemento":"VEICULO","sugestao_id":4,"frame_id":null,"rotulo":"Navio","posicao_no_texto":120,"situacao":"CONFIRMADO","imagem_id":null},
            {"tipo":"CENA","rotulo":"O duelo","situacao":"SUGERIDO"}]}"""

        val lidos = jsonDoImagineer.decodeFromString<com.allan.imagineer.rede.ArtefatosDoCapitulo>(json).artefatos

        assertEquals("VEICULO", lidos[0].tipo_do_elemento)
        assertEquals(120, lidos[0].posicao_no_texto)
        assertNull(lidos[1].posicao_no_texto)
        assertNull(lidos[1].tipo_do_elemento)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArtefatosDoCapituloViewModelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val capitulo = CapituloDetalhe(
        id = 5, ordem = 1, titulo = "Um", ignorado = false, tamanho_do_texto = 9, livro_id = 1, texto = "Jon chegou.",
    )

    private fun vm(artefatos: ArtefatosFalso) =
        CapituloViewModel(5, CapitulosParaLeituraDe(capitulo), artefatos)

    @Test
    fun `carregar os artefatos guarda o que o servidor devolveu`() = runTest {
        val falso = ArtefatosFalso(ResultadoDaChamada.Sucesso(listOf(artefato("Jon", 0))))
        val vm = vm(falso)

        vm.carregarArtefatos()
        advanceUntilIdle()

        assertEquals(listOf(artefato("Jon", 0)), vm.artefatos.value)
        assertEquals(1, falso.leituras)
    }

    @Test
    fun `falhar nos artefatos nao troca o texto por erro e mantem os que ja havia`() = runTest {
        val falso = ArtefatosFalso(ResultadoDaChamada.Sucesso(listOf(artefato("Jon", 0))))
        val vm = vm(falso)
        vm.carregar()
        vm.carregarArtefatos()
        advanceUntilIdle()

        falso.resposta = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        vm.carregarArtefatos()
        advanceUntilIdle()

        assertEquals(listOf(artefato("Jon", 0)), vm.artefatos.value) // ficou o que estava
        assertTrue(vm.estado.value is EstadoDoCapitulo.Pronto) // o texto continua lá
    }

    @Test
    fun `o texto carrega sem pedir artefatos - eles vem a parte`() = runTest {
        val falso = ArtefatosFalso()
        val vm = vm(falso)

        vm.carregar()
        advanceUntilIdle()

        assertTrue(vm.estado.value is EstadoDoCapitulo.Pronto)
        assertEquals(0, falso.leituras)
    }

    @Test
    fun `reler depois de uma mudanca mostra os novos`() = runTest {
        val falso = ArtefatosFalso(ResultadoDaChamada.Sucesso(listOf(artefato("Jon", 0))))
        val vm = vm(falso)
        vm.carregarArtefatos()
        advanceUntilIdle()

        falso.resposta = ResultadoDaChamada.Sucesso(listOf(artefato("Jon", 0, situacao = "CONFIRMADO")))
        vm.carregarArtefatos()
        advanceUntilIdle()

        assertEquals("CONFIRMADO", vm.artefatos.value.single().situacao)
    }
}

/** Os capítulos falsos do teste de leitura acima (o repositório real vive em outro arquivo). */
internal class CapitulosParaLeituraDe(private val capitulo: CapituloDetalhe) : com.allan.imagineer.rede.RepositorioDeCapitulos {
    override suspend fun abrirCapitulo(capituloId: Int): ResultadoDaChamada<CapituloDetalhe> =
        ResultadoDaChamada.Sucesso(capitulo)

    override suspend fun ajustarCapitulo(
        capituloId: Int,
        ajuste: com.allan.imagineer.rede.CapituloAjuste,
    ): ResultadoDaChamada<com.allan.imagineer.rede.CapituloResumo> = error("não usado")
}

@OptIn(ExperimentalCoroutinesApi::class)
class ModalDaSugestaoTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun sugestoes() = SugestoesFalso(
        leitura = ResultadoDaChamada.Sucesso(
            SugestoesDeCapitulo(
                gerado_em = "x",
                elementos = listOf(
                    elemento(id = 7, nome = "Jon", elementoId = 30, automatico = true, estadoId = 70),
                    elemento(id = 8, nome = "Ned"),
                ),
            ),
        ),
    )

    @Test
    fun `tocar no icone abre a sugestao no modal`() = runTest {
        val vm = PainelDeIaViewModel(5, sugestoes(), ElementosFalso())

        vm.abrirModal(7)
        advanceUntilIdle()

        assertEquals(7, vm.estado.value.emModal)
    }

    @Test
    fun `com o painel nunca aberto, o modal le as sugestoes - so o GET, sem gastar IA`() = runTest {
        val sug = sugestoes()
        val vm = PainelDeIaViewModel(5, sug, ElementosFalso())

        vm.abrirModal(7)
        advanceUntilIdle()

        assertEquals(1, sug.leituras)
        assertTrue(sug.analises.isEmpty())
        assertTrue(vm.estado.value.conteudo is ConteudoDoPainel.Pronto)
    }

    @Test
    fun `com as sugestoes ja lidas, abrir o modal nao le de novo`() = runTest {
        val sug = sugestoes()
        val vm = PainelDeIaViewModel(5, sug, ElementosFalso())
        vm.aoAbrirPainel()
        advanceUntilIdle()

        vm.abrirModal(8)
        advanceUntilIdle()

        assertEquals(1, sug.leituras)
        assertEquals(8, vm.estado.value.emModal)
    }

    @Test
    fun `fechar o modal limpa so ele`() = runTest {
        val vm = PainelDeIaViewModel(5, sugestoes(), ElementosFalso())
        vm.abrirModal(7)
        advanceUntilIdle()

        vm.fecharModal()

        assertNull(vm.estado.value.emModal)
        assertTrue(vm.estado.value.conteudo is ConteudoDoPainel.Pronto) // as sugestões continuam lidas
    }

    private fun abrirNoModal(
        sug: SugestoesFalso = sugestoes(),
        els: ElementosFalso = ElementosFalso(),
        id: Int = 7,
    ): PainelDeIaViewModel {
        val vm = PainelDeIaViewModel(5, sug, els)
        vm.definirLivro(4)
        vm.abrirModal(id)
        return vm
    }

    private val jon = elemento(id = 7, nome = "Jon", elementoId = 30, automatico = true, estadoId = 70)
    private val ned = elemento(id = 8, nome = "Ned")

    @Test
    fun `E44 confirmar conclui e fecha o modal`() = runTest {
        val els = ElementosFalso()
        val vm = abrirNoModal(els = els)
        advanceUntilIdle()

        vm.executar(AcaoDoElemento.CONFIRMAR, jon)
        advanceUntilIdle()

        assertEquals(listOf("ajustarCasamento(7,30)"), els.chamadas)
        assertNull(vm.estado.value.emModal)
    }

    @Test
    fun `E44 criar o elemento conclui e fecha o modal`() = runTest {
        val els = ElementosFalso()
        val vm = abrirNoModal(els = els, id = 8)
        advanceUntilIdle()
        vm.executar(AcaoDoElemento.CRIAR, ned)

        vm.confirmarCriacao("PERSONAGEM", "Ned", "")
        advanceUntilIdle()

        assertEquals(listOf("criar(4,PERSONAGEM,Ned,,8)"), els.chamadas)
        assertNull(vm.estado.value.emModal)
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `E44 vincular a um existente conclui e fecha o modal`() = runTest {
        val els = ElementosFalso().apply {
            lista = ResultadoDaChamada.Sucesso(listOf(com.allan.imagineer.rede.ElementoDoLivro(31, "PERSONAGEM", "Outro")))
        }
        val vm = abrirNoModal(els = els, id = 8)
        advanceUntilIdle()
        vm.executar(AcaoDoElemento.VINCULAR, ned)
        advanceUntilIdle()

        vm.escolherElemento(31)
        advanceUntilIdle()

        assertNull(vm.estado.value.emModal)
    }

    @Test
    fun `E44 descartar conclui e fecha o modal, inclusive depois do aviso de cenas`() = runTest {
        val vm = abrirNoModal(id = 8)
        advanceUntilIdle()

        vm.executar(AcaoDoElemento.DESCARTAR, ned)
        advanceUntilIdle()

        assertNull(vm.estado.value.emModal)
    }

    @Test
    fun `E44 registrar estado conclui e fecha o modal`() = runTest {
        val semEstado = elemento(id = 7, nome = "Jon", elementoId = 30)
        val sug = SugestoesFalso(
            leitura = ResultadoDaChamada.Sucesso(SugestoesDeCapitulo(gerado_em = "x", elementos = listOf(semEstado))),
        )
        val vm = abrirNoModal(sug = sug)
        advanceUntilIdle()

        vm.executar(AcaoDoElemento.REGISTRAR_ESTADO, semEstado)
        advanceUntilIdle()

        assertNull(vm.estado.value.emModal)
    }

    @Test
    fun `E44 o que NAO conclui deixa o modal aberto - desfazer pede a proxima decisao`() = runTest {
        val vm = abrirNoModal(sug = SugestoesFalso(
            leitura = ResultadoDaChamada.Sucesso(
                SugestoesDeCapitulo(gerado_em = "x", elementos = listOf(elemento(id = 7, nome = "Jon", elementoId = 30, automatico = true))),
            ),
        ))
        advanceUntilIdle()

        vm.executar(AcaoDoElemento.DESFAZER, elemento(id = 7, nome = "Jon", elementoId = 30, automatico = true))
        advanceUntilIdle()

        assertEquals(7, vm.estado.value.emModal)
    }

    @Test
    fun `E44 uma acao que falha nao fecha o modal`() = runTest {
        val els = ElementosFalso().apply { resposta = ResultadoDaChamada.Falha("Não consegui falar com o servidor.") }
        val vm = abrirNoModal(els = els)
        advanceUntilIdle()

        vm.executar(AcaoDoElemento.CONFIRMAR, jon)
        advanceUntilIdle()

        assertEquals(7, vm.estado.value.emModal)
        assertEquals("Não consegui falar com o servidor.", vm.estado.value.mensagens[7]?.texto)
    }

    @Test
    fun `E44 criar com erro mantem o modal e o dialogo`() = runTest {
        val els = ElementosFalso().apply { criacao = ResultadoDaChamada.Falha("Já existe.", 409) }
        val vm = abrirNoModal(els = els, id = 8)
        advanceUntilIdle()
        vm.executar(AcaoDoElemento.CRIAR, ned)

        vm.confirmarCriacao("PERSONAGEM", "Ned", "")
        advanceUntilIdle()

        assertEquals(8, vm.estado.value.emModal)
        assertTrue(vm.estado.value.dialogo is DialogoDeElemento.Criando)
    }

    @Test
    fun `E44 agir na sugestao de um modal fecha so o modal dela, nao o de outra`() = runTest {
        val vm = abrirNoModal(id = 7)
        advanceUntilIdle()

        vm.executar(AcaoDoElemento.DESCARTAR, ned) // a sugestão 8, enquanto o modal é da 7

        advanceUntilIdle()
        assertEquals(7, vm.estado.value.emModal)
    }

    @Test
    fun `ir a ficha e voltar - o modal continua aberto e as sugestoes sao relidas`() = runTest {
        val sug = sugestoes()
        val vm = PainelDeIaViewModel(5, sug, ElementosFalso())
        vm.abrirModal(7)
        advanceUntilIdle()
        val leituras = sug.leituras

        // O usuário abriu a ficha (outra tela) e voltou: a tela chama aoVoltarDaFicha.
        vm.aoVoltarDaFicha()
        advanceUntilIdle()

        assertEquals(7, vm.estado.value.emModal) // a mesma opção em que estava
        assertEquals(leituras + 1, sug.leituras) // com o que foi editado lá já refletido
    }

    @Test
    fun `um dialogo aberto a partir do modal tambem sobrevive a ida a ficha`() = runTest {
        val els = ElementosFalso().apply {
            lista = ResultadoDaChamada.Sucesso(listOf(com.allan.imagineer.rede.ElementoDoLivro(31, "PERSONAGEM", "Outro")))
        }
        val vm = PainelDeIaViewModel(5, sugestoes(), els)
        vm.abrirModal(8)
        advanceUntilIdle()
        vm.executar(AcaoDoElemento.VINCULAR, elemento(id = 8, nome = "Ned"))
        advanceUntilIdle()

        vm.aoVoltarDaFicha() // foi ver a ficha de "Outro" (Ver ficha, no diálogo de vincular) e voltou
        advanceUntilIdle()

        assertEquals(8, vm.estado.value.emModal)
        assertTrue(vm.estado.value.dialogo is DialogoDeElemento.Vinculando)
    }
}

/** A imagem no texto: o quadro e quais artefatos ganham imagem (item 7.5b, I1, I6, I7). */
class ImagemNoTextoTest {

    private fun ilustrado(rotulo: String, imagemId: Int?, orientacao: String?, situacao: String = "ILUSTRADO") = Artefato(
        tipo = "ELEMENTO", tipo_do_elemento = "PERSONAGEM", rotulo = rotulo, posicao_no_texto = 0, situacao = situacao,
        imagem_id = imagemId, imagem_orientacao = orientacao,
    )

    @Test
    fun `I1 o quadro vem da imagem real, e so a mais alta que larga e retrato`() {
        assertEquals(QuadroDaImagem.RETRATO, quadroDaImagem("RETRATO"))
        assertEquals(QuadroDaImagem.PAISAGEM, quadroDaImagem("PAISAGEM"))
    }

    @Test
    fun `I7 imagem sem dimensoes e tratada como paisagem`() {
        assertEquals(QuadroDaImagem.PAISAGEM, quadroDaImagem(null))
        assertEquals(QuadroDaImagem.PAISAGEM, quadroDaImagem(""))
    }

    @Test
    fun `I6 so os ilustrados com imagem ganham imagem no texto`() {
        val lista = listOf(
            ilustrado("A", 10, "RETRATO"),
            ilustrado("B", null, null, situacao = "PROMPT_PRONTO"),
            ilustrado("C", 11, "PAISAGEM", situacao = "CONFIRMADO"), // tem imagem_id mas não está ilustrado: não entra
            ilustrado("D", null, null), // ilustrado sem imagem (apagada): não entra
        )

        assertEquals(listOf("A"), imagensDoParagrafo(lista).map { it.rotulo })
    }

    @Test
    fun `I6 duas sugestoes do mesmo frame mostram a imagem uma vez so`() {
        val lista = listOf(ilustrado("A", 10, "RETRATO"), ilustrado("A de novo", 10, "RETRATO"), ilustrado("B", 12, "PAISAGEM"))

        assertEquals(listOf(10, 12), imagensDoParagrafo(lista).map { it.imagem_id })
    }

    @Test
    fun `o artefato do servidor traz o formato e as dimensoes da imagem`() {
        val json = """{"tipo":"ELEMENTO","tipo_do_elemento":"PERSONAGEM","rotulo":"Auri","posicao_no_texto":120,"situacao":"ILUSTRADO","imagem_id":9,"imagem_largura":1024,"imagem_altura":1536,"imagem_orientacao":"RETRATO"}"""

        val artefato = jsonDoImagineer.decodeFromString(Artefato.serializer(), json)

        assertEquals("RETRATO", artefato.imagem_orientacao)
        assertEquals(1024, artefato.imagem_largura)
        assertEquals(1536, artefato.imagem_altura)
    }

    @Test
    fun `artefato de servidor antigo, sem os campos novos, continua lendo`() {
        val json = """{"tipo":"CENA","rotulo":"A chegada","situacao":"SUGERIDO"}"""

        val artefato = jsonDoImagineer.decodeFromString(Artefato.serializer(), json)

        assertNull(artefato.imagem_orientacao)
    }
}
