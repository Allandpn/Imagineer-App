package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.dados.LeitorDeArquivos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

// Os nomes dos campos são os do JSON do backend (item 6.6).

/**
 * Um prompt já montado para um frame. [referencias_visuais] são as imagens-âncora dos elementos do frame: o fluxo é
 * **manual** (copiar o prompt e colar na ferramenta de imagem), então a API não as anexa — só avisa que existem.
 */
@Serializable
@Suppress("PropertyName")
data class PromptDeFrame(
    val id: Int,
    val frame_id: Int,
    val texto: String,
    val modelo_ia: String? = null,
    val data_criacao: String = "",
    val total_de_imagens: Int = 0,
    /** O prompt existe só para guardar uma imagem importada sem prompt (PI1): não vale como prompt, não copia, não edita, não gera. */
    val so_imagem: Boolean = false,
    /** A versão em português do prompt (PT1); nulo = ainda sem tradução. O que vai à imagem é o [texto], em inglês. */
    val texto_pt: String? = null,
    val referencias_visuais: List<ReferenciaVisual> = emptyList(),
    /** Só vêm em `GET /prompts/{id}` (a listagem do frame traz apenas [total_de_imagens]). */
    val imagens: List<ImagemDoPrompt> = emptyList(),
    /** O que o provedor respondeu à última tentativa de gerar a imagem: `NAO_TENTADO`, `RECUSADO` ou `COM_SUCESSO` (K5). */
    val situacao_da_geracao: String = "NAO_TENTADO",
    /** A mensagem do provedor quando `RECUSADO`. */
    val motivo_da_recusa: String? = null,
    /** No prompt suavizado ou editado, o prompt de onde ele saiu. */
    val prompt_original_id: Int? = null,
    /** O modelo de imagem da última tentativa deste prompt, inclusive a recusada (Z1, Z7); nulo se nunca tentado. */
    val modelo_imagem: String? = null,
    /** A última tentativa foi com o filtro de segurança do modelo desligado, a pedido da pessoa (F16). */
    val sem_filtro_de_seguranca: Boolean = false,
    /** Os ids das imagens enviadas como referência na última tentativa (W7); vazia = nenhuma. */
    val imagens_de_referencia: List<Int> = emptyList(),
    /** `IMAGEM` (padrão) ou `VIDEO` (item 4.8): o prompt de vídeo vai ao Gemini, não gera imagem. */
    val tipo: String = "IMAGEM",
    /** Só no `VIDEO`: a imagem que vira o primeiro quadro; nulo = sem imagem de partida. */
    val imagem_partida_id: Int? = null,
    /** Só no `VIDEO`: a pessoa o escondeu da lista (VD12); não foi apagado. */
    val oculto: Boolean = false,
)

/**
 * O desfecho de `POST /prompts/{id}/gerar-imagem` (item 6.6). `resultado` é `GERADA` ou `RECUSADA`; [prompt] é o que o
 * servidor enviou **por último** (o original, o suavizado ou o editado); [imagem] só vem se `GERADA`.
 */
@Serializable
data class ResultadoDaGeracao(
    val resultado: String,
    val suavizado: Boolean = false,
    val prompt: PromptDeFrame,
    val imagem: ImagemDoPrompt? = null,
) {
    /** `true` se o provedor gerou a imagem. */
    val gerada: Boolean get() = resultado == "GERADA"
}

