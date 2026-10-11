package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.CampoDoCenario
import com.allan.imagineer.rede.CenarioDaCena
import com.allan.imagineer.rede.DossieDaCena
import com.allan.imagineer.rede.DossieParaGravar
import com.allan.imagineer.rede.FichaDoPrompt
import com.allan.imagineer.rede.PresenteDaCena

// As regras puras da lista "O que vai aparecer" (item 4.9, FL9; especificação 7.5d, AP1 a AP5). Sem Android, para testar.

const val AVISO_MOMENTO_INCERTO =
    "Esta cena não tem um trecho do livro marcado; a lista pode misturar momentos. Selecione o trecho para a leitura acertar."
const val AVISO_DOSSIE_DESATUALIZADO =
    "A cena mudou depois desta leitura: o próximo prompt lê de novo e a confirmação se perde."
const val AVISO_SEM_DOSSIE =
    "O servidor ainda não leu esta cena. Ele lê sozinho quando você gerar o prompt; ou leia agora para ver e ajustar a lista antes."
const val AVISO_ALTERACOES_NAO_CONFIRMADAS = "Alterações ainda não confirmadas."
const val AVISO_LISTA_CONFIRMADA = "Lista confirmada."

/** AP8: depois de editar e confirmar a lista, os prompts que a cena já tinha não a levam em conta. */
const val AVISO_LISTA_MUDOU = "A lista mudou. Os prompts antigos não a levam em conta. Gerar um prompt novo?"

/**
 * O aviso da lista mudada deve aparecer? (AP8) Sim enquanto a cena tem **o mesmo número de prompts** que tinha quando a lista foi confirmada
 * ([avisadoCom]) e esse número não é zero: nasceu um prompt novo (a quantidade mudou) e o aviso some. Sem prompt, o próximo já nasce com a lista.
 */
fun listaMudouSemPromptNovo(avisadoCom: Int?, promptsAgora: Int?): Boolean = avisadoCom != null && avisadoCom > 0 && avisadoCom == promptsAgora

/** O que a pessoa está editando: a cópia da lista do servidor, que só volta a ele ao confirmar (AP3). */
data class RascunhoDoDossie(
    val presentes: List<PresenteDaCena> = emptyList(),
    val onde: String = "",
    val luzEClima: String = "",
    val acao: String = "",
    /** O texto de cada campo do cenário (item 4.11, AF8), pela chave do servidor (`ambiente`, `clima`...). Vazio = sem informação. */
    val cenario: Map<String, String> = emptyMap(),
)

/** Os 7 campos do cenário, na ordem da tela, com o nome que a pessoa lê (item 4.11, AF1). A chave é a do servidor. */
val CAMPOS_DO_CENARIO: List<Pair<String, String>> = listOf(
    "ambiente" to "Ambiente",
    "clima" to "Clima",
    "iluminacao" to "Iluminação",
    "hora_e_estacao" to "Hora do dia e estação",
    "atmosfera" to "Paleta e atmosfera",
    "enquadramento" to "Enquadramento e ângulo",
    "lente" to "Lente e profundidade de campo",
)

/** O campo [chave] do cenário (ou `null` se o cenário não o tem). */
fun CenarioDaCena.campo(chave: String): CampoDoCenario? = when (chave) {
    "ambiente" -> ambiente
    "clima" -> clima
    "iluminacao" -> iluminacao
    "hora_e_estacao" -> hora_e_estacao
    "atmosfera" -> atmosfera
    "enquadramento" -> enquadramento
    "lente" -> lente
    else -> null
}

/** De onde veio um campo do cenário, como a pessoa lê (AF8): "do texto", "herdado do anterior" ou "sugerido". */
fun rotuloDaOrigemDoCenario(origem: String): String = when (origem) {
    "anterior" -> "herdado do anterior"
    "sugerido" -> "sugerido"
    else -> "do texto"
}

/**
 * A etiqueta de um campo do cenário na tela (AF8): a origem que o servidor guardou, ou "editado por você" se o texto do [rascunho] já difere do que
 * o servidor tem; vazia (sem etiqueta) se o campo está em branco.
 */
