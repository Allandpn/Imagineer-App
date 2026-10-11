package com.allan.imagineer.telas.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.allan.imagineer.rede.AmostraDeVoz
import com.allan.imagineer.rede.decimalOuNulo
import com.allan.imagineer.rede.dolares
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

// O teste de voz da tela de Narração (item 7.5g, AU4): mostra a frase e o custo ANTES, e só então fala.

/** O custo que o servidor mandou no cabeçalho do MP3 (texto como "0.0016"), em palavras; sem ele, nada a dizer. */
fun descreverCustoDaAmostra(amostra: AmostraDeVoz): String? {
    val valor = decimalOuNulo(amostra.custoEstimado?.let { JsonPrimitive(it) }) ?: return null
    return if (valor <= 0.0) "Não custou nada." else "Custo estimado: ${dolares(valor)}."
}

/** O diálogo do teste de voz, conforme o passo (lendo, confirmar, gerando, tocando ou falha). */
@Composable
fun DialogoDoTesteDeVoz(teste: TesteDeVoz, aoOuvir: () -> Unit, aoFechar: () -> Unit) {
    if (teste is TesteDeVoz.Fechado) return
    // "Ouvir de novo" repete a amostra que já veio (não pede outra ao servidor, então não cobra de novo).
    var repeticao by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Testar a voz") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (teste) {
                    TesteDeVoz.Fechado -> Unit
                    TesteDeVoz.Lendo -> Carregando("Lendo o custo…")
                    is TesteDeVoz.Confirmar -> {
                        Text("“${teste.estimativa.frase}”", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Modelo: ${teste.estimativa.modelo}" + (teste.estimativa.voz?.let { " · voz: $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(descreverCustoDoTesteDeVoz(teste.estimativa), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "O teste não é guardado: serve só para ouvir a voz antes de escolher.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is TesteDeVoz.Gerando -> Carregando("Gerando a amostra…")
                    is TesteDeVoz.Tocando -> {
                        Text("Tocando a amostra…", style = MaterialTheme.typography.bodyLarge)
                        descreverCustoDaAmostra(teste.amostra)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    is TesteDeVoz.Falhou -> Text(teste.motivo, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when (teste) {
                is TesteDeVoz.Confirmar -> TextButton(onClick = aoOuvir) { Text("Ouvir") }
                is TesteDeVoz.Tocando -> TextButton(onClick = { repeticao++ }) { Text("Ouvir de novo") }
                else -> Unit
            }
        },
        dismissButton = { TextButton(onClick = aoFechar, enabled = teste !is TesteDeVoz.Gerando) { Text(if (teste is TesteDeVoz.Confirmar) "Cancelar" else "Fechar") } },
    )
    if (teste is TesteDeVoz.Tocando) TocadorDeAmostra(teste.amostra, repeticao)
}

@Composable
private fun Carregando(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Toca a amostra: grava o MP3 num arquivo do cache do app e o entrega a um `ExoPlayer`, que é liberado ao fechar o diálogo. "Ouvir de novo"
 * ([repeticao] maior) toca a mesma amostra outra vez desde o começo.
 */
@Composable
private fun TocadorDeAmostra(amostra: AmostraDeVoz, repeticao: Int) {
    val contexto = LocalContext.current
    val player = remember(amostra) {
        val arquivo = File(contexto.cacheDir, "teste-de-voz.mp3").also { it.writeBytes(amostra.mp3) }
        ExoPlayer.Builder(contexto).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(arquivo)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(repeticao) {
        if (repeticao > 0) {
            player.seekTo(0L)
            player.play()
        }
    }
}
