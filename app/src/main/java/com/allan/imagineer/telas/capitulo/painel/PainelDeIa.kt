package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.allan.imagineer.rede.ModelosDeImagem
import com.allan.imagineer.rede.PromptDeFrame
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.SugestoesDeCapitulo

// O desenho visual do painel é provisório: este incremento trata das REGRAS (item 7.5b, P1 a
// P15 e E1 a E32), e o visual é refinado depois.

/** As ações do painel, agrupadas para a tela de Capítulo não carregar uma lista de parâmetros. */
class AcoesDoPainel(
    val aoAnalisar: () -> Unit,
    val aoPedirReanalise: () -> Unit,
    val aoConfirmarReanalise: (orientacao: String) -> Unit,
    val aoCancelarReanalise: () -> Unit,
    val aoTentarDeNovo: () -> Unit,
    // Incremento 10a: confirmar elementos (E1 a E32).
    val aoExecutar: (AcaoDoElemento, ElementoSugerido) -> Unit,
    val aoCancelarDialogo: () -> Unit,
    val aoConfirmarCriacao: (tipo: String, nome: String, descricao: String) -> Unit,
    val aoTrocarCriacaoPorVinculo: () -> Unit,
    val aoEscolherElemento: (Int) -> Unit,
    val aoRecarregarLista: () -> Unit,
    val aoRestaurar: (ElementoSugerido) -> Unit,
    val aoEscolherFiltro: (FiltroDoPainel) -> Unit,
    /** Abre a tela da ficha (E22). `doCapitulo`: a ficha veio de uma sugestão deste capítulo. */
    val aoAbrirFicha: (elementoId: Int, doCapitulo: Boolean) -> Unit,
    val aoAlternarApagarEstado: () -> Unit,
    val aoConfirmarDesfazer: () -> Unit,
    val aoConfirmarDescarte: () -> Unit,
    val aoFecharModal: () -> Unit,
    // Incremento 10b, segunda fatia: gerar o prompt e copiar (G1 a G10).
    val aoCarregarPrompts: (frameId: Int) -> Unit,
    val aoModalDoPromptVisivel: (frameId: Int?) -> Unit,
    val aoRecarregarPrompts: (frameId: Int) -> Unit,
    val aoPedirGerarPrompt: (frameId: Int, rotulo: String) -> Unit,
    // Gerar a imagem em um toque (Q1 a Q9): o retrato de um elemento sem frame, e o botão principal de um frame.
    val aoGerarRetrato: (ElementoSugerido) -> Unit,
    val aoGerarImagemDoFrame: (chave: String, frameId: Int, rotulo: String) -> Unit,
    val aoCancelarGerarPrompt: () -> Unit,
    val aoGerarPrompt: (frameId: Int, ajuste: String) -> Unit,
    // Confirmar todos (pedido do Allan, 01/10/2026).
    val aoPedirConfirmarTodos: () -> Unit,
    val aoConfirmarTodos: () -> Unit,
    val aoCancelarConfirmarTodos: () -> Unit,
    val aoDispensarResultadoDoLote: () -> Unit,
    // Incremento 10b, primeira fatia: a cena (C1 a C10).
    val aoAbrirCena: (cenaId: Int) -> Unit,
    val aoFecharModalDaCena: () -> Unit,
    val aoExecutarCena: (AcaoDaCena, CenaSugerida) -> Unit,
    val aoRevisarParticipante: (sugestaoElementoId: Int) -> Unit,
    // Incremento 12, primeira fatia: importar a imagem (J1 a J10).
    val aoImportarImagem: (frameId: Int, promptId: Int, arquivo: com.allan.imagineer.dados.ArquivoEscolhido?) -> Unit,
    // Incremento 12, terceira fatia: gerar a imagem (K1 a K10).
    val aoGerarImagem: (frameId: Int, promptId: Int, textoEditado: String?, modelo: String?) -> Unit,
    // Qual modelo de imagem usar (Z6 a Z9).
    val aoCarregarModelosDeImagem: () -> Unit,
    val aoAbrirEscolhaDeModelo: () -> Unit,
    val aoEscolherModelo: (String) -> Unit,
    val aoFecharEscolhaDeModelo: () -> Unit,
    val aoEscolherImagem: (frameId: Int, promptId: Int) -> Unit,
    // O seletor de elementos e imagens, na cena e no retrato (EV1 a EV10).
    val aoAbrirSeletorDaCena: (frameId: Int) -> Unit,
    val aoAbrirSeletorDoRetrato: (elemento: ElementoSugerido, frameId: Int?) -> Unit,
    val aoAlternarImagemDoSeletor: (imagemId: Int) -> Unit,
    val aoAlternarElementoDoSeletor: (estadoId: Int) -> Unit,
    val aoLimparSeletor: () -> Unit,
    val aoUsarSeletor: () -> Unit,
    val aoFecharSeletor: () -> Unit,
    // As referências guardadas no servidor (RS1): lidas uma vez ao abrir o frame, em qualquer aparelho.
    val aoCarregarReferenciasGuardadas: (frameId: Int) -> Unit,
    val aoCarregarVinculados: (frameId: Int) -> Unit,
    // Editar o prompt antes de gerar (R1 a R3).
    val aoEditarPrompt: (frameId: Int, promptId: Int, texto: String) -> Unit,
    val aoFecharEdicaoDePrompt: () -> Unit,
    // Excluir a imagem (U3).
    val aoPedirExcluirImagem: (frameId: Int, promptId: Int, imagemId: Int, origem: String) -> Unit,
    // A imagem canônica do frame (CAN6): `imagemId` nulo tira a escolha.
    val aoDefinirImagemCanonica: (frameId: Int, imagemId: Int?) -> Unit,
    val aoConfirmarExclusaoDeImagem: () -> Unit,
    val aoCancelarExclusaoDeImagem: () -> Unit,
    val aoFecharRecusaDeImagem: () -> Unit,
)

/**
 * O painel de IA de um capítulo: um aside no tablet, a tela inteira no celular (P4).
 *
 * @param aoFechar botão de fechar, só no aside — na tela cheia quem fecha é o botão "voltar
 * ao texto", no mesmo canto do botão de IA.
 */
@Composable
fun PainelDeIa(
    estado: EstadoDoPainel,
    acoes: AcoesDoPainel,
    aoFechar: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Qual sugestão de elemento já tem retrato neste capítulo: sugestão -> frame (N4). */
    retratos: Map<Int, Int> = emptyMap(),
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.fillMaxSize()) {
            CabecalhoDoPainel(aoFechar)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CorpoDoPainel(estado, acoes, retratos)
            }
        }
    }
}

/**
 * Os diálogos do painel (reanalisar, criar, vincular, desfazer, descartar em cena). Desenhados **uma vez só**,
 * pela tela de Capítulo, e não dentro do painel: assim funcionam também quando o usuário age pelo modal
 * da sugestão, com o painel fechado.
 */
