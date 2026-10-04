package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.RepositorioDePrompts
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun prompt(id: Int, soImagem: Boolean = false) = PromptDeFrame(id = id, frame_id = 70, texto = "prompt $id", so_imagem = soImagem)

private fun foto(nome: String = "minha.png") = ArquivoEscolhido("content://x/$nome", nome, 2_000_000)

/** Um repositório que registra a importação **para o frame** e devolve o que o teste combinar na leitura seguinte. */
private class PromptsDeImportacao(private val base: PromptsFalso = PromptsFalso()) : RepositorioDePrompts by base {
    val paraOFrame = mutableListOf<Int>()
    val paraUmPrompt = mutableListOf<Int>()
    var falha: String? = null
    var depois: List<PromptDeFrame> = emptyList()

    override suspend fun importarImagemParaOFrame(frameId: Int, arquivo: ArquivoEscolhido, aoProgredir: (Long, Long?) -> Unit): ResultadoDaChamada<ImagemDoPrompt> {
        paraOFrame += frameId
        falha?.let { return ResultadoDaChamada.Falha(it) }
        base.lista = ResultadoDaChamada.Sucesso(depois)
        return ResultadoDaChamada.Sucesso(ImagemDoPrompt(id = 500, prompt_id = depois.firstOrNull()?.id ?: 0))
    }

    override suspend fun importarImagem(promptId: Int, arquivo: ArquivoEscolhido, aoProgredir: (Long, Long?) -> Unit): ResultadoDaChamada<ImagemDoPrompt> {
        paraUmPrompt += promptId
        return ResultadoDaChamada.Sucesso(ImagemDoPrompt(id = 501, prompt_id = promptId))
    }
}

/** Importar uma imagem para um frame que ainda não tem prompt (PI1, PI4). */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportarSemPromptTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private fun vm(prompts: RepositorioDePrompts) = PainelDeIaViewModel(5, SugestoesFalso(), ElementosFalso(), prompts)

    @Test
    fun o_prompt_so_da_imagem_nao_conta_como_prompt() {
        val lista = listOf(prompt(3), prompt(2, soImagem = true), prompt(1))

        assertEquals(listOf(3, 1), promptsComTexto(lista).map { it.id })
        assertEquals(2, numeroDoPrompt(promptsComTexto(lista), 3))  // a numeração ignora o que não é prompt: o mais novo é o 2
        assertEquals(1, numeroDoPrompt(promptsComTexto(lista), 1))
    }

    @Test
    fun a_chave_de_importacao_do_frame_nunca_bate_com_um_id_de_prompt() {
        assertEquals(-70, chaveDeImportacaoDoFrame(70))
        assertTrue(chaveDeImportacaoDoFrame(70) < 0)
    }

    @Test
    fun sem_prompt_a_imagem_vai_para_o_frame_e_a_lista_e_relida() = runTest {
        val repositorio = PromptsDeImportacao().also { it.depois = listOf(prompt(9, soImagem = true)) }
        val vm = vm(repositorio)

        vm.importarImagem(70, SEM_PROMPT, foto())
        advanceUntilIdle()

        assertEquals(listOf(70), repositorio.paraOFrame)
        assertTrue(repositorio.paraUmPrompt.isEmpty())
        assertEquals("Imagem importada.", vm.estado.value.mensagensDeImportacao[chaveDeImportacaoDoFrame(70)]?.texto)
        val lista = (vm.estado.value.prompts[70] as PromptsDoFrame.Pronto).lista
        assertEquals(listOf(9), lista.map { it.id })  // o prompt só da imagem aparece depois de relido
        assertTrue(vm.estado.value.importandoImagem.isEmpty())
    }

    @Test
    fun com_prompt_continua_indo_para_o_prompt() = runTest {
        val repositorio = PromptsDeImportacao()
        val vm = vm(repositorio)

        vm.importarImagem(70, 4, foto())
        advanceUntilIdle()

        assertEquals(listOf(4), repositorio.paraUmPrompt)
        assertTrue(repositorio.paraOFrame.isEmpty())
    }

    @Test
    fun falha_do_servidor_vira_recado_do_frame_e_nada_fica_enviando() = runTest {
        val repositorio = PromptsDeImportacao().also { it.falha = "Escolha uma imagem." }
        val vm = vm(repositorio)

        vm.importarImagem(70, SEM_PROMPT, foto())
        advanceUntilIdle()

        val recado = vm.estado.value.mensagensDeImportacao[chaveDeImportacaoDoFrame(70)]!!
        assertEquals("Escolha uma imagem.", recado.texto)
        assertTrue(recado.ehErro)
        assertTrue(vm.estado.value.importandoImagem.isEmpty())
    }

    @Test
    fun arquivo_que_nao_e_imagem_e_recusado_antes_de_enviar() = runTest {
        val repositorio = PromptsDeImportacao()
        val vm = vm(repositorio)

        vm.importarImagem(70, SEM_PROMPT, foto("livro.epub"))
        advanceUntilIdle()

        assertTrue(repositorio.paraOFrame.isEmpty())
        assertTrue(vm.estado.value.mensagensDeImportacao[chaveDeImportacaoDoFrame(70)]!!.ehErro)
    }
}
