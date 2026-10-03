package com.allan.imagineer.telas.lixeira

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.ImagemNaLixeira
import com.allan.imagineer.rede.enderecoDaImagem
import com.allan.imagineer.telas.capitulo.painel.urlDoServidorEmUso

/**
 * A **Lixeira** (item 7.5b, LX8): as imagens apagadas, que continuam no servidor até o usuário apagá-las de vez. Cada uma tem
 * **Restaurar** e **Apagar de vez** (com confirmação); no alto, **Esvaziar lixeira** (com confirmação que diz quanto espaço
 * vai embora). Nada some sozinho (LX6).
 */
@Composable
fun TelaLixeira(aoVoltar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: LixeiraViewModel = viewModel(
        factory = viewModelFactory { initializer { LixeiraViewModel(aplicacao.repositorioDaLixeira) } },
    )
    val estado by viewModel.estado.collectAsState()
    // Relê toda vez que a tela fica visível: o que se apagou nos capítulos aparece aqui.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    ConteudoDaLixeira(
        estado = estado,
        aoVoltar = aoVoltar,
        aoTentarDeNovo = viewModel::tentarDeNovo,
        aoRestaurar = viewModel::restaurar,
        aoPedirApagarDeVez = viewModel::pedirApagarDeVez,
        aoPedirEsvaziar = viewModel::pedirEsvaziar,
        aoConfirmar = viewModel::confirmar,
        aoCancelar = viewModel::cancelarConfirmacao,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConteudoDaLixeira(
    estado: EstadoDaLixeira,
    aoVoltar: () -> Unit,
    aoTentarDeNovo: () -> Unit,
    aoRestaurar: (Int) -> Unit,
    aoPedirApagarDeVez: (ImagemNaLixeira) -> Unit,
    aoPedirEsvaziar: () -> Unit,
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
) {
    val pronta = (estado.carga as? CargaDaLixeira.Pronta)?.lixeira
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lixeira") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                actions = {
                    if (pronta != null && pronta.imagens.isNotEmpty()) {
                        TextButton(onClick = aoPedirEsvaziar, enabled = !estado.esvaziando) { Text("Esvaziar") }
                    }
                },
            )
        },
    ) { margens ->
        Box(modifier = Modifier.fillMaxSize().padding(margens), contentAlignment = Alignment.TopCenter) {
            when (val carga = estado.carga) {
                CargaDaLixeira.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is CargaDaLixeira.Erro -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(carga.motivo, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = aoTentarDeNovo) { Text("Tentar de novo") }
                }
                is CargaDaLixeira.Pronta -> if (carga.lixeira.imagens.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                        Text(TEXTO_DA_LIXEIRA_VAZIA, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    ListaDaLixeira(carga.lixeira.imagens, carga.lixeira.total_em_bytes, estado, aoRestaurar, aoPedirApagarDeVez)
                }
            }
        }
    }

    estado.confirmacao?.let { pedido ->
        val (titulo, texto, rotulo) = when (pedido) {
            is ConfirmacaoDaLixeira.ApagarUma -> Triple("Apagar de vez esta imagem?", AVISO_APAGAR_DE_VEZ, "Apagar de vez")
            is ConfirmacaoDaLixeira.EsvaziarTudo -> Triple("Esvaziar a lixeira?", avisoDeEsvaziar(pedido.quantas, pedido.bytes), "Esvaziar")
        }
        AlertDialog(
            onDismissRequest = aoCancelar,
            title = { Text(titulo) },
            text = { Text(texto) },
            confirmButton = { TextButton(onClick = aoConfirmar) { Text(rotulo, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ListaDaLixeira(
    imagens: List<ImagemNaLixeira>,
    totalEmBytes: Long,
    estado: EstadoDaLixeira,
    aoRestaurar: (Int) -> Unit,
    aoPedirApagarDeVez: (ImagemNaLixeira) -> Unit,
) {
    val urlBase = urlDoServidorEmUso()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(resumoDaLixeira(imagens.size, totalEmBytes), style = MaterialTheme.typography.titleMedium)
                Text(
                    "Nada daqui some sozinho: as imagens ficam até você apagá-las de vez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                estado.recado?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (estado.recadoEhErro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        items(imagens, key = { it.id }) { imagem ->
            val ocupada = imagem.id in estado.ocupadas || estado.esvaziando
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (urlBase != null) {
                        AsyncImage(
                            model = enderecoDaImagem(urlBase, imagem.id, "miniatura"),
                            contentDescription = "Imagem apagada: ${nomeDoFrameNaLixeira(imagem)}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black),
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(legendaDaLixeira(imagem), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { aoRestaurar(imagem.id) }, enabled = !ocupada) { Text("Restaurar", maxLines = 1, softWrap = false) }
                            OutlinedButton(onClick = { aoPedirApagarDeVez(imagem) }, enabled = !ocupada) {
                                Text("Apagar de vez", maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