fun etiquetaDoCampoDoCenario(guardado: DossieDaCena?, rascunho: RascunhoDoDossie, chave: String): String? {
    val texto = rascunho.cenario[chave].orEmpty().trim()
    val noServidor = guardado?.cenario?.campo(chave)
    if (texto.isEmpty()) return null
    if (texto != noServidor?.texto?.trim().orEmpty()) return "editado por você"
    return rotuloDaOrigemDoCenario(noServidor?.origem ?: "texto")
}

/** As linhas "Rótulo: texto (origem)" de um cenário só para ler (a ficha do estado do elemento, item 4.11, AF8); vazia se não há nada. */
fun linhasDoCenario(cenario: CenarioDaCena?): List<String> = CAMPOS_DO_CENARIO.mapNotNull { (chave, rotulo) ->
    val campo = cenario?.campo(chave)
    val texto = campo?.texto?.trim().orEmpty()
    if (texto.isEmpty()) null else "$rotulo: $texto" + (campo?.origem?.takeIf { it != "texto" }?.let { " (${rotuloDaOrigemDoCenario(it)})" } ?: "")
}

/** O bloco Cenário aparece se o servidor leu um cenário (servidor antigo ou dossiê antigo não têm; item 4.11, AF8). */
fun temCenario(guardado: DossieDaCena?): Boolean = guardado?.cenario != null

/** Começa um rascunho a partir do que o servidor guardou (ou de uma lista vazia, se a cena ainda não foi lida). */
fun rascunhoDe(dossie: DossieDaCena?): RascunhoDoDossie = RascunhoDoDossie(
    presentes = dossie?.presentes.orEmpty(),
    onde = dossie?.onde.orEmpty(),
    luzEClima = dossie?.luz_e_clima.orEmpty(),
    acao = dossie?.acao.orEmpty(),
    cenario = CAMPOS_DO_CENARIO.associate { (chave, _) -> chave to dossie?.cenario?.campo(chave)?.texto.orEmpty() },
)

/** Marca ou desmarca o item [indice] (tirá-lo da cena é desmarcar a caixa; ele continua na tela, AP3). */
fun RascunhoDoDossie.comPresenteAlternado(indice: Int): RascunhoDoDossie = comPresente(indice) { it.copy(incluir = !it.incluir) }

/** Troca o texto das características do item [indice]. */
fun RascunhoDoDossie.comCaracteristicas(indice: Int, texto: String): RascunhoDoDossie = comPresente(indice) { it.copy(caracteristicas = texto) }

private fun RascunhoDoDossie.comPresente(indice: Int, mudar: (PresenteDaCena) -> PresenteDaCena): RascunhoDoDossie =
    if (indice !in presentes.indices) this else copy(presentes = presentes.mapIndexed { i, p -> if (i == indice) mudar(p) else p })

/**
 * Acrescenta um item livre ao fim (AP3): nome, tipo e características. Entra sem `elemento` (não é um cadastrado) e incluído. Sem
 * nome não entra; o tipo desconhecido vira `OBJETO`.
 */
fun RascunhoDoDossie.comPresenteNovo(nome: String, tipo: String, caracteristicas: String): RascunhoDoDossie {
    val limpo = nome.trim()
    if (limpo.isEmpty()) return this
    val tipoValido = if (tipo in com.allan.imagineer.rede.TIPOS_DE_PRESENTE) tipo else "OBJETO"
    return copy(presentes = presentes + PresenteDaCena(nome = limpo, tipo = tipoValido, caracteristicas = caracteristicas.trim(), incluir = true))
}

/**
 * O corpo do `PUT`: a lista inteira como a pessoa a deixou. Lugar, luz e ação em branco vão como `null` (o servidor deixa como
 * estavam) a não ser que o servidor já tivesse um valor, caso em que o texto vazio **limpa** (a pessoa apagou de propósito).
 */
