package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.rede.Artefato

// O texto fluindo ao redor do retrato (item 7.5b, I2 revisto e I10). Só as regras, **sem Compose**: quem mede as linhas
// de verdade (o `TextMeasurer`) entrega as medidas, e daqui sai onde cortar, para ser testado na JVM.

/** Uma linha de um parágrafo já medido: onde ela termina na vertical ([fundo], em pixels, desde o topo do parágrafo) e no texto ([fim], exclusivo). */
data class LinhaMedida(val fundo: Float, val fim: Int)

/**
 * Como os parágrafos se repartem ao lado de uma imagem de [alturaDaImagem] pixels. [paragrafosInteiros] parágrafos, a partir
 * do da imagem, cabem **inteiros** na coluna estreita; se [corte] não é nulo, o parágrafo seguinte é **cortado nesse índice**
 * do texto (o começo fica na coluna estreita e o **resto** vai embaixo, na largura inteira).
 */
data class DivisaoAoRedorDaImagem(val paragrafosInteiros: Int, val corte: Int?)

/**
 * Enche a altura da imagem com linhas (I10): põe parágrafos inteiros enquanto cabem, separados por [espaco] pixels; no primeiro
 * que não cabe, corta **no fim de uma linha** (a última que ainda cabe). Nunca sobra uma coluna estreita abaixo da imagem: o
 * que passa da altura volta à largura inteira. [linhas] dá as linhas medidas do parágrafo de índice `k` (0 = o da imagem);
 * [quantos] é o máximo de parágrafos que podem ser usados.
 */
fun dividirAoRedorDaImagem(
    alturaDaImagem: Float,
    espaco: Float,
    quantos: Int,
    linhas: (Int) -> List<LinhaMedida>,
): DivisaoAoRedorDaImagem {
    var y = 0f
    var inteiros = 0
    while (inteiros < quantos) {
        val doParagrafo = linhas(inteiros)
        if (doParagrafo.isEmpty()) break
        if (y + doParagrafo.last().fundo <= alturaDaImagem) {
            inteiros++
            y += doParagrafo.last().fundo + espaco
            if (y >= alturaDaImagem) return DivisaoAoRedorDaImagem(inteiros, null)
        } else {
            val cabem = doParagrafo.count { y + it.fundo <= alturaDaImagem }
            // Nenhuma linha cabe: este parágrafo e os seguintes ficam embaixo, na largura inteira.
            val corte = if (cabem in 1 until doParagrafo.size) doParagrafo[cabem - 1].fim else null
            return DivisaoAoRedorDaImagem(inteiros, corte)
        }
    }
    return DivisaoAoRedorDaImagem(inteiros, null)
}

/** Um pedaço de um parágrafo: `texto.substring(de, ate ?: texto.length)`. O parágrafo inteiro é `de = 0`, `ate = null`. */
data class FatiaDeParagrafo(val indice: Int, val de: Int = 0, val ate: Int? = null) {
    /** O pedaço do [texto] do parágrafo, sem espaços sobrando nas pontas do corte. */
    fun recortar(texto: String): String = texto.substring(de.coerceIn(0, texto.length), (ate ?: texto.length).coerceIn(0, texto.length)).trim()
}

/** Um item da lista do capítulo: parágrafo(s) com as imagens que entram ali (I2, I3, I10). */
sealed interface BlocoDoTexto {
    /** Um parágrafo (ou o que sobrou de um cortado), na largura inteira, com as imagens **paisagem** antes dele (I3). */
    data class Comum(val fatia: FatiaDeParagrafo, val paisagens: List<Artefato> = emptyList()) : BlocoDoTexto

    /**
     * Um **retrato** ao lado do texto (I2, I10): [fatias] na coluna estreita, o [retrato] na outra metade e, embaixo e na
     * largura inteira, o [resto] do parágrafo que foi cortado. [paisagens] vêm antes e [retratosExtras] depois, à direita.
     */
    data class ComRetrato(
        val retrato: Artefato,
        val fatias: List<FatiaDeParagrafo>,
        val resto: FatiaDeParagrafo?,
        val paisagens: List<Artefato> = emptyList(),
        val retratosExtras: List<Artefato> = emptyList(),
    ) : BlocoDoTexto
}

/**
 * Monta os blocos do capítulo (I2, I3, I10). Cada parágrafo vira um [BlocoDoTexto.Comum], a não ser que tenha uma imagem
 * **retrato**: então ele abre um [BlocoDoTexto.ComRetrato] que **consome** os parágrafos seguintes até encher a altura do quadro,
 * parando antes de um parágrafo que tenha imagem própria (cada imagem fica no seu lugar). [artefatosDo] diz os artefatos de
 * cada parágrafo; [alturaDoRetrato] e [espaco] vêm em pixels; [linhas] mede um parágrafo na **largura estreita**.
 */
fun montarBlocos(
    quantidade: Int,
    artefatosDo: (Int) -> List<Artefato>,
    alturaDoRetrato: Float,
    espaco: Float,
    linhas: (Int) -> List<LinhaMedida>,
): List<BlocoDoTexto> {
    val blocos = mutableListOf<BlocoDoTexto>()
    var i = 0
    while (i < quantidade) {
        val imagens = imagensDoParagrafo(artefatosDo(i))
        val (retratos, paisagens) = imagens.partition { quadroDaImagem(it.imagem_orientacao) == QuadroDaImagem.RETRATO }
        if (retratos.isEmpty()) {
            blocos += BlocoDoTexto.Comum(FatiaDeParagrafo(i), paisagens)
            i++
            continue
        }

        // Os parágrafos que o retrato pode consumir: o da imagem e os seguintes que não têm imagem própria.
        var limite = 1
        while (i + limite < quantidade && imagensDoParagrafo(artefatosDo(i + limite)).isEmpty()) limite++
        val divisao = dividirAoRedorDaImagem(alturaDoRetrato, espaco, limite) { linhas(i + it) }

        val fatias = mutableListOf<FatiaDeParagrafo>()
        repeat(divisao.paragrafosInteiros) { fatias += FatiaDeParagrafo(i + it) }
        var resto: FatiaDeParagrafo? = null
        var proximo = i + divisao.paragrafosInteiros
        if (divisao.corte != null) {
            fatias += FatiaDeParagrafo(proximo, 0, divisao.corte)
            resto = FatiaDeParagrafo(proximo, divisao.corte, null)
            proximo++
        }
        if (fatias.isEmpty()) {
            // O primeiro parágrafo não deu nenhuma linha ao lado (medida estranha): fica inteiro ao lado, para a imagem nunca ficar sozinha.
            fatias += FatiaDeParagrafo(i)
            proximo = i + 1
        }
        blocos += BlocoDoTexto.ComRetrato(retratos.first(), fatias, resto, paisagens, retratos.drop(1))
        i = proximo
    }
    return blocos
}
