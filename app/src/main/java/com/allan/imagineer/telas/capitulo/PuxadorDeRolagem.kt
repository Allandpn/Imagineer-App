package com.allan.imagineer.telas.capitulo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// O puxador da rolagem (AJ2): um ícone na lateral direita que, arrastado, percorre o capítulo rápido.

/** Onde o puxador fica (0 = topo, 1 = fim) quando o item [indice] é o primeiro à vista, numa lista de [total] itens. */
fun fracaoDoPuxador(indice: Int, total: Int): Float =
    if (total <= 1) 0f else (indice.toFloat() / (total - 1)).coerceIn(0f, 1f)

/** O item para onde rolar quando o puxador está na [fracao] (0 a 1) do caminho, numa lista de [total] itens. */
fun indiceDoPuxador(fracao: Float, total: Int): Int =
    if (total <= 0) 0 else (fracao.coerceIn(0f, 1f) * (total - 1)).toInt().coerceIn(0, total - 1)

/** Quanto tempo o puxador fica à vista depois que a rolagem para. */
private const val TEMPO_DO_PUXADOR_MS = 1500L

/** Capítulos curtos (poucos itens) não precisam de puxador. */
private const val MINIMO_DE_ITENS_PARA_PUXADOR = 6

/**
 * O puxador: aparece **enquanto a pessoa rola** (e some 1,5 s depois de parar); arrastá-lo para cima ou para baixo leva o texto
 * junto, de forma proporcional. Desenhado sobre o texto, na borda direita, sem tirar espaço da leitura.
 */
@Composable
fun PuxadorDeRolagem(lista: LazyListState, modifier: Modifier = Modifier) {
    val escopo = rememberCoroutineScope()
    var arrastando by remember { mutableStateOf(false) }
    var visivel by remember { mutableStateOf(false) }
    val rolando = lista.isScrollInProgress
    LaunchedEffect(rolando, arrastando) {
        if (rolando || arrastando) {
            visivel = true
        } else {
            delay(TEMPO_DO_PUXADOR_MS)
            visivel = false
        }
    }
    val total = lista.layoutInfo.totalItemsCount
    if (total < MINIMO_DE_ITENS_PARA_PUXADOR) return

    val alturaDoPuxador = 56.dp
    val densidade = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val alturaDaAreaPx = with(densidade) { maxHeight.toPx() }
        val alturaDoPuxadorPx = with(densidade) { alturaDoPuxador.toPx() }
        val percursoPx = (alturaDaAreaPx - alturaDoPuxadorPx).coerceAtLeast(1f)
        AnimatedVisibility(
            visible = visivel,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            val fracao = fracaoDoPuxador(lista.firstVisibleItemIndex, total)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                modifier = Modifier
                    .offset { IntOffset(0, (fracao * percursoPx).toInt()) }
                    .width(32.dp)
                    .height(alturaDoPuxador)
                    .pointerInput(total, percursoPx) {
                        var y = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                arrastando = true
                                y = fracaoDoPuxador(lista.firstVisibleItemIndex, total) * percursoPx
                            },
                            onDragEnd = { arrastando = false },
                            onDragCancel = { arrastando = false },
                        ) { mudanca, delta ->
                            mudanca.consume()
                            y = (y + delta).coerceIn(0f, percursoPx)
                            escopo.launch { lista.scrollToItem(indiceDoPuxador(y / percursoPx, total)) }
                        }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.UnfoldMore, contentDescription = "Arraste para percorrer o capítulo", modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
