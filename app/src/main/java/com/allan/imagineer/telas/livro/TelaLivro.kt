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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
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
 * A tela de Livro (item 7.4): metadados, lista de capítulos e o interruptor de
 * "ignorado" de cada um.
 *
 * Só monta o ViewModel e entrega o estado para [ConteudoDoLivro] — a separação
 * permite pré-visualizar cada estado sem rede.
 */
@Composable
fun TelaLivro(
    livroId: Int,
    aoVoltar: () -> Unit,
    aoAbrirCapitulo: (capituloId: Int) -> Unit,
    aoAbrirElementos: () -> Unit,
    aoAbrirPerfis: () -> Unit,
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: LivroViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LivroViewModel(
                    livroId,
                    aplicacao.repositorioDeLivros,
                    aplicacao.repositorioDeCapitulos,
                    aplicacao.repositorioDePerfis,
                )
            }
        },
    )
    val estado by viewModel.estado.collectAsState()
    val edicao by viewModel.edicao.collectAsState()
    val escolhaDePerfil by viewModel.escolhaDePerfil.collectAsState()
    val remocao by viewModel.remocao.collectAsState()
    val avisos = remember { SnackbarHostState() }

    // Recarrega toda vez que a tela volta a ficar visível: ao voltar de um capítulo,
    // as sugestões pendentes podem ter mudado. Cobre também a primeira abertura.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    // Cada falha vira um aviso novo. O anterior é dispensado antes: o Snackbar
    // enfileira, e vários toques seguidos empilhariam avisos de 4 segundos cada.
    LaunchedEffect(viewModel) {
        viewModel.avisos.collect { texto ->
            avisos.currentSnackbarData?.dismiss()
            launch { avisos.showSnackbar(texto) }
        }
    }

    // O livro foi apagado: a tela não tem mais o que mostrar, volta para a Biblioteca.
    LaunchedEffect(viewModel) {
        viewModel.livroRemovido.collect { aoVoltar() }
    }

    ConteudoDoLivro(
        estado = estado,
        avisos = avisos,
        aoVoltar = aoVoltar,
        aoTentarDeNovo = viewModel::carregar,
        aoAlternarIgnorado = viewModel::alternarIgnorado,
        aoAbrirCapitulo = aoAbrirCapitulo,
        aoAbrirElementos = aoAbrirElementos,
        aoAbrirPerfis = aoAbrirPerfis,
        aoEditar = viewModel::abrirEdicao,
        aoEscolherPerfilPadrao = viewModel::abrirEscolhaDePerfil,
        aoApagar = viewModel::pedirRemocao,
    )

    val livro = (estado as? EstadoDoLivro.Pronto)?.livro
    if (livro != null && edicao is EstadoDaEdicao.Editando) {
        DialogoDeEdicao(
            livro = livro,
            estado = edicao as EstadoDaEdicao.Editando,
            aoSalvar = viewModel::salvarEdicao,
            aoCancelar = viewModel::cancelarEdicao,
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
    aoAlternarIgnorado: (capituloId: Int) -> Unit,
    aoAbrirCapitulo: (capituloId: Int) -> Unit,
    aoAbrirElementos: () -> Unit,
    aoAbrirPerfis: () -> Unit,
    aoEditar: () -> Unit,
    aoEscolherPerfilPadrao: () -> Unit,
    aoApagar: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(avisos) },
        topBar = {
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
                        TextButton(onClick = aoAbrirElementos) { Text("Elementos") }
                        TextButton(onClick = aoAbrirPerfis) { Text("Perfis") }
                        MenuDoLivro(aoEditar, aoEscolherPerfilPadrao, aoApagar)
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
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = aoTentarDeNovo) { Text("Tentar de novo") }
                    }
                }

                is EstadoDoLivro.Pronto -> ListaDoLivro(
                    estado = estado,
                    aoAlternarIgnorado = aoAlternarIgnorado,
                    aoAbrirCapitulo = aoAbrirCapitulo,
                )
            }
        }
    }
}