@Composable
fun DialogosDoPainel(estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    if (estado.confirmandoReanalise) {
        DialogoDeReanalise(estado, acoes)
    }
    estado.confirmandoTodos?.let { DialogoConfirmarTodos(it, acoes) }
    estado.confirmandoPrompt?.let { DialogoGerarPrompt(it, acoes) }
    estado.recusaDeImagem?.let { DialogoDeRecusaDeImagem(it, estado, acoes) }
    estado.edicaoDePrompt?.let { DialogoDeEdicaoDePrompt(it, estado, acoes) }
    estado.escolhaDeElementos?.let { DialogoDoSeletorDeElementos(it, acoes) }
    estado.excluindoImagem?.let { DialogoExcluirImagem(acoes) }
    if (estado.escolhendoModelo) estado.modelosDeImagem?.let { DialogoEscolherModelo(it, estado.modeloEscolhido, acoes) }
    when (val dialogo = estado.dialogo) {
        is DialogoDeElemento.Criando -> DialogoCriarElemento(dialogo, acoes)
        is DialogoDeElemento.Vinculando -> DialogoVincularElemento(dialogo, acoes)
        is DialogoDeElemento.Desfazendo -> DialogoDesfazer(dialogo, acoes)
        is DialogoDeElemento.DescartandoEmCenas -> DialogoDescartarEmCenas(dialogo, acoes)
        null -> Unit
    }
}

/**
 * Todos os modais da pilha (C12), **do de baixo para o de cima**: cada um é uma janela, e a última composta fica por
 * cima. Fechar o de cima o tira da pilha e **revela o de baixo**, que se atualiza sozinho com o que se decidiu.
 */
@Composable
fun ModaisDoPainel(estado: EstadoDoPainel, acoes: AcoesDoPainel, retratos: Map<Int, Int>) {
    estado.modais.forEach { modal ->
        key(modal) {
            when (modal) {
                is ModalAberto.DeElemento -> ModalDaSugestao(estado, acoes, modal.sugestaoId, retratos)
                is ModalAberto.DeCena -> ModalDaCena(estado, acoes, modal.cenaId)
            }
        }
    }
}

/**
 * O modal da sugestão tocada no texto (E42): o mesmo cartão do painel, **aberto e com as mesmas ações**
 * (confirmar, criar, vincular, descartar, ver ficha...), para decidir sem sair da leitura. Fechar volta ao
 * texto exatamente onde estava.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalDaSugestao(estado: EstadoDoPainel, acoes: AcoesDoPainel, id: Int, retratos: Map<Int, Int> = emptyMap()) {
    val sugestoes = (estado.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes
    val elemento = sugestoes?.elementos?.firstOrNull { it.id == id }

    ModalBottomSheet(
        onDismissRequest = acoes.aoFecharModal,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                // Ainda lendo as sugestões (o painel nunca tinha sido aberto), ou a leitura falhou.
                sugestoes == null -> when (val conteudo = estado.conteudo) {
                    is ConteudoDoPainel.Erro -> {
                        Text(conteudo.motivo, color = MaterialTheme.colorScheme.error)
                        Button(onClick = acoes.aoTentarDeNovo) { Text("Tentar de novo") }
                    }
                    else -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                // Sumiu da lista (uma reanálise refez as sugestões): não há mais o que mostrar.
                elemento == null -> Text(
                    "Esta sugestão não existe mais. Feche e toque de novo no ícone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                elemento.descartada -> CartaoDeElementoDescartado(elemento, estado, acoes)
                else -> {
                    CartaoDeElemento(
                        elemento = elemento,
                        cenas = cenasDoElemento(sugestoes)[elemento.id].orEmpty(),
                        aberto = true,
                        aoAlternar = {},
                        estado = estado,
                        acoes = acoes,
                        // O frame vem do artefato (N4) ou do que acabou de ser criado.
                        frameDoRetrato = retratos[elemento.id] ?: estado.retratosCriados[elemento.id],
                    )
                }
            }
        }
    }
}

/**
 * O retrato do elemento (Q1, Q2): sem frame, o botão **Gerar retrato**, que cria o frame, gera o prompt e gera a imagem num
 * toque só (revisa N2: o *Novo retrato* deixou de ser um botão solto); com frame, a **mesma área do frame da cena**: a imagem
 * em destaque, as importadas e os prompts recolhidos.
 */
@Composable
private fun BlocoDoRetrato(elemento: ElementoSugerido, frameId: Int?, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val chave = chaveDoFluxoDoRetrato(elemento.id)
    Text("Retrato", style = MaterialTheme.typography.titleSmall)
    // EV1, EV8: a linha dos elementos e imagens, só para quem aceita (não para personagem: é individual, V2).
    if (aceitaVinculos(elemento)) {
        LinhaDoSeletorDeElementos(frameId, ehCena = false, estado = estado, acoes = acoes, aoEscolher = { acoes.aoAbrirSeletorDoRetrato(elemento, frameId) })
    }
    if (frameId == null) {
        val etapa = estado.etapasDeImagem[chave]
        val ocupado = etapa != null || elemento.id in estado.retratosOcupados
        Text(
            "Ainda não há retrato deste elemento neste capítulo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        estado.mensagensDeRetrato[elemento.id]?.let { RecadoDaCena(it) }
        if (ocupado) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(descreverEtapa(etapa ?: EtapaDaImagem.CRIANDO_O_RETRATO), style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = { acoes.aoGerarRetrato(elemento) }, enabled = !ocupado) { Text("Gerar retrato", maxLines = 1, softWrap = false) }
        Text(avisoDoBotaoPrincipal(jaTemPrompt = false), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        BlocoDePrompts(
            frameId, rotuloDoRetrato(elemento), estado, acoes, chave, "Gerar retrato", ehCena = false,
            aoEscolherElementos = if (aceitaVinculos(elemento)) ({ acoes.aoAbrirSeletorDoRetrato(elemento, frameId) }) else null,
        )
    }
}

@Composable
private fun CabecalhoDoPainel(aoFechar: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            Text("IA do capítulo", style = MaterialTheme.typography.titleMedium)
        }
        if (aoFechar != null) {
            IconButton(onClick = aoFechar) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar o painel de IA")
            }
        }
    }
}

@Composable
private fun CorpoDoPainel(estado: EstadoDoPainel, acoes: AcoesDoPainel, retratos: Map<Int, Int>) {
    when (val conteudo = estado.conteudo) {
        // P1: o painel só pede algo ao servidor depois de aberto; até lá não há o que mostrar.
        ConteudoDoPainel.NaoCarregado, ConteudoDoPainel.Lendo -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }

        is ConteudoDoPainel.Erro -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    conteudo.motivo,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = acoes.aoTentarDeNovo) { Text("Tentar de novo") }
            }
        }

        is ConteudoDoPainel.NuncaAnalisado -> NuncaAnalisado(conteudo, estado, acoes)

        is ConteudoDoPainel.Pronto -> ListaDeSugestoes(conteudo.sugestoes, estado, acoes, retratos)
    }
}

