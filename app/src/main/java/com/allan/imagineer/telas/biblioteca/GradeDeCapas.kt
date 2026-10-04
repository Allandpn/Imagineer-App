package com.allan.imagineer.telas.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.enderecoDaCapa
import com.allan.imagineer.telas.capitulo.painel.urlDoServidorEmUso

/** Quantas capas por linha: três, em qualquer tela (pedido do Allan); a largura de cada uma acompanha a tela. */
const val CAPAS_POR_LINHA = 3

/**
 * A biblioteca **em capas**, como no Kindle (CP4): só a capa de cada livro, **três por linha**. Tocar abre o livro; o menu ⋮ sobre a
 * capa remove.
 */
@Composable
fun GradeDeLivros(
    livros: List<LivroResumo>,
    aoAbrirLivro: (livroId: Int) -> Unit,
    aoPedirRemocao: (LivroResumo) -> Unit,
) {
    val urlBase = urlDoServidorEmUso()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(CAPAS_POR_LINHA),
            modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(livros, key = { it.id }) { livro ->
                CapaNaGrade(livro, urlBase, aoTocar = { aoAbrirLivro(livro.id) }, aoPedirRemocao = { aoPedirRemocao(livro) })
            }
        }
    }
}

@Composable
private fun CapaNaGrade(livro: LivroResumo, urlBase: String?, aoTocar: () -> Unit, aoPedirRemocao: () -> Unit) {
    // Só a capa, com o ⋮ de remover (sem legenda embaixo): a capa sem imagem já traz o título e o autor dentro dela.
    Box(modifier = Modifier.clickable(onClick = aoTocar)) {
        CapaDoLivro(livro, urlBase, Modifier.fillMaxWidth().aspectRatio(PROPORCAO_DA_CAPA).shadow(4.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)))
        MenuSobreACapa(aoPedirRemocao, Modifier.align(Alignment.TopEnd))
    }
}

/** A proporção de uma capa de livro (largura por altura): 2 por 3. */
const val PROPORCAO_DA_CAPA = 2f / 3f

/**
 * A capa do livro: a imagem que o servidor guarda ([LivroResumo.tem_capa]) ou, sem ela (ou enquanto carrega / se falhar), um **cartão com
 * o título e o autor**, de uma cor que sai do título (cada livro com a sua).
 */
@Composable
fun CapaDoLivro(livro: LivroResumo, urlBase: String?, modifier: Modifier = Modifier) {
    if (livro.tem_capa && urlBase != null) {
        SubcomposeAsyncImage(
            model = enderecoDaCapa(urlBase, livro.id, livro.revisao),
            contentDescription = "Capa de ${livro.titulo}",
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = { CapaSemImagem(livro, Modifier.fillMaxSize()) },
            error = { CapaSemImagem(livro, Modifier.fillMaxSize()) },
        )
    } else {
        CapaSemImagem(livro, modifier)
    }
}

@Composable
private fun CapaSemImagem(livro: LivroResumo, modifier: Modifier) {
    Box(
        modifier = modifier.background(corDaCapaSemImagem(livro.titulo)).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                livro.titulo,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            livro.autor?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A cor do cartão de um livro sem capa: um matiz que sai do título, escuro o bastante para o texto branco. */
fun corDaCapaSemImagem(titulo: String): Color = Color.hsv(matizDoTitulo(titulo), 0.45f, 0.45f)

/** O matiz (0 a 360) de um título: o mesmo título dá sempre a mesma cor, e títulos diferentes se espalham pelo círculo. */
fun matizDoTitulo(titulo: String): Float = ((titulo.fold(7) { soma, c -> soma * 31 + c.code } and 0x7fffffff) % 360).toFloat()

@Composable
private fun MenuSobreACapa(aoPedirRemocao: () -> Unit, modifier: Modifier) {
    var aberto by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(color = Color.Black.copy(alpha = 0.35f), shape = CircleShape, modifier = Modifier.padding(4.dp).size(28.dp)) {
            IconButton(onClick = { aberto = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            DropdownMenuItem(text = { Text("Remover") }, onClick = { aberto = false; aoPedirRemocao() })
        }
    }
}
