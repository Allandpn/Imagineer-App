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
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.fillMaxSize()) {
            CabecalhoDoPainel(aoFechar)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CorpoDoPainel(estado, acoes)
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
fun ModaisDoPainel(estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    estado.modais.forEach { modal ->
        key(modal) {
            when (modal) {
                is ModalAberto.DeElemento -> ModalDaSugestao(estado, acoes, modal.sugestaoId)
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
fun ModalDaSugestao(estado: EstadoDoPainel, acoes: AcoesDoPainel, id: Int) {
    val sugestoes = (estado.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes
    val elemento = sugestoes?.elementos?.firstOrNull { it.id == id }

    ModalBottomSheet(
        onDismissRequest = acoes.aoFecharModal,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
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
                else -> CartaoDeElemento(
                    elemento = elemento,
                    cenas = cenasDoElemento(sugestoes)[elemento.id].orEmpty(),
                    aberto = true,
                    aoAlternar = {},
                    estado = estado,
                    acoes = acoes,
                )
            }
        }
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
private fun CorpoDoPainel(estado: EstadoDoPainel, acoes: AcoesDoPainel) {
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

        is ConteudoDoPainel.Pronto -> ListaDeSugestoes(conteudo.sugestoes, estado, acoes)
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
private fun ListaDeSugestoes(sugestoes: SugestoesDeCapitulo, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
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
                    )
                }
            }
        }

        if (cenas.isNotEmpty()) {
            item { Text("Cenas (${cenas.size})", style = MaterialTheme.typography.titleSmall) }
            items(cenas, key = { "c${it.id}" }) { cena ->
                CartaoDeCena(cena, estado, acoes)
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
 * O cartão da cena (C9): título, quantos participantes e **uma etiqueta de situação**. Tocar abre o **modal da cena**
 * (C1); o cartão deixa de se expandir no lugar, porque o modal mostra os detalhes.
 */
@Composable
internal fun CartaoDeCena(cena: CenaSugerida, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val ocupada = cena.id in estado.cenasOcupadas
    Card(onClick = { acoes.aoAbrirCena(cena.id) }, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        maxLines = 1,
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
            estado.mensagensDeCena[cena.id]?.let { RecadoDaCena(it) }
            if (ocupada) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
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
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
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
    val ocupada = cena.id in estado.cenasOcupadas
    val sugestoes = (estado.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(cena.titulo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        EtiquetaDaCena(etiquetaDaCena(cena), filtroDaCena(cena))
    }
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
