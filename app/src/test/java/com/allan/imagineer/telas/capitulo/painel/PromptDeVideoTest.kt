package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** As regras do prompt de vídeo no app (item 4.8, VD9). */
class RegrasDoPromptDeVideoTest {

    @Test
    fun a_imagem_de_partida_padrao_e_a_canonica_e_sem_ela_a_mais_recente() {
        val imagens = listOf(ImagemDoPrompt(id = 1, canonica = true), ImagemDoPrompt(id = 5), ImagemDoPrompt(id = 3))

        assertEquals(1, imagemDePartidaPadrao(imagens))
        assertEquals(5, imagemDePartidaPadrao(imagens.map { it.copy(canonica = false) }))
        assertNull(imagemDePartidaPadrao(emptyList()))
    }

    @Test
    fun os_videos_aparecem_do_mais_novo_para_o_mais_antigo() {
        val lista = listOf(PromptDeFrame(1, 70, "a"), PromptDeFrame(3, 70, "c"), PromptDeFrame(2, 70, "b"))

        assertEquals(listOf(3, 2, 1), videosDoMaisNovoParaOMaisAntigo(lista).map { it.id })
    }

    @Test
    fun o_prompt_de_servidor_antigo_sem_os_campos_novos_e_de_imagem() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        val antigo = json.decodeFromString<PromptDeFrame>("""{"id":1,"frame_id":2,"texto":"x"}""")
        val video = json.decodeFromString<PromptDeFrame>("""{"id":2,"frame_id":2,"texto":"y","tipo":"VIDEO","imagem_partida_id":9}""")

        assertEquals("IMAGEM", antigo.tipo)
        assertNull(antigo.imagem_partida_id)
        assertEquals("VIDEO", video.tipo)
        assertEquals(9, video.imagem_partida_id)
    }
}

/** O diálogo e a lista de prompts de vídeo no ViewModel do painel. */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptDeVideoNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() = Dispatchers.setMain(agendador)

    @After
    fun limpar() = Dispatchers.resetMain()

    private val jon = elemento(id = 1, nome = "Jon", elementoId = 3, estadoId = 9)
    private val sugestoes = SugestoesDeCapitulo(gerado_em = "2026-10-05T10:00:00", elementos = listOf(jon))
    private val imagens = listOf(ImagemDoPrompt(id = 10, canonica = true), ImagemDoPrompt(id = 11))

    private fun vm(prompts: PromptsFalso): PainelDeIaViewModel {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes))
        return PainelDeIaViewModel(
            5, repositorio, ElementosFalso(), prompts,
            ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
        ).also { it.definirLivro(1) }
    }

    @Test
    fun os_videos_do_frame_sao_lidos_uma_vez() = runTest {
        val prompts = PromptsFalso().also { it.videos = ResultadoDaChamada.Sucesso(listOf(PromptDeFrame(7, 80, "v", tipo = "VIDEO"))) }
        val vm = vm(prompts)

        vm.carregarVideos(80); advanceUntilIdle()
        vm.carregarVideos(80); advanceUntilIdle()

        assertEquals(1, prompts.videosListados)
        assertEquals(listOf(7), (vm.estado.value.videos[80] as VideosDoFrame.Pronto).lista.map { it.id })
    }

    @Test
    fun abrir_o_dialogo_ja_vem_com_a_canonica_escolhida_e_sem_imagem_nao_abre() = runTest {
        val vm = vm(PromptsFalso())

        vm.abrirDialogoDeVideo(80, "A partida", emptyList())
        assertNull(vm.estado.value.dialogoDeVideo)

        vm.abrirDialogoDeVideo(80, "A partida", imagens)
        assertEquals(10, vm.estado.value.dialogoDeVideo?.escolhida)
    }

    @Test
    fun gerar_manda_a_imagem_escolhida_e_o_comentario_fecha_o_dialogo_e_poe_o_prompt_na_frente() = runTest {
        val prompts = PromptsFalso().also { it.videos = ResultadoDaChamada.Sucesso(listOf(PromptDeFrame(7, 80, "antigo", tipo = "VIDEO"))) }
        val vm = vm(prompts)
        vm.carregarVideos(80); advanceUntilIdle()
        vm.abrirDialogoDeVideo(80, "A partida", imagens)
        vm.escolherImagemDoVideo(11)
        vm.mudarComentarioDoVideo("só um movimento lento")

        vm.gerarVideo(); advanceUntilIdle()

        assertEquals(listOf(Triple(80, 11, "só um movimento lento")), prompts.videosPedidos)
        assertNull(vm.estado.value.dialogoDeVideo)
        val lista = (vm.estado.value.videos[80] as VideosDoFrame.Pronto).lista
        assertEquals(listOf(7, 300), lista.map { it.id })
        assertEquals(300, videosDoMaisNovoParaOMaisAntigo(lista).first().id)
    }

    @Test
    fun a_recusa_do_servidor_fica_no_dialogo_que_continua_aberto() = runTest {
        val prompts = PromptsFalso().also { it.geracaoDeVideo = ResultadoDaChamada.Falha("A imagem de partida tem de ser uma imagem deste frame.") }
        val vm = vm(prompts)
        vm.abrirDialogoDeVideo(80, "A partida", imagens)

        vm.gerarVideo(); advanceUntilIdle()

        val dialogo = vm.estado.value.dialogoDeVideo
        assertNotNull(dialogo)
        assertEquals("A imagem de partida tem de ser uma imagem deste frame.", dialogo!!.erro)
        assertFalse(dialogo.gerando)
    }

    @Test
    fun nao_gera_duas_vezes_e_nao_fecha_enquanto_gera() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)
        vm.abrirDialogoDeVideo(80, "A partida", imagens)

        vm.gerarVideo()
        vm.gerarVideo()
        vm.fecharDialogoDeVideo()  // gerando: não fecha
        assertTrue(vm.estado.value.dialogoDeVideo?.gerando == true)
        advanceUntilIdle()

        assertEquals(1, prompts.videosPedidos.size)
    }
}
