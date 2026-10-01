package com.allan.imagineer.rede

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Header
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// Os nomes dos campos abaixo são os do JSON do backend, em português e em
// snake_case, de propósito (item 7.3a): o espelhamento fica óbvio, sem
// @SerialName de tradução. Por isso o aviso de estilo do Kotlin é suprimido.

/**
 * A API do Imagineer, como interface Kotlin (item 7.0).
 *
 * O Retrofit lê as anotações e gera a implementação sozinho. Cada função é
 * `suspend`: roda em segundo plano sem travar a tela.
 *
 * Cresce uma rota por vez, conforme cada tela do app é implementada.
 */
interface ApiImagineer {

    /** `GET /configuracao` — o que o servidor tem configurado (nunca a chave). */
    @GET("configuracao")
    suspend fun configuracao(): ConfiguracaoAtual

    /** `GET /livros` — a biblioteca, em ordem alfabética de título (item 6.2). */
    @GET("livros")
    suspend fun livros(): List<LivroResumo>

    /** `GET /livros/{id}` — o livro com a lista de capítulos, sem o texto. */
    @GET("livros/{id}")
    suspend fun livro(@Path("id") livroId: Int): LivroDetalhe

    /**
     * `GET /livros/{id}` com `If-None-Match` (item 6.9): se [revisaoConhecida] ainda é a do
     * servidor, ele responde `304` **sem corpo**. Devolve a resposta crua (`Response`) porque
     * `304` não é erro nem traz livro: quem chama olha o código. Com `null`, o cabeçalho
     * simplesmente não vai e o servidor responde `200` com o livro.
     */
    @GET("livros/{id}")
    suspend fun livroSeMudou(
        @Path("id") livroId: Int,
        @Header("If-None-Match") revisaoConhecida: String?,
    ): Response<LivroDetalhe>

    /**
     * `POST /livros` — importa um EPUB (`multipart/form-data`, campo `arquivo`).
     * Síncrono no servidor (item 6.2): a resposta já traz o livro estruturado.
     */
    @Multipart
    @POST("livros")
    suspend fun importarLivro(@Part arquivo: MultipartBody.Part): RespostaImportacao

    /**
     * `PATCH /livros/{id}` — corrige metadados e define o perfil padrão; devolve o livro
     * completo. O corpo é um `JsonObject` para poder mandar `null` de propósito
     * (ver [LivroAjuste.paraJson]).
     */
    @PATCH("livros/{id}")
    suspend fun ajustarLivro(@Path("id") livroId: Int, @Body ajuste: JsonObject): LivroDetalhe

    /** `GET /capitulos/{id}` — um capítulo **com** o texto. */
    @GET("capitulos/{id}")
    suspend fun capitulo(@Path("id") capituloId: Int): CapituloDetalhe

    /**
     * `PATCH /capitulos/{id}` — muda o título e/ou o `ignorado`. A resposta traz o
     * capítulo com o texto, mas o [CapituloResumo] lê só o que interessa.
     */
    @PATCH("capitulos/{id}")
    suspend fun ajustarCapitulo(
        @Path("id") capituloId: Int,
        @Body ajuste: CapituloAjuste,
    ): CapituloResumo

    /** `GET /perfis-renderizacao` — os perfis, compartilhados entre livros. */
    @GET("perfis-renderizacao")
    suspend fun perfis(): List<PerfilRenderizacao>

    /** `GET /perfis-renderizacao/{id}` — um perfil. */
    @GET("perfis-renderizacao/{id}")
    suspend fun perfil(@Path("id") perfilId: Int): PerfilRenderizacao

    /**
     * `GET /capitulos/{id}/sugestoes` — **só lê** o que está salvo. Nunca chama a IA, nunca
     * cobra (item 6.8): é o que o painel usa ao abrir.
     */
    @GET("capitulos/{id}/sugestoes")
    suspend fun sugestoes(@Path("id") capituloId: Int): SugestoesDeCapitulo

