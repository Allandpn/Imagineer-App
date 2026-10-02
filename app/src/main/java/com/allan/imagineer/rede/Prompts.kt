package com.allan.imagineer.rede

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
}

/** A implementação de verdade, sobre o Retrofit. */
class RepositorioDePromptsPeloRetrofit(
    private val provedor: ProvedorDeApi,
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
}

/** Sem servidor para prompts (o padrão do ViewModel nos testes que não os usam): toda chamada falha com clareza. */
object PromptsSemServidor : RepositorioDePrompts {
    override suspend fun listar(frameId: Int): ResultadoDaChamada<List<PromptDeFrame>> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")

    override suspend fun gerar(frameId: Int, comentario: String?): ResultadoDaChamada<PromptDeFrame> =
        ResultadoDaChamada.Falha("Os prompts não estão disponíveis.")
}
