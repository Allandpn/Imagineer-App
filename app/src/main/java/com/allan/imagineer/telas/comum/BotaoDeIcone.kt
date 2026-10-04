package com.allan.imagineer.telas.comum

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Uma ação de texto que virou **ícone** (AJ4): o desenho diz o que faz e a [descricao] segue como texto para leitores de tela.
 * Para ação que apaga, passe [cor] com a cor de erro, como o texto vermelho que ela substitui.
 */
@Composable
fun BotaoDeIcone(
    icone: ImageVector,
    descricao: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cor: Color = Color.Unspecified,
) {
    IconButton(onClick = aoTocar, modifier = modifier, enabled = habilitado) {
        Icon(icone, contentDescription = descricao, tint = cor)
    }
}
