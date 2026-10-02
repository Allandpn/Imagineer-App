package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.allan.imagineer.rede.ReferenciasCandidatas
import com.allan.imagineer.rede.ElementoComImagens
import com.allan.imagineer.rede.ImagemCandidata
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.enderecoDaImagem

/**
 * O modal de **escolher as imagens de referência** da cena (W9): uma seção por elemento, com as miniaturas das imagens dele;
 * tocar marca e desmarca, **segurar** amplia. A âncora leva o selo "referência principal". O contador mostra o limite. Nada
 * é enviado ao servidor aqui: **Usar estas** só guarda a escolha deste frame (W10).
 */
@Composable
internal fun DialogoDeReferencias(escolha: EscolhaDeReferencias, acoes: AcoesDoPainel) {
    AlertDialog(
        onDismissRequest = acoes.aoFecharReferencias,
        title = { Text("Referências da cena") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "As imagens marcadas vão junto, para o modelo manter a aparência de cada personagem. A imagem pode custar mais " +
                        "com referências, e o resultado muda: o modelo pode copiar demais a pose da imagem.",
                    style = MaterialTheme.typography.bodySmall,
                )
                when (val candidatas = escolha.candidatas) {
                    CandidatasDasReferencias.Carregando -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    is CandidatasDasReferencias.Erro -> Text(candidatas.motivo, color = MaterialTheme.colorScheme.error)
                    is CandidatasDasReferencias.Prontas -> {
                        Text(
                            "${escolha.marcadas.size} de $MAXIMO_DE_REFERENCIAS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (candidatas.dados.elementos.isEmpty()) Text("Esta cena não tem elementos.", style = MaterialTheme.typography.bodyMedium)
                        candidatas.dados.elementos.forEach { SecaoDoElemento(it, escolha.marcadas, acoes) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = acoes.aoUsarReferencias, enabled = escolha.candidatas is CandidatasDasReferencias.Prontas) { Text("Usar estas") } },
        dismissButton = {
            Row {
                TextButton(onClick = acoes.aoLimparReferencias, enabled = escolha.marcadas.isNotEmpty()) { Text("Limpar") }
                TextButton(onClick = acoes.aoFecharReferencias) { Text("Cancelar") }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SecaoDoElemento(elemento: ElementoComImagens, marcadas: Set<Int>, acoes: AcoesDoPainel) {
    val urlBase = urlDoServidorEmUso()
    var ampliada by remember { mutableStateOf<ImagemCandidata?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${elemento.nome} (${elemento.tipo.lowercase()})", style = MaterialTheme.typography.titleSmall)
        if (elemento.imagens.isEmpty()) {
            Text("Ainda sem imagem.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (urlBase != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                elemento.imagens.forEach { imagem ->
                    val marcada = imagem.id in marcadas
                    Column(modifier = Modifier.size(width = 96.dp, height = if (imagem.ancora) 124.dp else 100.dp)) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                                .border(if (marcada) 3.dp else 1.dp, if (marcada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .combinedClickable(onClick = { acoes.aoAlternarReferencia(imagem.id) }, onLongClick = { ampliada = imagem })
                                .semantics { contentDescription = "Imagem de ${elemento.nome}, ${if (marcada) "marcada" else "não marcada"}. Segure para ampliar." },
                        ) {
                            AsyncImage(
                                model = enderecoDaImagem(urlBase, imagem.id, "miniatura"),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().size(96.dp),
                            )
                            if (marcada) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(Color.White, RoundedCornerShape(11.dp)),
                                )
                            }
                        }
                        if (imagem.ancora) Text("referência principal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
    ampliada?.let { imagem ->
        if (urlBase != null) {
            ImagemEmTelaCheia(
                imagem = ImagemDoPrompt(id = imagem.id, modelo = imagem.modelo, origem = imagem.origem),
                url = enderecoDaImagem(urlBase, imagem.id, "original"),
                aoExcluir = null,
                aoFechar = { ampliada = null },
                mostrarOrigem = false,
            )
        }
    }
}
