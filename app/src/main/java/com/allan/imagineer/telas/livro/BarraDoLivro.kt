package com.allan.imagineer.telas.livro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.PerfilRenderizacao
import com.allan.imagineer.telas.biblioteca.descreverCapitulos
import com.allan.imagineer.telas.importacao.nomesDosCampos
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Os ícones da barra do topo da tela do livro, **todos num lugar só**: o Allan quer poder trocar qualquer um que não lhe agrade,
 * e assim a troca é mexer numa linha aqui (o catálogo de ícones é o Material Icons, fonts.google.com/icons).
 */
object IconesDaTelaDoLivro {
    val elementos: ImageVector = Icons.Filled.Groups
    val pesquisar: ImageVector = Icons.Filled.Search
    val metadados: ImageVector = Icons.Filled.Info
    val pendencias: ImageVector = Icons.Filled.PendingActions
    val arquivados: ImageVector = Icons.Filled.Inventory2
    val caracteres: ImageVector = Icons.Filled.TextFields
    val faltamDados: ImageVector = Icons.Filled.Warning
}

/** O que a barra do topo pode abrir num diálogo. */
enum class DialogoDaBarra { METADADOS, PENDENCIAS }

/** Os capítulos **ativos** (não arquivados) do livro: é sobre eles que a barra conta e que a lista mostra. */
fun capitulosAtivos(livro: LivroDetalhe): List<CapituloResumo> = livro.capitulos.filter { !it.ignorado }

/** O total de caracteres dos capítulos ativos (o arquivado não conta: o leitor não o mostra). */
fun totalDeCaracteres(livro: LivroDetalhe): Int = capitulosAtivos(livro).sumOf { it.tamanho_do_texto }

/**
 * O número do selo de **pendências**: os campos que faltam nos metadados mais as sugestões da IA que esperam confirmação nos
 * capítulos ativos. Zero = nenhum selo.
 */
fun totalDePendencias(livro: LivroDetalhe): Int =
    livro.metadados_pendentes.size + capitulosAtivos(livro).sumOf { it.sugestoes_pendentes }

/** Os capítulos ativos com sugestões a confirmar, na ordem do livro. */
fun capitulosComPendencias(livro: LivroDetalhe): List<CapituloResumo> =
    capitulosAtivos(livro).filter { it.sugestoes_pendentes > 0 }

/**
 * A quantidade **curta** para caber num selo: "850", "3,4 mil", "112 mil", "1,2 mi". O texto longo ("3,4 mil caracteres") fica no
 * diálogo.
 *
 * @param locale só para os testes fixarem a vírgula decimal.
 */
fun abreviarQuantidade(n: Int, locale: Locale = Locale("pt", "BR")): String = when {
    n < 1000 -> n.toString()
    n < 1_000_000 -> {
        val mil = (n / 100.0).roundToInt() / 10.0
        if (mil >= 100 || mil == mil.toInt().toDouble()) "${mil.roundToInt()} mil" else String.format(locale, "%.1f mil", mil)
    }
    else -> {
        val mi = (n / 100_000.0).roundToInt() / 10.0
        if (mi == mi.toInt().toDouble()) "${mi.toInt()} mi" else String.format(locale, "%.1f mi", mi)
    }
}

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
 * A fileira de ícones da barra do topo da tela do livro (LV2): no lugar dos textos "Elementos", "Perfis", "Faltam dados…" e das
 * linhas de informação. O selo de cada um diz a quantidade; tocar abre o conteúdo.
 */
