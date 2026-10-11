package com.allan.imagineer.telas.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.EuAtual
import com.allan.imagineer.rede.RepositorioDeContas
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.bytesParaLer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// O Perfil (item 7.5g, PF1): quem o servidor reconhece, o papel, de quem são as chaves de IA e o espaço usado. O login é do Tailscale, então
// não há "entrar" nem "criar conta".

/** A letra do círculo: a inicial do nome (ou do login); "?" se não há nada. */
fun inicialDoPerfil(eu: EuAtual): String =
    (eu.nome?.takeIf { it.isNotBlank() } ?: eu.login?.takeIf { it.isNotBlank() })?.trim()?.first()?.uppercase() ?: "?"

/** O papel da pessoa neste servidor. */
fun papelDoPerfil(eu: EuAtual): String = if (eu.dono) "Dono do servidor" else "Convidado(a)"

/** De quem são as chaves de IA que ela gasta (CT9). */
fun chavesDoPerfil(eu: EuAtual): String =
    if (eu.usa_chaves_do_servidor) "Usa as chaves de IA do servidor." else "Usa as suas próprias chaves de IA (as do app)."

/** "3 livros · 1,5 GB de 5 GB" (com cota), ou "3 livros · 1,5 GB" (o dono não tem cota). */
fun usoDoPerfil(eu: EuAtual): String {
    val livros = if (eu.livros == 1) "1 livro" else "${eu.livros} livros"
    val usado = bytesParaLer(eu.uso_em_bytes)
    return eu.cota_efetiva_em_gb?.let { "$livros · $usado de $it GB" } ?: "$livros · $usado"
}

/** A fração da cota já usada, de 0 a 1; `null` sem cota (o dono). Passou da cota: 1. */
fun fracaoDaCota(eu: EuAtual): Float? {
    val cota = eu.cota_efetiva_em_gb?.takeIf { it > 0 } ?: return null
    return (eu.uso_em_bytes.toDouble() / (cota * 1024.0 * 1024.0 * 1024.0)).coerceIn(0.0, 1.0).toFloat()
}

/** Quem é a pessoa conforme o servidor respondeu. */
sealed interface CargaDoPerfil {
    data object Carregando : CargaDoPerfil
    data class Pronta(val eu: EuAtual) : CargaDoPerfil

    /** Sem servidor, sem conexão ou servidor antigo (sem `/eu`): [motivo] já está escrito para a pessoa ler. */
    data class Indisponivel(val motivo: String) : CargaDoPerfil
}

class PerfilViewModel(private val contas: RepositorioDeContas) : ViewModel() {
    private val _carga = MutableStateFlow<CargaDoPerfil>(CargaDoPerfil.Carregando)
    val carga: StateFlow<CargaDoPerfil> = _carga.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            _carga.value = when (val r = contas.eu()) {
                is ResultadoDaChamada.Sucesso -> CargaDoPerfil.Pronta(r.dado)
                is ResultadoDaChamada.Falha -> CargaDoPerfil.Indisponivel(r.motivo)
            }
        }
    }
}

@Composable
fun TelaPerfil(aoVoltar: () -> Unit, aoAbrirChavesDeIa: () -> Unit = {}, aoAbrirAdministracao: () -> Unit = {}) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: PerfilViewModel = viewModel(
        factory = viewModelFactory { initializer { PerfilViewModel(aplicacao.repositorioDeContas) } },
    )
    val carga by viewModel.carga.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    TelaDoMenu("Perfil", aoVoltar) {
        when (val c = carga) {
            CargaDoPerfil.Carregando -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
            is CargaDoPerfil.Indisponivel -> {
                Text("Não consegui saber quem é você.", style = MaterialTheme.typography.titleMedium)
                Text(c.motivo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Text(
                    "O perfil vem do servidor: confira a conexão (e o Tailscale) e tente de novo. Um servidor antigo ainda não diz quem você é.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = viewModel::carregar) { Text("Tentar de novo") }
            }
            is CargaDoPerfil.Pronta -> ConteudoDoPerfil(c.eu, aoAbrirChavesDeIa, aoAbrirAdministracao)
        }
    }
}

@Composable
private fun ConteudoDoPerfil(eu: EuAtual, aoAbrirChavesDeIa: () -> Unit, aoAbrirAdministracao: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(inicialDoPerfil(eu), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(eu.rotulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        eu.login?.takeIf { it.isNotBlank() && it != eu.rotulo }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(papelDoPerfil(eu), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider()

    Text("Espaço no servidor", style = MaterialTheme.typography.titleMedium)
    Text(usoDoPerfil(eu), style = MaterialTheme.typography.bodyMedium)
    fracaoDaCota(eu)?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
    Text(
        "Contam as imagens, os vídeos e as narrações dos seus livros.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HorizontalDivider()

    Text("Chaves de IA", style = MaterialTheme.typography.titleMedium)
    Text(chavesDoPerfil(eu), style = MaterialTheme.typography.bodyMedium)
    LinhaDoPerfil("Chaves de IA", "Informar ou trocar as suas chaves", aoAbrirChavesDeIa)
    if (eu.dono) LinhaDoPerfil("Administração", "Limites do servidor e contas", aoAbrirAdministracao)
    Text(
        "O acesso é pelo Tailscale: não há senha nem cadastro aqui.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LinhaDoPerfil(titulo: String, descricao: String, aoTocar: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = aoTocar).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
