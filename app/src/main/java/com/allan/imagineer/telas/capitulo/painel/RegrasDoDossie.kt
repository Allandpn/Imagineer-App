package com.allan.imagineer.telas.capitulo.painel

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

/** O que a pessoa está editando: a cópia da lista do servidor, que só volta a ele ao confirmar (AP3). */
data class RascunhoDoDossie(
    val presentes: List<PresenteDaCena> = emptyList(),
    val onde: String = "",
    val luzEClima: String = "",
    val acao: String = "",
)

/** Começa um rascunho a partir do que o servidor guardou (ou de uma lista vazia, se a cena ainda não foi lida). */
fun rascunhoDe(dossie: DossieDaCena?): RascunhoDoDossie = RascunhoDoDossie(
    presentes = dossie?.presentes.orEmpty(),
    onde = dossie?.onde.orEmpty(),
    luzEClima = dossie?.luz_e_clima.orEmpty(),
    acao = dossie?.acao.orEmpty(),
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
