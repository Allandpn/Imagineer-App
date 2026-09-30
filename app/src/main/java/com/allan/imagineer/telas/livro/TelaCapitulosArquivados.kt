package com.allan.imagineer.telas.livro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allan.imagineer.rede.CapituloResumo
import kotlinx.coroutines.launch

/**
 * A área de capítulos arquivados de um livro (item 7.5a, revisão do incremento 6).
 * Como as conversas arquivadas do WhatsApp: aqui ficam os capítulos que saíram da
 * lista principal, e de cada um se pode **ler** ou **restaurar**.
 *
 * Usa o mesmo [LivroViewModel] da tela de Livro (ver [livroViewModel]).
 */
@Composable
fun TelaCapitulosArquivados(
    aoVoltar: () -> Unit,
    aoAbrirCapitulo: (capituloId: Int) -> Unit,
    viewModel: LivroViewModel,
) {
    val estado by viewModel.estado.collectAsState()
    val avisos = remember { SnackbarHostState() }

    // Quem está na tela é quem recebe os avisos de falha (só uma tela do livro fica
    // visível por vez); o anterior é dispensado, como na tela de Livro.
    LaunchedEffect(viewModel) {
        viewModel.avisos.collect { texto ->
            avisos.currentSnackbarData?.dismiss()
            launch { avisos.showSnackbar(texto) }
        }
    }

    ConteudoDosArquivados(
        estado = estado,
        avisos = avisos,
        aoVoltar = aoVoltar,
        aoTentarDeNovo = viewModel::carregar,
        aoRestaurar = viewModel::restaurar,
        aoAbrirCapitulo = aoAbrirCapitulo,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConteudoDosArquivados(
    estado: EstadoDoLivro,
    avisos: SnackbarHostState,
    aoVoltar: () -> Unit,
    aoTentarDeNovo: () -> Unit,
    aoRestaurar: (capituloId: Int) -> Unit,
    aoAbrirCapitulo: (capituloId: Int) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(avisos) },
        topBar = {
            TopAppBar(
                title = { Text("Arquivados") },
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
                EstadoDoLivro.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                is EstadoDoLivro.Erro -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                    Column(
                        modifier = Modifier.widthIn(max = 600.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            estado.motivo,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = aoTentarDeNovo) { Text("Tentar de novo") }
                    }
                }

                is EstadoDoLivro.Pronto -> {
                    val arquivados = estado.livro.capitulos.filter { it.ignorado }
                    if (arquivados.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                            Text(
                                "Nenhum capítulo arquivado.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
                            LazyColumn(
                                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 8.dp),
                            ) {
                                items(arquivados, key = { it.id }) { capitulo ->
                                    LinhaDeArquivado(
                                        capitulo = capitulo,
                                        restaurando = capitulo.id in estado.ajustando,
                                        aoRestaurar = { aoRestaurar(capitulo.id) },
                                        aoAbrir = { aoAbrirCapitulo(capitulo.id) },
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Uma linha de capítulo arquivado. O título abre o capítulo para leitura; o botão de
 * restaurar fica numa área separada, para um toque impreciso não fazer uma coisa no
 * lugar da outra.
 */
@Composable
private fun LinhaDeArquivado(
    capitulo: CapituloResumo,
    restaurando: Boolean,
    aoRestaurar: () -> Unit,
    aoAbrir: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = aoAbrir)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                tituloDoCapitulo(capitulo.titulo, capitulo.ordem),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                descreverTamanho(capitulo.tamanho_do_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(modifier = Modifier.padding(end = 4.dp).size(48.dp), contentAlignment = Alignment.Center) {
            if (restaurando) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = aoRestaurar) {
                    Icon(Icons.Filled.Unarchive, contentDescription = "Restaurar capítulo")
                }
            }
        }
    }
}
