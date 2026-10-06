package com.allan.imagineer.telas.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.allan.imagineer.rede.descreverPrecoDoModelo

/** O aviso fixo no alto da tela: o que cada voz faz (AN1). */
const val EXPLICACAO_DA_NARRACAO =
    "A voz do aparelho é grátis e funciona sem internet, mas é robótica. A voz de IA é mais natural: o servidor gera o áudio do capítulo " +
        "uma vez (com um pequeno custo, que o app mostra antes) e o guarda; depois, ouvir não custa nada."

/** O aviso sobre o campo de instruções de tom: ele é guardado, mas o modelo de voz ainda não o recebe (NA2). */
const val AVISO_DAS_INSTRUCOES_DE_TOM =
    "As instruções de tom ficam guardadas, mas ainda não são enviadas ao modelo de voz (o OpenRouter não tem esse campo)."

/** A tela **Narração** das Configurações (RL26, AN1): o motor, o modelo e a voz de IA, o modo e as instruções de tom. */
@Composable
fun TelaNarracao(aoVoltar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: NarracaoViewModel = viewModel(
        factory = viewModelFactory { initializer { NarracaoViewModel(aplicacao.repositorioDeModelos, aplicacao.repositorioDeNarracao) } },
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
                val usaIa = estado.edicao.motor == "IA"
                Text("Quem narra", style = MaterialTheme.typography.titleMedium)
                OpcaoDaNarracao("Voz do aparelho", "Grátis, sem internet", marcada = !usaIa, disponivel = true) { viewModel.mudar(estado.edicao.copy(motor = "APARELHO")) }
                OpcaoDaNarracao("Voz de IA", "Mais natural; gerada pelo servidor", marcada = usaIa, disponivel = true) { viewModel.mudar(estado.edicao.copy(motor = "IA")) }

                if (usaIa) {
                    Text("Modelo de voz", style = MaterialTheme.typography.titleMedium)
                    estado.erroDosModelos?.let { Text("Não consegui ler a lista de modelos: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (estado.modelos.isEmpty() && estado.erroDosModelos == null) CircularProgressIndicator()
                    estado.modelos.forEach { modelo ->
                        OpcaoDaNarracao(modelo.nome, "${modelo.id} · ${descreverPrecoDoModelo(modelo)}", marcada = modelo.id == estado.edicao.modelo, disponivel = true) { viewModel.escolherModelo(modelo) }
                    }
                    if (estado.edicao.modelo.isNotBlank() && estado.modelos.none { it.id == estado.edicao.modelo } && estado.modelos.isNotEmpty()) {
                        Text("O modelo guardado (${estado.edicao.modelo}) não está mais na lista; escolha outro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    val vozes = estado.modelos.firstOrNull { it.id == estado.edicao.modelo }?.vozes.orEmpty()
                    Text("Voz", style = MaterialTheme.typography.titleMedium)
                    if (vozes.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            vozes.forEach { voz ->
                                FilterChip(
                                    selected = voz == estado.edicao.voz,
                                    onClick = { viewModel.mudar(estado.edicao.copy(voz = if (voz == estado.edicao.voz) "" else voz)) },
                                    label = { Text(voz) },
                                )
                            }
                        }
                        Text("Sem escolher, vale a voz padrão do modelo (alguns modelos exigem uma voz).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        OutlinedTextField(
                            value = estado.edicao.voz,
                            onValueChange = { viewModel.mudar(estado.edicao.copy(voz = it)) },
                            label = { Text("Voz (deixe em branco para a padrão)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Text("Quantas vozes", style = MaterialTheme.typography.titleMedium)
                OpcaoDaNarracao("Uma voz", "Um narrador lê tudo", marcada = carga.configuracao.narracao_modo != "POR_PERSONAGEM", disponivel = true) {}
                OpcaoDaNarracao("Uma voz por personagem", "Cada personagem fala com a sua voz", marcada = carga.configuracao.narracao_modo == "POR_PERSONAGEM", disponivel = false) {}

                Text("Instruções de tom", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = estado.edicao.instrucoes,
                    onValueChange = { viewModel.mudar(estado.edicao.copy(instrucoes = it)) },
                    label = { Text("Instruções de tom") },
                    placeholder = { Text("Ex.: voz grave e calma, com suspense nas cenas de tensão") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(AVISO_DAS_INSTRUCOES_DE_TOM, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = viewModel::salvar,
                    enabled = !estado.salvando && mudouANarracao(estado.edicao, carga.configuracao),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (estado.salvando) "Salvando…" else "Salvar") }
            }
        }
    }
}

/** Uma opção de escolha única: a disponível se escolhe tocando; a que ainda não existe aparece apagada, com "em breve". */
@Composable
private fun OpcaoDaNarracao(titulo: String, descricao: String, marcada: Boolean, disponivel: Boolean, aoEscolher: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = disponivel, onClick = aoEscolher),
    ) {
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
