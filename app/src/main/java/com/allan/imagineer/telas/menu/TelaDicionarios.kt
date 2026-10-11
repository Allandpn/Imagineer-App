package com.allan.imagineer.telas.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.DicionarioDoServidor

/** A explicação no alto da tela (RL28): para que servem a ordem e o interruptor. */
const val EXPLICACAO_DOS_DICIONARIOS =
    "Ao tocar numa palavra do livro, os dicionários ligados aparecem na ordem desta lista: o de cima vem primeiro. " +
        "Um dicionário desligado nunca é consultado. Cada livro usa os dicionários do idioma dele."

/** A tela **Dicionários** das Configurações (RL28): liga e desliga cada dicionário e escolhe a ordem de preferência. Grava a cada toque. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDicionarios(aoVoltar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: DicionariosViewModel = viewModel(
        factory = viewModelFactory { initializer { DicionariosViewModel(aplicacao.repositorioDeDicionario) } },
    )
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(viewModel) { viewModel.carregar() }
    // CF2: a lista, qual dicionário está sendo arrastado e quanto (px) a linha já andou.
    val lista = rememberLazyListState()
    var arrastando by remember { mutableStateOf<String?>(null) }
    var deslocamento by remember { mutableFloatStateOf(0f) }
    val contexto = LocalContext.current
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let { android.widget.Toast.makeText(contexto, it, android.widget.Toast.LENGTH_LONG).show(); viewModel.avisoLido() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dicionários") },
                navigationIcon = { IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { margens ->
        Box(modifier = Modifier.padding(margens).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val carga = estado.carga) {
                CargaDosDicionarios.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is CargaDosDicionarios.Erro -> Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(carga.motivo, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = viewModel::tentarDeNovo, modifier = Modifier.padding(top = 12.dp)) { Text("Tentar de novo") }
                }
                is CargaDosDicionarios.Pronta -> LazyColumn(
                    state = lista,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Text(EXPLICACAO_DOS_DICIONARIOS, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (carga.dicionarios.isEmpty()) {
                        item {
                            Text(
                                "O servidor não encontrou nenhum dicionário na pasta dele.",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                    itemsIndexed(carga.dicionarios, key = { _, d -> d.id }) { _, d ->
                        val sendoArrastado = arrastando == d.id
                        LinhaDoDicionario(
                            dicionario = d,
                            aoAlternar = { viewModel.alternar(d.id) },
                            // CF2: a linha acompanha o dedo (e fica por cima das outras) enquanto a alça é arrastada.
                            modifier = Modifier
                                .zIndex(if (sendoArrastado) 1f else 0f)
                                .graphicsLayer { translationY = if (sendoArrastado) deslocamento else 0f },
                            alca = Modifier.pointerInput(d.id) {
                                detectDragGestures(
                                    onDragStart = { arrastando = d.id; deslocamento = 0f },
                                    onDrag = { alteracao, arrasto -> alteracao.consume(); deslocamento += arrasto.y },
                                    onDragEnd = {
                                        // Soltou: vê sobre qual linha caiu e leva o dicionário para lá (uma gravação só).
                                        val linhas = lista.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                                            (info.key as? String)?.let { LinhaNaTela(it, info.offset, info.size) }
                                        }
                                        alvoDoArrasto(linhas, d.id, deslocamento)?.let { idAlvo ->
                                            val posicao = carga.dicionarios.indexOfFirst { it.id == idAlvo }
                                            if (posicao >= 0) viewModel.moverPara(d.id, posicao)
                                        }
                                        arrastando = null; deslocamento = 0f
                                    },
                                    onDragCancel = { arrastando = null; deslocamento = 0f },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaDoDicionario(
    dicionario: DicionarioDoServidor,
    aoAlternar: () -> Unit,
    modifier: Modifier = Modifier,
    /** O modificador de arrastar, posto só na alça (CF2): arrastar o resto da linha continua rolando a lista. */
    alca: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    dicionario.nome,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (dicionario.ativo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(usoDoDicionario(dicionario), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = dicionario.ativo, onCheckedChange = { aoAlternar() }, modifier = Modifier.padding(horizontal = 8.dp))
            // CF2: a alça de arrastar; segurar e mover muda a ordem de preferência.
            Box(modifier = alca.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Icon(Icons.Filled.DragHandle, contentDescription = "Arrastar ${dicionario.nome} para mudar a ordem")
            }
        }
    }
}
