package com.allan.imagineer.telas.capitulo.painel

import android.content.ClipboardManager
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.dp

/**
 * Dá à **barra de seleção de texto** do capítulo uma ação a mais, **"Gerar imagem deste trecho"** (TR1), mantendo **Copiar** e
 * **Selecionar tudo**: a seleção de texto continua sempre disponível (os dicionários do futuro dependem dela). A ação copia o trecho
 * selecionado, lê da área de transferência e o entrega a [aoGerarDoTrecho]; a barra normal do Android vai junto, só que com um item a mais.
 */
@Composable
internal fun ComAcaoDeGerarImagemDoTrecho(aoGerarDoTrecho: (String) -> Unit, conteudo: @Composable () -> Unit) {
    val visao = LocalView.current
    val contexto = LocalContext.current
    val barra = remember(visao, contexto) { BarraDeSelecaoComTrecho(visao, contexto, aoGerarDoTrecho) }
    barra.aoGerarDoTrecho = aoGerarDoTrecho
    CompositionLocalProvider(LocalTextToolbar provides barra) { conteudo() }
}

/** A barra flutuante da seleção (um `ActionMode`) com Copiar, Selecionar tudo e **Gerar imagem deste trecho** (TR1). */
private class BarraDeSelecaoComTrecho(
    private val visao: View,
    private val contexto: Context,
    var aoGerarDoTrecho: (String) -> Unit,
) : TextToolbar {
    private var modo: ActionMode? = null
    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        modo?.finish()
        val retangulo = android.graphics.Rect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        val chamadas = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                if (onCopyRequested != null) menu.add(Menu.NONE, ITEM_COPIAR, 0, android.R.string.copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                if (onSelectAllRequested != null) menu.add(Menu.NONE, ITEM_TODOS, 1, android.R.string.selectAll).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                if (onCopyRequested != null) menu.add(Menu.NONE, ITEM_GERAR, 2, ROTULO_GERAR_IMAGEM_DO_TRECHO).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    ITEM_COPIAR -> onCopyRequested?.invoke()
                    ITEM_TODOS -> onSelectAllRequested?.invoke()
                    ITEM_GERAR -> {
                        onCopyRequested?.invoke() // copia o trecho; a seleção é a fonte
                        val area = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val trecho = area?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
                        if (trecho.isNotBlank()) aoGerarDoTrecho(trecho)
                    }
                    else -> return false
                }
                mode.finish()
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                modo = null
                status = TextToolbarStatus.Hidden
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
                outRect.set(retangulo)
            }
        }
        modo = visao.startActionMode(chamadas, ActionMode.TYPE_FLOATING)
        status = if (modo != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
    }

    override fun hide() {
        modo?.finish()
        modo = null
        status = TextToolbarStatus.Hidden
    }

    private companion object {
        const val ITEM_COPIAR = 1
        const val ITEM_TODOS = 2
        const val ITEM_GERAR = 3
    }
}

/**
 * O diálogo do **trecho selecionado** (TR2): o trecho, o campo **"O que você quer ver"** e os **elementos confirmados do capítulo** para a
 * cena levar (já marcados os citados no trecho). **Criar a cena não gasta IA** (TR5); a análise e o prompt vêm depois, no modal da cena.
 */
@Composable
internal fun DialogoDoTrecho(trecho: TrechoParaImagem, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val opcoes = opcoesDeElementosDoTrecho((estado.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes?.elementos.orEmpty())
    AlertDialog(
        onDismissRequest = acoes.aoFecharTrecho,
        title = { Text("Gerar imagem deste trecho") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "«${trecho.trecho.take(400)}${if (trecho.trecho.length > 400) "…" else ""}»",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (trecho.posicao == null) {
                    Text(
                        "A seleção passa de um parágrafo: a cena ficará na faixa \"Sem posição no texto\" (você pode posicioná-la depois).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                OutlinedTextField(
                    value = trecho.descricao,
                    onValueChange = acoes.aoAlterarDescricaoDoTrecho,
                    label = { Text("O que você quer ver (opcional)") },
                    minLines = 2,
                    enabled = !trecho.criando,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Elementos na cena", style = MaterialTheme.typography.titleSmall)
                if (opcoes.isEmpty()) {
                    Text(
                        "Ainda não há elementos confirmados neste capítulo. A cena pode ser criada sem eles.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                opcoes.forEach { opcao ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !trecho.criando) { acoes.aoAlternarElementoDoTrecho(opcao.estadoId) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = opcao.estadoId in trecho.estadosEscolhidos, onCheckedChange = null)
                        Text(opcao.nome, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "Criar a cena não gasta IA. Depois, \"Gerar imagem\" lê o capítulo para entender o contexto (análise barata) e o modelo de prompt escreve o prompt (gasta IA).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                trecho.erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (trecho.criando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = acoes.aoCriarCenaDoTrecho, enabled = !trecho.criando) { Text(if (trecho.criando) "Criando…" else "Criar a cena") }
        },
        dismissButton = { TextButton(onClick = acoes.aoFecharTrecho, enabled = !trecho.criando) { Text("Cancelar") } },
    )
}

/**
 * O modal de um **frame sem sugestão** (a cena de um trecho, TR4): o título e a mesma área de prompts e imagens da cena (G1 a G13,
 * Q1 a Q7): **Gerar imagem**, **Só o prompt**, importar, referências. Fechar volta ao texto onde estava.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModalDoFrame(estado: EstadoDoPainel, acoes: AcoesDoPainel, frameId: Int, rotulo: String) {
    ModalBottomSheet(
        onDismissRequest = acoes.aoFecharModalDoFrame,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(rotulo, style = MaterialTheme.typography.titleLarge)
            BlocoDePrompts(
                frameId, rotulo, estado, acoes, chaveDoFluxoDoFrame(frameId), "Gerar imagem", ehCena = true,
                aoEscolherElementos = { acoes.aoAbrirSeletorDaCena(frameId) },
            )
        }
    }
}