/** Uma imagem importada para um prompt (item 6.6). Os bytes se buscam em `GET /imagens/{id}/arquivo` (item 6.9). */
@Serializable
@Suppress("PropertyName")
data class ImagemDoPrompt(
    val id: Int,
    val prompt_id: Int = 0,
    val tamanho_em_bytes: Long? = null,
    val largura: Int? = null,
    val altura: Int? = null,
    /** `RETRATO`, `PAISAGEM` ou nulo (sem dimensões). Serve à segunda fatia (desenhar no texto). */
    val orientacao: String? = null,
    /** `IMPORTADA` (a pessoa trouxe de fora) ou `GERADA` (o servidor gerou). */
    val origem: String = "IMPORTADA",
    /** O modelo de imagem que gerou esta imagem (Z8); nulo se foi importada. */
    val modelo: String? = null,
    /** A imagem foi gerada com o filtro de segurança do modelo desligado (F16). */
    val sem_filtro_de_seguranca: Boolean = false,
    /** É a imagem canônica do frame dela: a que o capítulo mostra (CAN5). */
    val canonica: Boolean = false,
    /** O frame desta imagem está com a imagem oculta no capítulo (OC1); a imagem continua no catálogo. */
    val oculta_no_capitulo: Boolean = false,
    /** As imagens enviadas como referência quando **esta** imagem foi gerada (RS2); vazia = nenhuma, ou importada. */
    val imagens_de_referencia: List<Int> = emptyList(),
    val data_importacao: String = "",
)

/** Uma imagem de referência (âncora) de um elemento do frame. Por ora só se conta; mostrar é do incremento 12. */
@Serializable
data class ReferenciaVisual(val id: Int)

/**
 * Os modelos de imagem que o usuário pode escolher (Z2): o [padrao] do servidor e a [lista] mantida na configuração. O
 * padrão sempre aparece na escolha, mesmo fora da lista (veja `modelosParaEscolher`).
 */
data class ModelosDeImagem(
    val padrao: String,
    val lista: List<String>,
    val semFiltro: List<String> = emptyList(),
    /** Os modelos que aceitam imagens de referência (W1). */
    val comReferencia: List<String> = emptyList(),
)

/** Uma imagem de um elemento da cena que pode ir como referência (W2); [ancora] = a referência principal dele. */
@Serializable
@Suppress("PropertyName")
data class ImagemCandidata(
    val id: Int,
    val prompt_id: Int = 0,
    val orientacao: String? = null,
    val modelo: String? = null,
    val origem: String = "IMPORTADA",
    val ancora: Boolean = false,
)

/** Um elemento da cena e as imagens dele (W2). Lista vazia = ainda sem imagem. */
@Serializable
@Suppress("PropertyName")
data class ElementoComImagens(
    val elemento_id: Int,
    val nome: String,
    val tipo: String = "",
    val imagens: List<ImagemCandidata> = emptyList(),
)

/** Um elemento que o usuário pode colocar no frame (EV6), com as imagens dele e o que já é dele no frame. */
@Serializable
@Suppress("PropertyName")
data class ElementoParaVincular(
    val elemento_id: Int,
    /** O estado a ligar ao frame. */
    val estado_id: Int,
    val nome: String,
    val tipo: String = "",
    /** Já participa da cena ou já está vinculado ao retrato. */
    val no_frame: Boolean = false,
    /** Está no frame e pode sair por este seletor: o participante que veio da sugestão da cena **não** pode (EV5). */
    val removivel: Boolean = false,
    val imagens: List<ImagemCandidata> = emptyList(),
)

/** O que o seletor de elementos e imagens mostra (EV2): os identificados pela IA e os outros que têm estado no capítulo. */
@Serializable
data class ElementosParaVincular(
    val identificados: List<ElementoParaVincular> = emptyList(),
    val outros: List<ElementoParaVincular> = emptyList(),
    /** Os demais elementos do livro, sem estado neste capítulo (VM7): cada um traz o estado vigente até aqui, ou o primeiro que tem. */
    val de_outros_capitulos: List<ElementoParaVincular> = emptyList(),
    /** As cenas do livro com imagem (EV15): as imagens delas também podem ir como referência. */
    val cenas: List<CenaComImagens> = emptyList(),
)

/** Uma cena do livro e as imagens dela, para usar como referência de outra cena (EV15). */
@Serializable
@Suppress("PropertyName")
data class CenaComImagens(
    val frame_id: Int,
    val titulo: String,
    val capitulo_id: Int,
    val ordem_do_capitulo: Int,
    val titulo_do_capitulo: String? = null,
    val imagens: List<ImagemCandidata> = emptyList(),
)

