package com.allan.imagineer.navegacao

import com.allan.imagineer.telas.livro.TelaDestaquesDoLivro
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import com.allan.imagineer.local.ETIQUETA_SEM_CONEXAO
import com.allan.imagineer.telas.menu.TelaArmazenamento
import com.allan.imagineer.telas.livro.AlvoNoCapitulo
import com.allan.imagineer.telas.livro.DestinoDoLivro
import com.allan.imagineer.telas.livro.TipoDaListaDoLivro
import com.allan.imagineer.telas.livro.TelaDaListaDoLivro
import com.allan.imagineer.telas.menu.TelaConfiguracoes
import com.allan.imagineer.telas.menu.TelaModelos
import com.allan.imagineer.telas.menu.TelaCustos
import com.allan.imagineer.telas.menu.TelaPerfil
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
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
import com.allan.imagineer.telas.lixeira.TelaLixeira
import com.allan.imagineer.telas.menu.TelaModelosDeImagem
import com.allan.imagineer.telas.elementos.TelaElementos
import com.allan.imagineer.telas.pesquisa.TelaPesquisa
import com.allan.imagineer.telas.elementos.TelaFichaDoElemento
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
/**
 * A tela desta entrada é a que o usuário está vendo (e pode tocar)? Durante uma transição de navegação a
 * entrada de saída ainda está de pé por alguns instantes, e um toque nela dispararia uma segunda navegação.
 */
private fun NavBackStackEntry.estaNaFrente(): Boolean = lifecycle.currentState == Lifecycle.State.RESUMED

/**
 * Um ícone da barra de baixo do livro (LY1): vai para a tela irmã **sem empilhar** — tudo acima da lista de capítulos sai da pilha, então
 * voltar leva sempre à lista. A tela do capítulo e a ficha não passam por aqui (ficam em tela cheia).
 */
private fun irParaODoLivro(controle: NavHostController, livroId: Int, destino: DestinoDoLivro, entrada: NavBackStackEntry) {
    if (!entrada.estaNaFrente()) return
    val opcoes: androidx.navigation.NavOptionsBuilder.() -> Unit = {
        popUpTo<Livro> { inclusive = false }
        launchSingleTop = true
    }
    when (destino) {
        DestinoDoLivro.ELEMENTOS -> controle.navigate(ElementosDoLivro(livroId), opcoes)
        DestinoDoLivro.CENAS -> controle.navigate(CenasDoLivro(livroId), opcoes)
        DestinoDoLivro.PENDENCIAS -> controle.navigate(PendenciasDoLivro(livroId), opcoes)
        DestinoDoLivro.ARQUIVADOS -> controle.navigate(CapitulosArquivados(livroId), opcoes)
    }
}

/** Abre o capítulo de um item das listas Pendências e Cenas, já com o que abrir nele (LY7, LY8). */
private fun abrirNoCapitulo(controle: NavHostController, alvo: AlvoNoCapitulo) {
    controle.navigate(Capitulo(alvo.capituloId, abrirFrameId = alvo.abrirFrameId, abrirRotulo = alvo.abrirRotulo, abrirPainel = alvo.abrirPainel))
}

