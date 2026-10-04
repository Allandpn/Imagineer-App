package com.allan.imagineer.telas.estatisticas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.RepositorioDeEstatisticas
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.capitulo.RegistroDeTempoDeLeitura
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

// A tela "Estatísticas de leitura" (RL17).

sealed interface EstadoDasEstatisticas {
    data object Carregando : EstadoDasEstatisticas
    data class Pronto(val resumo: ResumoDaLeitura, val livros: List<LinhaDoLivro>) : EstadoDasEstatisticas
    data class Erro(val motivo: String) : EstadoDasEstatisticas
}

class EstatisticasViewModel(
    private val estatisticas: RepositorioDeEstatisticas,
    private val livros: RepositorioDeLivros,
    private val registro: RegistroDeTempoDeLeitura?,
    private val hoje: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _estado = MutableStateFlow<EstadoDasEstatisticas>(EstadoDasEstatisticas.Carregando)
    val estado: StateFlow<EstadoDasEstatisticas> = _estado.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            // O que ficou pendente no aparelho vai primeiro, para a tela já contar o que se leu agora há pouco.
            registro?.enviarPendentes()
            val dados = when (val r = estatisticas.estatisticas()) {
                is ResultadoDaChamada.Sucesso -> r.dado
                is ResultadoDaChamada.Falha -> { _estado.value = EstadoDasEstatisticas.Erro(r.motivo); return@launch }
            }
            val lista = (livros.listarLivros() as? ResultadoDaChamada.Sucesso)?.dado.orEmpty()
            _estado.value = EstadoDasEstatisticas.Pronto(
                resumo = resumirALeitura(dados.dias, hoje(), dados.livros.sumOf { it.segundos }),
                livros = linhasPorLivro(lista, dados.livros),
            )
        }
    }

    fun tentarDeNovo() {
        _estado.value = EstadoDasEstatisticas.Carregando
        carregar()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaEstatisticas(aoVoltar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: EstatisticasViewModel = viewModel(
        factory = viewModelFactory {
            initializer { EstatisticasViewModel(aplicacao.repositorioDeEstatisticas, aplicacao.repositorioDeLivros, aplicacao.registroDeTempoDeLeitura) }
        },
    )
    val estado by viewModel.estado.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estatísticas de leitura") },
                navigationIcon = { IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { margens ->
        Box(modifier = Modifier.padding(margens).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val atual = estado) {
                EstadoDasEstatisticas.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is EstadoDasEstatisticas.Erro -> Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(atual.motivo, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = viewModel::tentarDeNovo, modifier = Modifier.padding(top = 12.dp)) { Text("Tentar de novo") }
                }
                is EstadoDasEstatisticas.Pronto -> LazyColumn(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { ResumoNaTela(atual.resumo) }
                    if (atual.livros.isEmpty()) {
                        item {
                            Text(
                                "Quando você ler um livro, o tempo e o progresso aparecem aqui.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        item { Text("Por livro", style = MaterialTheme.typography.titleMedium) }
                        items(atual.livros, key = { it.livroId }) { LinhaDoLivroNaTela(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumoNaTela(resumo: ResumoDaLeitura) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Numero("Hoje", descreverDuracao(resumo.hoje))
            Numero("7 dias", descreverDuracao(resumo.ultimosSeteDias))
            Numero("Sequência", if (resumo.sequenciaDeDias == 1) "1 dia" else "${resumo.sequenciaDeDias} dias")
        }
        Text("Tempo total de leitura: ${descreverDuracao(resumo.total)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Numero(rotulo: String, valor: String) {
    Column {
        Text(valor, style = MaterialTheme.typography.titleLarge)
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LinhaDoLivroNaTela(linha: LinhaDoLivro) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(linha.titulo, style = MaterialTheme.typography.titleSmall)
        if (linha.capitulosAtivos > 0) {
            LinearProgressIndicator(progress = { linha.capitulosLidos.toFloat() / linha.capitulosAtivos }, modifier = Modifier.fillMaxWidth())
        }
        Text(
            "${linha.capitulosLidos} de ${linha.capitulosAtivos} capítulos · ${descreverDuracao(linha.segundos)} lidos em ${linha.diasLidos} " +
                if (linha.diasLidos == 1) "dia" else "dias",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(descreverEstimativa(linha.estimativa), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