/** O que o modal de referências mostra (W2, W9). */
@Serializable
data class ReferenciasCandidatas(val elementos: List<ElementoComImagens> = emptyList())

/**
 * O que o modal da cena precisa dos prompts de um frame (item 6.6). Interface, para o ViewModel ser testado com uma
 * versão falsa, sem rede e **sem gastar IA**. **Ler não custa, gerar custa** (G2, G3).
 */
interface RepositorioDePrompts {
    /** `GET /frames/{id}/prompts`: o que já foi gerado, do mais antigo ao mais recente. Nunca gasta IA (G2). */
    suspend fun listar(frameId: Int): ResultadoDaChamada<List<PromptDeFrame>>

    /**
     * `POST /frames/{id}/prompts`: **gera** (e cobra) um prompt novo com a IA (G4). [comentario] é uma correção
     * pontual do usuário, com prioridade sobre a leitura automática; é assim que se pede um refinamento.
     */
    suspend fun gerar(frameId: Int, comentario: String?): ResultadoDaChamada<PromptDeFrame>

    /** `GET /frames/{id}/prompts?tipo=VIDEO`: os prompts de vídeo do frame, do mais antigo ao mais recente (VD7). Nunca gasta IA. */
    suspend fun listarVideos(frameId: Int): ResultadoDaChamada<List<PromptDeFrame>> = ResultadoDaChamada.Falha("Os prompts de vídeo não estão disponíveis.")

    /**
     * `POST /frames/{id}/prompts` com `tipo=VIDEO`: **gera** (e cobra, uma chamada) o prompt de vídeo (VD8). [imagemPartidaId] é o
     * primeiro quadro (422 se não é deste frame); [comentario] vale mais que tudo.
     */
    suspend fun gerarVideo(frameId: Int, imagemPartidaId: Int?, comentario: String?): ResultadoDaChamada<PromptDeFrame> =
        ResultadoDaChamada.Falha("Os prompts de vídeo não estão disponíveis.")

    /**
     * `POST /prompts/{id}/gerar-imagem`: **gera** (e cobra) a imagem (K1). [textoEditado] é o prompt que a pessoa editou à
     * mão depois de uma recusa (K4): o servidor o envia direto, sem suavizar. Recusa responde 200 com `RECUSADA`.
     */
    suspend fun gerarImagem(
        promptId: Int,
        textoEditado: String? = null,
        modelo: String? = null,
        semFiltro: Boolean = false,
        referencias: List<Int> = emptyList(),
        /** O português que a pessoa escreveu e que deu origem ao [textoEditado] (PT4): o prompt novo o guarda. */
        textoPt: String? = null,
    ): ResultadoDaChamada<ResultadoDaGeracao>

    /** `POST /prompts/{id}/traducao-pt`: o prompt em português (PT2); traduz uma vez e guarda, e a segunda vez não chama a IA. */
    suspend fun traduzirParaPortugues(promptId: Int): ResultadoDaChamada<Traducao> =
        ResultadoDaChamada.Falha("A tradução não está disponível.")

    /** `POST /prompts/{id}/traduzir-para-ingles`: o português escrito, em inglês (PT3); só uma prévia, não grava nada. */
    suspend fun traduzirParaIngles(promptId: Int, texto: String): ResultadoDaChamada<Traducao> =
        ResultadoDaChamada.Falha("A tradução não está disponível.")

    /** `GET /frames/{id}/elementos-para-vincular`: os elementos e as imagens do seletor (EV6). Nunca gasta IA. */
    suspend fun elementosParaVincular(frameId: Int): ResultadoDaChamada<ElementosParaVincular>

    /** `GET /capitulos/{id}/elementos-para-cena`: o mesmo seletor para uma cena que ainda não existe (LV8). Nunca gasta IA. */
    suspend fun elementosParaCena(capituloId: Int): ResultadoDaChamada<ElementosParaVincular> =
        ResultadoDaChamada.Falha("Os elementos do capítulo não estão disponíveis.")

