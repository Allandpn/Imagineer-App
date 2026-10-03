package com.allan.imagineer.telas.elementos

import com.allan.imagineer.rede.ImagemDoElemento
import com.allan.imagineer.rede.CenaDoElemento
import com.allan.imagineer.rede.DetalheDoElemento
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.EstadoDoElemento
import java.text.Normalizer

// Funções puras da tela de Elementos (item 7.8, regras E19 a E21). Ficam fora do Compose e dos
// ViewModels para serem testadas na JVM.

/**
 * Como o app chama um capítulo (E19): pelo **título**; sem título, "Capítulo N" com a posição no
 * livro, como a tela de Livro. O número sozinho confundia: a posição 8 de *A Vontade de Muitos* é
 * o "Capítulo VI", porque "Créditos" e "Classificações" ocupam as posições 1 e 2.
 */
fun rotuloDoCapitulo(titulo: String?, ordem: Int?): String =
    titulo?.takeIf { it.isNotBlank() } ?: ordem?.let { "Capítulo $it" } ?: "Capítulo"

/** Sem ligar para maiúsculas nem acentos — a base da busca por nome. */
fun semAcentos(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/**
 * A lista de elementos da tela (E20): filtrada pelo [tipo] (nulo = todos) e pela [busca] no nome
 * (sem ligar para acento nem caixa), em ordem alfabética.
 */
fun filtrarElementos(elementos: List<ElementoDoLivro>, busca: String, tipo: String?): List<ElementoDoLivro> {
    val termo = semAcentos(busca.trim())
    return elementos
        .filter { tipo == null || it.tipo == tipo }
        .filter { termo.isEmpty() || semAcentos(it.nome).contains(termo) }
        .sortedBy { semAcentos(it.nome) }
}

/** Os tipos que existem de fato na lista, na ordem canônica — para os filtros não oferecerem o vazio. */
fun tiposPresentes(elementos: List<ElementoDoLivro>, ordemCanonica: List<String>): List<String> {
    val presentes = elementos.map { it.tipo }.toSet()
    return ordemCanonica.filter { it in presentes } + (presentes - ordemCanonica.toSet()).sorted()
}

/** Um acréscimo de identidade mostrado na ficha (E21): de qual capítulo, e o texto. */
data class AcrescimoDeIdentidade(val id: Int, val capitulo: String, val texto: String)

/** O que cada capítulo acrescentou sobre **quem** o elemento é, em ordem narrativa. */
fun acrescimosDeIdentidade(detalhe: DetalheDoElemento): List<AcrescimoDeIdentidade> =
    detalhe.historico_identidade
        .sortedWith(compareBy({ it.ordem_do_capitulo ?: Int.MAX_VALUE }, { it.id }))
        .map { AcrescimoDeIdentidade(it.id, rotuloDoCapitulo(it.titulo_do_capitulo, it.ordem_do_capitulo), it.descricao) }

/** Os estados de aparência em ordem narrativa (E21). */
fun estadosEmOrdem(detalhe: DetalheDoElemento): List<EstadoDoElemento> =
    detalhe.estados.sortedWith(compareBy({ it.ordem_do_capitulo ?: Int.MAX_VALUE }, { it.id }))

/** Um capítulo na lista de escolha (E39): o rótulo pelo título e se está arquivado. */
fun rotuloParaEscolherCapitulo(titulo: String?, ordem: Int, arquivado: Boolean): String =
    rotuloDoCapitulo(titulo, ordem) + if (arquivado) " (arquivado)" else ""

/** A lista de capítulos da escolha (E39): sem ligar para acento nem caixa, na ordem do livro. */
fun filtrarCapitulos(capitulos: List<com.allan.imagineer.rede.CapituloResumo>, busca: String): List<com.allan.imagineer.rede.CapituloResumo> {
    val termo = semAcentos(busca.trim())
    return capitulos
        .sortedBy { it.ordem }
        .filter { termo.isEmpty() || semAcentos(rotuloDoCapitulo(it.titulo, it.ordem)).contains(termo) }
}

/** O elemento já tem um estado neste capítulo? Decide se a ficha oferece "Adicionar estado" (E21). */
fun temEstadoNoCapitulo(detalhe: DetalheDoElemento, capituloId: Int): Boolean =
    detalhe.estados.any { it.capitulo_id == capituloId }

// ---------------------------------------------------------------------------------------------
// As imagens e as cenas na ficha (FI1 a FI9)
// ---------------------------------------------------------------------------------------------

/** A legenda de uma imagem da ficha (FI5, CAN6): o capítulo de onde veio e, se for a canônica, o selo (ou, só âncora, "referência principal"). */
fun legendaDaImagemDoElemento(imagem: ImagemDoElemento): String =
    rotuloDoCapitulo(imagem.titulo_do_capitulo, imagem.ordem_do_capitulo) + when {
        imagem.canonica -> " · canônica"
        imagem.ancora -> " · referência principal"
        else -> ""
    }

/** "Com Prato, Manto" (FI5), ou `null` quando a cena só tem este elemento. */
fun linhaDeParticipantes(cena: CenaDoElemento): String? =
    cena.participantes.takeIf { it.isNotEmpty() }?.let { "Com ${it.joinToString(", ")}" }

/** O que a cena tem de imagem: "ainda sem imagem", "1 imagem" ou "N imagens" (FI3, FI5). */
fun situacaoDaImagemDaCena(cena: CenaDoElemento): String = when {
    cena.imagem_id == null -> "ainda sem imagem"
    cena.total_de_imagens <= 1 -> "1 imagem"
    else -> "${cena.total_de_imagens} imagens"
}

/** O capítulo e a situação da imagem de uma cena, numa linha (FI5). */
fun resumoDaCena(cena: CenaDoElemento): String =
    rotuloDoCapitulo(cena.titulo_do_capitulo, cena.ordem_do_capitulo) + " · " + situacaoDaImagemDaCena(cena)

/** Esta imagem da ficha pode virar a canônica do retrato dela (CAN6)? Só se ainda não é. */
fun podeSerCanonica(imagem: ImagemDoElemento): Boolean = !imagem.canonica
