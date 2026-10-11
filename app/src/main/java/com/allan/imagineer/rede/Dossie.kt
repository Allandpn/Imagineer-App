package com.allan.imagineer.rede

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

// O dossiê da cena (item 4.9, FL5 a FL9) e a conferência da imagem (FL13.1). Os nomes dos campos são os do JSON do servidor.

/** Os tipos de presente que o servidor aceita, na ordem em que o app os oferece. */
val TIPOS_DE_PRESENTE: List<String> = listOf("PESSOA", "CRIATURA", "OBJETO", "LUGAR")

/** O nome de um tipo de presente como a pessoa lê. */
fun rotuloDoTipoDePresente(tipo: String): String = when (tipo) {
    "PESSOA" -> "Pessoa"
    "CRIATURA" -> "Criatura"
    "OBJETO" -> "Objeto"
    "LUGAR" -> "Lugar"
    else -> tipo
}

/** Um campo do cenário (item 4.11, AF1): o [texto] e de onde ele veio ([origem]: `texto`, `anterior` ou `sugerido`). */
@Serializable
data class CampoDoCenario(val texto: String? = null, val origem: String = "texto")

/** O cenário e a fotografia da cena (item 4.11, AF1): ambiente, clima, luz, hora e estação, atmosfera, enquadramento e lente. Campo nulo = sem informação. */
@Serializable
data class CenarioDaCena(
    val ambiente: CampoDoCenario? = null,
    val clima: CampoDoCenario? = null,
    val iluminacao: CampoDoCenario? = null,
    val hora_e_estacao: CampoDoCenario? = null,
    val atmosfera: CampoDoCenario? = null,
    val enquadramento: CampoDoCenario? = null,
    val lente: CampoDoCenario? = null,
)

/** Quem ou o que aparece na cena naquele momento (FL5). [incluir] = a pessoa o deixou na cena (FL9). */
@Serializable
data class PresenteDaCena(
    val nome: String,
    val tipo: String = "OBJETO",
    /** O nome do elemento **cadastrado** a que ele corresponde; nulo = não é um (um prato, um cão que ninguém cadastrou). */
    val elemento: String? = null,
    val caracteristicas: String = "",
    /** A IA não tem certeza de que ele pertence a este momento. */
    val incerto: Boolean = false,
    val incluir: Boolean = true,
)

/** O dossiê da cena: o que a leitura do capítulo inteiro confirmou sobre aquele momento (FL5). */
@Serializable
data class DossieDaCena(
    val momento_incerto: Boolean = false,
    val presentes: List<PresenteDaCena> = emptyList(),
    val onde: String? = null,
    val luz_e_clima: String? = null,
    val acao: String? = null,
    /** O cenário e a fotografia (item 4.11, AF4). Nulo = dossiê lido antes de o cenário existir, ou servidor antigo. */
    val cenario: CenarioDaCena? = null,
    /** O que o texto não deixa claro (a IA não supôs). */
    val faltou: List<String> = emptyList(),
    /** A pessoa viu a lista e a aceitou (`PUT /frames/{id}/dossie`). */
    val confirmado: Boolean = false,
    /** O que entrou na leitura mudou depois dela: o próximo prompt a refaz. */
    val desatualizado: Boolean = false,
)

/** O dossiê recém-lido (`POST /frames/{id}/dossie`), com o que a leitura custou. [custo] vem em dólares **como texto**. */
@Serializable
data class DossieLido(
    val momento_incerto: Boolean = false,
    val presentes: List<PresenteDaCena> = emptyList(),
    val onde: String? = null,
    val luz_e_clima: String? = null,
    val acao: String? = null,
    val cenario: CenarioDaCena? = null,
    val faltou: List<String> = emptyList(),
    val confirmado: Boolean = false,
    val desatualizado: Boolean = false,
    val modelo: String = "",
    val custo: String? = null,
) {
    /** O mesmo dossiê, sem o modelo e o custo. */
    fun comoDossie(): DossieDaCena = DossieDaCena(momento_incerto, presentes, onde, luz_e_clima, acao, cenario, faltou, confirmado, desatualizado)
}

/** O corpo de `PUT /frames/{id}/dossie` (FL9): a lista inteira como a pessoa a deixou, mais o lugar, a luz e a ação. */
@Serializable
data class DossieParaGravar(
    val presentes: List<PresenteDaCena>,
    val onde: String? = null,
    val luz_e_clima: String? = null,
    val acao: String? = null,
    /** Só o que a pessoa deixou no cenário (AF8); campo ausente fica como estava no servidor. */
    val cenario: CenarioDaCena? = null,
)

/** O resultado de `POST /imagens/{id}/conferir` (FL13.1): uma opinião, que não muda nada. */
@Serializable
data class ConferenciaDaImagem(
    val conforme: Boolean = false,
    val divergencias: List<String> = emptyList(),
    val itens_conferidos: Int = 0,
    val modelo: String = "",
    val custo: String? = null,
)

/** Um presente como a ficha do prompt o guardou (FL13.2): só o que valeu para aquele prompt. */
@Serializable
data class PresenteDaFicha(
    val nome: String,
    val tipo: String = "",
    val elemento: String? = null,
    val caracteristicas: String = "",
    val incerto: Boolean = false,
)

/** A ficha do prompt (FL13.2): "de onde veio". O app só usa os presentes; o resto fica no servidor. */
@Serializable
data class FichaDoPrompt(val presentes: List<PresenteDaFicha> = emptyList())

/** `GET /frames/{id}/dossie` devolve `null` quando a cena ainda não foi lida: lê o JSON cru e decide. */
fun dossieDoJson(json: JsonElement): DossieDaCena? =
    if (json is JsonNull) null else jsonDoImagineer.decodeFromJsonElement(DossieDaCena.serializer(), json)