    /** `GET /frames/{id}/referencias-candidatas`: as imagens dos elementos da cena que podem ir como referência (W2). Nunca gasta IA. */
    suspend fun referenciasCandidatas(frameId: Int): ResultadoDaChamada<ReferenciasCandidatas>

    /** `GET /configuracao`: o modelo de imagem padrão e a lista de modelos que se pode escolher (Z2, Z6). Nunca gasta IA. */
    suspend fun modelosDeImagem(): ResultadoDaChamada<ModelosDeImagem>

    /**
     * `GET /imagens/{id}/arquivo`: baixa a imagem **original** para [destino] (U1, U2) e devolve o tipo MIME que o servidor
     * informou (`image/webp`...). Grava aos poucos, sem pôr o arquivo inteiro na memória.
     */
    suspend fun baixarImagem(imagemId: Int, destino: File): ResultadoDaChamada<String>

    /** `DELETE /imagens/{id}`: apaga a imagem para sempre, inclusive o arquivo no servidor (U3). Imagem que já não existe conta como apagada. */
    suspend fun removerImagem(imagemId: Int): ResultadoDaChamada<Unit>

    /** `GET /prompts/{id}`: o prompt **com as suas imagens** (J5). Nunca gasta IA. */
    suspend fun detalhar(promptId: Int): ResultadoDaChamada<PromptDeFrame>

    /**
     * `POST /prompts/{id}/imagens`: manda a imagem escolhida (J3). [aoProgredir] recebe (bytes enviados, total ou nulo)
     * de uma thread de rede. Não gasta IA.
     */
    suspend fun importarImagem(
        promptId: Int,
        arquivo: ArquivoEscolhido,
        aoProgredir: (enviados: Long, total: Long?) -> Unit,
    ): ResultadoDaChamada<ImagemDoPrompt>

    /** `GET /frames/{id}/videos`: os vídeos **importados** do frame, do mais novo ao mais antigo (VD16). */
    suspend fun listarVideosImportados(frameId: Int): ResultadoDaChamada<List<VideoImportado>> = ResultadoDaChamada.Falha("Os vídeos não estão disponíveis.")

    /** `POST /frames/{id}/videos`: manda o vídeo escolhido (VD16); [promptId] é o prompt de vídeo de origem. [aoProgredir] vem de uma thread de rede. */
    suspend fun importarVideo(
        frameId: Int,
        arquivo: ArquivoEscolhido,
        promptId: Int?,
        aoProgredir: (enviados: Long, total: Long?) -> Unit,
    ): ResultadoDaChamada<VideoImportado> = ResultadoDaChamada.Falha("Os vídeos não estão disponíveis.")

    /** `DELETE /videos/{id}`: apaga o vídeo e o arquivo (VD18). */
    suspend fun apagarVideo(videoId: Int): ResultadoDaChamada<Unit> = ResultadoDaChamada.Falha("Os vídeos não estão disponíveis.")

    /** `PUT /frames/{id}/video-no-texto`: o texto passa a mostrar o vídeo [videoId]; `null` volta à imagem (VD17). */
    suspend fun definirVideoNoTexto(frameId: Int, videoId: Int?): ResultadoDaChamada<Unit> = ResultadoDaChamada.Falha("Os vídeos não estão disponíveis.")

    /** `PATCH /prompts/{id}` num prompt de vídeo: esconde/mostra e/ou edita o texto (VD12, VD13). Só manda o que não é nulo. */
    suspend fun ajustarPromptDeVideo(promptId: Int, oculto: Boolean? = null, texto: String? = null, textoPt: String? = null): ResultadoDaChamada<PromptDeFrame> =
        ResultadoDaChamada.Falha("Os prompts de vídeo não estão disponíveis.")

    /**
     * `POST /frames/{id}/imagens` (PI1): importa a imagem **para o frame**, mesmo sem prompt (o servidor cria o "prompt só da
     * imagem" se não há nenhum). Não gasta IA.
     */
    suspend fun importarImagemParaOFrame(
        frameId: Int,
        arquivo: ArquivoEscolhido,
        aoProgredir: (enviados: Long, total: Long?) -> Unit,
    ): ResultadoDaChamada<ImagemDoPrompt> = ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")
}

