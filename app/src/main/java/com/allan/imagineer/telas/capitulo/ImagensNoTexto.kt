package com.allan.imagineer.telas.capitulo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.enderecoDaImagem

/**
 * Uma imagem **desenhada no texto** do capítulo (item 7.5b, I1 a I6). O quadro é fixo, **retrato 2:3** ou **paisagem 16:9**,
 * escolhido pela imagem real ([quadroDaImagem]); a imagem entra **inteira** e centralizada, e o que sobra do quadro fica
 * **preto** (letterbox), então o texto nunca depende de a ferramenta ter acertado a proporção. Carrega o tamanho `leitura`
 * (I5); tocar a **amplia** ([aoTocar]). Quem chama dá a largura ([modifier]); a altura vem da proporção.
 */
@Composable
internal fun QuadroDaImagemNoTexto(artefato: Artefato, urlBase: String, modifier: Modifier, aoTocar: () -> Unit) {
    val imagemId = artefato.imagem_id ?: return
    val proporcao = if (quadroDaImagem(artefato.imagem_orientacao) == QuadroDaImagem.RETRATO) 2f / 3f else 16f / 9f
    Box(
        modifier = modifier
            .aspectRatio(proporcao)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .clickable(onClickLabel = "Ampliar a imagem", onClick = aoTocar),
    ) {
        AsyncImage(
            model = enderecoDaImagem(urlBase, imagemId, "leitura"),
            contentDescription = "Imagem de ${artefato.rotulo}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Imagem de ${artefato.rotulo}. Toque para ampliar." },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------- //
// Os ícones dentro da linha e os blocos do texto (I9, I10)
// ---------------------------------------------------------------------------------------------------------------- //

/** O tamanho de um ícone dentro da linha: o de uma linha de leitura (I9). */
internal val TAMANHO_DO_ICONE_NA_LINHA = 26.sp

/** O parágrafo com os ícones desenhados **no começo**, cada um seguido de um espaço, e o que isso soma ao texto. */
internal class TextoComIcones(val anotado: AnnotatedString, val prefixo: Int, val marcadores: List<AnnotatedString.Range<Placeholder>>)

/** Monta o texto com [quantidade] ícones no começo (I9). O `prefixo` é quantos caracteres os ícones ocupam antes do texto. */
internal fun textoComIcones(texto: String, quantidade: Int): TextoComIcones {
    val anotado = buildAnnotatedString {
        repeat(quantidade) {
            appendInlineContent("icone$it", "\uFFFD")
            append(" ")
        }
        append(texto)
    }
    val marcador = Placeholder(TAMANHO_DO_ICONE_NA_LINHA, TAMANHO_DO_ICONE_NA_LINHA, PlaceholderVerticalAlign.TextCenter)
    return TextoComIcones(anotado, quantidade * 2, List(quantidade) { AnnotatedString.Range(marcador, it * 2, it * 2 + 1) })
}

/** Um ícone de artefato dentro da linha de texto: o desenho do tipo, a cor da situação, tocável (leva ao painel). */
@Composable
private fun IconeNaLinha(artefato: Artefato, aoTocar: (Artefato) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClickLabel = "Abrir no painel de IA") { aoTocar(artefato) }
            .semantics { contentDescription = descreverArtefato(artefato) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconeDoArtefato(artefato),
            contentDescription = null,
            tint = corDoArtefatoNaLinha(artefato),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Um parágrafo (ou um pedaço dele) com os [icones] dentro da primeira linha (I9). */
@Composable
internal fun TextoDoParagrafo(texto: String, icones: List<Artefato>, aoTocar: (Artefato) -> Unit, estilo: TextStyle, modifier: Modifier = Modifier) {
    val montado = remember(texto, icones.size) { textoComIcones(texto, icones.size) }
    val conteudo = remember(icones, aoTocar) {
        icones.mapIndexed { indice, artefato ->
            "icone$indice" to InlineTextContent(montado.marcadores[indice].item) { IconeNaLinha(artefato, aoTocar) }
        }.toMap()
    }
    Text(montado.anotado, inlineContent = conteudo, style = estilo, modifier = modifier)
}

/** Um bloco do capítulo na tela (I2, I3, I10): o(s) parágrafo(s) e as imagens, como o [montarBlocos] decidiu. */
@Composable
internal fun BlocoDoTextoNaTela(
    bloco: BlocoDoTexto,
    textoDe: (Int) -> String,
    artefatosDo: (Int) -> List<Artefato>,
    urlBase: String?,
    larguraDoQuadro: Dp,
    estilo: TextStyle,
    aoTocarArtefato: (Artefato) -> Unit,
    aoAmpliar: (Artefato) -> Unit,
) {
    @Composable
    fun fatia(f: FatiaDeParagrafo, modifier: Modifier = Modifier) = TextoDoParagrafo(
        texto = f.recortar(textoDe(f.indice)),
        // Os ícones ficam no começo do parágrafo: só o primeiro pedaço os leva (o resto de um corte não).
        icones = if (f.de == 0) artefatosDo(f.indice) else emptyList(),
        aoTocar = aoTocarArtefato,
        estilo = estilo,
        modifier = modifier,
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (bloco) {
            is BlocoDoTexto.Comum -> {
                // I3: a paisagem tem a largura do texto e vem antes do parágrafo.
                if (urlBase != null) bloco.paisagens.forEach { QuadroDaImagemNoTexto(it, urlBase, Modifier.fillMaxWidth()) { aoAmpliar(it) } }
                fatia(bloco.fatia)
            }
            is BlocoDoTexto.ComRetrato -> {
                if (urlBase != null) bloco.paisagens.forEach { QuadroDaImagemNoTexto(it, urlBase, Modifier.fillMaxWidth()) { aoAmpliar(it) } }
                // I2, I10: a coluna estreita do texto e, ao lado, o quadro, que tem metade da largura da área de leitura.
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        bloco.fatias.forEach { fatia(it) }
                    }
                    Spacer(Modifier.width(ESPACO_AO_LADO_DO_QUADRO))
                    if (urlBase != null) {
                        QuadroDaImagemNoTexto(bloco.retrato, urlBase, Modifier.width(larguraDoQuadro)) { aoAmpliar(bloco.retrato) }
                    }
                }
                // O que passou da altura do quadro volta à largura inteira.
                bloco.resto?.let { fatia(it) }
                if (urlBase != null) bloco.retratosExtras.forEach {
                    Row {
                        Spacer(Modifier.weight(1f))
                        QuadroDaImagemNoTexto(it, urlBase, Modifier.width(larguraDoQuadro)) { aoAmpliar(it) }
                    }
                }
                // I12: as paisagens dos parágrafos consumidos pelo retrato vêm por último, na largura inteira.
                if (urlBase != null) bloco.paisagensDepois.forEach { QuadroDaImagemNoTexto(it, urlBase, Modifier.fillMaxWidth()) { aoAmpliar(it) } }
            }
        }
    }
}

/** O espaço entre a coluna do texto e o retrato (I2). */
internal val ESPACO_AO_LADO_DO_QUADRO = 12.dp
