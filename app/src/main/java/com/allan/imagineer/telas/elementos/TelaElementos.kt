package com.allan.imagineer.telas.elementos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.telas.capitulo.painel.TIPOS_DE_ELEMENTO
import com.allan.imagineer.telas.capitulo.painel.rotuloDoTipo

/**
 * A tela de Elementos do livro (item 7.8, E20): todos os elementos já cadastrados, com busca por
 * nome e filtro por tipo. Tocar num abre a ficha.
 *
 * O visual é provisório: este incremento trata das regras.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaElementos(
    livroId: Int,
    aoVoltar: () -> Unit,
    aoAbrirFicha: (elementoId: Int) -> Unit,
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: ListaDeElementosViewModel = viewModel(
        key = "elementos$livroId",
        factory = viewModelFactory {
            initializer { ListaDeElementosViewModel(livroId, aplicacao.repositorioDeElementos) }
        },
    )
    val estado by viewModel.estado.collectAsState()

    // Ao voltar da ficha (onde algo pode ter sido editado ou apagado) a lista se atualiza.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Elementos") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { margens ->
        Box(modifier = Modifier.padding(margens).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                when (val carga = estado.carga) {
                    CargaDaLista.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    is CargaDaLista.Erro -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(carga.motivo, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            Button(onClick = viewModel::carregar) { Text("Tentar de novo") }
                        }
                    }
                    is CargaDaLista.Pronta -> ConteudoDaLista(carga.elementos, estado, viewModel, aoAbrirFicha)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConteudoDaLista(
    todos: List<ElementoDoLivro>,
    estado: EstadoDaLista,
    viewModel: ListaDeElementosViewModel,
    aoAbrirFicha: (Int) -> Unit,
) {
    val visiveis = filtrarElementos(todos, estado.busca, estado.tipo)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = estado.busca,
                    onValueChange = viewModel::buscar,
                    label = { Text("Buscar pelo nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiposPresentes(todos, TIPOS_DE_ELEMENTO).forEach { tipo ->
                        FilterChip(
                            selected = estado.tipo == tipo,
                            onClick = { viewModel.filtrarPorTipo(tipo) },
                            label = { Text("${rotuloDoTipo(tipo)} (${todos.count { it.tipo == tipo }})") },
                        )
                    }
                }
            }
        }
        if (todos.isEmpty()) {
            item {
                Text(
                    "Nenhum elemento cadastrado neste livro ainda. Eles nascem quando você confirma as sugestões da IA de um capítulo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (visiveis.isEmpty()) {
            item { Text("Nenhum elemento encontrado.", style = MaterialTheme.typography.bodyLarge) }
        }
        items(visiveis, key = { it.id }) { elemento -> LinhaDoElemento(elemento) { aoAbrirFicha(elemento.id) } }
    }
}

/** Nome, tipo, quantos estados e um trecho do estado mais recente (E20). */
@Composable
private fun LinhaDoElemento(elemento: ElementoDoLivro, aoTocar: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = aoTocar)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(elemento.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${rotuloDoTipo(elemento.tipo)} · " + when (elemento.total_de_estados) {
                    0 -> "sem estados"
                    1 -> "1 estado"
                    else -> "${elemento.total_de_estados} estados"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            elemento.estado_vigente?.let {
                Text(it.descricao, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
