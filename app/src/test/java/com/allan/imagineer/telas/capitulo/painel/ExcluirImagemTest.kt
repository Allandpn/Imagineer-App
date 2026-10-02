package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.extensaoDoTipo
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegrasDasAcoesDaImagemTest {

    @Test
    fun `U2 a extensao sai do tipo MIME e cai em png se nao reconhece`() {
        assertEquals("jpg", extensaoDoTipo("image/jpeg"))
        assertEquals("jpg", extensaoDoTipo("IMAGE/JPG"))
        assertEquals("webp", extensaoDoTipo("image/webp"))
        assertEquals("gif", extensaoDoTipo("image/gif"))
        assertEquals("png", extensaoDoTipo("image/png"))
        assertEquals("png", extensaoDoTipo("application/octet-stream"))
    }
}

/** Excluir a imagem no ViewModel do painel (U3). */
@OptIn(ExperimentalCoroutinesApi::class)
class ExcluirImagemNoPainelTest {

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

    private fun comImagens(prompts: PromptsFalso) {
        val lista = listOf(
            PromptDeFrame(
                id = 1, frame_id = 70, texto = "p1", total_de_imagens = 2,
                imagens = listOf(ImagemDoPrompt(id = 10, prompt_id = 1, origem = "GERADA"), ImagemDoPrompt(id = 11, prompt_id = 1, origem = "IMPORTADA")),
            ),
        )
        prompts.lista = ResultadoDaChamada.Sucesso(lista)
        prompts.detalhes[1] = lista.single()
    }

    @Test
    fun `U3 excluir pede confirmacao e nao apaga nada antes dela`() = runTest {
        val prompts = PromptsFalso().also { comImagens(it) }
        val vm = vm(prompts)
        vm.carregarPrompts(70); advanceUntilIdle()

        vm.pedirExcluirImagem(70, 1, 10, "GERADA")
        advanceUntilIdle()

        assertEquals(ImagemParaExcluir(70, 1, 10, "GERADA"), vm.estado.value.excluindoImagem)
        assertTrue(prompts.imagensRemovidas.isEmpty())
    }

    @Test
    fun `U3 cancelar a confirmacao nao apaga`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)
        vm.pedirExcluirImagem(70, 1, 10, "GERADA")

        vm.cancelarExclusaoDeImagem()
        advanceUntilIdle()

        assertNull(vm.estado.value.excluindoImagem)
        assertTrue(prompts.imagensRemovidas.isEmpty())
    }

    @Test
    fun `U3 confirmou - a imagem some da lista, a contagem desce e os artefatos sao relidos`() = runTest {
        val prompts = PromptsFalso().also { comImagens(it) }
        val vm = vm(prompts)
        vm.carregarPrompts(70); advanceUntilIdle()
        vm.pedirExcluirImagem(70, 1, 10, "GERADA")

        vm.confirmarExclusaoDeImagem()
        advanceUntilIdle()

        assertEquals(listOf(10), prompts.imagensRemovidas)
        val prompt = (vm.estado.value.prompts.getValue(70) as PromptsDoFrame.Pronto).lista.single()
        assertEquals(listOf(11), prompt.imagens.map { it.id }) // só a outra ficou
        assertEquals(1, prompt.total_de_imagens)
        assertEquals(1, vm.estado.value.versaoDosFrames) // o ícone no texto pode deixar de ser ILUSTRADO
        assertNull(vm.estado.value.excluindoImagem)
        assertEquals("Imagem excluída.", vm.estado.value.mensagensDeImagem.getValue(1).texto) // gerada: recado no cartão
    }

    @Test
    fun `U3 imagem importada leva o recado para a secao de importadas`() = runTest {
        val prompts = PromptsFalso().also { comImagens(it) }
        val vm = vm(prompts)
        vm.carregarPrompts(70); advanceUntilIdle()
        vm.pedirExcluirImagem(70, 1, 11, "IMPORTADA")

        vm.confirmarExclusaoDeImagem()
        advanceUntilIdle()

        assertEquals("Imagem excluída.", vm.estado.value.mensagensDeImportacao.getValue(1).texto)
        assertNull(vm.estado.value.mensagensDeImagem[1])
    }

    @Test
    fun `U3 falha do servidor mostra a mensagem e nao tira nada da lista`() = runTest {
        val prompts = PromptsFalso().also {
            comImagens(it)
            it.resultadoDaRemocao = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        }
        val vm = vm(prompts)
        vm.carregarPrompts(70); advanceUntilIdle()
        vm.pedirExcluirImagem(70, 1, 10, "GERADA")

        vm.confirmarExclusaoDeImagem()
        advanceUntilIdle()

        val prompt = (vm.estado.value.prompts.getValue(70) as PromptsDoFrame.Pronto).lista.single()
        assertEquals(listOf(10, 11), prompt.imagens.map { it.id })
        assertEquals(MensagemDoElemento("Não consegui falar com o servidor.", ehErro = true), vm.estado.value.mensagensDeImagem[1])
        assertEquals(0, vm.estado.value.versaoDosFrames)
    }

    @Test
    fun `U3 confirmar sem ter pedido nao faz nada`() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.confirmarExclusaoDeImagem()
        advanceUntilIdle()

        assertTrue(prompts.imagensRemovidas.isEmpty())
    }
}
