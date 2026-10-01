package com.allan.imagineer.telas.capitulo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.CapituloDetalhe
import com.allan.imagineer.telas.capitulo.painel.AcoesDoPainel
import com.allan.imagineer.telas.capitulo.painel.EstadoDoPainel
import com.allan.imagineer.telas.capitulo.painel.PainelDeIa
import com.allan.imagineer.telas.capitulo.painel.PainelDeIaViewModel
import com.allan.imagineer.telas.capitulo.painel.VisibilidadeDoBotao
import com.allan.imagineer.telas.capitulo.painel.usarAside
import com.allan.imagineer.telas.livro.descreverTamanho
import com.allan.imagineer.telas.livro.tituloDoCapitulo

/**
 * A tela de Capítulo (item 7.5), por enquanto só o leitor de texto (incremento 8).
 *
 * Só monta o ViewModel e entrega o estado para [ConteudoDoCapitulo] — a separação
 * permite pré-visualizar cada estado sem rede.
 */
@Composable
fun TelaCapitulo(
    capituloId: Int,
    aoVoltar: () -> Unit,
    /** Abre a ficha de um elemento (item 7.8). `doCapitulo`: veio de uma sugestão deste capítulo. */
    aoAbrirFicha: (elementoId: Int, livroId: Int, capituloId: Int?) -> Unit,
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: CapituloViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CapituloViewModel(capituloId, aplicacao.repositorioDeCapitulos) }
        },
    )
    val estado by viewModel.estado.collectAsState()

    // O painel de IA tem ViewModel próprio (item 7.5b): o estado da leitura e o da IA crescem
    // por motivos diferentes. Nada dele é pedido ao servidor até o painel ser aberto (P1).
    val painel: PainelDeIaViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PainelDeIaViewModel(capituloId, aplicacao.repositorioDeSugestoes, aplicacao.repositorioDeElementos)
            }
        },
    )
    val estadoDoPainel by painel.estado.collectAsState()
    // rememberSaveable: o painel aberto sobrevive a girar o aparelho (P4).
    var painelAberto by rememberSaveable { mutableStateOf(false) }
    val aside = usarAside(LocalConfiguration.current.screenWidthDp)

    // P3: só a direção da rolagem decide se o botão de IA aparece.
    val visibilidade = remember { VisibilidadeDoBotao() }
    var botaoVisivel by remember { mutableStateOf(true) }

    LaunchedEffect(painelAberto) { if (painelAberto) painel.aoAbrirPainel() }

    // As ações de elemento (10a) precisam saber de qual livro é o capítulo.
    val livroDoCapitulo = (estado as? EstadoDoCapitulo.Pronto)?.capitulo?.livro_id
    LaunchedEffect(livroDoCapitulo) { livroDoCapitulo?.let(painel::definirLivro) }

    // Ao voltar da ficha (E31), o que foi editado lá pode mudar os cartões: o painel relê no lugar.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { painel.aoVoltarDaFicha() }

    // No celular o painel é a tela inteira: voltar leva ao texto, e não para fora do capítulo.
    BackHandler(enabled = painelAberto && !aside) { painelAberto = false }

    // Carrega uma vez. Se a composição recomeçar (girar o tablet), o ViewModel já tem
    // o texto e a chamada não se repete.
    LaunchedEffect(viewModel) { viewModel.carregar() }

    ConteudoDoCapitulo(
        estado = estado,
        aoVoltar = aoVoltar,
        aoTentarDeNovo = viewModel::tentarDeNovo,
        estadoDoPainel = estadoDoPainel,
        acoesDoPainel = AcoesDoPainel(
            aoAnalisar = painel::analisar,
            aoPedirReanalise = painel::pedirReanalise,
            aoConfirmarReanalise = painel::confirmarReanalise,
            aoCancelarReanalise = painel::cancelarReanalise,
            aoTentarDeNovo = painel::tentarDeNovo,
            aoExecutar = painel::executar,
            aoCancelarDialogo = painel::cancelarDialogo,
            aoConfirmarCriacao = painel::confirmarCriacao,
            aoTrocarCriacaoPorVinculo = painel::trocarCriacaoPorVinculo,
            aoEscolherElemento = painel::escolherElemento,
            aoRecarregarLista = painel::recarregarLista,
            aoRestaurar = painel::restaurar,
            aoEscolherFiltro = painel::escolherFiltro,
            aoAbrirFicha = { elementoId, doCapitulo ->
                // Sem o livro (o capítulo ainda não carregou) não há como abrir a ficha.
                livroDoCapitulo?.let { aoAbrirFicha(elementoId, it, capituloId.takeIf { doCapitulo }) }
            },
            aoAlternarApagarEstado = painel::alternarApagarEstado,
            aoConfirmarDesfazer = painel::confirmarDesfazer,
            aoConfirmarDescarte = painel::confirmarDescarte,
        ),
        painelAberto = painelAberto,
        aoAlternarPainel = { painelAberto = !painelAberto },
        botaoVisivel = botaoVisivel,
        aoRolar = { delta, noTopo, noFim ->
            visibilidade.aoRolar(delta, noTopo, noFim)
            botaoVisivel = visibilidade.visivel
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConteudoDoCapitulo(
    estado: EstadoDoCapitulo,
    aoVoltar: () -> Unit,
    aoTentarDeNovo: () -> Unit,
    estadoDoPainel: EstadoDoPainel,
    acoesDoPainel: AcoesDoPainel,
    painelAberto: Boolean,
    aoAlternarPainel: () -> Unit,
    botaoVisivel: Boolean,
    aoRolar: (delta: Float, noTopo: Boolean, noFim: Boolean) -> Unit,
) {
    val titulo = (estado as? EstadoDoCapitulo.Pronto)?.capitulo
        ?.let { tituloDoCapitulo(it.titulo, it.ordem) }
        ?: "Capítulo"
    val aside = usarAside(LocalConfiguration.current.screenWidthDp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
        floatingActionButton = {
            // O botão de IA, no canto inferior direito (P3). Só com o texto na tela.
            if (estado is EstadoDoCapitulo.Pronto) {
                when {
                    // Tablet com o aside aberto: quem fecha é o "X" do próprio painel.
                    painelAberto && aside -> Unit
                    // Celular com o painel aberto: no MESMO canto, o botão de voltar ao texto (P4).
                    painelAberto -> ExtendedFloatingActionButton(
                        onClick = aoAlternarPainel,
                        icon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        text = { Text("Voltar ao texto") },
                    )
                    // Fechado: aparece e some conforme a rolagem.
                    else -> AnimatedVisibility(visible = botaoVisivel, enter = fadeIn(), exit = fadeOut()) {
                        ExtendedFloatingActionButton(
                            onClick = aoAlternarPainel,
                            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                            text = { Text("IA") },
                        )
                    }
                }
            }
        },
    ) { margens ->
        Box(modifier = Modifier.fillMaxSize().padding(margens)) {
            when (estado) {
                EstadoDoCapitulo.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                is EstadoDoCapitulo.Erro -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                    Column(
                        modifier = Modifier.widthIn(max = 600.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            estado.motivo,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = aoTentarDeNovo) { Text("Tentar de novo") }
                    }
                }

                is EstadoDoCapitulo.Pronto -> when {
                    // Tablet: o painel é um aside à direita, dividindo a tela com o texto (P4).
                    aside -> Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { LeitorDeTexto(estado, aoRolar) }
                        if (painelAberto) {
                            VerticalDivider()
                            PainelDeIa(
                                estado = estadoDoPainel,
                                acoes = acoesDoPainel,
                                aoFechar = aoAlternarPainel,
                                modifier = Modifier.width(380.dp).fillMaxHeight(),
                            )
                        }
                    }
                    // Celular: o painel é a tela inteira; o texto some enquanto ele está aberto.
                    painelAberto -> PainelDeIa(
                        estado = estadoDoPainel,
                        acoes = acoesDoPainel,
                        aoFechar = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> LeitorDeTexto(estado, aoRolar)
                }
            }
        }
    }
}

/**
 * O texto do capítulo. Cada parágrafo é **um item de uma lista com rolagem
 * preguiçosa**: um único `Text` com ~110 KB seria medido e desenhado inteiro, mesmo
 * com quase tudo fora da tela.
 */
@Composable
private fun LeitorDeTexto(
    estado: EstadoDoCapitulo.Pronto,
    aoRolar: (delta: Float, noTopo: Boolean, noFim: Boolean) -> Unit,
) {
    val capitulo = estado.capitulo
    val listaDeParagrafos = rememberLazyListState()

    // Escuta a rolagem da lista para o botão de IA (P3). O sinal do deslocamento do Compose é o
    // contrário do que a regra espera (dedo para cima = y negativo = rolando para baixo), por
    // isso o "-consumed.y". No topo ou no fim o botão fica sempre visível.
    val ouvinte = remember(listaDeParagrafos) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                aoRolar(
                    -consumed.y,
                    !listaDeParagrafos.canScrollBackward,
                    !listaDeParagrafos.canScrollForward,
                )
                return Offset.Zero
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // O usuário vai querer copiar um trecho. A seleção não atravessa parágrafos
        // (cada um é um item da lista), mas dentro de um funciona.
        SelectionContainer(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().nestedScroll(ouvinte)) {
            LazyColumn(
                state = listaDeParagrafos,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { CabecalhoDoCapitulo(capitulo) }

                if (estado.paragrafos.isEmpty()) {
                    item {
                        Text(
                            "Este capítulo não tem texto.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                itemsIndexed(estado.paragrafos) { _, paragrafo ->
                    Text(
                        text = paragrafo,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            // Espaçamento de linha ampliado: leitura longa cansa menos.
                            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.6,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CabecalhoDoCapitulo(capitulo: CapituloDetalhe) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Capítulo ${capitulo.ordem} · ${descreverTamanho(capitulo.tamanho_do_texto)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (capitulo.ignorado) {
            // Abre-se um capítulo arquivado pela área de arquivados; a leitura não é bloqueada.
            Text(
                "Este capítulo está arquivado.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
