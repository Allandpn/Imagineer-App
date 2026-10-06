package com.allan.imagineer.telas.livro

import com.allan.imagineer.telas.comum.BotaoDeIcone
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SmallFloatingActionButton
import com.allan.imagineer.telas.comum.HostDeAvisos
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.PerfilRenderizacao
import com.allan.imagineer.telas.comum.DialogoDeRemocao
import com.allan.imagineer.telas.biblioteca.descreverCapitulos
import com.allan.imagineer.telas.importacao.nomesDosCampos

/**
 * O [LivroViewModel] de um livro. Sem [dono], fica preso à tela que o pede; passando
 * o [dono] (a entrada da tela de Livro na pilha de navegação), a área de arquivados
 * **compartilha o mesmo ViewModel** da tela de Livro — arquivar numa e restaurar na
 * outra sempre enxergam o mesmo livro, sem recarregar nem ficar defasado.
 */
@Composable
fun livroViewModel(livroId: Int, dono: ViewModelStoreOwner? = null): LivroViewModel {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val fabrica = viewModelFactory {
        initializer {
            LivroViewModel(
                livroId,
                aplicacao.repositorioDeLivros,
                aplicacao.repositorioDeCapitulos,
                aplicacao.repositorioDePerfis,
                aplicacao.repositorioDeMarcador,
            )
        }
    }
    return if (dono != null) {
        viewModel(viewModelStoreOwner = dono, factory = fabrica)
    } else {
        viewModel(factory = fabrica)
    }
}

/**
 * Mostra os avisos do [LivroViewModel] no Snackbar. Usado pela tela de Livro e pela de
 * Arquivados (que compartilham o ViewModel): quem está na tela é quem exibe.
 *
 * - Cada aviso novo **dispensa o anterior**: o Snackbar enfileira, e vários toques
 *   seguidos empilhariam avisos de vários segundos cada. Por isso só o último
 *   arquivamento fica desfazível pelo aviso.
 * - Com ação, a duração é **`Long` (10 s)**: o padrão do Material 3 para um aviso com
 *   ação é indefinido, e um "Desfazer" que fica na tela até alguém tocar é o oposto do
 *   que se quer.
 * - "Desfazer" restaura **exatamente** os capítulos do lote que gerou o aviso.
 */
