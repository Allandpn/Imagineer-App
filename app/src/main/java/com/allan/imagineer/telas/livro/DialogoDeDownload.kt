package com.allan.imagineer.telas.livro

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.allan.imagineer.local.BaixadorDeLivros
import com.allan.imagineer.local.Estimativa
import com.allan.imagineer.local.EstadoDoDownload
import com.allan.imagineer.local.Progresso
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.lixeira.descreverTamanho
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// "Baixar para ler offline" na tela do livro (item 7.0a, passo 4; PL3 e PL4).

/** O texto do item do ⋮ do livro (DL3): sempre "Download"; o estado vem numa linha pequena embaixo ([detalheDoDownload]) e as ações, no diálogo. */
const val ROTULO_DO_DOWNLOAD = "Download"

/** Como o ícone e o item de menu veem o download de um livro (item 7.5g, DL1 e DL2). */
enum class SituacaoDoDownload { NAO_BAIXADO, BAIXANDO, PARADO, EM_DIA, DESATUALIZADO, FALHOU }

/**
 * A situação do download de um livro (DL2): Baixado e a `revisao` do servidor maior que a baixada = **desatualizado**. Sem saber a revisão
 * do servidor (sem conexão, servidor antigo), o livro baixado conta como em dia: não se acusa uma atualização que não se pode conferir.
 * Mudar só um favorito ou a posição de leitura não sobe a `revisao` no servidor, então não acusa atualização à toa.
 */
fun situacaoDoDownload(estado: EstadoDoDownload, revisaoDoServidor: Int?): SituacaoDoDownload = when (estado) {
    is EstadoDoDownload.NaoBaixado -> SituacaoDoDownload.NAO_BAIXADO
    is EstadoDoDownload.Baixando -> SituacaoDoDownload.BAIXANDO
    is EstadoDoDownload.Pausado -> SituacaoDoDownload.PARADO
    is EstadoDoDownload.Falhou -> SituacaoDoDownload.FALHOU
    is EstadoDoDownload.Baixado ->
        if (revisaoDoServidor != null && revisaoDoServidor > estado.revisao) SituacaoDoDownload.DESATUALIZADO else SituacaoDoDownload.EM_DIA
}

/** A linha pequena sob "Download" no menu (DL3). */
fun detalheDoDownload(situacao: SituacaoDoDownload): String = when (situacao) {
    SituacaoDoDownload.NAO_BAIXADO -> "não baixado"
    SituacaoDoDownload.BAIXANDO -> "baixando…"
    SituacaoDoDownload.PARADO -> "parado"
    SituacaoDoDownload.EM_DIA -> "baixado"
    SituacaoDoDownload.DESATUALIZADO -> "há uma atualização"
    SituacaoDoDownload.FALHOU -> "falhou"
}

/** Um livro Baixado abre sem conexão, então o item do menu continua valendo sem rede (para remover o download). */
fun downloadValeSemRede(situacao: SituacaoDoDownload): Boolean = situacao == SituacaoDoDownload.EM_DIA || situacao == SituacaoDoDownload.DESATUALIZADO

/** "12 de 150 arquivos · 34 MB". */
fun descreverProgressoDoDownload(progresso: Progresso): String =
    "${progresso.arquivosFeitos} de ${progresso.arquivosTotal} arquivos · ${descreverTamanho(progresso.bytesFeitos)}"

/** "04/10/2026", no fuso do aparelho. */
fun descreverDataDoDownload(milissegundos: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date(milissegundos))

/** O que o texto de "Baixar — 240 MB" diz; sem nada a baixar, diz que já está tudo no aparelho. */
fun textoDaEstimativa(estimativa: Estimativa): String =
    if (estimativa.imagens == 0) "Baixar o texto do livro (ele não tem imagens)."
    else if (estimativa.bytesRestantes == 0L) "As ${estimativa.imagens} imagens já estão no aparelho; só falta marcar o livro como baixado."
    else "Baixar — ${descreverTamanho(estimativa.bytesRestantes)} (${estimativa.imagens} imagens, mais as versões reduzidas) e o texto de todos os capítulos."

/**
 * O diálogo do download: confirma o tamanho antes de começar (com "Só em Wi-Fi"), mostra o andamento (pausar, cancelar) e, depois de
 * pronto, oferece remover. Fechar o diálogo **não interrompe** o download: ele vive no escopo do app.
 */
