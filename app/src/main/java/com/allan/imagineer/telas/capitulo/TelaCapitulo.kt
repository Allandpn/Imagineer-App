package com.allan.imagineer.telas.capitulo

import androidx.compose.material.icons.filled.PushPin
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import kotlinx.coroutines.flow.debounce
import androidx.compose.runtime.derivedStateOf
import com.allan.imagineer.telas.livro.DialogoDoCapitulo
import com.allan.imagineer.telas.livro.IconesDaTelaDoLivro
import com.allan.imagineer.telas.livro.IconeComSelo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.CapituloDetalhe
import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.enderecoDaImagem
import com.allan.imagineer.telas.capitulo.painel.ConteudoDoPainel
import com.allan.imagineer.telas.capitulo.painel.EstadoDoPainel
import com.allan.imagineer.telas.capitulo.painel.ComAcaoDeGerarImagemDoTrecho
import com.allan.imagineer.telas.capitulo.painel.indicesDoBloco
import com.allan.imagineer.telas.capitulo.painel.ComMarcaDeParagrafo
import com.allan.imagineer.telas.capitulo.painel.blocoMarcado
import com.allan.imagineer.telas.capitulo.painel.alternarBloco
import com.allan.imagineer.telas.capitulo.painel.BarraDeParagrafos
import com.allan.imagineer.telas.capitulo.painel.copiarParaAAreaDeTransferencia
import com.allan.imagineer.telas.capitulo.painel.trechoDosParagrafos
import com.allan.imagineer.telas.capitulo.painel.posicaoDosParagrafos
import com.allan.imagineer.telas.capitulo.painel.paragrafoDoTrecho
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.allan.imagineer.telas.capitulo.painel.PosicionandoArtefato
import com.allan.imagineer.telas.capitulo.painel.ROTULO_POSICIONAR
import com.allan.imagineer.telas.capitulo.painel.avisoDePosicionar
import com.allan.imagineer.telas.capitulo.painel.indiceInicialDoBloco
import com.allan.imagineer.telas.capitulo.painel.ROTULO_OCULTAR_DO_CAPITULO
import com.allan.imagineer.telas.capitulo.painel.ImagemEmTelaCheia
import com.allan.imagineer.telas.capitulo.painel.urlDoServidorEmUso
import com.allan.imagineer.telas.capitulo.painel.AcoesDoPainel
import com.allan.imagineer.telas.capitulo.painel.DialogosDoPainel
import com.allan.imagineer.telas.capitulo.painel.ModaisDoPainel
import com.allan.imagineer.telas.capitulo.painel.PainelDeIa
import com.allan.imagineer.telas.capitulo.painel.PainelDeIaViewModel
import com.allan.imagineer.telas.capitulo.painel.retratosPorSugestao
import com.allan.imagineer.telas.capitulo.painel.VisibilidadeDoBotao
import com.allan.imagineer.telas.capitulo.painel.usarAside
import com.allan.imagineer.telas.livro.descreverTamanho
import com.allan.imagineer.telas.livro.tituloDoCapitulo

/** O ViewModel de um capítulo; a mesma chave devolve o mesmo, então a página e a tela enxergam o mesmo estado. */
@Composable
private fun capituloViewModel(capituloId: Int): CapituloViewModel {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    return viewModel(
        key = "capitulo$capituloId",
        factory = viewModelFactory {
            initializer {
                CapituloViewModel(capituloId, aplicacao.repositorioDeCapitulos, aplicacao.repositorioDeArtefatos, aplicacao.repositorioDeMarcador)
            }
        },
    )
}

/** O ViewModel do painel de IA de um capítulo (item 7.5b): cada capítulo tem o seu. */
@Composable
private fun painelViewModel(capituloId: Int): PainelDeIaViewModel {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    return viewModel(
        key = "painel$capituloId",
        factory = viewModelFactory {
            initializer {
                PainelDeIaViewModel(
                    capituloId,
                    aplicacao.repositorioDeSugestoes,
                    aplicacao.repositorioDeElementos,
                    aplicacao.repositorioDePrompts,
                    aplicacao.servicoDeAnalises,
                )
            }
        },
    )
}

/**
 * A tela de Capítulo (itens 7.5 e 7.5c): o leitor de texto, o painel de IA e, agora, **a passagem de página**.
 *
 * O texto do capítulo aberto aparece como sempre, sozinho. Assim que a lista de capítulos do livro chega (do aparelho,
 * em geral na hora), o leitor vira um **pager**: arrastar para os lados faz a página vizinha **acompanhar o dedo**
 * — com o texto, se já estava carregada, ou com um carregando, se ainda não.
 */
