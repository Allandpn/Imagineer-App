package com.allan.imagineer.rede

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
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

/**
 * Monta o cliente da API para um endereço.
 *
 * O endereço não é fixo (o usuário digita, e pode mudar), então não existe um
 * cliente único criado na inicialização — cada uso monta o seu a partir da URL.
 *
 * @param urlBase sem barra final, como o app guarda; o Retrofit exige a barra e
 * ela é acrescentada aqui.
 */
fun criarApi(urlBase: String): ApiImagineer {
    val cliente = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    return Retrofit.Builder()
        .baseUrl(urlBase.trimEnd('/') + "/")
        .client(cliente)
        .addConverterFactory(jsonDoImagineer.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiImagineer::class.java)
}
