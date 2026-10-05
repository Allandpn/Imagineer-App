package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.CompositionLocalProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.allan.imagineer.telas.capitulo.BlocoDoTexto
import com.allan.imagineer.telas.capitulo.ParagrafoDoTexto

// A seleção por parágrafo (LV4): o item "Marcar parágrafo" do menu da seleção de texto liga o modo; no modo, o toque simples soma ou tira,
// e uma barra de ícones aparece embaixo. (Antes era o toque longo, que disputava com a seleção de texto e a fazia sumir.)

/** Os ícones da barra de parágrafos marcados, num lugar só (o Allan quer poder trocá-los). */
object IconesDaSelecao {
    val copiar: ImageVector = Icons.Filled.ContentCopy
    val gerarPrompt: ImageVector = Icons.Filled.AutoAwesome
    val voltarAoNormal: ImageVector = Icons.Filled.Deselect
}

/** Os parágrafos que um bloco do texto cobre (um comum; vários quando o retrato ao lado consome os seguintes). */
fun indicesDoBloco(bloco: BlocoDoTexto): List<Int> = when (bloco) {
    is BlocoDoTexto.Comum -> listOf(bloco.fatia.indice)
    is BlocoDoTexto.ComRetrato -> (bloco.fatias.map { it.indice } + listOfNotNull(bloco.resto?.indice)).distinct()
}

/** Um bloco está marcado quando **todos** os parágrafos dele estão. */
fun blocoMarcado(marcados: Set<Int>, indices: List<Int>): Boolean = indices.isNotEmpty() && marcados.containsAll(indices)

/** Toque num bloco: se está marcado, tira os parágrafos dele; senão, soma. */
fun alternarBloco(marcados: Set<Int>, indices: List<Int>): Set<Int> =
    if (blocoMarcado(marcados, indices)) marcados - indices.toSet() else marcados + indices

/** O trecho da cena: os parágrafos marcados, na ordem do texto, separados por uma linha em branco. */
fun trechoDosParagrafos(paragrafos: List<ParagrafoDoTexto>, marcados: Set<Int>): String =
    marcados.sorted().mapNotNull { paragrafos.getOrNull(it)?.texto }.joinToString("\n\n")

/** A posição da cena: o início do **primeiro** parágrafo marcado (como em TR4); `null` sem marcados. */
fun posicaoDosParagrafos(paragrafos: List<ParagrafoDoTexto>, marcados: Set<Int>): Int? =
    marcados.minOrNull()?.let { paragrafos.getOrNull(it)?.inicio }

/** Quanto tempo de toque vale como "segurar" o parágrafo (menor que o da seleção de texto do sistema, que é 400 ms). */

/** O quanto os parágrafos **não** marcados escurecem enquanto há marcados. */
const val OPACIDADE_DO_PARAGRAFO_NAO_MARCADO = 0.35f

/**
 * Envolve um bloco do texto com a marca de seleção: **no modo** (ligado pelo menu da seleção de texto), o toque simples marca ou
 * desmarca. Os não marcados escurecem. No modo a seleção de texto nativa fica desligada, para o toque não disputar com ela.
 * **Fora do modo o bloco não reage a toque nem a toque longo**: o toque longo é da seleção de texto (destacar, dicionário, copiar).
 */
@Composable
internal fun ComMarcaDeParagrafo(
    marcado: Boolean,
    emModo: Boolean,
    aoTocar: () -> Unit,
    /** O parágrafo achado pela pesquisa (LV5): fica com um fundo de destaque por alguns segundos. */
    destacado: Boolean = false,
    conteudo: @Composable () -> Unit,
) {
    val fundo = when {
        marcado -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        destacado -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val modificador = Modifier
        .alpha(if (emModo && !marcado) OPACIDADE_DO_PARAGRAFO_NAO_MARCADO else 1f)
        .background(fundo, RoundedCornerShape(8.dp))
        // Só no modo o bloco é tocável: fora dele, um clicável aqui disputaria o toque longo com a seleção de texto.
        .then(
            if (emModo) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = aoTocar)
            else Modifier,
        )
    androidx.compose.foundation.layout.Box(modifier = modificador) {
        if (emModo) DisableSelection { conteudo() } else conteudo()
    }
}

/** A barra horizontal de ícones, sem rótulo, que aparece embaixo enquanto há parágrafos marcados. */
@Composable
internal fun BarraDeParagrafos(
    aoCopiar: () -> Unit,
    aoGerarPrompt: () -> Unit,
    aoVoltarAoNormal: () -> Unit,
    modifier: Modifier = Modifier,
    /** RL36: os parágrafos marcados, para o coração de favoritar (vazio = sem coração). */
    paraFavoritar: List<com.allan.imagineer.rede.AlvoDeFavorito> = emptyList(),
) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            IconButton(onClick = aoCopiar) { Icon(IconesDaSelecao.copiar, contentDescription = "Copiar texto") }
            IconButton(onClick = aoGerarPrompt) { Icon(IconesDaSelecao.gerarPrompt, contentDescription = ROTULO_GERAR_IMAGEM_DO_TRECHO) }
            com.allan.imagineer.telas.favoritos.BotaoDeFavoritoDosAlvos(paraFavoritar, cor = MaterialTheme.colorScheme.inverseOnSurface)
            IconButton(onClick = aoVoltarAoNormal) { Icon(IconesDaSelecao.voltarAoNormal, contentDescription = "Voltar ao normal") }
        }
    }
}

/** Põe o [texto] na área de transferência e avisa. */
internal fun copiarParaAAreaDeTransferencia(contexto: Context, texto: String) {
    val area = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    area.setPrimaryClip(ClipData.newPlainText("trecho", texto))
    Toast.makeText(contexto, "Texto copiado.", Toast.LENGTH_SHORT).show()
}
