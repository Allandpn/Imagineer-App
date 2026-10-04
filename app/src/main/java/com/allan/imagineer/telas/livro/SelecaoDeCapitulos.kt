package com.allan.imagineer.telas.livro

import com.allan.imagineer.telas.comum.MarcaDeLido
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Archive
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.allan.imagineer.rede.CapituloResumo

// O desenho visual destas peças é provisório: esta rodada trata das REGRAS de negócio
// (item 7.5a, segunda revisão do incremento 6), e o visual é refinado depois.

/**
 * Uma linha de capítulo, usada pela lista principal **e** pela área de arquivados.
 *
 * - Fora do modo de seleção: tocar abre o capítulo; **tocar e segurar** entra no modo
 *   de seleção já com esta linha marcada.
 * - No modo de seleção: aparece a caixa, e tocar marca/desmarca (não abre nada).
 *
 * Quem usa a linha decide o que "tocar" e "segurar" fazem em cada modo — a linha só os
 * repassa. Enquanto o capítulo tem uma chamada em andamento, mostra progresso.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinhaDeCapitulo(
    capitulo: CapituloResumo,
    emSelecao: Boolean,
    marcado: Boolean,
    ajustando: Boolean,
    aoTocar: () -> Unit,
    aoSegurar: () -> Unit,
    /** `false` na lista do livro (minimalista): só o título. Os detalhes do capítulo vão para o botão de metadados dentro dele. */
    detalhes: Boolean = true,
    /** Tocar no ícone de lido marca e desmarca (LE5); nulo = o ícone é só um sinal. */
    aoAlternarLido: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emSelecao) {
            Checkbox(
                checked = marcado,
                onCheckedChange = { aoTocar() },
                enabled = !ajustando,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = aoTocar, onLongClick = aoSegurar)
                .padding(start = if (emSelecao) 8.dp else 16.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                tituloDoCapitulo(capitulo.titulo, capitulo.ordem),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (detalhes) {
                Text(
                    listOfNotNull(
                        descreverTamanho(capitulo.tamanho_do_texto),
                        descreverSugestoes(capitulo.sugestoes_pendentes),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // LE5: **todo** capítulo mostra se foi lido, de forma discreta: o lido com um círculo marcado, o não lido com um círculo vazio, bem
        // apagado. Assim se vê de relance o que já foi lido.
        if (!emSelecao) {
            val descricao = if (capitulo.lido) "Lido: tocar para marcar como não lido" else "Não lido: tocar para marcar como lido"
            if (aoAlternarLido != null) {
                IconButton(onClick = aoAlternarLido, modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = descricao }) {
                    MarcaDeLido(capitulo.lido)
                }
            } else {
                MarcaDeLido(capitulo.lido, Modifier.padding(end = 16.dp).semantics { contentDescription = if (capitulo.lido) "Lido" else "Não lido" }, 20.dp)
            }
        }

        if (ajustando) {
            Box(modifier = Modifier.padding(end = 16.dp).size(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/**
 * A barra superior do modo de seleção: cancelar, "N selecionados", "Selecionar todos" e o botão que
 * confirma ("Arquivar (N)" ou "Restaurar (N)"). O botão é a confirmação — sem diálogo
 * extra — e fica desabilitado com zero marcados ou com o lote no ar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraDeSelecao(
    selecao: Selecao,
    rotuloDaAcao: String,
    todosMarcados: Boolean,
    aoAlternarTodos: () -> Unit,
    aoCancelar: () -> Unit,
    aoConfirmar: () -> Unit,
) {
    TopAppBar(
        // Só o número, numa linha: "Nenhum selecionado" quebrava o texto.
        title = { Text(selecao.ids.size.toString(), maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = aoCancelar, enabled = !selecao.executando) {
                Icon(Icons.Filled.Close, contentDescription = "Cancelar seleção")
            }
        },
        actions = {
            if (selecao.executando) {
                Box(modifier = Modifier.padding(end = 16.dp).size(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                // Ícones: o que cada um faz fica na descrição (leitor de tela).
                IconButton(onClick = aoAlternarTodos) {
                    Icon(
                        if (todosMarcados) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                        contentDescription = if (todosMarcados) "Desmarcar todos" else "Selecionar todos",
                    )
                }
                IconButton(onClick = aoConfirmar, enabled = selecao.ids.isNotEmpty()) {
                    Icon(
                        if (rotuloDaAcao == "Arquivar") Icons.Filled.Archive else Icons.Filled.Unarchive,
                        contentDescription = "$rotuloDaAcao (${selecao.ids.size})",
                    )
                }
            }
        },
    )
}

/**
 * Todos os capítulos que dá para marcar agora já estão marcados? Decide o rótulo do botão:
 * "Desmarcar todos" se sim, "Selecionar todos" se não. Os capítulos com chamada em andamento
 * não contam — nunca podem ser marcados (R5).
 */
fun todosMarcados(estado: EstadoDoLivro.Pronto, selecao: Selecao): Boolean {
    val elegiveis = estado.livro.capitulos
        .filter { if (selecao.modo == ModoDeSelecao.ARQUIVAR) !it.ignorado else it.ignorado }
        .map { it.id }
        .filter { it !in estado.ajustando }
    return elegiveis.isNotEmpty() && selecao.ids.containsAll(elegiveis)
}

/** "Nenhum selecionado", "1 selecionado", "3 selecionados" — para a barra do modo de seleção. */
fun descreverSelecionados(quantidade: Int): String = when (quantidade) {
    0 -> "Nenhum selecionado"
    1 -> "1 selecionado"
    else -> "$quantidade selecionados"
}
