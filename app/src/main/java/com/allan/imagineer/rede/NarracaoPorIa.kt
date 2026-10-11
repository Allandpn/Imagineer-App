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

/** Uma narração pronta e guardada no servidor (`GET /capitulos/{id}/audios`, item 7.5g, AU3): uma por (capítulo, modelo, voz). */
@Serializable
@Suppress("PropertyName")
data class AudioSalvo(
    val id: Int,
    val modelo: String,
    val voz: String? = null,
    val caracteres: Int? = null,
    val tamanho_em_bytes: Long? = null,
    /** O custo **estimado** em dólares; nulo = não se sabe. */
    val custo: JsonPrimitive? = null,
    val criado_em: String? = null,
    /** É a feita com o modelo e a voz escolhidos agora em Configurações → Narração. */
    val de_agora: Boolean = false,
)

/** A frase do teste de voz e quanto custa (`GET /configuracao/narracao/teste`, AU4). */
@Serializable
@Suppress("PropertyName")
data class EstimativaDoTesteDeVoz(
    val frase: String,
    val caracteres: Int,
    /** Dólares; zero = modelo gratuito; nulo = sem estimativa. */
    val custo_estimado: JsonPrimitive? = null,
    val modelo: String = "",
    val voz: String? = null,
)

/** O MP3 do teste de voz e o custo estimado que o servidor mandou no cabeçalho (AU4). */
class AmostraDeVoz(val mp3: ByteArray, val custoEstimado: String?)

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

/** O endereço do MP3 de uma narração guardada, pelo id (`GET /audios/{id}/arquivo`, AU3): é o que o player lê em fluxo. */
fun enderecoDoAudioSalvo(urlBase: String, audioId: Int): String = "${urlBase.trimEnd('/')}/audios/$audioId/arquivo"

/** O título de uma narração guardada na folha do fone (AU2): o modelo e, se houver, a voz. "microsoft/mai-voice-2.1-flash · pt-BR-Luana". */
fun tituloDoAudioSalvo(audio: AudioSalvo): String = if (audio.voz.isNullOrBlank()) audio.modelo else "${audio.modelo} · ${audio.voz}"

/** A linha de baixo de uma narração guardada (AU2): "2,4 MB · ≈ US$ 0,21 · 09/10/2026 · em uso agora". */
fun detalheDoAudioSalvo(audio: AudioSalvo): String {
    val pt = java.util.Locale("pt", "BR")
    val tamanho = audio.tamanho_em_bytes?.let { String.format(pt, "%.1f MB", it / 1_048_576.0) }
    val custo = decimalOuNulo(audio.custo)?.let { if (it <= 0.0) "grátis" else "≈ ${dolares(it)}" }
    val dia = audio.criado_em?.take(10)?.split("-")?.takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}/${it[0]}" }
    return listOfNotNull(tamanho, custo, dia, if (audio.de_agora) "em uso agora" else null).joinToString(" · ")
}

/** O texto do teste de voz antes de gerar (AU4): "Esta é a voz que… · ≈ US$ 0,0016" (ou "grátis", ou "sem estimativa de custo"). */
fun descreverCustoDoTeste(estimativa: EstimativaDoTesteDeVoz): String = descreverCustoEstimado(estimativa.custo_estimado)

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

    // Os métodos abaixo (item 7.5g) têm um padrão para os fakes antigos continuarem compilando.

    /** `GET /capitulos/{id}/audios`: as narrações prontas guardadas, da mais nova para a mais antiga (AU3). */
    suspend fun audios(capituloId: Int): ResultadoDaChamada<List<AudioSalvo>> = ResultadoDaChamada.Falha("Sem servidor.")

    /** `DELETE /audios/{id}`: apaga UMA narração guardada (AU3). */
    suspend fun apagarAudio(audioId: Int): ResultadoDaChamada<Unit> = ResultadoDaChamada.Falha("Sem servidor.")

    /** `GET /configuracao/narracao/teste`: a frase e o custo do teste de voz, antes de gerar (AU4). [modelo] e [voz] nulos = os da configuração. */
    suspend fun estimativaDoTeste(modelo: String?, voz: String?): ResultadoDaChamada<EstimativaDoTesteDeVoz> = ResultadoDaChamada.Falha("Sem servidor.")

    /** `POST /configuracao/narracao/teste`: o MP3 da frase de teste. Gasta uma fração de centavo (nada num modelo gratuito) (AU4). */
    suspend fun testarVoz(modelo: String?, voz: String?): ResultadoDaChamada<AmostraDeVoz> = ResultadoDaChamada.Falha("Sem servidor.")
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

    override suspend fun audios(capituloId: Int): ResultadoDaChamada<List<AudioSalvo>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.audiosDoCapitulo(capituloId) }
    }

    override suspend fun apagarAudio(audioId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.apagarAudio(audioId) }
    }

    override suspend fun estimativaDoTeste(modelo: String?, voz: String?): ResultadoDaChamada<EstimativaDoTesteDeVoz> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.estimativaDoTesteDeVoz(modelo?.takeIf { it.isNotBlank() }, voz?.takeIf { it.isNotBlank() }) }
    }

    override suspend fun testarVoz(modelo: String?, voz: String?): ResultadoDaChamada<AmostraDeVoz> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject {
            modelo?.takeIf { it.isNotBlank() }?.let { put("modelo", it) }
            voz?.takeIf { it.isNotBlank() }?.let { put("voz", it) }
        }
        return chamarApi {
            val resposta = api.testarAVoz(corpo)
            // O Retrofit não lança para um erro de `Response<>`: lança-se aqui, para o `chamarApi` traduzir a mensagem do servidor.
            if (!resposta.isSuccessful) throw retrofit2.HttpException(resposta)
            val bytes = resposta.body()?.use { it.bytes() } ?: ByteArray(0)
            AmostraDeVoz(bytes, resposta.headers()["X-Custo-Estimado"])
        }
    }
}
