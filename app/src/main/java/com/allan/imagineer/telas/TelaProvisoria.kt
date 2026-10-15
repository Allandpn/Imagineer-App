package com.allan.imagineer.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.allan.imagineer.telas.comum.BotaoDeIcone

/**
 * Uma ação da tela provisória: um botão que leva a outro destino.
 *
 * Existe só enquanto as telas reais não são implementadas — serve para provar
 * que a navegação do incremento 1 funciona, no aparelho, antes de haver conteúdo.
 */
data class AcaoProvisoria(val texto: String, val aoTocar: () -> Unit)

/**
 * Tela de mentira, com título, uma explicação e botões de navegação.
 *
 * Cada tela do item 7 da especificação começa como uma destas e é substituída
 * pela real no seu incremento. O conteúdo fica limitado a 600 dp de largura e
 * centralizado, para não esticar num tablet (item 7.3a).
 *
 * @param aoVoltar nulo na tela inicial, que não tem para onde voltar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaProvisoria(
    titulo: String,
    descricao: String,
    acoes: List<AcaoProvisoria> = emptyList(),
    aoVoltar: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = {
                    if (aoVoltar != null) {
                        BotaoDeIcone(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", aoVoltar, cor = LocalContentColor.current)
                    }
                },
            )
        },
    ) { margens ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(margens)
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(descricao, style = MaterialTheme.typography.bodyLarge)
                acoes.forEach { acao ->
                    Button(onClick = acao.aoTocar) { Text(acao.texto) }
                }
            }
        }
    }
}
