@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.allan.imagineer.telas.capitulo.painel

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.allan.imagineer.rede.VideoImportado
import com.allan.imagineer.rede.enderecoDaImagem
import com.allan.imagineer.rede.extensaoDoTipo
import com.allan.imagineer.rede.tamanhoDoVideoParaLer
import com.allan.imagineer.telas.comum.BotaoDeIcone
import kotlinx.coroutines.launch
import java.io.File

// O vídeo de um frame (item 4.8): a pessoa escolhe a imagem que será o primeiro quadro, a IA monta o prompt e ela o leva, com a imagem,
// a um gerador de vídeo (o Gemini, por exemplo). O Imagineer **não** gera o vídeo: o vídeo pronto volta por **Importar vídeo** (VD18) e
// pode ser o que o texto mostra no lugar da imagem (VD17), tocado por um player (VD19).

/** O que o diálogo explica antes de gerar (VD9): o custo e quem gera o vídeo. */
const val AVISO_DO_PROMPT_DE_VIDEO =
    "A IA monta o prompt do vídeo (uma chamada, uns centavos). O vídeo em si é gerado fora, com esta imagem de primeiro quadro; depois, importe-o aqui."

const val ROTULO_PROMPT_DE_VIDEO = "Prompt de vídeo"

const val ROTULO_IMPORTAR_VIDEO = "Importar vídeo"

/** Sem imagem, não há quadro inicial: o servidor faria o modo texto para vídeo, que é o de reserva (VD1) e o app não oferece. */
const val AVISO_SEM_IMAGEM_PARA_VIDEO = "Gere ou importe uma imagem da cena para criar o prompt de vídeo a partir dela."

/** A imagem que vem escolhida: a **canônica** (a que o capítulo mostra) e, sem ela, a mais recente. */
fun imagemDePartidaPadrao(imagens: List<ImagemDoPrompt>): Int? = imagens.firstOrNull { it.canonica }?.id ?: imagens.maxByOrNull { it.id }?.id

/** Os prompts de vídeo do frame, do mais novo para o mais antigo (a leitura do servidor vem do mais antigo). */
fun videosDoMaisNovoParaOMaisAntigo(lista: List<PromptDeFrame>): List<PromptDeFrame> = lista.sortedByDescending { it.id }

/** Os prompts de vídeo que a lista mostra: os **não ocultos** (VD12), do mais novo ao mais antigo. */
fun promptsDeVideoVisiveis(lista: List<PromptDeFrame>): List<PromptDeFrame> = videosDoMaisNovoParaOMaisAntigo(lista).filter { !it.oculto }

/** Os que a pessoa escondeu (VD12): não foram apagados e voltam por **Mostrar**. */
fun promptsDeVideoOcultos(lista: List<PromptDeFrame>): List<PromptDeFrame> = videosDoMaisNovoParaOMaisAntigo(lista).filter { it.oculto }

/** O botão que recolhe e abre a lista de prompts de vídeo (VD12). */
fun rotuloDeVerPromptsDeVideo(aberto: Boolean, total: Int): String = if (aberto) "Esconder prompts de vídeo" else "Ver prompts de vídeo ($total)"

/** O botão que abre os prompts de vídeo ocultos (VD12). */
fun rotuloDosPromptsOcultos(aberto: Boolean, total: Int): String = if (aberto) "Esconder os ocultos" else "Ocultos ($total)"

/** A linha de baixo de um vídeo importado (VD18): o tamanho e, se veio de um prompt de vídeo, a origem. */
fun descreverVideoImportado(video: VideoImportado): String =
    listOfNotNull(tamanhoDoVideoParaLer(video.tamanho_em_bytes), if (video.prompt_id != null) "de um prompt de vídeo" else null).joinToString(" · ")

/**
 * A seção **Vídeo** de um frame: **Prompt de vídeo** (abre o diálogo de escolher a imagem), **Importar vídeo**, os prompts de vídeo
 * **recolhidos** atrás de "Ver prompts de vídeo (N)" (VD12) e os vídeos importados, que se tocam, se escolhem para o texto e se apagam.
 */
