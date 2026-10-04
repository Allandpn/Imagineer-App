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

    /** `GET /configuracao/modelos-de-imagem` (MI1). */
    suspend fun catalogoDeImagem(): ResultadoDaChamada<CatalogoDeImagem>

    /** `PUT /configuracao/modelos-de-imagem/preco`: o preço por imagem que a pessoa informa ([preco] nulo limpa) (PD5). Devolve o catálogo. */
    suspend fun informarPreco(modelo: String, preco: String?): ResultadoDaChamada<CatalogoDeImagem>

    /** `POST /configuracao/modelos-de-imagem/testar`: gera uma imagem de teste. **Gasta dinheiro** (MI5). */
    suspend fun testarImagem(modelo: String): ResultadoDaChamada<TesteDeImagem>

    /** `PUT /configuracao` com a lista de modelos de imagem que se pode escolher ao gerar (MI6). */
    suspend fun definirModelosDeImagem(modelos: List<String>): ResultadoDaChamada<ConfiguracaoAtual>

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

    override suspend fun catalogoDeImagem(): ResultadoDaChamada<CatalogoDeImagem> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.catalogoDeImagem() }
    }

    override suspend fun informarPreco(modelo: String, preco: String?): ResultadoDaChamada<CatalogoDeImagem> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.informarPrecoDoModelo(PrecoInformado(modelo, preco)) }
    }

    override suspend fun testarImagem(modelo: String): ResultadoDaChamada<TesteDeImagem> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.testarModeloDeImagem(mapOf("modelo" to modelo)) }
    }

    override suspend fun definirModelosDeImagem(modelos: List<String>): ResultadoDaChamada<ConfiguracaoAtual> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.gravarModelosDeImagem(ListaDeModelosDeImagem(modelos)) }
    }

    override suspend fun escolher(campo: String, modelo: String?): ResultadoDaChamada<ConfiguracaoAtual> {
        val api = provedor.obter() ?: return provedor.semServidor()
        // O servidor trata texto vazio como "limpar".
        return chamarApi { api.gravarConfiguracao(mapOf(campo to (modelo ?: ""))) }
    }
}
