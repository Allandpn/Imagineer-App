package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/** Uma imagem na lixeira, com o contexto para o usuário reconhecê-la (item 7.5b, LX5). */
@Serializable
@Suppress("PropertyName")
data class ImagemNaLixeira(
    val id: Int,
    val prompt_id: Int = 0,
    val frame_id: Int = 0,
    val frame_titulo: String = "",
    /** `CENA` ou `PERSONAGEM` (retrato). */
    val frame_tipo: String = "CENA",
    /** No retrato, o elemento retratado; nulo na cena. */
    val nome_do_elemento: String? = null,
    val capitulo_id: Int = 0,
    val titulo_do_capitulo: String? = null,
    val ordem_do_capitulo: Int? = null,
    val livro_id: Int = 0,
    val titulo_do_livro: String = "",
    val orientacao: String? = null,
    val modelo: String? = null,
    val origem: String = "IMPORTADA",
    val sem_filtro_de_seguranca: Boolean = false,
    val tamanho_em_bytes: Long? = null,
    val apagada_em: String = "",
)

/** O que a lixeira guarda, da apagada mais recentemente para a mais antiga, e o espaço que ocupa (LX5, LX6). */
@Serializable
@Suppress("PropertyName")
data class Lixeira(
    val imagens: List<ImagemNaLixeira> = emptyList(),
    val total_em_bytes: Long = 0,
)

/** O resultado de esvaziar a lixeira (LX5). */
@Serializable
@Suppress("PropertyName")
data class LixeiraEsvaziada(
    val removidas: Int = 0,
    val liberados_em_bytes: Long = 0,
)

/**
 * O que as telas precisam saber fazer com a lixeira de imagens (item 7.5b, LX8). Interface, para o ViewModel ser testado com uma
 * versão falsa, sem rede. **Nada aqui gasta IA.**
 */
interface RepositorioDaLixeira {
    /** `GET /lixeira/imagens`. */
    suspend fun listar(): ResultadoDaChamada<Lixeira>

    /** `POST /lixeira/imagens/{id}/restaurar`: a imagem volta ao prompt, ao capítulo e à galeria. */
    suspend fun restaurar(imagemId: Int): ResultadoDaChamada<Unit>

    /** `DELETE /lixeira/imagens/{id}`: apaga de vez (linha e arquivo). **Não tem volta.** */
    suspend fun apagarDeVez(imagemId: Int): ResultadoDaChamada<Unit>

    /** `DELETE /lixeira/imagens`: apaga de vez **tudo** o que está na lixeira. */
    suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada>
}

/** A implementação de verdade, sobre o Retrofit. */
class RepositorioDaLixeiraPeloRetrofit(
    private val provedor: ProvedorDeApi,
) : RepositorioDaLixeira {

    override suspend fun listar(): ResultadoDaChamada<Lixeira> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.lixeira() }
    }

    override suspend fun restaurar(imagemId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.restaurarImagem(imagemId); Unit }
    }

    override suspend fun apagarDeVez(imagemId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.apagarImagemDeVez(imagemId) }
    }

    override suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.esvaziarALixeira() }
    }
}
