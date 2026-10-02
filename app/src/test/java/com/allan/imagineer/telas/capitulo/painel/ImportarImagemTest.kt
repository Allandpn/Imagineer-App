package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.enderecoDaImagem
import com.allan.imagineer.rede.extensaoDaImagem
import com.allan.imagineer.rede.nomeParaEnviar
import com.allan.imagineer.rede.motivoParaNaoImportar
import com.allan.imagineer.rede.tipoDaImagem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun foto(nome: String = "retrato.png", tamanho: Long? = 2_000_000) = ArquivoEscolhido("content://x/$nome", nome, tamanho)

/** As regras puras da importação (J2, J4, J7). */
class RegrasDaImportacaoDeImagemTest {

    @Test
    fun `J2 aceita as extensoes do servidor, em qualquer caixa`() {
        listOf("a.png", "a.JPG", "a.jpeg", "a.WebP", "a.gif").forEach { assertNull(it, motivoParaNaoImportar(foto(it))) }
    }

    @Test
    fun `J2 recusa o que nao e imagem, antes de enviar`() {
        assertEquals("Escolha uma imagem PNG, JPG, WEBP ou GIF.", motivoParaNaoImportar(foto("livro.epub")))
        assertEquals("Escolha uma imagem PNG, JPG, WEBP ou GIF.", motivoParaNaoImportar(foto("semextensao")))
    }

    @Test
    fun `J2 recusa o que passa de 25 MB mas aceita exatamente 25 MB`() {
        assertEquals("A imagem passa de 25 MB, o limite do servidor.", motivoParaNaoImportar(foto(tamanho = 25L * 1024 * 1024 + 1)))
        assertNull(motivoParaNaoImportar(foto(tamanho = 25L * 1024 * 1024)))
        assertNull("tamanho desconhecido: o servidor confere", motivoParaNaoImportar(foto(tamanho = null)))
    }

    @Test
    fun `J2 nome sem extensao vale pelo tipo MIME que o seletor informou`() {
        // Alguns seletores entregam nomes como "image-3f2a": a foto é válida, o nome é que não ajuda.
        assertNull(motivoParaNaoImportar(ArquivoEscolhido("content://x/1", "image-3f2a", 1000, tipo = "image/jpeg")))
        assertNull(motivoParaNaoImportar(ArquivoEscolhido("content://x/1", "1000012345", 1000, tipo = "image/PNG")))
        assertEquals("jpg", extensaoDaImagem(ArquivoEscolhido("content://x/1", "image-3f2a", 1000, tipo = "image/jpeg")))
    }

    @Test
    fun `J2 sem extensao e sem tipo de imagem continua recusando`() {
        assertEquals("Escolha uma imagem PNG, JPG, WEBP ou GIF.", motivoParaNaoImportar(ArquivoEscolhido("content://x/1", "arquivo", 1000, tipo = null)))
        assertEquals("Escolha uma imagem PNG, JPG, WEBP ou GIF.", motivoParaNaoImportar(ArquivoEscolhido("content://x/1", "doc", 1000, tipo = "application/pdf")))
        assertEquals("Escolha uma imagem PNG, JPG, WEBP ou GIF.", motivoParaNaoImportar(ArquivoEscolhido("content://x/1", "foto.heic", 1000, tipo = "image/heic")))
    }

    @Test
    fun `J2 o nome enviado ganha a extensao do tipo quando o original nao tem`() {
        assertEquals("image-3f2a.jpg", nomeParaEnviar(ArquivoEscolhido("content://x/1", "image-3f2a", 1000, tipo = "image/jpeg")))
        assertEquals("retrato.png", nomeParaEnviar(ArquivoEscolhido("content://x/1", "retrato.png", 1000, tipo = "image/png")))
        assertEquals("foto.JPG", nomeParaEnviar(ArquivoEscolhido("content://x/1", "foto.JPG", 1000)))
    }

