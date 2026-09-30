package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.SugestoesDeCapitulo

// O desenho visual do painel é provisório: este incremento trata das REGRAS (item 7.5b, P1 a
// P15), e o visual é refinado depois.

/** As ações do painel, agrupadas para a tela de Capítulo não carregar uma lista de parâmetros. */
class AcoesDoPainel(
    val aoAnalisar: () -> Unit,
    val aoPedirReanalise: () -> Unit,
    val aoConfirmarReanalise: () -> Unit,
    val aoCancelarReanalise: () -> Unit,
    val aoTentarDeNovo: () -> Unit,
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

    if (estado.confirmandoReanalise) {
        DialogoDeReanalise(estado, acoes)
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

@Composable
private fun ListaDeSugestoes(sugestoes: SugestoesDeCapitulo, estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Sugestões da IA", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = acoes.aoPedirReanalise, enabled = !estado.analisando) {
                    Text("Reanalisar")
                }
            }
        }
        item { ErroDaAnalise(estado) }
        item { AnaliseEmAndamento(estado) }

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

        if (sugestoes.elementos.isNotEmpty()) {
            item { Text("Elementos (${sugestoes.elementos.size})", style = MaterialTheme.typography.titleSmall) }
            items(sugestoes.elementos, key = { "e${it.id}" }) { CartaoDeElemento(it) }
        }
        if (sugestoes.cenas.isNotEmpty()) {
            item { Text("Cenas (${sugestoes.cenas.size})", style = MaterialTheme.typography.titleSmall) }
            items(sugestoes.cenas, key = { "c${it.id}" }) { CartaoDeCena(it) }
        }
    }
}

/** P12: tipo, nome, identidade e os destaques, na ordem de importância. Somente leitura (P15). */
@Composable
private fun CartaoDeElemento(elemento: ElementoSugerido) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                rotuloDoTipo(elemento.tipo),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(elemento.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            elemento.descricao?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            destaquesDoElemento(elemento).forEach { Destaque(it) }
        }
    }
}

/** P13: título, descrição e, só quando existem, horário, clima e humor; e os participantes. */
@Composable
private fun CartaoDeCena(cena: CenaSugerida) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(cena.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            cena.descricao?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            val situacao = listOfNotNull(cena.horario, cena.clima, cena.humor)
            if (situacao.isNotEmpty()) {
                Text(
                    situacao.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (cena.participantes.isNotEmpty()) {
                Text("Participantes", style = MaterialTheme.typography.labelMedium)
                cena.participantes.forEach { participante ->
                    Text(
                        "${rotuloDoTipo(participante.tipo)}: ${participante.nome}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    destaqueDoParticipante(participante)?.let { Destaque(it) }
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

/** P7: reanalisar gasta IA, então pede confirmação — e repete o aviso das pendências (P11). */
@Composable
private fun DialogoDeReanalise(estado: EstadoDoPainel, acoes: AcoesDoPainel) {
    val pendentes = (estado.conteudo as? ConteudoDoPainel.Pronto)
        ?.sugestoes?.sugestoes_pendentes_anteriores
        ?.let { descreverPendentesAnteriores(it) }

    AlertDialog(
        onDismissRequest = acoes.aoCancelarReanalise,
        title = { Text("Reanalisar o capítulo?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Isso refaz as sugestões ainda não confirmadas e gasta IA. " +
                        "As já confirmadas ficam.",
                )
                pendentes?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
            }
        },
        confirmButton = { TextButton(onClick = acoes.aoConfirmarReanalise) { Text("Reanalisar") } },
        dismissButton = { TextButton(onClick = acoes.aoCancelarReanalise) { Text("Cancelar") } },
    )
}
