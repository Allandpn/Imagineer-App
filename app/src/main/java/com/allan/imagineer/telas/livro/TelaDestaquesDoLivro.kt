package com.allan.imagineer.telas.livro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.Destaque
import com.allan.imagineer.rede.RepositorioDeDestaques
import com.allan.imagineer.rede.RepositorioDeElementos
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.capitulo.corDeFundoDoDestaque
import com.allan.imagineer.telas.comum.BotaoDeIcone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// A lista "Destaques e notas" do livro (RL12): tudo o que a pessoa destacou, por capítulo, na ordem do livro.

/** Os destaques de um capítulo, com o título dele (RL12). */
data class GrupoDeDestaques(val capituloId: Int, val ordem: Int, val titulo: String?, val destaques: List<Destaque>)

/**
 * Agrupa os destaques por capítulo **na ordem do livro** e, dentro de cada um, na ordem do texto. Capítulo que o livro não lista mais
 * (arquivado, por exemplo) vai para o fim, com a ordem 0, em vez de sumir.
 */
fun agruparDestaques(destaques: List<Destaque>, capitulos: List<CapituloResumo>): List<GrupoDeDestaques> {
    val porId = capitulos.associateBy { it.id }
    return destaques.groupBy { it.capitulo_id }
        .map { (capituloId, lista) ->
            val capitulo = porId[capituloId]
            GrupoDeDestaques(capituloId, capitulo?.ordem ?: Int.MAX_VALUE, capitulo?.titulo, lista.sortedBy { it.inicio })
        }
        .sortedBy { it.ordem }
}

sealed interface EstadoDosDestaquesDoLivro {
    data object Carregando : EstadoDosDestaquesDoLivro
    data class Pronto(val grupos: List<GrupoDeDestaques>, val nomesDosElementos: Map<Int, String>) : EstadoDosDestaquesDoLivro
    data class Erro(val motivo: String) : EstadoDosDestaquesDoLivro
}

class DestaquesDoLivroViewModel(
    private val livroId: Int,
    private val destaques: RepositorioDeDestaques,
    private val livros: RepositorioDeLivros,
    private val elementos: RepositorioDeElementos,
) : ViewModel() {
    private val _estado = MutableStateFlow<EstadoDosDestaquesDoLivro>(EstadoDosDestaquesDoLivro.Carregando)
    val estado: StateFlow<EstadoDosDestaquesDoLivro> = _estado.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            val lista = when (val r = destaques.listar(livroId)) {
                is ResultadoDaChamada.Sucesso -> r.dado
                is ResultadoDaChamada.Falha -> { _estado.value = EstadoDosDestaquesDoLivro.Erro(r.motivo); return@launch }
            }
            // Os títulos e os nomes são um enfeite da lista: se não vierem, ela aparece assim mesmo.
            val capitulos = (livros.abrirLivro(livroId) as? ResultadoDaChamada.Sucesso)?.dado?.capitulos.orEmpty()
            val nomes = (elementos.listar(livroId) as? ResultadoDaChamada.Sucesso)?.dado?.associate { it.id to it.nome }.orEmpty()
            _estado.value = EstadoDosDestaquesDoLivro.Pronto(agruparDestaques(lista, capitulos), nomes)
        }
    }

    fun tentarDeNovo() {
        _estado.value = EstadoDosDestaquesDoLivro.Carregando
        carregar()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDestaquesDoLivro(
    livroId: Int,
    aoVoltar: () -> Unit,
    /** Abre o capítulo na posição do destaque. */
    aoAbrir: (capituloId: Int, posicao: Int) -> Unit,
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: DestaquesDoLivroViewModel = viewModel(
        key = "destaques-do-livro-$livroId",
        factory = viewModelFactory {
            initializer { DestaquesDoLivroViewModel(livroId, aplicacao.repositorioDeDestaques, aplicacao.repositorioDeLivros, aplicacao.repositorioDeElementos) }
        },
    )
    val estado by viewModel.estado.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Destaques e notas") },
                navigationIcon = { BotaoDeIcone(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", aoVoltar, cor = LocalContentColor.current) },
            )
        },
    ) { margens ->
        Box(modifier = Modifier.padding(margens).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val atual = estado) {
                EstadoDosDestaquesDoLivro.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is EstadoDosDestaquesDoLivro.Erro -> Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(atual.motivo, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = viewModel::tentarDeNovo, modifier = Modifier.padding(top = 12.dp)) { Text("Tentar de novo") }
                }
                is EstadoDosDestaquesDoLivro.Pronto -> if (atual.grupos.isEmpty()) {
                    Text(
                        "Nada destacado ainda. No capítulo, selecione um trecho e toque em Destacar.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        atual.grupos.forEach { grupo ->
                            item(key = "cap-${grupo.capituloId}") {
                                Text(
                                    if (grupo.ordem == Int.MAX_VALUE) "Outro capítulo" else tituloDoGrupo(grupo.ordem, grupo.titulo),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(grupo.destaques, key = { "d-${it.id}" }) { d ->
                                LinhaDoDestaque(d, d.elemento_id?.let { atual.nomesDosElementos[it] }) { aoAbrir(grupo.capituloId, d.inicio) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaDoDestaque(destaque: Destaque, nomeDoElemento: String?, aoTocar: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = aoTocar).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(corDeFundoDoDestaque(destaque.corDoDestaque)))
        Column(modifier = Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("“${destaque.trecho}”", style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            destaque.nota?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            nomeDoElemento?.let { Text("→ $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