@Composable
fun AcoesDaBarraDoLivro(
    livro: LivroDetalhe,
    aoAbrirElementos: () -> Unit,
    aoAbrirArquivados: () -> Unit,
    aoPesquisar: (() -> Unit)?,
    aoMostrar: (DialogoDaBarra) -> Unit,
) {
    if (aoPesquisar != null) IconeComSelo(IconesDaTelaDoLivro.pesquisar, "Pesquisar", null, aoPesquisar)
    IconeComSelo(IconesDaTelaDoLivro.elementos, "Elementos", null, aoAbrirElementos)
    IconeComSelo(IconesDaTelaDoLivro.metadados, "Metadados", null) { aoMostrar(DialogoDaBarra.METADADOS) }
    val arquivados = livro.capitulos.count { it.ignorado }
    if (arquivados > 0) IconeComSelo(IconesDaTelaDoLivro.arquivados, "Capítulos arquivados", arquivados.toString(), aoAbrirArquivados)
}

/** A informação de um capítulo da lista (LV7): tamanho, tempo estimado de leitura e pendências. */
@Composable
fun DialogoDoCapitulo(capitulo: CapituloResumo, totalDeCapitulos: Int, aoAbrir: () -> Unit, aoFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(tituloDoCapitulo(capitulo.titulo, capitulo.ordem)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Capítulo ${capitulo.ordem} de $totalDeCapitulos")
                Text("Tamanho: ${descreverTamanho(capitulo.tamanho_do_texto)}")
                Text("Tempo estimado de leitura: ${descreverTempoDeLeitura(capitulo.tamanho_do_texto)}")
                Text(
                    descreverSugestoes(capitulo.sugestoes_pendentes) ?: "Nenhuma sugestão a confirmar.",
                    color = if (capitulo.sugestoes_pendentes > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { aoFechar(); aoAbrir() }) { Text("Abrir") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}

/** O diálogo de cada ícone que mostra informação (metadados, pendências, caracteres). */
@Composable
fun DialogoDaBarraDoLivro(
    qual: DialogoDaBarra,
    livro: LivroDetalhe,
    perfil: PerfilRenderizacao?,
    aoAbrirCapitulo: (Int) -> Unit,
    aoFechar: () -> Unit,
) {
    val titulo = when (qual) {
        DialogoDaBarra.METADADOS -> "Metadados"
        DialogoDaBarra.PENDENCIAS -> "Pendências"
    }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (qual) {
                    DialogoDaBarra.METADADOS -> {
                        Text(livro.titulo, style = MaterialTheme.typography.titleMedium)
                        Text(livro.autor ?: "Autor desconhecido")
                        livro.idioma?.let { Text("Idioma: $it") }
                        Text(descreverCapitulos(livro.total_de_capitulos, livro.capitulos_ignorados))
                        Text("Caracteres: ${descreverTamanho(totalDeCaracteres(livro)).removeSuffix(" caracteres")} nos capítulos ativos")
                        Text("Tempo estimado de leitura: ${descreverTempoDeLeitura(totalDeCaracteres(livro))}")
                        Text("Arquivo: ${livro.nome_arquivo}")
                        Text(
                            when {
                                livro.perfil_renderizacao_padrao_id == null -> "Perfil de renderização padrão: nenhum definido"
                                perfil != null -> "Perfil de renderização padrão: ${perfil.nome}"
                                else -> "Perfil de renderização padrão: definido"
                            },
                        )
                    }

                    DialogoDaBarra.PENDENCIAS -> {
                        val comPendencias = capitulosComPendencias(livro)
                        if (livro.metadados_pendentes.isEmpty() && comPendencias.isEmpty()) Text("Nada pendente.")
                        if (livro.metadados_pendentes.isNotEmpty()) {
                            Text(
                                "Faltam dados: ${nomesDosCampos(livro.metadados_pendentes)}.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        comPendencias.forEach { capitulo ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { aoFechar(); aoAbrirCapitulo(capitulo.id) }
                                    .padding(vertical = 6.dp),
                            ) {
                                Text(tituloDoCapitulo(capitulo.titulo, capitulo.ordem))
                                Text(
                                    descreverSugestoes(capitulo.sugestoes_pendentes).orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                }
            }
        },
        confirmButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}
