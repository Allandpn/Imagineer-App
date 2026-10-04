package com.allan.imagineer.telas.configuracao

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.telas.comum.BotaoDeIcone

/**
 * Configuração mínima (item 7.10, parte 1): o endereço do servidor.
 *
 * Esta função só monta o ViewModel e entrega o estado para [ConteudoDaConfiguracao].
 * Separar as duas permite ver o desenho da tela no preview com um estado qualquer,
 * sem precisar de ViewModel, rede nem DataStore.
 *
 * @param aoSalvar chamada depois de gravar a URL.
 * @param aoVoltar nulo na primeira abertura, quando não há para onde voltar.
 */
@Composable
fun TelaConfiguracao(aoSalvar: () -> Unit, aoVoltar: (() -> Unit)?) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: ConfiguracaoViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ConfiguracaoViewModel(aplicacao.armazenamento, aplicacao.servidor) }
        },
    )
    val estado by viewModel.estado.collectAsState()

    // Reage a "salvou" uma vez: quando o ViewModel grava a URL, a tela navega.
    LaunchedEffect(estado.salvou) {
        if (estado.salvou) aoSalvar()
    }

    ConteudoDaConfiguracao(
        estado = estado,
        aoMudarUrl = viewModel::aoMudarUrl,
        aoTestar = viewModel::testar,
        aoSalvar = viewModel::salvar,
        aoVoltar = aoVoltar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConteudoDaConfiguracao(
    estado: EstadoDaConfiguracao,
    aoMudarUrl: (String) -> Unit,
    aoTestar: () -> Unit,
    aoSalvar: () -> Unit,
    aoVoltar: (() -> Unit)?,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuração") },
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Endereço do servidor do Imagineer. Use o endereço Tailscale do " +
                        "Raspberry Pi (ex.: 100.64.0.5:8000).",
                    style = MaterialTheme.typography.bodyLarge,
                )

                OutlinedTextField(
                    value = estado.url,
                    onValueChange = aoMudarUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL do servidor") },
                    singleLine = true,
                    isError = estado.urlInvalida,
                    supportingText = {
                        if (estado.urlInvalida) Text("Isso não parece um endereço válido.")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )

                ResultadoDoTesteNaTela(estado.teste)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = aoTestar,
                        enabled = estado.url.isNotBlank() && estado.teste != EstadoDoTeste.Testando,
                    ) { Text("Testar") }

                    when (estado.teste) {
                        is EstadoDoTeste.Conectado ->
                            Button(onClick = aoSalvar) { Text("Salvar") }
                        // Servidor desligado agora, mas o endereço pode estar certo.
                        is EstadoDoTeste.Falhou ->
                            Button(onClick = aoSalvar) { Text("Salvar mesmo assim") }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultadoDoTesteNaTela(teste: EstadoDoTeste) {
    when (teste) {
        EstadoDoTeste.Nenhum -> Unit
        EstadoDoTeste.Testando -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text("Testando…")
        }
        is EstadoDoTeste.Conectado -> Text(
            text = if (teste.servidorTemChave) {
                "Conectado. O servidor tem chave de API própria."
            } else {
                "Conectado. O servidor não tem chave de API própria — as chamadas de IA " +
                    "precisarão da sua (Bloco E)."
            },
            color = MaterialTheme.colorScheme.primary,
        )
        is EstadoDoTeste.Falhou -> Text(
            text = teste.motivo,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
