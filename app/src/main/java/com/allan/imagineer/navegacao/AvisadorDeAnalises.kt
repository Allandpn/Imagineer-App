package com.allan.imagineer.navegacao

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.analise.LocalDoUsuario
import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.analise.descreverAviso
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.launch

/**
 * O avisador global das análises de IA (defeito D1): quando uma análise termina, mostra um aviso curto **de
 * qualquer tela** — mesmo que o usuário já tenha saído do capítulo (ou do livro) onde a pediu.
 *
 * O texto depende de onde ele está (ver [descreverAviso]): dentro do livro da análise, só o capítulo; fora,
 * o livro e o capítulo. O que dá para testar sem tela mora em [descreverAviso]; aqui fica só a cola com a
 * navegação.
 */
@Composable
fun AvisadorDeAnalises(controle: NavHostController, avisos: SnackbarHostState) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val servico = aplicacao.servicoDeAnalises

    LaunchedEffect(servico) {
        servico.eventos.collect { evento ->
            val local = LocalDoUsuario(livroAbertoNa(controle.currentBackStackEntry, servico))
            val dentroDoLivro = evento.livroId != null && local.livroId == evento.livroId

            // O nome do livro só importa quando se está fora dele; descobri-lo pode ir à rede, então só então.
            val titulo = if (dentroDoLivro || evento.livroId == null) {
                null
            } else {
                (aplicacao.repositorioDeLivros.abrirLivro(evento.livroId) as? ResultadoDaChamada.Sucesso)?.dado?.titulo
            }

            val texto = descreverAviso(evento, local, servico.painelVisivel.value, titulo, servico.modalDoFrameVisivel.value) ?: return@collect
            // Cada aviso em sua própria corrotina: o Snackbar enfileira, e esperar um fechar travaria a coleta.
            launch { avisos.showSnackbar(texto) }
        }
    }
}

/** O livro cuja área está aberta nesta tela, ou `null` (Biblioteca, Configuração, Perfis...). */
private fun livroAbertoNa(entrada: NavBackStackEntry?, servico: ServicoDeAnalises): Int? {
    val destino = entrada?.destination ?: return null
    return when {
        destino.hasRoute<Livro>() -> entrada.toRoute<Livro>().livroId
        destino.hasRoute<ElementosDoLivro>() -> entrada.toRoute<ElementosDoLivro>().livroId
        destino.hasRoute<CapitulosArquivados>() -> entrada.toRoute<CapitulosArquivados>().livroId
        destino.hasRoute<FichaDoElemento>() -> entrada.toRoute<FichaDoElemento>().livroId
        destino.hasRoute<Capitulo>() -> servico.livroDoCapitulo(entrada.toRoute<Capitulo>().capituloId)
        else -> null
    }
}
