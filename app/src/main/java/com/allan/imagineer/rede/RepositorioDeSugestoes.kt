package com.allan.imagineer.rede

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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

    /**
     * `POST /capitulos/{id}/frames` com `tipo=PERSONAGEM`: cria o **retrato** de um elemento a partir do estado que vale
     * neste capítulo (N2). Não gasta IA.
     */
    suspend fun criarRetrato(capituloId: Int, estadoId: Int, vinculadosIds: List<Int> = emptyList()): ResultadoDaChamada<FrameCriado>

    /**
     * `POST /capitulos/{id}/frames` (tipo `CENA`): a cena **avulsa** de um trecho selecionado (TR3), com a descrição da pessoa e o trecho,
     * a [posicao] do parágrafo e os [estadosIds] escolhidos. Não gasta IA; a análise e o prompt vêm depois, no botão de gerar.
     */
    suspend fun criarCenaDoTrecho(capituloId: Int, titulo: String, descricao: String, posicao: Int?, estadosIds: List<Int>): ResultadoDaChamada<FrameCriado>

    /** `PUT /frames/{id}/estados`: **substitui** os participantes de um frame, a cena (EV7). Não gasta IA. */
    suspend fun definirEstados(frameId: Int, estadosIds: List<Int>): ResultadoDaChamada<Unit>

    /** `GET /frames/{id}`: os elementos vinculados ao sujeito do retrato (V4). Nunca gasta IA. */
    suspend fun vinculosDoFrame(frameId: Int): ResultadoDaChamada<List<VinculadoDoFrame>>

    /** `PUT /sugestoes-elemento|cena/{id}/posicao` (PM1): põe o artefato no parágrafo que começa em [posicao] (UTF-16); nulo tira. Não gasta IA. */
    suspend fun posicionarArtefato(ehCena: Boolean, sugestaoId: Int, posicao: Int?): ResultadoDaChamada<Unit>

    /** `GET /frames/{id}`: as imagens escolhidas como referência, guardadas no servidor (RS1); valem em qualquer aparelho. */
    suspend fun referenciasDoFrame(frameId: Int): ResultadoDaChamada<List<Int>>

    /** `PUT /frames/{id}/referencias`: guarda a escolha (até 4 imagens; vazia limpa). Não gasta IA. */
    suspend fun guardarReferencias(frameId: Int, imagensIds: List<Int>): ResultadoDaChamada<Unit>

    /** `PUT /frames/{id}/vinculos`: **substitui** os vinculados do retrato (V4). Lista vazia tira todos. 422 = regra de personagem individual. */
    suspend fun definirVinculos(frameId: Int, estadosIds: List<Int>): ResultadoDaChamada<List<VinculadoDoFrame>>
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

    override suspend fun criarRetrato(capituloId: Int, estadoId: Int, vinculadosIds: List<Int>): ResultadoDaChamada<FrameCriado> {
        val api = provedor.obter() ?: return provedor.semServidor()
        // O título ("Retrato de <nome>") o servidor gera sozinho para um frame PERSONAGEM (item 6.4).
        val corpo: JsonObject = buildJsonObject {
            put("tipo", "PERSONAGEM")
            put("estados_ids", buildJsonArray { add(JsonPrimitive(estadoId)) })
            // V4: os elementos vinculados que a pessoa escolheu antes de o retrato existir; sem escolha o campo nem vai.
            if (vinculadosIds.isNotEmpty()) put("estados_vinculados_ids", buildJsonArray { vinculadosIds.forEach { add(JsonPrimitive(it)) } })
        }
        return chamarApi { api.criarRetrato(capituloId, corpo) }
    }

    override suspend fun criarCenaDoTrecho(
        capituloId: Int, titulo: String, descricao: String, posicao: Int?, estadosIds: List<Int>,
    ): ResultadoDaChamada<FrameCriado> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject {
            put("tipo", "CENA")
            put("titulo", titulo)
            put("descricao", descricao)
            if (posicao != null) put("posicao_no_texto", posicao)
            put("estados_ids", buildJsonArray { estadosIds.forEach { add(JsonPrimitive(it)) } })
        }
        return chamarApi { api.criarRetrato(capituloId, corpo) }
    }

    override suspend fun definirEstados(frameId: Int, estadosIds: List<Int>): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject { put("estados_ids", buildJsonArray { estadosIds.forEach { add(JsonPrimitive(it)) } }) }
        return chamarApi { api.definirEstados(frameId, corpo); Unit }
    }

    override suspend fun vinculosDoFrame(frameId: Int): ResultadoDaChamada<List<VinculadoDoFrame>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.frame(frameId).vinculados }
    }

    override suspend fun posicionarArtefato(ehCena: Boolean, sugestaoId: Int, posicao: Int?): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject { if (posicao != null) put("posicao_no_texto", posicao) else put("posicao_no_texto", kotlinx.serialization.json.JsonNull) }
        return chamarApi { if (ehCena) api.posicionarCena(sugestaoId, corpo) else api.posicionarElemento(sugestaoId, corpo); Unit }
    }

    override suspend fun referenciasDoFrame(frameId: Int): ResultadoDaChamada<List<Int>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.frame(frameId).imagens_de_referencia }
    }

    override suspend fun guardarReferencias(frameId: Int, imagensIds: List<Int>): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject { put("imagens_ids", buildJsonArray { imagensIds.forEach { add(JsonPrimitive(it)) } }) }
        return chamarApi { api.definirReferencias(frameId, corpo); Unit }
    }

    override suspend fun definirVinculos(frameId: Int, estadosIds: List<Int>): ResultadoDaChamada<List<VinculadoDoFrame>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject { put("estados_ids", buildJsonArray { estadosIds.forEach { add(JsonPrimitive(it)) } }) }
        return chamarApi { api.definirVinculos(frameId, corpo).vinculados }
    }

    override suspend fun confirmarCena(capituloId: Int, sugestaoCenaId: Int): ResultadoDaChamada<FrameCriado> {
        val api = provedor.obter() ?: return provedor.semServidor()
        // Só `sugestao_cena_id`: título, descrição e participantes vêm da própria sugestão (item 6.4).
        val corpo: JsonObject = buildJsonObject { put("sugestao_cena_id", sugestaoCenaId) }
        return chamarApi { api.confirmarCena(capituloId, corpo) }
    }
}
