package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.rede.Artefato

// Funções puras dos ícones sobre o texto (item 7.5b, incremento 11). Ficam fora do Compose e do
// ViewModel para serem testadas na JVM.

/** Um parágrafo e **onde ele começa** no texto do capítulo, em unidades UTF-16 (os índices de uma String Kotlin). */
data class ParagrafoDoTexto(val inicio: Int, val texto: String)

/**
 * Divide o texto em parágrafos **sabendo onde cada um começa** (item 3.4g): o servidor diz a posição de
 * um artefato como o início do parágrafo, e o app precisa achar de qual parágrafo é.
 *
 * Mesma regra de [dividirEmParagrafos] — divide em `\n{2,}`, apara e descarta o vazio —, mas guarda o
 * começo do texto **depois** do aparo. A unidade é UTF-16 de propósito: é a de um índice de `String` em
 * Kotlin, e a que o contrato do servidor usa.
 */
fun dividirEmParagrafosComInicio(texto: String): List<ParagrafoDoTexto> {
    val resultado = mutableListOf<ParagrafoDoTexto>()
    var inicioDoPedaco = 0
    fun acrescentar(fim: Int) {
        val pedaco = texto.substring(inicioDoPedaco, fim)
        val aparado = pedaco.trim()
        if (aparado.isNotEmpty()) {
            val folga = pedaco.length - pedaco.trimStart().length
            resultado += ParagrafoDoTexto(inicioDoPedaco + folga, aparado)
        }
    }
    for (separador in Regex("\n{2,}").findAll(texto)) {
        acrescentar(separador.range.first)
        inicioDoPedaco = separador.range.last + 1
    }
    acrescentar(texto.length)
    return resultado
}

/** Os artefatos de um capítulo, já distribuídos: um grupo por parágrafo e os que não têm posição. */
data class ArtefatosDistribuidos(
    /** O índice do parágrafo (na lista de [ParagrafoDoTexto]) → os artefatos dele, na ordem recebida. */
    val porParagrafo: Map<Int, List<Artefato>>,
    /** Os de posição nula, ou que não cabem em nenhum parágrafo: vão para a faixa "sem posição". */
    val semPosicao: List<Artefato>,
)

/**
 * Distribui cada artefato no parágrafo a que pertence: o **último** que começa na posição dele ou antes.
 * Isso tolera uma pequena diferença de aparo entre servidor e app. Artefato sem posição, ou com uma
 * posição antes do primeiro parágrafo, vai para [ArtefatosDistribuidos.semPosicao].
 */
fun distribuirArtefatos(artefatos: List<Artefato>, paragrafos: List<ParagrafoDoTexto>): ArtefatosDistribuidos {
    val porParagrafo = linkedMapOf<Int, MutableList<Artefato>>()
    val semPosicao = mutableListOf<Artefato>()
    for (artefato in artefatos) {
        val posicao = artefato.posicao_no_texto
        val indice = if (posicao == null) -1 else paragrafos.indexOfLast { it.inicio <= posicao }
        if (indice < 0) semPosicao += artefato else porParagrafo.getOrPut(indice) { mutableListOf() } += artefato
    }
    return ArtefatosDistribuidos(porParagrafo, semPosicao)
}

/** O texto de acessibilidade de um artefato: o nome e onde o usuário parou. */
fun descreverArtefato(artefato: Artefato): String {
    val situacao = when (artefato.situacao) {
        "SUGERIDO" -> "sugerido, ainda não confirmado"
        "CONFIRMADO" -> "confirmado"
        "PROMPT_PRONTO" -> "com prompt pronto"
        "ILUSTRADO" -> "ilustrado"
        else -> artefato.situacao.lowercase()
    }
    return "${artefato.rotulo}, $situacao"
}

/** Os dois quadros da imagem no texto (item 7.5b, I1). */
enum class QuadroDaImagem { RETRATO, PAISAGEM }

/**
 * O quadro pelo formato da **imagem real** (I1): `RETRATO` só se o servidor diz que a altura passa da largura; o resto, a
 * quadrada e a de dimensões desconhecidas (I7), é `PAISAGEM`. O layout do texto não depende do formato que se pediu à ferramenta.
 */
fun quadroDaImagem(orientacao: String?): QuadroDaImagem = if (orientacao == "RETRATO") QuadroDaImagem.RETRATO else QuadroDaImagem.PAISAGEM

/**
 * Os artefatos de um parágrafo que ganham **imagem no texto** (I6): só os `ILUSTRADO` que têm imagem, uma vez cada imagem
 * (dois artefatos do mesmo frame mostram a mesma).
 */
fun imagensDoParagrafo(artefatos: List<Artefato>): List<Artefato> =
    artefatos.filter { it.situacao == "ILUSTRADO" && (it.imagem_id != null || it.video_id != null) }
        .distinctBy { if (it.video_id != null) "v${it.video_id}" else "i${it.imagem_id}" }

/**
 * O formato do quadro de um artefato no texto (VD17): o do **vídeo**, se o texto mostra um (paisagem na largura da área de leitura, retrato na
 * metade, as mesmas regras da imagem), senão o da imagem.
 */
fun orientacaoDoQuadro(artefato: Artefato): String? = if (artefato.video_id != null) artefato.video_orientacao else artefato.imagem_orientacao
