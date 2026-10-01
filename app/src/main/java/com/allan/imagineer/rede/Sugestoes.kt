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
    /** A orientação do usuário que vale neste capítulo (item 6.7, M1); nulo = nenhuma. */
    val orientacao: String? = null,
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
    /**
     * `false` quando o nome não aparece no texto do capítulo (a IA pode ter listado o que o capítulo não traz,
     * ou o texto o chama por outro nome). Só sinaliza: o cartão mostra "confira" e vai para o fim da lista
     * (item 6.7). Padrão `true`: um servidor antigo, sem o campo, não gera aviso falso.
     */
    val achado_no_texto: Boolean = true,
    /** O Estado já registrado **neste** capítulo; nulo pode ainda haver um [estado_vigente] de outro. */
    val estado_id: Int? = null,
    /** Com quem a sugestão foi casada, com a identidade vigente (item 7.5b, E11). */
    val elemento_casado: ElementoCasado? = null,
    /** O estado que **vale** neste capítulo, talvez vindo de um capítulo anterior (E11, E12). */
    val estado_vigente: EstadoVigente? = null,
    /** Descartada pelo usuário: não conta como pendente e sobrevive à reanálise (E16). */
    val descartada: Boolean = false,
    val modelo: String = "",
)

/** O elemento cadastrado a que uma sugestão está ligada, com a identidade vigente até o capítulo. */
@Serializable
data class ElementoCasado(
    val id: Int,
    val tipo: String,
    val nome: String,
    val identidade: String? = null,
)

/** O estado de aparência que vale para o elemento casado neste capítulo. */
@Serializable
@Suppress("PropertyName")
data class EstadoVigente(
    val id: Int,
    val capitulo_id: Int,
    val ordem_do_capitulo: Int,
    /** Nulo quando o capítulo não tem título: a tela usa "Capítulo N" (E19). */
    val titulo_do_capitulo: String? = null,
    val descricao: String,
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
    /** Descartada pelo usuário: não conta como pendente (E16). */
    val descartada: Boolean = false,
    /** O frame criado a partir da cena: **nulo = pendente (ou descartada); preenchido = confirmada** (item 6.7). */
    val frame_id: Int? = null,
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

/**
 * O corpo **opcional** de `POST /capitulos/{id}/sugestoes` (item 6.7, M1): o que o usuário acha que a análise
 * deixou passar. Só vai quando há o que dizer; sem corpo, o servidor reanalisa como sempre.
 *
 * @property orientacao texto livre (até 1000 caracteres). Não vazio: roda a IA e fica guardado no capítulo.
 * **Vazio (`""`): apaga** a guardada e roda a IA sem ela.
 */
@Serializable
data class PedidoDeAnalise(
    val orientacao: String,
)
