package com.allan.imagineer.telas.livro

import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.LocalContentColor
import com.allan.imagineer.telas.comum.BotaoDeIcone
import com.allan.imagineer.telas.comum.HostDeAvisos
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allan.imagineer.rede.CapituloResumo

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
    /** A barra de baixo do livro (LY1); nulo = sem a barra. */
    aoIrParaODoLivro: ((DestinoDoLivro) -> Unit)? = null,
) {
    val estado by viewModel.estado.collectAsState()
    val selecao by viewModel.selecao.collectAsState()
    val avisos = remember { SnackbarHostState() }

    // O botão voltar, no modo de seleção, cancela a seleção em vez de sair da tela.
    BackHandler(enabled = selecao != null) { viewModel.cancelarSelecao() }

    // Só uma tela do livro fica visível por vez: quem está na tela exibe os avisos.
    ExibirAvisos(viewModel, avisos)

    ConteudoDosArquivados(
        estado = estado,
        avisos = avisos,
        aoVoltar = aoVoltar,
        aoTentarDeNovo = viewModel::carregar,
        selecao = selecao,
        aoIniciarSelecao = viewModel::iniciarSelecao,
        aoAlternarSelecao = viewModel::alternarSelecao,
        aoCancelarSelecao = viewModel::cancelarSelecao,
        aoConfirmarSelecao = viewModel::confirmarSelecao,
        aoAlternarTodos = viewModel::alternarTodos,
        aoAbrirCapitulo = aoAbrirCapitulo,
        aoIrParaODoLivro = aoIrParaODoLivro,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConteudoDosArquivados(
    estado: EstadoDoLivro,
    avisos: SnackbarHostState,
    aoVoltar: () -> Unit,
    aoTentarDeNovo: () -> Unit,
    selecao: Selecao?,
    aoIniciarSelecao: (ModoDeSelecao, Int?) -> Unit,
    aoAlternarSelecao: (capituloId: Int) -> Unit,
    aoCancelarSelecao: () -> Unit,
    aoConfirmarSelecao: () -> Unit,
    aoAlternarTodos: () -> Unit,
    aoAbrirCapitulo: (capituloId: Int) -> Unit,
    aoIrParaODoLivro: ((DestinoDoLivro) -> Unit)? = null,
) {
    Scaffold(
        bottomBar = { aoIrParaODoLivro?.let { BarraDeNavegacaoDoLivro(DestinoDoLivro.ARQUIVADOS, it) } },
        snackbarHost = { HostDeAvisos(avisos) },
        topBar = {
            if (selecao != null) {
                BarraDeSelecao(
                    selecao = selecao,
                    rotuloDaAcao = "Restaurar",
                    todosMarcados = estado is EstadoDoLivro.Pronto && todosMarcados(estado, selecao),
                    aoAlternarTodos = aoAlternarTodos,
                    aoCancelar = aoCancelarSelecao,
                    aoConfirmar = aoConfirmarSelecao,
                )
            } else {
                TopAppBar(
                    title = { Text("Arquivados") },
                    navigationIcon = {
                        BotaoDeIcone(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", aoVoltar, cor = LocalContentColor.current)
                    },
                    actions = {
                        // Arquivar mais capítulos: volta à lista do livro já no modo de seleção para arquivar.
                        BotaoDeIcone(Icons.Filled.Archive, "Arquivar capítulos", aoTocar = { aoIniciarSelecao(ModoDeSelecao.ARQUIVAR, null); aoVoltar() }, cor = LocalContentColor.current)
                        // O botão só faz sentido se há capítulos arquivados para restaurar.
                        if (estado is EstadoDoLivro.Pronto && estado.livro.capitulos.any { it.ignorado }) {
                            BotaoDeIcone(Icons.Filled.Unarchive, "Restaurar capítulos", aoTocar = { aoIniciarSelecao(ModoDeSelecao.RESTAURAR, null) }, cor = LocalContentColor.current)
                        }
                    },
                )
            }
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
                                    LinhaDeCapitulo(
                                        capitulo = capitulo,
                                        emSelecao = selecao != null,
                                        marcado = selecao != null && capitulo.id in selecao.ids,
                                        ajustando = capitulo.id in estado.ajustando,
                                        // Fora do modo, tocar abre para leitura; no modo, marca ou desmarca.
                                        aoTocar = {
                                            if (selecao != null) aoAlternarSelecao(capitulo.id) else aoAbrirCapitulo(capitulo.id)
                                        },
                                        // Tocar e segurar entra no modo já com esta linha marcada.
                                        aoSegurar = {
                                            if (selecao == null) aoIniciarSelecao(ModoDeSelecao.RESTAURAR, capitulo.id)
                                        },
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
