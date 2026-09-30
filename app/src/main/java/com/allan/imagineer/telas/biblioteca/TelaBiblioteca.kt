package com.allan.imagineer.telas.biblioteca

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.LivroResumo

/**
 * A Biblioteca (item 7.2): a tela inicial, com a lista de livros importados.
 *
 * Só monta o ViewModel e entrega o estado para [ConteudoDaBiblioteca] — a
 * separação permite pré-visualizar cada estado sem rede.
 */
@Composable
fun TelaBiblioteca(
    aoAbrirLivro: (livroId: Int) -> Unit,
    aoAbrirConfiguracao: () -> Unit,
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: BibliotecaViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BibliotecaViewModel(aplicacao.repositorioDeLivros) }
        },
    )
    val estado by viewModel.estado.collectAsState()

    // Recarrega toda vez que a tela volta a ficar visível, não só na primeira
    // vez: é o que mostra o livro recém-importado (ou removido) sem ação do
    // usuário. Também cobre a primeira abertura.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    ConteudoDaBiblioteca(
        estado = estado,
        aoAtualizar = viewModel::carregar,
        aoAbrirLivro = aoAbrirLivro,
        aoAbrirConfiguracao = aoAbrirConfiguracao,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConteudoDaBiblioteca(
    estado: EstadoDaBiblioteca,
    aoAtualizar: () -> Unit,
    aoAbrirLivro: (livroId: Int) -> Unit,
    aoAbrirConfiguracao: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biblioteca") },
                actions = {
                    IconButton(onClick = aoAbrirConfiguracao) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configuração")
                    }
                },
            )
        },
    ) { margens ->
        Box(modifier = Modifier.fillMaxSize().padding(margens)) {
            when (estado) {
                EstadoDaBiblioteca.Carregando -> Centralizado {
                    CircularProgressIndicator()
                }

                is EstadoDaBiblioteca.Lista -> PullToRefreshBox(
                    isRefreshing = estado.atualizando,
                    onRefresh = aoAtualizar,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ListaDeLivros(estado.livros, aoAbrirLivro)
                }

                EstadoDaBiblioteca.Vazia -> Centralizado {
                    Text(
                        "Nenhum livro ainda.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Importe um EPUB para começar a catalogar.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                is EstadoDaBiblioteca.Erro -> Centralizado {
                    Text(
                        estado.motivo,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = aoAtualizar) { Text("Tentar de novo") }
                }
            }
        }
    }
}

/** Conteúdo no meio da tela, com largura máxima (o alvo de teste é um tablet). */
@Composable
private fun Centralizado(conteudo: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            conteudo()
        }
    }
}

@Composable
private fun ListaDeLivros(
    livros: List<LivroResumo>,
    aoAbrirLivro: (livroId: Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(livros, key = { it.id }) { livro ->
                CartaoDeLivro(livro, aoTocar = { aoAbrirLivro(livro.id) })
            }
        }
    }
}

@Composable
private fun CartaoDeLivro(livro: LivroResumo, aoTocar: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = aoTocar)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(livro.titulo, style = MaterialTheme.typography.titleMedium)
            Text(
                livro.autor ?: "Autor desconhecido",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                descreverCapitulos(livro.total_de_capitulos, livro.capitulos_ignorados),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
