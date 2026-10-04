package com.allan.imagineer.telas.menu

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.ModeloDeImagem

/**
 * O catálogo dos modelos de imagem (MI6): busca, e por modelo o **preço por imagem** (medido ou estimado), a **moderação**, se aceita
 * referência e a **resolução** que já entregou; **Usar** (vira o padrão), **Mostrar ao gerar** e **Testar resolução** (gera uma imagem
 * de teste, depois de confirmar o custo).
 */
@Composable
fun TelaModelosDeImagem(aoVoltar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: ModelosDeImagemViewModel = viewModel(
        factory = viewModelFactory { initializer { ModelosDeImagemViewModel(aplicacao.repositorioDeModelos) } },
    )
    val estado by viewModel.estado.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }
    var busca by rememberSaveable { mutableStateOf("") }
    var adicionando by rememberSaveable { mutableStateOf(false) }

    TelaDoMenu("Modelo de imagem", aoVoltar) {
        when (val carga = estado.carga) {
            CargaDoCatalogo.Carregando -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
            is CargaDoCatalogo.Erro -> {
                Text(carga.motivo, color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::carregar) { Text("Tentar de novo") }
            }
            is CargaDoCatalogo.Pronta -> {
                carga.aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                estado.recado?.let { Text(it, color = if (estado.recadoEhErro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary) }
                OutlinedTextField(value = busca, onValueChange = { busca = it }, label = { Text("Buscar modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { adicionando = true }) { Text("Adicionar um modelo pelo id") }
                Text(
                    "O preço por imagem só aparece quando se sabe: depois de gerar ou testar (média real) ou, no fal.ai e no Replicate, pela tabela de preços (estimado, com ~).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                filtrarCatalogo(carga.modelos, busca).forEach { modelo ->
                    CartaoDoModeloDeImagem(modelo, ocupado = modelo.id in estado.ocupados, viewModel = viewModel)
                }
            }
        }
    }
    if (adicionando) DialogoDeAdicionarModelo(aoFechar = { adicionando = false }, aoAdicionar = { viewModel.adicionar(it); adicionando = false })
    estado.teste?.let { DialogoDoTeste(it, viewModel) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartaoDoModeloDeImagem(modelo: ModeloDeImagem, ocupado: Boolean, viewModel: ModelosDeImagemViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(modelo.nome + if (modelo.em_uso) " · padrão" else "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${modelo.id} · ${modelo.fornecedor}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(precoDaImagem(modelo), style = MaterialTheme.typography.bodyMedium)
            precoPorTokenDeImagem(modelo)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                listOfNotNull(
                    modelo.moderacao.takeIf { it.isNotBlank() },
                    if (modelo.aceita_referencia) "aceita imagem de referência" else null,
                    modelo.resolucao_tipica?.let { "resolução $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.usar(modelo) }, enabled = !modelo.em_uso && !ocupado) { Text(if (modelo.em_uso) "Em uso" else "Usar", maxLines = 1, softWrap = false) }
                OutlinedButton(onClick = { viewModel.alternarDisponivel(modelo) }, enabled = !ocupado) {
                    Text(if (modelo.disponivel) "Tirar da lista" else "Mostrar ao gerar", maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = { viewModel.pedirTeste(modelo) }, enabled = !ocupado) { Text("Testar resolução", maxLines = 1, softWrap = false) }
            }
        }
    }
}

@Composable
private fun DialogoDeAdicionarModelo(aoFechar: () -> Unit, aoAdicionar: (String) -> Unit) {
    var id by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Adicionar modelo de imagem") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Use o id do modelo. Sem prefixo é do OpenRouter; no fal.ai ou no Replicate, comece com fal: ou replicate: (por exemplo replicate:black-forest-labs/flux-dev).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("Id do modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { aoAdicionar(id) }, enabled = id.isNotBlank()) { Text("Adicionar") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

/** O teste de um modelo: confirma o custo, espera e mostra a prévia, a resolução, o custo e o tempo. */
@Composable
private fun DialogoDoTeste(teste: TesteDeModelo, viewModel: ModelosDeImagemViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::fecharTeste,
        title = {
            Text(
                when (teste) {
                    is TesteDeModelo.Confirmando -> "Testar ${teste.modelo.nome}?"
                    is TesteDeModelo.Rodando -> "Gerando a imagem de teste…"
                    is TesteDeModelo.Pronto -> "Resultado do teste"
                    is TesteDeModelo.Falhou -> "O teste não deu certo"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (teste) {
                    is TesteDeModelo.Confirmando -> {
                        Text(avisoDeCustoDoTeste(teste.modelo), style = MaterialTheme.typography.bodyMedium)
                        Text("O resultado mostra a resolução e o custo reais desse modelo, e o custo passa a valer como preço medido.", style = MaterialTheme.typography.bodySmall)
                    }
                    is TesteDeModelo.Rodando -> {
                        Text("Pode levar alguns instantes (um modelo em fila ainda cobra a imagem).", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is TesteDeModelo.Pronto -> {
                        val r = teste.resultado
                        val previa = remember(r.previa_base64) {
                            runCatching {
                                val bytes = Base64.decode(r.previa_base64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }.getOrNull()
                        }
                        previa?.let { Image(it, contentDescription = "Imagem de teste", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) }
                        Text("Resolução: ${resolucaoDoTeste(r)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Custo: ${r.custo?.let { formatarDolar(it, estimado = r.estimado) } ?: "não informado pelo fornecedor"}", style = MaterialTheme.typography.bodyMedium)
                        Text("Tempo: ${"%.1f".format(java.util.Locale.forLanguageTag("pt-BR"), r.segundos)} s · ${descreverTamanhoDoArquivo(r.tamanho_em_bytes)}", style = MaterialTheme.typography.bodySmall)
                    }
                    is TesteDeModelo.Falhou -> Text(teste.motivo, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            when (teste) {
                is TesteDeModelo.Confirmando -> TextButton(onClick = viewModel::confirmarTeste) { Text("Gerar e cobrar") }
                is TesteDeModelo.Rodando -> {}
                else -> TextButton(onClick = viewModel::fecharTeste) { Text("Fechar") }
            }
        },
        dismissButton = {
            if (teste is TesteDeModelo.Confirmando) TextButton(onClick = viewModel::fecharTeste) { Text("Cancelar") }
        },
    )
}

private fun descreverTamanhoDoArquivo(bytes: Long): String = com.allan.imagineer.telas.lixeira.descreverTamanho(bytes)
