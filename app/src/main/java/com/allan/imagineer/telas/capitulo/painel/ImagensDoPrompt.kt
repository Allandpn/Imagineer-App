package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Button
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
import android.util.Log
import android.widget.Toast
import com.allan.imagineer.rede.enderecoDaImagem

/** O endereço do servidor configurado, para montar as URLs das imagens (J7). `null` enquanto não se sabe. */
@Composable
internal fun urlDoServidorEmUso(): String? {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val url by aplicacao.armazenamento.urlDoServidor.collectAsState(initial = null)
    return url
}

/**
 * As **imagens do frame**, em destaque (Q1, Q7, T2 revisada em 02/10/2026): as miniaturas de **todas** as imagens, geradas e
 * importadas, da mais nova para a mais antiga (a origem não separa mais em seções); a barra com a etapa em andamento (Q4);
 * os recados; o **botão principal**, que faz o que falta (Q2) e, ao lado, **Importar imagem** (T1), que leva a imagem para o
 * prompt mais recente. Sob os botões, a linha que diz o que o principal faz e que gasta IA (Q3). [lista] vem do mais novo
 * para o mais antigo; vazia se os prompts ainda não foram lidos. A relação prompt-imagem continua visível nos prompts (cada
 * cartão mostra as imagens dele).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SecaoDaImagemDoFrame(
    frameId: Int,
    lista: List<PromptDeFrame>,
    estado: EstadoDoPainel,
    acoes: AcoesDoPainel,
    chave: String,
    rotulo: String,
    rotuloDoBotao: String,
    ehCena: Boolean = false,
) {
    val etapa = estado.etapasDeImagem[chave]
    val gerandoAlgum = lista.any { it.id in estado.gerandoImagem }
    val maisRecente = lista.firstOrNull() // para onde vai a imagem importada (T1)
    val importando = maisRecente != null && maisRecente.id in estado.importandoImagem
    val gerandoPrompt = frameId in estado.gerandoPrompt
    val ocupado = etapa != null || gerandoAlgum || importando || gerandoPrompt

    // Z6: a lista de modelos vem do servidor, uma vez.
    LaunchedEffect(Unit) { acoes.aoCarregarModelosDeImagem() }
    Text("Imagens", style = MaterialTheme.typography.titleSmall)
    Miniaturas(frameId, lista.flatMap { it.imagens }, "Imagem", acoes)
    if (etapa != null || gerandoAlgum || gerandoPrompt) {
        // O servidor não informa o andamento, então a barra é indeterminada (K2).
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            // O "Novo prompt" (fora do fluxo de um toque) também mostra a sua etapa aqui.
            val texto = etapa?.let(::descreverEtapa)
                ?: if (gerandoPrompt) descreverEtapa(EtapaDaImagem.MONTANDO_O_PROMPT) else AVISO_GERANDO_IMAGEM
            Text(texto, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (importando && maisRecente != null) {
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
    lista.forEach { prompt -> estado.mensagensDeImagem[prompt.id]?.let { RecadoDeImagem(it) } }
    // Z6: qual modelo de imagem a próxima geração vai usar, com a troca.
    ModeloDeImagemEmUso(estado, acoes, ocupado)
    // EV1, EV8: numa cena, a linha dos elementos e imagens (o retrato tem a dele, no bloco do retrato).
    if (ehCena) LinhaDoSeletorDeElementos(frameId, ehCena = true, estado = estado, acoes = acoes, aoEscolher = { acoes.aoAbrirSeletorDaCena(frameId) }, ocupado = ocupado)
    maisRecente?.let { prompt -> estado.mensagensDeImportacao[prompt.id]?.let { RecadoDeImagem(it) } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { acoes.aoGerarImagemDoFrame(chave, frameId, rotulo) },
            enabled = !ocupado,
        ) { Text(rotuloDoBotao, maxLines = 1, softWrap = false) }
        // T1: um só botão por frame; a imagem vai para o prompt mais recente. Sem prompt não há para onde importar.
        // O seletor não abre aqui: mora na tela do capítulo (J2).
        if (maisRecente != null) {
            OutlinedButton(onClick = { acoes.aoEscolherImagem(frameId, maisRecente.id) }, enabled = !ocupado) {
                Text("Importar imagem", maxLines = 1, softWrap = false)
            }
            // G3, Q6: **Novo prompt** à vista: gera outro prompt (com o diálogo de custo e o ajuste opcional). Como o botão
            // principal usa o prompt mais recente, é por aqui que se recomeça do zero, por exemplo para testar outro
            // modelo de suavização. Depois é só tocar em Gerar imagem.
            OutlinedButton(onClick = { acoes.aoPedirGerarPrompt(frameId, rotulo) }, enabled = !ocupado) {
                Text(rotuloDoBotaoDePrompt(jaTemPrompts = true), maxLines = 1, softWrap = false)
            }
        }
    }
    Text(avisoDoBotaoPrincipal(lista.isNotEmpty()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * "Modelo de imagem: X" com **Trocar** (Z6, Z12): o modelo que o próximo **Gerar imagem** vai usar. Aparece junto de **todo**
 * botão de gerar (o do frame e o de cada cartão de prompt), para o usuário ver e trocar o modelo em qualquer geração. A escolha
 * é a mesma para todos (Z6). Some enquanto a lista de modelos do servidor não chegou.
 */
