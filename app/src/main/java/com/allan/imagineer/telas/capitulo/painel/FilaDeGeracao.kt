package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// As peças de tela da geração em fila (item 7.5e, GF2 a GF5). As regras estão em RegrasDaFila.kt; o laço, no PainelDeIaViewModel.

/**
 * Um cartão da lista no modo **Selecionar** (GF2): uma caixa à esquerda e, se o item não pode entrar, o motivo em letra pequena. Fora do
 * modo Selecionar (ou sem [item]) mostra só o [conteudo], como sempre.
 */
@Composable
internal fun LinhaSelecionavel(item: ItemDaFila?, fila: EstadoDaFila, acoes: AcoesDoPainel, conteudo: @Composable () -> Unit) {
    if (!fila.selecionando || item == null) {
        conteudo()
        return
    }
    Row(verticalAlignment = Alignment.Top) {
        Checkbox(
            checked = item.chave in fila.marcados,
            onCheckedChange = { acoes.aoAlternarMarcaDaFila(item.chave) },
            enabled = item.podeEntrar,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            conteudo()
            item.motivoDeNaoEntrar?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A barra de baixo do modo Selecionar (GF2, GF3): quantos marcados, os atalhos e os dois botões da fila. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BarraDeSelecaoDaFila(itens: List<ItemDaFila>, fila: EstadoDaFila, acoes: AcoesDoPainel) {
    val marcados = itens.count { it.chave in fila.marcados && it.podeEntrar }
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when (marcados) {
                    0 -> "Nenhum marcado"
                    1 -> "1 marcado"
                    else -> "$marcados marcados"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { acoes.aoPedirFila(AcaoDaFila.PROMPTS_E_IMAGENS, itens) }, enabled = marcados > 0) {
                    Text(AcaoDaFila.PROMPTS_E_IMAGENS.rotulo, maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = { acoes.aoPedirFila(AcaoDaFila.SO_PROMPTS, itens) }, enabled = marcados > 0) {
                    Text(AcaoDaFila.SO_PROMPTS.rotulo, maxLines = 1, softWrap = false)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { acoes.aoMarcarOsQueFaltamNaFila(itens) }) { Text("Marcar os que faltam", maxLines = 1, softWrap = false) }
                TextButton(onClick = acoes.aoLimparMarcasDaFila, enabled = fila.marcados.isNotEmpty()) { Text("Limpar") }
                TextButton(onClick = acoes.aoAlternarSelecaoDaFila) { Text("Concluir") }
            }
        }
    }
}

/** O diálogo de confirmação (GF3): quantos itens, o custo e o que será pulado; só então a fila começa. */
@Composable
internal fun DialogoDaFila(pedido: PedidoDaFila, acoes: AcoesDoPainel) {
    AlertDialog(
        onDismissRequest = acoes.aoCancelarPedidoDaFila,
        title = { Text(pedido.acao.rotulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pedido.vazio) {
                    Text("Nada a fazer: o que você marcou já está pronto.")
                } else {
                    Text(perguntaDaFila(pedido))
                    // Os nomes, na ordem em que serão feitos (até oito, para o diálogo não crescer sem fim).
                    val nomes = pedido.aFazer.take(8).joinToString("\n") { "• ${it.rotulo}" }
                    Text(nomes + if (pedido.aFazer.size > 8) "\n… e mais ${pedido.aFazer.size - 8}" else "", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { Button(onClick = acoes.aoConfirmarFila, enabled = !pedido.vazio) { Text("Gerar") } },
        dismissButton = { TextButton(onClick = acoes.aoCancelarPedidoDaFila) { Text("Cancelar") } },
    )
}

/** O cartão de progresso (GF4): "Gerando 3 de 8 — Auri", a etapa do item e o botão **Parar**. */
@Composable
internal fun AndamentoDaFilaNaTela(fila: EstadoDaFila, etapas: Map<String, EtapaDaImagem>, acoes: AcoesDoPainel) {
    val andamento = fila.andamento ?: return
    val total = andamento.pedido.aFazer.size
    val item = andamento.itemAtual
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(descreverProgressoDaFila(andamento.indice, total, item?.rotulo.orEmpty()), style = MaterialTheme.typography.titleSmall)
            // A barra conta os itens já terminados; o que está em curso mostra a etapa em palavras.
            LinearProgressIndicator(progress = { if (total == 0) 0f else andamento.indice / total.toFloat() }, modifier = Modifier.fillMaxWidth())
            item?.let { etapas[it.chave] }?.let { Text(descreverEtapa(it), style = MaterialTheme.typography.bodySmall) }
            if (andamento.parando) {
                Text("Parando depois deste item…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                OutlinedButton(onClick = acoes.aoPararFila) { Text("Parar") }
            }
        }
    }
}

/** O resumo do fim (GF5): os números, o motivo de ter parado, o que falhou e **Tentar de novo**. */
@Composable
internal fun ResumoDaFilaNaTela(resumo: ResumoDaFila, acoes: AcoesDoPainel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(descreverResumoDaFila(resumo), style = MaterialTheme.typography.titleSmall)
            if (resumo.parouAPedido) Text("Você parou a fila.", style = MaterialTheme.typography.bodySmall)
            resumo.motivoDaParada?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            resumo.falhas.forEach { (item, motivo) ->
                Text("${item.rotulo}: $motivo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (resumo.paraRepetir.isNotEmpty()) {
                    Button(onClick = acoes.aoTentarDeNovoNaFila) { Text("Tentar de novo (${resumo.paraRepetir.size})", maxLines = 1, softWrap = false) }
                }
                TextButton(onClick = acoes.aoFecharResumoDaFila) { Text("Fechar") }
            }
        }
    }
}
