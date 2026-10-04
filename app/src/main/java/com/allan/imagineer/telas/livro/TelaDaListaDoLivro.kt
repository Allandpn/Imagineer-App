package com.allan.imagineer.telas.livro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.enderecoDaImagem
import com.allan.imagineer.telas.capitulo.iconeDoArtefato
import com.allan.imagineer.telas.capitulo.painel.urlDoServidorEmUso

/**
 * As telas **Pendências** (LY7) e **Cenas** (LY8) do livro: os artefatos do livro inteiro, **por capítulo**, com a barra de baixo do livro.
 * Tocar num item leva ao capítulo ([aoAbrir]). Só leitura: nada aqui chama a IA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDaListaDoLivro(
    livroId: Int,
    tipo: TipoDaListaDoLivro,
    aoVoltar: () -> Unit,
    aoAbrir: (AlvoNoCapitulo) -> Unit,
    aoIrParaODoLivro: (DestinoDoLivro) -> Unit,
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: ArtefatosDoLivroViewModel = viewModel(
        key = "lista-${tipo.name}-$livroId",
        factory = viewModelFactory { initializer { ArtefatosDoLivroViewModel(livroId, tipo, aplicacao.repositorioDeArtefatosDoLivro) } },
    )
    val estado by viewModel.estado.collectAsState()
    // Relê ao voltar para a tela: o que se confirmou ou gerou no capítulo muda a lista.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }
    val urlBase = urlDoServidorEmUso()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tipo.titulo) },
                navigationIcon = { IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
        bottomBar = {
            BarraDeNavegacaoDoLivro(
                selecionado = if (tipo == TipoDaListaDoLivro.PENDENCIAS) DestinoDoLivro.PENDENCIAS else DestinoDoLivro.CENAS,
                aoIr = aoIrParaODoLivro,
            )
        },
    ) { margens ->
        Box(modifier = Modifier.padding(margens).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val atual = estado) {
                EstadoDaListaDoLivro.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is EstadoDaListaDoLivro.Erro -> Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(atual.motivo, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = viewModel::tentarDeNovo, modifier = Modifier.padding(top = 12.dp)) { Text("Tentar de novo") }
                }
                is EstadoDaListaDoLivro.Pronto -> if (atual.dados.capitulos.isEmpty()) {
                    Text(
                        tipo.vazia,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                        atual.dados.capitulos.forEach { grupo ->
                            item(key = "cap-${grupo.capitulo_id}") {
                                Text(
                                    tituloDoGrupo(grupo.ordem, grupo.titulo),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(grupo.artefatos, key = { "${grupo.capitulo_id}-${it.tipo}-${it.sugestao_id}-${it.frame_id}-${it.rotulo}" }) { artefato ->
                                LinhaDoArtefatoDoLivro(artefato, mostrarSituacao = tipo == TipoDaListaDoLivro.CENAS, urlBase = urlBase) {
                                    aoAbrir(alvoDoToque(grupo.capitulo_id, artefato))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaDoArtefatoDoLivro(artefato: Artefato, mostrarSituacao: Boolean, urlBase: String?, aoTocar: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = aoTocar).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (urlBase != null && artefato.imagem_id != null) {
            AsyncImage(
                model = enderecoDaImagem(urlBase, artefato.imagem_id, "miniatura"),
                contentDescription = artefato.rotulo,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black),
            )
        } else {
            Icon(iconeDoArtefato(artefato), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(artefato.rotulo, style = MaterialTheme.typography.bodyLarge)
            if (mostrarSituacao) {
                Text(rotuloDaSituacao(artefato.situacao), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