/** A implementação de verdade, sobre o Retrofit. */
class RepositorioDePromptsPeloRetrofit(
    private val provedor: ProvedorDeApi,
    private val leitor: LeitorDeArquivos,
) : RepositorioDePrompts {

    override suspend fun listar(frameId: Int): ResultadoDaChamada<List<PromptDeFrame>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.prompts(frameId) }
    }

    override suspend fun listarVideos(frameId: Int): ResultadoDaChamada<List<PromptDeFrame>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.prompts(frameId, tipo = "VIDEO") }
    }

    override suspend fun listarVideosImportados(frameId: Int): ResultadoDaChamada<List<VideoImportado>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.videosDoFrame(frameId) }
    }

    override suspend fun importarVideo(
        frameId: Int,
        arquivo: ArquivoEscolhido,
        promptId: Int?,
        aoProgredir: (enviados: Long, total: Long?) -> Unit,
    ): ResultadoDaChamada<VideoImportado> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val entrada = leitor.abrir(arquivo.uri) ?: return ResultadoDaChamada.Falha("Não consegui abrir o arquivo escolhido.")
        val nome = nomeDoVideoParaEnviar(arquivo)
        val corpo = CorpoComProgresso(entrada, tipoDoVideo(nome).toMediaType(), arquivo.tamanho, aoProgredir)
        val origem = promptId?.toString()?.toRequestBody("text/plain".toMediaType())
        return try {
            chamarApi { api.importarVideo(frameId, MultipartBody.Part.createFormData("arquivo", nome, corpo), origem) }
        } finally {
            entrada.close()
        }
    }

    override suspend fun apagarVideo(videoId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.apagarVideo(videoId) }
    }

    override suspend fun definirVideoNoTexto(frameId: Int, videoId: Int?): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject { put("video_id", videoId) }
        return chamarApi { api.definirVideoNoTexto(frameId, corpo); Unit }
    }

    override suspend fun ajustarPromptDeVideo(promptId: Int, oculto: Boolean?, texto: String?, textoPt: String?): ResultadoDaChamada<PromptDeFrame> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject {
            if (oculto != null) put("oculto", oculto)
            if (texto != null) put("texto", texto)
            if (textoPt != null) put("texto_pt", textoPt)
        }
        return chamarApi { api.ajustarPrompt(promptId, corpo) }
    }

    override suspend fun gerarVideo(frameId: Int, imagemPartidaId: Int?, comentario: String?): ResultadoDaChamada<PromptDeFrame> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject {
            put("tipo", "VIDEO")
            if (imagemPartidaId != null) put("imagem_partida_id", imagemPartidaId)
            if (!comentario.isNullOrBlank()) put("comentario", comentario.trim())
        }
        return chamarApi { api.gerarPrompt(frameId, corpo) }
    }

    override suspend fun gerar(frameId: Int, comentario: String?): ResultadoDaChamada<PromptDeFrame> {
        val api = provedor.obter() ?: return provedor.semServidor()
        // Só o comentário, e só se houver: o perfil é o padrão do livro e o modelo, o da configuração (item 6.6).
        val corpo: JsonObject = buildJsonObject { if (comentario != null) put("comentario", comentario) }
        return chamarApi { api.gerarPrompt(frameId, corpo) }
    }

    override suspend fun detalhar(promptId: Int): ResultadoDaChamada<PromptDeFrame> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.prompt(promptId) }
    }

    override suspend fun baixarImagem(imagemId: Int, destino: File): ResultadoDaChamada<String> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return when (val resposta = chamarApi { api.baixarImagem(imagemId) }) {
            is ResultadoDaChamada.Falha -> resposta
            is ResultadoDaChamada.Sucesso -> resposta.dado.use { corpo ->
                try {
                    withContext(Dispatchers.IO) {
                        destino.parentFile?.mkdirs()
                        corpo.byteStream().use { entrada -> destino.outputStream().use { saida -> entrada.copyTo(saida) } }
                    }
                    // O servidor já manda o tipo certo; o que não for image/* (um servidor antigo respondia octet-stream para .webp)
                    // se descobre pelos primeiros bytes, porque o Android recusa salvar na galeria um tipo que não seja de imagem.
                    val doServidor = corpo.contentType()?.let { "${it.type}/${it.subtype}" }
                    ResultadoDaChamada.Sucesso(if (doServidor != null && doServidor.startsWith("image/")) doServidor else tipoDeImagemPelosBytes(destino))
                } catch (erro: IOException) {
                    destino.delete() // um arquivo pela metade não serve para compartilhar nem salvar
                    ResultadoDaChamada.Falha("Não consegui baixar a imagem.")
                }
            }
        }
    }

    override suspend fun removerImagem(imagemId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return interpretarRemocao(chamarApi { api.removerImagem(imagemId) })
    }

    override suspend fun traduzirParaPortugues(promptId: Int): ResultadoDaChamada<Traducao> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.traduzirParaPortugues(promptId) }
    }

    override suspend fun traduzirParaIngles(promptId: Int, texto: String): ResultadoDaChamada<Traducao> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject { put("texto", texto) }
        return chamarApi { api.traduzirParaIngles(promptId, corpo) }
    }

    override suspend fun gerarImagem(
        promptId: Int,
        textoEditado: String?,
        modelo: String?,
        semFiltro: Boolean,
        referencias: List<Int>,
        textoPt: String?,
    ): ResultadoDaChamada<ResultadoDaGeracao> {
        val api = provedor.obter() ?: return provedor.semServidor()
        // Só o que a pessoa decidiu: sem texto o servidor segue o fluxo normal (original e, se recusar, suaviza); sem
        // modelo vale o padrão do servidor (Z3).
        val corpo: JsonObject = buildJsonObject {
            if (textoEditado != null) put("texto", textoEditado)
            if (textoEditado != null && !textoPt.isNullOrBlank()) put("texto_pt", textoPt)
            if (modelo != null) put("modelo", modelo)
            // F12: só por pedido explícito da pessoa, no diálogo próprio; nunca vai por padrão.
            if (semFiltro) put("sem_filtro_de_seguranca", true)
            // W3: só as imagens que a pessoa escolheu no modal; sem escolha o campo nem vai.
            if (referencias.isNotEmpty()) put("imagens_de_referencia", kotlinx.serialization.json.JsonArray(referencias.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }
        return chamarApi { api.gerarImagem(promptId, corpo) }
    }

    override suspend fun referenciasCandidatas(frameId: Int): ResultadoDaChamada<ReferenciasCandidatas> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.referenciasCandidatas(frameId) }
    }

    override suspend fun elementosParaVincular(frameId: Int): ResultadoDaChamada<ElementosParaVincular> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.elementosParaVincular(frameId) }
    }

    override suspend fun elementosParaCena(capituloId: Int): ResultadoDaChamada<ElementosParaVincular> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.elementosParaCena(capituloId) }
    }

    override suspend fun modelosDeImagem(): ResultadoDaChamada<ModelosDeImagem> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return when (val resposta = chamarApi { api.configuracao() }) {
            is ResultadoDaChamada.Sucesso ->
                ResultadoDaChamada.Sucesso(ModelosDeImagem(
                    resposta.dado.modelo_imagem.orEmpty(),
                    resposta.dado.modelos_de_imagem,
                    resposta.dado.modelos_sem_filtro,
                    resposta.dado.modelos_com_referencia.keys.toList(),
                ),
            )
            is ResultadoDaChamada.Falha -> resposta
        }
    }

    override suspend fun importarImagem(
        promptId: Int,
        arquivo: ArquivoEscolhido,
        aoProgredir: (enviados: Long, total: Long?) -> Unit,
    ): ResultadoDaChamada<ImagemDoPrompt> {
        val api = provedor.obter() ?: return provedor.semServidor()
        // Abre antes do pedido: permissão perdida ou arquivo movido não pode virar "falha de rede".
        val entrada = leitor.abrir(arquivo.uri) ?: return ResultadoDaChamada.Falha("Não consegui abrir o arquivo escolhido.")
        val nome = nomeParaEnviar(arquivo)
        val corpo = CorpoComProgresso(entrada, tipoDaImagem(nome).toMediaType(), arquivo.tamanho, aoProgredir)
        return try {
            chamarApi { api.importarImagem(promptId, MultipartBody.Part.createFormData("arquivo", nome, corpo)) }
        } finally {
            entrada.close()
        }
    }

    override suspend fun importarImagemParaOFrame(
        frameId: Int,
        arquivo: ArquivoEscolhido,
        aoProgredir: (enviados: Long, total: Long?) -> Unit,
    ): ResultadoDaChamada<ImagemDoPrompt> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val entrada = leitor.abrir(arquivo.uri) ?: return ResultadoDaChamada.Falha("Não consegui abrir o arquivo escolhido.")
        val nome = nomeParaEnviar(arquivo)
        val corpo = CorpoComProgresso(entrada, tipoDaImagem(nome).toMediaType(), arquivo.tamanho, aoProgredir)
        return try {
            chamarApi { api.importarImagemParaOFrame(frameId, MultipartBody.Part.createFormData("arquivo", nome, corpo)) }
        } finally {
            entrada.close()
        }
    }
}

