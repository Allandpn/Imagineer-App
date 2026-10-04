package com.allan.imagineer.telas.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.local.EspacoPorLivro
import com.allan.imagineer.local.GerenteDeArmazenamento
import com.allan.imagineer.telas.lixeira.descreverTamanho
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// A tela Armazenamento (PL10, regra A11): quanto cada livro ocupa no aparelho e como liberar.

/** O que falta confirmar. Nenhuma das ações tem volta (o servidor guarda tudo, então dá para baixar de novo). */
sealed interface PedidoDeArmazenamento {
    data class LimparCache(val livro: EspacoPorLivro) : PedidoDeArmazenamento
    data class RemoverDownload(val livro: EspacoPorLivro) : PedidoDeArmazenamento
    data class LimparTudo(val bytes: Long, val livros: Int) : PedidoDeArmazenamento
}

data class EstadoDoArmazenamento(
    val carregando: Boolean = true,
    val livros: List<EspacoPorLivro> = emptyList(),
    val pedido: PedidoDeArmazenamento? = null,
    val recado: String? = null,
)

/** O texto de uma linha de livro: "Leve 0,5 MB" e/ou "Baixado 120 MB". */
fun descreverEspaco(livro: EspacoPorLivro): String = buildList {
    if (livro.bytesLeve > 0) add("Texto guardado: ${descreverTamanho(livro.bytesLeve)}")
    if (livro.baixado) add("Baixado: ${descreverTamanho(livro.bytesBaixado)}")
}.joinToString(" · ")

/** As contas da tela: o total de todos os livros e quanto do nível Leve dá para limpar. */
object EspacoNaTela {
    fun total(livros: List<EspacoPorLivro>): Long = livros.sumOf { it.total }
    fun limpavel(livros: List<EspacoPorLivro>): Long = livros.filter { !it.baixado }.sumOf { it.bytesLeve }
}

class ArmazenamentoViewModel(private val gerente: GerenteDeArmazenamento) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDoArmazenamento())
    val estado: StateFlow<EstadoDoArmazenamento> = _estado.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            val lista = runCatching { gerente.listar() }.getOrDefault(emptyList())
            _estado.update { it.copy(carregando = false, livros = lista) }
        }
    }

    fun pedirLimparCache(livro: EspacoPorLivro) { if (!livro.baixado) _estado.update { it.copy(pedido = PedidoDeArmazenamento.LimparCache(livro), recado = null) } }

    fun pedirRemoverDownload(livro: EspacoPorLivro) { if (livro.baixado) _estado.update { it.copy(pedido = PedidoDeArmazenamento.RemoverDownload(livro), recado = null) } }

    fun pedirLimparTudo() {
        val limpaveis = _estado.value.livros.filter { !it.baixado && it.bytesLeve > 0 }
        if (limpaveis.isEmpty()) return
        _estado.update { it.copy(pedido = PedidoDeArmazenamento.LimparTudo(limpaveis.sumOf { l -> l.bytesLeve }, limpaveis.size), recado = null) }
    }

    fun cancelarPedido() { _estado.update { it.copy(pedido = null) } }

    fun confirmar() {
        val pedido = _estado.value.pedido ?: return
        _estado.update { it.copy(pedido = null) }
        viewModelScope.launch {
            val liberado = runCatching {
                when (pedido) {
                    is PedidoDeArmazenamento.LimparCache -> gerente.limparCache(pedido.livro.livroId)
                    is PedidoDeArmazenamento.RemoverDownload -> gerente.removerDownload(pedido.livro.livroId)
                    is PedidoDeArmazenamento.LimparTudo -> gerente.limparTodoOCache()
                }
            }.getOrDefault(0L)
            _estado.update { it.copy(recado = "${descreverTamanho(liberado)} liberados.") }
            carregar()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaArmazenamento(aoVoltar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: ArmazenamentoViewModel = viewModel(
        factory = viewModelFactory { initializer { ArmazenamentoViewModel(aplicacao.gerenteDeArmazenamento) } },
    )
    val estado by viewModel.estado.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    TelaDoMenu("Armazenamento", aoVoltar) {
        when {
            estado.carregando -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
            estado.livros.isEmpty() -> Text("Nada guardado no aparelho ainda. Os livros que você lê e os que baixar aparecem aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                estado.recado?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                Text("No aparelho: ${descreverTamanho(EspacoNaTela.total(estado.livros))}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = viewModel::pedirLimparTudo, enabled = EspacoNaTela.limpavel(estado.livros) > 0) {
                    Text("Limpar todo o cache (${descreverTamanho(EspacoNaTela.limpavel(estado.livros))})")
                }
                Text(
                    "Limpar o cache só apaga o texto guardado de passagem; o que você baixou para ler offline só sai por \"Remover download\".",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                estado.livros.forEach { livro ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(livro.titulo, style = MaterialTheme.typography.titleMedium)
                            Text(descreverEspaco(livro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (livro.baixado) {
                                    OutlinedButton(onClick = { viewModel.pedirRemoverDownload(livro) }) { Text("Remover download", color = MaterialTheme.colorScheme.error) }
                                } else {
                                    OutlinedButton(onClick = { viewModel.pedirLimparCache(livro) }) { Text("Limpar cache") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    estado.pedido?.let { pedido ->
        val (titulo, texto) = when (pedido) {
            is PedidoDeArmazenamento.LimparCache -> "Limpar o cache de ${pedido.livro.titulo}?" to "Libera ${descreverTamanho(pedido.livro.bytesLeve)}. O texto volta sozinho quando você abrir os capítulos com conexão."
            is PedidoDeArmazenamento.RemoverDownload -> "Remover o download de ${pedido.livro.titulo}?" to "Libera ${descreverTamanho(pedido.livro.bytesBaixado)}. Sem o download, o livro só abre offline no que você já tiver lido; dá para baixar de novo."
            is PedidoDeArmazenamento.LimparTudo -> "Limpar todo o cache?" to "Libera ${descreverTamanho(pedido.bytes)} de ${pedido.livros} livros. Os livros baixados não são tocados."
        }
        AlertDialog(
            onDismissRequest = viewModel::cancelarPedido,
            title = { Text(titulo) },
            text = { Text(texto) },
            confirmButton = { TextButton(onClick = viewModel::confirmar) { Text("Liberar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelarPedido) { Text("Cancelar") } },
        )
    }
}