@Composable
fun DialogoDeDownload(
    livroId: Int,
    baixador: BaixadorDeLivros,
    estado: EstadoDoDownload,
    aoFechar: () -> Unit,
    /** A `revisao` do livro no servidor agora (DL2); `null` = não se sabe, e o livro baixado conta como em dia. */
    revisaoDoServidor: Int? = null,
) {
    val escopo = rememberCoroutineScope()
    var somenteWifi by rememberSaveable { mutableStateOf(true) }
    var estimativa by remember { mutableStateOf<ResultadoDaChamada<Estimativa>?>(null) }
    val precisaEstimar = estado is EstadoDoDownload.NaoBaixado || estado is EstadoDoDownload.Falhou
    LaunchedEffect(livroId, precisaEstimar) { if (precisaEstimar) estimativa = baixador.estimar(livroId) }
    val desatualizado = situacaoDoDownload(estado, revisaoDoServidor) == SituacaoDoDownload.DESATUALIZADO

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(ROTULO_DO_DOWNLOAD) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (estado) {
                    is EstadoDoDownload.NaoBaixado, is EstadoDoDownload.Falhou -> {
                        if (estado is EstadoDoDownload.Falhou) Text(estado.motivo, color = MaterialTheme.colorScheme.error)
                        when (val e = estimativa) {
                            null -> CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                            is ResultadoDaChamada.Falha -> Text(e.motivo, color = MaterialTheme.colorScheme.error)
                            is ResultadoDaChamada.Sucesso -> Text(textoDaEstimativa(e.dado))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Switch(checked = somenteWifi, onCheckedChange = { somenteWifi = it })
                            Text("Só em Wi-Fi")
                        }
                    }
                    is EstadoDoDownload.Baixando -> {
                        LinearProgressIndicator(progress = { estado.progresso.fracao }, modifier = Modifier.fillMaxWidth())
                        Text(descreverProgressoDoDownload(estado.progresso), style = MaterialTheme.typography.bodySmall)
                        Text("Pode fechar esta janela: o download continua.", style = MaterialTheme.typography.bodySmall)
                    }
                    is EstadoDoDownload.Pausado -> {
                        LinearProgressIndicator(progress = { estado.progresso.fracao }, modifier = Modifier.fillMaxWidth())
                        Text("${estado.motivo} · ${descreverProgressoDoDownload(estado.progresso)}", style = MaterialTheme.typography.bodySmall)
                    }
                    is EstadoDoDownload.Baixado -> {
                        Text("Baixado em ${descreverDataDoDownload(estado.em)} · ${descreverTamanho(estado.bytes)}. O livro abre sem conexão, com todas as imagens.")
                        if (desatualizado) {
                            Text(
                                "O livro mudou no servidor desde então. Atualize para trazer o texto e as imagens novos.",
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (estado) {
                is EstadoDoDownload.NaoBaixado, is EstadoDoDownload.Falhou ->
                    TextButton(
                        onClick = { baixador.baixar(livroId, somenteWifi); },
                        enabled = estimativa is ResultadoDaChamada.Sucesso,
                    ) { Text(if (estado is EstadoDoDownload.Falhou) "Tentar de novo" else "Baixar") }
                is EstadoDoDownload.Baixando -> TextButton(onClick = { baixador.pausar(livroId) }) { Text("Pausar") }
                is EstadoDoDownload.Pausado -> TextButton(onClick = { baixador.baixar(livroId, somenteWifi) }) { Text("Continuar") }
                // DL3: desatualizado, o botão principal é Atualizar (Remover vai para o lado); em dia, o principal é Remover.
                is EstadoDoDownload.Baixado ->
                    if (desatualizado) TextButton(onClick = { baixador.atualizar(livroId, somenteWifi) }) { Text("Atualizar") }
                    else TextButton(onClick = { escopo.launch { baixador.remover(livroId) } }) { Text("Remover download", color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = {
            Row {
                // DL3: com uma atualização à espera, remover o download fica ao lado (o botão principal é Atualizar).
                if (estado is EstadoDoDownload.Baixado && desatualizado) {
                    TextButton(onClick = { escopo.launch { baixador.remover(livroId) } }) { Text("Remover", color = MaterialTheme.colorScheme.error) }
                }
                if (estado is EstadoDoDownload.Baixando || estado is EstadoDoDownload.Pausado) {
                    TextButton(onClick = { escopo.launch { baixador.cancelar(livroId) } }) { Text("Cancelar", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = aoFechar) { Text("Fechar") }
            }
        },
    )
}

/**
 * O que o ⋮ de um livro faz **na biblioteca** (item 7.2b). Na visão de **capas** vão só [aoAlternarFavorito], [aoDefinirCapa] e [aoApagar]
 * (o menu é pequeno, sobre a capa); na visão de **lista** vão também as telas do livro (BL5), que o menu da tela do livro já tem.
 * Item com ação nula não aparece.
 */
data class AcoesDoMenuNaBiblioteca(
    val aoDefinirCapa: () -> Unit,
    val aoApagar: () -> Unit,
    val aoAlternarFavorito: (() -> Unit)? = null,
    val rotuloDoFavoritar: String = "Adicionar aos favoritos",
    val aoPesquisar: (() -> Unit)? = null,
    val aoAbrirDestaques: (() -> Unit)? = null,
    val aoAbrirFavoritos: (() -> Unit)? = null,
    val aoAbrirModelos: (() -> Unit)? = null,
    val aoAbrirLixeira: (() -> Unit)? = null,
    val aoEditar: (() -> Unit)? = null,
)

/**
 * O ⋮ de um livro **na biblioteca**, com o **Baixar para ler offline** (PL3) como na tela do livro: o rótulo e o diálogo seguem o estado do
 * download daquele livro, que o baixador guarda no escopo do app.
 */
@Composable
fun MenuDoLivroDaBiblioteca(
    livroId: Int,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    sobreACapa: Boolean = false,
    acoes: AcoesDoMenuNaBiblioteca,
    /** A `revisao` do livro na lista do servidor (DL2): com ela se sabe se o download está desatualizado. */
    revisaoDoServidor: Int? = null,
) {
    val baixador = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.allan.imagineer.ImagineerApp).baixadorDeLivros
    val estados by baixador.estados.collectAsState()
    val estado = estados[livroId] ?: EstadoDoDownload.NaoBaixado
    var dialogo by remember { mutableStateOf(false) }
    LaunchedEffect(livroId) { baixador.carregar(livroId) }
    MenuDoLivro(
        modifier = modifier,
        sobreACapa = sobreACapa,
        aoAlternarFavorito = acoes.aoAlternarFavorito,
        rotuloDoFavoritar = acoes.rotuloDoFavoritar,
        aoPesquisar = acoes.aoPesquisar,
        aoAbrirDestaques = acoes.aoAbrirDestaques,
        aoAbrirFavoritos = acoes.aoAbrirFavoritos,
        aoAbrirModelos = acoes.aoAbrirModelos,
        aoAbrirLixeira = acoes.aoAbrirLixeira,
        aoEditar = acoes.aoEditar,
        aoDefinirCapa = acoes.aoDefinirCapa,
        aoAbrirOffline = { dialogo = true },
        rotuloDoOffline = ROTULO_DO_DOWNLOAD,
        detalheDoOffline = detalheDoDownload(situacaoDoDownload(estado, revisaoDoServidor)),
        offlineValeSemRede = downloadValeSemRede(situacaoDoDownload(estado, revisaoDoServidor)),
        aoApagar = acoes.aoApagar,
    )
    if (dialogo) DialogoDeDownload(livroId, baixador, estado, aoFechar = { dialogo = false }, revisaoDoServidor = revisaoDoServidor)
}

/** O que o leitor de tela diz do ícone de download de um livro, e o que o toque faz (DL1). */
fun descricaoDoIconeDeDownload(situacao: SituacaoDoDownload): String = when (situacao) {
    SituacaoDoDownload.NAO_BAIXADO -> "Baixar para ler offline"
    SituacaoDoDownload.BAIXANDO -> "Baixando; toque para ver o andamento"
    SituacaoDoDownload.PARADO -> "Download parado; toque para continuar"
    SituacaoDoDownload.EM_DIA -> "Baixado; toque para remover o download"
    SituacaoDoDownload.DESATUALIZADO -> "Há uma atualização; toque para atualizar o download"
    SituacaoDoDownload.FALHOU -> "O download falhou; toque para tentar de novo"
}
