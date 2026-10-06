package com.allan.imagineer.telas.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.CampoDeLimite
import com.allan.imagineer.rede.ContaDoServidor
import com.allan.imagineer.rede.LimitesDoServidor
import com.allan.imagineer.rede.RepositorioDeContas
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.bytesParaLer
import com.allan.imagineer.rede.inteiroPositivoOuNulo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// A tela "Administração" (bloco K, AP7): só o dono. Os limites do servidor e as contas.

data class EstadoDaAdministracao(
    val carregando: Boolean = true,
    /** `false` = esta pessoa não é o dono (o servidor respondeu 404): a tela só diz isso. */
    val ehDono: Boolean = true,
    val limites: LimitesDoServidor? = null,
    val contas: List<ContaDoServidor> = emptyList(),
    val aviso: String? = null,
)

/** O que a tela diz do disco: "Em uso: 1,2 GB de 20 GB (novos arquivos são recusados a partir de 18,0 GB)". */
fun descreverUsoDoDisco(limites: LimitesDoServidor): String =
    "Em uso: ${bytesParaLer(limites.uso_da_aplicacao_em_bytes)} de ${limites.armazenamento_total_em_gb} GB " +
        "(novos arquivos são recusados a partir de ${bytesParaLer(limites.recusa_novos_arquivos_a_partir_de_bytes)})."

/** A linha de cima de uma conta: quem é e quanto ocupa. */
fun descreverConta(conta: ContaDoServidor): String {
    val quem = conta.nome?.takeIf { it.isNotBlank() } ?: conta.login ?: "conta ${conta.id}"
    val papel = if (conta.dono) " (dono)" else ""
    return "$quem$papel · ${conta.livros} livro(s) · ${bytesParaLer(conta.uso_em_bytes)}"
}

/** A cota da conta, em palavras: o dono não tem; "padrão (5 GB)" ou "própria (10 GB)". */
fun descreverCota(conta: ContaDoServidor): String = when {
    conta.dono || conta.cota_efetiva_em_gb == null -> "Sem cota (dono)"
    conta.cota_em_gb != null -> "Cota própria: ${conta.cota_em_gb} GB"
    else -> "Cota padrão: ${conta.cota_efetiva_em_gb} GB"
}

class AdministracaoViewModel(private val contas: RepositorioDeContas) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDaAdministracao())
    val estado: StateFlow<EstadoDaAdministracao> = _estado.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            val limites = contas.limites()
            if (limites is ResultadoDaChamada.Falha) {
                // 404 é "você não é o dono"; qualquer outra falha é um problema de verdade.
                _estado.update { it.copy(carregando = false, ehDono = limites.codigoHttp != 404, aviso = if (limites.codigoHttp == 404) null else limites.motivo) }
                return@launch
            }
            val lista = contas.contas()
            _estado.update {
                it.copy(
                    carregando = false,
                    limites = (limites as ResultadoDaChamada.Sucesso).dado,
                    contas = (lista as? ResultadoDaChamada.Sucesso)?.dado ?: it.contas,
                    aviso = (lista as? ResultadoDaChamada.Falha)?.motivo,
                )
            }
        }
    }

    /** Grava os limites cujo texto mudou e é um inteiro maior que zero; o resto não vai. Devolve `false` se algum texto é inválido. */
    fun gravarLimites(textos: Map<CampoDeLimite, String>): Boolean {
        val atuais = _estado.value.limites ?: return false
        val invalido = textos.entries.firstOrNull { (_, texto) -> inteiroPositivoOuNulo(texto) == null }
        if (invalido != null) {
            _estado.update { it.copy(aviso = "${invalido.key.rotulo}: use um número inteiro maior que zero.") }
            return false
        }
        val mudados = textos.mapNotNull { (campo, texto) -> inteiroPositivoOuNulo(texto)?.takeIf { it != campo.valorDe(atuais) }?.let { campo to it } }.toMap()
        if (mudados.isEmpty()) {
            _estado.update { it.copy(aviso = "Nenhum limite mudou.") }
            return true
        }
        viewModelScope.launch {
            when (val r = contas.gravarLimites(mudados)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(limites = r.dado, aviso = "Limites salvos.") }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(aviso = r.motivo) }
            }
        }
        return true
    }

    fun usarChavesDoServidor(conta: ContaDoServidor, usa: Boolean) {
        viewModelScope.launch { trocar(contas.usarChavesDoServidor(conta.id, usa)) }
    }

    /** Muda a cota própria; texto em branco volta ao padrão. Um texto que não é número inteiro maior que zero não vai. */
    fun mudarCota(conta: ContaDoServidor, texto: String) {
        val gb = if (texto.isBlank()) null else inteiroPositivoOuNulo(texto)
        if (texto.isNotBlank() && gb == null) {
            _estado.update { it.copy(aviso = "Cota: use um número inteiro maior que zero, ou deixe em branco para o padrão.") }
            return
        }
        viewModelScope.launch { trocar(contas.mudarCota(conta.id, gb)) }
    }

    private fun trocar(r: ResultadoDaChamada<ContaDoServidor>) {
        when (r) {
            is ResultadoDaChamada.Sucesso -> _estado.update { atual -> atual.copy(contas = atual.contas.map { if (it.id == r.dado.id) r.dado else it }, aviso = null) }
            is ResultadoDaChamada.Falha -> _estado.update { it.copy(aviso = r.motivo) }  // inclusive o 422 do "dono não pode"
        }
    }

    fun avisoLido() {
        _estado.update { it.copy(aviso = null) }
    }
}

