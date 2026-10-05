@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.extensaoDoTipo
import com.allan.imagineer.rede.enderecoDaImagem
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.comum.BotaoDeIcone
import kotlinx.coroutines.launch
import java.io.File

// O prompt de vídeo (item 4.8, VD9): a pessoa escolhe a imagem que será o primeiro quadro, a IA monta o prompt e ela o leva, com a imagem,
// ao app do Gemini (Veo). O Imagineer **não** gera o vídeo.

/** O que o diálogo explica antes de gerar (VD9): o custo e quem gera o vídeo. */
const val AVISO_DO_PROMPT_DE_VIDEO =
    "A IA monta o prompt do vídeo (uma chamada, uns centavos). O vídeo em si é gerado no app do Gemini, com esta imagem de primeiro quadro."

const val ROTULO_PROMPT_DE_VIDEO = "Prompt de vídeo"

/** Sem imagem, não há quadro inicial: o servidor faria o modo texto para vídeo, que é o de reserva (VD1) e o app não oferece. */
const val AVISO_SEM_IMAGEM_PARA_VIDEO = "Gere ou importe uma imagem da cena para criar o prompt de vídeo a partir dela."

/** A imagem que vem escolhida: a **canônica** (a que o capítulo mostra) e, sem ela, a mais recente. */
fun imagemDePartidaPadrao(imagens: List<ImagemDoPrompt>): Int? = imagens.firstOrNull { it.canonica }?.id ?: imagens.maxByOrNull { it.id }?.id

/** Os prompts de vídeo do frame, do mais novo para o mais antigo (a leitura do servidor vem do mais antigo). */
fun videosDoMaisNovoParaOMaisAntigo(lista: List<PromptDeFrame>): List<PromptDeFrame> = lista.sortedByDescending { it.id }

/**
 * A seção **Vídeo** de um frame: o botão **Prompt de vídeo** (abre o diálogo de escolher a imagem) e os prompts de vídeo já gerados,
 * cada um com **Copiar** e **Abrir no Gemini**.
 */
