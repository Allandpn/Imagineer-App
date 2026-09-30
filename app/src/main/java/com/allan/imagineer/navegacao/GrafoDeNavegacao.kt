package com.allan.imagineer.navegacao

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.allan.imagineer.telas.AcaoProvisoria
import com.allan.imagineer.telas.TelaProvisoria

/**
 * O grafo de navegação do app: quais telas existem e como se chega a cada uma.
 *
 * A pilha é hierárquica (item 7.0): Biblioteca → Livro → Capítulo → Frame →
 * Prompt. Elementos, Perfis e Configuração ficam fora da pilha principal e
 * podem ser abertos de vários pontos.
 *
 * Por enquanto, todas as telas são provisórias (incremento 1): mostram só o
 * nome, os parâmetros recebidos e botões que provam que a navegação funciona.
 */
@Composable
fun GrafoDeNavegacao() {
    val controle = rememberNavController()
    val voltar: () -> Unit = { controle.popBackStack() }

    NavHost(navController = controle, startDestination = Biblioteca) {
        composable<Biblioteca> {
            TelaProvisoria(
                titulo = "Biblioteca",
                descricao = "Tela inicial: a lista de livros importados (item 7.2).",
                acoes = listOf(
                    AcaoProvisoria("Abrir um livro (id 1)") { controle.navigate(Livro(livroId = 1)) },
                    AcaoProvisoria("Perfis de renderização") { controle.navigate(PerfisDeRenderizacao) },
                    AcaoProvisoria("Configuração") { controle.navigate(Configuracao) },
                ),
            )
        }
        composable<Livro> { entrada ->
            val destino = entrada.toRoute<Livro>()
            TelaProvisoria(
                titulo = "Livro ${destino.livroId}",
                descricao = "Detalhe do livro e lista de capítulos (item 7.4).",
                acoes = listOf(
                    AcaoProvisoria("Abrir um capítulo (id 10)") { controle.navigate(Capitulo(capituloId = 10)) },
                    AcaoProvisoria("Elementos do livro") { controle.navigate(ElementosDoLivro(destino.livroId)) },
                ),
                aoVoltar = voltar,
            )
        }
        composable<Capitulo> { entrada ->
            val destino = entrada.toRoute<Capitulo>()
            TelaProvisoria(
                titulo = "Capítulo ${destino.capituloId}",
                descricao = "Texto, sugestões e cenas do capítulo (item 7.5).",
                acoes = listOf(
                    AcaoProvisoria("Abrir um frame (id 100)") { controle.navigate(Frame(frameId = 100)) },
                ),
                aoVoltar = voltar,
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
                aoVoltar = voltar,
            )
        }
        composable<Prompt> { entrada ->
            val destino = entrada.toRoute<Prompt>()
            val qual = destino.promptId?.let { "prompt existente $it" } ?: "prompt novo"
            TelaProvisoria(
                titulo = "Prompt",
                descricao = "Frame ${destino.frameId}, $qual (item 7.7).",
                aoVoltar = voltar,
            )
        }
        composable<ElementosDoLivro> { entrada ->
            val destino = entrada.toRoute<ElementosDoLivro>()
            TelaProvisoria(
                titulo = "Elementos",
                descricao = "Elementos do livro ${destino.livroId} (item 7.8).",
                aoVoltar = voltar,
            )
        }
        composable<PerfisDeRenderizacao> {
            TelaProvisoria(
                titulo = "Perfis de renderização",
                descricao = "Estilo visual dos livros (item 7.9).",
                aoVoltar = voltar,
            )
        }
        composable<Configuracao> {
            TelaProvisoria(
                titulo = "Configuração",
                descricao = "Endereço do servidor, chave própria e modelos (item 7.10).",
                aoVoltar = voltar,
            )
        }
    }
}
