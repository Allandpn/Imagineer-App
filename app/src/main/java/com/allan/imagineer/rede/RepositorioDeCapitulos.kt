package com.allan.imagineer.rede

/**
 * O que as telas precisam saber fazer com capítulos. Interface, para o ViewModel
 * ser testado com uma versão falsa, sem rede.
 */
interface RepositorioDeCapitulos {

    /** `GET /capitulos/{id}`: o capítulo com o texto inteiro. */
    suspend fun abrirCapitulo(capituloId: Int): ResultadoDaChamada<CapituloDetalhe>

    /**
     * `PATCH /capitulos/{id}`: muda o título e/ou marca o capítulo como ignorado.
     *
     * O servidor devolve o capítulo **com o texto**; o app lê só o resumo (os campos
     * extras são ignorados) e descarta o resto.
     */
    suspend fun ajustarCapitulo(
        capituloId: Int,
        ajuste: CapituloAjuste,
    ): ResultadoDaChamada<CapituloResumo>
}

/** A implementação de verdade, sobre o Retrofit. */
class RepositorioDeCapitulosPeloRetrofit(
    private val provedor: ProvedorDeApi,
) : RepositorioDeCapitulos {

    override suspend fun abrirCapitulo(capituloId: Int): ResultadoDaChamada<CapituloDetalhe> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.capitulo(capituloId) }
    }

    override suspend fun ajustarCapitulo(
        capituloId: Int,
        ajuste: CapituloAjuste,
    ): ResultadoDaChamada<CapituloResumo> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.ajustarCapitulo(capituloId, ajuste) }
    }
}