/** Sem servidor para prompts (o padrão do ViewModel nos testes que não os usam): toda chamada falha com clareza. */
object PromptsSemServidor : RepositorioDePrompts {
    override suspend fun listar(frameId: Int): ResultadoDaChamada<List<PromptDeFrame>> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun gerar(frameId: Int, comentario: String?): ResultadoDaChamada<PromptDeFrame> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun detalhar(promptId: Int): ResultadoDaChamada<PromptDeFrame> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun gerarImagem(
        promptId: Int,
        textoEditado: String?,
        modelo: String?,
        semFiltro: Boolean,
        referencias: List<Int>,
        textoPt: String?,
    ): ResultadoDaChamada<ResultadoDaGeracao> = ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun referenciasCandidatas(frameId: Int): ResultadoDaChamada<ReferenciasCandidatas> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun elementosParaVincular(frameId: Int): ResultadoDaChamada<ElementosParaVincular> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun modelosDeImagem(): ResultadoDaChamada<ModelosDeImagem> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun baixarImagem(imagemId: Int, destino: File): ResultadoDaChamada<String> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun removerImagem(imagemId: Int): ResultadoDaChamada<Unit> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun importarImagem(
        promptId: Int,
        arquivo: ArquivoEscolhido,
        aoProgredir: (enviados: Long, total: Long?) -> Unit,
    ): ResultadoDaChamada<ImagemDoPrompt> = ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")
}

