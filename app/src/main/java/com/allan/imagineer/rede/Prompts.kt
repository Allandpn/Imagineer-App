package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.dados.LeitorDeArquivos
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody

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
    val referencias_visuais: List<ReferenciaVisual> = emptyList(),
    /** Só vêm em `GET /prompts/{id}` (a listagem do frame traz apenas [total_de_imagens]). */
    val imagens: List<ImagemDoPrompt> = emptyList(),
    /** O que o provedor respondeu à última tentativa de gerar a imagem: `NAO_TENTADO`, `RECUSADO` ou `COM_SUCESSO` (K5). */
    val situacao_da_geracao: String = "NAO_TENTADO",
    /** A mensagem do provedor quando `RECUSADO`. */
    val motivo_da_recusa: String? = null,
    /** No prompt suavizado ou editado, o prompt de onde ele saiu. */
    val prompt_original_id: Int? = null,
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
    /** `IMPORTADA` (a pessoa trouxe de fora) ou `GERADA` (o servidor gerou): as geradas ficam em destaque (T2). */
    val origem: String = "IMPORTADA",
    val data_importacao: String = "",
)

/** Uma imagem de referência (âncora) de um elemento do frame. Por ora só se conta; mostrar é do incremento 12. */
@Serializable
data class ReferenciaVisual(val id: Int)

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

    /**
     * `POST /prompts/{id}/gerar-imagem`: **gera** (e cobra) a imagem (K1). [textoEditado] é o prompt que a pessoa editou à
     * mão depois de uma recusa (K4): o servidor o envia direto, sem suavizar. Recusa responde 200 com `RECUSADA`.
     */
    suspend fun gerarImagem(promptId: Int, textoEditado: String? = null): ResultadoDaChamada<ResultadoDaGeracao>

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

    override suspend fun gerarImagem(promptId: Int, textoEditado: String?): ResultadoDaChamada<ResultadoDaGeracao> {
        val api = provedor.obter() ?: return provedor.semServidor()
        // Só o texto, e só se a pessoa editou: sem ele o servidor segue o fluxo normal (original e, se recusar, suaviza).
        val corpo: JsonObject = buildJsonObject { if (textoEditado != null) put("texto", textoEditado) }
        return chamarApi { api.gerarImagem(promptId, corpo) }
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
}

/** Sem servidor para prompts (o padrão do ViewModel nos testes que não os usam): toda chamada falha com clareza. */
object PromptsSemServidor : RepositorioDePrompts {
    override suspend fun listar(frameId: Int): ResultadoDaChamada<List<PromptDeFrame>> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun gerar(frameId: Int, comentario: String?): ResultadoDaChamada<PromptDeFrame> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun detalhar(promptId: Int): ResultadoDaChamada<PromptDeFrame> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun gerarImagem(promptId: Int, textoEditado: String?): ResultadoDaChamada<ResultadoDaGeracao> =
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

/** O maior arquivo que o servidor aceita (item 6.6): 25 MB. */
const val TAMANHO_MAXIMO_DA_IMAGEM_EM_BYTES = 25L * 1024 * 1024

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
    (arquivo.tamanho ?: 0L) > TAMANHO_MAXIMO_DA_IMAGEM_EM_BYTES -> "A imagem passa de 25 MB, o limite do servidor."
    else -> null
}

/** O endereço de uma imagem no servidor (item 6.9): `miniatura`, `leitura` ou `original` (J4, J7). */
fun enderecoDaImagem(urlBase: String, imagemId: Int, tamanho: String): String =
    "${urlBase.trimEnd('/')}/imagens/$imagemId/arquivo?tamanho=$tamanho"

