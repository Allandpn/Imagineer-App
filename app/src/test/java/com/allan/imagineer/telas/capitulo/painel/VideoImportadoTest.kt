package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import com.allan.imagineer.rede.VideoImportado
import com.allan.imagineer.rede.enderecoDoVideo
import com.allan.imagineer.rede.extensaoDoVideo
import com.allan.imagineer.rede.motivoParaNaoImportarVideo
import com.allan.imagineer.rede.nomeDoVideoParaEnviar
import com.allan.imagineer.rede.tamanhoDoVideoParaLer
import com.allan.imagineer.rede.tipoDoVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun arquivo(nome: String, tamanho: Long? = 1_000L, tipo: String? = null) = ArquivoEscolhido(uri = "content://video/1", nome = nome, tamanho = tamanho, tipo = tipo)

/** O que o app confere do vídeo antes de enviar e como lê o que o servidor devolve (VD16 a VD19). */
class RegrasDoVideoImportadoTest {

    @Test
    fun aceita_mp4_mov_webm_e_m4v_e_recusa_o_resto() {
        for (nome in listOf("cena.mp4", "CENA.MOV", "cena.webm", "cena.m4v")) assertNull(nome, motivoParaNaoImportarVideo(arquivo(nome)))

        assertEquals("Escolha um vídeo MP4, MOV ou WEBM.", motivoParaNaoImportarVideo(arquivo("cena.avi")))
        assertEquals("Escolha um vídeo MP4, MOV ou WEBM.", motivoParaNaoImportarVideo(arquivo("cena")))
    }

    @Test
    fun o_seletor_sem_extensao_no_nome_usa_o_tipo_que_ele_informou() {
        val sem = arquivo("video-3f2a", tipo = "video/mp4")

        assertEquals("mp4", extensaoDoVideo(sem))
        assertNull(motivoParaNaoImportarVideo(sem))
        assertEquals("video-3f2a.mp4", nomeDoVideoParaEnviar(sem))
        assertEquals("cena.mov", nomeDoVideoParaEnviar(arquivo("cena.mov")))  // já tem extensão aceita: o nome fica
        assertEquals("webm", extensaoDoVideo(arquivo("x", tipo = "video/webm")))
    }

    @Test
    fun acima_de_200_mb_o_app_recusa_antes_de_enviar() {
        assertNull(motivoParaNaoImportarVideo(arquivo("a.mp4", tamanho = 200L * 1024 * 1024)))
        assertEquals("O vídeo passa de 200 MB, o limite do servidor.", motivoParaNaoImportarVideo(arquivo("a.mp4", tamanho = 200L * 1024 * 1024 + 1)))
        assertNull(motivoParaNaoImportarVideo(arquivo("a.mp4", tamanho = null)))  // tamanho desconhecido: o servidor decide
    }

    @Test
    fun o_tipo_de_midia_vem_da_extensao() {
        assertEquals("video/mp4", tipoDoVideo("a.mp4"))
        assertEquals("video/quicktime", tipoDoVideo("a.MOV"))
        assertEquals("video/webm", tipoDoVideo("a.webm"))
        assertEquals("video/mp4", tipoDoVideo("sem-extensao"))
    }

    @Test
    fun o_endereco_do_fluxo_e_o_do_arquivo_e_o_tamanho_se_le_em_portugues() {
        assertEquals("http://100.1.1.1:8000/videos/7/arquivo", enderecoDoVideo("http://100.1.1.1:8000/", 7))
        assertEquals("12,4 MB", tamanhoDoVideoParaLer((12.4 * 1024 * 1024).toLong()))
        assertEquals("512 KB", tamanhoDoVideoParaLer(512L * 1024))
    }

    @Test
    fun o_video_do_servidor_decodifica_e_o_artefato_antigo_nao_tem_video() {
        val json = Json { ignoreUnknownKeys = true }

        val video = json.decodeFromString<VideoImportado>("""{"id":1,"frame_id":2,"prompt_id":null,"nome_original":"a.mp4","tamanho_em_bytes":2048,"data_importacao":"2026-10-05T10:00:00","no_texto":true,"novo":1}""")
        val antigo = json.decodeFromString<Artefato>("""{"tipo":"CENA","rotulo":"A cena","situacao":"ILUSTRADO","imagem_id":3}""")
        val comVideo = json.decodeFromString<Artefato>("""{"tipo":"CENA","rotulo":"A cena","situacao":"ILUSTRADO","imagem_id":3,"video_id":9}""")

        assertTrue(video.no_texto)
        assertEquals(2048L, video.tamanho_em_bytes)
        assertNull(antigo.video_id)
        assertEquals(9, comVideo.video_id)
    }

