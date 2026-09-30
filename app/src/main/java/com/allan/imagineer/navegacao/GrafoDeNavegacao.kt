package com.allan.imagineer.navegacao

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.telas.AcaoProvisoria
import com.allan.imagineer.telas.TelaProvisoria
import com.allan.imagineer.telas.biblioteca.TelaBiblioteca
import com.allan.imagineer.telas.capitulo.TelaCapitulo
import com.allan.imagineer.telas.configuracao.TelaConfiguracao
import com.allan.imagineer.telas.livro.TelaCapitulosArquivados
import com.allan.imagineer.telas.livro.TelaLivro
import com.allan.imagineer.telas.livro.livroViewModel
import kotlinx.coroutines.flow.first

/**
 * O grafo de navegação do app: quais telas existem e como se chega a cada uma.
 *
 * A pilha é hierárquica (item 7.0): Biblioteca → Livro → Capítulo → Frame →
 * Prompt. Elementos, Perfis e Configuração ficam fora da pilha principal e
 * podem ser abertos de vários pontos.
 *
 * Biblioteca, Livro, Capítulo e Configuração já são reais; as demais ainda são provisórias
 * (mostram só o nome e os parâmetros recebidos) e serão trocadas incremento a
 * incremento.
 *
 * **Primeira abertura** (item 7.3a): sem URL salva, o app começa na Configuração
 * em vez da Biblioteca. Ler o DataStore é assíncrono, então, até a leitura
 * terminar, nada é desenhado — assim a Biblioteca não pisca antes de a tela
 * mudar para a Configuração.
 */
@Composable
fun GrafoDeNavegacao() {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp

    // null = ainda lendo; true/false = já se sabe se há URL salva.
    val temUrlSalva: Boolean? by produceState<Boolean?>(initialValue = null) {
        value = aplicacao.armazenamento.urlDoServidor.first() != null
    }

    val jaSeSabe = temUrlSalva ?: return
    val controle = rememberNavController()

    NavHost(
        navController = controle,
        startDestination = if (jaSeSabe) Biblioteca else Configuracao,
    ) {
        composable<Biblioteca> {
            TelaBiblioteca(
                aoAbrirLivro = { livroId -> controle.navigate(Livro(livroId)) },
                aoAbrirConfiguracao = { controle.navigate(Configuracao) },
                aoAbrirPerfis = { controle.navigate(PerfisDeRenderizacao) },
            )
        }
        composable<Livro> { entrada ->
            val destino = entrada.toRoute<Livro>()
            TelaLivro(
                livroId = destino.livroId,
                aoVoltar = { controle.popBackStack() },
                aoAbrirCapitulo = { capituloId -> controle.navigate(Capitulo(capituloId)) },
                aoAbrirElementos = { controle.navigate(ElementosDoLivro(destino.livroId)) },
                aoAbrirPerfis = { controle.navigate(PerfisDeRenderizacao) },
                aoAbrirArquivados = { controle.navigate(CapitulosArquivados(destino.livroId)) },
            )
        }
        composable<CapitulosArquivados> { entrada ->
            val destino = entrada.toRoute<CapitulosArquivados>()
            // Compartilha o ViewModel da tela de Livro (escopado à entrada dela na
            // pilha): o que se arquiva lá e o que se restaura aqui é sempre o mesmo livro.
            val entradaDoLivro = remember(entrada) { controle.getBackStackEntry<Livro>() }
            TelaCapitulosArquivados(
                aoVoltar = { controle.popBackStack() },
                aoAbrirCapitulo = { capituloId -> controle.navigate(Capitulo(capituloId)) },
                viewModel = livroViewModel(destino.livroId, dono = entradaDoLivro),
            )
        }
        composable<Capitulo> { entrada ->
            val destino = entrada.toRoute<Capitulo>()
            TelaCapitulo(
                capituloId = destino.capituloId,
                aoVoltar = { controle.popBackStack() },
            )
        }
        composable<Frame> { entrada ->
            val destino = entrada.toRoute<Frame>()
            TelaProvisoria(
                titulo = "Frame ${destino.frameId}",
                descricao = "Retrato ou cena, com os elementos ligados (item 7.6).",
                acoes = listOf(
                    AcaoProvisoria("Gerar um prompt") { controle.navigate(Prompt(frameId = destino.frameId)) },
                ),
                aoVoltar = { controle.popBackStack() },
            )
        }
        composable<Prompt> { entrada ->
            val destino = entrada.toRoute<Prompt>()
            val qual = destino.promptId?.let { "prompt existente $it" } ?: "prompt novo"
            TelaProvisoria(
                titulo = "Prompt",
                descricao = "Frame ${destino.frameId}, $qual (item 7.7).",
                aoVoltar = { controle.popBackStack() },
            )
        }
        composable<ElementosDoLivro> { entrada ->
            val destino = entrada.toRoute<ElementosDoLivro>()
            TelaProvisoria(
                titulo = "Elementos",
                descricao = "Elementos do livro ${destino.livroId} (item 7.8).",
                aoVoltar = { controle.popBackStack() },
            )
        }
        composable<PerfisDeRenderizacao> {
            TelaProvisoria(
                titulo = "Perfis de renderização",
                descricao = "Estilo visual dos livros (item 7.9).",
                aoVoltar = { controle.popBackStack() },
            )
        }
        composable<Configuracao> {
            TelaConfiguracao(
                aoSalvar = { irParaBibliotecaLimpandoAPilha(controle) },
                // Na primeira abertura a Configuração é a raiz da pilha: sem para onde voltar.
                aoVoltar = if (controle.previousBackStackEntry != null) {
                    { controle.popBackStack() }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Trocar o servidor invalida tudo o que estava aberto (ids de livros de outro
 * servidor não significam nada no novo), então depois de salvar a pilha é zerada
 * e a Biblioteca vira a raiz (item 7.3a).
 */
private fun irParaBibliotecaLimpandoAPilha(controle: NavHostController) {
    controle.navigate(Biblioteca) {
        popUpTo(controle.graph.id) { inclusive = true }
    }
}
