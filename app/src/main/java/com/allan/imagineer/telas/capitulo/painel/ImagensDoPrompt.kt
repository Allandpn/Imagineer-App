package com.allan.imagineer.telas.capitulo.painel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTransformGestures
import coil3.compose.AsyncImage
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.enderecoDaImagem

/** O endereço do servidor configurado, para montar as URLs das imagens (J7). `null` enquanto não se sabe. */
@Composable
private fun urlDoServidorEmUso(): String? {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val url by aplicacao.armazenamento.urlDoServidor.collectAsState(initial = null)
    return url
}

/**
 * **Importar imagem** (J1 e J2): abre o seletor de arquivos do Android, só de imagens, e entrega o escolhido ao
 * ViewModel, que confere e envia. Fica desabilitado enquanto este prompt já tem um envio em andamento (J3).
 */
@Composable
internal fun BotaoImportarImagem(frameId: Int, promptId: Int, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        // Cancelar o seletor devolve null: não é erro, é só desistir.
        if (uri != null) acoes.aoImportarImagem(frameId, promptId, aplicacao.leitorDeArquivos.descrever(uri.toString()))
    }
    OutlinedButton(onClick = { seletor.launch("image/*") }, enabled = promptId !in estado.importandoImagem) {
        Text("Importar imagem", maxLines = 1, softWrap = false)
    }
}

/**
 * O que a importação mostra dentro do cartão do prompt (J3, J4, J8): a barra de envio, o recado e as **miniaturas** das
 * imagens, da mais nova para a mais antiga. Tocar numa miniatura abre a imagem em tela cheia.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImagensDoPrompt(prompt: PromptDeFrame, estado: EstadoDoPainel) {
    val urlBase = urlDoServidorEmUso()
    var aberta by remember(prompt.id) { mutableStateOf<ImagemDoPrompt?>(null) }

    if (prompt.id in estado.importandoImagem) {
        val fracao = estado.importandoImagem[prompt.id]
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (fracao == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fracao }, modifier = Modifier.fillMaxWidth())
            }
            Text("Enviando a imagem…", style = MaterialTheme.typography.bodySmall)
        }
    }
    estado.mensagensDeImagem[prompt.id]?.let { mensagem ->
        Text(
            mensagem.texto,
            style = MaterialTheme.typography.bodySmall,
            color = if (mensagem.ehErro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        )
    }
    if (urlBase != null && prompt.imagens.isNotEmpty()) {
        // Do mais novo para o mais antigo (J4). O servidor entrega por ordem de importação.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            prompt.imagens.sortedByDescending { it.id }.forEach { imagem ->
                AsyncImage(
                    model = enderecoDaImagem(urlBase, imagem.id, "miniatura"),
                    contentDescription = "Imagem importada",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .clickable { aberta = imagem },
                )
            }
        }
    }
    aberta?.let { imagem ->
        if (urlBase != null) ImagemEmTelaCheia(enderecoDaImagem(urlBase, imagem.id, "original")) { aberta = null }
    }
}

/**
 * A imagem em **tela cheia**, no tamanho normal (J4), sobre fundo preto, com **zoom por pinça** e arrastar. O botão de
 * fechar e o botão de voltar do Android fecham.
 */
@Composable
private fun ImagemEmTelaCheia(url: String, aoFechar: () -> Unit) {
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = url,
                contentDescription = "Imagem em tela cheia",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, arraste, zoom, _ ->
                            escala = (escala * zoom).coerceIn(1f, 6f)
                            // Sem zoom não há para onde arrastar: a imagem volta ao centro.
                            deslocamento = if (escala == 1f) Offset.Zero else deslocamento + arraste
                        }
                    }
                    .graphicsLayer(
                        scaleX = escala,
                        scaleY = escala,
                        translationX = deslocamento.x,
                        translationY = deslocamento.y,
                    ),
            )
            IconButton(onClick = aoFechar, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
            }
        }
    }
}
