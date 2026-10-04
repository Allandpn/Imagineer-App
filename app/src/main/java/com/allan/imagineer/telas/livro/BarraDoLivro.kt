package com.allan.imagineer.telas.livro

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import com.allan.imagineer.telas.comum.BotaoDeIcone
import com.allan.imagineer.telas.comum.MarcaDeLido
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
fun DialogoDosMetadados(
    livro: LivroDetalhe,
    perfil: PerfilRenderizacao?,
    aoFechar: () -> Unit,
    /** PL4: se o livro está baixado para ler offline, quando e quanto ocupa. */
    baixado: com.allan.imagineer.local.EstadoDoDownload.Baixado? = null,
) {
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
                baixado?.let { Text("Offline: baixado em ${descreverDataDoDownload(it.em)} (${com.allan.imagineer.telas.lixeira.descreverTamanho(it.bytes)})") }
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
    /** RN1: o título como está guardado (vazio = o padrão "Capítulo N") e como renomear; nulo = sem o lápis. */
    tituloGuardado: String = "",
    aoRenomear: ((String) -> Unit)? = null,
) {
    // RN1: o lápis ao lado do título troca o título por um campo, com ✓ (salvar) e X (cancelar).
    var editando by remember { mutableStateOf(false) }
    var texto by remember(tituloGuardado) { mutableStateOf(tituloGuardado) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = {
            if (editando && aoRenomear != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        label = { Text("Título do capítulo") },
                        placeholder = { Text("Capítulo $ordem") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    BotaoDeIcone(Icons.Filled.Check, "Salvar o título", aoTocar = { aoRenomear(texto); editando = false }, cor = LocalContentColor.current)
                    BotaoDeIcone(Icons.Filled.Close, "Cancelar", aoTocar = { texto = tituloGuardado; editando = false }, cor = LocalContentColor.current)
                }
            } else {
                TituloComFechar(titulo, aoFechar, aoEditar = aoRenomear?.let { { editando = true } })
            }
        },
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
                // Lido: um ícone discreto que **muda** ao tocar (círculo vazio = não lido; marcado = lido), com o estado ao lado.
                if (aoAlternarLido != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = aoAlternarLido).padding(top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MarcaDeLido(lido, Modifier.semantics { contentDescription = if (lido) "Marcar como não lido" else "Marcar como lido" })
                        Text(
                            if (lido) "Lido" else "Não lido",
                            modifier = Modifier.padding(start = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
private fun TituloComFechar(titulo: String, aoFechar: () -> Unit, aoEditar: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, modifier = Modifier.weight(1f))
        // O lápis: indica que o título pode ser renomeado.
        aoEditar?.let { BotaoDeIcone(Icons.Filled.Edit, "Renomear o capítulo", it, cor = LocalContentColor.current) }
        BotaoDeIcone(Icons.Filled.Close, "Fechar", aoFechar, cor = LocalContentColor.current)
    }
}