fun RascunhoDoDossie.paraGravar(guardado: DossieDaCena?): DossieParaGravar {
    fun campo(texto: String, antes: String?): String? = texto.trim().takeIf { it.isNotEmpty() } ?: if (antes != null) "" else null
    return DossieParaGravar(
        presentes = presentes,
        onde = campo(onde, guardado?.onde),
        luz_e_clima = campo(luzEClima, guardado?.luz_e_clima),
        acao = campo(acao, guardado?.acao),
        cenario = cenarioParaGravar(guardado),
    )
}

/**
 * O cenário do `PUT` (item 4.11, AF8): cada campo com o texto do rascunho e a origem que o servidor guardou (o servidor decide se virou palavra da
 * pessoa, comparando o texto). Campo em branco vai como `texto: null` (apaga) só se o servidor tinha texto; senão não vai. `null` se o servidor não tem cenário.
 */
private fun RascunhoDoDossie.cenarioParaGravar(guardado: DossieDaCena?): CenarioDaCena? {
    if (!temCenario(guardado)) return null
    fun campoDe(chave: String): CampoDoCenario? {
        val texto = cenario[chave].orEmpty().trim()
        val antes = guardado?.cenario?.campo(chave)
        return when {
            texto.isNotEmpty() -> CampoDoCenario(texto, antes?.origem ?: "texto")
            antes?.texto.isNullOrBlank() -> null
            else -> CampoDoCenario(null)
        }
    }
    return CenarioDaCena(
        ambiente = campoDe("ambiente"), clima = campoDe("clima"), iluminacao = campoDe("iluminacao"), hora_e_estacao = campoDe("hora_e_estacao"),
        atmosfera = campoDe("atmosfera"), enquadramento = campoDe("enquadramento"), lente = campoDe("lente"),
    )
}

/** Quantos itens a pessoa deixou na cena (os marcados). */
fun quantosAparecem(presentes: List<PresenteDaCena>): Int = presentes.count { it.incluir }

/** A linha do cartão recolhido (AP1): "ainda não lida", "4 itens · lida", "4 itens · confirmada" + os avisos curtos. */
fun resumoDoDossie(guardado: DossieDaCena?, comRascunho: Boolean, rascunho: RascunhoDoDossie? = null): String {
    if (guardado == null && !comRascunho) return "ainda não lida"
    val itens = quantosAparecem(if (comRascunho) rascunho?.presentes.orEmpty() else guardado?.presentes.orEmpty())
    val partes = mutableListOf(if (itens == 1) "1 item" else "$itens itens")
    if (guardado != null) partes += if (guardado.confirmado) "confirmada" else "lida"
    if (guardado?.desatualizado == true) partes += "a cena mudou"
    if (comRascunho) partes += "alterações não confirmadas"
    return partes.joinToString(" · ")
}

/** "Entrou: Auri, Hospius, um prato" (AP5): só o que valeu para o prompt; `null` sem ficha ou sem presentes (prompt antigo, retrato). */
fun linhaDaFicha(ficha: FichaDoPrompt?): String? {
    val nomes = ficha?.presentes?.map { it.nome }?.filter { it.isNotBlank() }.orEmpty()
    return if (nomes.isEmpty()) null else "Entrou: " + nomes.joinToString(", ")
}

const val ROTULO_CONFERIR_COM_A_LISTA = "Conferir com a lista"

/** Os prompts cujas imagens podem ser conferidas (AP7): os que guardaram a lista do que deveria aparecer. */
fun promptsConferiveis(lista: List<com.allan.imagineer.rede.PromptDeFrame>): Set<Int> =
    lista.filter { !it.so_imagem && it.ficha?.presentes?.isNotEmpty() == true }.map { it.id }.toSet()

/** "O texto não deixa claro: ..." para cada frase de `faltou` (AP4). */
fun avisosDoQueFaltou(dossie: DossieDaCena?): List<String> = dossie?.faltou.orEmpty().filter { it.isNotBlank() }.map { "O texto não deixa claro: ${it.trim().trimEnd('.')}." }

/** Os custos vêm em dólares como texto; "US$ 0,0123" ou nada (sem custo informado). */
fun custoDaChamada(custo: String?): String? = custo?.toDoubleOrNull()?.let { com.allan.imagineer.telas.menu.formatarDolar(custo) }
