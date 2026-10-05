package com.allan.imagineer.telas.favoritos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.AlvoDeFavorito
import com.allan.imagineer.rede.Favorito
import com.allan.imagineer.rede.RepositorioDeFavoritos
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.comum.BotaoDeIcone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Os favoritos de UM livro (RL36): uma lista só, lida uma vez, que cada estrela consulta e muda. Favoritar precisa do servidor (RL37).

data class EstadoDosFavoritos(
    val favoritos: List<Favorito> = emptyList(),
    val carregados: Boolean = false,
    /** Um recado curto (a falha ao favoritar); nulo quando não há nada a dizer. */
    val aviso: String? = null,
)

/** O favorito que já existe para o [alvo], ou `null`. */
fun favoritoDe(favoritos: List<Favorito>, alvo: AlvoDeFavorito): Favorito? = favoritos.firstOrNull { alvo.combina(it) }

class FavoritosDoLivroViewModel(private val livroId: Int, private val repositorio: RepositorioDeFavoritos) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDosFavoritos())
    val estado: StateFlow<EstadoDosFavoritos> = _estado.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            when (val r = repositorio.listar(livroId)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(favoritos = r.dado, carregados = true) }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(carregados = true) }  // sem servidor: as estrelas ficam vazias
            }
        }
    }

    /** Favorita se não é favorito; desfavorita se é. A estrela muda na hora e o servidor confirma; se falhar, volta e avisa. */
    fun alternar(alvo: AlvoDeFavorito) {
        val existente = favoritoDe(_estado.value.favoritos, alvo)
        viewModelScope.launch {
            if (existente != null) {
                _estado.update { it.copy(favoritos = it.favoritos - existente) }
                if (repositorio.desfavoritar(existente.id) is ResultadoDaChamada.Falha) {
                    _estado.update { it.copy(favoritos = listOf(existente) + it.favoritos, aviso = "Não consegui desfavoritar.") }
                }
            } else {
                when (val r = repositorio.favoritar(livroId, alvo)) {
                    is ResultadoDaChamada.Sucesso -> _estado.update { atual ->
                        if (atual.favoritos.any { it.id == r.dado.id }) atual else atual.copy(favoritos = listOf(r.dado) + atual.favoritos)
                    }
                    is ResultadoDaChamada.Falha -> _estado.update { it.copy(aviso = "Não consegui favoritar: ${r.motivo}") }
                }
            }
        }
    }

    fun avisoLido() {
        _estado.update { it.copy(aviso = null) }
    }
}

/** O que cada estrela precisa: a lista de favoritos do livro e como alternar um alvo. */
class ControleDeFavoritos(val favoritos: List<Favorito>, val alternar: (AlvoDeFavorito) -> Unit) {
    fun de(alvo: AlvoDeFavorito): Favorito? = favoritoDe(favoritos, alvo)
}

/** Os favoritos do livro da tela em que se está; `null` fora de uma tela que os forneça (então a estrela não aparece). */
val LocalFavoritos = compositionLocalOf<ControleDeFavoritos?> { null }

/** Fornece [LocalFavoritos] ao [conteudo], com os favoritos do livro [livroId] (lidos uma vez ao entrar). */
@Composable
fun ComFavoritosDoLivro(livroId: Int, conteudo: @Composable () -> Unit) {
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
    val controle = androidx.compose.runtime.remember(estado.favoritos, viewModel) { ControleDeFavoritos(estado.favoritos, viewModel::alternar) }
    CompositionLocalProvider(LocalFavoritos provides controle) { conteudo() }
}

/** A estrela de favoritar o [alvo]: cheia se já é favorito, só o contorno se não. Não aparece sem [LocalFavoritos]. */
@Composable
fun BotaoDeFavorito(alvo: AlvoDeFavorito, modifier: Modifier = Modifier, cor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    val controle = LocalFavoritos.current ?: return
    val favorito = controle.de(alvo) != null
    BotaoDeIcone(
        icone = if (favorito) Icons.Filled.Star else Icons.Outlined.StarBorder,
        descricao = if (favorito) "Desfavoritar" else "Favoritar",
        aoTocar = { controle.alternar(alvo) },
        modifier = modifier,
        cor = cor,
    )
}
