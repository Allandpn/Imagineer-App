package com.allan.imagineer.rede

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** O que se pode favoritar (RL31). [rotulo] é o nome no singular e [plural] o do filtro da tela de Favoritos. */
enum class TipoDeFavorito(val rotulo: String, val plural: String) {
    LIVRO("Livro", "Livros"),
    PARAGRAFO("Parágrafo", "Parágrafos"),
    ELEMENTO("Elemento", "Elementos"),
    CENA("Cena", "Cenas"),
    IMAGEM("Imagem", "Imagens"),
    ;

    companion object {
        /** Nome da API → tipo; um tipo que este app não conhece (de um servidor mais novo) é `null` e o favorito não aparece. */
        fun de(nome: String): TipoDeFavorito? = entries.firstOrNull { it.name == nome }
    }
}

/** Um favorito como o servidor o devolve (RL34). [rotulo] é o que a lista mostra; os demais campos levam ao lugar. */
@Serializable
@Suppress("PropertyName")
data class Favorito(
    val id: Int,
    val livro_id: Int,
    val tipo: String,
    val rotulo: String,
    val capitulo_id: Int? = null,
    val ordem_do_capitulo: Int? = null,
    val titulo_do_capitulo: String? = null,
    /** Só no parágrafo: onde ele começa (UTF-16 desde o início do texto do capítulo). */
    val posicao: Int? = null,
    val elemento_id: Int? = null,
    /** Na cena, a própria; na imagem, o frame de onde ela é. */
    val frame_id: Int? = null,
    val imagem_id: Int? = null,
    val criado_em: String = "",
) {
    val tipoDoFavorito: TipoDeFavorito? get() = TipoDeFavorito.de(tipo)
}

/** O que se favorita: o livro, ou um alvo dele. Sabe montar o corpo do `POST` e reconhecer o favorito que já existe para ele. */
sealed interface AlvoDeFavorito {
    val tipo: TipoDeFavorito

    /** O corpo de `POST /livros/{id}/favoritos`. */
    fun corpo(): JsonObject

    /** Este favorito é deste alvo? */
    fun combina(favorito: Favorito): Boolean

    data object Livro : AlvoDeFavorito {
        override val tipo = TipoDeFavorito.LIVRO
        override fun corpo() = buildJsonObject { put("tipo", tipo.name) }
        override fun combina(favorito: Favorito) = favorito.tipo == tipo.name
    }

    /** Um parágrafo, pelo lugar em que **começa** (a mesma posição que o texto usa). */
    data class Paragrafo(val capituloId: Int, val posicao: Int) : AlvoDeFavorito {
        override val tipo = TipoDeFavorito.PARAGRAFO
        override fun corpo() = buildJsonObject {
            put("tipo", tipo.name)
            put("capitulo_id", capituloId)
            put("posicao", posicao)
        }
        override fun combina(favorito: Favorito) = favorito.tipo == tipo.name && favorito.capitulo_id == capituloId && favorito.posicao == posicao
    }

    data class Elemento(val elementoId: Int) : AlvoDeFavorito {
        override val tipo = TipoDeFavorito.ELEMENTO
        override fun corpo() = buildJsonObject {
            put("tipo", tipo.name)
            put("elemento_id", elementoId)
        }
        override fun combina(favorito: Favorito) = favorito.tipo == tipo.name && favorito.elemento_id == elementoId
    }

    data class Cena(val frameId: Int) : AlvoDeFavorito {
        override val tipo = TipoDeFavorito.CENA
        override fun corpo() = buildJsonObject {
            put("tipo", tipo.name)
            put("frame_id", frameId)
        }
        override fun combina(favorito: Favorito) = favorito.tipo == tipo.name && favorito.frame_id == frameId
    }

    data class Imagem(val imagemId: Int) : AlvoDeFavorito {
        override val tipo = TipoDeFavorito.IMAGEM
        override fun corpo() = buildJsonObject {
            put("tipo", tipo.name)
            put("imagem_id", imagemId)
        }
        override fun combina(favorito: Favorito) = favorito.tipo == tipo.name && favorito.imagem_id == imagemId
    }
}

/** Os favoritos (RL31 a RL38). Interface, para os ViewModels serem testados com uma versão falsa. */
interface RepositorioDeFavoritos {
    /** `GET /livros/{id}/favoritos`: do mais novo ao mais antigo, só os que ainda existem; [tipo] filtra. */
    suspend fun listar(livroId: Int, tipo: TipoDeFavorito? = null): ResultadoDaChamada<List<Favorito>>

    /** `POST /livros/{id}/favoritos`: idempotente (favoritar o que já é favorito devolve o que existe). */
    suspend fun favoritar(livroId: Int, alvo: AlvoDeFavorito): ResultadoDaChamada<Favorito>

    /** `DELETE /favoritos/{id}`. */
    suspend fun desfavoritar(favoritoId: Int): ResultadoDaChamada<Unit>
}

class RepositorioDeFavoritosPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeFavoritos {
    override suspend fun listar(livroId: Int, tipo: TipoDeFavorito?): ResultadoDaChamada<List<Favorito>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.favoritos(livroId, tipo?.name) }
    }

    override suspend fun favoritar(livroId: Int, alvo: AlvoDeFavorito): ResultadoDaChamada<Favorito> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.favoritar(livroId, alvo.corpo()) }
    }

    override suspend fun desfavoritar(favoritoId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.desfavoritar(favoritoId) }
    }
}