    @Test
    fun a_lista_de_prompts_de_video_separa_os_visiveis_dos_ocultos() {
        val lista = listOf(
            PromptDeFrame(1, 80, "a", tipo = "VIDEO"),
            PromptDeFrame(2, 80, "b", tipo = "VIDEO", oculto = true),
            PromptDeFrame(3, 80, "c", tipo = "VIDEO"),
        )

        assertEquals(listOf(3, 1), promptsDeVideoVisiveis(lista).map { it.id })
        assertEquals(listOf(2), promptsDeVideoOcultos(lista).map { it.id })
        assertFalse(json_sem_oculto().oculto)  // servidor antigo: sem o campo, o prompt é visível
    }

    private fun json_sem_oculto() = Json { ignoreUnknownKeys = true }.decodeFromString<PromptDeFrame>("""{"id":1,"frame_id":2,"texto":"x","tipo":"VIDEO"}""")

    @Test
    fun os_rotulos_dizem_quantos_ha_e_o_que_o_toque_faz() {
        assertEquals("Ver prompts de vídeo (3)", rotuloDeVerPromptsDeVideo(aberto = false, total = 3))
        assertEquals("Esconder prompts de vídeo", rotuloDeVerPromptsDeVideo(aberto = true, total = 3))
        assertEquals("Ocultos (2)", rotuloDosPromptsOcultos(aberto = false, total = 2))
        assertEquals("Esconder os ocultos", rotuloDosPromptsOcultos(aberto = true, total = 2))
    }

    @Test
    fun a_linha_do_video_diz_o_tamanho_e_de_qual_prompt_veio() {
        val sem = VideoImportado(id = 1, frame_id = 2, tamanho_em_bytes = 3L * 1024 * 1024)

        assertEquals("3,0 MB", descreverVideoImportado(sem))
        assertEquals("3,0 MB · de um prompt de vídeo", descreverVideoImportado(sem.copy(prompt_id = 7)))
    }
}

