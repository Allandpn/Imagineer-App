package com.allan.imagineer.telas.comum

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Uma ação de texto que virou **ícone** (AJ4, PB1): o desenho diz o que faz e a [descricao] segue como texto para leitores de tela.
 * Segurar o dedo no ícone mostra a [descricao] numa dica (PB3), para ninguém ficar sem saber o que o desenho faz.
 * Sem [cor], o ícone usa a **cor de destaque** escolhida pela pessoa. Para ação que apaga, passe [cor] com a cor de erro, como o texto vermelho que ela substitui.
 * Na barra superior, passe `LocalContentColor.current`: ali os ícones ficam na cor neutra da barra (PB4).
 */
@Composable
fun BotaoDeIcone(
    icone: ImageVector,
    descricao: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cor: Color = MaterialTheme.colorScheme.primary,
) {
    ComDica(descricao, modifier) {
        IconButton(onClick = aoTocar, enabled = habilitado) {
            // Desabilitado, esmaece como o IconButton do Material faz por conta própria (38%).
            Icon(icone, contentDescription = descricao, tint = if (habilitado) cor else cor.copy(alpha = ALFA_DESABILITADO))
        }
    }
}

/** A opacidade do Material para o conteúdo de um botão desabilitado. */
private const val ALFA_DESABILITADO = 0.38f

/**
 * Um botão flutuante (PB5): **redondo e só com o ícone**, com a [descricao] para leitores de tela e na dica de segurar o dedo.
 * [pequeno] é para atalho (o "Continuar lendo" do livro, LY3); o tamanho normal, para a ação principal da tela.
 */
@Composable
fun BotaoFlutuante(
    icone: ImageVector,
    descricao: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    pequeno: Boolean = false,
) {
    ComDica(descricao, modifier) {
        if (pequeno) {
            SmallFloatingActionButton(onClick = aoTocar, shape = CircleShape) { Icon(icone, contentDescription = descricao) }
        } else {
            FloatingActionButton(onClick = aoTocar, shape = CircleShape) { Icon(icone, contentDescription = descricao) }
        }
    }
}

/** A dica do Material (PB3): segurar o dedo sobre [conteudo] mostra [texto] logo acima dele. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComDica(texto: String, modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(texto) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = conteudo,
    )
}
