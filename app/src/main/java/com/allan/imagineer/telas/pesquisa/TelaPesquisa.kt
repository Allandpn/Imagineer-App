package com.allan.imagineer.telas.pesquisa

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.OcorrenciaNoTexto
import com.allan.imagineer.telas.livro.tituloDoCapitulo
import kotlinx.coroutines.launch

/**
 * A tela de pesquisa (LV5): um campo no alto e três abas — **Capítulo**, **Livro** e **Biblioteca** — que se trocam **deslizando**
 * para o lado. Tocar numa ocorrência abre o capítulo no parágrafo dela.
 *
 * @param capituloId o capítulo de onde se pesquisou; `null` quando se pesquisa da tela do livro (então não há a aba Capítulo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaPesquisa(
    livroId: Int,
    capituloId: Int?,
    aoVoltar: () -> Unit,
    aoAbrirOcorrencia: (ocorrencia: OcorrenciaNoTexto, termo: String) -> Unit,
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: PesquisaViewModel = viewModel(
        key = "pesquisa$livroId-$capituloId",
        factory = viewModelFactory {
            initializer { PesquisaViewModel(livroId, capituloId, aplicacao.repositorioDeBusca) }
        },
    )
    val estado by viewModel.estado.collectAsState()
    val abas = viewModel.abas
    val pager = rememberPagerState { abas.size }
    val escopo = rememberCoroutineScope()
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = estado.termo,
                        onValueChange = viewModel::alterarTermo,
                        placeholder = { Text("Pesquisar no texto") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.alterarTermo(estado.termo) }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        trailingIcon = {
                            if (estado.termo.isNotEmpty()) {
                                IconButton(onClick = { viewModel.alterarTermo("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Limpar")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(foco),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { margens ->
        Column(modifier = Modifier.padding(margens).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                abas.forEachIndexed { indice, aba ->
                    val resultado = estado.resultados[aba]
                    Tab(
                        selected = pager.currentPage == indice,
                        onClick = { escopo.launch { pager.animateScrollToPage(indice) } },
                        text = { Text(rotuloDaAba(aba, resultado)) },
                    )
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { pagina ->
                val aba = abas[pagina]
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                        ConteudoDaAba(aba, estado.resultados[aba] ?: ResultadoDaAba.Vazio) { aoAbrirOcorrencia(it, estado.termo.trim()) }
                    }
                }
            }
        }
    }
}

/** O texto da aba, com quantas ocorrências há ("Livro (12)"), quando já se sabe. */
fun rotuloDaAba(aba: AbaDaPesquisa, resultado: ResultadoDaAba?): String =
    if (resultado is ResultadoDaAba.Pronto) "${aba.rotulo} (${resultado.resultado.total})" else aba.rotulo

@Composable
private fun ConteudoDaAba(aba: AbaDaPesquisa, resultado: ResultadoDaAba, aoTocar: (OcorrenciaNoTexto) -> Unit) {
    when (resultado) {
        ResultadoDaAba.Vazio -> Aviso("Digite ao menos $MINIMO_DO_TERMO letras para pesquisar.")
        ResultadoDaAba.Buscando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is ResultadoDaAba.Erro -> Aviso(resultado.motivo, erro = true)
        is ResultadoDaAba.Pronto -> {
            val dados = resultado.resultado
            if (dados.ocorrencias.isEmpty()) {
                Aviso("Nada encontrado.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(dados.ocorrencias) { ocorrencia ->
                        LinhaDaOcorrencia(ocorrencia, mostrarLivro = aba != AbaDaPesquisa.CAPITULO && aba != AbaDaPesquisa.LIVRO, aoTocar = { aoTocar(ocorrencia) })
                        HorizontalDivider()
                    }
                    if (dados.truncado) {
                        item {
                            Text(
                                "Mostrando ${dados.ocorrencias.size} de ${dados.total}. Refine o termo para ver as outras.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Aviso(texto: String, erro: Boolean = false) {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Text(
            texto,
            style = MaterialTheme.typography.bodyLarge,
            color = if (erro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Uma ocorrência: de onde é (livro e capítulo) e o trecho com o achado em negrito. */
@Composable
private fun LinhaDaOcorrencia(ocorrencia: OcorrenciaNoTexto, mostrarLivro: Boolean, aoTocar: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = aoTocar).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val capitulo = tituloDoCapitulo(ocorrencia.capitulo_titulo, ocorrencia.capitulo_ordem)
        Text(
            if (mostrarLivro) "${ocorrencia.livro_titulo} · $capitulo" else capitulo,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            trechoDestacado(ocorrencia.trecho, ocorrencia.inicio_no_trecho, ocorrencia.fim_no_trecho),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