@Composable
fun GrafoDeNavegacao() {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp

    // null = ainda lendo; true/false = já se sabe se há URL salva.
    val temUrlSalva: Boolean? by produceState<Boolean?>(initialValue = null) {
        value = aplicacao.armazenamento.urlDoServidor.first() != null
    }

    val jaSeSabe = temUrlSalva ?: return
    val controle = rememberNavController()

    // D1: o aviso de "análise concluída" aparece de qualquer tela.
    val avisos = remember { SnackbarHostState() }
    AvisadorDeAnalises(controle, avisos)

    Box(Modifier.fillMaxSize()) {
    NavHost(
        navController = controle,
        startDestination = if (jaSeSabe) Biblioteca else Configuracao,
    ) {
        composable<Biblioteca> {
            TelaBiblioteca(
                aoAbrirLivro = { livroId -> controle.navigate(Livro(livroId)) },
                aoAbrirPerfil = { controle.navigate(Perfil) },
                aoAbrirConfiguracoes = { controle.navigate(Configuracoes) },
                aoAbrirLixeira = { controle.navigate(Lixeira) },
                aoAbrirCustos = { controle.navigate(Custos) },
            )
        }
        composable<Perfil> { TelaPerfil(aoVoltar = { controle.popBackStack() }) }
        composable<Custos> { TelaCustos(aoVoltar = { controle.popBackStack() }) }
        composable<ModelosDeIa> { entrada ->
            TelaModelos(
                aoVoltar = { controle.popBackStack() },
                aoEscolherModeloDeImagem = { if (entrada.estaNaFrente()) controle.navigate(ModelosDeImagem) },
            )
        }
        composable<ModelosDeImagem> { TelaModelosDeImagem(aoVoltar = { controle.popBackStack() }) }
        composable<Armazenamento> { TelaArmazenamento(aoVoltar = { controle.popBackStack() }) }
        composable<Configuracoes> { entrada ->
            TelaConfiguracoes(
                aoVoltar = { controle.popBackStack() },
                aoAbrirServidor = { if (entrada.estaNaFrente()) controle.navigate(Configuracao) },
                aoAbrirPerfisDeRenderizacao = { if (entrada.estaNaFrente()) controle.navigate(PerfisDeRenderizacao) },
                aoAbrirModelos = { if (entrada.estaNaFrente()) controle.navigate(ModelosDeIa) },
                aoAbrirArmazenamento = { if (entrada.estaNaFrente()) controle.navigate(Armazenamento) },
            )
        }
        composable<Lixeira> {
            TelaLixeira(aoVoltar = { controle.popBackStack() })
        }
        composable<LixeiraDoLivro> { entrada ->
            TelaLixeira(livroId = entrada.toRoute<LixeiraDoLivro>().livroId, aoVoltar = { controle.popBackStack() })
        }
        composable<Livro> { entrada ->
            val destino = entrada.toRoute<Livro>()
            TelaLivro(
                livroId = destino.livroId,
                aoIrParaODoLivro = { irParaODoLivro(controle, destino.livroId, it, entrada) },
                aoVoltar = { controle.popBackStack() },
                aoAbrirCapitulo = { capituloId -> controle.navigate(Capitulo(capituloId)) },
                aoAbrirPesquisa = { controle.navigate(Pesquisa(destino.livroId)) },
                aoAbrirLixeira = { controle.navigate(LixeiraDoLivro(destino.livroId)) },
                aoAbrirDestaques = { controle.navigate(DestaquesDoLivro(destino.livroId)) },
                aoContinuarLendo = { capituloId, posicao -> controle.navigate(Capitulo(capituloId, irParaPosicao = posicao)) },
            )
        }
        composable<DestaquesDoLivro> { entrada ->
            TelaDestaquesDoLivro(
                livroId = entrada.toRoute<DestaquesDoLivro>().livroId,
                aoVoltar = { controle.popBackStack() },
                aoAbrir = { capituloId, posicao -> if (entrada.estaNaFrente()) controle.navigate(Capitulo(capituloId, irParaPosicao = posicao)) },
            )
        }
        composable<CenasDoLivro> { entrada ->
            val destino = entrada.toRoute<CenasDoLivro>()
            TelaDaListaDoLivro(
                livroId = destino.livroId,
                tipo = TipoDaListaDoLivro.CENAS,
                aoVoltar = { controle.popBackStack() },
                aoAbrir = { alvo -> if (entrada.estaNaFrente()) abrirNoCapitulo(controle, alvo) },
                aoIrParaODoLivro = { irParaODoLivro(controle, destino.livroId, it, entrada) },
            )
        }
        composable<PendenciasDoLivro> { entrada ->
            val destino = entrada.toRoute<PendenciasDoLivro>()
            TelaDaListaDoLivro(
                livroId = destino.livroId,
                tipo = TipoDaListaDoLivro.PENDENCIAS,
                aoVoltar = { controle.popBackStack() },
                aoAbrir = { alvo -> if (entrada.estaNaFrente()) abrirNoCapitulo(controle, alvo) },
                aoIrParaODoLivro = { irParaODoLivro(controle, destino.livroId, it, entrada) },
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
                aoIrParaODoLivro = { irParaODoLivro(controle, destino.livroId, it, entrada) },
                viewModel = livroViewModel(destino.livroId, dono = entradaDoLivro),
            )
        }
        composable<Capitulo> { entrada ->
            val destino = entrada.toRoute<Capitulo>()
            TelaCapitulo(
                capituloId = destino.capituloId,
                abrirElementoId = destino.abrirElementoId,
                irParaPosicao = destino.irParaPosicao,
                abrirFrameId = destino.abrirFrameId,
                abrirRotulo = destino.abrirRotulo,
                abrirPainel = destino.abrirPainel,
                aoPesquisar = { livroId, capituloId -> if (entrada.estaNaFrente()) controle.navigate(Pesquisa(livroId, capituloId)) },
                aoVoltar = { controle.popBackStack() },
                aoAbrirFicha = { elementoId, livroId, capituloId ->
                    // Só navega com esta tela na frente: um segundo toque durante a transição (ou um toque numa
                    // janela que ainda estava de pé) não empilha uma segunda ficha.
                    if (entrada.estaNaFrente()) {
                        controle.navigate(FichaDoElemento(elementoId, livroId, capituloId)) { launchSingleTop = true }
                    }
                },
            )
        }
        composable<Pesquisa> { entrada ->
            val destino = entrada.toRoute<Pesquisa>()
            TelaPesquisa(
                livroId = destino.livroId,
                capituloId = destino.capituloId,
                aoVoltar = { controle.popBackStack() },
                aoAbrirOcorrencia = { ocorrencia, _ ->
                    if (entrada.estaNaFrente()) {
                        controle.navigate(Capitulo(ocorrencia.capitulo_id, irParaPosicao = ocorrencia.inicio_do_paragrafo))
                    }
                },
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
            TelaElementos(
                livroId = destino.livroId,
                aoVoltar = { controle.popBackStack() },
                aoIrParaODoLivro = { irParaODoLivro(controle, destino.livroId, it, entrada) },
                aoAbrirFicha = { elementoId ->
                    if (entrada.estaNaFrente()) {
                        controle.navigate(FichaDoElemento(elementoId, destino.livroId)) { launchSingleTop = true }
                    }
                },
                aoAbrirNoCapitulo = { capituloId, elementoId ->
                    if (entrada.estaNaFrente()) controle.navigate(Capitulo(capituloId, elementoId))
                },
            )
        }
        composable<FichaDoElemento> { entrada ->
            val destino = entrada.toRoute<FichaDoElemento>()
            TelaFichaDoElemento(
                elementoId = destino.elementoId,
                capituloId = destino.capituloId,
                // Só volta uma vez: um segundo toque na seta durante a transição desempilharia também o capítulo.
                aoVoltar = { if (entrada.estaNaFrente()) controle.popBackStack() },
                aoAbrirPassagem = { capituloIdDaPassagem, posicao ->
                    if (entrada.estaNaFrente()) controle.navigate(Capitulo(capituloIdDaPassagem, irParaPosicao = posicao))
                },
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
    // PL8: sem conexão, uma etiqueta pequena no alto de qualquer tela; some quando a rede volta.
    val online by aplicacao.conexao.online.collectAsState()
    if (!online) {
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 4.dp),
        ) {
            Text(ETIQUETA_SEM_CONEXAO, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
    // O aviso: centralizado, bem embaixo e translúcido, para não esconder o texto que se lê (feedback do Allan).
    SnackbarHost(
        hostState = avisos,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
    ) { dados ->
        Snackbar(
            snackbarData = dados,
            modifier = Modifier.widthIn(max = 520.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inverseOnSurface,
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
