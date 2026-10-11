package com.allan.imagineer.telas.menu

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.allan.imagineer.ImagineerApp
import kotlinx.coroutines.launch

// Os modelos favoritos (item 7.5g, CF3): uma estrela em cada modelo das listas de escolha, e os favoritos no topo. Ficam no aparelho.

/** As categorias de lista de modelos que têm favoritos: cada uma guarda o seu conjunto. */
object CategoriaDeModelo {
    const val TEXTO = "texto"
    const val IMAGEM = "imagem"
    const val NARRACAO = "narracao"
}

/** Lê os favoritos guardados (um id por linha, sem linhas em branco). Sem nada guardado, nenhum. */
fun favoritosDeTexto(texto: String?): Set<String> =
    texto.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.toCollection(LinkedHashSet())

/** Escreve os favoritos para guardar: um id por linha. */
fun favoritosParaTexto(favoritos: Set<String>): String = favoritos.joinToString("\n")

/** Marca o modelo [id] como favorito, ou tira a marca se já era (CF3). */
fun alternarFavorito(favoritos: Set<String>, id: String): Set<String> =
    if (id in favoritos) favoritos - id else LinkedHashSet(favoritos).apply { add(id) }

/**
 * Os [itens] com os **favoritos primeiro** (CF3), cada grupo na ordem em que já estava. Um modelo aparece em um lugar só. [id] diz o
 * id de cada item.
 */
fun <T> ordenarComFavoritos(itens: List<T>, favoritos: Set<String>, id: (T) -> String): List<T> {
    val (dosFavoritos, outros) = itens.partition { id(it) in favoritos }
    return dosFavoritos + outros
}

/** Quantos dos [itens] são favoritos: onde a lista vira "Outros". */
fun <T> quantosSaoFavoritos(itens: List<T>, favoritos: Set<String>, id: (T) -> String): Int = itens.count { id(it) in favoritos }

/** Os favoritos de uma categoria, para a tela: o conjunto e como marcar ou desmarcar um modelo. */
class FavoritosDeModelos(val conjunto: Set<String>, val alternar: (String) -> Unit) {
    fun ehFavorito(id: String): Boolean = id in conjunto
}

/** Os favoritos guardados da [categoria], atualizados na tela a cada mudança. */
@Composable
fun rememberFavoritosDeModelos(categoria: String): FavoritosDeModelos {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val armazenamento = aplicacao.armazenamento
    val guardado by armazenamento.modelosFavoritos(categoria).collectAsState(initial = null)
    val escopo = rememberCoroutineScope()
    val conjunto = remember(guardado) { favoritosDeTexto(guardado) }
    return remember(conjunto, categoria) {
        FavoritosDeModelos(conjunto) { id ->
            escopo.launch { armazenamento.salvarModelosFavoritos(categoria, favoritosParaTexto(alternarFavorito(conjunto, id))) }
        }
    }
}

/** A estrela de um modelo (CF3): cheia se é favorito, só o contorno se não. */
@Composable
fun EstrelaDeModelo(favorito: Boolean, aoAlternar: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = aoAlternar, modifier = modifier) {
        Icon(
            if (favorito) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (favorito) "Tirar dos favoritos" else "Marcar como favorito",
            tint = if (favorito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** O título de um grupo numa lista de modelos: "Favoritos" ou "Outros modelos" (CF3). */
@Composable
fun TituloDeGrupoDeModelos(titulo: String) {
    androidx.compose.material3.Text(
        titulo,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}
