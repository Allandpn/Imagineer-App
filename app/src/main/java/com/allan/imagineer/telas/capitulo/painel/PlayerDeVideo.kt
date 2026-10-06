package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.allan.imagineer.rede.enderecoDoVideo

// O player do vídeo importado (item 4.8, VD19): uma janela em tela cheia por cima do capítulo, como a da imagem.

/** O que o player diz quando o vídeo não toca: o mais provável é o servidor fora do ar ou sem conexão (o vídeo não vai ao ler offline, VD19). */
const val AVISO_DE_VIDEO_QUE_NAO_TOCOU = "Não consegui tocar o vídeo. Confira a conexão com o servidor."

/**
 * Toca o vídeo [videoId] **em fluxo** (o servidor aceita pedaços, `Range`), com a barra padrão do Media3: tocar e pausar, avançar e voltar
 * arrastando, tempo e velocidade. Começa tocando. **Para ao sair da tela** (o som não continua com o app em segundo plano) e libera o
 * player ao fechar.
 */
@Composable
fun PlayerDeVideo(videoId: Int, aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    val urlBase = urlDoServidorEmUso()
    var erro by remember(videoId) { mutableStateOf<String?>(if (urlBase == null) AVISO_DE_VIDEO_QUE_NAO_TOCOU else null) }

    val player = remember(videoId) {
        ExoPlayer.Builder(contexto).build().apply {
            if (urlBase != null) {
                setMediaItem(MediaItem.fromUri(enderecoDoVideo(urlBase, videoId)))
                prepare()
                playWhenReady = true
            }
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    erro = AVISO_DE_VIDEO_QUE_NAO_TOCOU
                }
            })
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { contextoDaView ->
                    PlayerView(contextoDaView).apply {
                        this.player = player
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            erro?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
            IconButton(onClick = aoFechar, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar o vídeo", tint = Color.White)
            }
        }
    }
}
