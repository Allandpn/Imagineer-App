package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.telas.capitulo.ParagrafoDoTexto
import com.allan.imagineer.telas.elementos.semAcentos

/** Quanto do trecho vai para a descrição da cena: o resto o servidor não precisa para ler o contexto (TR3). */
const val LIMITE_DO_TRECHO_NA_DESCRICAO = 1500

/** O botão da barra de seleção que começa o fluxo (TR1). */
const val ROTULO_GERAR_IMAGEM_DO_TRECHO = "Gerar imagem deste trecho"

/** Um elemento confirmado do capítulo que pode entrar na cena do trecho (TR2): o estado que vai ao servidor e o nome para mostrar. */
data class OpcaoDeElementoDoTrecho(val estadoId: Int, val nome: String)

/**
 * O parágrafo em que o [trecho] selecionado está: o início dele, em UTF-16, que é a posição que o servidor guarda (TR4). Compara sem
 * ligar para quebras de linha nem espaços repetidos (a seleção do Android os traz diferentes do texto). `null` se o trecho não está em
 * nenhum parágrafo (a seleção atravessou parágrafos, por exemplo): a cena nasce sem posição e cai na faixa "Sem posição no texto".
 */
fun paragrafoDoTrecho(paragrafos: List<ParagrafoDoTexto>, trecho: String): Int? {
    val procurado = espacosColapsados(trecho)
    if (procurado.isEmpty()) return null
    return paragrafos.firstOrNull { espacosColapsados(it.texto).contains(procurado) }?.inicio
}

private fun espacosColapsados(texto: String): String = texto.trim().replace(Regex("\\s+"), " ")

/** O título da cena do trecho (TR3): o começo do que a pessoa quer ver, ou do próprio trecho se ela não escreveu nada. */
fun tituloDaCenaDoTrecho(descricao: String, trecho: String): String {
    val base = espacosColapsados(descricao.ifBlank { trecho })
    return if (base.length <= 60) base else base.take(59).trimEnd() + "…"
}

/**
 * A descrição da cena do trecho (TR3): o que a pessoa quer ver, **com prioridade**, e o trecho como contexto. É o que o servidor
 * confere contra o capítulo (a análise barata) antes de o modelo de prompt, o caro, escrever o prompt.
 */
fun descricaoDaCenaDoTrecho(descricao: String, trecho: String): String {
    val contexto = "Trecho do capítulo: «${espacosColapsados(trecho).take(LIMITE_DO_TRECHO_NA_DESCRICAO)}»"
    return if (descricao.isBlank()) contexto else "${descricao.trim()}\n\n$contexto"
}

/**
 * Os elementos confirmados do capítulo que a cena do trecho pode levar (TR2): os casados com um elemento, não descartados e com um
 * estado a usar (o do capítulo, senão o vigente). Ordem alfabética. Só entram os que o servidor aceita como participante.
 */
fun opcoesDeElementosDoTrecho(elementos: List<ElementoSugerido>): List<OpcaoDeElementoDoTrecho> =
    elementos
        .filter { !it.descartada && it.elemento_casado != null }
        .mapNotNull { elemento -> (elemento.estado_id ?: elemento.estado_vigente?.id)?.let { OpcaoDeElementoDoTrecho(it, elemento.elemento_casado?.nome ?: elemento.nome) } }
        .distinctBy { it.estadoId }
        .sortedBy { semAcentos(it.nome) }

/** Os estados cujos nomes aparecem no [trecho]: já vêm marcados ao abrir, para a pessoa só desmarcar o que não quer (TR2). */
fun estadosCitadosNoTrecho(opcoes: List<OpcaoDeElementoDoTrecho>, trecho: String): Set<Int> {
    val texto = semAcentos(trecho)
    return opcoes.filter { semAcentos(it.nome).takeIf { nome -> nome.isNotBlank() }?.let(texto::contains) == true }.map { it.estadoId }.toSet()
}
