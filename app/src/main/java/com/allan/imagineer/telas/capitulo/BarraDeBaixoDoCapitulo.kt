package com.allan.imagineer.telas.capitulo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/** Os quatro destinos da barra de baixo do capítulo (BC1). Cada ícone leva a [descricao] como legenda pequena embaixo. */
enum class DestinoDaBarraDoCapitulo(val icone: ImageVector, val descricao: String, val precisaDoLivro: Boolean) {
    BIBLIOTECA(Icons.Filled.LibraryBooks, "Biblioteca", precisaDoLivro = false),
    CONFIGURACOES(Icons.Filled.Settings, "Configurações", precisaDoLivro = false),
    ELEMENTOS(Icons.Filled.Groups, "Elementos", precisaDoLivro = true),
    CENAS(Icons.Filled.Movie, "Cenas", precisaDoLivro = true),
}

/**
 * Quando a barra de baixo do capítulo aparece (BC2, BC3): junto com a barra do topo ([barraDoTopoVisivel], que segue a rolagem),
 * mas nunca com o painel de IA em tela cheia por cima do texto, nem nos modos de marcar parágrafos (LV4) e de posicionar (PM1),
 * que têm o próprio aviso ou barra embaixo.
 */
fun barraDeBaixoVisivel(barraDoTopoVisivel: Boolean, painelCheio: Boolean, marcandoParagrafos: Boolean, posicionando: Boolean): Boolean =
    barraDoTopoVisivel && !painelCheio && !marcandoParagrafos && !posicionando

/**
 * A barra de navegação do capítulo (BC1): como a do livro, ícones com a legenda pequena embaixo e nenhum marcado.
 * Elementos e Cenas ficam apagados enquanto o livro do capítulo não é conhecido ([livroConhecido], BC4).
 */
@Composable
fun BarraDeBaixoDoCapitulo(livroConhecido: Boolean, aoIr: (DestinoDaBarraDoCapitulo) -> Unit) {
    NavigationBar {
        DestinoDaBarraDoCapitulo.entries.forEach { destino ->
            NavigationBarItem(
                selected = false,
                onClick = { aoIr(destino) },
                enabled = livroConhecido || !destino.precisaDoLivro,
                icon = { Icon(destino.icone, contentDescription = destino.descricao) },
                label = { Text(destino.descricao, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                alwaysShowLabel = true,
            )
        }
    }
}
