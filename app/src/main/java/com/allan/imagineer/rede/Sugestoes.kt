package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

// Os nomes dos campos são os do JSON do backend (item 7.3a). Os tipos de elemento ficam como
// texto, e não como enum: se o backend ganhar um tipo novo, o app antigo continua lendo a
// resposta em vez de quebrar na desserialização.

/**
 * O que `GET` e `POST /capitulos/{id}/sugestoes` devolvem (item 6.7 / 6.8).
 *
 * @property gerado_em quando a última análise rodou a IA; **nulo = o capítulo nunca foi
 * analisado** (e as listas vêm vazias). É o sinal que decide entre "Analisar" e "Reanalisar".
 * @property sugestoes_pendentes_anteriores sugestões de capítulos anteriores ainda não
 * confirmadas (item 4.6): só avisa que o contexto da análise está mais pobre.
 */
@Serializable
@Suppress("PropertyName")
data class SugestoesDeCapitulo(
    val gerado_em: String? = null,
    val sugestoes_pendentes_anteriores: Int = 0,
    val elementos: List<ElementoSugerido> = emptyList(),
    val cenas: List<CenaSugerida> = emptyList(),
)

/** Um elemento que a IA sugeriu para o capítulo: só identificação (item 4.4, fase 1). */
@Serializable
@Suppress("PropertyName")
data class ElementoSugerido(
    val id: Int,
    /** `PERSONAGEM`, `AMBIENTE`, `OBJETO`, `CRIATURA`, `GRUPO`, `VEICULO` ou `EDIFICACAO`. */
    val tipo: String,
    val nome: String,
    /** A identidade do elemento: quem ou o que é. Não a aparência. */
    val descricao: String? = null,
    val manter_estado_atual: Boolean = false,
    /** O elemento já cadastrado a que a sugestão corresponde; nulo = ainda não confirmada. */
    val elemento_id: Int? = null,
    /** `true` quando o casamento veio só do nome e ninguém o revisou (item 4.6). */
    val casamento_automatico: Boolean = false,
    /** O Estado já registrado neste capítulo; nulo com `elemento_id` = "casada, mas sem Estado". */
    val estado_id: Int? = null,
    val modelo: String = "",
)

/** Uma cena sugerida: elementos interagindo num momento que vale ilustrar. */
@Serializable
@Suppress("PropertyName")
data class CenaSugerida(
    val id: Int,
    val titulo: String,
    val descricao: String? = null,
    val horario: String? = null,
    val clima: String? = null,
    val humor: String? = null,
    val participantes: List<ParticipanteSugerido> = emptyList(),
    val modelo: String = "",
)

/** Quem aparece numa cena sugerida, com o mesmo casamento dos elementos. */
@Serializable
@Suppress("PropertyName")
data class ParticipanteSugerido(
    val sugestao_elemento_id: Int,
    val tipo: String,
    val nome: String,
    val elemento_id: Int? = null,
    val casamento_automatico: Boolean = false,
    val estado_id: Int? = null,
)
