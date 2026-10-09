package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.allan.imagineer.rede.TIPOS_DE_PRESENTE
import com.allan.imagineer.rede.rotuloDoTipoDePresente

// A lista "O que vai aparecer" (item 4.9, FL9; especificação 7.5d, AP1 a AP4) e os diálogos dela e da conferência da imagem (AP7).

/**
 * O cartão "O que vai aparecer" de uma **cena**: recolhido por padrão (a pessoa não é obrigada a olhar, FL9), com uma linha que diz
 * em que pé a lista está. Aberto, mostra os itens (caixa, nome, características), o lugar, a luz e a ação, e as ações: confirmar,
 * acrescentar, ler de novo (que gasta IA). Ler a lista guardada não gasta (AP2).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CartaoDoQueVaiAparecer(frameId: Int, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    var aberto by rememberSaveable(frameId) { mutableStateOf(false) }
    // A lista guardada vem uma vez, ao abrir o cartão (é só o GET).
    LaunchedEffect(frameId, aberto) { if (aberto) acoes.aoCarregarDossie(frameId) }
    val dossie = estado.dossies[frameId]
    val pronto = dossie as? DossieDoFrame.Pronto
    val resumo = when (dossie) {
        null -> null
        DossieDoFrame.Lendo -> "lendo…"
        is DossieDoFrame.Erro -> "não consegui ler"
        is DossieDoFrame.Pronto -> resumoDoDossie(dossie.guardado, dossie.rascunho != null, dossie.rascunho)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("O que vai aparecer", style = MaterialTheme.typography.titleSmall)
                    resumo?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                TextButton(onClick = { aberto = !aberto }) { Text(if (aberto) "Recolher" else "Ver e ajustar", maxLines = 1, softWrap = false) }
            }
            if (!aberto) return@Column
            when (dossie) {
                null, DossieDoFrame.Lendo -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                is DossieDoFrame.Erro -> {
                    Text(dossie.motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { acoes.aoRecarregarDossie(frameId) }) { Text("Tentar de novo") }
                }
                is DossieDoFrame.Pronto -> CorpoDoQueVaiAparecer(frameId, dossie, acoes)
            }
            // O recado (erro ou aviso do que acabou de acontecer) fica à vista mesmo com o cartão em edição.
            pronto?.recado?.let { RecadoDaCena(it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CorpoDoQueVaiAparecer(frameId: Int, dossie: DossieDoFrame.Pronto, acoes: AcoesDoPainel) {
    val ocupado = dossie.lendoDeNovo || dossie.gravando
    val rascunho = dossie.rascunho ?: rascunhoDe(dossie.guardado)
    val guardado = dossie.guardado

    if (guardado == null) Text(AVISO_SEM_DOSSIE, style = MaterialTheme.typography.bodySmall)
    if (guardado?.momento_incerto == true) Text(AVISO_MOMENTO_INCERTO, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    if (guardado?.desatualizado == true) Text(AVISO_DOSSIE_DESATUALIZADO, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    avisosDoQueFaltou(guardado).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (dossie.lendoDeNovo) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text("Lendo o capítulo inteiro…", style = MaterialTheme.typography.bodySmall)
    }

    rascunho.presentes.forEachIndexed { indice, presente ->
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = presente.incluir, onCheckedChange = { acoes.aoAlternarPresente(frameId, indice) }, enabled = !ocupado)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val partes = listOfNotNull(
                    rotuloDoTipoDePresente(presente.tipo),
                    presente.elemento?.let { "cadastrado: $it" },
                    if (presente.incerto) "incerto" else null,
                )
                Text(presente.nome, style = MaterialTheme.typography.bodyMedium)
                Text(
                    partes.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (presente.incerto) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = presente.caracteristicas,
                    onValueChange = { acoes.aoMudarCaracteristicas(frameId, indice, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("O que se vê") },
                    enabled = !ocupado && presente.incluir,
                    minLines = 1,
                    maxLines = 5,
                )
            }
        }
    }
    if (rascunho.presentes.isEmpty() && guardado != null) {
        Text("A leitura não achou ninguém nem nada na cena. Acrescente o que deve aparecer.", style = MaterialTheme.typography.bodySmall)
    }

    CampoDoDossieNaTela("Lugar", rascunho.onde, ocupado) { acoes.aoMudarCampoDoDossie(frameId, CampoDoDossie.ONDE, it) }
    CampoDoDossieNaTela("Luz e clima", rascunho.luzEClima, ocupado) { acoes.aoMudarCampoDoDossie(frameId, CampoDoDossie.LUZ_E_CLIMA, it) }
    CampoDoDossieNaTela("Ação (um instante parado)", rascunho.acao, ocupado) { acoes.aoMudarCampoDoDossie(frameId, CampoDoDossie.ACAO, it) }

    if (dossie.rascunho != null) Text(AVISO_ALTERACOES_NAO_CONFIRMADAS, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { acoes.aoConfirmarDossie(frameId) }, enabled = !ocupado) {
            Text(if (guardado?.confirmado == true && dossie.rascunho == null) "Confirmada" else "Confirmar a lista", maxLines = 1, softWrap = false)
        }
        OutlinedButton(onClick = { acoes.aoPedirAcrescentarAoDossie(frameId) }, enabled = !ocupado) { Text("Acrescentar item", maxLines = 1, softWrap = false) }
        if (dossie.rascunho != null) {
            TextButton(onClick = { acoes.aoDescartarRascunhoDoDossie(frameId) }, enabled = !ocupado) { Text("Descartar alterações", maxLines = 1, softWrap = false) }
        }
        OutlinedButton(onClick = { acoes.aoPedirLerDossie(frameId) }, enabled = !ocupado) {
            Text(if (guardado == null) "Ler a cena" else "Ler de novo", maxLines = 1, softWrap = false)
        }
    }
    Text(
        "Confirmar não gasta nada. Ler a cena gasta IA: o servidor lê o capítulo inteiro.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CampoDoDossieNaTela(rotulo: String, valor: String, ocupado: Boolean, aoMudar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(rotulo) },
        enabled = !ocupado,
        minLines = 1,
        maxLines = 4,
    )
}

/** Os diálogos da lista e da conferência: desenhados uma vez só, junto dos outros diálogos do painel. */
@Composable
internal fun DialogosDoDossie(estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    estado.confirmandoLeituraDoDossie?.let { DialogoLerDossie(it, estado, acoes) }
    estado.acrescentandoAoDossie?.let { DialogoAcrescentarAoDossie(it, acoes) }
    estado.conferencia?.let { DialogoDaConferencia(it, acoes) }
}