@Composable
fun TelaCapitulo(
    capituloId: Int,
    aoVoltar: () -> Unit,
    /** Abre a ficha de um elemento (item 7.8). `doCapitulo`: veio de uma sugestão deste capítulo. */
    aoAbrirFicha: (elementoId: Int, livroId: Int, capituloId: Int?) -> Unit,
    /** LV3: veio dos chips da lista de elementos; ao entrar, abre o painel de IA já no modal deste elemento. */
    abrirElementoId: Int? = null,
    /** LV5: veio da pesquisa; ao ter o texto, rola até o parágrafo que começa nesta posição e o destaca. */
    irParaPosicao: Int? = null,
    /** Veio do aviso de um prompt gerado: abre o modal deste frame (a cena); [abrirRotulo] é o nome dele. */
    abrirFrameId: Int? = null,
    abrirRotulo: String? = null,
    /** Veio do aviso de análise concluída: abre o painel de IA. */
    abrirPainel: Boolean = false,
    /** Abre a pesquisa (LV5): o livro e o capítulo de onde se pesquisa. */
    aoPesquisar: (livroId: Int, capituloId: Int) -> Unit = { _, _ -> },
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp

    // A lista de capítulos do livro, para o pager. Pede-se depois de o texto inicial estar pronto (é dele que vem o livro).
    val lista: ListaDoLeitorViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ListaDoLeitorViewModel(capituloId, aplicacao.repositorioDeLivros) }
        },
    )
    val listaDoLeitor by lista.estado.collectAsState()
    val inicial = capituloViewModel(capituloId)
    val estadoInicial by inicial.estado.collectAsState()
    LaunchedEffect(inicial) { inicial.carregar() }
    LaunchedEffect(estadoInicial) { (estadoInicial as? EstadoDoCapitulo.Pronto)?.let { lista.carregar(it.capitulo) } }

    // rememberSaveable: o painel aberto sobrevive a girar o aparelho (P4); fica acima do pager, que é refeito
    // quando a lista completa chega.
    var painelAberto by rememberSaveable { mutableStateOf(abrirPainel) }
    // Pedido de abrir um elemento (LV3): vale uma vez só; depois de atendido (ou girando o aparelho) não reabre.
    var elementoPendente by rememberSaveable { mutableStateOf(abrirElementoId) }
    var posicaoPendente by rememberSaveable { mutableStateOf(irParaPosicao) }
    var framePendente by rememberSaveable { mutableStateOf(abrirFrameId) }

    // O pager é refeito uma vez, quando a lista completa chega (de [capituloId] sozinho para todos os capítulos).
    // O que está dentro das páginas (textos, rolagem) vive nos ViewModels, e a página aberta volta no mesmo ponto.
    key(listaDoLeitor.completa) {
        LeitorPaginado(
            lista = listaDoLeitor,
            painelAberto = painelAberto,
            aoAlternarPainel = { painelAberto = !painelAberto },
            aoFecharPainel = { painelAberto = false },
            aoVoltar = aoVoltar,
            aoAbrirFicha = aoAbrirFicha,
            elementoPendente = elementoPendente,
            aoAtenderElementoPendente = { elementoPendente = null },
            posicaoPendente = posicaoPendente,
            aoAtenderPosicao = { posicaoPendente = null },
            aoPesquisar = aoPesquisar,
            modoDireto = abrirElementoId != null || abrirFrameId != null,
            framePendente = framePendente,
            rotuloDoFramePendente = abrirRotulo,
            aoAtenderFramePendente = { framePendente = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeitorPaginado(
    lista: ListaDoLeitor,
    painelAberto: Boolean,
    aoAlternarPainel: () -> Unit,
    aoFecharPainel: () -> Unit,
    aoVoltar: () -> Unit,
    aoAbrirFicha: (elementoId: Int, livroId: Int, capituloId: Int?) -> Unit,
    elementoPendente: Int?,
    aoAtenderElementoPendente: () -> Unit,
    posicaoPendente: Int?,
    aoAtenderPosicao: () -> Unit,
    aoPesquisar: (livroId: Int, capituloId: Int) -> Unit,
    /** Veio dos chips da lista de elementos (LV3): só o modal do elemento aparece, e fechá-lo volta direto à lista. */
    modoDireto: Boolean,
    framePendente: Int?,
    rotuloDoFramePendente: String?,
    aoAtenderFramePendente: () -> Unit,
) {
    val estadoDoPager = rememberPagerState(initialPage = lista.indiceInicial) { lista.ids.size }
    val aside = usarAside(LocalConfiguration.current.screenWidthDp)
    val painelCheio = painelAberto && !aside

    // O capítulo "da tela": o painel de IA, o modal, os ícones e a ficha são do capítulo em que a página PAROU
    // (settledPage); o título acompanha a página que está mais à vista (currentPage).
    val idDaTela = lista.ids[estadoDoPager.settledPage.coerceIn(0, lista.ids.lastIndex)]
    val idDoTitulo = lista.ids[estadoDoPager.currentPage.coerceIn(0, lista.ids.lastIndex)]
    val vmDaTela = capituloViewModel(idDaTela)
    val estadoDaTela by vmDaTela.estado.collectAsState()
    val estadoDoTitulo by capituloViewModel(idDoTitulo).estado.collectAsState()

    val painel = painelViewModel(idDaTela)
    val estadoDoPainel by painel.estado.collectAsState()

    // N4: qual elemento já tem retrato vem do artefato dele; e, ao criar um frame, os ícones se relêem.
    val artefatosDaTela by vmDaTela.artefatos.collectAsState()
    LaunchedEffect(estadoDoPainel.versaoDosFrames) { if (estadoDoPainel.versaoDosFrames > 0) vmDaTela.carregarArtefatos() }

    // P3: só a direção da rolagem decide se o botão de IA aparece. Trocar de página o faz reaparecer.
    val visibilidade = remember { VisibilidadeDoBotao() }
    var botaoVisivel by remember { mutableStateOf(true) }
    var barraVisivel by remember { mutableStateOf(true) }
    LaunchedEffect(idDaTela) { botaoVisivel = true; barraVisivel = true }

    // Nada do painel é pedido ao servidor até ele ser aberto (P1); trocar de página com ele aberto lê o do novo capítulo.
    LaunchedEffect(painelAberto, idDaTela) { if (painelAberto) painel.aoAbrirPainel() }

    // As ações de elemento (10a) precisam saber de qual livro é o capítulo.
    val livroDoCapitulo = (estadoDaTela as? EstadoDoCapitulo.Pronto)?.capitulo?.livro_id
    LaunchedEffect(livroDoCapitulo, painel) { livroDoCapitulo?.let(painel::definirLivro) }

    // D1: o aviso de "análise concluída" precisa saber como o capítulo se chama...
    val capituloDaTela = (estadoDaTela as? EstadoDoCapitulo.Pronto)?.capitulo
    LaunchedEffect(capituloDaTela, painel) {
        capituloDaTela?.let { painel.definirRotuloDoCapitulo(it.ordem, it.titulo) }
    }
    // ...e quando quem lê já está olhando o painel daquele capítulo (aí o resultado aparece nele, sem aviso).
    val servicoDeAnalises = (LocalContext.current.applicationContext as ImagineerApp).servicoDeAnalises
    DisposableEffect(painelAberto, idDaTela) {
        servicoDeAnalises.definirPainelVisivel(if (painelAberto) idDaTela else null)
        onDispose { servicoDeAnalises.definirPainelVisivel(null) }
    }

    // Veio do aviso de prompt gerado: lê as sugestões e abre o modal da cena (ou, sem sugestão por trás, o do frame).
    LaunchedEffect(framePendente) { if (framePendente != null) painel.aoAbrirPainel() }
    LaunchedEffect(framePendente, estadoDoPainel.conteudo) {
        val frameId = framePendente ?: return@LaunchedEffect
        val conteudo = estadoDoPainel.conteudo
        val pronto = conteudo is com.allan.imagineer.telas.capitulo.painel.ConteudoDoPainel.Pronto
        val terminou = pronto || conteudo is com.allan.imagineer.telas.capitulo.painel.ConteudoDoPainel.NuncaAnalisado ||
            conteudo is com.allan.imagineer.telas.capitulo.painel.ConteudoDoPainel.Erro
        if (!terminou) return@LaunchedEffect
        val cena = (conteudo as? com.allan.imagineer.telas.capitulo.painel.ConteudoDoPainel.Pronto)?.sugestoes?.cenas?.firstOrNull { it.frame_id == frameId }
        if (cena != null) painel.abrirModalDeCena(cena.id) else painel.abrirModalDeFrame(frameId, rotuloDoFramePendente ?: "Cena")
        aoAtenderFramePendente()
    }

    // LV3: o painel não abre (só o modal do elemento), mas a lista de sugestões precisa ser lida.
    LaunchedEffect(elementoPendente) { if (elementoPendente != null) painel.aoAbrirPainel() }

    // LV3: chegou pelos chips da lista de elementos: com a lista de sugestões lida, abre o modal do elemento (ou, sem sugestão
    // dele neste capítulo, a ficha).
    LaunchedEffect(elementoPendente, estadoDoPainel.conteudo, livroDoCapitulo) {
        val id = elementoPendente ?: return@LaunchedEffect
        when (val conteudo = estadoDoPainel.conteudo) {
            is com.allan.imagineer.telas.capitulo.painel.ConteudoDoPainel.Pronto -> {
                val sugestaoId = com.allan.imagineer.telas.capitulo.painel.sugestaoDoElemento(conteudo.sugestoes, id)
                if (sugestaoId != null) {
                    painel.abrirModal(sugestaoId)
                    aoAtenderElementoPendente()
                } else if (livroDoCapitulo != null) {
                    aoAbrirFicha(id, livroDoCapitulo, idDaTela)
                    aoAtenderElementoPendente()
                }
            }
            is com.allan.imagineer.telas.capitulo.painel.ConteudoDoPainel.NuncaAnalisado,
            is com.allan.imagineer.telas.capitulo.painel.ConteudoDoPainel.Erro,
            -> if (livroDoCapitulo != null) {
                aoAbrirFicha(id, livroDoCapitulo, idDaTela)
                aoAtenderElementoPendente()
            }
            else -> Unit
        }
    }

    // Ao voltar da ficha (E31), o que foi editado lá pode mudar os cartões: o painel relê no lugar.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { painel.aoVoltarDaFicha() }

    // No celular o painel é a tela inteira: voltar leva ao texto, e não para fora do capítulo.
    BackHandler(enabled = painelCheio) { aoFecharPainel() }

    // Os ícones se relêem quando o painel muda o que há de sugestão (analisar, confirmar, descartar...). Cada página
    // lê os seus uma vez ao ficar pronta (ver [PaginaDoCapitulo]). Só leitura: nenhuma leitura chama a IA.
    val textoProntoDaTela = estadoDaTela is EstadoDoCapitulo.Pronto
    LaunchedEffect(estadoDoPainel.conteudo) {
        if (textoProntoDaTela && estadoDoPainel.conteudo is com.allan.imagineer.telas.capitulo.painel.ConteudoDoPainel.Pronto) {
            vmDaTela.carregarArtefatos()
        }
    }

    var infoAberta by remember { mutableStateOf(false) }
    if (infoAberta && capituloDaTela != null) {
        DialogoDoCapitulo(
            titulo = tituloDoCapitulo(capituloDaTela.titulo, capituloDaTela.ordem),
            ordem = capituloDaTela.ordem,
            totalDeCapitulos = lista.ids.size.takeIf { lista.completa },
            caracteres = capituloDaTela.tamanho_do_texto,
            sugestoesPendentes = capituloDaTela.sugestoes_pendentes,
            arquivado = capituloDaTela.ignorado,
            aoFechar = { infoAberta = false },
            lido = capituloDaTela.lido,
            aoAlternarLido = { vmDaTela.marcarLido(!capituloDaTela.lido) },
        )
    }

    val titulo = (estadoDoTitulo as? EstadoDoCapitulo.Pronto)?.capitulo
        ?.let { tituloDoCapitulo(it.titulo, it.ordem) }
        ?: "Capítulo"

    // J2: o seletor de imagens mora **aqui**, e não no botão dentro do modal: o modal some enquanto o seletor do Android
    // está aberto, e com ele o resultado da escolha. O alvo (frame e prompt) fica no ViewModel do painel.
    val aplicacaoDoSeletor = LocalContext.current.applicationContext as ImagineerApp
    val seletorDeImagem = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        painel.imagemEscolhida(uri?.let { aplicacaoDoSeletor.leitorDeArquivos.descrever(it.toString()) }, cancelou = uri == null)
    }

    // Posicionando com o painel em tela cheia: o painel fecha e, quando o modo termina (escolheu o parágrafo ou cancelou), volta.
    var reabrirPainelAoFim by remember { mutableStateOf(false) }
    LaunchedEffect(estadoDoPainel.posicionando) {
        if (estadoDoPainel.posicionando == null && reabrirPainelAoFim) {
            reabrirPainelAoFim = false
            if (!painelAberto) aoAlternarPainel()
        }
    }

    val acoesDoPainel = AcoesDoPainel(
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
        aoCarregarPrompts = painel::carregarPrompts,
        aoModalDoPromptVisivel = painel::definirModalDoFrameVisivel,
        aoRecarregarPrompts = painel::recarregarPrompts,
        aoPedirGerarPrompt = { frameId, rotulo -> painel.pedirGerarPrompt(frameId, rotulo) },
        aoGerarRetrato = painel::gerarRetrato,
        aoGerarSoOPromptDoRetrato = painel::gerarSoOPromptDoRetrato,
        aoGerarSoOPromptDoFrame = painel::gerarSoOPromptDoFrame,
        aoGerarImagemDoFrame = painel::gerarImagemDoFrame,
        aoImportarImagem = painel::importarImagem,
        aoEscolherImagem = { frameId, promptId ->
            painel.escolherImagemPara(frameId, promptId)
            seletorDeImagem.launch("image/*")
        },
        aoGerarImagem = painel::gerarImagem,
        aoCarregarModelosDeImagem = painel::carregarModelosDeImagem,
        aoAbrirEscolhaDeModelo = painel::abrirEscolhaDeModelo,
        aoEscolherModelo = painel::escolherModelo,
        aoFecharEscolhaDeModelo = painel::fecharEscolhaDeModelo,
        aoFecharRecusaDeImagem = painel::fecharRecusaDeImagem,
        aoAbrirSeletorDaCena = painel::abrirSeletorDaCena,
        aoAbrirSeletorDoRetrato = painel::abrirSeletorDoRetrato,
        aoAlternarImagemDoSeletor = painel::alternarImagemDoSeletor,
        aoAlternarElementoDoSeletor = painel::alternarElementoDoSeletor,
        aoLimparSeletor = painel::limparSeletor,
        aoUsarSeletor = painel::usarSeletor,
        aoFecharSeletor = painel::fecharSeletor,
        aoCarregarVinculados = painel::carregarVinculados,
        aoCarregarReferenciasGuardadas = painel::carregarReferenciasGuardadas,
        aoEditarPrompt = painel::editarPrompt,
        aoFecharEdicaoDePrompt = painel::fecharEdicaoDePrompt,
        aoPedirExcluirImagem = painel::pedirExcluirImagem,
        aoDefinirImagemCanonica = painel::definirImagemCanonica,
        aoDefinirImagemOculta = painel::definirImagemOculta,
        // Com o painel em tela cheia (tela estreita), o texto fica escondido: fecha o painel para a pessoa tocar no parágrafo e o reabre ao fim.
        aoIniciarPosicionamento = { ehCena, sugestaoId, rotulo ->
            painel.iniciarPosicionamento(ehCena, sugestaoId, rotulo)
            if (painelCheio) { reabrirPainelAoFim = true; aoFecharPainel() }
        },
        aoIniciarPosicionamentoDeFrame = { ehCena, frameId, rotulo ->
            painel.iniciarPosicionamentoDeFrame(ehCena, frameId, rotulo)
            if (painelCheio) { reabrirPainelAoFim = true; aoFecharPainel() }
        },
        aoTirarPosicao = painel::tirarPosicao,
        aoPedirApagarFrame = { frameId, rotulo, deSugestao -> painel.pedirApagarFrame(frameId, rotulo, deSugestao) },
        aoCancelarApagarFrame = painel::cancelarApagarFrame,
        aoAbrirEdicaoDaCena = painel::abrirEdicaoDaCena,
        aoAbrirEdicaoDeFrame = painel::abrirEdicaoDeFrame,
        aoSalvarEdicaoDaCena = painel::salvarEdicaoDaCena,
        aoFecharEdicaoDaCena = painel::fecharEdicaoDaCena,
        aoConfirmarApagarFrame = painel::confirmarApagarFrame,
        aoFecharTrecho = painel::fecharTrecho,
        aoAlterarDescricaoDoTrecho = painel::alterarDescricaoDoTrecho,
        aoAlternarElementoDoTrecho = painel::alternarElementoDoTrecho,
        aoCriarCenaDoTrecho = painel::criarCenaDoTrecho,
        aoFecharModalDoFrame = {
            painel.fecharModalDoFrame()
            if (modoDireto && estadoDoPainel.modais.size <= 1) aoVoltar()
        },
        aoAbrirImagemExistente = painel::abrirImagemExistente,
        aoFecharImagemExistente = painel::fecharImagemExistente,
        aoUsarImagemExistente = painel::usarImagemExistente,
        aoConfirmarExclusaoDeImagem = painel::confirmarExclusaoDeImagem,
        aoCancelarExclusaoDeImagem = painel::cancelarExclusaoDeImagem,
        aoCancelarGerarPrompt = painel::cancelarGerarPrompt,
        aoGerarPrompt = painel::gerarPrompt,
        aoPedirConfirmarTodos = painel::pedirConfirmarTodos,
        aoConfirmarTodos = painel::confirmarTodos,
        aoCancelarConfirmarTodos = painel::cancelarConfirmarTodos,
        aoDispensarResultadoDoLote = painel::dispensarResultadoDoLote,
        aoAbrirCena = painel::abrirModalDeCena,
        aoFecharModalDaCena = {
            painel.fecharModalDaCena()
            if (modoDireto && estadoDoPainel.modais.size <= 1) aoVoltar()
        },
        aoExecutarCena = painel::executarCena,
        aoRevisarParticipante = painel::revisarParticipante,
        aoAbrirFicha = { elementoId, doCapitulo ->
            // Sem o livro (o capítulo ainda não carregou) não há como abrir a ficha.
            livroDoCapitulo?.let {
                painel.fecharModalAoAbrirFicha() // ao voltar, o modal não reabre sozinho
                aoAbrirFicha(elementoId, it, idDaTela.takeIf { doCapitulo })
            }
        },
        aoAlternarApagarEstado = painel::alternarApagarEstado,
        aoConfirmarDesfazer = painel::confirmarDesfazer,
        aoConfirmarDescarte = painel::confirmarDescarte,
        // No modo direto, fechar o (único) modal do elemento volta à lista de elementos, sem passar pelo capítulo.
        aoFecharModal = {
            painel.fecharModal()
            if (modoDireto && estadoDoPainel.modais.size <= 1) aoVoltar()
        },
    )

    Scaffold(
        topBar = {
            // Como o botão de IA: some ao rolar para baixo e volta ao rolar para cima (e no topo e no fim do texto).
            AnimatedVisibility(visible = barraVisivel, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                TopAppBar(
                    title = { Text(titulo) },
                    navigationIcon = {
                        IconButton(onClick = aoVoltar) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    actions = {
                        // LV7: os metadados do capítulo (número, tamanho, tempo de leitura, pendências), ao lado de pesquisar.
                        if (capituloDaTela != null) {
                            IconeComSelo(
                                icone = IconesDaTelaDoLivro.metadados,
                                descricao = "Metadados do capítulo",
                                selo = null,
                                aoTocar = { infoAberta = true },
                            )
                        }
                        // LV5: pesquisar no texto (neste capítulo, no livro e na biblioteca).
                        if (livroDoCapitulo != null) {
                            IconButton(onClick = { aoPesquisar(livroDoCapitulo, idDaTela) }) {
                                Icon(Icons.Filled.Search, contentDescription = "Pesquisar")
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            // O botão de IA, no canto inferior direito (P3). Só com o texto na tela.
            if (textoProntoDaTela) {
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
            // Tablet: o painel é um aside à direita, dividindo a tela com o texto (P4). No celular, o pager ocupa tudo.
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    HorizontalPager(
                        state = estadoDoPager,
                        modifier = Modifier.fillMaxSize(),
                        // Uma página de cada lado já pronta: quando o dedo chega nela, o texto costuma estar lá.
                        beyondViewportPageCount = 1,
                        key = { lista.ids[it] },
                        // Com o painel em tela cheia por cima, o pager não pode reagir ao dedo por baixo.
                        userScrollEnabled = !painelCheio,
                    ) { pagina ->
                        val idDaPagina = lista.ids[pagina]
                        PaginaDoCapitulo(
                            capituloId = idDaPagina,
                            ehAtual = pagina == estadoDoPager.currentPage,
                            ehAssentada = pagina == estadoDoPager.settledPage,
                            aoTocarArtefato = { artefato ->
                                // C1: o id de uma cena e o de um elemento são de tabelas diferentes; cada um abre o seu modal.
                                val sugestaoId = artefato.sugestao_id
                                if (sugestaoId != null) {
                                    if (artefato.tipo == "CENA") painel.abrirModalDeCena(sugestaoId) else painel.abrirModal(sugestaoId)
                                } else if (artefato.frame_id != null) {
                                    // TR4: a cena de um trecho (ou o retrato sem sugestão) não tem sugestão por trás: abre o modal do frame.
                                    painel.abrirModalDeFrame(artefato.frame_id, artefato.rotulo)
                                }
                            },
                            aoOcultarImagem = { frameId -> painel.definirImagemOculta(frameId, true) },
                            posicionando = estadoDoPainel.posicionando,
                            aoIniciarPosicionamento = { artefato ->
                                val sugestaoId = artefato.sugestao_id
                                if (sugestaoId != null) {
                                    painel.iniciarPosicionamento(artefato.tipo == "CENA", sugestaoId, artefato.rotulo)
                                } else if (artefato.frame_id != null) {
                                    painel.iniciarPosicionamentoDeFrame(artefato.tipo == "CENA", artefato.frame_id, artefato.rotulo)
                                }
                            },
                            aoEscolherParagrafo = painel::escolherParagrafo,
                            aoGerarImagemDoTrecho = painel::abrirTrecho,
                            aoVerPerfilDoArtefato = { artefato -> verPerfilDoArtefato(artefato, estadoDoPainel, painel, acoesDoPainel) },
                            // LV5: só a página em que se começou recebe o pedido de rolar até o achado.
                            // Só com a lista completa: quando ela chega, o pager é refeito e uma página nova nasceria no topo, perdendo a rolagem.
                            irParaPosicao = if (lista.completa && idDaPagina == lista.ids[lista.indiceInicial]) posicaoPendente else null,
                            aoAtenderPosicao = aoAtenderPosicao,
                            aoCancelarPosicionamento = painel::cancelarPosicionamento,
                            aoRolar = { delta, noTopo, noFim ->
                                visibilidade.aoRolar(delta, noTopo, noFim)
                                botaoVisivel = visibilidade.visivel
                                barraVisivel = visibilidade.barraVisivel
                            },
                        )
                    }
                }
                if (painelAberto && aside) {
                    VerticalDivider()
                    PainelDeIa(
                        estado = estadoDoPainel,
                        acoes = acoesDoPainel,
                        aoFechar = aoAlternarPainel,
                        modifier = Modifier.width(380.dp).fillMaxHeight(),
                        retratos = retratosPorSugestao(artefatosDaTela),
                    )
                }
            }
            // Celular: o painel é a tela inteira, POR CIMA do pager — que continua composto por baixo, e por isso
            // a posição de leitura de cada capítulo não se perde (E43). O pointerInput vazio impede o toque de vazar.
            if (painelCheio) {
                PainelDeIa(
                    estado = estadoDoPainel,
                    acoes = acoesDoPainel,
                    aoFechar = null,
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {},
                    retratos = retratosPorSugestao(artefatosDaTela),
                )
            }
        }
    }

    // O modal e os diálogos do painel são JANELAS próprias, por cima de tudo, e capturam o botão voltar (E43).
    // **Só são desenhados com o capítulo RESUMED**, isto é, na frente e com a animação já terminada.
    //
    // Causa do D3 (achada com o rastro do Logcat, 01/10/2026): o "voltar" do Android anima a volta ENQUANTO o
    // usuário o executa, e nessa fase o capítulo já está STARTED (visível por baixo da ficha), mas ainda não
    // RESUMED. Se o modal aparecesse em STARTED, a janela dele nascia por cima no meio do gesto, ficava como
    // destino do "voltar" e **cancelava a animação da navegação**: a ficha reaparecia ("pisca e volta"), sem
    // nenhuma mudança de tela registrada. Várias toques seguidos só funcionavam quando um deles pegava a
    // navegação antes de o modal compor.
    //
    // Efeito colateral aceito: ao voltar da ficha o modal aparece só depois da animação (~0,3 s), e não junto dela.
    val estadoDoCiclo by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (estadoDoCiclo == Lifecycle.State.RESUMED) {
        ModaisDoPainel(estadoDoPainel, acoesDoPainel, retratosPorSugestao(artefatosDaTela))
        DialogosDoPainel(estadoDoPainel, acoesDoPainel)
    }
}

/**
 * Uma página do pager: o capítulo [capituloId], com o seu próprio ViewModel. Carrega o texto quando entra na composição —
 * ou seja, quando o dedo a traz para perto — e mostra um **carregando** enquanto isso; se o texto já estava no
 * ViewModel (a página já foi vista) ou no aparelho, aparece de imediato.
 */
@Composable
private fun PaginaDoCapitulo(
    capituloId: Int,
    ehAtual: Boolean,
    /** A página em que o pager **parou** (não só a que está mais à vista no meio de um deslize): só ela conta para lido e marcador. */
    ehAssentada: Boolean,
    aoTocarArtefato: (Artefato) -> Unit,
    aoOcultarImagem: (frameId: Int) -> Unit,
    posicionando: PosicionandoArtefato?,
    aoIniciarPosicionamento: (Artefato) -> Unit,
    aoEscolherParagrafo: (posicao: Int) -> Unit,
    aoCancelarPosicionamento: () -> Unit,
    aoGerarImagemDoTrecho: (trecho: String, posicao: Int?) -> Unit,
    aoVerPerfilDoArtefato: (Artefato) -> Unit,
    irParaPosicao: Int?,
    aoAtenderPosicao: () -> Unit,
    aoRolar: (delta: Float, noTopo: Boolean, noFim: Boolean) -> Unit,
) {
    val viewModel = capituloViewModel(capituloId)
    val estado by viewModel.estado.collectAsState()
    val artefatos by viewModel.artefatos.collectAsState()
    // Carrega uma vez. Se a composição recomeçar (girar o tablet), o ViewModel já tem o texto.
    LaunchedEffect(viewModel) { viewModel.carregar() }
    // Os ícones vêm depois do texto, nunca antes — o texto nunca espera por eles (E42).
    val textoPronto = estado is EstadoDoCapitulo.Pronto
    LaunchedEffect(textoPronto) { if (textoPronto) viewModel.carregarArtefatos() }
    // A posição de leitura desta página; o pager a guarda por chave, e o painel em tela cheia não a perde.
    val posicaoDeLeitura = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val atual = estado) {
            EstadoDoCapitulo.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            is EstadoDoCapitulo.Erro -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                Column(
                    modifier = Modifier.widthIn(max = 600.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        atual.motivo,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = viewModel::tentarDeNovo) { Text("Tentar de novo") }
                }
            }

            is EstadoDoCapitulo.Pronto -> LeitorDeTexto(
                estado = atual,
                artefatos = artefatos,
                aoTocarArtefato = { if (ehAtual) aoTocarArtefato(it) },
                aoOcultarImagem = aoOcultarImagem,
                posicionando = if (ehAtual) posicionando else null,
                aoIniciarPosicionamento = aoIniciarPosicionamento,
                aoEscolherParagrafo = aoEscolherParagrafo,
                aoCancelarPosicionamento = aoCancelarPosicionamento,
                aoGerarImagemDoTrecho = { trecho, posicao -> if (ehAtual) aoGerarImagemDoTrecho(trecho, posicao) },
                aoVerPerfilDoArtefato = aoVerPerfilDoArtefato,
                irParaPosicao = irParaPosicao,
                aoAtenderPosicao = aoAtenderPosicao,
                emFoco = ehAssentada,
                aoChegarAoFim = viewModel::chegouAoFim,
                aoLerAte = viewModel::gravarPosicao,
                listaDeParagrafos = posicaoDeLeitura,
                // Só a página em foco manda no botão de IA; a vizinha, rolando por baixo, não.
                aoRolar = if (ehAtual) aoRolar else { _, _, _ -> },
            )
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
    artefatos: List<Artefato>,
    aoTocarArtefato: (Artefato) -> Unit,
    aoOcultarImagem: (frameId: Int) -> Unit,
    posicionando: PosicionandoArtefato?,
    aoIniciarPosicionamento: (Artefato) -> Unit,
    aoEscolherParagrafo: (posicao: Int) -> Unit,
    aoCancelarPosicionamento: () -> Unit,
    aoGerarImagemDoTrecho: (trecho: String, posicao: Int?) -> Unit,
    aoVerPerfilDoArtefato: (Artefato) -> Unit,
    irParaPosicao: Int?,
    aoAtenderPosicao: () -> Unit,
    /** Esta página é a que a pessoa está lendo (o pager parou nela). */
    emFoco: Boolean,
    /** Chegou ao fim do texto: o capítulo vira lido (LE4, LE7). */
    aoChegarAoFim: () -> Unit,
    /** O parágrafo à vista mudou e parou: grava onde a pessoa está (LE2). */
    aoLerAte: (posicao: Int) -> Unit,
    listaDeParagrafos: LazyListState,
    aoRolar: (delta: Float, noTopo: Boolean, noFim: Boolean) -> Unit,
) {
    val capitulo = estado.capitulo
    // Onde cada parágrafo começa (UTF-16, como o servidor conta) e quais ícones vão em cada um.
    val trechos = remember(capitulo.id) { dividirEmParagrafosComInicio(capitulo.texto) }
    val distribuidos = remember(artefatos, trechos) { distribuirArtefatos(artefatos, trechos) }
    // I1 a I6: a imagem ampliada (tela cheia, tamanho normal) e o endereço do servidor para montar as URLs.
    val urlBase = urlDoServidorEmUso()
    var ampliada by remember { mutableStateOf<Artefato?>(null) }
    // LV4: os parágrafos marcados (índices em [trechos]); vazio = leitura normal, sem barra.
    var marcados by remember(capitulo.id) { mutableStateOf(emptySet<Int>()) }
    // O modo de posicionar tem prioridade: ao ligá-lo, a marcação some.
    LaunchedEffect(posicionando) { if (posicionando != null) marcados = emptySet() }
    // Voltar (do aparelho) desfaz a marcação antes de sair do capítulo.
    BackHandler(enabled = marcados.isNotEmpty()) { marcados = emptySet() }
    val contextoDaTela = LocalContext.current
    // LV5: o parágrafo achado pela pesquisa fica destacado por uns segundos.
    var destacado by remember(capitulo.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(destacado) {
        if (destacado != null) {
            kotlinx.coroutines.delay(3000)
            destacado = null
        }
    }

    // Escuta a rolagem da lista para o botão de IA (P3). O sinal do deslocamento do Compose é o
    // contrário do que a regra espera (dedo para cima = y negativo = rolando para baixo), por
    // isso o "-consumed.y". No topo ou no fim o botão fica sempre visível.
    val ouvinte = remember(listaDeParagrafos, aoRolar) {
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

    // A largura do texto (I2, I3): a coluna de leitura tem no máximo 600 dp, menos o respiro dos lados.
    val medidor = rememberTextMeasurer()
    val densidade = LocalDensity.current
    val estiloDoParagrafo = MaterialTheme.typography.bodyLarge.let {
        // Espaçamento de linha ampliado: leitura longa cansa menos.
        it.copy(lineHeight = it.fontSize * 1.6)
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val larguraDoTexto = minOf(maxWidth, LARGURA_MAXIMA_DA_LEITURA) - 32.dp
        val larguraDoQuadro = larguraDoTexto / 2 // I2 (revisto): metade da área de leitura
        val larguraEstreita = larguraDoTexto - larguraDoQuadro - ESPACO_AO_LADO_DO_QUADRO
        val artefatosDo: (Int) -> List<Artefato> = { distribuidos.porParagrafo[it].orEmpty() }
        // I10: o app mede o texto na largura estreita para saber até onde ele cabe ao lado do retrato.
        val blocos = remember(trechos, distribuidos, urlBase, larguraDoTexto, densidade, estiloDoParagrafo) {
            val alturaDoRetrato = with(densidade) { (larguraDoQuadro * 3 / 2).toPx() }
            val espaco = with(densidade) { 12.dp.toPx() }
            val larguraEstreitaPx = with(densidade) { larguraEstreita.roundToPx() }
            montarBlocos(
                quantidade = trechos.size,
                // Sem o endereço do servidor não há imagem para desenhar: todo parágrafo é comum.
                artefatosDo = { if (urlBase == null) emptyList() else artefatosDo(it) },
                alturaDoRetrato = alturaDoRetrato,
                espaco = espaco,
                linhas = { indice ->
                    val montado = textoComIcones(trechos[indice].texto, artefatosDo(indice).size)
                    val medido = medidor.measure(
                        text = montado.anotado,
                        style = estiloDoParagrafo,
                        placeholders = montado.marcadores,
                        constraints = Constraints(maxWidth = larguraEstreitaPx),
                    )
                    List(medido.lineCount) {
                        // O corte é no texto do parágrafo, sem os caracteres dos ícones do começo.
                        LinhaMedida(medido.getLineBottom(it), (medido.getLineEnd(it) - montado.prefixo).coerceAtLeast(0))
                    }
                },
            )
        }

        // LV5: veio da pesquisa: rola até o bloco do parágrafo achado e o destaca. Os itens antes dos blocos são o cabeçalho, o aviso de
        // "sem texto" e a faixa "Sem posição", quando existem.
        LaunchedEffect(blocos, irParaPosicao) {
            val alvo = irParaPosicao ?: return@LaunchedEffect
            val paragrafo = trechos.indexOfLast { it.inicio <= alvo }
            val bloco = blocos.indexOfFirst { paragrafo in indicesDoBloco(it) }
            if (paragrafo >= 0 && bloco >= 0) {
                val antes = 1 + (if (estado.paragrafos.isEmpty()) 1 else 0) + (if (distribuidos.semPosicao.isNotEmpty()) 1 else 0)
                listaDeParagrafos.scrollToItem(antes + bloco)
                destacado = paragrafo
                aoAtenderPosicao()
            }
        }

        // LE4, LE7: ao chegar ao fim do texto (ou se ele cabe inteiro na tela), o capítulo vira lido. Espera um instante parado lá, para uma
        // passada rápida (ou o primeiro quadro, antes de a lista ser medida) não contar.
        // Tolerância (a pessoa nem sempre vai até o último pixel): vale também quando o **respiro do fim** (último item) já aparece.
        val noFim by remember {
            derivedStateOf {
                val info = listaDeParagrafos.layoutInfo
                !listaDeParagrafos.canScrollForward ||
                    (info.totalItemsCount > 0 && info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1)
            }
        }
        LaunchedEffect(noFim, emFoco, estado.capitulo.lido) {
            if (noFim && emFoco && !estado.capitulo.lido) {
                kotlinx.coroutines.delay(TEMPO_NO_FIM_PARA_LIDO_MS)
                aoChegarAoFim()
            }
        }

        // LE2: onde a pessoa está lendo. Quando o parágrafo à vista para de mudar por 2 s, grava o início dele (o servidor guarda o mais recente).
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        LaunchedEffect(emFoco, blocos) {
            if (!emFoco) return@LaunchedEffect
            val antes = 1 + (if (estado.paragrafos.isEmpty()) 1 else 0) + (if (distribuidos.semPosicao.isNotEmpty()) 1 else 0)
            androidx.compose.runtime.snapshotFlow { listaDeParagrafos.firstVisibleItemIndex }
                .debounce(ATRASO_PARA_GRAVAR_POSICAO_MS)
                .collect { indice ->
                    val bloco = blocos.getOrNull((indice - antes).coerceAtLeast(0)) ?: return@collect
                    val paragrafo = indicesDoBloco(bloco).firstOrNull() ?: return@collect
                    trechos.getOrNull(paragrafo)?.let { aoLerAte(it.inicio) }
                }
        }

        // O usuário vai querer copiar um trecho. A seleção não atravessa parágrafos
        // (cada um é um item da lista), mas dentro de um funciona.
        ComAcaoDeGerarImagemDoTrecho(aoGerarDoTrecho = { trecho -> aoGerarImagemDoTrecho(trecho, paragrafoDoTrecho(trechos, trecho)) }) {
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

                // Os sem posição (o nome não foi achado no texto) ficam numa faixa no começo.
                if (distribuidos.semPosicao.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Sem posição no texto",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            distribuidos.semPosicao.forEach { artefato ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconeDoArtefato(artefato, aoTocarArtefato)
                                    Text(artefato.rotulo, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
                                    // PM1: a sugestão guarda a posição; o frame sem sugestão (cena de um trecho) guarda a dele. Um pin, sem texto.
                                    if (artefato.sugestao_id != null || artefato.frame_id != null) {
                                        IconButton(onClick = { aoIniciarPosicionamento(artefato) }) {
                                            Icon(Icons.Filled.PushPin, contentDescription = ROTULO_POSICIONAR)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                items(blocos) { bloco ->
                    // PM1: no modo "toque no parágrafo", cada bloco é um alvo; tocar grava a posição do parágrafo em que ele começa.
                    val inicio = if (posicionando != null) indiceInicialDoBloco(bloco)?.let { trechos.getOrNull(it)?.inicio } else null
                    val indices = indicesDoBloco(bloco)
                    val conteudoDoBloco: @Composable () -> Unit = {
                        BlocoDoTextoNaTela(
                            bloco = bloco,
                            textoDe = { trechos[it].texto },
                            artefatosDo = artefatosDo,
                            urlBase = urlBase,
                            larguraDoQuadro = larguraDoQuadro,
                            estilo = estiloDoParagrafo,
                            aoTocarArtefato = aoTocarArtefato,
                            aoAmpliar = { ampliada = it },
                        )
                    }
                    if (posicionando != null) {
                        Box(
                            modifier = if (inicio != null) {
                                Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable { aoEscolherParagrafo(inicio) }
                            } else {
                                Modifier
                            },
                        ) { conteudoDoBloco() }
                    } else {
                        // LV4: toque longo marca o parágrafo; no modo, o toque simples soma ou tira.
                        ComMarcaDeParagrafo(
                            marcado = blocoMarcado(marcados, indices),
                            emModo = marcados.isNotEmpty(),
                            aoTocar = { if (marcados.isNotEmpty()) marcados = alternarBloco(marcados, indices) },
                            aoSegurar = { if (marcados.isEmpty()) marcados = marcados + indices },
                            destacado = destacado != null && destacado in indices,
                            conteudo = conteudoDoBloco,
                        )
                    }
                }
                // Respiro no fim do capítulo: o último parágrafo não fica colado na borda, e chegar até aqui conta como chegar ao fim.
                item { Spacer(Modifier.height(RESPIRO_DO_FIM_DO_CAPITULO)) }
            }
        }
        }
    }
    // LV4: a barra de ícones dos parágrafos marcados; some quando o último é desmarcado.
    if (marcados.isNotEmpty() && posicionando == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            BarraDeParagrafos(
                aoCopiar = { copiarParaAAreaDeTransferencia(contextoDaTela, trechoDosParagrafos(trechos, marcados)) },
                aoGerarPrompt = {
                    aoGerarImagemDoTrecho(trechoDosParagrafos(trechos, marcados), posicaoDosParagrafos(trechos, marcados))
                    marcados = emptySet()
                },
                aoVoltarAoNormal = { marcados = emptySet() },
                modifier = Modifier.padding(16.dp),
            )
        }
    }
    // PM1: o aviso fixo do modo de posicionar, com o Cancelar; fica à vista mesmo com o texto rolado.
    posicionando?.let { alvo ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(12.dp).widthIn(max = 600.dp).fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(avisoDePosicionar(alvo.rotulo), style = MaterialTheme.typography.bodyMedium)
                        alvo.erro?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = aoCancelarPosicionamento) { Text("Cancelar") }
                }
            }
        }
    }
    // I4: tocar na imagem a abre no tamanho normal (o arquivo `original`), com zoom por pinça; voltar fecha.
    ampliada?.let { artefato ->
        val imagemId = artefato.imagem_id
        if (urlBase != null && imagemId != null) {
            ImagemEmTelaCheia(
                imagem = ImagemDoPrompt(id = imagemId),
                url = enderecoDaImagem(urlBase, imagemId, "original"),
                aoExcluir = null,
                aoFechar = { ampliada = null },
                mostrarOrigem = false,
                // OC1: tirar a imagem do capítulo sem apagar (ela continua na galeria; "Mostrar no capítulo" volta pelo painel).
                acaoExtra = artefato.frame_id?.let { frame -> ROTULO_OCULTAR_DO_CAPITULO to { aoOcultarImagem(frame); ampliada = null } },
                // O perfil de quem a imagem ilustra: o elemento (ficha) ou a cena (o modal dela).
                aoVerPerfil = if (artefato.sugestao_id != null || artefato.frame_id != null) ({ aoVerPerfilDoArtefato(artefato); ampliada = null }) else null,
            )
        }
    }
}

/**
 * "Ver perfil" na imagem em tela cheia: o **elemento** abre a ficha dele; a **cena** abre o modal dela (que é o perfil da cena); um
 * frame sem sugestão (a cena de um trecho) abre o modal do frame. Sem as sugestões lidas ainda, abre o modal da sugestão (que as lê).
 */
private fun verPerfilDoArtefato(artefato: Artefato, estado: EstadoDoPainel, painel: PainelDeIaViewModel, acoes: AcoesDoPainel) {
    val sugestaoId = artefato.sugestao_id
    val frameId = artefato.frame_id
    when {
        artefato.tipo == "CENA" && sugestaoId != null -> painel.abrirModalDeCena(sugestaoId)
        sugestaoId == null && frameId != null -> painel.abrirModalDeFrame(frameId, artefato.rotulo)
        sugestaoId != null -> {
            val elementoId = (estado.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes?.elementos
                ?.firstOrNull { it.id == sugestaoId }?.elemento_casado?.id
            if (elementoId != null) acoes.aoAbrirFicha(elementoId, true) else painel.abrirModal(sugestaoId)
        }
    }
}

/** O espaço vazio depois do último parágrafo (cerca de quatro linhas). */
private val RESPIRO_DO_FIM_DO_CAPITULO = 120.dp

/** Quanto a pessoa fica parada no fim do texto para o capítulo valer como lido (LE4). */
private const val TEMPO_NO_FIM_PARA_LIDO_MS = 800L

/** Quanto o parágrafo à vista fica parado antes de a posição ser gravada no servidor (LE2). */
private const val ATRASO_PARA_GRAVAR_POSICAO_MS = 2000L

/** A coluna de leitura nunca passa de 600 dp, mesmo num tablet largo. */
private val LARGURA_MAXIMA_DA_LEITURA = 600.dp

@Composable
private fun CabecalhoDoCapitulo(capitulo: CapituloDetalhe) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
