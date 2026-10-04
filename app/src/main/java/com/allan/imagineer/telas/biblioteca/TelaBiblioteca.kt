package com.allan.imagineer.telas.biblioteca

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import com.allan.imagineer.telas.menu.ModoDaBiblioteca
import com.allan.imagineer.telas.menu.ItemDoMenu
import com.allan.imagineer.telas.menu.GavetaDaBiblioteca
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.allan.imagineer.telas.comum.DialogoDeRemocao
import com.allan.imagineer.telas.comum.EstadoDaRemocao
import com.allan.imagineer.telas.importacao.DialogosDeImportacao
import com.allan.imagineer.telas.importacao.ImportacaoViewModel

/**
 * A Biblioteca (item 7.2): a tela inicial, com a lista de livros importados.
 *
 * Só monta o ViewModel e entrega o estado para [ConteudoDaBiblioteca] — a
 * separação permite pré-visualizar cada estado sem rede.
 */
@Composable
fun TelaBiblioteca(
    aoAbrirLivro: (livroId: Int) -> Unit,
    aoAbrirPerfil: () -> Unit,
    aoAbrirConfiguracoes: () -> Unit,
    aoAbrirLixeira: () -> Unit,
    aoAbrirCustos: () -> Unit,
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: BibliotecaViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BibliotecaViewModel(aplicacao.repositorioDeLivros) }
        },
    )
    val estado by viewModel.estado.collectAsState()
    val remocao by viewModel.remocao.collectAsState()

    val importacao: ImportacaoViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ImportacaoViewModel(aplicacao.repositorioDeLivros, aplicacao.leitorDeArquivos) }
        },
    )
    val estadoDaImportacao by importacao.estado.collectAsState()
    val livroParaAbrir by importacao.irParaLivro.collectAsState()
    val versaoDaBiblioteca by importacao.versaoDaBiblioteca.collectAsState()
    val modoGuardado by aplicacao.armazenamento.modoDaBiblioteca.collectAsState(initial = null)
    val escopoDoModo = rememberCoroutineScope()

    // O seletor de arquivos do sistema. Aceita "octet-stream" também: alguns
    // gerenciadores de arquivos classificam EPUB assim, e com o filtro estrito o
    // arquivo apareceria acinzentado. A extensão é conferida no ViewModel.
    val seletorDeArquivo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> importacao.escolherArquivo(uri?.toString()) }

    // Importação concluída: abre o livro. O evento é de uso único.
    LaunchedEffect(livroParaAbrir) {
        livroParaAbrir?.let { livroId ->
            aoAbrirLivro(livroId)
            importacao.consumirNavegacao()
        }
    }

    // O servidor mudou (livro criado, removido, ajustado): recarrega a lista, mesmo
    // que o fluxo termine sem navegar.
    LaunchedEffect(versaoDaBiblioteca) {
        if (versaoDaBiblioteca > 0) viewModel.carregar()
    }

    // Recarrega toda vez que a tela volta a ficar visível, não só na primeira
    // vez: é o que mostra o livro recém-importado (ou removido) sem ação do
    // usuário. Também cobre a primeira abertura.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    ConteudoDaBiblioteca(
        estado = estado,
        remocao = remocao,
        aoAtualizar = viewModel::carregar,
        aoAbrirLivro = aoAbrirLivro,
        aoAbrirMenu = { item ->
            when (item) {
                ItemDoMenu.PERFIL -> aoAbrirPerfil()
                ItemDoMenu.CONFIGURACOES -> aoAbrirConfiguracoes()
                ItemDoMenu.LIXEIRA -> aoAbrirLixeira()
                ItemDoMenu.CUSTOS -> aoAbrirCustos()
            }
        },
        modo = ModoDaBiblioteca.deTexto(modoGuardado),
        aoAlternarModo = {
            val novo = if (ModoDaBiblioteca.deTexto(modoGuardado) == ModoDaBiblioteca.CAPAS) ModoDaBiblioteca.LISTA else ModoDaBiblioteca.CAPAS
            escopoDoModo.launch { aplicacao.armazenamento.salvarModoDaBiblioteca(novo.name) }
        },
        aoPedirRemocao = viewModel::pedirRemocao,
        aoCancelarRemocao = viewModel::cancelarRemocao,
        aoConfirmarRemocao = viewModel::confirmarRemocao,
        aoImportar = {
            seletorDeArquivo.launch(arrayOf("application/epub+zip", "application/octet-stream"))
        },
    )

    DialogosDeImportacao(
        estado = estadoDaImportacao,
        aoTentarDeNovo = importacao::tentarDeNovo,
        aoFechar = importacao::fechar,
        aoSeguirMesmoAssim = importacao::seguirMesmoAssim,
        aoAbrirExistente = importacao::abrirExistente,
        aoRemoverONovo = importacao::removerONovo,
        aoSalvarMetadados = importacao::salvarMetadados,
        aoRemoverLivroDoFormulario = importacao::removerLivroDoFormulario,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConteudoDaBiblioteca(
    estado: EstadoDaBiblioteca,
    remocao: EstadoDaRemocao,
    aoAtualizar: () -> Unit,
    aoAbrirLivro: (livroId: Int) -> Unit,
    aoAbrirMenu: (ItemDoMenu) -> Unit,
    modo: ModoDaBiblioteca,
    aoAlternarModo: () -> Unit,
    aoPedirRemocao: (LivroResumo) -> Unit,
    aoCancelarRemocao: () -> Unit,
    aoConfirmarRemocao: () -> Unit,
    aoImportar: () -> Unit,
) {
    // MN1, MN2: a barra só tem o hambúrguer; tudo o mais mora na gaveta.
    val gaveta = rememberDrawerState(DrawerValue.Closed)
    val escopo = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = gaveta,
        drawerContent = {
            GavetaDaBiblioteca { item ->
                escopo.launch { gaveta.close() }
                aoAbrirMenu(item)
            }
        },
    ) {
    Scaffold(
        floatingActionButton = {
            // Visível em todos os estados, inclusive Vazia e Erro: escolher o arquivo é
            // o primeiro passo do fluxo, e o estado vazio ("Importe um EPUB") não
            // teria como cumprir o que diz sem ele.
            ExtendedFloatingActionButton(
                onClick = aoImportar,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Importar") },
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("Biblioteca") },
                navigationIcon = {
                    IconButton(onClick = { escopo.launch { gaveta.open() } }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    // Capas ou lista: o mesmo modo que a tela de Configurações guarda.
                    IconButton(onClick = aoAlternarModo) {
                        Icon(
                            if (modo == ModoDaBiblioteca.CAPAS) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (modo == ModoDaBiblioteca.CAPAS) "Ver em lista" else "Ver em capas",
                        )
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
                    if (modo == ModoDaBiblioteca.CAPAS) {
                        GradeDeLivros(estado.livros, aoAbrirLivro, aoPedirRemocao)
                    } else {
                        ListaDeLivros(estado.livros, aoAbrirLivro, aoPedirRemocao)
                    }
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

    DialogoDeRemocao(remocao, aoCancelarRemocao, aoConfirmarRemocao)
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
    aoPedirRemocao: (LivroResumo) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(livros, key = { it.id }) { livro ->
                CartaoDeLivro(
                    livro,
                    aoTocar = { aoAbrirLivro(livro.id) },
                    aoPedirRemocao = { aoPedirRemocao(livro) },
                )
            }
        }
    }
}

@Composable
private fun CartaoDeLivro(
    livro: LivroResumo,
    aoTocar: () -> Unit,
    aoPedirRemocao: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = aoTocar)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(livro.titulo, style = MaterialTheme.typography.titleMedium)
                // O autor na mesma fonte da tela do livro (serifada, itálico).
                Text(
                    livro.autor ?: "Autor desconhecido",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                )
                Text(
                    resumoDoLivroNaLista(livro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MenuDoCartao(aoPedirRemocao)
        }
    }
}

/** O menu de três pontos (⋮) do cartão. Hoje só tem "Remover". */
@Composable
private fun MenuDoCartao(aoPedirRemocao: () -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { aberto = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            DropdownMenuItem(
                text = { Text("Remover") },
                onClick = {
                    aberto = false
                    aoPedirRemocao()
                },
            )
        }
    }
}