    /**
     * `POST /capitulos/{id}/sugestoes` — **gera** (e cobra): chama a IA se o capítulo nunca foi
     * analisado ou se [forcar] é `true`. É o único ponto do app que gasta IA.
     *
     * O cabeçalho `X-Timeout-Leitura` **não vai ao servidor**: o [interceptador de tempo de
     * espera][criarApi] o lê e o remove. Uma análise leva de segundos a mais de um minuto, bem
     * além dos 30 s comuns, que a dariam como falha enganosamente (item 7.5b, P10).
     */
    @Headers("X-Timeout-Leitura: 180")
    @POST("capitulos/{id}/sugestoes")
    suspend fun analisar(
        @Path("id") capituloId: Int,
        @Query("forcar") forcar: Boolean,
    ): SugestoesDeCapitulo

    /**
     * `POST /capitulos/{id}/sugestoes` **com a orientação do usuário** (item 6.7, M1). É o mesmo `POST`: o
     * servidor roda a IA mesmo sem `forcar`, porque mandar o texto já é o pedido de reanalisar.
     */
    @Headers("X-Timeout-Leitura: 180")
    @POST("capitulos/{id}/sugestoes")
    suspend fun analisarComOrientacao(
        @Path("id") capituloId: Int,
        @Query("forcar") forcar: Boolean,
        @Body pedido: PedidoDeAnalise,
    ): SugestoesDeCapitulo

    /** `GET /livros/{id}/elementos` — os elementos já cadastrados no livro (item 6.3). */
    @GET("livros/{id}/elementos")
    suspend fun elementosDoLivro(@Path("id") livroId: Int): List<ElementoDoLivro>

    /**
     * `POST /livros/{id}/elementos` — cadastra um elemento. Com `sugestoes_elemento_ids`, o
     * servidor também cria o Estado deste capítulo e liga a sugestão (item 3.4e). O corpo é um
     * `JsonObject` porque só vão os campos que o app preenche.
     */
    @POST("livros/{id}/elementos")
    suspend fun criarElemento(@Path("id") livroId: Int, @Body corpo: JsonObject): ElementoCriado

    /**
     * `POST /elementos/{id}/estados-de-sugestoes` — liga as sugestões ao elemento **e** cria um
     * Estado por sugestão (item 3.4e).
     */
    @POST("elementos/{id}/estados-de-sugestoes")
    suspend fun registrarEstados(
        @Path("id") elementoId: Int,
        @Body corpo: JsonObject,
    ): List<EstadoRegistrado>

    /**
     * `PATCH /sugestoes-elemento/{id}` — corrige **só** o casamento (`elemento_id`), sem criar
     * Estado (item 4.6). `null` desfaz o casamento; o mesmo id confirma o automático.
     */
    @PATCH("sugestoes-elemento/{id}")
    suspend fun ajustarCasamento(
        @Path("id") sugestaoId: Int,
        @Body corpo: JsonObject,
    ): ElementoSugerido

    /** `GET /capitulos/{id}/artefatos` — os ícones a desenhar sobre o texto; só leitura, nunca gasta IA (item 6.8). */
    @GET("capitulos/{id}/artefatos")
    suspend fun artefatos(@Path("id") capituloId: Int): ArtefatosDoCapitulo

    /** `GET /elementos/{id}` — o elemento com os estados e o histórico de identidade (item 3.4f). */
    @GET("elementos/{id}")
    suspend fun elemento(@Path("id") elementoId: Int): DetalheDoElemento

    /**
     * `POST /elementos/{id}/mesclar` — junta este elemento (a origem) ao `destino_id`, que fica:
     * estados, identidade e sugestões passam para ele, e a origem deixa de existir (item 6.3).
     */
    @POST("elementos/{id}/mesclar")
    suspend fun mesclarElemento(@Path("id") elementoId: Int, @Body corpo: JsonObject): DetalheDoElemento

    /** `POST /elementos/{id}/historico-identidade` — acrescenta à mão o que um capítulo revela sobre quem é (item 3.4f). */
    @POST("elementos/{id}/historico-identidade")
    suspend fun criarAcrescimo(@Path("id") elementoId: Int, @Body corpo: JsonObject): IdentidadeDoCapitulo

    /** `PATCH /historico-identidade/{id}` — corrige o texto de um acréscimo. */
    @PATCH("historico-identidade/{id}")
    suspend fun ajustarAcrescimo(@Path("id") acrescimoId: Int, @Body corpo: JsonObject): IdentidadeDoCapitulo

    /** `DELETE /historico-identidade/{id}` — apaga um acréscimo (204). */
    @DELETE("historico-identidade/{id}")
    suspend fun removerAcrescimo(@Path("id") acrescimoId: Int)