// ---------------------------------------------------------------------------------------------------------------------
// Regras puras da importação de imagem (J2, J7)
// ---------------------------------------------------------------------------------------------------------------------

/** O maior arquivo que o servidor aceita (item 6.6): 15 MB. */
const val TAMANHO_MAXIMO_DA_IMAGEM_EM_BYTES = 15L * 1024 * 1024

private val TIPOS_DE_IMAGEM = mapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "webp" to "image/webp",
    "gif" to "image/gif",
)

/** A extensão que o servidor aceita para cada tipo MIME (usada quando o nome do arquivo não tem extensão). */
private val EXTENSOES_POR_TIPO = mapOf(
    "image/png" to "png",
    "image/jpeg" to "jpg",
    "image/jpg" to "jpg",
    "image/webp" to "webp",
    "image/gif" to "gif",
)

/** O tipo do arquivo pela extensão; o servidor também decide pela extensão, então é ela que importa (J2). */
fun tipoDaImagem(nome: String): String = TIPOS_DE_IMAGEM[nome.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

/**
 * A extensão do arquivo escolhido, ou `null` se não dá para saber (J2): a do **nome**, se o servidor a aceita; senão a do
 * **tipo MIME** que o seletor informou (um nome como `image-3f2a` sem extensão não é motivo para recusar uma foto).
 */
fun extensaoDaImagem(arquivo: ArquivoEscolhido): String? {
    val doNome = arquivo.nome.substringAfterLast('.', "").lowercase()
    if (doNome in TIPOS_DE_IMAGEM) return doNome
    return arquivo.tipo?.lowercase()?.let { EXTENSOES_POR_TIPO[it] }
}

/** O nome que vai ao servidor: o original se já tem extensão aceita; senão o nome com a extensão achada pelo tipo (J2). */
fun nomeParaEnviar(arquivo: ArquivoEscolhido): String {
    val extensao = extensaoDaImagem(arquivo) ?: return arquivo.nome
    val temExtensaoAceita = arquivo.nome.substringAfterLast('.', "").lowercase() in TIPOS_DE_IMAGEM
    return if (temExtensaoAceita) arquivo.nome else "${arquivo.nome}.$extensao"
}

/**
 * Confere, **antes de enviar**, o que o servidor recusaria (J2): a extensão e o tamanho. Devolve o motivo, ou `null` se
 * pode enviar. Evita subir 30 MB para ouvir "não" no fim.
 */
fun motivoParaNaoImportar(arquivo: ArquivoEscolhido): String? = when {
    extensaoDaImagem(arquivo) == null -> "Escolha uma imagem PNG, JPG, WEBP ou GIF."
    (arquivo.tamanho ?: 0L) > TAMANHO_MAXIMO_DA_IMAGEM_EM_BYTES -> "A imagem passa de 15 MB, o limite do servidor."
    else -> null
}

/** A extensão do arquivo para um tipo MIME de imagem (U1, U2); `png` se não reconhece. */
fun extensaoDoTipo(tipo: String): String = when (tipo.lowercase()) {
    "image/jpeg", "image/jpg" -> "jpg"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    else -> "png"
}

/**
 * O resultado de uma tradução de prompt (PT2, PT3, PT6). [custo] vem em dólares **como texto** (ou nulo: já estava guardada, ou o
 * fornecedor não informou); [reaproveitada] = já estava guardada, sem chamar a IA.
 */
@Serializable
data class Traducao(
    val texto: String,
    val modelo: String? = null,
    val custo: String? = null,
    val reaproveitada: Boolean = false,
)

/** O endereço de uma imagem no servidor (item 6.9): `miniatura`, `leitura` ou `original` (J4, J7). */
fun enderecoDaImagem(urlBase: String, imagemId: Int, tamanho: String): String =
    "${urlBase.trimEnd('/')}/imagens/$imagemId/arquivo?tamanho=$tamanho"

/**
 * O tipo da imagem pelos primeiros bytes do [arquivo]: PNG, JPEG, GIF ou WebP (`RIFF....WEBP`); sem reconhecer, `image/png`. Serve
 * quando o servidor não informa um tipo de imagem (ele já informa; isto é a rede de segurança do "Salvar na galeria").
 */
fun tipoDeImagemPelosBytes(arquivo: File): String {
    val cabecalho = ByteArray(12)
    val lidos = try {
        arquivo.inputStream().use { it.read(cabecalho) }
    } catch (erro: IOException) {
        0
    }
    return tipoDeImagemPeloCabecalho(cabecalho.copyOf(maxOf(lidos, 0)))
}

/** O tipo de imagem pelo [cabecalho] (os primeiros bytes); sem reconhecer, `image/png`. */
fun tipoDeImagemPeloCabecalho(cabecalho: ByteArray): String {
    fun comeca(vararg bytes: Int) = cabecalho.size >= bytes.size && bytes.indices.all { cabecalho[it] == bytes[it].toByte() }
    return when {
        comeca(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        comeca(0x47, 0x49, 0x46, 0x38) -> "image/gif"
        cabecalho.size >= 12 && comeca(0x52, 0x49, 0x46, 0x46) && String(cabecalho, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> "image/png"
    }
}
