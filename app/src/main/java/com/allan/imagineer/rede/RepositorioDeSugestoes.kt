package com.allan.imagineer.rede

/**
 * O que o painel de IA precisa saber fazer com as sugestões de um capítulo. Interface, para o
 * ViewModel ser testado com uma versão falsa, sem rede e **sem gastar IA**.
 *
 * As duas primeiras funções são separadas de propósito: **ler nunca custa, gerar custa** (item 6.8).
 */
interface RepositorioDeSugestoes {

    /** `GET`: o que está salvo. Nunca chama a IA. */
    suspend fun ler(capituloId: Int): ResultadoDaChamada<SugestoesDeCapitulo>

    /**
     * `POST`: **gera** — chama a IA. [forcar] refaz as sugestões ainda não confirmadas; sem
     * ele, serve o que já está salvo (e só chama a IA se o capítulo nunca foi analisado).
     * É o único ponto do app que gasta IA.
     */
    suspend fun analisar(capituloId: Int, forcar: Boolean): ResultadoDaChamada<SugestoesDeCapitulo>
}

/** A implementação de verdade, sobre o Retrofit. */
class RepositorioDeSugestoesPeloRetrofit(
    private val provedor: ProvedorDeApi,
) : RepositorioDeSugestoes {

    override suspend fun ler(capituloId: Int): ResultadoDaChamada<SugestoesDeCapitulo> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.sugestoes(capituloId) }
    }

    override suspend fun analisar(capituloId: Int, forcar: Boolean): ResultadoDaChamada<SugestoesDeCapitulo> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.analisar(capituloId, forcar) }
    }
}