/** P6: o capítulo nunca foi analisado — o único botão é "Analisar com IA". */
@Composable
private fun NuncaAnalisado(
    conteudo: ConteudoDoPainel.NuncaAnalisado,
    estado: EstadoDoPainel,
    acoes: AcoesDoPainel,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Este capítulo ainda não foi analisado.", style = MaterialTheme.typography.titleSmall)
        Text(
            "A IA lê o capítulo e sugere os personagens, lugares, objetos e cenas que valem ilustrar. " +
                "Gasta uma chamada de IA.",
            style = MaterialTheme.typography.bodyMedium,
        )
        // P11: sem bloquear nada — só diz que o contexto desta análise está mais pobre.
        descreverPendentesAnteriores(conteudo.pendentesAnteriores)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        ErroDaAnalise(estado)
        AnaliseEmAndamento(estado)
        Button(onClick = acoes.aoAnalisar, enabled = !estado.analisando) {
            Text("Analisar com IA")
        }
    }
}

/**
 * A lista de sugestões (E24): filtros com contadores no topo, cartões **compactos** que abrem ao
 * toque, e as cenas por último. Quais cartões estão abertos é estado só da tela (sobrevive a girar
 * o aparelho) e vale **só dentro do filtro em que foi aberto** (E33): ao trocar de filtro, ou quando um
 * cartão muda de filtro (ao ser confirmado, por exemplo), ele volta compacto. A chave junta o filtro e
 * o id; as cenas usam "cena".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListaDeSugestoes(sugestoes: SugestoesDeCapitulo, estado: EstadoDoPainel, acoes: AcoesDoPainel, retratos: Map<Int, Int>) {
    val abertos = rememberSaveable { mutableStateListOf<String>() }
    fun alternar(chave: String) {
        if (chave in abertos) abertos.remove(chave) else abertos.add(chave)
    }
    // E33: trocar de filtro fecha tudo, para cada lista sempre começar compacta.
    LaunchedEffect(estado.filtro) { abertos.clear() }

    val contagem = contagemPorFiltro(sugestoes.elementos, sugestoes.cenas)
    val doFiltro = elementosDoFiltro(sugestoes.elementos, estado.filtro)
    val cenasDosElementos = cenasDoElemento(sugestoes)
    val cenas = cenasDoFiltro(sugestoes.cenas, estado.filtro) // D2: as cenas também obedecem ao filtro

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            // O título numa linha e os botões embaixo, numa FlowRow: com dois botões, a linha única espremia o texto
            // do segundo ("Reanalisar" saía na vertical) em telas estreitas. Aqui os botões quebram de linha se faltar espaço.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sugestões da IA", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // L1: só tem sentido quando há algo a confirmar; o diálogo mostra a conta antes de agir.
                    Button(
                        onClick = acoes.aoPedirConfirmarTodos,
                        enabled = !estado.analisando && !estado.executandoLote && resumoParaConfirmarTodos(sugestoes).temAlgoParaConfirmar,
                    ) { Text("Confirmar todos", maxLines = 1, softWrap = false) }
                    OutlinedButton(onClick = acoes.aoPedirReanalise, enabled = !estado.analisando && !estado.executandoLote) {
                        Text("Reanalisar", maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        item { ErroDaAnalise(estado) }
        item { AnaliseEmAndamento(estado) }
        item { ResultadoDoLote(estado, acoes) }

        // P14: uma análise que não achou nada é um resultado, não um erro.
        if (sugestoes.elementos.isEmpty() && sugestoes.cenas.isEmpty()) {
            item {
                Text(
                    "A análise não encontrou elementos nem cenas neste capítulo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (sugestoes.elementos.isNotEmpty() || sugestoes.cenas.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Elementos e cenas", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FiltroDoPainel.entries.forEach { filtro ->
                            FilterChip(
                                selected = estado.filtro == filtro,
                                onClick = { acoes.aoEscolherFiltro(filtro) },
                                label = { Text("${filtro.rotulo} (${contagem[filtro]})") },
                            )
                        }
                    }
                }
            }
            if (doFiltro.isEmpty() && cenas.isEmpty()) {
                item {
                    Text(
                        when (estado.filtro) {
                            FiltroDoPainel.PENDENTES -> "Nada pendente por aqui."
                            FiltroDoPainel.CONFIRMADOS -> "Nada confirmado neste capítulo ainda."
                            FiltroDoPainel.DESCARTADOS -> "Nada descartado."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(doFiltro, key = { "e${it.id}" }) { elemento ->
                if (elemento.descartada) {
                    CartaoDeElementoDescartado(elemento, estado, acoes)
                } else {
                    val chave = chaveDoCartao(elemento)
                    CartaoDeElemento(
                        elemento = elemento,
                        cenas = cenasDosElementos[elemento.id].orEmpty(),
                        aberto = chave in abertos,
                        aoAlternar = { alternar(chave) },
                        estado = estado,
                        acoes = acoes,
                        frameDoRetrato = retratos[elemento.id] ?: estado.retratosCriados[elemento.id],
                    )
                }
            }
        }

        if (cenas.isNotEmpty()) {
            item { Text("Cenas (${cenas.size})", style = MaterialTheme.typography.titleSmall) }
            items(cenas, key = { "c${it.id}" }) { cena ->
                val chaveDaCena = "cena:${cena.id}"
                CartaoDeCena(cena, aberto = chaveDaCena in abertos, aoAlternar = { alternar(chaveDaCena) }, estado = estado, acoes = acoes)
            }
        }
    }
}

/** O aviso do cartão cujo nome a IA sugeriu mas o texto do capítulo não traz (item 6.7, `achado_no_texto`). */
internal const val AVISO_DE_NAO_ACHADO_NO_TEXTO = "Não achado no texto — confira"

/**
 * E24 e E25: o cartão **fechado** tem tipo, nome, **uma** etiqueta de situação e, se for o caso, "Aparece em
 * N cenas"; o recado da última ação e o "em andamento" aparecem também fechado. **Aberto** mostra os detalhes
 * que cada situação tem (a "Sugestão da IA" só numa sugestão nova — numa casada ela só repete a identidade) e as
 * ações.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CartaoDeElemento(
    elemento: ElementoSugerido,
    cenas: List<String>,
    aberto: Boolean,
    aoAlternar: () -> Unit,
    estado: EstadoDoPainel,
    acoes: AcoesDoPainel,
    /** O frame do retrato deste elemento neste capítulo, se já existe (N4). */
    frameDoRetrato: Int? = null,
) {
    val ocupado = elemento.id in estado.ocupados
    val situacao = situacaoDoElemento(elemento)
    Card(onClick = aoAlternar, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        rotuloDoTipo(elemento.tipo),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        elemento.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (aberto) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Etiqueta(etiquetaDaSituacao(elemento), situacao)
            }
            descreverCenasDoElemento(cenas)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            if (!elemento.achado_no_texto) {
                Text(
                    AVISO_DE_NAO_ACHADO_NO_TEXTO,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            estado.mensagens[elemento.id]?.let { mensagem ->
                Text(
                    mensagem.texto,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mensagem.ehErro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )
            }
            if (ocupado) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (aberto) {
                DetalhesDoElemento(elemento, situacao, cenas)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    acoesDoElemento(situacao).forEachIndexed { indice, acao ->
                        val aoTocar: () -> Unit = {
                            val elementoCasado = elemento.elemento_id
                            if (acao == AcaoDoElemento.ABRIR_FICHA && elementoCasado != null) {
                                acoes.aoAbrirFicha(elementoCasado, true)
                            } else {
                                acoes.aoExecutar(acao, elemento)
                            }
                        }
                        if (indice == 0) {
                            Button(onClick = aoTocar, enabled = !ocupado) { Text(acao.rotulo) }
                        } else {
                            OutlinedButton(onClick = aoTocar, enabled = !ocupado) { Text(acao.rotulo) }
                        }
                    }
                }
                // N1 e N3: o retrato (e, com ele, os prompts) vive no cartão — na lista do painel e no modal, a mesma coisa.
                if (podeTerRetrato(elemento)) BlocoDoRetrato(elemento, frameDoRetrato, estado, acoes)
            }
        }
    }
}

