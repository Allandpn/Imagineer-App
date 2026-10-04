package com.allan.imagineer.telas.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp

/** O aviso fixo no alto da tela: o que já funciona e o que ainda não (RL26). */
const val EXPLICACAO_DA_NARRACAO =
    "Hoje o botão Ouvir usa sempre a voz do aparelho. A voz de IA, mais natural e com tom dramático, ainda não está disponível: " +
        "o que você escrever aqui fica guardado e passa a valer quando ela chegar."

/** A tela **Narração** das Configurações (RL26): o motor, o modo, a voz e as instruções de tom. */
@Composable
fun TelaNarracao(aoVoltar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: NarracaoViewModel = viewModel(
        factory = viewModelFactory { initializer { NarracaoViewModel(aplicacao.repositorioDeModelos) } },
    )
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(viewModel) { viewModel.carregar() }
    val contexto = LocalContext.current
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let { android.widget.Toast.makeText(contexto, it, android.widget.Toast.LENGTH_SHORT).show(); viewModel.avisoLido() }
    }

    TelaDoMenu("Narração", aoVoltar) {
        Text(EXPLICACAO_DA_NARRACAO, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (val carga = estado.carga) {
            CargaDaNarracao.Carregando -> CircularProgressIndicator()
            is CargaDaNarracao.Erro -> {
                Text(carga.motivo, color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::tentarDeNovo) { Text("Tentar de novo") }
            }
            is CargaDaNarracao.Pronta -> {
                Text("Quem narra", style = MaterialTheme.typography.titleMedium)
                OpcaoDaNarracao("Voz do aparelho", "Grátis, sem internet", marcada = carga.configuracao.narracao_motor != "IA", disponivel = true)
                OpcaoDaNarracao("Voz de IA", "Mais natural; gerada pelo servidor", marcada = carga.configuracao.narracao_motor == "IA", disponivel = false)

                Text("Quantas vozes", style = MaterialTheme.typography.titleMedium)
                OpcaoDaNarracao("Uma voz", "Um narrador lê tudo", marcada = carga.configuracao.narracao_modo != "POR_PERSONAGEM", disponivel = true)
                OpcaoDaNarracao("Uma voz por personagem", "Cada personagem fala com a sua voz", marcada = carga.configuracao.narracao_modo == "POR_PERSONAGEM", disponivel = false)

                Text("Voz de IA", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = estado.edicao.voz,
                    onValueChange = { viewModel.mudar(estado.edicao.copy(voz = it)) },
                    label = { Text("Voz (deixe em branco para a padrão)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = estado.edicao.instrucoes,
                    onValueChange = { viewModel.mudar(estado.edicao.copy(instrucoes = it)) },
                    label = { Text("Instruções de tom") },
                    placeholder = { Text("Ex.: voz grave e calma, com suspense nas cenas de tensão") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::salvar,
                    enabled = !estado.salvando && mudouANarracao(estado.edicao, carga.configuracao),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (estado.salvando) "Salvando…" else "Salvar") }
            }
        }
    }
}

/** Uma opção de escolha única: a disponível mostra se está marcada; a que ainda não existe aparece apagada, com "em breve". */
@Composable
private fun OpcaoDaNarracao(titulo: String, descricao: String, marcada: Boolean, disponivel: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = marcada, onClick = null, enabled = disponivel)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                if (disponivel) titulo else "$titulo — em breve",
                style = MaterialTheme.typography.bodyLarge,
                color = if (disponivel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
