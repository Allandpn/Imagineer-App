package com.allan.imagineer.telas.capitulo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
