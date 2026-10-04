package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/** Uma cena ou um retrato movido para a lixeira, com o contexto para reconhecê-lo (LT3). Espelha o `FrameNaLixeira` do servidor. */
@Serializable
@Suppress("PropertyName")
data class FrameNaLixeira(
    val id: Int,
    val titulo: String,
    /** `CENA` ou `PERSONAGEM` (retrato). */
    val tipo: String,
    val nome_do_elemento: String? = null,
    val capitulo_id: Int,
    val titulo_do_capitulo: String? = null,
    val ordem_do_capitulo: Int = 0,
    val livro_id: Int,
    val titulo_do_livro: String,
    val apagado_em: String,
    val total_de_prompts: Int = 0,
    val total_de_imagens: Int = 0,
    val tamanho_em_bytes: Long = 0,
    val imagem_id: Int? = null,
)

/** As cenas e os retratos da lixeira, do apagado mais recentemente para o mais antigo. */
@Serializable
@Suppress("PropertyName")
data class FramesDaLixeira(
    val frames: List<FrameNaLixeira> = emptyList(),
    val total_em_bytes: Long = 0,
)

/** A lixeira de cenas e retratos (LT3). Interface, para os ViewModels serem testados com uma versão falsa. **Nada aqui gasta IA.** */
interface RepositorioDaLixeiraDeFrames {
    /** `GET /lixeira/frames`. */
    suspend fun listar(): ResultadoDaChamada<FramesDaLixeira>

    /** `POST /lixeira/frames/{id}/restaurar`: volta ao capítulo com os prompts e as imagens (e religa a cena sugerida, se der). */
    suspend fun restaurar(frameId: Int): ResultadoDaChamada<Unit>

    /** `DELETE /lixeira/frames/{id}`: apaga de vez, com prompts e imagens. **Não tem volta.** */
    suspend fun apagarDeVez(frameId: Int): ResultadoDaChamada<Unit>

    /** `DELETE /lixeira/frames`: apaga de vez **todos** os da lixeira. */
    suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada>
}

class RepositorioDaLixeiraDeFramesPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDaLixeiraDeFrames {
    override suspend fun listar(): ResultadoDaChamada<FramesDaLixeira> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.lixeiraDeFrames() }
    }

    override suspend fun restaurar(frameId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.restaurarFrameDaLixeira(frameId); Unit }
    }

    override suspend fun apagarDeVez(frameId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.apagarFrameDeVez(frameId) }
    }

    override suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.esvaziarLixeiraDeFrames() }
    }
}