@Composable
internal fun SecaoDeVideo(frameId: Int, rotulo: String, imagens: List<ImagemDoPrompt>, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    LaunchedEffect(frameId) { acoes.aoCarregarVideos(frameId) }
    Text("Vídeo", style = MaterialTheme.typography.titleSmall)
    if (imagens.isEmpty()) {
        Text(AVISO_SEM_IMAGEM_PARA_VIDEO, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        OutlinedButton(onClick = { acoes.aoAbrirDialogoDeVideo(frameId, rotulo, imagens) }) { Text(ROTULO_PROMPT_DE_VIDEO, maxLines = 1, softWrap = false) }
    }
    when (val videos = estado.videos[frameId]) {
        null, VideosDoFrame.Lendo -> Unit
        is VideosDoFrame.Erro -> Text(videos.motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        is VideosDoFrame.Pronto -> videosDoMaisNovoParaOMaisAntigo(videos.lista).forEach { video -> CartaoDeVideo(video) }
    }
}

@Composable
private fun CartaoDeVideo(video: PromptDeFrame) {
    val contexto = LocalContext.current
    val aplicacao = contexto.applicationContext as ImagineerApp
    val escopo = rememberCoroutineScope()
    val area = LocalClipboardManager.current
    var abrindo by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ROTULO_PROMPT_DE_VIDEO, style = MaterialTheme.typography.titleSmall)
            video.imagem_partida_id?.let { MiniaturaDoQuadroInicial(it) }
            SelectionContainer { Text(video.texto, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BotaoDeIcone(Icons.Filled.ContentCopy, "Copiar o prompt de vídeo", {
                    area.setText(AnnotatedString(video.texto))
                    Toast.makeText(contexto, "Prompt de vídeo copiado.", Toast.LENGTH_SHORT).show()
                })
                OutlinedButton(
                    enabled = !abrindo,
                    onClick = {
                        abrindo = true
                        escopo.launch {
                            try {
                                // O prompt vai também para a área de transferência: se o Gemini não aproveitar o texto junto da imagem, é só colar.
                                area.setText(AnnotatedString(video.texto))
                                abrirNoGemini(contexto, aplicacao, video)
                            } finally {
                                abrindo = false
                            }
                        }
                    },
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Abrir no Gemini", maxLines = 1, softWrap = false)
                }
            }
            if (abrindo) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MiniaturaDoQuadroInicial(imagemId: Int) {
    val urlBase = urlDoServidorEmUso() ?: return
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AsyncImage(
            model = enderecoDaImagem(urlBase, imagemId, "miniatura"),
            contentDescription = "Primeiro quadro do vídeo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black),
        )
        Text("Primeiro quadro", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * **Abrir no Gemini** (VD9): baixa a imagem de partida para o cache e abre o seletor de apps com o **texto e a imagem juntos**
 * (`ACTION_SEND` com `EXTRA_TEXT` e `EXTRA_STREAM`). Se o Gemini aproveita os dois ou só um é coisa que só se vê no aparelho: por isso o
 * prompt também foi para a área de transferência. Sem imagem de partida, compartilha só o texto. Nada aqui fecha o app.
 */
private suspend fun abrirNoGemini(contexto: Context, aplicacao: ImagineerApp, video: PromptDeFrame) {
    val imagemId = video.imagem_partida_id
    try {
        var arquivo: File? = null
        var tipo = "image/png"
        if (imagemId != null) {
            val temporario = File(contexto.cacheDir, "imagens/imagem_${imagemId}.baixando")
            when (val resultado = aplicacao.repositorioDePrompts.baixarImagem(imagemId, temporario)) {
                is ResultadoDaChamada.Falha -> {
                    Toast.makeText(contexto, resultado.motivo, Toast.LENGTH_LONG).show()
                    return
                }
                is ResultadoDaChamada.Sucesso -> {
                    tipo = resultado.dado
                    arquivo = File(temporario.parentFile, "imagem_${imagemId}.${extensaoDoTipo(tipo)}")
                    arquivo.delete()
                    temporario.renameTo(arquivo)
                }
            }
        }
        val envio = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, video.texto)
            if (arquivo != null) {
                type = tipo
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", arquivo))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        contexto.startActivity(Intent.createChooser(envio, "Abrir no Gemini").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Toast.makeText(contexto, "Prompt copiado. Se o Gemini abrir só com a imagem, cole o prompt.", Toast.LENGTH_LONG).show()
    } catch (erro: kotlinx.coroutines.CancellationException) {
        throw erro
    } catch (erro: Exception) {
        Log.e(ETIQUETA_DO_LOG, "Não consegui abrir o prompt de vídeo no Gemini", erro)
        Toast.makeText(contexto, "Não consegui compartilhar (${erro.javaClass.simpleName}). O prompt está copiado.", Toast.LENGTH_LONG).show()
    }
}

/** O diálogo de gerar o prompt de vídeo (VD9): escolhe o primeiro quadro, comenta se quiser e gera. */
@Composable
internal fun DialogoDeVideo(alvo: DialogoDeVideo, acoes: AcoesDoPainel) {
    val urlBase = urlDoServidorEmUso()
    AlertDialog(
        onDismissRequest = { if (!alvo.gerando) acoes.aoFecharDialogoDeVideo() },
        title = { Text(ROTULO_PROMPT_DE_VIDEO) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(AVISO_DO_PROMPT_DE_VIDEO, style = MaterialTheme.typography.bodyMedium)
                Text("Primeiro quadro", style = MaterialTheme.typography.labelLarge)
                if (urlBase != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        alvo.imagens.sortedByDescending { it.id }.forEach { imagem ->
                            val marcada = imagem.id == alvo.escolhida
                            Column(modifier = Modifier.width(80.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                AsyncImage(
                                    model = enderecoDaImagem(urlBase, imagem.id, "miniatura"),
                                    contentDescription = if (marcada) "Imagem escolhida" else "Imagem",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                        .border(if (marcada) 3.dp else 0.dp, if (marcada) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                        .clickable(enabled = !alvo.gerando) { acoes.aoEscolherImagemDoVideo(imagem.id) },
                                )
                                if (imagem.canonica) Text("Canônica", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = alvo.comentario,
                    onValueChange = acoes.aoMudarComentarioDoVideo,
                    label = { Text("Comentário (opcional)") },
                    placeholder = { Text("Ex.: só um movimento lento de câmera") },
                    minLines = 2,
                    enabled = !alvo.gerando,
                    modifier = Modifier.fillMaxWidth(),
                )
                alvo.erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (alvo.gerando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = acoes.aoGerarVideo, enabled = !alvo.gerando && alvo.escolhida != null) { Text(if (alvo.gerando) "Gerando…" else "Gerar") } },
        dismissButton = { TextButton(onClick = acoes.aoFecharDialogoDeVideo, enabled = !alvo.gerando) { Text("Cancelar") } },
    )
}
