package com.allan.imagineer.navegacao

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import com.allan.imagineer.telas.configuracao.TelaConfiguracao
import kotlinx.coroutines.flow.first

/**
 * O grafo de navegação do app: quais telas existem e como se chega a cada uma.
 *
 * A pilha é hierárquica (item 7.0): Biblioteca → Livro → Capítulo → Frame →
 * Prompt. Elementos, Perfis e Configuração ficam fora da pilha principal e
 * podem ser abertos de vários pontos.
 *
 * Biblioteca e Configuração já são reais; as demais ainda são provisórias
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
                aoVoltar = { controle.popBackStack() },
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
