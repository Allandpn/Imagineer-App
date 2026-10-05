package com.allan.imagineer.telas.capitulo

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.allan.imagineer.rede.CorDeDestaque
import com.allan.imagineer.rede.Destaque
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.RepositorioDeElementos
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.comum.BotaoDeIcone

// A folha de um trecho destacado (RL10, RL11): cor, nota, elemento ligado e remover.

/**
 * A folha de baixo de um [destaque]. Cada mudança vai direto ao servidor por [aoMudarCor], [aoMudarNota] e [aoLigar]; a nota só é
 * enviada ao tocar em **Salvar nota** (digitar não dispara uma chamada a cada letra).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaDoDestaque(
    destaque: Destaque,
    elementos: RepositorioDeElementos,
    aoMudarCor: (CorDeDestaque) -> Unit,
    aoMudarNota: (String?) -> Unit,
    aoLigar: (elementoId: Int?) -> Unit,
    aoRemover: () -> Unit,
    aoFechar: () -> Unit,
) {
    var nota by remember(destaque.id) { mutableStateOf(destaque.nota.orEmpty()) }
    var escolhendo by remember { mutableStateOf(false) }
    val doLivro by produzirElementos(elementos, destaque.livro_id)
    val ligado = doLivro.firstOrNull { it.id == destaque.elemento_id }

    ModalBottomSheet(onDismissRequest = aoFechar) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Trecho destacado", style = MaterialTheme.typography.titleLarge)
            Text("“${destaque.trecho}”", style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CorDeDestaque.entries.forEach { cor ->
                    val selecionada = cor == destaque.corDoDestaque
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(corDeFundoDoDestaque(cor))
                            .then(if (selecionada) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                            .clickable(onClickLabel = "Cor ${cor.rotulo}") { aoMudarCor(cor) },
                    )
                }
            }

            OutlinedTextField(
                value = nota,
                onValueChange = { nota = it.take(LIMITE_DA_NOTA_DO_DESTAQUE) },
                label = { Text("Nota") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            if (nota.trim() != destaque.nota.orEmpty()) {
                TextButton(onClick = { aoMudarNota(nota) }) { Text("Salvar nota") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Elemento", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        ligado?.nome ?: if (destaque.elemento_id != null) "Ligado a um elemento" else "Nenhum",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                TextButton(onClick = { escolhendo = true }) { Text(if (destaque.elemento_id == null) "Ligar a um elemento" else "Trocar") }
                if (destaque.elemento_id != null) {
                    BotaoDeIcone(Icons.Filled.LinkOff, "Desligar do elemento", aoTocar = { aoLigar(null) })
                }
            }

            TextButton(onClick = aoRemover) {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                Text("  Remover destaque", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (escolhendo) {
        AlertDialog(
            onDismissRequest = { escolhendo = false },
            title = { Text("Ligar a qual elemento?") },
            text = {
                if (doLivro.isEmpty()) Text("Este livro ainda não tem elementos.")
                else LazyColumn {
                    items(doLivro, key = { it.id }) { e ->
                        Text(
                            e.nome,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { aoLigar(e.id); escolhendo = false }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { escolhendo = false }) { Text("Cancelar") } },
        )
    }
}

/** O limite de uma nota de destaque, o mesmo do servidor. */
const val LIMITE_DA_NOTA_DO_DESTAQUE = 1000

/** Os elementos do livro, lidos uma vez ao abrir a folha; falhar deixa a lista vazia (a folha continua útil sem o nome do elemento). */
@Composable
private fun produzirElementos(repositorio: RepositorioDeElementos, livroId: Int) =
    androidx.compose.runtime.produceState(initialValue = emptyList<ElementoDoLivro>(), livroId) {
        (repositorio.listar(livroId) as? ResultadoDaChamada.Sucesso)?.let { value = it.dado.sortedBy { e -> e.nome.lowercase() } }
    }