@Composable
fun TelaAdministracao(aoVoltar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: AdministracaoViewModel = viewModel(
        factory = viewModelFactory { initializer { AdministracaoViewModel(aplicacao.repositorioDeContas) } },
    )
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(viewModel) { viewModel.carregar() }

    TelaDoMenu("Administração", aoVoltar) {
        when {
            estado.carregando -> CircularProgressIndicator()
            !estado.ehDono -> Text("Só o dono do servidor tem acesso a esta tela.", style = MaterialTheme.typography.bodyMedium)
            else -> {
                estado.aviso?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                estado.limites?.let { LimitesNaTela(it, aoSalvar = viewModel::gravarLimites) }
                HorizontalDivider()
                Text("Contas", style = MaterialTheme.typography.titleMedium)
                estado.contas.forEach { conta ->
                    CartaoDaConta(conta, aoMudarChaves = { viewModel.usarChavesDoServidor(conta, it) }, aoMudarCota = { viewModel.mudarCota(conta, it) })
                }
            }
        }
    }
}

@Composable
private fun LimitesNaTela(limites: LimitesDoServidor, aoSalvar: (Map<CampoDeLimite, String>) -> Boolean) {
    var textos by remember(limites) { mutableStateOf(CampoDeLimite.entries.associateWith { it.valorDe(limites).toString() }) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Limites", style = MaterialTheme.typography.titleMedium)
        Text(descreverUsoDoDisco(limites), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CampoDeLimite.entries.forEach { campo ->
            OutlinedTextField(
                value = textos.getValue(campo),
                onValueChange = { novo -> textos = textos + (campo to novo) },
                label = { Text("${campo.rotulo} (${campo.unidade})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = { aoSalvar(textos) }) { Text("Salvar limites") }
    }
}

@Composable
private fun CartaoDaConta(conta: ContaDoServidor, aoMudarChaves: (Boolean) -> Unit, aoMudarCota: (String) -> Unit) {
    var cota by remember(conta.id, conta.cota_em_gb) { mutableStateOf(conta.cota_em_gb?.toString().orEmpty()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(descreverConta(conta), style = MaterialTheme.typography.titleSmall)
            Text(descreverCota(conta), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Usa as chaves do servidor", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                // O dono não pode ficar sem as chaves (o servidor recusaria): o interruptor fica ligado e parado.
                Switch(checked = conta.usa_chaves_do_servidor, onCheckedChange = aoMudarChaves, enabled = !conta.dono)
            }
            if (!conta.dono) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cota,
                        onValueChange = { cota = it },
                        label = { Text("Cota própria (GB; vazio = padrão)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { aoMudarCota(cota) }) { Text("Salvar") }
                }
            }
        }
    }
}
