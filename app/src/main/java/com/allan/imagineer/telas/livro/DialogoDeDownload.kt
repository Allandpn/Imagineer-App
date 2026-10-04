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

/** O texto do item do ⋮ do livro, conforme o estado do download: só a palavra; ao tocar, abre o diálogo com os detalhes e as ações. */
fun rotuloDoOffline(estado: EstadoDoDownload): String = when (estado) {
    is EstadoDoDownload.Baixado -> "Baixado"
    is EstadoDoDownload.Baixando, is EstadoDoDownload.Pausado -> "Baixando"
    else -> "Baixar"
}

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
fun DialogoDeDownload(livroId: Int, baixador: BaixadorDeLivros, estado: EstadoDoDownload, aoFechar: () -> Unit) {
    val escopo = rememberCoroutineScope()
    var somenteWifi by rememberSaveable { mutableStateOf(true) }
    var estimativa by remember { mutableStateOf<ResultadoDaChamada<Estimativa>?>(null) }
    val precisaEstimar = estado is EstadoDoDownload.NaoBaixado || estado is EstadoDoDownload.Falhou
    LaunchedEffect(livroId, precisaEstimar) { if (precisaEstimar) estimativa = baixador.estimar(livroId) }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Ler offline") },
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
                    is EstadoDoDownload.Baixado -> Text(
                        "Baixado em ${descreverDataDoDownload(estado.em)} · ${descreverTamanho(estado.bytes)}. O livro abre sem conexão, com todas as imagens.",
                    )
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
                is EstadoDoDownload.Baixado -> TextButton(onClick = { escopo.launch { baixador.remover(livroId) } }) {
                    Text("Remover download", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            Row {
                if (estado is EstadoDoDownload.Baixando || estado is EstadoDoDownload.Pausado) {
                    TextButton(onClick = { escopo.launch { baixador.cancelar(livroId) } }) { Text("Cancelar", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = aoFechar) { Text("Fechar") }
            }
        },
    )
}

/**
 * O ⋮ de um livro **na biblioteca**, com o **Baixar para ler offline** (PL3) como na tela do livro: o rótulo e o diálogo seguem o estado do
 * download daquele livro, que o baixador guarda no escopo do app.
 */
@Composable
fun MenuDoLivroDaBiblioteca(
    livroId: Int,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    sobreACapa: Boolean = false,
    aoDefinirCapa: () -> Unit,
    aoApagar: () -> Unit,
) {
    val baixador = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.allan.imagineer.ImagineerApp).baixadorDeLivros
    val estados by baixador.estados.collectAsState()
    val estado = estados[livroId] ?: EstadoDoDownload.NaoBaixado
    var dialogo by remember { mutableStateOf(false) }
    LaunchedEffect(livroId) { baixador.carregar(livroId) }
    MenuDoLivro(
        modifier = modifier,
        sobreACapa = sobreACapa,
        aoDefinirCapa = aoDefinirCapa,
        aoAbrirOffline = { dialogo = true },
        rotuloDoOffline = rotuloDoOffline(estado),
        aoApagar = aoApagar,
    )
    if (dialogo) DialogoDeDownload(livroId, baixador, estado, aoFechar = { dialogo = false })
}