    /** `DELETE /elementos/{id}` — apaga o elemento e todos os estados dele (204). */
    @DELETE("elementos/{id}")
    suspend fun removerElemento(@Path("id") elementoId: Int)

    /** `PATCH /elementos/{id}` — corrige nome, tipo e/ou identidade; só vão os campos que mudam. */
    @PATCH("elementos/{id}")
    suspend fun ajustarElemento(@Path("id") elementoId: Int, @Body corpo: JsonObject): DetalheDoElemento

    /** `POST /elementos/{id}/estados` — registra o estado do elemento num capítulo. */
    @POST("elementos/{id}/estados")
    suspend fun criarEstado(@Path("id") elementoId: Int, @Body corpo: JsonObject): EstadoRegistrado

    /** `PATCH /estados/{id}` — muda a descrição do estado. */
    @PATCH("estados/{id}")
    suspend fun ajustarEstado(@Path("id") estadoId: Int, @Body corpo: JsonObject): EstadoRegistrado

    /** `DELETE /estados/{id}` — apaga o estado (204). Frames que o citavam perdem esse participante. */
    @DELETE("estados/{id}")
    suspend fun removerEstado(@Path("id") estadoId: Int)

    /** `DELETE /livros/{id}` — remove o livro e tudo que depende dele (204, sem corpo). */
    @DELETE("livros/{id}")
    suspend fun removerLivro(@Path("id") livroId: Int)
}

/** Resposta de `GET /configuracao` (item 4.3 do backend). */
@Serializable
@Suppress("PropertyName")
data class ConfiguracaoAtual(
    val tem_chave_api: Boolean,
    val origem_da_chave: String,
    val modelo_extracao: String? = null,
    val modelo_prompt: String? = null,
    val modelo_perfil: String? = null,
    val prioridade_ia: String,
)

/**
 * `ignoreUnknownKeys`: se o backend ganhar um campo novo, o app antigo continua
 * funcionando. O contrário (campo que o app espera e o servidor não manda) segue
 * sendo erro, de propósito.
 */
val jsonDoImagineer = Json { ignoreUnknownKeys = true }

/** O cabeçalho que uma chamada usa para pedir um tempo de espera de leitura maior. */
const val CABECALHO_TEMPO_DE_ESPERA = "X-Timeout-Leitura"

/**
 * Dá a **uma chamada** um tempo de espera de leitura maior, sem mexer nas outras.
 *
 * Lê o cabeçalho [CABECALHO_TEMPO_DE_ESPERA] (em segundos), aplica só àquele pedido e o
 * **remove** antes de enviar — o servidor nunca o vê. Assim uma análise de IA, que leva
 * muito mais que 30 s, não é dada como falha, e todas as demais chamadas continuam
 * falhando depressa quando o servidor não responde.
 */
internal val interceptadorDeTempoDeEspera = Interceptor { cadeia ->
    val pedido = cadeia.request()
    val segundos = pedido.header(CABECALHO_TEMPO_DE_ESPERA)?.toIntOrNull()
    val semCabecalho = pedido.newBuilder().removeHeader(CABECALHO_TEMPO_DE_ESPERA).build()
    val cadeiaAjustada = if (segundos != null) cadeia.withReadTimeout(segundos, TimeUnit.SECONDS) else cadeia
    cadeiaAjustada.proceed(semCabecalho)
}

/**
 * Monta o cliente da API para um endereço.
 *
 * O endereço não é fixo (o usuário digita, e pode mudar), então não existe um
 * cliente único criado na inicialização — cada uso monta o seu a partir da URL.
 *
 * @param urlBase sem barra final, como o app guarda; o Retrofit exige a barra e
 * ela é acrescentada aqui.
 */
fun criarApi(urlBase: String, leituraPadraoEmSegundos: Long = 30): ApiImagineer {
    val cliente = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(leituraPadraoEmSegundos, TimeUnit.SECONDS)
        // O padrão (10 s por escrita) é curto para subir um EPUB numa conexão lenta.
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(interceptadorDeTempoDeEspera)
        .build()

    return Retrofit.Builder()
        .baseUrl(urlBase.trimEnd('/') + "/")
        .client(cliente)
        .addConverterFactory(jsonDoImagineer.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiImagineer::class.java)
}