/** O vídeo importado e o prompt de vídeo no ViewModel do painel (VD12, VD13, VD16 a VD18). */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoImportadoNoPainelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() = Dispatchers.setMain(agendador)

    @After
    fun limpar() = Dispatchers.resetMain()

    private val jon = elemento(id = 1, nome = "Jon", elementoId = 3, estadoId = 9)
    private val sugestoes = SugestoesDeCapitulo(gerado_em = "2026-10-05T10:00:00", elementos = listOf(jon))

    private fun vm(prompts: PromptsFalso): PainelDeIaViewModel {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(sugestoes))
        return PainelDeIaViewModel(
            5, repositorio, ElementosFalso(), prompts,
            ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + agendador), prompts),
        ).also { it.definirLivro(1) }
    }

    private fun importado(id: Int, noTexto: Boolean = false) = VideoImportado(id = id, frame_id = 80, tamanho_em_bytes = 1000, no_texto = noTexto)

    @Test
    fun os_videos_importados_sao_lidos_uma_vez() = runTest {
        val prompts = PromptsFalso().also { it.videosImportadosDoFrame = ResultadoDaChamada.Sucesso(listOf(importado(1))) }
        val vm = vm(prompts)

        vm.carregarVideosImportados(80); advanceUntilIdle()
        vm.carregarVideosImportados(80); advanceUntilIdle()

        assertEquals(1, prompts.videosImportadosListados)
        assertEquals(listOf(1), (vm.estado.value.videosImportados[80] as VideosImportadosDoFrame.Pronto).lista.map { it.id })
    }

    @Test
    fun importar_manda_o_arquivo_com_o_prompt_de_origem_e_poe_o_video_na_frente() = runTest {
        val prompts = PromptsFalso().also { it.videosImportadosDoFrame = ResultadoDaChamada.Sucesso(listOf(importado(1))) }
        val vm = vm(prompts)
        vm.carregarVideosImportados(80); advanceUntilIdle()

        vm.importarVideo(80, promptId = 7, arquivo = arquivo("cena.mp4")); advanceUntilIdle()

        assertEquals(listOf(Triple(80, "cena.mp4", 7)), prompts.videosEnviados)
        assertEquals(listOf(500, 1), (vm.estado.value.videosImportados[80] as VideosImportadosDoFrame.Pronto).lista.map { it.id })
        assertTrue(80 !in vm.estado.value.importandoVideo)
        assertEquals("Vídeo importado.", vm.estado.value.mensagensDeVideo[80]?.texto)
    }

    @Test
    fun a_extensao_ou_o_tamanho_errados_nem_chegam_ao_servidor() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.importarVideo(80, null, arquivo("cena.avi")); advanceUntilIdle()
        assertEquals("Escolha um vídeo MP4, MOV ou WEBM.", vm.estado.value.mensagensDeVideo[80]?.texto)
        vm.importarVideo(80, null, null); advanceUntilIdle()
        assertEquals("Não consegui abrir o arquivo escolhido.", vm.estado.value.mensagensDeVideo[80]?.texto)
        vm.importarVideo(80, null, arquivo("cena.mp4", tamanho = 300L * 1024 * 1024)); advanceUntilIdle()

        assertTrue(prompts.videosEnviados.isEmpty())
        assertTrue(vm.estado.value.mensagensDeVideo[80]!!.ehErro)
    }

    @Test
    fun a_recusa_do_servidor_aparece_e_o_envio_termina() = runTest {
        val prompts = PromptsFalso().also { it.envioDeVideo = ResultadoDaChamada.Falha("O vídeo passa do limite de 200 MB.") }
        val vm = vm(prompts)

        vm.importarVideo(80, null, arquivo("cena.mp4")); advanceUntilIdle()

        assertEquals("O vídeo passa do limite de 200 MB.", vm.estado.value.mensagensDeVideo[80]?.texto)
        assertTrue(vm.estado.value.mensagensDeVideo[80]!!.ehErro)
        assertTrue(80 !in vm.estado.value.importandoVideo)
    }

    @Test
    fun escolher_o_video_do_texto_liga_so_ele_e_a_tela_rele_os_artefatos() = runTest {
        val prompts = PromptsFalso().also { it.videosImportadosDoFrame = ResultadoDaChamada.Sucesso(listOf(importado(2), importado(1, noTexto = true))) }
        val vm = vm(prompts)
        vm.carregarVideosImportados(80); advanceUntilIdle()
        val versao = vm.estado.value.versaoDosFrames

        vm.definirVideoNoTexto(80, 2); advanceUntilIdle()

        assertEquals(listOf(80 to 2), prompts.videosNoTexto)
        val lista = (vm.estado.value.videosImportados[80] as VideosImportadosDoFrame.Pronto).lista
        assertEquals(listOf(2 to true, 1 to false), lista.map { it.id to it.no_texto })
        assertEquals(versao + 1, vm.estado.value.versaoDosFrames)

        vm.definirVideoNoTexto(80, null); advanceUntilIdle()  // volta à imagem
        assertTrue((vm.estado.value.videosImportados[80] as VideosImportadosDoFrame.Pronto).lista.none { it.no_texto })
    }

    @Test
    fun se_o_servidor_recusa_a_escolha_nada_muda_e_avisa() = runTest {
        val prompts = PromptsFalso().also {
            it.videosImportadosDoFrame = ResultadoDaChamada.Sucesso(listOf(importado(1)))
            it.escolhaDoVideoNoTexto = ResultadoDaChamada.Falha("Este vídeo não é deste frame.")
        }
        val vm = vm(prompts)
        vm.carregarVideosImportados(80); advanceUntilIdle()

        vm.definirVideoNoTexto(80, 1); advanceUntilIdle()

        assertFalse((vm.estado.value.videosImportados[80] as VideosImportadosDoFrame.Pronto).lista.single().no_texto)
        assertEquals("Este vídeo não é deste frame.", vm.estado.value.mensagensDeVideo[80]?.texto)
    }

    @Test
    fun apagar_tira_da_lista_e_se_era_o_do_texto_a_tela_rele_os_artefatos() = runTest {
        val prompts = PromptsFalso().also { it.videosImportadosDoFrame = ResultadoDaChamada.Sucesso(listOf(importado(2, noTexto = true), importado(1))) }
        val vm = vm(prompts)
        vm.carregarVideosImportados(80); advanceUntilIdle()
        val versao = vm.estado.value.versaoDosFrames

        vm.apagarVideo(80, 1); advanceUntilIdle()
        assertEquals(versao, vm.estado.value.versaoDosFrames)  // não era o do texto: o capítulo não muda
        vm.apagarVideo(80, 2); advanceUntilIdle()

        assertEquals(listOf(1, 2), prompts.videosApagados)
        assertTrue((vm.estado.value.videosImportados[80] as VideosImportadosDoFrame.Pronto).lista.isEmpty())
        assertEquals(versao + 1, vm.estado.value.versaoDosFrames)
    }

    @Test
    fun ocultar_e_mostrar_o_prompt_de_video_atualiza_a_lista() = runTest {
        val prompts = PromptsFalso().also { it.videos = ResultadoDaChamada.Sucesso(listOf(PromptDeFrame(7, 80, "a", tipo = "VIDEO"), PromptDeFrame(8, 80, "b", tipo = "VIDEO"))) }
        val vm = vm(prompts)
        vm.carregarVideos(80); advanceUntilIdle()

        vm.ocultarPromptDeVideo(80, 7, oculto = true); advanceUntilIdle()
        val depois = (vm.estado.value.videos[80] as VideosDoFrame.Pronto).lista
        assertEquals(listOf(7), promptsDeVideoOcultos(depois).map { it.id })
        assertEquals(listOf(8), promptsDeVideoVisiveis(depois).map { it.id })

        vm.ocultarPromptDeVideo(80, 7, oculto = false); advanceUntilIdle()
        assertTrue(promptsDeVideoOcultos((vm.estado.value.videos[80] as VideosDoFrame.Pronto).lista).isEmpty())
    }

    @Test
    fun salvar_o_texto_troca_o_prompt_na_lista_com_o_portugues_junto() = runTest {
        val prompts = PromptsFalso().also { it.videos = ResultadoDaChamada.Sucesso(listOf(PromptDeFrame(7, 80, "velho", tipo = "VIDEO"))) }
        val vm = vm(prompts)
        vm.carregarVideos(80); advanceUntilIdle()

        vm.salvarPromptDeVideo(80, 7, "a slow pull-back", "um recuo lento"); advanceUntilIdle()

        val salvo = (vm.estado.value.videos[80] as VideosDoFrame.Pronto).lista.single()
        assertEquals("a slow pull-back", salvo.texto)
        assertEquals("um recuo lento", salvo.texto_pt)
        assertEquals(Triple(7, null, "a slow pull-back"), prompts.ajustesDePromptDeVideo.single())
    }

    @Test
    fun a_falha_ao_ocultar_ou_salvar_vira_aviso_e_a_lista_nao_muda() = runTest {
        val prompts = PromptsFalso().also {
            it.videos = ResultadoDaChamada.Sucesso(listOf(PromptDeFrame(7, 80, "velho", tipo = "VIDEO")))
            it.resultadoDoAjuste = { _, _, _, _ -> ResultadoDaChamada.Falha("Sem conexão.") }
        }
        val vm = vm(prompts)
        vm.carregarVideos(80); advanceUntilIdle()

        vm.ocultarPromptDeVideo(80, 7, oculto = true); advanceUntilIdle()
        vm.salvarPromptDeVideo(80, 7, "novo", null); advanceUntilIdle()

        assertEquals("velho", (vm.estado.value.videos[80] as VideosDoFrame.Pronto).lista.single().texto)
        assertFalse((vm.estado.value.videos[80] as VideosDoFrame.Pronto).lista.single().oculto)
        assertNotNull(vm.estado.value.mensagensDeVideo[80])
    }

    @Test
    fun o_alvo_do_seletor_sobrevive_ate_o_arquivo_voltar_e_so_entao_envia() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.escolherVideoPara(80, promptId = 7)
        assertEquals(AlvoDaImportacaoDeVideo(80, 7), vm.estado.value.alvoDoVideo)  // o modal pode sumir: o alvo fica no ViewModel
        vm.videoEscolhido(arquivo("cena.mp4"), cancelou = false); advanceUntilIdle()

        assertNull(vm.estado.value.alvoDoVideo)
        assertEquals(listOf(Triple(80, "cena.mp4", 7)), prompts.videosEnviados)
    }

    @Test
    fun desistir_do_seletor_nao_envia_nada_e_limpa_o_alvo() = runTest {
        val prompts = PromptsFalso()
        val vm = vm(prompts)

        vm.escolherVideoPara(80, null)
        vm.videoEscolhido(null, cancelou = true); advanceUntilIdle()
        vm.videoEscolhido(arquivo("cena.mp4"), cancelou = false); advanceUntilIdle()  // sem alvo: ignorado

        assertNull(vm.estado.value.alvoDoVideo)
        assertTrue(prompts.videosEnviados.isEmpty())
        assertNull(vm.estado.value.mensagensDeVideo[80])
    }

    @Test
    fun o_icone_traduzir_abre_a_edicao_ja_em_portugues() = runTest {
        val vm = vm(PromptsFalso())

        vm.editarPrompt(80, 7, "texto")
        assertFalse(vm.estado.value.edicaoDePrompt!!.emPortugues)
        vm.traduzirPrompt(80, 7, "texto")

        assertTrue(vm.estado.value.edicaoDePrompt!!.emPortugues)
    }
}
