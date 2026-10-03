package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.allan.imagineer.rede.ElementoParaVincular
import com.allan.imagineer.rede.ImagemCandidata
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.enderecoDaImagem

/**
 * A linha **"Elementos e imagens: ..."** (EV1, EV8) junto do botão de gerar, de uma cena ou do retrato de um elemento que não é
 * personagem, com **Escolher**. Diz quantos elementos estão vinculados (no retrato, pelos nomes) e quantas imagens vão como
 * referência; se as imagens estão guardadas mas o modelo em uso não as usa, avisa (W10). Avisa também quando a escolha mudou e o
 * prompt é o antigo (V7, EV10).
 */
@Composable
internal fun LinhaDoSeletorDeElementos(
    frameId: Int?,
    ehCena: Boolean,
    estado: EstadoDoPainel,
    acoes: AcoesDoPainel,
    aoEscolher: () -> Unit,
    ocupado: Boolean = false,
) {
    // O retrato lê, uma vez, os vinculados que já tem no servidor (EV10).
    if (frameId != null && !ehCena) LaunchedEffect(frameId) { acoes.aoCarregarVinculados(frameId) }
    // RS1: as imagens de referência guardadas no servidor (cena e retrato), lidas uma vez.
    if (frameId != null) LaunchedEffect(frameId) { acoes.aoCarregarReferenciasGuardadas(frameId) }
    val modelos = estado.modelosDeImagem
    val aceita = modeloAceitaReferencia(modeloEmUso(estado.modeloEscolhido, modelos), modelos)
    val imagens = frameId?.let { estado.referenciasEscolhidas[it] }.orEmpty().size
    val nomes = if (ehCena) emptyList() else frameId?.let { estado.vinculadosPorFrame[it] }.orEmpty()
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            descreverSelecao(ehCena, nomes, imagens, aceita),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = aoEscolher, enabled = !ocupado) { Text("Escolher", maxLines = 1, softWrap = false) }
    }
    // EV14: as miniaturas das imagens que vão como referência (esmaecidas se o modelo em uso não as usa).
    val ids = frameId?.let { estado.referenciasEscolhidas[it] }.orEmpty()
    if (ids.isNotEmpty()) MiniaturasDeReferencia(ids, legenda = "Vão como referência na próxima geração")
    val aoMudar = frameId?.let { estado.mudancasPendentesDePrompt[it] }
    val promptsAgora = frameId?.let { (estado.prompts[it] as? PromptsDoFrame.Pronto)?.lista?.size }
    if (aoMudar != null && aoMudar == promptsAgora) {
        Text(AVISO_ELEMENTOS_MUDARAM, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

/**
 * As **miniaturas das imagens de referência** (EV14): uma fileira pequena, com a [legenda] em cima, para o usuário ver quais imagens
 * vão (ou foram) junto da geração, sem abrir o seletor. **No tom original** (sem esmaecer nem fundo preto: o Allan achou escuro);
     * quando o modelo em uso não as usa, quem avisa é a linha de cima (W10). Só mostra; não toca.
 */
@Composable
internal fun MiniaturasDeReferencia(ids: List<Int>, legenda: String, corDaLegenda: Color = Color.Unspecified) {
    val urlBase = urlDoServidorEmUso() ?: return
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(legenda, style = MaterialTheme.typography.labelSmall, color = if (corDaLegenda != Color.Unspecified) corDaLegenda else MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ids.forEach { id ->
                AsyncImage(
                    model = enderecoDaImagem(urlBase, id, "miniatura"),
                    contentDescription = "Imagem de referência",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}

/**
 * O **seletor de elementos e imagens** (EV1 a EV5): duas seções, **"Identificados neste capítulo"** e **"Outros elementos deste
 * capítulo"**; cada elemento é uma faixa com o **nome em cima** (e uma caixa de marcar) e, **embaixo, o carrossel das imagens
 * dele**. **Tocar** numa imagem a marca, com o quadradinho no canto superior direito, e **marca também o elemento**; **segurar**
 * a amplia. Nada vai ao servidor aqui: **Usar** decide (EV7).
 */
@Composable
internal fun DialogoDoSeletorDeElementos(escolha: EscolhaDeElementos, acoes: AcoesDoPainel) {
    AlertDialog(
        onDismissRequest = acoes.aoFecharSeletor,
        title = { Text(if (escolha.ehCena) "Elementos e imagens da cena" else "Elementos e imagens do retrato") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Marcar uma imagem coloca o elemento " + (if (escolha.ehCena) "na cena" else "no retrato") +
                        " e manda a imagem junto, para o modelo manter a aparência. A imagem pode custar mais, e o resultado muda: " +
                        "o modelo pode copiar demais a pose. Personagens ficam individuais nos retratos.",
                    style = MaterialTheme.typography.bodySmall,
                )
                when (val candidatos = escolha.candidatos) {
                    CandidatosDoSeletor.Carregando -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    is CandidatosDoSeletor.Erro -> Text(candidatos.motivo, color = MaterialTheme.colorScheme.error)
                    is CandidatosDoSeletor.Prontos -> {
                        Text(
                            "Imagens: ${escolha.selecao.imagens.size} de $MAXIMO_DE_REFERENCIAS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (todosOsElementos(candidatos.dados).isEmpty()) {
                            Text(
                                if (escolha.ehCena) "Não há elementos para escolher neste capítulo." else "Não há outros elementos neste capítulo.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        SecaoDoSeletor("Identificados neste capítulo", candidatos.dados.identificados, escolha, acoes)
                        SecaoDoSeletor("Outros elementos deste capítulo", candidatos.dados.outros, escolha, acoes)
                        // VM7: os do resto do livro; marcar um usa o estado dele até este capítulo (ou o primeiro, se só aparece depois).
                        SecaoDoSeletor("De outros capítulos", candidatos.dados.de_outros_capitulos, escolha, acoes)
                    }
                }
                escolha.erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(onClick = acoes.aoUsarSeletor, enabled = escolha.candidatos is CandidatosDoSeletor.Prontos && !escolha.salvando) {
                Text(if (escolha.salvando) "Gravando…" else "Usar estes")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = acoes.aoLimparSeletor, enabled = !escolha.salvando) { Text("Limpar") }
                TextButton(onClick = acoes.aoFecharSeletor, enabled = !escolha.salvando) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun SecaoDoSeletor(titulo: String, elementos: List<ElementoParaVincular>, escolha: EscolhaDeElementos, acoes: AcoesDoPainel) {
    if (elementos.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        elementos.forEach { FaixaDoElemento(it, escolha, acoes) }
    }
}

/** Uma faixa: a caixa de marcar e o nome em cima, o carrossel de imagens embaixo (EV3). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FaixaDoElemento(elemento: ElementoParaVincular, escolha: EscolhaDeElementos, acoes: AcoesDoPainel) {
    val urlBase = urlDoServidorEmUso()
    var ampliada by remember { mutableStateOf<ImagemCandidata?>(null) }
    val marcado = elemento.estado_id in escolha.selecao.elementos
    val fixo = elementoFixo(elemento)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !fixo) { acoes.aoAlternarElementoDoSeletor(elemento.estado_id) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = marcado, onCheckedChange = null, enabled = !fixo)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text("${elemento.nome} (${elemento.tipo.lowercase()})", style = MaterialTheme.typography.titleSmall)
                if (fixo) Text("na cena", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        if (elemento.imagens.isEmpty()) {
            Text("Ainda sem imagem.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (urlBase != null) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp)) {
                items(elemento.imagens, key = { it.id }) { imagem ->
                    val daSelecao = imagem.id in escolha.selecao.imagens
                    Column(modifier = Modifier.size(width = 96.dp, height = if (imagem.ancora) 124.dp else 100.dp)) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                                .border(
                                    if (daSelecao) 3.dp else 1.dp,
                                    if (daSelecao) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(8.dp),
                                )
                                .combinedClickable(
                                    onClick = { acoes.aoAlternarImagemDoSeletor(imagem.id) },
                                    onLongClick = { ampliada = imagem },
                                )
                                .semantics { contentDescription = "Imagem de ${elemento.nome}, ${if (daSelecao) "marcada" else "não marcada"}. Segure para ampliar." },
                        ) {
                            AsyncImage(
                                model = enderecoDaImagem(urlBase, imagem.id, "miniatura"),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(96.dp),
                            )
                            // O quadradinho de seleção, no canto superior direito (EV3).
                            Icon(
                                imageVector = if (daSelecao) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (daSelecao) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp)),
                            )
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
