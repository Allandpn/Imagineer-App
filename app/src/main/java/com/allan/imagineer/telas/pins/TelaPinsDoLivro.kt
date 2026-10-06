package com.allan.imagineer.telas.pins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.allan.imagineer.rede.LIMITE_DA_NOTA_DO_PIN
import com.allan.imagineer.rede.PinDoLivro
import com.allan.imagineer.rede.RepositorioDePins
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// A tela "Pins" de um livro (bloco J, PN2): as posições que a pessoa marcou à mão, na ordem do livro.

data class EstadoDosPins(
    val pins: List<PinDoLivro> = emptyList(),
    val carregados: Boolean = false,
    /** Um recado curto (a falha ao ler, editar ou apagar); nulo quando não há nada a dizer. */
    val aviso: String? = null,
)

/** A linha de cima do pin: onde ele está no livro. */
fun localDoPin(pin: PinDoLivro): String {
    val capitulo = pin.ordem_do_capitulo?.let { "Capítulo $it" }
    val titulo = pin.titulo_do_capitulo?.takeIf { it.isNotBlank() }
    return when {
        capitulo != null && titulo != null -> "$capitulo · $titulo"
        else -> capitulo ?: titulo ?: "Capítulo ${pin.capitulo_id}"
    }
}

class PinsDoLivroViewModel(private val livroId: Int, private val repositorio: RepositorioDePins) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDosPins())
    val estado: StateFlow<EstadoDosPins> = _estado.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            when (val r = repositorio.listar(livroId)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(pins = r.dado, carregados = true) }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(carregados = true, aviso = "Não consegui ler os pins: ${r.motivo}") }
            }
        }
    }

    /** Troca a nota do pin; em branco apaga a nota. A lista só muda quando o servidor confirma. */
    fun editarNota(pin: PinDoLivro, nota: String) {
        viewModelScope.launch {
            when (val r = repositorio.ajustarNota(pin.id, nota)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { atual -> atual.copy(pins = atual.pins.map { if (it.id == pin.id) r.dado else it }) }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(aviso = "Não consegui salvar a nota: ${r.motivo}") }
            }
        }
    }

    /** Apaga o pin. Some da lista quando o servidor confirma. */
    fun apagar(pin: PinDoLivro) {
        viewModelScope.launch {
            when (val r = repositorio.apagar(pin.id)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { atual -> atual.copy(pins = atual.pins.filter { it.id != pin.id }) }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(aviso = "Não consegui apagar o pin: ${r.motivo}") }
            }
        }
    }

    fun avisoLido() {
        _estado.update { it.copy(aviso = null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaPinsDoLivro(livroId: Int, aoVoltar: () -> Unit, aoAbrir: (PinDoLivro) -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: PinsDoLivroViewModel = viewModel(
        key = "pins-$livroId",
        factory = viewModelFactory { initializer { PinsDoLivroViewModel(livroId, aplicacao.repositorioDePins) } },
    )
    val estado by viewModel.estado.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }
    val contexto = LocalContext.current
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let { android.widget.Toast.makeText(contexto, it, android.widget.Toast.LENGTH_SHORT).show(); viewModel.avisoLido() }
    }
    var editando by remember { mutableStateOf<PinDoLivro?>(null) }
    var apagando by remember { mutableStateOf<PinDoLivro?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pins") },
                navigationIcon = { IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { margens ->
        Box(Modifier.fillMaxSize().padding(margens)) {
            when {
                !estado.carregados -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                estado.pins.isEmpty() -> Text(
                    "Nenhum pin neste livro ainda. No texto, segure um parágrafo, deixe só ele marcado e toque em Marcar pin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(estado.pins, key = { it.id }) { pin ->
                        LinhaDePin(pin, aoAbrir = { aoAbrir(pin) }, aoEditar = { editando = pin }, aoApagar = { apagando = pin })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    editando?.let { pin ->
        DialogoDaNotaDoPin(
            titulo = "Nota do pin",
            notaInicial = pin.nota.orEmpty(),
            rotuloDoBotao = "Salvar",
            aoConfirmar = { viewModel.editarNota(pin, it); editando = null },
            aoFechar = { editando = null },
        )
    }
    apagando?.let { pin ->
        AlertDialog(
            onDismissRequest = { apagando = null },
            title = { Text("Apagar este pin?") },
            text = { Text("O pin e a nota dele somem. O texto do livro não muda.") },
            confirmButton = { TextButton(onClick = { viewModel.apagar(pin); apagando = null }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { apagando = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun LinhaDePin(pin: PinDoLivro, aoAbrir: () -> Unit, aoEditar: () -> Unit, aoApagar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = aoAbrir).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(localDoPin(pin), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (pin.trecho.isNotBlank()) Text(pin.trecho, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            pin.nota?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        IconButton(onClick = aoEditar) { Icon(Icons.Filled.Edit, contentDescription = "Editar a nota do pin", tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = aoApagar) { Icon(Icons.Filled.Delete, contentDescription = "Apagar o pin", tint = MaterialTheme.colorScheme.error) }
    }
}

/** O diálogo da nota do pin (PN1, PN2): o campo é opcional e vai até [LIMITE_DA_NOTA_DO_PIN] caracteres. */
@Composable
fun DialogoDaNotaDoPin(titulo: String, notaInicial: String, rotuloDoBotao: String, aoConfirmar: (String) -> Unit, aoFechar: () -> Unit) {
    var nota by remember { mutableStateOf(notaInicial) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = nota,
                onValueChange = { nota = it.take(LIMITE_DA_NOTA_DO_PIN) },
                label = { Text("Nota (opcional)") },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { aoConfirmar(nota) }) { Text(rotuloDoBotao) } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}
