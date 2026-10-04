package com.allan.imagineer.telas.capitulo.painel

import android.os.PersistableBundle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.Icons
import com.allan.imagineer.telas.comum.BotaoDeIcone
import androidx.compose.foundation.layout.FlowRow
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/**
 * Dá ao **menu da seleção de texto** do capítulo um item a mais, **"Gerar imagem deste trecho"** (TR1), junto de Copiar e Selecionar
 * tudo: a seleção continua intacta (os dicionários do futuro dependem dela). O Compose 1.10 usa o menu de texto **novo**
 * (`appendTextContextMenuComponents`), que **não entrega o texto selecionado** ao item; por isso a ação aciona o atalho **Copiar** da
 * própria seleção (Ctrl+C, que o `SelectionContainer` já trata), lê o trecho da área de transferência e o entrega a [aoGerarDoTrecho].
 * Efeito colateral: o trecho fica copiado.
 */
@Composable
internal fun ComAcaoDeGerarImagemDoTrecho(aoGerarDoTrecho: (String) -> Unit, aoDestacarTrecho: (String) -> Unit = {}, aoConsultarDicionario: (String) -> Unit = {}, aoMarcarParagrafo: (String) -> Unit = {}, conteudo: @Composable () -> Unit) {
    val visao = LocalView.current
    val contexto = LocalContext.current
    val aoGerar by rememberUpdatedState(aoGerarDoTrecho)
    val aoDestacar by rememberUpdatedState(aoDestacarTrecho)
    val aoConsultar by rememberUpdatedState(aoConsultarDicionario)
    val aoMarcar by rememberUpdatedState(aoMarcarParagrafo)
    Box(
        modifier = Modifier.appendTextContextMenuComponents {
            separator()
            item(key = ChaveDeGerarImagemDoTrecho, label = ROTULO_GERAR_IMAGEM_DO_TRECHO) {
                close()
                copiarSelecaoE(visao, contexto) { trecho -> aoGerar(trecho) }
            }
            // RL9: o mesmo caminho (copiar e ler o trecho), agora para grifar o trecho no texto.
            item(key = ChaveDeDestacarTrecho, label = ROTULO_DESTACAR_TRECHO) {
                close()
                copiarSelecaoE(visao, contexto) { trecho -> aoDestacar(trecho) }
            }
            // RL20: a palavra selecionada vai ao dicionário.
            item(key = ChaveDeConsultarDicionario, label = ROTULO_DICIONARIO) {
                close()
                copiarSelecaoE(visao, contexto) { trecho -> aoConsultar(trecho) }
            }
            // LV4: liga o modo de marcar parágrafos (copiar vários, gerar imagem de um trecho maior) a partir da seleção.
            item(key = ChaveDeMarcarParagrafo, label = ROTULO_MARCAR_PARAGRAFO) {
                close()
                copiarSelecaoE(visao, contexto) { trecho -> aoMarcar(trecho) }
            }
        },
    ) {
        // Copiar aqui é só o meio de pegar o trecho: marcado como sensível, o Android não mostra o aviso de "copiado" (nem o do Samsung,
        // "copiado para dispositivos conectados"). O texto continua na área de transferência normalmente.
        val real = LocalClipboard.current
        val silenciosa = remember(real) { AreaDeTransferenciaSemAviso(real) }
        CompositionLocalProvider(LocalClipboard provides silenciosa) { conteudo() }
    }
}

/** Uma área de transferência que grava o que o app copia **marcado como sensível** (`IS_SENSITIVE`, Android 13+; antes disso é ignorado). */
private class AreaDeTransferenciaSemAviso(private val real: Clipboard) : Clipboard by real {
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        clipEntry?.clipData?.description?.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        real.setClipEntry(clipEntry)
    }
}

private object ChaveDeGerarImagemDoTrecho

private object ChaveDeDestacarTrecho

private object ChaveDeConsultarDicionario

private object ChaveDeMarcarParagrafo

/** O item do menu da seleção que entra no modo de marcar parágrafos (LV4). */
const val ROTULO_MARCAR_PARAGRAFO = "Marcar parágrafo"

/** O item do menu da seleção que procura a palavra no dicionário (RL20). */
const val ROTULO_DICIONARIO = "Dicionário"

