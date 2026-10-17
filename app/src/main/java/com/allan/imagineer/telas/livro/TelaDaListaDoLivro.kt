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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.compose.AsyncImage
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.ArtefatosDoLivro
import com.allan.imagineer.rede.enderecoDaImagem
import com.allan.imagineer.telas.capitulo.SeloDoArtefato
import com.allan.imagineer.telas.capitulo.painel.urlDoServidorEmUso
import com.allan.imagineer.telas.comum.BotaoDeIcone

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
    val filtro by viewModel.filtro.collectAsState()
    // Relê ao voltar para a tela: o que se confirmou ou gerou no capítulo muda a lista.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }
    val urlBase = urlDoServidorEmUso()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tipo.titulo) },
                navigationIcon = { BotaoDeIcone(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", aoVoltar, cor = LocalContentColor.current) },
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
                } else Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                    // LF1 a LF3: só a lista de cenas tem filtros.
                    val comFiltros = tipo == TipoDaListaDoLivro.CENAS
                    val visiveis = if (comFiltros) filtrarCenas(atual.dados, filtro) else atual.dados
                    if (comFiltros) BarraDeFiltrosDasCenas(atual.dados, visiveis.total, filtro, viewModel)
                    if (visiveis.capitulos.isEmpty()) {
                        Text(
                            "Nenhuma cena com esses filtros.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                        visiveis.capitulos.forEach { grupo ->
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

/** Qual diálogo de filtro está aberto (LF1, LF2). */
private enum class DialogoDeFiltro { CAPITULOS, ELEMENTOS }

/**
 * A linha de filtros da lista de cenas (LF1 a LF3): **Capítulos** e **Elementos** abrem uma lista para marcar, **Limpar** tira tudo e o texto diz
 * quantas cenas sobraram. As opções vêm da lista **inteira** ([dados]), e não da filtrada, para a pessoa poder marcar mais de um.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarraDeFiltrosDasCenas(dados: ArtefatosDoLivro, sobraram: Int, filtro: FiltroDasCenas, viewModel: ArtefatosDoLivroViewModel) {
    var aberto by remember { mutableStateOf<DialogoDeFiltro?>(null) }
    val nomes = remember(dados) { elementosDasCenas(dados) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filtro.capitulos.isNotEmpty(),
                onClick = { aberto = DialogoDeFiltro.CAPITULOS },
                label = { Text(rotuloDoFiltro("Capítulos", filtro.capitulos.size)) },
            )
            // Um servidor antigo não manda os participantes: sem nomes, não há o que filtrar.
            if (nomes.isNotEmpty()) {
                FilterChip(
                    selected = filtro.elementos.isNotEmpty(),
                    onClick = { aberto = DialogoDeFiltro.ELEMENTOS },
                    label = { Text(rotuloDoFiltro("Elementos", filtro.elementos.size)) },
                )
            }
            if (!filtro.vazio) TextButton(onClick = viewModel::limparFiltro) { Text("Limpar") }
        }
        if (!filtro.vazio) {
            Text(textoDasCenasQueSobraram(sobraram, dados.total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    when (aberto) {
        DialogoDeFiltro.CAPITULOS -> DialogoDeEscolhas(
            titulo = "Filtrar por capítulo",
            opcoes = dados.capitulos.map { it.capitulo_id to tituloDoGrupo(it.ordem, it.titulo) },
            marcadas = filtro.capitulos,
            aoAlternar = viewModel::alternarCapitulo,
            aoFechar = { aberto = null },
        )
        DialogoDeFiltro.ELEMENTOS -> DialogoDeEscolhas(
            titulo = "Filtrar por elemento",
            opcoes = nomes.map { it to it },
            marcadas = filtro.elementos,
            aoAlternar = viewModel::alternarElemento,
            aoFechar = { aberto = null },
        )
        null -> Unit
    }
}

/** Uma lista de opções com caixa de marcar (LF1, LF2); [opcoes] são pares (chave, texto). Cada toque já vale: não há "Aplicar". */
@Composable
private fun <T> DialogoDeEscolhas(titulo: String, opcoes: List<Pair<T, String>>, marcadas: Set<T>, aoAlternar: (T) -> Unit, aoFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                opcoes.forEach { (chave, texto) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { aoAlternar(chave) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = chave in marcadas, onCheckedChange = null)
                        Text(texto, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = aoFechar) { Text("Pronto") } },
    )
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
            SeloDoArtefato(artefato, Modifier.size(28.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(artefato.rotulo, style = MaterialTheme.typography.bodyLarge)
            if (mostrarSituacao) {
                Text(rotuloDaSituacao(artefato.situacao), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