/** O que o cartão aberto mostra, conforme a situação (E25). */
@Composable
private fun DetalhesDoElemento(elemento: ElementoSugerido, situacao: SituacaoDoElemento, cenas: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (situacao == SituacaoDoElemento.NOVA) {
            elemento.descricao?.let { Rotulado("Sugestão da IA", it) }
        }
        elemento.elemento_casado?.let { casado ->
            Rotulado(
                "Casada com ${casado.nome} (${rotuloDoTipo(casado.tipo)})",
                casado.identidade ?: "Sem identidade registrada.",
                maxLinhas = 4,
            )
        }
        linhaDoEstado(elemento)?.let { Rotulado(it.rotulo, it.texto, maxLinhas = 4) }
        if (elemento.elemento_id != null && elemento.estado_vigente == null) {
            Text(
                "Este elemento ainda não tem estado até este capítulo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (cenas.isNotEmpty()) {
            Rotulado("Aparece em", cenas.joinToString("; "))
        }
    }
}

/** A etiqueta de situação do cartão fechado (E24). */
@Composable
private fun Etiqueta(texto: String, situacao: SituacaoDoElemento) {
    val cor: Color = when (situacao) {
        SituacaoDoElemento.NOVA -> MaterialTheme.colorScheme.primaryContainer
        SituacaoDoElemento.CASADA_AUTOMATICAMENTE -> MaterialTheme.colorScheme.tertiaryContainer
        SituacaoDoElemento.CASADA_SEM_ESTADO -> MaterialTheme.colorScheme.secondaryContainer
        SituacaoDoElemento.CONFIRMADA -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = cor, shape = RoundedCornerShape(8.dp)) {
        Text(texto, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/** Um texto com um rótulo pequeno em cima; [maxLinhas] corta textos longos com reticências. */
@Composable
private fun Rotulado(rotulo: String, texto: String, maxLinhas: Int = Int.MAX_VALUE) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(texto, style = MaterialTheme.typography.bodyMedium, maxLines = maxLinhas, overflow = TextOverflow.Ellipsis)
    }
}

/** Uma sugestão descartada (E16), com o caminho de volta. */
@Composable
internal fun CartaoDeElementoDescartado(elemento: ElementoSugerido, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val ocupado = elemento.id in estado.ocupados
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(rotuloDoTipo(elemento.tipo), style = MaterialTheme.typography.labelMedium)
                    Text(elemento.nome, style = MaterialTheme.typography.titleSmall)
                }
                TextButton(onClick = { acoes.aoRestaurar(elemento) }, enabled = !ocupado) { Text("Restaurar") }
            }
            estado.mensagens[elemento.id]?.let {
                Text(it.texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** E2: tipo, nome e identidade já preenchidos pela IA, editáveis. O 409 oferece vincular. */
@Composable
private fun DialogoCriarElemento(dialogo: DialogoDeElemento.Criando, acoes: AcoesDoPainel) {
    val sugestao = dialogo.sugestao
    var tipo by rememberSaveable { mutableStateOf(sugestao.tipo) }
    var nome by rememberSaveable { mutableStateOf(sugestao.nome) }
    var descricao by rememberSaveable { mutableStateOf(sugestao.descricao.orEmpty()) }

    AlertDialog(
        onDismissRequest = { if (!dialogo.salvando) acoes.aoCancelarDialogo() },
        title = { Text("Criar elemento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                SeletorDeTipo(tipo) { tipo = it }
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it },
                    label = { Text("Quem ou o que é") },
                    modifier = Modifier.fillMaxWidth(),
                )
                dialogo.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (dialogo.conflito) {
                    TextButton(onClick = acoes.aoTrocarCriacaoPorVinculo) { Text("Vincular a um existente") }
                }
                if (dialogo.salvando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { acoes.aoConfirmarCriacao(tipo, nome, descricao) },
                enabled = !dialogo.salvando,
            ) { Text("Criar") }
        },
        dismissButton = {
            TextButton(onClick = acoes.aoCancelarDialogo, enabled = !dialogo.salvando) { Text("Cancelar") }
        },
    )
}

/** O menu de tipos de elemento do diálogo de criar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorDeTipo(tipo: String, aoEscolher: (String) -> Unit) {
    var menuAberto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = menuAberto, onExpandedChange = { menuAberto = it }) {
        OutlinedTextField(
            value = rotuloDoTipo(tipo),
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuAberto) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
            TIPOS_DE_ELEMENTO.forEach { opcao ->
                DropdownMenuItem(
                    text = { Text(rotuloDoTipo(opcao)) },
                    onClick = {
                        aoEscolher(opcao)
                        menuAberto = false
                    },
                )
            }
        }
    }
}

/**
 * E3, E5 e E23: a lista dos elementos do livro, **do mesmo tipo** da sugestão (com a chave "Incluir
 * outros tipos"), com busca. Cada um tem o nome **em linha própria** e, embaixo, "Ver ficha" — que abre
 * a ficha por cima; ao voltar, este diálogo reaparece como estava — e "Usar este".
 */
@Composable
private fun DialogoVincularElemento(dialogo: DialogoDeElemento.Vinculando, acoes: AcoesDoPainel) {
    var busca by rememberSaveable { mutableStateOf("") }
    var outrosTipos by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!dialogo.salvando) acoes.aoCancelarDialogo() },
        title = { Text(if (dialogo.trocando) "Trocar por outro elemento" else "Vincular a um existente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Rotulado(
                    "Sugestão da IA (${rotuloDoTipo(dialogo.sugestao.tipo)})",
                    dialogo.sugestao.nome,
                )
                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it },
                    label = { Text("Buscar pelo nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = outrosTipos,
                    onClick = { outrosTipos = !outrosTipos },
                    label = { Text("Incluir outros tipos") },
                )
                dialogo.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (dialogo.salvando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                when (val lista = dialogo.lista) {
                    ListaParaVincular.Carregando ->
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                    is ListaParaVincular.Erro -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lista.motivo, color = MaterialTheme.colorScheme.error)
                        Button(onClick = acoes.aoRecarregarLista) { Text("Tentar de novo") }
                    }
                    is ListaParaVincular.Pronta -> {
                        val filtrados = filtrarParaVincular(lista.elementos, dialogo.sugestao.tipo, busca, outrosTipos)
                        if (filtrados.isEmpty()) {
                            Text(
                                if (outrosTipos) "Nenhum elemento encontrado."
                                else "Nenhum elemento de ${rotuloDoTipo(dialogo.sugestao.tipo).lowercase()} encontrado.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(filtrados, key = { it.id }) { elemento ->
                                ElementoDaLista(elemento, dialogo.salvando, acoes)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = acoes.aoCancelarDialogo, enabled = !dialogo.salvando) { Text("Cancelar") }
        },
    )
}

