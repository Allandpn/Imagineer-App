package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/** Um modelo de **texto** da lista do servidor (`GET /configuracao/modelos`), com o que ajuda a escolher (MT1). */
@Serializable
@Suppress("PropertyName")
data class ModeloDeTexto(
    val id: String,
    val nome: String,
    val contexto: Int = 0,
    val gratuito: Boolean = false,
    val suporta_json: Boolean = false,
    /** O preço por **token** de saída, em dólares (multiplique por 1 milhão para o preço de costume). */
    val custo_saida: Double = 0.0,
    val moderado: Boolean = false,
)

/** Os modelos de IA do servidor: ler a configuração e a lista, e escolher o modelo de cada tarefa (MT1). **Nada aqui gasta IA.** */
interface RepositorioDeModelos {
    /** `GET /configuracao`. */
    suspend fun configuracao(): ResultadoDaChamada<ConfiguracaoAtual>

    /** `GET /configuracao/modelos`, do mais barato para o mais caro. */
    suspend fun modelosDeTexto(): ResultadoDaChamada<List<ModeloDeTexto>>

    /** `PUT /configuracao` com **um** campo (`modelo_traducao` etc.); [modelo] `null` limpa (volta ao padrão). Devolve a configuração nova. */
    suspend fun escolher(campo: String, modelo: String?): ResultadoDaChamada<ConfiguracaoAtual>
}

class RepositorioDeModelosPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeModelos {
    override suspend fun configuracao(): ResultadoDaChamada<ConfiguracaoAtual> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.configuracao() }
    }

    override suspend fun modelosDeTexto(): ResultadoDaChamada<List<ModeloDeTexto>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.modelosDeTexto() }
    }

    override suspend fun escolher(campo: String, modelo: String?): ResultadoDaChamada<ConfiguracaoAtual> {
        val api = provedor.obter() ?: return provedor.semServidor()
        // O servidor trata texto vazio como "limpar".
        return chamarApi { api.gravarConfiguracao(mapOf(campo to (modelo ?: ""))) }
    }
}
