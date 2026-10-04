package com.allan.imagineer.telas.capitulo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.RepositorioDeDicionario
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.Verbete
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// O dicionário por seleção de palavra (RL20): a folha de baixo com o que o servidor achou.

sealed interface EstadoDoDicionario {
    data object Carregando : EstadoDoDicionario

    /** [resultados] vazio = nada achado. [consultouTodos]: a consulta já foi feita em todos os dicionários (não há mais onde procurar). */
    data class Pronto(val palavra: String, val resultados: List<Verbete>, val consultouTodos: Boolean) : EstadoDoDicionario

    /** [motivo] já está escrito para a pessoa ler. */
    data class Erro(val motivo: String) : EstadoDoDicionario
}

/** Os verbetes agrupados por dicionário, na ordem em que o servidor os entregou (RL20: um bloco por dicionário). */
fun agruparPorDicionario(verbetes: List<Verbete>): List<Pair<String, List<Verbete>>> =
    verbetes.groupBy { it.dicionario }.toList()

/** A palavra a procurar a partir do que foi selecionado: tira as pontas; uma frase longa demais não é uma palavra. */
fun palavraParaProcurar(selecao: String): String? =
    selecao.trim().takeIf { it.isNotEmpty() && it.length <= LIMITE_DA_PALAVRA_DO_DICIONARIO && it.count { c -> c.isWhitespace() } < 3 }

const val LIMITE_DA_PALAVRA_DO_DICIONARIO = 60

class DicionarioViewModel(
    private val palavra: String,
    private val livroId: Int,
    private val dicionario: RepositorioDeDicionario,
    private val livros: RepositorioDeLivros,
) : ViewModel() {
    private val _estado = MutableStateFlow<EstadoDoDicionario>(EstadoDoDicionario.Carregando)
    val estado: StateFlow<EstadoDoDicionario> = _estado.asStateFlow()

    private var idioma: String? = null
    private var idiomaLido = false

    fun consultar(todos: Boolean = false) {
        _estado.value = EstadoDoDicionario.Carregando
        viewModelScope.launch {
            // O idioma do livro decide quais dicionários valem; se não vier, o servidor usa os de uso padrão.
            if (!idiomaLido) {
                idioma = (livros.abrirLivro(livroId) as? ResultadoDaChamada.Sucesso)?.dado?.idioma
                idiomaLido = true
            }
            _estado.value = when (val r = dicionario.consultar(palavra, idioma, todos)) {
                is ResultadoDaChamada.Sucesso -> EstadoDoDicionario.Pronto(r.dado.palavra.ifBlank { palavra }, r.dado.resultados, todos)
                is ResultadoDaChamada.Falha -> EstadoDoDicionario.Erro(
                    if (r.codigoHttp == null) "O dicionário precisa do servidor, e ele não respondeu. ${r.motivo}" else r.motivo,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaDoDicionario(palavra: String, livroId: Int, aoFechar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: DicionarioViewModel = viewModel(
        key = "dicionario-$livroId-$palavra",
        factory = viewModelFactory {
            initializer { DicionarioViewModel(palavra, livroId, aplicacao.repositorioDeDicionario, aplicacao.repositorioDeLivros) }
        },
    )
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(viewModel) { if (viewModel.estado.value is EstadoDoDicionario.Carregando) viewModel.consultar() }

    ModalBottomSheet(onDismissRequest = aoFechar) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(palavra, style = MaterialTheme.typography.titleLarge)
            when (val atual = estado) {
                EstadoDoDicionario.Carregando -> CircularProgressIndicator()
                is EstadoDoDicionario.Erro -> {
                    Text(atual.motivo, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.consultar() }) { Text("Tentar de novo") }
                }
                is EstadoDoDicionario.Pronto -> {
                    if (atual.resultados.isEmpty()) {
                        Text(
                            if (atual.consultouTodos) "Não achei essa palavra em nenhum dicionário." else "Não achei essa palavra nos dicionários do idioma do livro.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Se você desligou algum dicionário, ligue-o em Configurações → Dicionários.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    agruparPorDicionario(atual.resultados).forEachIndexed { indice, (nome, verbetes) ->
                        if (indice > 0) HorizontalDivider()
                        Text(nome, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        verbetes.forEach { v ->
                            if (v.entrada.lowercase() != atual.palavra.lowercase()) {
                                Text(v.entrada, style = MaterialTheme.typography.labelLarge)
                            }
                            Text(v.texto, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!atual.consultouTodos) {
                        TextButton(onClick = { viewModel.consultar(todos = true) }) { Text("Procurar em todos os dicionários") }
                    }
                }
            }
        }
    }
}
