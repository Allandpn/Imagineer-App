package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.allan.imagineer.rede.ElementoSugerido

/**
 * A linha **"Vinculados: ..."** do retrato de um elemento que aceita vínculos (V8), com **Escolher**. Se o retrato já existe,
 * lê do servidor, uma vez, os vinculados que ele já tem; e, se os vínculos mudaram e o prompt é o antigo, avisa (V7).
 */
@Composable
internal fun VinculadosDoRetrato(elemento: ElementoSugerido, frameId: Int?, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    if (frameId != null) LaunchedEffect(elemento.id, frameId) { acoes.aoCarregarVinculos(elemento.id, frameId) }
    val vinculados = estado.vinculosDoRetrato[elemento.id].orEmpty()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            descreverVinculados(vinculados.map { it.nome }),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = { acoes.aoAbrirVinculos(elemento, frameId) }) { Text("Escolher", maxLines = 1, softWrap = false) }
    }
    if (elemento.id in estado.vinculosPendentesDePrompt) {
        Text(AVISO_VINCULOS_MUDARAM, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

/**
 * O modal de **escolher os elementos vinculados** ao retrato (V8): os outros elementos confirmados do capítulo que não são
 * personagens (V2), marcáveis até o máximo. Nada vai ao servidor aqui: **Usar estes** decide (cria o frame depois, ou atualiza o que existe).
 */
@Composable
internal fun DialogoDeVinculos(escolha: EscolhaDeVinculos, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val elementos = (estado.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes?.elementos.orEmpty()
    val sujeito = elementos.firstOrNull { it.id == escolha.elementoId }
    val candidatos = sujeito?.let { candidatosAoVinculo(elementos, it) }.orEmpty()
    AlertDialog(
        onDismissRequest = acoes.aoFecharVinculos,
        title = { Text("Vincular elementos ao retrato") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Os elementos marcados aparecem junto no retrato (o objeto que carrega, o lugar onde está). Personagens ficam " +
                        "individuais: para um personagem com outro elemento, use uma cena. Vale para o próximo prompt.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("${escolha.marcados.size} de $MAXIMO_DE_VINCULADOS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (candidatos.isEmpty()) {
                    Text("Não há outros elementos (que não sejam personagens) confirmados neste capítulo.", style = MaterialTheme.typography.bodyMedium)
                }
                candidatos.forEach { candidato ->
                    val estadoId = candidato.estado_vigente?.id ?: return@forEach
                    val marcado = estadoId in escolha.marcados
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { acoes.aoAlternarVinculo(estadoId) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = marcado, onCheckedChange = null)
                        Text(
                            "${candidato.elemento_casado?.nome ?: candidato.nome} (${candidato.tipo.lowercase()})",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { sujeito?.let(acoes.aoUsarVinculos) }, enabled = sujeito != null) { Text("Usar estes") } },
        dismissButton = {
            Row {
                TextButton(onClick = acoes.aoLimparVinculos, enabled = escolha.marcados.isNotEmpty()) { Text("Limpar") }
                TextButton(onClick = acoes.aoFecharVinculos) { Text("Cancelar") }
            }
        },
    )
}
