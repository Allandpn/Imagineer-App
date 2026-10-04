package com.allan.imagineer.telas.lixeira

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.ImagemNaLixeira
import com.allan.imagineer.rede.RepositorioDaLixeiraDeElementosDoLivro
import com.allan.imagineer.rede.RepositorioDaLixeiraDeFramesDoLivro
import com.allan.imagineer.rede.RepositorioDaLixeiraDoLivro
import com.allan.imagineer.rede.enderecoDaCapa
import com.allan.imagineer.rede.enderecoDaImagem
import com.allan.imagineer.telas.capitulo.painel.urlDoServidorEmUso

/** O que a Lixeira guarda, por tipo (LT1). Cada tipo novo (cenas, elementos) entra aqui, um de cada vez. */
enum class TipoDaLixeira(val rotulo: String) {
    IMAGENS("Imagens"),
    LIVROS("Livros"),
    CENAS("Cenas e retratos"),
    ELEMENTOS("Elementos"),
}

/**
 * A **Lixeira** (item 7.5b, LX8 e LT1): o que foi apagado e continua no servidor até o usuário apagar de vez. No alto, o **tipo**
 * (Imagens, Livros...); cada item tem **Restaurar** e **Apagar de vez** (com confirmação); **Esvaziar** (com confirmação que diz
 * quanto vai embora). Nada some sozinho (LX6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaLixeira(aoVoltar: () -> Unit, livroId: Int? = null) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val imagens: LixeiraViewModel = viewModel(
        key = "lixeira-imagens-$livroId",
        factory = viewModelFactory {
            initializer {
                // AJ3: a lixeira do livro (pelo menu ⋮ dele) só traz o que é dele.
                LixeiraViewModel(livroId?.let { RepositorioDaLixeiraDoLivro(aplicacao.repositorioDaLixeira, it) } ?: aplicacao.repositorioDaLixeira)
            }
        },
    )
    val livros: LixeiraDeItensViewModel<LivroDaLixeira> = viewModel(
        key = "lixeira-livros",
        factory = viewModelFactory { initializer { LixeiraDeItensViewModel(FonteDaLixeiraDeLivros(aplicacao.repositorioDaLixeiraDeLivros)) } },
    )
    val cenas: LixeiraDeItensViewModel<FrameDaLixeira> = viewModel(
        key = "lixeira-cenas-$livroId",
        factory = viewModelFactory {
            initializer {
                val base = aplicacao.repositorioDaLixeiraDeFrames
                LixeiraDeItensViewModel(FonteDaLixeiraDeFrames(livroId?.let { RepositorioDaLixeiraDeFramesDoLivro(base, it) } ?: base))
            }
        },
    )
    val elementos: LixeiraDeItensViewModel<ElementoDaLixeira> = viewModel(
        key = "lixeira-elementos-$livroId",
        factory = viewModelFactory {
            initializer {
                val base = aplicacao.repositorioDaLixeiraDeElementos
                LixeiraDeItensViewModel(FonteDaLixeiraDeElementos(livroId?.let { RepositorioDaLixeiraDeElementosDoLivro(base, it) } ?: base))
            }
        },
    )
    val estadoDosElementos by elementos.estado.collectAsState()
    val estadoDasCenas by cenas.estado.collectAsState()
    val estadoDasImagens by imagens.estado.collectAsState()
    val estadoDosLivros by livros.estado.collectAsState()
    var tipo by rememberSaveable { mutableStateOf(TipoDaLixeira.IMAGENS) }
    // Relê toda vez que a tela fica visível: o que se apagou nos capítulos e na biblioteca aparece aqui.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        imagens.carregar()
        if (livroId == null) livros.carregar()
        cenas.carregar()
        elementos.carregar()
    }

    val temAlgo = when (tipo) {
        TipoDaLixeira.IMAGENS -> ((estadoDasImagens.carga as? CargaDaLixeira.Pronta)?.lixeira?.imagens?.isNotEmpty()) == true
        TipoDaLixeira.LIVROS -> ((estadoDosLivros.carga as? CargaDeItens.Pronta)?.itens?.isNotEmpty()) == true
        TipoDaLixeira.CENAS -> ((estadoDasCenas.carga as? CargaDeItens.Pronta)?.itens?.isNotEmpty()) == true
        TipoDaLixeira.ELEMENTOS -> ((estadoDosElementos.carga as? CargaDeItens.Pronta)?.itens?.isNotEmpty()) == true
    }
    val esvaziando = estadoDasImagens.esvaziando || estadoDosLivros.esvaziando || estadoDasCenas.esvaziando || estadoDosElementos.esvaziando
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (livroId == null) "Lixeira" else "Lixeira do livro") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                actions = {
                    // Esvaziar apaga a lixeira inteira no servidor; na do livro, só item a item (para não levar o de outro livro).
                    if (temAlgo && livroId == null) {
                        TextButton(
                            onClick = {
                                when (tipo) {
                                    TipoDaLixeira.IMAGENS -> imagens.pedirEsvaziar()
                                    TipoDaLixeira.LIVROS -> livros.pedirEsvaziar()
                                    TipoDaLixeira.CENAS -> cenas.pedirEsvaziar()
                                    TipoDaLixeira.ELEMENTOS -> elementos.pedirEsvaziar()
                                }
                            },
                            enabled = !esvaziando,
                        ) { Text("Esvaziar") }
                    }
                },
            )
        },
    ) { margens ->
        Column(modifier = Modifier.padding(margens).fillMaxSize()) {
            // O tipo: Imagens, Livros...
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TipoDaLixeira.entries.filter { livroId == null || it != TipoDaLixeira.LIVROS }.forEach { opcao ->
                    FilterChip(selected = tipo == opcao, onClick = { tipo = opcao }, label = { Text(opcao.rotulo) })
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                when (tipo) {
                    TipoDaLixeira.IMAGENS -> CorpoDaLixeiraDeImagens(
                        estado = estadoDasImagens,
                        aoTentarDeNovo = imagens::tentarDeNovo,
                        aoRestaurar = imagens::restaurar,
                        aoPedirApagarDeVez = imagens::pedirApagarDeVez,
                    )
                    TipoDaLixeira.LIVROS -> CorpoDaLixeiraDeItens(
                        estado = estadoDosLivros,
                        textos = livros.textos,
                        aoTentarDeNovo = livros::tentarDeNovo,
                        aoRestaurar = livros::restaurar,
                        aoPedirApagarDeVez = livros::pedirApagarDeVez,
                    ) { item, ocupado, restaurar, apagar -> CartaoDoLivroNaLixeira(item.livro, ocupado, restaurar, apagar) }
                    TipoDaLixeira.CENAS -> CorpoDaLixeiraDeItens(
                        estado = estadoDasCenas,
                        textos = cenas.textos,
                        aoTentarDeNovo = cenas::tentarDeNovo,
                        aoRestaurar = cenas::restaurar,
                        aoPedirApagarDeVez = cenas::pedirApagarDeVez,
                    ) { item, ocupado, restaurar, apagar -> CartaoDoFrameNaLixeira(item.frame, ocupado, restaurar, apagar) }
                    TipoDaLixeira.ELEMENTOS -> CorpoDaLixeiraDeItens(
                        estado = estadoDosElementos,
                        textos = elementos.textos,
                        aoTentarDeNovo = elementos::tentarDeNovo,
                        aoRestaurar = elementos::restaurar,
                        aoPedirApagarDeVez = elementos::pedirApagarDeVez,
                    ) { item, ocupado, restaurar, apagar -> CartaoDoElementoNaLixeira(item.elemento, ocupado, restaurar, apagar) }
                }
            }
        }
    }

    // As confirmações (apagar de vez, esvaziar): uma de cada vez, do tipo em que se está.
    estadoDasImagens.confirmacao?.let { pedido ->
        val (titulo, texto, rotulo) = when (pedido) {
            is ConfirmacaoDaLixeira.ApagarUma -> Triple("Apagar de vez esta imagem?", AVISO_APAGAR_DE_VEZ, "Apagar de vez")
            is ConfirmacaoDaLixeira.EsvaziarTudo -> Triple("Esvaziar a lixeira?", avisoDeEsvaziar(pedido.quantas, pedido.bytes), "Esvaziar")
        }
        DialogoDeConfirmacao(titulo, texto, rotulo, imagens::confirmar, imagens::cancelarConfirmacao)
    }
    estadoDosLivros.confirmacao?.let { pedido ->
        val textos = livros.textos
        val (titulo, texto, rotulo) = when (pedido) {
            is ConfirmacaoDeItens.ApagarUm -> Triple(textos.tituloDeApagar, textos.avisoDeApagar, "Apagar de vez")
            is ConfirmacaoDeItens.EsvaziarTudo -> Triple(textos.tituloDeEsvaziar, textos.avisoDeEsvaziar(pedido.quantos, pedido.bytes), "Esvaziar")
        }
        DialogoDeConfirmacao(titulo, texto, rotulo, livros::confirmar, livros::cancelarConfirmacao)
    }
}

@Composable
private fun DialogoDeConfirmacao(titulo: String, texto: String, rotulo: String, aoConfirmar: () -> Unit, aoCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = aoConfirmar) { Text(rotulo, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}

// --------------------------------------------------------------------------- //
// Imagens
// --------------------------------------------------------------------------- //

@Composable
private fun CorpoDaLixeiraDeImagens(
    estado: EstadoDaLixeira,
    aoTentarDeNovo: () -> Unit,
    aoRestaurar: (Int) -> Unit,
    aoPedirApagarDeVez: (ImagemNaLixeira) -> Unit,
) {
    when (val carga = estado.carga) {
        CargaDaLixeira.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is CargaDaLixeira.Erro -> ErroDaLixeira(carga.motivo, aoTentarDeNovo)
        is CargaDaLixeira.Pronta -> if (carga.lixeira.imagens.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(TEXTO_DA_LIXEIRA_VAZIA, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            ListaDaLixeira(carga.lixeira.imagens, carga.lixeira.total_em_bytes, estado, aoRestaurar, aoPedirApagarDeVez)
        }
    }
}

@Composable
private fun ErroDaLixeira(motivo: String, aoTentarDeNovo: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(motivo, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Button(onClick = aoTentarDeNovo) { Text("Tentar de novo") }
    }
}

@Composable
private fun ListaDaLixeira(
    imagens: List<ImagemNaLixeira>,
    totalEmBytes: Long,
    estado: EstadoDaLixeira,
    aoRestaurar: (Int) -> Unit,
    aoPedirApagarDeVez: (ImagemNaLixeira) -> Unit,
) {
    val urlBase = urlDoServidorEmUso()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(resumoDaLixeira(imagens.size, totalEmBytes), style = MaterialTheme.typography.titleMedium)
                Text(
                    "Nada daqui some sozinho: as imagens ficam até você apagá-las de vez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                estado.recado?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (estado.recadoEhErro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        items(imagens, key = { it.id }) { imagem ->
            val ocupada = imagem.id in estado.ocupadas || estado.esvaziando
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (urlBase != null) {
                        AsyncImage(
                            model = enderecoDaImagem(urlBase, imagem.id, "miniatura"),
                            contentDescription = "Imagem apagada: ${nomeDoFrameNaLixeira(imagem)}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black),
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(legendaDaLixeira(imagem), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { aoRestaurar(imagem.id) }, enabled = !ocupada) { Text("Restaurar", maxLines = 1, softWrap = false) }
                            OutlinedButton(onClick = { aoPedirApagarDeVez(imagem) }, enabled = !ocupada) {
                                Text("Apagar de vez", maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- //
// Itens (livros, e depois cenas e elementos)
// --------------------------------------------------------------------------- //

/** O corpo da lixeira de um tipo de **item**: carregando, erro, vazia ou a lista (com o cartão de cada tipo). */
@Composable
private fun <T : ItemDaLixeira> CorpoDaLixeiraDeItens(
    estado: EstadoDaLixeiraDeItens<T>,
    textos: TextosDaLixeira,
    aoTentarDeNovo: () -> Unit,
    aoRestaurar: (Int) -> Unit,
    aoPedirApagarDeVez: (T) -> Unit,
    cartao: @Composable (item: T, ocupado: Boolean, restaurar: () -> Unit, apagar: () -> Unit) -> Unit,
) {
    when (val carga = estado.carga) {
        CargaDeItens.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is CargaDeItens.Erro -> ErroDaLixeira(carga.motivo, aoTentarDeNovo)
        is CargaDeItens.Pronta -> if (carga.itens.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(textos.vazia, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(textos.resumo(carga.itens.size, carga.totalEmBytes), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Nada daqui some sozinho: fica até você apagar de vez.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        estado.recado?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = if (estado.recadoEhErro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                items(carga.itens, key = { it.id }) { item ->
                    val ocupado = item.id in estado.ocupados || estado.esvaziando
                    cartao(item, ocupado, { aoRestaurar(item.id) }, { aoPedirApagarDeVez(item) })
                }
            }
        }
    }
}

/** O cartão de um livro da lixeira: a capa, o título, o autor (na fonte do livro), o que ele leva junto e os botões. */
@Composable
private fun CartaoDoLivroNaLixeira(
    livro: com.allan.imagineer.rede.LivroNaLixeira,
    ocupado: Boolean,
    aoRestaurar: () -> Unit,
    aoApagarDeVez: () -> Unit,
) {
    val urlBase = urlDoServidorEmUso()
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (urlBase != null && livro.tem_capa) {
                AsyncImage(
                    model = enderecoDaCapa(urlBase, livro.id, 0),
                    contentDescription = "Capa de ${livro.titulo}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 64.dp, height = 96.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(livro.titulo, style = MaterialTheme.typography.titleMedium)
                livro.autor?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic) }
                Text(detalhesDoLivroNaLixeira(livro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = aoRestaurar, enabled = !ocupado) { Text("Restaurar", maxLines = 1, softWrap = false) }
                    OutlinedButton(onClick = aoApagarDeVez, enabled = !ocupado) {
                        Text("Apagar de vez", maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** O cartão de uma cena ou retrato da lixeira: a imagem (se tem), o nome, onde estava, o que leva junto e os botões. */
@Composable
private fun CartaoDoFrameNaLixeira(
    frame: com.allan.imagineer.rede.FrameNaLixeira,
    ocupado: Boolean,
    aoRestaurar: () -> Unit,
    aoApagarDeVez: () -> Unit,
) {
    val urlBase = urlDoServidorEmUso()
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (urlBase != null && frame.imagem_id != null) {
                AsyncImage(
                    model = enderecoDaImagem(urlBase, frame.imagem_id, "miniatura"),
                    contentDescription = tituloDoFrameNaLixeira(frame),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tituloDoFrameNaLixeira(frame), style = MaterialTheme.typography.titleMedium)
                Text(detalhesDoFrameNaLixeira(frame), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = aoRestaurar, enabled = !ocupado) { Text("Restaurar", maxLines = 1, softWrap = false) }
                    OutlinedButton(onClick = aoApagarDeVez, enabled = !ocupado) {
                        Text("Apagar de vez", maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** O cartão de um elemento da lixeira: a imagem (se tem), o nome, o tipo e o livro, o que leva junto e os botões. */
@Composable
private fun CartaoDoElementoNaLixeira(
    elemento: com.allan.imagineer.rede.ElementoNaLixeira,
    ocupado: Boolean,
    aoRestaurar: () -> Unit,
    aoApagarDeVez: () -> Unit,
) {
    val urlBase = urlDoServidorEmUso()
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (urlBase != null && elemento.imagem_id != null) {
                AsyncImage(
                    model = enderecoDaImagem(urlBase, elemento.imagem_id, "miniatura"),
                    contentDescription = elemento.nome,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(elemento.nome, style = MaterialTheme.typography.titleMedium)
                Text(detalhesDoElementoNaLixeira(elemento), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = aoRestaurar, enabled = !ocupado) { Text("Restaurar", maxLines = 1, softWrap = false) }
                    OutlinedButton(onClick = aoApagarDeVez, enabled = !ocupado) {
                        Text("Apagar de vez", maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
