package com.allan.imagineer.rede

/**
 * O que as telas precisam saber fazer com perfis de renderização. Interface, para
 * o ViewModel ser testado com uma versão falsa, sem rede.
 *
 * Por enquanto só leitura: criar e editar perfis é da tela de Perfis (Bloco E).
 */
interface RepositorioDePerfis {
    /** `GET /perfis-renderizacao`. */
    suspend fun listarPerfis(): ResultadoDaChamada<List<PerfilRenderizacao>>

    /** `GET /perfis-renderizacao/{id}`. */
    suspend fun abrirPerfil(perfilId: Int): ResultadoDaChamada<PerfilRenderizacao>
}

/** A implementação de verdade, sobre o Retrofit. */
class RepositorioDePerfisPeloRetrofit(
    private val provedor: ProvedorDeApi,
) : RepositorioDePerfis {

    override suspend fun listarPerfis(): ResultadoDaChamada<List<PerfilRenderizacao>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.perfis() }
    }

    override suspend fun abrirPerfil(perfilId: Int): ResultadoDaChamada<PerfilRenderizacao> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.perfil(perfilId) }
    }
}
