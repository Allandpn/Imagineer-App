package com.allan.imagineer.telas.comum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * O diálogo de remover livro. O aviso lista o que a rota apaga de propósito:
 * "remover um livro" soa menos grave do que apagar todo o catálogo visual que
 * ele acumulou (item 7.3a, incremento 4).
 */
@Composable
fun DialogoDeRemocao(
    remocao: EstadoDaRemocao,
    aoCancelar: () -> Unit,
    aoConfirmar: () -> Unit,
) {
    val livro = when (remocao) {
        EstadoDaRemocao.Nenhuma -> return
        is EstadoDaRemocao.Confirmando -> remocao.livro
        is EstadoDaRemocao.Removendo -> remocao.livro
        is EstadoDaRemocao.Falhou -> remocao.livro
    }
    val removendo = remocao is EstadoDaRemocao.Removendo

    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text("Remover livro?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\"${livro.titulo}\"", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Isto apaga também os capítulos, elementos, frames, prompts e " +
                        "imagens deste livro. Não dá para desfazer.",
                )
                if (remocao is EstadoDaRemocao.Falhou) {
                    Text(remocao.motivo, color = MaterialTheme.colorScheme.error)
                }
                if (removendo) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = aoConfirmar, enabled = !removendo) {
                Text(if (remocao is EstadoDaRemocao.Falhou) "Tentar de novo" else "Remover")
            }
        },
        dismissButton = {
            TextButton(onClick = aoCancelar, enabled = !removendo) {
                Text(if (remocao is EstadoDaRemocao.Falhou) "Fechar" else "Cancelar")
            }
        },
    )
}
