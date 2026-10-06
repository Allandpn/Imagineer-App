package com.allan.imagineer.rede

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Os pins: as posições que a pessoa marcou à mão num livro (item 6.10 e bloco J, PN1 a PN7). Não confundir com o **marcador**, que é a
// posição de leitura automática.

/** Um pin como o servidor o devolve. [trecho] é o começo do parágrafo, [ordem_do_capitulo] e [titulo_do_capitulo] dizem onde ele está (PN3). */
@Serializable
@Suppress("PropertyName")
data class PinDoLivro(
    val id: Int,
    val livro_id: Int,
    val capitulo_id: Int,
    /** Onde o parágrafo começa (UTF-16 desde o início do texto do capítulo). */
    val posicao_no_texto: Int,
    val nota: String? = null,
    val criado_em: String = "",
    val trecho: String = "",
    val ordem_do_capitulo: Int? = null,
    val titulo_do_capitulo: String? = null,
)

/** O maior tamanho da nota que o servidor aceita. */
const val LIMITE_DA_NOTA_DO_PIN = 1000

/** Os pins. Interface, para os ViewModels serem testados com uma versão falsa. */
interface RepositorioDePins {
    /** `GET /livros/{id}/pins`: na ordem do livro. */
    suspend fun listar(livroId: Int): ResultadoDaChamada<List<PinDoLivro>>

    /** `POST /livros/{id}/pins`: marca o começo do parágrafo; [nota] é opcional. */
    suspend fun criar(livroId: Int, capituloId: Int, posicao: Int, nota: String?): ResultadoDaChamada<PinDoLivro>

    /** `PATCH /pins/{id}`: troca a nota; `null` (ou em branco) a apaga. */
    suspend fun ajustarNota(pinId: Int, nota: String?): ResultadoDaChamada<PinDoLivro>

    /** `DELETE /pins/{id}`. */
    suspend fun apagar(pinId: Int): ResultadoDaChamada<Unit>
}

class RepositorioDePinsPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDePins {
    override suspend fun listar(livroId: Int): ResultadoDaChamada<List<PinDoLivro>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.pins(livroId) }
    }

    override suspend fun criar(livroId: Int, capituloId: Int, posicao: Int, nota: String?): ResultadoDaChamada<PinDoLivro> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject {
            put("capitulo_id", capituloId)
            put("posicao_no_texto", posicao)
            put("nota", nota?.trim()?.takeIf { it.isNotEmpty() })
        }
        return chamarApi { api.criarPin(livroId, corpo) }
    }

    override suspend fun ajustarNota(pinId: Int, nota: String?): ResultadoDaChamada<PinDoLivro> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject { put("nota", nota?.trim()?.takeIf { it.isNotEmpty() }) }
        return chamarApi { api.ajustarPin(pinId, corpo) }
    }

    override suspend fun apagar(pinId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.apagarPin(pinId) }
    }
}