/** O item do menu da seleção que grifa o trecho (RL9). */
const val ROTULO_DESTACAR_TRECHO = "Destacar"

/** Aciona o Copiar da seleção atual e entrega o texto copiado a [aoTerOTrecho]; sem texto, avisa o que fazer. */
private fun copiarSelecaoE(visao: View, contexto: Context, aoTerOTrecho: (String) -> Unit) {
    val agora = android.os.SystemClock.uptimeMillis()
    val ctrl = KeyEvent.META_CTRL_ON
    visao.dispatchKeyEvent(KeyEvent(agora, agora, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0, ctrl))
    visao.dispatchKeyEvent(KeyEvent(agora, agora, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C, 0, ctrl))
    // A cópia é feita na hora; o post só dá a vez ao sistema antes de ler a área de transferência.
    visao.post {
        val area = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val trecho = area?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
        if (trecho.isNotBlank()) {
            aoTerOTrecho(trecho)
        } else {
            Toast.makeText(contexto, AVISO_TRECHO_NAO_COPIADO, Toast.LENGTH_LONG).show()
        }
    }
}

/** O aviso quando a ação não conseguiu pegar o trecho selecionado: o caminho manual (Copiar) existe sempre. */
const val AVISO_TRECHO_NAO_COPIADO = "Não consegui pegar o trecho. Toque em Copiar e depois em Gerar imagem deste trecho de novo."

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
                val dados = (trecho.candidatos as? CandidatosDoSeletor.Prontos)?.dados
                if (dados == null) {
                    // O seletor completo ainda não chegou (ou falhou): enquanto isso, os elementos confirmados do capítulo.
                    if (trecho.candidatos is CandidatosDoSeletor.Erro) {
                        Text("Não consegui ler os outros elementos: ${trecho.candidatos.motivo}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (opcoes.isEmpty()) {
                        Text(
                            "Ainda não há elementos confirmados neste capítulo. A cena pode ser criada sem eles.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    opcoes.forEach { opcao -> LinhaDeElementoDoTrecho(opcao.estadoId, opcao.nome, "", trecho, acoes) }
                } else {
                    // LV8: o mesmo seletor de qualquer cena: os identificados, os outros do capítulo e os de outros capítulos.
                    SecaoDeElementosDoTrecho("Identificados neste capítulo", dados.identificados, trecho, acoes)
                    SecaoDeElementosDoTrecho("Outros deste capítulo", dados.outros, trecho, acoes)
                    SecaoDeElementosDoTrecho("De outros capítulos", dados.de_outros_capitulos, trecho, acoes, recolhida = true)
                    if (dados.identificados.isEmpty() && dados.outros.isEmpty() && dados.de_outros_capitulos.isEmpty()) {
                        Text("Ainda não há elementos neste livro. A cena pode ser criada sem eles.", style = MaterialTheme.typography.bodySmall)
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

/** Uma seção do seletor de elementos da cena do trecho; a de outros capítulos começa **recolhida** (a lista pode ser longa). */
@Composable
private fun SecaoDeElementosDoTrecho(
    titulo: String,
    elementos: List<com.allan.imagineer.rede.ElementoParaVincular>,
    trecho: TrechoParaImagem,
    acoes: AcoesDoPainel,
    recolhida: Boolean = false,
) {
    if (elementos.isEmpty()) return
    var aberta by remember { mutableStateOf(!recolhida) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { aberta = !aberta },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$titulo (${elementos.size})", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(if (aberta) "▾" else "▸", style = MaterialTheme.typography.labelLarge)
    }
    if (aberta) elementos.forEach { LinhaDeElementoDoTrecho(it.estado_id, it.nome, rotuloDoTipo(it.tipo), trecho, acoes) }
}

@Composable
private fun LinhaDeElementoDoTrecho(estadoId: Int, nome: String, tipo: String, trecho: TrechoParaImagem, acoes: AcoesDoPainel) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !trecho.criando) { acoes.aoAlternarElementoDoTrecho(estadoId) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = estadoId in trecho.estadosEscolhidos, onCheckedChange = null)
        Text(
            if (tipo.isBlank()) nome else "$nome · $tipo",
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * O modal de um **frame sem sugestão** (a cena de um trecho, TR4): o título e a mesma área de prompts e imagens da cena (G1 a G13,
 * Q1 a Q7): **Gerar imagem**, **Só o prompt**, importar, referências. Fechar volta ao texto onde estava.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
            // PM3: reposicionar, tirar a posição; editar e apagar a cena (só frames sem sugestão chegam a este modal). Quebra de linha, não corte.
            FlowRow {
                BotaoDeIcone(Icons.Filled.Place, ROTULO_POSICIONAR, { acoes.aoIniciarPosicionamentoDeFrame(true, frameId, rotulo) })
                BotaoDeIcone(Icons.Filled.LocationOff, ROTULO_TIRAR_POSICAO, { acoes.aoTirarPosicao(true, null, frameId) })
                BotaoDeIcone(Icons.Filled.Edit, ROTULO_EDITAR_A_CENA, { acoes.aoAbrirEdicaoDeFrame(frameId, rotulo) })
                BotaoDeIcone(Icons.Filled.Delete, ROTULO_APAGAR_A_CENA, { acoes.aoPedirApagarFrame(frameId, rotulo, false) }, cor = MaterialTheme.colorScheme.error)
            }
            estado.mensagensDePrompt[frameId]?.takeIf { it.ehErro }?.let { Text(it.texto, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            BlocoDePrompts(
                frameId, rotulo, estado, acoes, chaveDoFluxoDoFrame(frameId), "Gerar imagem", ehCena = true,
                aoEscolherElementos = { acoes.aoAbrirSeletorDaCena(frameId) },
            )
        }
    }
}

/** Editar o título e a descrição de uma cena (LV6): serve a toda cena (sugerida, confirmada ou criada de um trecho). */
@Composable
internal fun DialogoDeEdicaoDaCena(alvo: EdicaoDaCena, acoes: AcoesDoPainel) {
    // Os campos recomeçam quando o texto do frame termina de chegar (carregando passa de true para false).
    var titulo by remember(alvo.sugestaoId, alvo.frameId, alvo.carregando) { mutableStateOf(alvo.titulo) }
    var descricao by remember(alvo.sugestaoId, alvo.frameId, alvo.carregando) { mutableStateOf(alvo.descricao) }
    AlertDialog(
        onDismissRequest = acoes.aoFecharEdicaoDaCena,
        title = { Text(ROTULO_EDITAR_A_CENA) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text("Título") },
                    singleLine = true,
                    enabled = !alvo.carregando && !alvo.salvando,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it },
                    label = { Text("Descrição") },
                    minLines = 3,
                    maxLines = 8,
                    enabled = !alvo.carregando && !alvo.salvando,
                    modifier = Modifier.fillMaxWidth(),
                )
                alvo.erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (alvo.carregando || alvo.salvando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { acoes.aoSalvarEdicaoDaCena(titulo, descricao) }, enabled = !alvo.carregando && !alvo.salvando) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = acoes.aoFecharEdicaoDaCena, enabled = !alvo.salvando) { Text("Cancelar") } },
    )
}

/**
 * "Apagar esta cena?": o frame **e os prompts e as imagens dele** somem, **sem volta** (a lixeira ainda é só de imagens avulsas).
 * A recusa do servidor fica no próprio diálogo.
 */
@Composable
internal fun DialogoApagarFrame(alvo: ApagandoFrame, acoes: AcoesDoPainel) {
    AlertDialog(
        onDismissRequest = acoes.aoCancelarApagarFrame,
        title = { Text("Mover a cena para a lixeira?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (alvo.deSugestao) {
                        "«${alvo.rotulo}» vai para a lixeira com os prompts e as imagens; dá para restaurar. A cena continua na lista, como pendente."
                    } else {
                        "«${alvo.rotulo}» vai para a lixeira com os prompts e as imagens dela; dá para restaurar."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                alvo.erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (alvo.apagando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = acoes.aoConfirmarApagarFrame, enabled = !alvo.apagando) { Text("Mover", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = acoes.aoCancelarApagarFrame, enabled = !alvo.apagando) { Text("Cancelar") } },
    )
}
