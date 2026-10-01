package com.allan.imagineer.rede

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
     *
     * [orientacao] (item 6.7, M1): o que o usuário acha que faltou. Nulo = não mexe na que está guardada;
     * **vazio apaga**; não vazio roda a IA com ela.
     */
    suspend fun analisar(
        capituloId: Int,
        forcar: Boolean,
        orientacao: String? = null,
    ): ResultadoDaChamada<SugestoesDeCapitulo>

    /** `PATCH /sugestoes-cena/{id}`: descarta (`true`) ou restaura (`false`) a cena. Imediato e reversível (C6). */
    suspend fun descartarCena(sugestaoCenaId: Int, descartada: Boolean): ResultadoDaChamada<Unit>

    /**
     * `POST /capitulos/{id}/frames` com `sugestao_cena_id`: **confirma a cena** — vira um frame (C5). A falha traz
     * o código HTTP: **422** = falta confirmar um elemento da cena; **409** = a cena já estava confirmada.
     */
    suspend fun confirmarCena(capituloId: Int, sugestaoCenaId: Int): ResultadoDaChamada<FrameCriado>
}

/** A implementação de verdade, sobre o Retrofit. */
class RepositorioDeSugestoesPeloRetrofit(
    private val provedor: ProvedorDeApi,
) : RepositorioDeSugestoes {

    override suspend fun ler(capituloId: Int): ResultadoDaChamada<SugestoesDeCapitulo> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.sugestoes(capituloId) }
    }

    override suspend fun analisar(
        capituloId: Int,
        forcar: Boolean,
        orientacao: String?,
    ): ResultadoDaChamada<SugestoesDeCapitulo> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi {
            if (orientacao == null) api.analisar(capituloId, forcar)
            else api.analisarComOrientacao(capituloId, forcar, PedidoDeAnalise(orientacao))
        }
    }

    override suspend fun descartarCena(sugestaoCenaId: Int, descartada: Boolean): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject { put("descartada", descartada) }
        return chamarApi { api.ajustarCena(sugestaoCenaId, corpo); Unit }
    }

    override suspend fun confirmarCena(capituloId: Int, sugestaoCenaId: Int): ResultadoDaChamada<FrameCriado> {
        val api = provedor.obter() ?: return provedor.semServidor()
        // Só `sugestao_cena_id`: título, descrição e participantes vêm da própria sugestão (item 6.4).
        val corpo: JsonObject = buildJsonObject { put("sugestao_cena_id", sugestaoCenaId) }
        return chamarApi { api.confirmarCena(capituloId, corpo) }
    }
}
