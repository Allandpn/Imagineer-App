package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.extensaoDoTipo
import kotlinx.coroutines.launch
import android.widget.Toast
import com.allan.imagineer.rede.enderecoDaImagem

/** O endereço do servidor configurado, para montar as URLs das imagens (J7). `null` enquanto não se sabe. */
@Composable
private fun urlDoServidorEmUso(): String? {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val url by aplicacao.armazenamento.urlDoServidor.collectAsState(initial = null)
    return url
}

/**
 * As imagens **geradas** de um prompt (T2): a barra enquanto o servidor gera (K2), o recado do resultado (K3) e as
 * miniaturas, da mais nova para a mais antiga. As importadas **não** aparecem aqui: ficam na seção própria, no fim.
 */
@Composable
internal fun ImagensDoPrompt(prompt: PromptDeFrame, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    if (prompt.id in estado.gerandoImagem) {
        // K2: o servidor não informa o andamento, então a barra é indeterminada.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(AVISO_GERANDO_IMAGEM, style = MaterialTheme.typography.bodySmall)
        }
    }
    estado.mensagensDeImagem[prompt.id]?.let { RecadoDeImagem(it) }
    Miniaturas(prompt.frame_id, prompt.imagens.filter { it.origem == "GERADA" }, "Imagem gerada", acoes)
}

/**
 * A seção **"Imagens importadas"** (T1, T2), no fim da lista de prompts do frame: as miniaturas de todas as imagens
 * importadas e **um só botão "Importar imagem"**, que leva a imagem para o prompt **mais recente** ([lista] vem do mais novo
 * para o mais antigo). O botão **não abre o seletor aqui**: o seletor mora na tela do capítulo (J2).
 */
@Composable
internal fun SecaoDeImagensImportadas(frameId: Int, lista: List<PromptDeFrame>, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val maisRecente = lista.firstOrNull() ?: return // sem prompt não há para onde importar (T1)
    val importadas = lista.flatMap { prompt -> prompt.imagens.filter { it.origem == "IMPORTADA" } }.sortedByDescending { it.id }
    val importando = maisRecente.id in estado.importandoImagem

    Text("Imagens importadas", style = MaterialTheme.typography.titleSmall)
    if (importando) {
        val fracao = estado.importandoImagem[maisRecente.id]
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (fracao == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fracao }, modifier = Modifier.fillMaxWidth())
            }
            Text("Enviando a imagem…", style = MaterialTheme.typography.bodySmall)
        }
    }
    estado.mensagensDeImportacao[maisRecente.id]?.let { RecadoDeImagem(it) }
    if (importadas.isEmpty() && !importando) {
        Text("Nenhuma imagem importada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Miniaturas(frameId, importadas, "Imagem importada", acoes)
    // J2: um botão só por frame; a imagem vai para o prompt mais recente.
    OutlinedButton(
        onClick = { acoes.aoEscolherImagem(frameId, maisRecente.id) },
        enabled = !importando && maisRecente.id !in estado.gerandoImagem,
    ) {
        Text("Importar imagem", maxLines = 1, softWrap = false)
    }
}

@Composable
private fun RecadoDeImagem(mensagem: MensagemDoElemento) {
    Text(
        mensagem.texto,
        style = MaterialTheme.typography.bodySmall,
        color = if (mensagem.ehErro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
    )
}

/**
 * As miniaturas de [imagens] (J4): tocar numa abre a imagem em tela cheia, no tamanho normal. A tela cheia só existe
 * enquanto a imagem está na lista: ao **excluir** (U3), ela some da lista e a tela cheia fecha sozinha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Miniaturas(frameId: Int, imagens: List<ImagemDoPrompt>, descricao: String, acoes: AcoesDoPainel) {
    val urlBase = urlDoServidorEmUso()
    var abertaId by remember { mutableStateOf<Int?>(null) }
    if (urlBase != null && imagens.isNotEmpty()) {
        // Do mais novo para o mais antigo (J4).
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            imagens.sortedByDescending { it.id }.forEach { imagem ->
                AsyncImage(
                    model = enderecoDaImagem(urlBase, imagem.id, "miniatura"),
                    contentDescription = descricao,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .clickable { abertaId = imagem.id },
                )
            }
        }
    }
    imagens.firstOrNull { it.id == abertaId }?.let { imagem ->
        if (urlBase != null) {
            ImagemEmTelaCheia(
                imagem = imagem,
                url = enderecoDaImagem(urlBase, imagem.id, "original"),
                aoExcluir = { acoes.aoPedirExcluirImagem(frameId, imagem.prompt_id, imagem.id, imagem.origem) },
                aoFechar = { abertaId = null },
            )
        }
    }
}

/**
 * A imagem em **tela cheia**, no tamanho normal (J4), sobre fundo preto, com **zoom por pinça** e arrastar. Embaixo, as
 * ações (U): **Compartilhar**, **Salvar na galeria** e **Excluir**. O botão de fechar e o de voltar do Android fecham.
 */
@Composable
private fun ImagemEmTelaCheia(imagem: ImagemDoPrompt, url: String, aoExcluir: () -> Unit, aoFechar: () -> Unit) {
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }
    val contexto = LocalContext.current
    val aplicacao = contexto.applicationContext as ImagineerApp
    val escopo = rememberCoroutineScope()
    var baixando by remember { mutableStateOf(false) }

    /** Baixa o original para o cache (U4) e entrega o arquivo já com a extensão certa à [acao]; erro vira um aviso. */
    fun baixarE(acao: (arquivo: java.io.File, tipo: String) -> Unit) {
        if (baixando) return
        baixando = true
        escopo.launch {
            val temporario = java.io.File(contexto.cacheDir, "imagens/imagem_${imagem.id}.baixando")
            when (val resultado = aplicacao.repositorioDePrompts.baixarImagem(imagem.id, temporario)) {
                is ResultadoDaChamada.Falha -> Toast.makeText(contexto, resultado.motivo, Toast.LENGTH_LONG).show()
                is ResultadoDaChamada.Sucesso -> {
                    val arquivo = java.io.File(temporario.parentFile, "imagem_${imagem.id}.${extensaoDoTipo(resultado.dado)}")
                    arquivo.delete()
                    temporario.renameTo(arquivo)
                    acao(arquivo, resultado.dado)
                }
            }
            baixando = false
        }
    }

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
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { baixarE { arquivo, tipo -> compartilharImagem(contexto, arquivo, tipo) } }, enabled = !baixando) {
                    Text("Compartilhar", color = Color.White)
                }
                TextButton(
                    onClick = {
                        baixarE { arquivo, tipo ->
                            val salvou = salvarNaGaleria(contexto, arquivo, tipo)
                            Toast.makeText(contexto, if (salvou) AVISO_SALVA_NA_GALERIA else AVISO_NAO_SALVOU_NA_GALERIA, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !baixando,
                ) { Text("Salvar na galeria", color = Color.White) }
                TextButton(onClick = aoExcluir) { Text("Excluir", color = Color(0xFFFF8A80)) }
            }
            if (baixando) LinearProgressIndicator(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
    }
}
