package com.allan.imagineer.telas.livro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.ModelosDoLivro
import com.allan.imagineer.telas.menu.EstrelaDeModelo
import com.allan.imagineer.telas.menu.TelaDoMenu
import com.allan.imagineer.telas.menu.TituloDeGrupoDeModelos
import com.allan.imagineer.telas.menu.ordenarComFavoritos
import com.allan.imagineer.telas.menu.ehModeloEmLote
import androidx.compose.ui.draw.alpha
import com.allan.imagineer.telas.menu.quantosSaoFavoritos
import com.allan.imagineer.telas.menu.rememberFavoritosDeModelos

/**
 * **Modelos deste livro** (item 7.5g, MV4): uma linha por tarefa, dizendo o modelo que vale neste livro — o dele ou, se ele não trocou,
 * o global. Tocar abre a lista de escolha de sempre, com **Usar o padrão** para desfazer a troca. Trocar aqui não mexe nos outros livros.
 */
@Composable
fun TelaModelosDoLivro(livroId: Int, aoVoltar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: ModelosDoLivroViewModel = viewModel(
        key = "modelos-do-livro-$livroId",
        factory = viewModelFactory {
            initializer {
                ModelosDoLivroViewModel(livroId, aplicacao.repositorioDeModelosDoLivro, aplicacao.repositorioDeModelos, aplicacao.repositorioDeNarracao)
            }
        },
    )
    val estado by viewModel.estado.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    TelaDoMenu("Modelos deste livro", aoVoltar) {
        when (val carga = estado.carga) {
            CargaDosModelosDoLivro.Carregando -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
            is CargaDosModelosDoLivro.Erro -> {
                Text(carga.motivo, color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::carregar) { Text("Tentar de novo") }
            }
            is CargaDosModelosDoLivro.Pronta -> {
                Text(
                    "O que você trocar aqui vale só para este livro. O que ficar no padrão segue o que está em Configurações → Modelos de IA.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                estado.recado?.let { Text(it, color = if (estado.recadoEhErro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary) }
                TAREFAS_DO_LIVRO.forEach { tarefa -> CartaoDaTarefa(tarefa, carga.modelos) { viewModel.abrirEscolha(tarefa) } }
                estado.escolhendo?.let { tarefa -> DialogoDeEscolhaDoLivro(tarefa, carga.modelos, estado, viewModel) }
            }
        }
    }
}

@Composable
private fun CartaoDaTarefa(tarefa: TarefaDoLivro, modelos: ModelosDoLivro, aoTocar: () -> Unit) {
    val trocada = tarefaTrocada(modelos, tarefa)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = aoTocar),
        colors = if (trocada) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tarefa.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(tarefa.descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(descricaoDaTarefaNoLivro(modelos, tarefa), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A escolha de uma tarefa para este livro: busca, favoritos no topo, a lista e **Usar o padrão** (só quando o livro trocou). */
@Composable
private fun DialogoDeEscolhaDoLivro(tarefa: TarefaDoLivro, modelos: ModelosDoLivro, estado: EstadoDosModelosDoLivro, viewModel: ModelosDoLivroViewModel) {
    var busca by rememberSaveable { mutableStateOf("") }
    val atual = modelos.trocas[tarefa.campo]
    AlertDialog(
        onDismissRequest = viewModel::fecharEscolha,
        title = { Text(tarefa.titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = busca, onValueChange = { busca = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                estado.recado?.takeIf { estado.recadoEhErro }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                when (val opcoes = estado.opcoes) {
                    OpcoesDaEscolha.Carregando -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                    is OpcoesDaEscolha.Erro -> {
                        Text(opcoes.motivo, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = viewModel::tentarOpcoesDeNovo) { Text("Tentar de novo") }
                    }
                    is OpcoesDaEscolha.Prontas -> ListaDeOpcoes(tarefa, filtrarOpcoes(opcoes.opcoes, busca), atual, estado.salvando) { viewModel.escolher(tarefa, it) }
                }
                if (estado.salvando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.escolher(tarefa, null) }, enabled = !estado.salvando && atual != null) { Text("Usar o padrão") }
        },
        dismissButton = { TextButton(onClick = viewModel::fecharEscolha) { Text("Fechar") } },
    )
}

@Composable
private fun ListaDeOpcoes(tarefa: TarefaDoLivro, opcoes: List<OpcaoDeEscolha>, atual: String?, salvando: Boolean, aoEscolher: (String) -> Unit) {
    val categoria = tarefa.fonte.categoriaDeFavoritos
    // Tarefas sem estrela (a voz) usam um conjunto vazio: nada sobe e nada ganha título de grupo.
    val favoritos = categoria?.let { rememberFavoritosDeModelos(it) }
    val conjunto = favoritos?.conjunto.orEmpty()
    val ordenadas = ordenarComFavoritos(opcoes, conjunto) { it.id }
    val quantosFavoritos = quantosSaoFavoritos(ordenadas, conjunto) { it.id }
    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
        if (quantosFavoritos > 0) item(key = "titulo-favoritos") { TituloDeGrupoDeModelos("Favoritos") }
        ordenadas.forEachIndexed { indice, opcao ->
            if (indice == quantosFavoritos && quantosFavoritos > 0) item(key = "titulo-outros") { TituloDeGrupoDeModelos("Outros") }
            item(key = opcao.id) {
                val emLote = ehModeloEmLote(opcao.id)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).clickable(enabled = !salvando && !emLote) { aoEscolher(opcao.id) }.alpha(if (emLote) 0.38f else 1f).padding(vertical = 8.dp)) {
                        Text(opcao.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = if (opcao.id == atual) FontWeight.Bold else FontWeight.Normal)
                        Text(
                            listOf(opcao.detalhe, if (emLote) "em lote: indisponível" else null, if (opcao.id == atual) "neste livro" else null).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    favoritos?.let { EstrelaDeModelo(it.ehFavorito(opcao.id), { it.alternar(opcao.id) }) }
                }
                HorizontalDivider()
            }
        }
    }
    if (ordenadas.isEmpty()) Text("Nada combina com a busca.", style = MaterialTheme.typography.bodySmall)
}