@Composable
internal fun SecaoDeVideo(frameId: Int, rotulo: String, imagens: List<ImagemDoPrompt>, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    LaunchedEffect(frameId) {
        acoes.aoCarregarVideos(frameId)
        acoes.aoCarregarVideosImportados(frameId)
    }
    val contexto = LocalContext.current
    val aplicacao = contexto.applicationContext as ImagineerApp
    var promptDeOrigem by remember(frameId) { mutableStateOf<Int?>(null) }
    var tocando by remember(frameId) { mutableStateOf<Int?>(null) }
    var verPrompts by rememberSaveable(frameId) { mutableStateOf(false) }
    var verOcultos by rememberSaveable(frameId) { mutableStateOf(false) }

    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) acoes.aoImportarVideo(frameId, promptDeOrigem, aplicacao.leitorDeArquivos.descrever(uri.toString()))
    }
    fun importar(deUmPrompt: Int?) {
        promptDeOrigem = deUmPrompt
        seletor.launch(arrayOf("video/*"))
    }

    Text("Vídeo", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (imagens.isNotEmpty()) {
            OutlinedButton(onClick = { acoes.aoAbrirDialogoDeVideo(frameId, rotulo, imagens) }) { Text(ROTULO_PROMPT_DE_VIDEO, maxLines = 1, softWrap = false) }
        }
        OutlinedButton(onClick = { importar(null) }, enabled = frameId !in estado.importandoVideo) {
            androidx.compose.material3.Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  $ROTULO_IMPORTAR_VIDEO", maxLines = 1, softWrap = false)
        }
    }
    if (imagens.isEmpty()) {
        Text(AVISO_SEM_IMAGEM_PARA_VIDEO, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (frameId in estado.importandoVideo) {
        val fracao = estado.importandoVideo[frameId]
        Text("Enviando o vídeo…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (fracao != null) LinearProgressIndicator(progress = { fracao }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    estado.mensagensDeVideo[frameId]?.let {
        Text(it.texto, style = MaterialTheme.typography.bodySmall, color = if (it.ehErro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
    }

    // Os vídeos importados: o que a pessoa quer ver e tocar, sempre à mostra.
    when (val importados = estado.videosImportados[frameId]) {
        null, VideosImportadosDoFrame.Lendo -> Unit
        is VideosImportadosDoFrame.Erro -> Text(importados.motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        is VideosImportadosDoFrame.Pronto -> importados.lista.forEach { video ->
            CartaoDeVideoImportado(video, aoTocar = { tocando = video.id }, frameId = frameId, acoes = acoes)
        }
    }
    tocando?.let { PlayerDeVideo(it) { tocando = null } }

    // Os prompts de vídeo: recolhidos (VD12); os ocultos têm a própria porta.
    when (val videos = estado.videos[frameId]) {
        null, VideosDoFrame.Lendo -> Unit
        is VideosDoFrame.Erro -> Text(videos.motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        is VideosDoFrame.Pronto -> {
            val visiveis = promptsDeVideoVisiveis(videos.lista)
            val ocultos = promptsDeVideoOcultos(videos.lista)
            if (visiveis.isNotEmpty()) TextButton(onClick = { verPrompts = !verPrompts }) { Text(rotuloDeVerPromptsDeVideo(verPrompts, visiveis.size)) }
            if (verPrompts) visiveis.forEach { prompt -> CartaoDeVideo(prompt, frameId, acoes, aoImportarDeste = { importar(prompt.id) }) }
            if (ocultos.isNotEmpty()) {
                TextButton(onClick = { verOcultos = !verOcultos }) { Text(rotuloDosPromptsOcultos(verOcultos, ocultos.size)) }
                if (verOcultos) ocultos.forEach { prompt -> CartaoDeVideo(prompt, frameId, acoes, aoImportarDeste = { importar(prompt.id) }) }
            }
        }
    }
}

/** Um vídeo importado (VD18): **Tocar**, **No texto** (liga e desliga o que o capítulo mostra, VD17) e **Apagar** (com confirmação). */
@Composable
private fun CartaoDeVideoImportado(video: VideoImportado, aoTocar: () -> Unit, frameId: Int, acoes: AcoesDoPainel) {
    var confirmandoApagar by remember(video.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Vídeo importado", style = MaterialTheme.typography.titleSmall)
            Text(descreverVideoImportado(video), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BotaoDeIcone(Icons.Filled.PlayArrow, "Tocar o vídeo", aoTocar)
                FilterChip(
                    selected = video.no_texto,
                    onClick = { acoes.aoDefinirVideoNoTexto(frameId, if (video.no_texto) null else video.id) },
                    label = { Text(if (video.no_texto) "Aparece no texto" else "Mostrar no texto") },
                )
                BotaoDeIcone(Icons.Filled.Delete, "Apagar o vídeo", { confirmandoApagar = true }, cor = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmandoApagar) {
        AlertDialog(
            onDismissRequest = { confirmandoApagar = false },
            title = { Text("Apagar este vídeo?") },
            text = { Text("O arquivo sai do servidor, sem lixeira. Se o texto mostrava o vídeo, volta a mostrar a imagem.") },
            confirmButton = { TextButton(onClick = { confirmandoApagar = false; acoes.aoApagarVideo(frameId, video.id) }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmandoApagar = false }) { Text("Cancelar") } },
        )
    }
}

/**
 * Um prompt de vídeo, com tudo à mão: **Copiar**, **Compartilhar** (VD15), **Editar** (VD13), **Traduzir** (VD14), **Ocultar** ou
 * **Mostrar** (VD12) e **Importar vídeo** deste prompt (o vídeo importado diz de qual prompt veio, VD18).
 */
@Composable
private fun CartaoDeVideo(video: PromptDeFrame, frameId: Int, acoes: AcoesDoPainel, aoImportarDeste: () -> Unit) {
    val contexto = LocalContext.current
    val aplicacao = contexto.applicationContext as ImagineerApp
    val escopo = rememberCoroutineScope()
    val area = LocalClipboardManager.current
    var abrindo by remember { mutableStateOf(false) }
    // Editando; e se o diálogo abre já na aba Português (o ícone Traduzir).
    var editando by remember(video.id) { mutableStateOf(false) }
    var emPortugues by remember(video.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (video.oculto) "$ROTULO_PROMPT_DE_VIDEO (oculto)" else ROTULO_PROMPT_DE_VIDEO, style = MaterialTheme.typography.titleSmall)
            video.imagem_partida_id?.let { MiniaturaDoQuadroInicial(it) }
            SelectionContainer { Text(video.texto, style = MaterialTheme.typography.bodyMedium) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BotaoDeIcone(Icons.Filled.ContentCopy, "Copiar o prompt de vídeo", {
                    area.setText(AnnotatedString(video.texto))
                    Toast.makeText(contexto, "Prompt de vídeo copiado.", Toast.LENGTH_SHORT).show()
                })
                BotaoDeIcone(Icons.Filled.Share, "Compartilhar o prompt de vídeo", {
                    if (!abrindo) {
                        abrindo = true
                        escopo.launch {
                            try {
                                // O prompt vai também para a área de transferência: se o app escolhido não aproveitar o texto junto da imagem, é só colar.
                                area.setText(AnnotatedString(video.texto))
                                compartilharPromptDeVideo(contexto, aplicacao, video)
                            } finally {
                                abrindo = false
                            }
                        }
                    }
                })
                BotaoDeIcone(Icons.Filled.Edit, "Editar o prompt de vídeo", { emPortugues = false; editando = true })
                BotaoDeIcone(Icons.Filled.Translate, "Ver em português", { emPortugues = true; editando = true })
                BotaoDeIcone(Icons.Filled.Upload, "Importar o vídeo deste prompt", aoImportarDeste)
                if (video.oculto) BotaoDeIcone(Icons.Filled.Visibility, "Mostrar o prompt de vídeo", { acoes.aoOcultarPromptDeVideo(frameId, video.id, false) })
                else BotaoDeIcone(Icons.Filled.VisibilityOff, "Ocultar o prompt de vídeo", { acoes.aoOcultarPromptDeVideo(frameId, video.id, true) })
            }
            if (abrindo) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
    if (editando) {
        DialogoDoTextoDoPrompt(
            chave = "video${video.id}",
            titulo = "Editar o prompt de vídeo",
            motivo = null,
            explicacao = "O texto novo substitui o atual neste prompt. Vale o inglês: é ele que vai ao gerador de vídeo.",
            textoInicial = video.texto,
            rotuloDoBotao = "Salvar",
            opcoesDeModelo = emptyList(),
            opcoesSemFiltro = emptyList(),
            padraoDoServidor = null,
            modeloInicial = null,
            promptId = video.id,
            acoes = acoes,
            aoConfirmar = { texto, _, textoPt ->
                acoes.aoSalvarPromptDeVideo(frameId, video.id, texto, textoPt)
                editando = false
            },
            aoFechar = { editando = false },
            rotuloDoFechar = "Cancelar",
            iniciarEmPortugues = emPortugues,
        )
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
 * **Compartilhar** (VD15): baixa a imagem de partida para o cache e abre a folha de compartilhamento do Android com o **texto e a imagem
 * juntos** (`ACTION_SEND` com `EXTRA_TEXT` e `EXTRA_STREAM`); o aplicativo é a pessoa que escolhe. Se ele aproveita os dois ou só um é coisa que
 * só se vê no aparelho: por isso o prompt também foi para a área de transferência. Sem imagem de partida, compartilha só o texto. Nada aqui
 * fecha o app.
 */
private suspend fun compartilharPromptDeVideo(contexto: Context, aplicacao: ImagineerApp, video: PromptDeFrame) {
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
        contexto.startActivity(Intent.createChooser(envio, "Compartilhar o prompt de vídeo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Toast.makeText(contexto, "Prompt copiado. Se o app escolhido receber só a imagem, cole o prompt.", Toast.LENGTH_LONG).show()
    } catch (erro: kotlinx.coroutines.CancellationException) {
        throw erro
    } catch (erro: Exception) {
        Log.e(ETIQUETA_DO_LOG, "Não consegui compartilhar o prompt de vídeo", erro)
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
