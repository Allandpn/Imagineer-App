package com.allan.imagineer.telas.livro

import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.PerfilRenderizacao
import com.allan.imagineer.telas.biblioteca.descreverCapitulos

/** Os ícones da barra do topo da tela do livro, **todos num lugar só**. */
object IconesDaTelaDoLivro {
    val elementos: ImageVector = Icons.Filled.Groups
    val pesquisar: ImageVector = Icons.Filled.Search
    val metadados: ImageVector = Icons.Filled.Info
    val continuar: ImageVector = Icons.Filled.AutoStories
}

/** Os capítulos **ativos** (não arquivados) do livro: é sobre eles que a lista e as contagens trabalham. */
fun capitulosAtivos(livro: LivroDetalhe): List<CapituloResumo> = livro.capitulos.filter { !it.ignorado }

/** O total de caracteres dos capítulos ativos (o arquivado não conta: o leitor não o mostra). */
fun totalDeCaracteres(livro: LivroDetalhe): Int = capitulosAtivos(livro).sumOf { it.tamanho_do_texto }

/** Um ícone da barra com o **selo** (número) em cima, quando há [selo]. */
@Composable
fun IconeComSelo(icone: ImageVector, descricao: String, selo: String?, aoTocar: () -> Unit) {
    IconButton(onClick = aoTocar) {
        if (selo != null) {
            BadgedBox(badge = { Badge { Text(selo) } }) { Icon(icone, contentDescription = descricao) }
        } else {
            Icon(icone, contentDescription = descricao)
        }
    }
}

/**
 * Os ícones da barra do topo da tela do livro (LV2, revisto): **Pesquisar**, **Elementos** e **Metadados**. O resto (capítulos
 * arquivados, perfis, edição) mora no menu ⋮; pendências não são informação do livro.
 */
@Composable
fun AcoesDaBarraDoLivro(
    /** O "Continuar lendo" (ou "Começar a ler") como **ícone**; a descrição diz qual dos dois (LE3). */
    continuar: ContinuarLendo?,
    aoContinuar: (ContinuarLendo) -> Unit,
    aoAbrirElementos: () -> Unit,
    aoPesquisar: (() -> Unit)?,
    aoAbrirMetadados: () -> Unit,
) {
    continuar?.let { IconeComSelo(IconesDaTelaDoLivro.continuar, it.rotulo, null) { aoContinuar(it) } }
    if (aoPesquisar != null) IconeComSelo(IconesDaTelaDoLivro.pesquisar, "Pesquisar", null, aoPesquisar)
    IconeComSelo(IconesDaTelaDoLivro.elementos, "Elementos", null, aoAbrirElementos)
    IconeComSelo(IconesDaTelaDoLivro.metadados, "Metadados", null, aoAbrirMetadados)
}

/** Os metadados do livro (LV2): tudo o que é informação dele — título, autor, idioma, capítulos, caracteres, tempo de leitura e perfil. */
@Composable
fun DialogoDosMetadados(livro: LivroDetalhe, perfil: PerfilRenderizacao?, aoFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { TituloComFechar("Metadados", aoFechar) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(livro.titulo, style = MaterialTheme.typography.titleMedium)
                Text(livro.autor ?: "Autor desconhecido")
                livro.idioma?.let { Text("Idioma: $it") }
                Text(descreverCapitulos(livro.total_de_capitulos, livro.capitulos_ignorados))
                Text(descreverProgresso(livro.capitulos_lidos, capitulosAtivos(livro).size))
                Text("Tamanho: ${descreverTamanho(totalDeCaracteres(livro))} nos capítulos ativos")
                Text("Tempo de leitura: ${descreverTempoDeLeitura(totalDeCaracteres(livro))}")
                Text("Arquivo: ${livro.nome_arquivo}")
                Text(
                    when {
                        livro.perfil_renderizacao_padrao_id == null -> "Perfil de renderização padrão: nenhum definido"
                        perfil != null -> "Perfil de renderização padrão: ${perfil.nome}"
                        else -> "Perfil de renderização padrão: definido"
                    },
                )
            }
        },
        confirmButton = {},
    )
}

/**
 * Os metadados de um **capítulo** (LV7), no botão ao lado de pesquisar na barra do capítulo: o número dele no livro, o tamanho, o
 * tempo estimado de leitura e as sugestões a confirmar.
 */
@Composable
fun DialogoDoCapitulo(
    titulo: String,
    ordem: Int,
    totalDeCapitulos: Int?,
    caracteres: Int,
    sugestoesPendentes: Int,
    arquivado: Boolean,
    aoFechar: () -> Unit,
    /** LE5: se o capítulo está lido, e como marcar/desmarcar à mão (nulo = sem o botão). */
    lido: Boolean = false,
    aoAlternarLido: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { TituloComFechar(titulo, aoFechar) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (totalDeCapitulos != null) "Capítulo $ordem de $totalDeCapitulos" else "Capítulo $ordem")
                Text("Tamanho: ${descreverTamanho(caracteres)}")
                Text("Tempo de leitura: ${descreverTempoDeLeitura(caracteres)}")
                Text(
                    descreverSugestoes(sugestoesPendentes) ?: "Nenhuma sugestão a confirmar.",
                    color = if (sugestoesPendentes > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (arquivado) Text("Este capítulo está arquivado.", color = MaterialTheme.colorScheme.error)
                // Lido: um interruptor (o padrão de mercado), em vez de botões de texto.
                if (aoAlternarLido != null) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Lido", modifier = Modifier.weight(1f))
                        Switch(checked = lido, onCheckedChange = { aoAlternarLido() })
                    }
                } else {
                    Text(if (lido) "Lido" else "Ainda não lido", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {},
    )
}

/** O título de um diálogo de informação, com um **X** à direita para fechar (no lugar do botão "Fechar" de texto). */
@Composable
private fun TituloComFechar(titulo: String, aoFechar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, modifier = Modifier.weight(1f))
        IconButton(onClick = aoFechar) { Icon(Icons.Filled.Close, contentDescription = "Fechar") }
    }
}
