package com.allan.imagineer.telas.favoritos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.Favorito
import com.allan.imagineer.rede.TipoDeFavorito

// A tela "Favoritos" de um livro (RL36): o filtro dentro do livro, aberto pelo menu ⋮. Mostra o que se favoritou *dentro* do livro
// (parágrafos, elementos, cenas, imagens); o próprio livro favorito aparece na biblioteca, não aqui.

/** Os tipos que a tela lista, na ordem dos filtros. */
val TIPOS_NA_TELA_DE_FAVORITOS: List<TipoDeFavorito> = listOf(TipoDeFavorito.PARAGRAFO, TipoDeFavorito.ELEMENTO, TipoDeFavorito.CENA, TipoDeFavorito.IMAGEM)

/**
 * O que a lista mostra: só os tipos de dentro do livro (nem o livro, nem um tipo que o app não conhece) e, se [tipo] não for nulo,
 * só ele. A ordem do servidor (do mais novo ao mais antigo) é mantida.
 */
fun favoritosParaMostrar(favoritos: List<Favorito>, tipo: TipoDeFavorito?): List<Favorito> =
    favoritos.filter { f ->
        val doFavorito = f.tipoDoFavorito
        doFavorito != null && doFavorito in TIPOS_NA_TELA_DE_FAVORITOS && (tipo == null || doFavorito == tipo)
    }

/** A linha de baixo do favorito: onde ele está no livro. */
fun localDoFavorito(favorito: Favorito): String? {
    val capitulo = favorito.ordem_do_capitulo?.let { "Capítulo $it" }
    val titulo = favorito.titulo_do_capitulo?.takeIf { it.isNotBlank() }
    return when {
        capitulo != null && titulo != null -> "$capitulo · $titulo"
        else -> capitulo ?: titulo
    }
}

/** Onde tocar num favorito leva: `null` = não dá para abrir (o capítulo ou o frame não vieram do servidor). */
fun podeAbrirOFavorito(favorito: Favorito): Boolean = when (favorito.tipoDoFavorito) {
    TipoDeFavorito.PARAGRAFO -> favorito.capitulo_id != null && favorito.posicao != null
    TipoDeFavorito.ELEMENTO -> favorito.elemento_id != null
    TipoDeFavorito.CENA, TipoDeFavorito.IMAGEM -> favorito.capitulo_id != null && favorito.frame_id != null
    else -> false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaFavoritosDoLivro(livroId: Int, aoVoltar: () -> Unit, aoAbrir: (Favorito) -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: FavoritosDoLivroViewModel = viewModel(
        key = "favoritos-$livroId",
        factory = viewModelFactory { initializer { FavoritosDoLivroViewModel(livroId, aplicacao.repositorioDeFavoritos) } },
    )
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(viewModel) { viewModel.carregar() }
    val contexto = LocalContext.current
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let { android.widget.Toast.makeText(contexto, it, android.widget.Toast.LENGTH_SHORT).show(); viewModel.avisoLido() }
    }
    var filtro by rememberSaveable { mutableStateOf<String?>(null) }
    val tipo = filtro?.let { TipoDeFavorito.de(it) }
    val lista = favoritosParaMostrar(estado.favoritos, tipo)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favoritos") },
                navigationIcon = { IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { margens ->
        Column(Modifier.fillMaxSize().padding(margens)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = tipo == null, onClick = { filtro = null }, label = { Text("Todos") }) }
                items(TIPOS_NA_TELA_DE_FAVORITOS) { t ->
                    FilterChip(selected = tipo == t, onClick = { filtro = t.name }, label = { Text(t.plural) })
                }
            }
            when {
                !estado.carregados -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                lista.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (tipo == null) "Nada favoritado neste livro ainda. Toque no coração de um parágrafo, elemento, cena ou imagem."
                        else "Nenhum favorito de ${tipo.plural.lowercase()}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(lista, key = { it.id }) { favorito ->
                        LinhaDeFavorito(favorito, aoAbrir = { aoAbrir(favorito) }, aoDesfavoritar = { viewModel.remover(favorito) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaDeFavorito(favorito: Favorito, aoAbrir: () -> Unit, aoDesfavoritar: () -> Unit) {
    val abrivel = podeAbrirOFavorito(favorito)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = abrivel, onClick = aoAbrir).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(favorito.tipoDoFavorito?.rotulo.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(favorito.rotulo, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            localDoFavorito(favorito)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = aoDesfavoritar) { Icon(Icons.Filled.Favorite, contentDescription = "Desfavoritar", tint = MaterialTheme.colorScheme.primary) }
    }
}