@Composable
internal fun ModeloDeImagemEmUso(estado: EstadoDoPainel, acoes: AcoesDoPainel, ocupado: Boolean) {
    modeloEmUso(estado.modeloEscolhido, estado.modelosDeImagem)?.let { emUso ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val sufixo = if (modeloEstaSemFiltro(emUso, estado.modelosDeImagem)) " (sem filtro)" else ""
            Text("Modelo de imagem: $emUso$sufixo", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f, fill = false))
            TextButton(onClick = acoes.aoAbrirEscolhaDeModelo, enabled = !ocupado) { Text("Trocar", maxLines = 1, softWrap = false) }
        }
    }
}

/**
 * A lista de modelos de imagem para escolher (Z6, Z9), uma opção por linha com um botão de opção. O [padrao] do servidor
 * leva "(padrão)". Usado no diálogo de trocar o modelo e dentro dos diálogos de editar e da recusa.
 */
@Composable
internal fun SeletorDeModelo(
    opcoes: List<String>,
    padrao: String?,
    selecionado: String?,
    aoSelecionar: (String) -> Unit,
    semFiltro: List<String> = emptyList(),
) {
    Column(modifier = Modifier.selectableGroup()) {
        opcoes.forEach { modelo -> LinhaDeModelo(modelo, if (modelo == padrao) "$modelo (padrão)" else modelo, selecionado, aoSelecionar) }
        if (semFiltro.isNotEmpty()) {
            // F19: os modelos que permitem desligar o filtro ficam numa seção própria, com o aviso à vista.
            Text("Sem filtro de segurança", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Escolher um destes DESLIGA o filtro de segurança do modelo (só no Replicate). A responsabilidade pelo conteúdo é de " +
                    "quem pede, e o sistema recusa se o texto falar de um menor de idade. A imagem fica marcada \"Sem filtro\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            semFiltro.forEach { modelo -> LinhaDeModelo(modelo, modelo, selecionado, aoSelecionar) }
        }
    }
}

@Composable
private fun LinhaDeModelo(modelo: String, texto: String, selecionado: String?, aoSelecionar: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = modelo == selecionado, onClick = { aoSelecionar(modelo) }, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = modelo == selecionado, onClick = null)
        Text(texto, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** As imagens de **um prompt**, no cartão dele: a relação prompt-imagem (02/10/2026). Tocar numa abre a tela cheia. */
@Composable
internal fun ImagensDoPrompt(prompt: PromptDeFrame, acoes: AcoesDoPainel) {
    Miniaturas(prompt.frame_id, prompt.imagens, "Imagem deste prompt", acoes)
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
                Column(modifier = Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AsyncImage(
                        model = enderecoDaImagem(urlBase, imagem.id, "miniatura"),
                        contentDescription = if (imagem.canonica) "$descricao, canônica" else descricao,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .border(if (imagem.canonica) 3.dp else 0.dp, if (imagem.canonica) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { abertaId = imagem.id },
                    )
                    // CAN6: o selo da imagem que o capítulo mostra.
                    if (imagem.canonica) Text("Canônica", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
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
                // CAN6: escolher a canônica (a que o capítulo mostra) ou, se já é, tirar a escolha.
                acaoExtra = rotuloDaAcaoCanonica(imagem.canonica) to { acoes.aoDefinirImagemCanonica(frameId, if (imagem.canonica) null else imagem.id); abertaId = null },
            )
        }
    }
}

/**
 * A imagem em **tela cheia**, no tamanho normal (J4), sobre fundo preto, com **zoom por pinça** e arrastar. Embaixo, as
 * ações (U): **Compartilhar**, **Salvar na galeria** e **Excluir**. O botão de fechar e o de voltar do Android fecham.
 */
@Composable
internal fun ImagemEmTelaCheia(
    imagem: ImagemDoPrompt,
    url: String,
    aoExcluir: (() -> Unit)?,
    aoFechar: () -> Unit,
    mostrarOrigem: Boolean = true,
    /** Uma ação a mais na barra de baixo ("Definir como canônica", CAN6): rótulo e o que fazer. */
    acaoExtra: Pair<String, () -> Unit>? = null,
) {
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }
    val contexto = LocalContext.current
    val aplicacao = contexto.applicationContext as ImagineerApp
    val escopo = rememberCoroutineScope()
    var baixando by remember { mutableStateOf(false) }

    /**
     * Baixa o original para o cache (U4) e entrega o arquivo já com a extensão certa à [acao]. **Nada daqui fecha o app**:
     * qualquer falha, esperada ou não, vira um aviso (com o tipo do erro, para dar para diagnosticar) e vai para o log.
     */
    fun baixarE(oQueFaz: String, acao: (arquivo: java.io.File, tipo: String) -> Unit) {
        if (baixando) return
        baixando = true
        escopo.launch {
            try {
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
            } catch (erro: kotlinx.coroutines.CancellationException) {
                throw erro // a tela fechou no meio: não é falha
            } catch (erro: Exception) {
                Log.e(ETIQUETA_DO_LOG, "Falhou ao $oQueFaz a imagem ${imagem.id}", erro)
                Toast.makeText(contexto, "Não consegui $oQueFaz a imagem (${erro.javaClass.simpleName}).", Toast.LENGTH_LONG).show()
            } finally {
                baixando = false
            }
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
            // Z8: quem gerou a imagem (no capítulo não se sabe, só o id: sem rótulo).
            if (mostrarOrigem) Text(
                descreverOrigemDaImagem(imagem),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        baixarE("compartilhar") { arquivo, tipo ->
                            if (!compartilharImagem(contexto, arquivo, tipo)) {
                                Toast.makeText(contexto, "Não consegui compartilhar a imagem.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !baixando,
                ) {
                    Text("Compartilhar", color = Color.White)
                }
                TextButton(
                    onClick = {
                        baixarE("salvar") { arquivo, tipo ->
                            val salvou = salvarNaGaleria(contexto, arquivo, tipo)
                            Toast.makeText(contexto, if (salvou) AVISO_SALVA_NA_GALERIA else AVISO_NAO_SALVOU_NA_GALERIA, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !baixando,
                ) { Text("Salvar na galeria", color = Color.White) }
                acaoExtra?.let { (rotulo, aoTocar) -> TextButton(onClick = aoTocar) { Text(rotulo, color = Color.White) } }
                // Excluir só onde se sabe de que prompt é a imagem (o painel); no capítulo, não.
                if (aoExcluir != null) TextButton(onClick = aoExcluir) { Text("Excluir", color = Color(0xFFFF8A80)) }
            }
            if (baixando) LinearProgressIndicator(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
    }
}