/** O nome em linha própria (nunca espremido por botões) e as ações embaixo (E23). */
@Composable
private fun ElementoDaLista(elemento: ElementoDoLivro, salvando: Boolean, acoes: AcoesDoPainel) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(elemento.nome, style = MaterialTheme.typography.titleSmall)
        Text(
            rotuloDoTipo(elemento.tipo),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { acoes.aoAbrirFicha(elemento.id, false) }) { Text("Ver ficha") }
            Button(onClick = { acoes.aoEscolherElemento(elemento.id) }, enabled = !salvando) { Text("Usar este") }
        }
    }
}

/** E15: desligar a sugestão do elemento, com a escolha — nunca automática — de apagar o estado daqui. */
@Composable
private fun DialogoDesfazer(dialogo: DialogoDeElemento.Desfazendo, acoes: AcoesDoPainel) {
    val nome = dialogo.sugestao.elemento_casado?.nome ?: dialogo.sugestao.nome
    AlertDialog(
        onDismissRequest = { if (!dialogo.salvando) acoes.aoCancelarDialogo() },
        title = { Text("Desfazer confirmação?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A sugestão \"${dialogo.sugestao.nome}\" deixa de estar ligada a $nome e volta a ser nova. O elemento não é apagado.")
                if (dialogo.sugestao.estado_id != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !dialogo.salvando) { acoes.aoAlternarApagarEstado() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = dialogo.apagarEstado, onCheckedChange = { acoes.aoAlternarApagarEstado() }, enabled = !dialogo.salvando)
                        Text(
                            "Apagar também o estado deste capítulo. Frames que usam esse estado perdem esse participante.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                dialogo.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (dialogo.salvando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = acoes.aoConfirmarDesfazer, enabled = !dialogo.salvando) { Text("Desfazer") }
        },
        dismissButton = {
            TextButton(onClick = acoes.aoCancelarDialogo, enabled = !dialogo.salvando) { Text("Cancelar") }
        },
    )
}

/** E27: descartar quem aparece em cenas sugeridas pede confirmação, dizendo quais cenas. */
@Composable
private fun DialogoDescartarEmCenas(dialogo: DialogoDeElemento.DescartandoEmCenas, acoes: AcoesDoPainel) {
    AlertDialog(
        onDismissRequest = acoes.aoCancelarDialogo,
        title = { Text("Descartar ${dialogo.sugestao.nome}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Este elemento aparece em cenas sugeridas. Descartá-lo pode atrapalhar a imagem delas depois:")
                dialogo.cenas.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = acoes.aoConfirmarDescarte) { Text("Descartar mesmo assim") } },
        dismissButton = { TextButton(onClick = acoes.aoCancelarDialogo) { Text("Cancelar") } },
    )
}

/**
 * O cartão da cena na lista do painel: título, quantos participantes e **uma etiqueta de situação**. **Tocar o expande no
 * lugar**, com o **mesmo corpo do modal** (participantes, decisões e, na cena confirmada, os prompts): tudo o que se faz
 * pelo ícone no texto também se faz pela lista (pedido do Allan, 01/10/2026).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CartaoDeCena(cena: CenaSugerida, aberto: Boolean, aoAlternar: () -> Unit, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val ocupada = cena.id in estado.cenasOcupadas
    Card(onClick = aoAlternar, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        cena.titulo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (aberto) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when (cena.participantes.size) {
                            0 -> "Sem participantes"
                            1 -> "1 participante"
                            else -> "${cena.participantes.size} participantes"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                EtiquetaDaCena(etiquetaDaCena(cena), filtroDaCena(cena))
            }
            // Fechado, o recado e o progresso continuam visíveis; aberto, o corpo já os mostra.
            if (!aberto) {
                estado.mensagensDeCena[cena.id]?.let { RecadoDaCena(it) }
                if (ocupada) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                CorpoDaCena(cena, estado, acoes)
            }
        }
    }
}

/**
 * A área de um frame que já existe (cena ou retrato): **as imagens em destaque**, com o botão principal e o de importar
 * (Q1 a Q4, T), e os **prompts recolhidos** em "Ver prompts" (Q7), onde ficam a lista, copiar, compartilhar, editar, o
 * *Gerar imagem* de um prompt antigo. O **Novo prompt** (G3: com diálogo de custo e ajuste opcional, Q6) fica **à vista**, ao lado
 * do botão principal. Ler a lista não custa (G2).
 */
