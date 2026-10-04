package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/** Um elemento movido para a lixeira, com o que ele leva junto (LT4). Espelha o `ElementoNaLixeira` do servidor. */
@Serializable
@Suppress("PropertyName")
data class ElementoNaLixeira(
    val id: Int,
    val nome: String,
    /** `PERSONAGEM`, `AMBIENTE`, `OBJETO`, `CRIATURA`... */
    val tipo: String,
    val livro_id: Int,
    val titulo_do_livro: String,
    val apagado_em: String,
    val total_de_estados: Int = 0,
    /** Os retratos que foram para a lixeira junto com ele. */
    val total_de_retratos: Int = 0,
    val total_de_imagens: Int = 0,
    val tamanho_em_bytes: Long = 0,
    val imagem_id: Int? = null,
)

/** Os elementos da lixeira, do apagado mais recentemente para o mais antigo. */
@Serializable
@Suppress("PropertyName")
data class ElementosDaLixeira(
    val elementos: List<ElementoNaLixeira> = emptyList(),
    val total_em_bytes: Long = 0,
)

/** A lixeira de elementos (LT4). Interface, para os ViewModels serem testados com uma versão falsa. **Nada aqui gasta IA.** */
interface RepositorioDaLixeiraDeElementos {
    /** `GET /lixeira/elementos`. */
    suspend fun listar(): ResultadoDaChamada<ElementosDaLixeira>

    /** `POST /lixeira/elementos/{id}/restaurar`: volta ao livro com estados, identidade e retratos (e religa as sugestões, se der). */
    suspend fun restaurar(elementoId: Int): ResultadoDaChamada<Unit>

    /** `DELETE /lixeira/elementos/{id}`: apaga de vez, com estados, retratos e imagens. **Não tem volta.** */
    suspend fun apagarDeVez(elementoId: Int): ResultadoDaChamada<Unit>

    /** `DELETE /lixeira/elementos`: apaga de vez **todos** os da lixeira. */
    suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada>
}

class RepositorioDaLixeiraDeElementosPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDaLixeiraDeElementos {
    override suspend fun listar(): ResultadoDaChamada<ElementosDaLixeira> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.lixeiraDeElementos() }
    }

    override suspend fun restaurar(elementoId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.restaurarElementoDaLixeira(elementoId); Unit }
    }

    override suspend fun apagarDeVez(elementoId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.apagarElementoDeVez(elementoId) }
    }

    override suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.esvaziarLixeiraDeElementos() }
    }
}

/** A lixeira de elementos **de um livro só** (a do menu ⋮ do livro): lê tudo e fica com o que é dele. O resto repassa. */
class RepositorioDaLixeiraDeElementosDoLivro(
    private val base: RepositorioDaLixeiraDeElementos,
    private val livroId: Int,
) : RepositorioDaLixeiraDeElementos by base {
    override suspend fun listar(): ResultadoDaChamada<ElementosDaLixeira> =
        when (val r = base.listar()) {
            is ResultadoDaChamada.Sucesso -> {
                val dele = r.dado.elementos.filter { it.livro_id == livroId }
                ResultadoDaChamada.Sucesso(ElementosDaLixeira(dele, dele.sumOf { it.tamanho_em_bytes }))
            }
            is ResultadoDaChamada.Falha -> r
        }
}
