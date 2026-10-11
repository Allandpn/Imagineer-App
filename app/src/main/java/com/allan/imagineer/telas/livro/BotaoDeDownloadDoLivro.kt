package com.allan.imagineer.telas.livro

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.local.EstadoDoDownload
import com.allan.imagineer.rede.LivroResumo

/** O desenho do ícone de download de cada situação (DL1). Baixando é um anel de progresso, não um desenho. */
internal fun iconeDaSituacaoDoDownload(situacao: SituacaoDoDownload): ImageVector = when (situacao) {
    SituacaoDoDownload.NAO_BAIXADO -> Icons.Filled.Download
    SituacaoDoDownload.BAIXANDO -> Icons.Filled.Download
    SituacaoDoDownload.PARADO -> Icons.Filled.PauseCircleOutline
    SituacaoDoDownload.EM_DIA -> Icons.Filled.DownloadDone
    SituacaoDoDownload.DESATUALIZADO -> Icons.Filled.Update
    SituacaoDoDownload.FALHOU -> Icons.Filled.ErrorOutline
}

/**
 * O ícone de download no cartão de um livro da biblioteca, na lista e nas capas (item 7.5g, DL1): mostra o estado (baixar, baixando com o
 * anel de progresso, parado, baixado, há uma atualização, falhou) e, ao tocar, abre o diálogo de Download (baixar, atualizar, remover).
 * Sem conexão só vale tocar no livro já baixado (para remover); baixar e atualizar precisam do servidor.
 *
 * @param sobreACapa o ícone desenhado sobre a imagem de uma capa: um círculo escuro translúcido, com o ícone branco, como o ⋮ da capa.
 */
@Composable
fun BotaoDeDownloadDoLivro(livro: LivroResumo, modifier: Modifier = Modifier, sobreACapa: Boolean = false) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val baixador = aplicacao.baixadorDeLivros
    val estados by baixador.estados.collectAsState()
    val estado = estados[livro.id] ?: EstadoDoDownload.NaoBaixado
    val online by aplicacao.conexao.online.collectAsState()
    var dialogo by remember { mutableStateOf(false) }
    LaunchedEffect(livro.id) { baixador.carregar(livro.id) }

    val situacao = situacaoDoDownload(estado, livro.revisao)
    val cor = if (sobreACapa) Color.White else when (situacao) {
        SituacaoDoDownload.DESATUALIZADO -> MaterialTheme.colorScheme.tertiary
        SituacaoDoDownload.FALHOU -> MaterialTheme.colorScheme.error
        SituacaoDoDownload.EM_DIA -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tamanho = if (sobreACapa) 28.dp else 40.dp
    val conteudo: @Composable () -> Unit = {
        IconButton(
            onClick = { dialogo = true },
            enabled = online || downloadValeSemRede(situacao),
            modifier = Modifier.size(tamanho).semantics { contentDescription = descricaoDoIconeDeDownload(situacao) },
        ) {
            if (estado is EstadoDoDownload.Baixando) {
                CircularProgressIndicator(progress = { estado.progresso.fracao }, strokeWidth = 2.dp, color = cor, modifier = Modifier.size(if (sobreACapa) 16.dp else 20.dp))
            } else {
                Icon(iconeDaSituacaoDoDownload(situacao), contentDescription = null, tint = cor, modifier = Modifier.size(if (sobreACapa) 18.dp else 24.dp))
            }
        }
    }
    if (sobreACapa) {
        Surface(color = Color.Black.copy(alpha = 0.35f), shape = CircleShape, modifier = modifier) { conteudo() }
    } else {
        androidx.compose.foundation.layout.Box(modifier) { conteudo() }
    }
    if (dialogo) DialogoDeDownload(livro.id, baixador, estado, aoFechar = { dialogo = false }, revisaoDoServidor = livro.revisao)
}
