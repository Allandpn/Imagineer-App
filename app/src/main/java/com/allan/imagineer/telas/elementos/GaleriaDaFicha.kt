package com.allan.imagineer.telas.elementos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.allan.imagineer.rede.CenaDoElemento
import com.allan.imagineer.rede.GaleriaDoElemento
import com.allan.imagineer.rede.ImagemDoElemento
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.enderecoDaImagem
import com.allan.imagineer.telas.capitulo.painel.ImagemEmTelaCheia
import com.allan.imagineer.telas.capitulo.painel.urlDoServidorEmUso

/** A imagem que está em tela cheia (FI5): o id, e, se a imagem é do retrato dele e ainda não é a principal, o que pode fazer (FI6). */
private data class Ampliada(val imagemId: Int, val acao: Pair<String, () -> Unit>?)

/**
 * A seção **Imagens** da ficha (FI5): a grade de miniaturas dos retratos do elemento, a **âncora** com um selo e o capítulo na
 * legenda. **Tocar** abre a tela cheia (I4), onde, se a imagem ainda não é a canônica, há **"Definir como canônica"** (CAN6).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecaoDeImagensDaFicha(
    carga: CargaDaGaleria,
    recado: String?,
    aoTentarDeNovo: () -> Unit,
    aoDefinirCanonica: (frameId: Int, imagemId: Int) -> Unit,
) {
    val urlBase = urlDoServidorEmUso()
    var ampliada by remember { mutableStateOf<Ampliada?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Imagens", style = MaterialTheme.typography.titleMedium)
        when (carga) {
            CargaDaGaleria.Carregando -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            is CargaDaGaleria.Erro -> ErroDaGaleria(carga.motivo, aoTentarDeNovo)
            is CargaDaGaleria.Pronta -> {
                val imagens = carga.galeria.imagens
                if (imagens.isEmpty()) {
                    Text(
                        "Ainda sem imagens. Elas aparecem aqui quando um retrato deste elemento é gerado ou importado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (urlBase != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        imagens.forEach { imagem -> MiniaturaDaFicha(imagem, urlBase) {
                            ampliada = Ampliada(
                                imagem.id,
                                if (podeSerCanonica(imagem)) "Definir como canônica" to { aoDefinirCanonica(imagem.frame_id, imagem.id) } else null,
                            )
                        } }
                    }
                }
            }
        }
        recado?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    ampliada?.let { aberta ->
        if (urlBase != null) {
            ImagemEmTelaCheia(
                imagem = ImagemDoPrompt(id = aberta.imagemId),
                url = enderecoDaImagem(urlBase, aberta.imagemId, "original"),
                aoExcluir = null,
                aoFechar = { ampliada = null },
                mostrarOrigem = false,
                acaoExtra = aberta.acao?.let { (rotulo, fazer) -> rotulo to { fazer(); ampliada = null } },
            )
        }
    }
}

@Composable
private fun MiniaturaDaFicha(imagem: ImagemDoElemento, urlBase: String, aoTocar: () -> Unit) {
    val legenda = legendaDaImagemDoElemento(imagem)
    Column(modifier = Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AsyncImage(
            model = enderecoDaImagem(urlBase, imagem.id, "miniatura"),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(if (imagem.canonica) 3.dp else 0.dp, if (imagem.canonica) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "Ampliar a imagem", onClick = aoTocar)
                .semantics { contentDescription = "Imagem. $legenda. Toque para ampliar." },
        )
        Text(legenda, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/**
 * A seção **Cenas em que aparece** (FI5): um cartão por cena com o título, o capítulo, os outros participantes e a miniatura da imagem
 * mais recente (ou "ainda sem imagem"). Tocar na miniatura a amplia.
 */
@Composable
fun SecaoDeCenasDaFicha(carga: CargaDaGaleria, aoTentarDeNovo: () -> Unit) {
    val urlBase = urlDoServidorEmUso()
    var ampliada by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cenas em que aparece", style = MaterialTheme.typography.titleMedium)
        when (carga) {
            CargaDaGaleria.Carregando -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            is CargaDaGaleria.Erro -> ErroDaGaleria(carga.motivo, aoTentarDeNovo)
            is CargaDaGaleria.Pronta -> {
                if (carga.galeria.cenas.isEmpty()) {
                    Text("Ainda não aparece em nenhuma cena.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                carga.galeria.cenas.forEach { cena -> CartaoDaCena(cena, urlBase) { ampliada = cena.imagem_id } }
            }
        }
    }
    ampliada?.let { imagemId ->
        if (urlBase != null) {
            ImagemEmTelaCheia(
                imagem = ImagemDoPrompt(id = imagemId),
                url = enderecoDaImagem(urlBase, imagemId, "original"),
                aoExcluir = null,
                aoFechar = { ampliada = null },
                mostrarOrigem = false,
            )
        }
    }
}

@Composable
private fun CartaoDaCena(cena: CenaDoElemento, urlBase: String?, aoAmpliar: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val imagemId = cena.imagem_id
            if (imagemId != null && urlBase != null) {
                // A paisagem (16:9) é mais larga que o retrato (2:3), pela imagem real (I1).
                val retrato = cena.imagem_orientacao == "RETRATO"
                AsyncImage(
                    model = enderecoDaImagem(urlBase, imagemId, "miniatura"),
                    contentDescription = "Imagem da cena ${cena.titulo}. Toque para ampliar.",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(if (retrato) 64.dp else 112.dp)
                        .aspectRatio(if (retrato) 2f / 3f else 16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .clickable(onClickLabel = "Ampliar a imagem", onClick = aoAmpliar),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cena.titulo, style = MaterialTheme.typography.titleSmall)
                Text(resumoDaCena(cena), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                linhaDeParticipantes(cena)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ErroDaGaleria(motivo: String, aoTentarDeNovo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Button(onClick = aoTentarDeNovo) { Text("Tentar de novo") }
    }
}