@Composable
private fun BlocoDePrompts(
    frameId: Int,
    rotulo: String,
    estado: EstadoDoPainel,
    acoes: AcoesDoPainel,
    chaveDoFluxo: String,
    rotuloDoBotao: String,
    ehCena: Boolean,
    aoEscolherElementos: (() -> Unit)? = null,
) {
    LaunchedEffect(frameId) { acoes.aoCarregarPrompts(frameId) }
    // G13: com este modal na tela, ele mostra o próprio aviso; o aviso global (que ficaria atrás do modal) se cala.
    DisposableEffect(frameId) {
        acoes.aoModalDoPromptVisivel(frameId)
        onDispose { acoes.aoModalDoPromptVisivel(null) }
    }
    val area = LocalClipboardManager.current
    val contexto = LocalContext.current
    val conteudo = estado.prompts[frameId]
    val lista = (conteudo as? PromptsDoFrame.Pronto)?.lista.orEmpty()
    var aberto by rememberSaveable(frameId) { mutableStateOf(false) }

    // Q1: a imagem em destaque, com o botão que faz o que falta.
    SecaoDaImagemDoFrame(frameId, lista, estado, acoes, chaveDoFluxo, rotulo, rotuloDoBotao, ehCena)

    // Falhas do prompt (do fluxo ou do Novo prompt) ficam fora do recolhido: a pessoa precisa vê-las.
    estado.mensagensDePrompt[frameId]?.let { RecadoDaCena(it) }

    // Q7: os prompts recolhidos.
    TextButton(onClick = { aberto = !aberto }) { Text(rotuloDeVerPrompts(aberto, lista.size)) }
    if (aberto) {
        when (conteudo) {
            null, PromptsDoFrame.Lendo -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is PromptsDoFrame.Erro -> {
                Text(conteudo.motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { acoes.aoRecarregarPrompts(frameId) }) { Text("Tentar de novo") }
            }
            is PromptsDoFrame.Pronto -> {
                if (conteudo.lista.isEmpty()) {
                    Text("Nenhum prompt gerado ainda.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                conteudo.lista.forEach { prompt ->
                    CartaoDePrompt(
                        prompt,
                        numero = numeroDoPrompt(conteudo.lista, prompt.id) ?: 0,
                        frameId = frameId,
                        estado = estado,
                        acoes = acoes,
                        ehCena = ehCena,
                        aoEscolherElementos = aoEscolherElementos,
                        aoCopiar = { area.setText(AnnotatedString(prompt.texto)) },
                        aoCompartilhar = { compartilharTexto(contexto, prompt.texto) },
                    )
                }
            }
        }
        // O "Novo prompt" saiu daqui: ficou à vista, ao lado do botão principal (Q6).
    }
    AvisoDePromptGerado(estado.promptsGerados[frameId] ?: 0)
}

/**
 * O aviso translúcido "Prompt gerado." (G13), no mesmo espírito do aviso global da análise: aparece por uns segundos
 * quando o número de prompts gerados **sobe**. Mostra-se aqui, e não só no aviso global, porque o modal é uma janela
 * por cima de tudo e esconderia o aviso do app.
 */
@Composable
private fun AvisoDePromptGerado(geradosAgora: Int) {
    val aoAbrir = remember { geradosAgora } // o que já havia quando o modal abriu não conta
    var visivel by remember { mutableStateOf(false) }
    LaunchedEffect(geradosAgora) {
        if (geradosAgora > aoAbrir) {
            visivel = true
            delay(DURACAO_DO_AVISO_DE_PROMPT_EM_MS)
            visivel = false
        }
    }
    AnimatedVisibility(visible = visivel) {
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(AVISO_PROMPT_GERADO, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Abre o seletor do Android para mandar o [texto] a outro app (uma IA, por exemplo) — G11. */
private fun compartilharTexto(contexto: Context, texto: String) {
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    contexto.startActivity(Intent.createChooser(envio, "Compartilhar o prompt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Um prompt gerado: o texto (selecionável) e **Copiar**, que confirma na hora (G6). */
@Composable
private fun CartaoDePrompt(
    prompt: PromptDeFrame,
    numero: Int,
    frameId: Int,
    estado: EstadoDoPainel,
    acoes: AcoesDoPainel,
    ehCena: Boolean,
    aoEscolherElementos: (() -> Unit)?,
    aoCopiar: () -> Unit,
    aoCompartilhar: () -> Unit,
) {
    var copiado by remember(prompt.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // PN1: o número do prompt dentro do frame (1 = o mais antigo), para o botão de gerar poder citá-lo.
            Text("Prompt $numero", style = MaterialTheme.typography.titleSmall)
            EtiquetasDoPrompt(prompt)
            SelectionContainer { Text(prompt.texto, style = MaterialTheme.typography.bodyMedium) }
            // EV14: as imagens que foram de referência na geração deste prompt.
            if (prompt.imagens_de_referencia.isNotEmpty()) MiniaturasDeReferencia(prompt.imagens_de_referencia, legenda = "Referências enviadas")
            descreverReferenciasVisuais(prompt.referencias_visuais.size)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            // Z12: o modelo que este "Gerar imagem" vai usar, com a troca, à vista em todo cartão.
            val ocupado = prompt.id in estado.gerandoImagem || prompt.id in estado.importandoImagem
            ModeloDeImagemEmUso(estado, acoes, ocupado)
            // As imagens de referência são do frame, mas valem para qualquer prompt: a escolha também fica à vista aqui.
            aoEscolherElementos?.let { LinhaDoSeletorDeElementos(frameId, ehCena, estado, acoes, aoEscolher = it, ocupado = ocupado) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { aoCopiar(); copiado = true }) { Text("Copiar", maxLines = 1, softWrap = false) }
                OutlinedButton(onClick = aoCompartilhar) { Text("Compartilhar", maxLines = 1, softWrap = false) }
                // K1: gera de verdade, sem confirmação (a imagem custa cerca de US$ 0,01). K2: um pedido por prompt.
                OutlinedButton(
                    onClick = { acoes.aoGerarImagem(frameId, prompt.id, null, null) },
                    enabled = prompt.id !in estado.gerandoImagem && prompt.id !in estado.importandoImagem,
                ) { Text(rotuloDeGerarComNumero("Gerar imagem", numero), maxLines = 1, softWrap = false) }
                // R1: editar o texto antes de gerar; T4: a importação é única, por frame (não por prompt).
                OutlinedButton(onClick = { acoes.aoEditarPrompt(frameId, prompt.id, prompt.texto) }) { Text("Editar", maxLines = 1, softWrap = false) }
            }
            ImagensDoPrompt(prompt, acoes)
            if (copiado) Text(AVISO_PROMPT_COPIADO, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/** As etiquetas do prompt (K5): "Versão suavizada/editada" e "Recusado pelo provedor", com o motivo da recusa à mostra. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EtiquetasDoPrompt(prompt: PromptDeFrame) {
    val etiquetas = etiquetasDoPrompt(prompt)
    if (etiquetas.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        etiquetas.forEach { etiqueta ->
            val recusado = etiqueta == "Recusado pelo provedor"
            Surface(
                color = if (recusado) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(etiqueta, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    prompt.motivo_da_recusa?.takeIf { prompt.situacao_da_geracao == "RECUSADO" }?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** U3, LX8: excluir **move a imagem para a lixeira** (dá para recuperar), mas ainda pede confirmação. */
@Composable
private fun DialogoExcluirImagem(acoes: AcoesDoPainel) {
    AlertDialog(
        onDismissRequest = acoes.aoCancelarExclusaoDeImagem,
        title = { Text("Mover esta imagem para a lixeira?") },
        text = { Text("A imagem sai do prompt e do capítulo, mas fica na Lixeira (menu da Biblioteca), de onde você pode restaurá-la ou apagá-la de vez. O prompt continua.") },
        confirmButton = { TextButton(onClick = acoes.aoConfirmarExclusaoDeImagem) { Text("Mover para a lixeira") } },
        dismissButton = { TextButton(onClick = acoes.aoCancelarExclusaoDeImagem) { Text("Cancelar") } },
    )
}

/** Z6: escolher o modelo de imagem das próximas gerações. Não muda o padrão do servidor. */
@Composable
private fun DialogoEscolherModelo(modelos: ModelosDeImagem, escolhido: String?, acoes: AcoesDoPainel) {
    AlertDialog(
        onDismissRequest = acoes.aoFecharEscolhaDeModelo,
        title = { Text("Modelo de imagem") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Vale para as próximas imagens até você trocar de novo. Não muda o padrão do servidor.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SeletorDeModelo(
                    modelosParaEscolher(modelos), modelos.padrao, modeloEmUso(escolhido, modelos), acoes.aoEscolherModelo,
                    semFiltro = modelosSemFiltroParaEscolher(modelos),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = acoes.aoFecharEscolhaDeModelo) { Text("Fechar") } },
    )
}

/**
 * O diálogo da recusa (K4, Z9): o provedor recusou o prompt (de novo, depois da suavização). Diz **qual modelo recusou**, mostra
 * o **motivo**, o prompt devolvido num campo **editável** e o **seletor de modelo**, já marcado com o primeiro modelo
 * **diferente** do que recusou. **Tentar com este modelo** manda o texto com esse modelo (que vira o modelo ativo).
 * Desenhado **uma vez**, na raiz do painel, como o do *Gerar prompt*.
 */
@Composable
private fun DialogoDeRecusaDeImagem(recusa: RecusaDeImagem, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val opcoes = estado.modelosDeImagem?.let(::modelosParaEscolher).orEmpty()
    DialogoDoTextoDoPrompt(
        chave = "recusa${recusa.promptId}",
        titulo = recusa.modelo?.let { "O modelo $it recusou este prompt" } ?: "O provedor recusou este prompt",
        motivo = recusa.motivo,
        explicacao = "Escolha outro modelo ou edite o prompt, e tente de novo. A nova tentativa vai direto ao modelo, sem outra suavização.",
        textoInicial = recusa.texto,
        rotuloDoBotao = "Tentar com este modelo",
        opcoesDeModelo = opcoes,
        opcoesSemFiltro = estado.modelosDeImagem?.let(::modelosSemFiltroParaEscolher).orEmpty(),
        padraoDoServidor = estado.modelosDeImagem?.padrao,
        modeloInicial = alternativaAoModelo(recusa.modelo, opcoes),
        aoConfirmar = { texto, modelo -> acoes.aoGerarImagem(recusa.frameId, recusa.promptId, texto, modelo) },
        aoFechar = acoes.aoFecharRecusaDeImagem,
        rotuloDoFechar = "Fechar",
    )
}

/**
 * O diálogo de **editar o prompt** (R1 a R3): o mesmo campo do da recusa, com o texto do prompt e o seletor de modelo (já no
 * modelo ativo). **Gerar imagem com este texto** envia a edição como prompt novo, em chamada direta (sem suavizar);
 * **Cancelar** não muda nada.
 */
@Composable
private fun DialogoDeEdicaoDePrompt(edicao: EdicaoDePrompt, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val opcoes = estado.modelosDeImagem?.let(::modelosParaEscolher).orEmpty()
    DialogoDoTextoDoPrompt(
        chave = "edicao${edicao.promptId}",
        titulo = "Editar o prompt",
        motivo = null,
        explicacao = "O texto editado vira um prompt novo, ligado a este; o original não muda. Vai direto ao modelo, sem suavizar.",
        textoInicial = edicao.texto,
        rotuloDoBotao = "Gerar imagem com este texto",
        opcoesDeModelo = opcoes,
        opcoesSemFiltro = estado.modelosDeImagem?.let(::modelosSemFiltroParaEscolher).orEmpty(),
        padraoDoServidor = estado.modelosDeImagem?.padrao,
        modeloInicial = modeloEmUso(estado.modeloEscolhido, estado.modelosDeImagem),
        aoConfirmar = { texto, modelo -> acoes.aoGerarImagem(edicao.frameId, edicao.promptId, texto, modelo) },
        aoFechar = acoes.aoFecharEdicaoDePrompt,
        rotuloDoFechar = "Cancelar",
    )
}

/** O campo editável do texto de um prompt, com o seletor de modelo de imagem, comum à recusa (K4, Z9) e à edição (R1). */
@Composable
private fun DialogoDoTextoDoPrompt(
    chave: String,
    titulo: String,
    motivo: String?,
    explicacao: String,
    textoInicial: String,
    rotuloDoBotao: String,
    opcoesDeModelo: List<String>,
    opcoesSemFiltro: List<String>,
    padraoDoServidor: String?,
    modeloInicial: String?,
    aoConfirmar: (texto: String, modelo: String?) -> Unit,
    aoFechar: () -> Unit,
    rotuloDoFechar: String,
) {
    var texto by rememberSaveable(chave) { mutableStateOf(textoInicial) }
    var modelo by rememberSaveable(chave) { mutableStateOf(modeloInicial) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                motivo?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Text(explicacao, style = MaterialTheme.typography.bodySmall)
                if (opcoesDeModelo.isNotEmpty()) {
                    Text("Modelo de imagem", style = MaterialTheme.typography.labelLarge)
                    SeletorDeModelo(opcoesDeModelo, padraoDoServidor, modelo, { modelo = it }, semFiltro = opcoesSemFiltro)
                }
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(LIMITE_DO_PROMPT_EDITADO) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Prompt") },
                    minLines = 4,
                    maxLines = 10,
                )
            }
        },
        confirmButton = { TextButton(onClick = { aoConfirmar(texto, modelo) }, enabled = texto.isNotBlank()) { Text(rotuloDoBotao) } },
        dismissButton = { TextButton(onClick = aoFechar) { Text(rotuloDoFechar) } },
    )
}

@Composable
private fun EtiquetaDaCena(texto: String, filtro: FiltroDoPainel) {
    val cor: Color = when (filtro) {
        FiltroDoPainel.PENDENTES -> MaterialTheme.colorScheme.primaryContainer
        FiltroDoPainel.CONFIRMADOS -> MaterialTheme.colorScheme.surfaceVariant
        FiltroDoPainel.DESCARTADOS -> MaterialTheme.colorScheme.errorContainer
    }
    Surface(color = cor, shape = RoundedCornerShape(8.dp)) {
        Text(texto, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RecadoDaCena(mensagem: MensagemDoElemento) {
    Text(
        mensagem.texto,
        style = MaterialTheme.typography.bodySmall,
        color = if (mensagem.ehErro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
    )
}

/**
 * O modal da cena (C1 a C7): a mesma folha do modal do elemento, com título, descrição, situação, **cada participante
 * com a sua situação** e as ações de decisão. Fechar volta ao texto exatamente onde estava.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalDaCena(estado: EstadoDoPainel, acoes: AcoesDoPainel, id: Int) {
    val sugestoes = (estado.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes
    val cena = sugestoes?.cenas?.firstOrNull { it.id == id }

    ModalBottomSheet(
        onDismissRequest = acoes.aoFecharModalDaCena,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // Com rolagem: o que passa da altura da folha (um prompt longo, por exemplo) ficava cortado.
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                sugestoes == null -> when (val conteudo = estado.conteudo) {
                    is ConteudoDoPainel.Erro -> {
                        Text(conteudo.motivo, color = MaterialTheme.colorScheme.error)
                        Button(onClick = acoes.aoTentarDeNovo) { Text("Tentar de novo") }
                    }
                    else -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                // Sumiu da lista (uma reanálise refez as sugestões): não há mais o que mostrar.
                cena == null -> Text(
                    "Esta cena não existe mais. Feche e toque de novo no ícone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> ConteudoDoModalDaCena(cena, estado, acoes)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConteudoDoModalDaCena(cena: CenaSugerida, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(cena.titulo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        EtiquetaDaCena(etiquetaDaCena(cena), filtroDaCena(cena))
    }
    CorpoDaCena(cena, estado, acoes)
}

/**
 * O corpo de uma cena (C3 a C7 e G1 a G13): descrição, participantes com as suas ações, decisões e, na cena confirmada, os
 * prompts. **O mesmo** no modal (ícone no texto) e no cartão expandido da lista do painel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CorpoDaCena(cena: CenaSugerida, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val ocupada = cena.id in estado.cenasOcupadas
    val sugestoes = (estado.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes

    cena.descricao?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    val situacao = listOfNotNull(cena.horario, cena.clima, cena.humor)
    if (situacao.isNotEmpty()) {
        Text(situacao.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Text("Participantes", style = MaterialTheme.typography.titleSmall)
    cena.participantes.forEach { participante ->
        // O elemento do participante vem na mesma resposta: com ele, sabe-se tudo o que falta para confirmar a cena.
        val elemento = sugestoes?.elementos?.firstOrNull { it.id == participante.sugestao_elemento_id }
        val situacaoDele = situacaoDoParticipante(participante, elemento)
        val ocupado = elemento != null && elemento.id in estado.ocupados
        val pendente = situacaoDele.precisaRevisar || situacaoDele.acaoRapida != null
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${rotuloDoTipo(participante.tipo)}: ${participante.nome}", style = MaterialTheme.typography.bodyMedium)
            Text(
                situacaoDele.texto,
                style = MaterialTheme.typography.labelMedium,
                color = if (pendente) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            elemento?.let { estado.mensagens[it.id] }?.let { RecadoDaCena(it) }
            if (ocupado) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (pendente) {
                // C11: a ação principal ali mesmo (confirmar o casamento, registrar o estado) e, sempre, "Revisar", que
                // empilha o modal do elemento por cima (C12).
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (elemento != null && situacaoDele.acaoRapida != null) {
                        Button(onClick = { acoes.aoExecutar(situacaoDele.acaoRapida, elemento) }, enabled = !ocupado) {
                            Text(situacaoDele.acaoRapida.rotulo)
                        }
                    }
                    OutlinedButton(onClick = { acoes.aoRevisarParticipante(participante.sugestao_elemento_id) }, enabled = !ocupado) {
                        Text("Revisar")
                    }
                }
            }
        }
    }

    estado.mensagensDeCena[cena.id]?.let { RecadoDaCena(it) }
    if (ocupada) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

    // G1: o bloco de prompts só existe para a cena que já virou frame.
    cena.frame_id?.let { frameId -> BlocoDePrompts(frameId, cena.titulo, estado, acoes, chaveDoFluxoDoFrame(frameId), "Gerar imagem", ehCena = true, aoEscolherElementos = { acoes.aoAbrirSeletorDaCena(frameId) }) }

    val acoesDaCena = acoesDaCena(cena)
    if (acoesDaCena.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            acoesDaCena.forEachIndexed { indice, acao ->
                val aoTocar = { acoes.aoExecutarCena(acao, cena) }
                if (indice == 0) {
                    Button(onClick = aoTocar, enabled = !ocupada) { Text(acao.rotulo) }
                } else {
                    OutlinedButton(onClick = aoTocar, enabled = !ocupada) { Text(acao.rotulo) }
                }
            }
        }
    }
}

@Composable
private fun Destaque(texto: String) {
    // O "confira" chama atenção; os demais são informação.
    val cor = if (texto == CASAMENTO_AUTOMATICO) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(texto, style = MaterialTheme.typography.labelMedium, color = cor)
}

/** P8: a mensagem da API, e o que já estava na tela continua ali. */
@Composable
private fun ErroDaAnalise(estado: EstadoDoPainel) {
    estado.erroDaAnalise?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

/** O lote de "Confirmar todos": o progresso enquanto roda e, depois, o resumo do que foi feito (L5), que se dispensa. */
@Composable
private fun ResultadoDoLote(estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    if (estado.executandoLote) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Confirmando… uma chamada por vez.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    estado.resultadoDoLote?.let { resumo ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(resumo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = acoes.aoDispensarResultadoDoLote) {
                    Icon(Icons.Filled.Close, contentDescription = "Dispensar o resumo")
                }
            }
        }
    }
}

/** P6/P10: enquanto a IA roda — o que pode levar mais de um minuto. */
@Composable
private fun AnaliseEmAndamento(estado: EstadoDoPainel) {
    if (estado.analisando) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Analisando… pode levar até um minuto.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** "Gerar prompt" (G3): diz que **gasta IA** e oferece o campo opcional de ajuste. */
@Composable
private fun DialogoGerarPrompt(frameId: Int, acoes: AcoesDoPainel) {
    var ajuste by rememberSaveable(frameId) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = acoes.aoCancelarGerarPrompt,
        title = { Text("Gerar o prompt?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(TEXTO_DO_DIALOGO_DE_PROMPT)
                OutlinedTextField(
                    value = ajuste,
                    onValueChange = { ajuste = it.take(LIMITE_DO_AJUSTE_DO_PROMPT) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ajuste (opcional)") },
                    placeholder = { Text("Ex.: ela deve estar de costas, com o manto azul") },
                    supportingText = { Text("${ajuste.length}/$LIMITE_DO_AJUSTE_DO_PROMPT") },
                    minLines = 2,
                    maxLines = 5,
                )
            }
        },
        confirmButton = { TextButton(onClick = { acoes.aoGerarPrompt(frameId, ajuste) }) { Text("Gerar") } },
        dismissButton = { TextButton(onClick = acoes.aoCancelarGerarPrompt) { Text("Cancelar") } },
    )
}

/** "Confirmar todos" (L2): diz o que vai acontecer **e o que não vai**, antes de agir. */
@Composable
private fun DialogoConfirmarTodos(resumo: ResumoDoLote, acoes: AcoesDoPainel) {
    AlertDialog(
        onDismissRequest = acoes.aoCancelarConfirmarTodos,
        title = { Text("Confirmar todos?") },
        text = { Text(descreverLoteParaConfirmar(resumo)) },
        confirmButton = { TextButton(onClick = acoes.aoConfirmarTodos) { Text("Confirmar todos") } },
        dismissButton = { TextButton(onClick = acoes.aoCancelarConfirmarTodos) { Text("Cancelar") } },
    )
}

/** P7: reanalisar gasta IA, então pede confirmação — e repete o aviso das pendências (P11). */
@Composable
private fun DialogoDeReanalise(estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val sugestoes = (estado.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes
    val pendentes = sugestoes?.sugestoes_pendentes_anteriores?.let { descreverPendentesAnteriores(it) }
    // O campo já vem com a orientação que vale no capítulo (item 6.7, M1): dá para ajustá-la ou apagá-la.
    var orientacao by rememberSaveable { mutableStateOf(sugestoes?.orientacao.orEmpty()) }

    AlertDialog(
        onDismissRequest = acoes.aoCancelarReanalise,
        title = { Text("Reanalisar o capítulo?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Isso refaz as sugestões ainda não confirmadas e gasta IA. " +
                        "As já confirmadas e as descartadas ficam.",
                )
                pendentes?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                OutlinedTextField(
                    value = orientacao,
                    onValueChange = { orientacao = it.take(LIMITE_DA_ORIENTACAO) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("O que a análise não pegou? (opcional)") },
                    placeholder = { Text("Ex.: falta a cena em que o personagem chega ao porto") },
                    supportingText = {
                        Text(
                            "A IA trata isto como palpite: o que o capítulo não traz, ela ignora. " +
                                "Fica guardado para as próximas reanálises; apague para tirar. " +
                                "${orientacao.length}/$LIMITE_DA_ORIENTACAO",
                        )
                    },
                    minLines = 2,
                    maxLines = 5,
                )
            }
        },
        confirmButton = { TextButton(onClick = { acoes.aoConfirmarReanalise(orientacao) }) { Text("Reanalisar") } },
        dismissButton = { TextButton(onClick = acoes.aoCancelarReanalise) { Text("Cancelar") } },
    )
}