@Composable
fun ExibirAvisos(viewModel: LivroViewModel, avisos: SnackbarHostState) {
    LaunchedEffect(viewModel) {
        viewModel.avisos.collect { aviso ->
            avisos.currentSnackbarData?.dismiss()
            launch {
                val comAcao = aviso.desfazerCapitulos.isNotEmpty()
                val resultado = avisos.showSnackbar(
                    message = aviso.texto,
                    actionLabel = if (comAcao) "Desfazer" else null,
                    duration = if (comAcao) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (resultado == SnackbarResult.ActionPerformed && comAcao) {
                    // Restaura exatamente os capítulos daquele lote.
                    viewModel.desfazerArquivamento(aviso.desfazerCapitulos)
                }
            }
        }
    }
}

/**
 * A tela de Livro (item 7.4): metadados, lista dos capítulos **ativos** e o acesso à
 * área de arquivados.
 *
 * Só entrega o estado do ViewModel para [ConteudoDoLivro] — a separação permite
 * pré-visualizar cada estado sem rede.
 */
@Composable
fun TelaLivro(
    livroId: Int,
    aoVoltar: () -> Unit,
    aoAbrirCapitulo: (capituloId: Int) -> Unit,
    /** A barra de baixo do livro (LY1): o ícone tocado. */
    aoIrParaODoLivro: (DestinoDoLivro) -> Unit = {},
    aoAbrirPesquisa: () -> Unit = {},
    aoAbrirLixeira: (() -> Unit)? = null,
    aoAbrirDestaques: (() -> Unit)? = null,
    aoAbrirFavoritos: (() -> Unit)? = null,
    aoAbrirPins: (() -> Unit)? = null,
    /** LE3: abre o capítulo onde a pessoa parou (a posição nula abre do começo). */
    aoContinuarLendo: (capituloId: Int, posicao: Int?) -> Unit = { _, _ -> },
    viewModel: LivroViewModel = livroViewModel(livroId),
) {
    // CP5: "Definir capa…" abre o seletor de arquivos (uma imagem ou o EPUB do livro).
    val aplicacaoDaCapa = LocalContext.current.applicationContext as ImagineerApp
    val seletorDeCapa = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.definirCapa(aplicacaoDaCapa.leitorDeArquivos.descrever(uri.toString()))
    }
    val estado by viewModel.estado.collectAsState()
    val edicao by viewModel.edicao.collectAsState()
    val escolhaDePerfil by viewModel.escolhaDePerfil.collectAsState()
    val remocao by viewModel.remocao.collectAsState()
    val selecao by viewModel.selecao.collectAsState()
    val avisos = remember { SnackbarHostState() }
    val marcador by viewModel.marcador.collectAsState()

    // PL3 a PL5: o download para ler offline. Ao abrir, lê se o livro já está baixado e, com Wi-Fi, completa o que faltar.
    val baixador = aplicacaoDaCapa.baixadorDeLivros
    val downloads by baixador.estados.collectAsState()
    val download = downloads[livroId] ?: com.allan.imagineer.local.EstadoDoDownload.NaoBaixado
    var dialogoDeDownload by remember { mutableStateOf(false) }
    val scopeDaCopia = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(livroId) {
        baixador.carregar(livroId)
        baixador.completarSeBaixado(livroId)
    }

    // O botão voltar do aparelho, no modo de seleção, cancela a seleção em vez de sair
    // da tela.
    BackHandler(enabled = selecao != null) { viewModel.cancelarSelecao() }

    // Recarrega toda vez que a tela volta a ficar visível: ao voltar de um capítulo,
    // as sugestões pendentes podem ter mudado. Cobre também a primeira abertura.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    ExibirAvisos(viewModel, avisos)

    // O livro foi apagado: a tela não tem mais o que mostrar, volta para a Biblioteca.
    LaunchedEffect(viewModel) {
        viewModel.livroRemovido.collect { aoVoltar() }
    }

    // RL36: a estrela do livro, na barra de cima, lê os favoritos do livro.
    com.allan.imagineer.telas.favoritos.ComFavoritosDoLivro(livroId) {
    ConteudoDoLivro(
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
        aoIrParaODoLivro = aoIrParaODoLivro,
        aoAbrirCapitulo = aoAbrirCapitulo,
        aoAbrirPesquisa = aoAbrirPesquisa,
        aoAbrirLixeira = aoAbrirLixeira,
        aoAbrirDestaques = aoAbrirDestaques,
        aoAbrirFavoritos = aoAbrirFavoritos,
        aoAbrirPins = aoAbrirPins,
        aoApagarCopiaLocal = { viewModel.apagarCopiaLocal(); scopeDaCopia.launch { baixador.remover(livroId) } },
        aoAlternarLido = viewModel::alternarLido,
        marcador = marcador,
        aoContinuarLendo = aoContinuarLendo,
        estadoDoDownload = download,
        aoAbrirOffline = { dialogoDeDownload = true },
        aoEditar = viewModel::abrirEdicao,
        aoApagar = viewModel::pedirRemocao,
    )
    }

    if (dialogoDeDownload) DialogoDeDownload(livroId, baixador, download, aoFechar = { dialogoDeDownload = false })

    val livro = (estado as? EstadoDoLivro.Pronto)?.livro
    if (livro != null && edicao is EstadoDaEdicao.Editando) {
        DialogoDeEdicao(
            livro = livro,
            estado = edicao as EstadoDaEdicao.Editando,
            aoSalvar = viewModel::salvarEdicao,
            aoCancelar = viewModel::cancelarEdicao,
            perfil = (estado as? EstadoDoLivro.Pronto)?.perfil,
            aoDefinirCapa = { seletorDeCapa.launch(arrayOf("image/*", "application/epub+zip", "application/octet-stream")) },
            aoEscolherPerfil = viewModel::abrirEscolhaDePerfil,
        )
    }
    DialogoDePerfilPadrao(
        estado = escolhaDePerfil,
        perfilAtualId = livro?.perfil_renderizacao_padrao_id,
        aoEscolher = viewModel::escolherPerfil,
        aoFechar = viewModel::fecharEscolhaDePerfil,
    )
    DialogoDeRemocao(remocao, viewModel::cancelarRemocao, viewModel::confirmarRemocao)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConteudoDoLivro(
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
    aoIrParaODoLivro: (DestinoDoLivro) -> Unit = {},
    aoAbrirCapitulo: (capituloId: Int) -> Unit,
    aoAbrirPesquisa: () -> Unit = {},
    aoAbrirLixeira: (() -> Unit)? = null,
    aoAbrirDestaques: (() -> Unit)? = null,
    aoAbrirFavoritos: (() -> Unit)? = null,
    aoAbrirPins: (() -> Unit)? = null,
    aoApagarCopiaLocal: () -> Unit = {},
    aoAlternarLido: (capituloId: Int) -> Unit = {},
    marcador: com.allan.imagineer.rede.Marcador? = null,
    aoContinuarLendo: (capituloId: Int, posicao: Int?) -> Unit = { _, _ -> },
    estadoDoDownload: com.allan.imagineer.local.EstadoDoDownload = com.allan.imagineer.local.EstadoDoDownload.NaoBaixado,
    aoAbrirOffline: (() -> Unit)? = null,
    aoEditar: () -> Unit,
    aoApagar: () -> Unit,
) {
    var metadadosAbertos by remember { mutableStateOf(false) }
    val pronto = estado as? EstadoDoLivro.Pronto
    if (metadadosAbertos && pronto != null) {
        DialogoDosMetadados(
            livro = pronto.livro,
            perfil = pronto.perfil,
            aoFechar = { metadadosAbertos = false },
            baixado = estadoDoDownload as? com.allan.imagineer.local.EstadoDoDownload.Baixado,
        )
    }
    val continuar = pronto?.let { continuarLendo(it.livro, marcador) }
    Scaffold(
        snackbarHost = { HostDeAvisos(avisos) },
        // LY1: a barra de navegação do livro (só ícones); some no modo de seleção, que tem a barra dele.
        bottomBar = { if (selecao == null && pronto != null) BarraDeNavegacaoDoLivro(selecionado = null, aoIr = aoIrParaODoLivro) },
        // LY3: "Continuar lendo" é um botão pequeno, só com o ícone, no canto de baixo.
        floatingActionButton = {
            if (selecao == null && continuar != null) {
                SmallFloatingActionButton(
                    onClick = { aoContinuarLendo(continuar.capituloId, continuar.posicao) },
                    shape = androidx.compose.foundation.shape.CircleShape, // redondo, como os outros botões (os menus é que são quadrados)
                ) {
                    Icon(IconesDaTelaDoLivro.continuar, contentDescription = continuar.rotulo)
                }
            }
        },
        topBar = {
            if (selecao != null) {
                // Modo de seleção: enquanto seleciona, as outras ações ficam indisponíveis.
                BarraDeSelecao(
                    selecao = selecao,
                    rotuloDaAcao = "Arquivar",
                    todosMarcados = estado is EstadoDoLivro.Pronto && todosMarcados(estado, selecao),
                    aoAlternarTodos = aoAlternarTodos,
                    aoCancelar = aoCancelarSelecao,
                    aoConfirmar = aoConfirmarSelecao,
                )
            } else {
                TopAppBar(
                    title = { Text("Livro") },
                    navigationIcon = {
                        IconButton(onClick = aoVoltar) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    actions = {
                        // Só com o livro na tela: os atalhos não fazem sentido em erro.
                        if (estado is EstadoDoLivro.Pronto) {
                            // LY2, LY5: no topo, só o ⋮. Elementos, Cenas, Pendências e Arquivados são a barra de baixo (LY1);
                            // os metadados estão no cabeçalho (LY4); pesquisar mora aqui (LY3).
                            // As informações ficam junto do ⋮, na cor neutra da barra (como os outros ícones dela).
                            com.allan.imagineer.telas.favoritos.BotaoDeFavorito(com.allan.imagineer.rede.AlvoDeFavorito.Livro, cor = LocalContentColor.current)
                            BotaoDeIcone(IconesDaTelaDoLivro.metadados, "Informações", { metadadosAbertos = true }, cor = LocalContentColor.current)
                            // Capa e perfil de renderização moram dentro de "Editar" (DialogoDeEdicao), não mais neste menu.
                            MenuDoLivro(
                                aoPesquisar = aoAbrirPesquisa,
                                aoAbrirOffline = aoAbrirOffline,
                                rotuloDoOffline = rotuloDoOffline(estadoDoDownload),
                                aoEditar = aoEditar,
                                aoAbrirLixeira = aoAbrirLixeira,
                                aoAbrirDestaques = aoAbrirDestaques,
                                aoAbrirFavoritos = aoAbrirFavoritos,
                                aoAbrirPins = aoAbrirPins,
                                aoApagar = aoApagar,
                            )
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
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = aoTentarDeNovo) { Text("Tentar de novo") }
                        // PL11: o servidor não tem mais o livro, mas o aparelho ainda tem uma cópia: oferece apagá-la (nunca apaga sozinho).
                        if (estado.ofereceApagarCopia) {
                            Text(
                                "O servidor não tem mais este livro, mas ainda há uma cópia dele neste aparelho.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            androidx.compose.material3.OutlinedButton(onClick = aoApagarCopiaLocal) { Text("Apagar a cópia do aparelho") }
                        }
                    }
                }

                is EstadoDoLivro.Pronto -> ListaDoLivro(
                    estado = estado,
                    selecao = selecao,
                    aoIniciarSelecao = aoIniciarSelecao,
                    aoAlternarSelecao = aoAlternarSelecao,
                    aoAbrirCapitulo = aoAbrirCapitulo,
                    aoAlternarLido = aoAlternarLido,
                )
            }
        }
    }
}

@Composable
private fun ListaDoLivro(
    estado: EstadoDoLivro.Pronto,
    selecao: Selecao?,
    aoIniciarSelecao: (ModoDeSelecao, Int?) -> Unit,
    aoAlternarSelecao: (Int) -> Unit,
    aoAbrirCapitulo: (Int) -> Unit,
    aoAlternarLido: (Int) -> Unit,
) {
    val livro = estado.livro
    // A lista principal mostra só os ativos; os arquivados vivem na área própria.
    val ativos = livro.capitulos.filter { !it.ignorado }
    val arquivados = livro.capitulos.count { it.ignorado }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { CabecalhoDoLivro(livro) }

            if (ativos.isEmpty()) {
                item {
                    Text(
                        if (arquivados > 0) "Todos os capítulos estão arquivados." else "Este livro não tem capítulos.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            itemsIndexed(ativos, key = { _, capitulo -> capitulo.id }) { indice, capitulo ->
                // Sem divisores nem faixas: a separação vem só do espaço.
                LinhaDeCapitulo(
                    capitulo = capitulo,
                    emSelecao = selecao != null,
                    marcado = selecao != null && capitulo.id in selecao.ids,
                    ajustando = capitulo.id in estado.ajustando,
                    // Fora do modo, tocar abre; no modo, tocar marca ou desmarca.
                    aoTocar = {
                        if (selecao != null) aoAlternarSelecao(capitulo.id) else aoAbrirCapitulo(capitulo.id)
                    },
                    // Tocar e segurar entra no modo já com esta linha marcada.
                    aoSegurar = {
                        if (selecao == null) aoIniciarSelecao(ModoDeSelecao.ARQUIVAR, capitulo.id)
                    },
                    detalhes = false,
                    aoAlternarLido = { aoAlternarLido(capitulo.id) },
                )
            }
        }
    }
}

/** O cabeçalho da lista (LV2, minimalista): só o **título**, grande e em destaque, e o **autor**. O resto está nas informações (barra de cima). */
@Composable
private fun CabecalhoDoLivro(livro: LivroDetalhe) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(livro.titulo, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            // O autor: do tamanho e da cor dos títulos dos capítulos, numa fonte diferente (serifada, em itálico).
            Text(
                livro.autor ?: "Autor desconhecido",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}