@Composable
private fun DialogoLerDossie(frameId: Int, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val jaLida = ((estado.dossies[frameId] as? DossieDoFrame.Pronto)?.guardado) != null
    AlertDialog(
        onDismissRequest = acoes.aoCancelarLerDossie,
        title = { Text(if (jaLida) "Ler a cena de novo?" else "Ler a cena?") },
        text = {
            Text(
                "Isso gasta IA: o servidor lê o capítulo inteiro e refaz a lista do que vai aparecer." +
                    if (jaLida) " O que você editou ou confirmou será descartado." else "",
            )
        },
        confirmButton = { TextButton(onClick = { acoes.aoLerDossie(frameId) }) { Text("Ler") } },
        dismissButton = { TextButton(onClick = acoes.aoCancelarLerDossie) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoAcrescentarAoDossie(frameId: Int, acoes: AcoesDoPainel) {
    var nome by rememberSaveable(frameId) { mutableStateOf("") }
    var tipo by rememberSaveable(frameId) { mutableStateOf("OBJETO") }
    var caracteristicas by rememberSaveable(frameId) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = acoes.aoCancelarAcrescentarAoDossie,
        title = { Text("Acrescentar à cena") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = nome, onValueChange = { nome = it.take(200) }, modifier = Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIPOS_DE_PRESENTE.forEach { opcao ->
                        FilterChip(selected = tipo == opcao, onClick = { tipo = opcao }, label = { Text(rotuloDoTipoDePresente(opcao)) })
                    }
                }
                OutlinedTextField(
                    value = caracteristicas,
                    onValueChange = { caracteristicas = it.take(2000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("O que se vê") },
                    minLines = 2,
                    maxLines = 5,
                )
            }
        },
        confirmButton = { TextButton(onClick = { acoes.aoAcrescentarAoDossie(frameId, nome, tipo, caracteristicas) }, enabled = nome.isNotBlank()) { Text("Acrescentar") } },
        dismissButton = { TextButton(onClick = acoes.aoCancelarAcrescentarAoDossie) { Text("Cancelar") } },
    )
}

/** Pede confirmação (gasta IA), espera, e mostra a opinião do modelo com visão (AP7). Não muda nada. */
@Composable
private fun DialogoDaConferencia(conferencia: ConferenciaEmCurso, acoes: AcoesDoPainel) {
    when (conferencia) {
        is ConferenciaEmCurso.Confirmando -> AlertDialog(
            onDismissRequest = acoes.aoFecharConferencia,
            title = { Text("Conferir com a lista?") },
            text = { Text("Isso gasta IA: um modelo que enxerga olha a imagem e compara com a lista do que deveria aparecer. É só uma opinião; nada é alterado.") },
            confirmButton = { TextButton(onClick = acoes.aoConferirImagem) { Text("Conferir") } },
            dismissButton = { TextButton(onClick = acoes.aoFecharConferencia) { Text("Cancelar") } },
        )
        is ConferenciaEmCurso.Rodando -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Conferindo…") },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
        is ConferenciaEmCurso.Falhou -> AlertDialog(
            onDismissRequest = acoes.aoFecharConferencia,
            title = { Text("Não consegui conferir") },
            text = { Text(conferencia.motivo) },
            confirmButton = { TextButton(onClick = acoes.aoFecharConferencia) { Text("Fechar") } },
        )
        is ConferenciaEmCurso.Pronta -> AlertDialog(
            onDismissRequest = acoes.aoFecharConferencia,
            title = { Text(if (conferencia.resultado.conforme) "Tudo bate com a lista" else "A imagem diverge da lista") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    conferencia.resultado.divergencias.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    val custo = custoDaChamada(conferencia.resultado.custo)
                    Text(
                        "${conferencia.resultado.itens_conferidos} item(ns) conferido(s) · ${conferencia.resultado.modelo}" + (custo?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = acoes.aoFecharConferencia) { Text("Fechar") } },
        )
    }
}
