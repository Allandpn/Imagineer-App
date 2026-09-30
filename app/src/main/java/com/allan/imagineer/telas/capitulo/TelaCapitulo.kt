package com.allan.imagineer.telas.capitulo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.CapituloDetalhe
import com.allan.imagineer.telas.livro.descreverTamanho
import com.allan.imagineer.telas.livro.tituloDoCapitulo

/**
 * A tela de Capítulo (item 7.5), por enquanto só o leitor de texto (incremento 8).
 *
 * Só monta o ViewModel e entrega o estado para [ConteudoDoCapitulo] — a separação
 * permite pré-visualizar cada estado sem rede.
 */
@Composable
fun TelaCapitulo(
    capituloId: Int,
    aoVoltar: () -> Unit,
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: CapituloViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CapituloViewModel(capituloId, aplicacao.repositorioDeCapitulos) }
        },
    )
    val estado by viewModel.estado.collectAsState()

    // Carrega uma vez. Se a composição recomeçar (girar o tablet), o ViewModel já tem
    // o texto e a chamada não se repete.
    LaunchedEffect(viewModel) { viewModel.carregar() }

    ConteudoDoCapitulo(
        estado = estado,
        aoVoltar = aoVoltar,
        aoTentarDeNovo = viewModel::tentarDeNovo,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConteudoDoCapitulo(
    estado: EstadoDoCapitulo,
    aoVoltar: () -> Unit,
    aoTentarDeNovo: () -> Unit,
) {
    val titulo = (estado as? EstadoDoCapitulo.Pronto)?.capitulo
        ?.let { tituloDoCapitulo(it.titulo, it.ordem) }
        ?: "Capítulo"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { margens ->
        Box(modifier = Modifier.fillMaxSize().padding(margens)) {
            when (estado) {
                EstadoDoCapitulo.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                is EstadoDoCapitulo.Erro -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                    Column(
                        modifier = Modifier.widthIn(max = 600.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            estado.motivo,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = aoTentarDeNovo) { Text("Tentar de novo") }
                    }
                }

                is EstadoDoCapitulo.Pronto -> LeitorDeTexto(estado)
            }
        }
    }
}

/**
 * O texto do capítulo. Cada parágrafo é **um item de uma lista com rolagem
 * preguiçosa**: um único `Text` com ~110 KB seria medido e desenhado inteiro, mesmo
 * com quase tudo fora da tela.
 */
@Composable
private fun LeitorDeTexto(estado: EstadoDoCapitulo.Pronto) {
    val capitulo = estado.capitulo

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // O usuário vai querer copiar um trecho. A seleção não atravessa parágrafos
        // (cada um é um item da lista), mas dentro de um funciona.
        SelectionContainer(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { CabecalhoDoCapitulo(capitulo) }

                if (estado.paragrafos.isEmpty()) {
                    item {
                        Text(
                            "Este capítulo não tem texto.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                itemsIndexed(estado.paragrafos) { _, paragrafo ->
                    Text(
                        text = paragrafo,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            // Espaçamento de linha ampliado: leitura longa cansa menos.
                            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.6,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CabecalhoDoCapitulo(capitulo: CapituloDetalhe) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Capítulo ${capitulo.ordem} · ${descreverTamanho(capitulo.tamanho_do_texto)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (capitulo.ignorado) {
            // Abre-se um capítulo arquivado pela área de arquivados; a leitura não é bloqueada.
            Text(
                "Este capítulo está arquivado.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
