package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.allan.imagineer.telas.capitulo.LegendaDosSelos

// As peças que se repetem no painel de IA (item 7.5f). Um padrão só para o que abre e fecha (PX1), o ⋮ dos cartões (PC2) e a legenda (PT2).

/** Um item de um [MenuMais]: o texto, se é perigoso (vermelho, separado dos outros), se está habilitado e o que faz. */
class ItemDeMenu(val rotulo: String, val perigoso: Boolean = false, val habilitado: Boolean = true, val aoTocar: () -> Unit)

/**
 * O ⋮ de um cartão ou do topo do painel (PC2, PT3). Os itens perigosos ficam no fim, depois de um divisor, e em vermelho. Sem itens, não
 * desenha nada. [descricao] é o que o leitor de tela diz do botão.
 */
@Composable
fun MenuMais(itens: List<ItemDeMenu>, modifier: Modifier = Modifier, descricao: String = "Mais ações", habilitado: Boolean = true) {
    if (itens.isEmpty()) return
    var aberto by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(modifier) {
        IconButton(onClick = { aberto = true }, enabled = habilitado) { Icon(Icons.Filled.MoreVert, contentDescription = descricao) }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            val (comuns, perigosos) = itens.partition { !it.perigoso }
            comuns.forEach { item -> DropdownMenuItem(text = { Text(item.rotulo) }, enabled = item.habilitado, onClick = { aberto = false; item.aoTocar() }) }
            if (comuns.isNotEmpty() && perigosos.isNotEmpty()) HorizontalDivider()
            perigosos.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.rotulo, color = MaterialTheme.colorScheme.error) },
                    enabled = item.habilitado,
                    onClick = { aberto = false; item.aoTocar() },
                )
            }
        }
    }
}

/**
 * A linha de uma **seção que abre e fecha** (PX1): o título e a seta, tocáveis na linha inteira, para parecer o que é (e não um rótulo solto).
 * [aberta] escolhe a seta.
 */
@Composable
fun LinhaDeSecao(titulo: String, aberta: Boolean, aoAlternar: () -> Unit, modifier: Modifier = Modifier, icone: ImageVector? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (aberta) "Recolher" else "Abrir", role = Role.Button, onClick = aoAlternar)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // PD5: o ícone de imagem ou de vídeo à esquerda do título, para distinguir as duas seções de prompts.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icone?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(titulo, style = MaterialTheme.typography.titleSmall)
        }
        Icon(
            if (aberta) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** O título pequeno de uma seção que não recolhe (PM1): "Imagens". */
@Composable
fun TituloDeSecao(titulo: String, modifier: Modifier = Modifier, icone: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.padding(top = 4.dp)) {
        icone?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(titulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { contentDescription = titulo })
    }
}

/** O botão de **escolher algo** (PX1): contornado, com o lápis, para não parecer um texto solto. */
@Composable
fun BotaoDeEscolha(rotulo: String, aoTocar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    OutlinedButton(onClick = aoTocar, modifier = modifier, enabled = habilitado) {
        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
        Text(rotulo, maxLines = 1, softWrap = false)
    }
}

/**
 * Um botão de **só ícone** com dica (LP2 a LP5): **segurar o dedo** mostra o texto [dica], que também é o que o leitor de tela fala. É o que
 * faz o ícone poder substituir um botão de texto sem a pessoa ter de adivinhar o que ele faz. [cheio] marca a ação principal (preenchido); o
 * resto é contornado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotaoDeIconeComDica(
    icone: ImageVector,
    dica: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cheio: Boolean = false,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(dica) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        if (cheio) {
            FilledIconButton(onClick = aoTocar, enabled = habilitado) { Icon(icone, contentDescription = dica) }
        } else {
            OutlinedIconButton(onClick = aoTocar, enabled = habilitado) { Icon(icone, contentDescription = dica) }
        }
    }
}

/** A legenda das cores dos selos num diálogo curto (PT2), aberto pelo **?** do cabeçalho. */
@Composable
fun DialogoDaLegenda(aoFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("O que as cores querem dizer") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "O selo de cada elemento e cena no texto muda de cor conforme onde ele parou:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LegendaDosSelos()
                Text(
                    "Cinza: ainda é só uma sugestão da IA. Azul: você confirmou. Âmbar: tem prompt, falta a imagem. Verde-azulado: tem imagem.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = aoFechar) { Text("Entendi") } },
    )
}