    @Test
    fun `o tipo do arquivo vem da extensao`() {
        assertEquals("image/jpeg", tipoDaImagem("a.JPG"))
        assertEquals("image/png", tipoDaImagem("a.png"))
        assertEquals("application/octet-stream", tipoDaImagem("a.bin"))
    }

    @Test
    fun `J4 o endereco da imagem leva o tamanho pedido e ignora a barra final da URL`() {
        assertEquals("http://100.1.2.3:8000/imagens/7/arquivo?tamanho=miniatura", enderecoDaImagem("http://100.1.2.3:8000/", 7, "miniatura"))
        assertEquals("http://h/imagens/7/arquivo?tamanho=original", enderecoDaImagem("http://h", 7, "original"))
    }
}

/** A importação no ViewModel do painel (J2, J3, J5, J6). */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportarImagemNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(agendador)
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun vm(prompts: PromptsFalso): PainelDeIaViewModel {
        val repositorio = SugestoesFalso()
        return PainelDeIaViewModel(
            5, repositorio, ElementosFalso(), prompts,
            ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
        )
    }

    private fun prompt(id: Int, imagens: Int = 0) = PromptDeFrame(id = id, frame_id = 70, texto = "p$id", total_de_imagens = imagens)

    @Test
    fun `J3 importa, poe a imagem nova na frente e pede para reler os artefatos`() = runTest {
        val prompts = PromptsFalso().also {
            it.lista = ResultadoDaChamada.Sucesso(listOf(prompt(1, imagens = 1)))
            it.detalhes[1] = prompt(1, 1).copy(imagens = listOf(ImagemDoPrompt(id = 400, prompt_id = 1)))
            it.resultadoDaImportacao = ResultadoDaChamada.Sucesso(ImagemDoPrompt(id = 500, prompt_id = 1))
        }
        val vm = vm(prompts)
        vm.carregarPrompts(70); advanceUntilIdle()

        vm.importarImagem(70, 1, foto())
        advanceUntilIdle()

        val lista = (vm.estado.value.prompts.getValue(70) as PromptsDoFrame.Pronto).lista
        assertEquals(listOf(500, 400), lista.single().imagens.map { it.id }) // a mais nova primeiro
        assertEquals(2, lista.single().total_de_imagens)
        assertEquals(1, vm.estado.value.versaoDosFrames) // J6
        assertEquals(MensagemDoElemento("Imagem importada.", ehErro = false), vm.estado.value.mensagensDeImportacao[1])
        assertTrue(vm.estado.value.importandoImagem.isEmpty())
    }

    @Test
    fun `J2 arquivo que nao e imagem nao e enviado e diz o motivo`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.importarImagem(70, 1, foto("livro.epub"))
        advanceUntilIdle()

        assertTrue(prompts.importacoes.isEmpty())
        assertEquals(MensagemDoElemento("Escolha uma imagem PNG, JPG, WEBP ou GIF.", ehErro = true), vm.estado.value.mensagensDeImportacao[1])
        assertEquals(0, vm.estado.value.versaoDosFrames)
    }

    @Test
    fun `J2 arquivo que o app nao conseguiu descrever diz que nao conseguiu abrir`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.importarImagem(70, 1, null)
        advanceUntilIdle()

        assertTrue(prompts.importacoes.isEmpty())
        assertEquals(MensagemDoElemento("Não consegui abrir o arquivo escolhido.", ehErro = true), vm.estado.value.mensagensDeImportacao[1])
    }

    @Test
    fun `J3 um envio por prompt de cada vez, com o progresso`() = runTest {
        val trava = CompletableDeferred<Unit>()
        val prompts = PromptsFalso().also { it.travaDaImportacao = trava }
        val vm = vm(prompts)

        vm.importarImagem(70, 1, foto())
        runCurrent()
        assertEquals(0.5f, vm.estado.value.importandoImagem[1]) // o falso reportou 50 de 100
        vm.importarImagem(70, 1, foto("outra.png")) // toque repetido
        trava.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, prompts.importacoes.size)
    }

    @Test
    fun `J3 falha mostra a mensagem do servidor, nao mexe na lista e deixa tentar de novo`() = runTest {
        val prompts = PromptsFalso().also {
            it.resultadoDaImportacao = ResultadoDaChamada.Falha("Extensão não suportada.", codigoHttp = 422)
        }
        val vm = vm(prompts)

        vm.importarImagem(70, 1, foto())
        advanceUntilIdle()

        assertEquals(MensagemDoElemento("Extensão não suportada.", ehErro = true), vm.estado.value.mensagensDeImportacao[1])
        assertEquals(0, vm.estado.value.versaoDosFrames)
        assertTrue(vm.estado.value.importandoImagem.isEmpty())

        prompts.resultadoDaImportacao = ResultadoDaChamada.Sucesso(ImagemDoPrompt(id = 501, prompt_id = 1))
        vm.importarImagem(70, 1, foto())
        advanceUntilIdle()
        assertEquals(2, prompts.importacoes.size)
        assertEquals("Imagem importada.", vm.estado.value.mensagensDeImportacao.getValue(1).texto) // o erro antigo some
    }

    @Test
    fun `J5 ao listar, so os prompts que ja tem imagem pedem o detalhe`() = runTest {
        val prompts = PromptsFalso().also {
            it.lista = ResultadoDaChamada.Sucesso(listOf(prompt(1, imagens = 0), prompt(2, imagens = 2)))
            it.detalhes[2] = prompt(2, 2).copy(imagens = listOf(ImagemDoPrompt(id = 10), ImagemDoPrompt(id = 11)))
        }
        val vm = vm(prompts)

        vm.carregarPrompts(70); advanceUntilIdle()

        assertEquals(listOf(2), prompts.detalhesPedidos)
        val lista = (vm.estado.value.prompts.getValue(70) as PromptsDoFrame.Pronto).lista
        assertEquals(listOf(2, 1), lista.map { it.id }) // o mais novo primeiro (G2)
        assertEquals(listOf(10, 11), lista.first { it.id == 2 }.imagens.map { it.id })
    }

    @Test
    fun `J5 se o detalhe falha, o prompt aparece sem as miniaturas`() = runTest {
        val prompts = PromptsFalso().also { it.lista = ResultadoDaChamada.Sucesso(listOf(prompt(2, imagens = 1))) } // sem detalhe combinado
        val vm = vm(prompts)

        vm.carregarPrompts(70); advanceUntilIdle()

        val lista = (vm.estado.value.prompts.getValue(70) as PromptsDoFrame.Pronto).lista
        assertEquals(listOf(2), lista.map { it.id })
        assertTrue(lista.single().imagens.isEmpty())
    }

    @Test
    fun `J2 o seletor devolve o arquivo ao prompt que pediu, mesmo com o modal ja fechado`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.escolherImagemPara(70, 3)
        assertEquals(AlvoDaImportacao(70, 3), vm.estado.value.alvoDaImportacao)
        vm.imagemEscolhida(foto(), cancelou = false)
        advanceUntilIdle()

        assertEquals(3, prompts.importacoes.single().first)
        assertNull(vm.estado.value.alvoDaImportacao) // o alvo vale para uma escolha só
    }

    @Test
    fun `J2 cancelar o seletor nao importa nada nem mostra erro`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)
        vm.escolherImagemPara(70, 3)

        vm.imagemEscolhida(null, cancelou = true)
        advanceUntilIdle()

        assertTrue(prompts.importacoes.isEmpty())
        assertTrue(vm.estado.value.mensagensDeImportacao.isEmpty())
        assertNull(vm.estado.value.alvoDaImportacao)
    }

    @Test
    fun `J2 resultado do seletor sem alvo e ignorado`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.imagemEscolhida(foto(), cancelou = false)
        advanceUntilIdle()

        assertTrue(prompts.importacoes.isEmpty())
    }
}
