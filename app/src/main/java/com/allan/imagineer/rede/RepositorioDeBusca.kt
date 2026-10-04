package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/** Um lugar onde o termo aparece, com o capítulo e o livro (LV5). Espelha a `OcorrenciaNoTexto` do servidor. */
@Serializable
@Suppress("PropertyName")
data class OcorrenciaNoTexto(
    val livro_id: Int,
    val livro_titulo: String,
    val capitulo_id: Int,
    val capitulo_ordem: Int,
    val capitulo_titulo: String? = null,
    /** Onde o achado começa, em UTF-16 desde o início do capítulo. */
    val posicao_no_texto: Int,
    /** Onde começa o parágrafo do achado (UTF-16): para onde o leitor rola. */
    val inicio_do_paragrafo: Int,
    /** O texto em volta do achado, numa linha só. */
    val trecho: String,
    val inicio_no_trecho: Int,
    val fim_no_trecho: Int,
)

/** O que `GET /busca` devolve: [total] conta todas as ocorrências, mesmo as que não vieram na lista. */
@Serializable
data class ResultadoDaBusca(
    val termo: String,
    val total: Int = 0,
    val ocorrencias: List<OcorrenciaNoTexto> = emptyList(),
    val truncado: Boolean = false,
)

/** A pesquisa no texto (LV5). Interface, para o ViewModel ser testado com uma versão falsa. Só leitura. */
interface RepositorioDeBusca {
    /** `GET /busca`: sem [livroId] e sem [capituloId] procura na biblioteca inteira. */
    suspend fun buscar(termo: String, livroId: Int? = null, capituloId: Int? = null): ResultadoDaChamada<ResultadoDaBusca>
}

class RepositorioDeBuscaPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeBusca {
    override suspend fun buscar(termo: String, livroId: Int?, capituloId: Int?): ResultadoDaChamada<ResultadoDaBusca> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.buscar(termo, livroId, capituloId) }
    }
}
