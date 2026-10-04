package com.allan.imagineer.telas.elementos

import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import com.allan.imagineer.telas.capitulo.painel.urlDoServidorEmUso
import com.allan.imagineer.rede.enderecoDaImagem
import coil3.compose.AsyncImage
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
import androidx.compose.material3.AssistChip
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
    /** Abre o capítulo com a área de IA do elemento (LV3). */
    aoAbrirNoCapitulo: (capituloId: Int, elementoId: Int) -> Unit = { _, _ -> },
    /** A barra de baixo do livro (LY1); nulo = sem a barra. */
    aoIrParaODoLivro: ((com.allan.imagineer.telas.livro.DestinoDoLivro) -> Unit)? = null,
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
        bottomBar = {
            aoIrParaODoLivro?.let { com.allan.imagineer.telas.livro.BarraDeNavegacaoDoLivro(com.allan.imagineer.telas.livro.DestinoDoLivro.ELEMENTOS, it) }
        },
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
                    is CargaDaLista.Pronta -> ConteudoDaLista(carga.elementos, estado, viewModel, aoAbrirFicha, aoAbrirNoCapitulo)
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
    aoAbrirNoCapitulo: (capituloId: Int, elementoId: Int) -> Unit,
) {
    val visiveis = filtrarElementos(todos, estado.busca, estado.tipo, estado.capituloId, estado.capitulosDosElementos)
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
                    FiltroPorCapitulo(estado.capitulosDosElementos, estado.capituloId, viewModel::filtrarPorCapitulo)
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
        items(visiveis, key = { it.id }) { elemento ->
            LinhaDoElemento(
                elemento = elemento,
                capitulos = estado.capitulosDosElementos[elemento.id].orEmpty(),
                aoAbrirNoCapitulo = { capituloId -> aoAbrirNoCapitulo(capituloId, elemento.id) },
            ) { aoAbrirFicha(elemento.id) }
        }
    }
}

/** O filtro "Capítulo": um chip que abre a lista dos capítulos com elementos (LV3b); "Todos os capítulos" limpa. */
@Composable
private fun FiltroPorCapitulo(
    capitulosDosElementos: Map<Int, List<CapituloDoElemento>>,
    escolhido: Int?,
    aoEscolher: (Int?) -> Unit,
) {
    val capitulos = capitulosComElementos(capitulosDosElementos)
    if (capitulos.isEmpty()) return
    var aberto by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = escolhido != null,
            onClick = { aberto = true },
            label = { Text(capitulos.firstOrNull { it.capituloId == escolhido }?.rotulo ?: "Capítulo") },
        )
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            DropdownMenuItem(text = { Text("Todos os capítulos") }, onClick = { aberto = false; aoEscolher(null) })
            capitulos.forEach { capitulo ->
                DropdownMenuItem(
                    text = { Text(capitulo.rotulo + (capitulo.titulo?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")) },
                    onClick = { aberto = false; aoEscolher(capitulo.capituloId) },
                )
            }
        }
    }
}

/** Nome, tipo, quantos estados e um trecho do estado mais recente (E20). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LinhaDoElemento(
    elemento: ElementoDoLivro,
    capitulos: List<CapituloDoElemento>,
    aoAbrirNoCapitulo: (capituloId: Int) -> Unit,
    aoTocar: () -> Unit,
) {
    val urlBase = urlDoServidorEmUso()
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = aoTocar)) {
      Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // FI4: a capa do elemento (a âncora ou a imagem mais recente do retrato), quando há.
        val capa = elemento.imagem_de_capa_id
        if (capa != null && urlBase != null) {
            AsyncImage(
                model = enderecoDaImagem(urlBase, capa, "miniatura"),
                contentDescription = "Imagem de ${elemento.nome}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            // LV3: os capítulos em que ele aparece; tocar num abre o capítulo com a área de IA dele.
            if (capitulos.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    capitulos.forEach { capitulo ->
                        AssistChip(onClick = { aoAbrirNoCapitulo(capitulo.capituloId) }, label = { Text(capitulo.rotulo, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        }
      }
    }
}
