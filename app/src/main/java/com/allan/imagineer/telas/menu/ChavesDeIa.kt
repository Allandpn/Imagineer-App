package com.allan.imagineer.telas.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.dados.CofreDeChaves
import com.allan.imagineer.dados.mascararChave
import com.allan.imagineer.dados.motivoParaNaoGuardarAChave
import com.allan.imagineer.rede.ProvedorDeIa
import com.allan.imagineer.rede.RepositorioDeContas
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// A tela "Chaves de IA" (bloco K, AP4): a chave de cada provedor, guardada só neste aparelho.

/** Como está a chave de um provedor para esta pessoa. */
enum class SituacaoDaChave(val texto: String) {
    /** Há uma chave cadastrada neste aparelho: é a que vale. */
    NO_APARELHO("Chave cadastrada neste aparelho"),

    /** Não há chave local, mas o servidor fornece a dele a esta pessoa. */
    DO_SERVIDOR("Usando a chave do servidor"),

    /** Sem chave: as chamadas deste provedor vão recusar até cadastrar. */
    SEM_CHAVE("Sem chave"),
}

/** A precedência do servidor (CT24): a chave local vale; sem ela, a do servidor se ele fornece; senão, nada. */
fun situacaoDaChave(provedor: ProvedorDeIa, temChaveLocal: Boolean): SituacaoDaChave = when {
    temChaveLocal -> SituacaoDaChave.NO_APARELHO
    provedor.servidor_fornece -> SituacaoDaChave.DO_SERVIDOR
    else -> SituacaoDaChave.SEM_CHAVE
}

data class LinhaDeChave(val provedor: ProvedorDeIa, val situacao: SituacaoDaChave, val mascara: String?)

data class EstadoDasChaves(
    val linhas: List<LinhaDeChave> = emptyList(),
    val carregando: Boolean = true,
    /** Um recado curto (a falha ao ler a lista, ou a recusa de uma chave); nulo quando não há nada a dizer. */
    val aviso: String? = null,
)

class ChavesDeIaViewModel(private val contas: RepositorioDeContas, private val cofre: CofreDeChaves) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDasChaves())
    val estado: StateFlow<EstadoDasChaves> = _estado.asStateFlow()
    private var provedores: List<ProvedorDeIa> = emptyList()

    fun carregar() {
        viewModelScope.launch {
            when (val r = contas.provedores()) {
                is ResultadoDaChamada.Sucesso -> { provedores = r.dado; _estado.update { it.copy(linhas = montar(), carregando = false, aviso = null) } }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(carregando = false, aviso = "Não consegui ler a lista de provedores: ${r.motivo}") }
            }
        }
    }

    private fun montar(): List<LinhaDeChave> = provedores.map { provedor ->
        val chave = cofre.chaves()[provedor.cabecalho]
        LinhaDeChave(provedor, situacaoDaChave(provedor, chave != null), chave?.let(::mascararChave))
    }

    /** Guarda a chave do provedor, depois de conferir que cabe num header. Devolve `true` se guardou. */
    fun salvar(provedor: ProvedorDeIa, chave: String): Boolean {
        val limpa = chave.trim()
        motivoParaNaoGuardarAChave(limpa)?.let { motivo -> _estado.update { it.copy(aviso = motivo) }; return false }
        cofre.gravar(provedor.cabecalho, limpa)
        _estado.update { it.copy(linhas = montar(), aviso = null) }
        return true
    }

    fun remover(provedor: ProvedorDeIa) {
        cofre.remover(provedor.cabecalho)
        _estado.update { it.copy(linhas = montar(), aviso = null) }
    }

    fun avisoLido() {
        _estado.update { it.copy(aviso = null) }
    }
}

@Composable
fun TelaChavesDeIa(aoVoltar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: ChavesDeIaViewModel = viewModel(
        factory = viewModelFactory { initializer { ChavesDeIaViewModel(aplicacao.repositorioDeContas, aplicacao.cofreDeChaves) } },
    )
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(viewModel) { viewModel.carregar() }

    TelaDoMenu("Chaves de IA", aoVoltar) {
        Text(
            "A chave de cada provedor fica só neste aparelho, cifrada. Ela vai ao servidor apenas durante cada chamada de IA, e o servidor não a guarda.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        estado.aviso?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        if (estado.carregando) CircularProgressIndicator()
        estado.linhas.forEach { linha ->
            HorizontalDivider()
            CartaoDeChave(linha, aoSalvar = { viewModel.salvar(linha.provedor, it) }, aoRemover = { viewModel.remover(linha.provedor) })
        }
    }
}

@Composable
private fun CartaoDeChave(linha: LinhaDeChave, aoSalvar: (String) -> Boolean, aoRemover: () -> Unit) {
    var chave by remember(linha.provedor.id) { mutableStateOf("") }
    var mostrar by remember(linha.provedor.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(linha.provedor.nome, style = MaterialTheme.typography.titleMedium)
        if (linha.provedor.usado_para.isNotBlank()) {
            Text(linha.provedor.usado_para, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            linha.situacao.texto + (linha.mascara?.let { " ($it)" } ?: ""),
            style = MaterialTheme.typography.labelLarge,
            color = if (linha.situacao == SituacaoDaChave.SEM_CHAVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
            value = chave,
            onValueChange = { chave = it },
            label = { Text(if (linha.situacao == SituacaoDaChave.NO_APARELHO) "Nova chave (troca a atual)" else "Chave") },
            singleLine = true,
            visualTransformation = if (mostrar) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            Button(onClick = { if (aoSalvar(chave)) chave = "" }, enabled = chave.isNotBlank()) { Text("Salvar") }
            OutlinedButton(onClick = { mostrar = !mostrar }) { Text(if (mostrar) "Esconder" else "Mostrar") }
            if (linha.situacao == SituacaoDaChave.NO_APARELHO) OutlinedButton(onClick = aoRemover) { Text("Remover") }
        }
    }
}
