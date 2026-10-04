package com.allan.imagineer.telas.capitulo.painel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.allan.imagineer.rede.ElementoDoCapitulo
import com.allan.imagineer.rede.enderecoDaImagem

/**
 * A lista **por capítulo** do seletor único de vínculo (VM1, VM2): para cada [secoes] o título do capítulo e, embaixo, os elementos com
 * as **miniaturas das imagens** que têm ali. **Tocar numa miniatura** escolhe aquela imagem (e o elemento); o botão do elemento
 * ([rotuloDoElemento], se houver) escolhe só o elemento. Quem decide o que fazer é [aoEscolher]; aqui só se mostra e se toca.
 */
@Composable
internal fun ListaPorCapitulo(
    secoes: List<SecaoPorCapitulo>,
    desativado: Boolean,
    rotuloDoElemento: String?,
    aoEscolher: (elementoId: Int, imagemId: Int?) -> Unit,
    aoVerFicha: ((elementoId: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        secoes.forEach { secao ->
            item(key = "capitulo-${secao.titulo}") {
                Text(
                    secao.titulo,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (secao.ehAtual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            items(secao.elementos, key = { "${secao.titulo}-${it.elemento_id}" }) { elemento ->
                LinhaDoElementoPorCapitulo(elemento, desativado, rotuloDoElemento, aoEscolher, aoVerFicha)
            }
        }
    }
}

@Composable
private fun LinhaDoElementoPorCapitulo(
    elemento: ElementoDoCapitulo,
    desativado: Boolean,
    rotuloDoElemento: String?,
    aoEscolher: (elementoId: Int, imagemId: Int?) -> Unit,
    aoVerFicha: ((elementoId: Int) -> Unit)?,
) {
    val urlBase = urlDoServidorEmUso()
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${elemento.nome} (${rotuloDoTipo(elemento.tipo).lowercase()})", style = MaterialTheme.typography.titleSmall)
        if (elemento.imagens.isEmpty()) {
            Text("Ainda sem imagem neste capítulo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (urlBase != null) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(elemento.imagens, key = { it.id }) { imagem ->
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .clickable(enabled = !desativado) { aoEscolher(elemento.elemento_id, imagem.id) }
                            .semantics { contentDescription = "Imagem de ${elemento.nome}. Toque para escolher." },
                    ) {
                        AsyncImage(
                            model = enderecoDaImagem(urlBase, imagem.id, "miniatura"),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(80.dp),
                        )
                    }
                }
            }
        }
        if (aoVerFicha != null || rotuloDoElemento != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (aoVerFicha != null) TextButton(onClick = { aoVerFicha(elemento.elemento_id) }) { Text("Ver ficha") }
                if (rotuloDoElemento != null) {
                    Button(onClick = { aoEscolher(elemento.elemento_id, null) }, enabled = !desativado) { Text(rotuloDoElemento) }
                }
            }
        }
    }
}

/**
 * "Usar imagem existente" (VM3, VM4): as imagens **do elemento** (e só dele), capítulo a capítulo, para o retrato dele apontar uma
 * sem gerar nada. Tocar numa miniatura a aponta como a imagem do capítulo (a canônica); o seletor continua aberto se o servidor recusar.
 */
@Composable
internal fun DialogoDeImagemExistente(uso: UsoDeImagemExistente, capituloAtualId: Int?, acoes: AcoesDoPainel) {
    var busca by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!uso.aplicando) acoes.aoFecharImagemExistente() },
        title = { Text("Imagens de ${uso.elemento.nome}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Toque numa imagem para usá-la neste capítulo. Nada é gerado nem gasta IA; a imagem continua onde está.",
                    style = MaterialTheme.typography.bodySmall,
                )
                uso.erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (uso.aplicando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                when (val carga = uso.carga) {
                    CargaPorCapitulo.Carregando -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                    is CargaPorCapitulo.Erro -> Text(carga.motivo, color = MaterialTheme.colorScheme.error)
                    is CargaPorCapitulo.Pronta -> {
                        val secoes = organizarPorCapitulo(carga.capitulos, capituloAtualId, emptyList(), apenasElementoId = uso.elemento.elemento_casado?.id)
                            .map { secao -> secao.copy(elementos = secao.elementos.filter { it.imagens.isNotEmpty() }) }
                            .filter { it.elementos.isNotEmpty() }
                        if (secoes.isEmpty()) {
                            Text("Este elemento ainda não tem nenhuma imagem.", style = MaterialTheme.typography.bodyMedium)
                        }
                        ListaPorCapitulo(
                            secoes = secoes,
                            desativado = uso.aplicando,
                            rotuloDoElemento = null,
                            aoEscolher = { _, imagemId -> if (imagemId != null) acoes.aoUsarImagemExistente(imagemId) },
                            modifier = Modifier.heightIn(max = 420.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = acoes.aoFecharImagemExistente, enabled = !uso.aplicando) { Text("Cancelar") } },
    )
}
