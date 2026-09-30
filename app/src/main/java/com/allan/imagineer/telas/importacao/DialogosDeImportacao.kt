package com.allan.imagineer.telas.importacao

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * O diálogo que corresponde ao estado atual da importação — ou nada, se não há
 * importação em andamento. A Biblioteca só chama isto e não precisa conhecer os
 * detalhes de cada passo.
 */
@Composable
fun DialogosDeImportacao(
    estado: EstadoDaImportacao,
    aoTentarDeNovo: () -> Unit,
    aoFechar: () -> Unit,
    aoSeguirMesmoAssim: () -> Unit,
    aoAbrirExistente: () -> Unit,
    aoRemoverONovo: () -> Unit,
    aoSalvarMetadados: (titulo: String, autor: String) -> Unit,
    aoRemoverLivroDoFormulario: () -> Unit,
) {
    when (estado) {
        EstadoDaImportacao.Nenhuma -> Unit
        is EstadoDaImportacao.Enviando -> DialogoDeEnvio(estado)
        is EstadoDaImportacao.Semelhantes ->
            DialogoDeSemelhantes(estado, aoSeguirMesmoAssim, aoAbrirExistente, aoRemoverONovo)
        is EstadoDaImportacao.MetadadosPendentes ->
            DialogoDeMetadados(estado, aoSalvarMetadados, aoRemoverLivroDoFormulario)
        is EstadoDaImportacao.Falhou -> DialogoDeFalha(estado, aoTentarDeNovo, aoFechar)
    }
}

/** Não fecha por toque fora nem pelo botão voltar: não há cancelamento na v1 (item 7.3a). */
@Composable
private fun DialogoDeEnvio(estado: EstadoDaImportacao.Enviando) {
    val fracao = fracaoEnviada(estado.enviados, estado.total)
    val enviadoTudo = fracao != null && fracao >= 1f

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Importando") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(estado.nome, style = MaterialTheme.typography.titleSmall)
                when {
                    // Tudo enviado: o servidor ainda extrai e grava os capítulos.
                    enviadoTudo -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Processando no servidor…")
                    }
                    fracao != null -> {
                        LinearProgressIndicator(
                            progress = { fracao },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${(fracao * 100).toInt()}%")
                    }
                    // O seletor de arquivos não informou o tamanho: sem porcentagem.
                    else -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Enviando…")
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun DialogoDeSemelhantes(
    estado: EstadoDaImportacao.Semelhantes,
    aoSeguirMesmoAssim: () -> Unit,
    aoAbrirExistente: () -> Unit,
    aoRemoverONovo: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Já existe um livro parecido") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Já existe na biblioteca:")
                estado.semelhantes.forEach { Text("• ${it.titulo}", style = MaterialTheme.typography.titleSmall) }
                Text(
                    "O livro novo já foi importado. Você pode mantê-lo mesmo assim, " +
                        "abrir o que já existia ou remover o novo.",
                )
                if (estado.erro != null) {
                    Text(estado.erro, color = MaterialTheme.colorScheme.error)
                }
                if (estado.removendo) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = aoSeguirMesmoAssim, enabled = !estado.removendo) {
                Text("Seguir mesmo assim")
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = aoAbrirExistente, enabled = !estado.removendo) {
                    Text("Abrir o existente")
                }
                TextButton(onClick = aoRemoverONovo, enabled = !estado.removendo) {
                    Text("Remover o novo")
                }
            }
        },
    )
}

/**
 * O formulário de título/autor pendentes. Mostra **só os campos pendentes** e não
 * tem "Cancelar" — são mandatórios (item 7.3) —, mas tem "Remover livro" para o
 * usuário não ficar preso.
 */
@Composable
private fun DialogoDeMetadados(
    estado: EstadoDaImportacao.MetadadosPendentes,
    aoSalvar: (titulo: String, autor: String) -> Unit,
    aoRemover: () -> Unit,
) {
    val livro = estado.livro
    // remember(livro.id): recomeça do valor do servidor se for outro livro.
    var titulo by remember(livro.id) { mutableStateOf(livro.titulo) }
    var autor by remember(livro.id) { mutableStateOf(livro.autor.orEmpty()) }
    val ocupado = estado.salvando || estado.removendo

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Confirme os dados do livro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Não consegui identificar ${nomesDosCampos(livro.metadados_pendentes)} " +
                        "deste livro com certeza. Confira e complete:",
                )
                if ("titulo" in livro.metadados_pendentes) {
                    OutlinedTextField(
                        value = titulo,
                        onValueChange = { titulo = it },
                        label = { Text("Título") },
                        singleLine = true,
                        enabled = !ocupado,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                }
                if ("autor" in livro.metadados_pendentes) {
                    OutlinedTextField(
                        value = autor,
                        onValueChange = { autor = it },
                        label = { Text("Autor") },
                        singleLine = true,
                        enabled = !ocupado,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                }
                if (estado.erro != null) {
                    Text(estado.erro, color = MaterialTheme.colorScheme.error)
                }
                if (ocupado) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { aoSalvar(titulo, autor) }, enabled = !ocupado) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = aoRemover, enabled = !ocupado) { Text("Remover livro") }
        },
    )
}

@Composable
private fun DialogoDeFalha(
    estado: EstadoDaImportacao.Falhou,
    aoTentarDeNovo: () -> Unit,
    aoFechar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Não consegui importar") },
        text = { Text(estado.motivo, color = MaterialTheme.colorScheme.error) },
        confirmButton = {
            // Sem arquivo guardado, não há o que reenviar (o arquivo em si foi recusado).
            if (estado.arquivo != null) {
                TextButton(onClick = aoTentarDeNovo) { Text("Tentar de novo") }
            }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}
