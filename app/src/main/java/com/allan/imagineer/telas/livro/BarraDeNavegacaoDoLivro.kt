package com.allan.imagineer.telas.livro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/** As quatro telas irmãs da área do livro, na barra de baixo (LY1). Só ícones: a [descricao] é para leitores de tela. */
enum class DestinoDoLivro(val icone: ImageVector, val descricao: String) {
    ELEMENTOS(Icons.Filled.Groups, "Elementos"),
    CENAS(Icons.Filled.Movie, "Cenas"),
    PENDENCIAS(Icons.Filled.PendingActions, "Pendências"),
    ARQUIVADOS(Icons.Filled.Archive, "Arquivados"),
}

/**
 * A **barra de navegação** do livro (LY1): quatro ícones, sem texto; o da tela atual fica marcado. Tocar no que já está marcado não
 * faz nada. A tela de Capítulo e a Ficha do elemento não a mostram (ficam em tela cheia).
 */
@Composable
fun BarraDeNavegacaoDoLivro(selecionado: DestinoDoLivro?, aoIr: (DestinoDoLivro) -> Unit) {
    NavigationBar {
        DestinoDoLivro.entries.forEach { destino ->
            NavigationBarItem(
                selected = destino == selecionado,
                onClick = { if (destino != selecionado) aoIr(destino) },
                icon = { Icon(destino.icone, contentDescription = destino.descricao) },
                label = null,
                alwaysShowLabel = false,
            )
        }
    }
}
