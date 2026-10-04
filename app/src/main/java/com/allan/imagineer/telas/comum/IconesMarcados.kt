package com.allan.imagineer.telas.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** O quanto do destaque preenche um ícone marcado: bem de leve (o contorno e o símbolo é que levam a cor). */
const val PREENCHIMENTO_DO_ICONE_MARCADO = 0.14f

/**
 * O sinal de **lido / não lido** (LE5): um círculo.
 * - **Lido (marcado):** o contorno e o ✓ na **cor de destaque** do app, com o preenchimento dessa cor bem de leve.
 * - **Não lido:** só o contorno, num cinza apagado, sem nada dentro.
 *
 * O texto para quem usa leitor de tela fica a cargo de quem chama (o botão que o envolve).
 */
@Composable
fun MarcaDeLido(lido: Boolean, modifier: Modifier = Modifier, tamanho: Dp = 22.dp) {
    val destaque = MaterialTheme.colorScheme.primary
    val contorno = if (lido) destaque else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    Box(
        modifier = modifier
            .size(tamanho)
            .clip(CircleShape)
            .background(if (lido) destaque.copy(alpha = PREENCHIMENTO_DO_ICONE_MARCADO) else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.5.dp, contorno, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (lido) Icon(Icons.Filled.Check, contentDescription = null, tint = destaque, modifier = Modifier.size(tamanho * 0.64f))
    }
}
