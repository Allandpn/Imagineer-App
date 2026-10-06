package com.allan.imagineer.rede

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// A narração por voz de IA (bloco G, fase 1, AN1 a AN8): o servidor gera o MP3 do capítulo e o app o toca.

/**
 * Um modelo de voz que a pessoa escolhe em Configurações → Narração (`GET /configuracao/modelos-de-narracao`, NA1). O preço vem como
 * número ou texto conforme o servidor serializa o decimal: por isso [JsonPrimitive] e as funções abaixo.
 */
@Serializable
@Suppress("PropertyName")
data class ModeloDeNarracao(
    val id: String,
    val nome: String,
    val vozes: List<String> = emptyList(),
    /** Dólares por caractere; zero = grátis; nulo = o modelo cobra por token ou por segundo (sem estimativa). */
    val preco_por_caractere: JsonPrimitive? = null,
    val gratuito: Boolean = false,
)

/** Quanto custa narrar o capítulo, antes de gerar (`GET /capitulos/{id}/audio/estimativa`, NA3). */
@Serializable
@Suppress("PropertyName")
data class EstimativaDaNarracao(
    val caracteres: Int,
    val minutos: Double = 0.0,
    /** O custo **estimado** em dólares; nulo = não dá para estimar (nunca um número inventado). */
    val custo_estimado: JsonPrimitive? = null,
    val modelo: String = "",
    val voz: String? = null,
    /** Já existe um áudio pronto com o modelo e a voz de agora: gerar de novo é opcional. */
    val ja_gerado: Boolean = false,
)

/** A situação do áudio de um capítulo, com o modelo e a voz de agora (`GET /capitulos/{id}/audio/estado`, NA5). */
@Serializable
@Suppress("PropertyName")
data class EstadoDoAudio(
    /** `NAO_GERADO`, `GERANDO`, `PRONTO` ou `FALHOU`. */
    val situacao: String,
    val audio_id: Int? = null,
    val modelo: String? = null,
    val voz: String? = null,
    val caracteres: Int? = null,
    val tamanho_em_bytes: Long? = null,
    val custo: JsonPrimitive? = null,
    /** Por que falhou, em português (só se `FALHOU`). */
    val erro: String? = null,
    val criado_em: String? = null,
)

object SituacaoDoAudio {
    const val NAO_GERADO = "NAO_GERADO"
    const val GERANDO = "GERANDO"
    const val PRONTO = "PRONTO"
    const val FALHOU = "FALHOU"
}

/** O número de um decimal do servidor (que chega como texto ou como número), ou `null` se não há. */
fun decimalOuNulo(valor: JsonPrimitive?): Double? = valor?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()

/** Um valor em dólares como a pessoa lê: "US$ 0,21"; abaixo de um centavo mostra quatro casas ("US$ 0,0090"). */
fun dolares(valor: Double): String {
    val pt = java.util.Locale("pt", "BR")
    return if (valor >= 0.01) String.format(pt, "US$ %.2f", valor) else String.format(pt, "US$ %.4f", valor)
}

/** O custo estimado em palavras (AN3): "grátis", "≈ US$ 0,21" ou "sem estimativa de custo". */
fun descreverCustoEstimado(custo: JsonPrimitive?): String {
    val valor = decimalOuNulo(custo) ?: return "sem estimativa de custo"
    return if (valor <= 0.0) "grátis" else "≈ ${dolares(valor)}"
}

/** A linha da estimativa (AN3): "14.000 caracteres · cerca de 16 min · ≈ US$ 0,21". */
fun descreverEstimativa(estimativa: EstimativaDaNarracao): String {
    val pt = java.util.Locale("pt", "BR")
    val caracteres = String.format(pt, "%,d", estimativa.caracteres).replace(',', '.')
    val minutos = maxOf(1, Math.round(estimativa.minutos).toInt())
    return "$caracteres caracteres · cerca de $minutos min · ${descreverCustoEstimado(estimativa.custo_estimado)}"
}

/** O quanto custa um modelo, para a lista da tela de Narração: um capítulo de 14 mil caracteres como régua. */
fun descreverPrecoDoModelo(modelo: ModeloDeNarracao): String {
    if (modelo.gratuito) return "grátis"
    val preco = decimalOuNulo(modelo.preco_por_caractere) ?: return "cobrado por token ou por segundo (sem estimativa)"
    return if (preco <= 0.0) "grátis" else "≈ ${dolares(preco * CAPITULO_DE_REFERENCIA)} por capítulo de 14 mil caracteres"
}

private const val CAPITULO_DE_REFERENCIA = 14_000

/** A voz de IA só se oferece quando o servidor tem modelo de voz escolhido e chave para gastar (NA8). */
fun narracaoPorIaDisponivel(modeloNarracao: String?, temChave: Boolean): Boolean = !modeloNarracao.isNullOrBlank() && temChave

/** O endereço do MP3 do capítulo, para o player ler em fluxo (`GET /capitulos/{id}/audio`, que aceita pedaços). */
fun enderecoDaNarracao(urlBase: String, capituloId: Int): String = "${urlBase.trimEnd('/')}/capitulos/$capituloId/audio"

/** A narração por IA. Interface, para os ViewModels serem testados com uma versão falsa. */
interface RepositorioDeNarracao {
    /** `GET /configuracao/modelos-de-narracao`: os modelos de voz, os gratuitos primeiro. */
    suspend fun modelos(): ResultadoDaChamada<List<ModeloDeNarracao>>

    /** `GET /capitulos/{id}/audio/estimativa`. */
    suspend fun estimativa(capituloId: Int): ResultadoDaChamada<EstimativaDaNarracao>

    /** `GET /capitulos/{id}/audio/estado`. */
    suspend fun estado(capituloId: Int): ResultadoDaChamada<EstadoDoAudio>

    /** `POST /capitulos/{id}/audio`: 202 começou, 200 já havia um pronto; [refazer] gera de novo. */
    suspend fun gerar(capituloId: Int, refazer: Boolean): ResultadoDaChamada<EstadoDoAudio>

    /** `DELETE /capitulos/{id}/audio`: apaga os áudios do capítulo e os arquivos. */
    suspend fun apagar(capituloId: Int): ResultadoDaChamada<Unit>
}

class RepositorioDeNarracaoPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeNarracao {
    override suspend fun modelos(): ResultadoDaChamada<List<ModeloDeNarracao>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.modelosDeNarracao() }
    }

    override suspend fun estimativa(capituloId: Int): ResultadoDaChamada<EstimativaDaNarracao> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.estimativaDaNarracao(capituloId) }
    }

    override suspend fun estado(capituloId: Int): ResultadoDaChamada<EstadoDoAudio> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.estadoDaNarracao(capituloId) }
    }

    override suspend fun gerar(capituloId: Int, refazer: Boolean): ResultadoDaChamada<EstadoDoAudio> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject { put("refazer", refazer) }
        return chamarApi { api.gerarNarracao(capituloId, corpo) }
    }

    override suspend fun apagar(capituloId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.apagarNarracao(capituloId) }
    }
}