@Composable
private fun ListaDoLivro(
    estado: EstadoDoLivro.Pronto,
    aoAlternarIgnorado: (Int) -> Unit,
    aoAbrirCapitulo: (Int) -> Unit,
) {
    val livro = estado.livro

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { CabecalhoDoLivro(livro, estado.perfil) }
            items(livro.capitulos, key = { it.id }) { capitulo ->
                LinhaDeCapitulo(
                    capitulo = capitulo,
                    ajustando = capitulo.id in estado.ajustando,
                    aoAlternarIgnorado = { aoAlternarIgnorado(capitulo.id) },
                    aoAbrir = { aoAbrirCapitulo(capitulo.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CabecalhoDoLivro(livro: LivroDetalhe, perfil: PerfilRenderizacao?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(livro.titulo, style = MaterialTheme.typography.headlineSmall)
        Text(livro.autor ?: "Autor desconhecido", style = MaterialTheme.typography.titleMedium)
        if (livro.idioma != null) {
            Text(livro.idioma, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            descreverCapitulos(livro.total_de_capitulos, livro.capitulos_ignorados),
            style = MaterialTheme.typography.bodyMedium,
        )
        // A API só devolve o id do perfil; o nome vem de uma busca à parte, que pode
        // ainda não ter chegado (ou ter falhado) — aí cai no "definido".
        Text(
            when {
                livro.perfil_renderizacao_padrao_id == null -> "Perfil de renderização padrão: nenhum definido"
                perfil != null -> "Perfil de renderização padrão: ${perfil.nome}"
                else -> "Perfil de renderização padrão: definido"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (livro.metadados_pendentes.isNotEmpty()) {
            Text(
                "Faltam dados: ${nomesDosCampos(livro.metadados_pendentes)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Uma linha da lista. O interruptor **ligado** significa "capítulo catalogado";
 * desligado, ignorado. Um capítulo ignorado aparece esmaecido e não abre ao tocar
 * (item 7.4).
 */
@Composable
private fun LinhaDeCapitulo(
    capitulo: CapituloResumo,
    ajustando: Boolean,
    aoAlternarIgnorado: () -> Unit,
    aoAbrir: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .clickable(enabled = !capitulo.ignorado, onClick = aoAbrir)
            .alpha(if (capitulo.ignorado) 0.55f else 1f),
        headlineContent = { Text(tituloDoCapitulo(capitulo.titulo, capitulo.ordem)) },
        supportingContent = {
            Text(
                buildString {
                    append(descreverTamanho(capitulo.tamanho_do_texto))
                    if (capitulo.ignorado) append(" · ignorado")
                },
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (capitulo.sugestoes_pendentes > 0) {
                    Badge { Text(capitulo.sugestoes_pendentes.toString()) }
                }
                if (ajustando) {
                    // Esperando o servidor: um interruptor apagado não diz que o app está
                    // esperando; um indicador de progresso diz. Ocupa o mesmo espaço do
                    // interruptor, para a linha não "pular" de largura.
                    Box(modifier = Modifier.size(width = 52.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Switch(
                        checked = !capitulo.ignorado,
                        onCheckedChange = { aoAlternarIgnorado() },
                    )
                }
            }
        },
    )
}

/** O menu ⋮ da barra superior: as ações sobre o livro aberto. */
@Composable
private fun MenuDoLivro(
    aoEditar: () -> Unit,
    aoEscolherPerfilPadrao: () -> Unit,
    aoApagar: () -> Unit,
) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { aberto = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            DropdownMenuItem(text = { Text("Editar") }, onClick = { aberto = false; aoEditar() })
            DropdownMenuItem(
                text = { Text("Perfil padrão…") },
                onClick = { aberto = false; aoEscolherPerfilPadrao() },
            )
            DropdownMenuItem(text = { Text("Apagar livro") }, onClick = { aberto = false; aoApagar() })
        }
    }
}
