package com.allan.imagineer.telas.elementos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.DetalheDoElemento
import com.allan.imagineer.telas.capitulo.painel.TIPOS_DE_ELEMENTO
import com.allan.imagineer.telas.capitulo.painel.identidadeVigente
import com.allan.imagineer.telas.capitulo.painel.rotuloDoTipo

/**
 * A ficha de um elemento (item 7.8, E21): quem ele é (a identidade, com o que cada capítulo
 * acrescentou) e a aparência em cada capítulo — para consultar e corrigir sem poluir o painel.
 *
 * @param capituloId o capítulo da sugestão que abriu a ficha, se foi o caso: oferece "Adicionar
 * estado neste capítulo" quando ele ainda não tem estado.
 *
 * O visual é provisório: este incremento trata das regras.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaFichaDoElemento(
    elementoId: Int,
    capituloId: Int?,
    aoVoltar: () -> Unit,
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: FichaDoElementoViewModel = viewModel(
        key = "ficha$elementoId",
        factory = viewModelFactory {
            initializer {
                FichaDoElementoViewModel(
                    elementoId, capituloId, aplicacao.repositorioDeElementos, aplicacao.repositorioDeLivros,
                )
            }
        },
    )
    val estado by viewModel.estado.collectAsState()

    LaunchedEffect(viewModel) { viewModel.carregar() }
    // Apagado: não há mais ficha para mostrar.
    LaunchedEffect(estado.apagado) { if (estado.apagado) aoVoltar() }

    val detalhe = (estado.carga as? CargaDaFicha.Pronta)?.detalhe
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detalhe?.nome ?: "Elemento") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (detalhe != null) {
                        IconButton(onClick = viewModel::abrirMesclagem) {
                            Icon(Icons.Filled.MergeType, contentDescription = "Mesclar com outro elemento")
                        }
                        IconButton(onClick = { viewModel.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ELEMENTO) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar o elemento")
                        }
                        IconButton(onClick = { viewModel.abrirDialogo(TipoDeDialogoDaFicha.APAGAR_ELEMENTO) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Apagar o elemento")
                        }
                    }
                },
            )
        },
    ) { margens ->
        Box(modifier = Modifier.padding(margens).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                when (val carga = estado.carga) {
                    CargaDaFicha.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    is CargaDaFicha.Erro -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(carga.motivo, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            Button(onClick = viewModel::tentarDeNovo) { Text("Tentar de novo") }
                        }
                    }
                    is CargaDaFicha.Pronta -> ConteudoDaFicha(carga.detalhe, capituloId, viewModel, estado.galeria, estado.recadoDaGaleria)
                }
            }
        }
    }

    estado.dialogo?.let { dialogo ->
        if (detalhe != null) Dialogos(dialogo, detalhe, estado, viewModel)
    }
}

@Composable
private fun ConteudoDaFicha(
    detalhe: DetalheDoElemento,
    capituloId: Int?,
    viewModel: FichaDoElementoViewModel,
    galeria: CargaDaGaleria,
    recadoDaGaleria: String?,
) {
    val estados = estadosEmOrdem(detalhe)
    val acrescimos = acrescimosDeIdentidade(detalhe)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    rotuloDoTipo(detalhe.tipo),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(detalhe.nome, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }

        // --- Imagens do elemento (FI5): logo no alto, porque é o que a pessoa quer ver primeiro ---------
        item {
            SecaoDeImagensDaFicha(galeria, recadoDaGaleria, viewModel::tentarDeNovoAGaleria, viewModel::definirImagemCanonica)
        }

        // --- Quem é: a identidade inicial mais o que cada capítulo acrescentou ---------------
        item { Text("Quem é", style = MaterialTheme.typography.titleMedium) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val inicial = detalhe.descricao?.takeIf { it.isNotBlank() }
                    if (inicial == null && acrescimos.isEmpty()) {
                        Text("Sem identidade registrada.", style = MaterialTheme.typography.bodyMedium)
                    }
                    inicial?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        // E38: cada acréscimo é editável e apagável; E39: e dá para acrescentar à mão.
        items(acrescimos, key = { "a${it.id}" }) { acrescimo ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        acrescimo.capitulo,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(acrescimo.texto, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ACRESCIMO, acrescimoId = acrescimo.id) }) {
                            Text("Editar")
                        }
                        TextButton(onClick = { viewModel.abrirDialogo(TipoDeDialogoDaFicha.APAGAR_ACRESCIMO, acrescimoId = acrescimo.id) }) {
                            Text("Apagar")
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.abrirAdicao(TipoDeDialogoDaFicha.ADICIONAR_ACRESCIMO, noCapituloDaSugestao = true) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Adicionar acréscimo") }
        }

        // --- Aparência por capítulo -----------------------------------------------------------
        item { Text("Aparência por capítulo", style = MaterialTheme.typography.titleMedium) }
        // E34: só quando a ficha veio de um capítulo que ainda não tem estado — em botão próprio,
        // de largura total, e não espremido ao lado do título.
        if (capituloId != null && !temEstadoNoCapitulo(detalhe, capituloId)) {
            item {
                Button(
                    onClick = { viewModel.abrirAdicao(TipoDeDialogoDaFicha.ADICIONAR_ESTADO, noCapituloDaSugestao = true) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Adicionar estado neste capítulo") }
            }
        }
        // E40: em qualquer outro capítulo, escolhendo-o primeiro.
        item {
            OutlinedButton(
                onClick = { viewModel.abrirAdicao(TipoDeDialogoDaFicha.ADICIONAR_ESTADO, noCapituloDaSugestao = false) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Adicionar estado em outro capítulo") }
        }
        if (estados.isEmpty()) {
            item { Text("Ainda sem estados registrados.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(estados, key = { it.id }) { estado ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        rotuloDoCapitulo(estado.titulo_do_capitulo, estado.ordem_do_capitulo) +
                            if (estado.capitulo_id == capituloId) " (este capítulo)" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(estado.descricao, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.abrirDialogo(TipoDeDialogoDaFicha.EDITAR_ESTADO, estado.id) }) {
                            Text("Editar")
                        }
                        TextButton(onClick = { viewModel.abrirDialogo(TipoDeDialogoDaFicha.APAGAR_ESTADO, estado.id) }) {
                            Text("Apagar")
                        }
                    }
                }
            }
        }

        // --- Cenas em que aparece (FI5) ------------------------------------------------------------
        item { SecaoDeCenasDaFicha(galeria, viewModel::tentarDeNovoAGaleria) }
    }
}

@Composable
private fun Dialogos(
    dialogo: DialogoDaFicha,
    detalhe: DetalheDoElemento,
    estadoDaFicha: EstadoDaFicha,
    viewModel: FichaDoElementoViewModel,
) {
    val candidatos = estadoDaFicha.candidatos
    // O título do capítulo onde se acrescenta, quando a lista de capítulos já foi lida.
    val capituloDoDialogo = (estadoDaFicha.capitulos as? CargaDosCapitulos.Pronta)
        ?.capitulos?.firstOrNull { it.id == dialogo.capituloId }
        ?.let { rotuloDoCapitulo(it.titulo, it.ordem) }
    when (dialogo.tipo) {
        TipoDeDialogoDaFicha.ESCOLHER_CAPITULO -> DialogoEscolherCapitulo(estadoDaFicha.capitulos, dialogo, viewModel)
        TipoDeDialogoDaFicha.ADICIONAR_ACRESCIMO -> DialogoDeTexto(
            titulo = "Adicionar acréscimo" + (capituloDoDialogo?.let { " — $it" } ?: ""),
            rotulo = "O que este capítulo revela sobre quem ${detalhe.nome} é",
            inicial = "",
            dialogo = dialogo,
            aoSalvar = viewModel::adicionarAcrescimo,
            aoCancelar = viewModel::cancelarDialogo,
        )
        TipoDeDialogoDaFicha.EDITAR_ACRESCIMO -> {
            val acrescimo = detalhe.historico_identidade.firstOrNull { it.id == dialogo.acrescimoId } ?: return
            DialogoDeTexto(
                titulo = "Editar acréscimo",
                rotulo = rotuloDoCapitulo(acrescimo.titulo_do_capitulo, acrescimo.ordem_do_capitulo),
                inicial = acrescimo.descricao,
                dialogo = dialogo,
                aoSalvar = { viewModel.salvarAcrescimo(acrescimo.id, it) },
                aoCancelar = viewModel::cancelarDialogo,
            )
        }
        TipoDeDialogoDaFicha.APAGAR_ACRESCIMO -> {
            val acrescimo = detalhe.historico_identidade.firstOrNull { it.id == dialogo.acrescimoId } ?: return
            DialogoDeConfirmacao(
                titulo = "Apagar este acréscimo?",
                texto = "O que ${rotuloDoCapitulo(acrescimo.titulo_do_capitulo, acrescimo.ordem_do_capitulo)} revelava sobre " +
                    "${detalhe.nome} será apagado da identidade. A IA pode registrar um novo ao gerar um prompt.",
                rotuloDoBotao = "Apagar",
                dialogo = dialogo,
                aoConfirmar = viewModel::confirmarApagarAcrescimo,
                aoCancelar = viewModel::cancelarDialogo,
            )
        }
        TipoDeDialogoDaFicha.ESCOLHER_PARA_MESCLAR -> DialogoEscolherParaMesclar(detalhe, candidatos, dialogo, viewModel)
        TipoDeDialogoDaFicha.CONFIRMAR_MESCLAR -> {
            val destino = (candidatos as? CargaDaLista.Pronta)?.elementos?.firstOrNull { it.id == dialogo.destinoId } ?: return
            DialogoDeConfirmacao(
                titulo = "Juntar a ${destino.nome}?",
                texto = "«${detalhe.nome}» (${rotuloDoTipo(detalhe.tipo)}) será juntado a «${destino.nome}» " +
                    "(${rotuloDoTipo(destino.tipo)}): estados, identidade e sugestões passam para o escolhido, e " +
                    "«${detalhe.nome}» (${rotuloDoTipo(detalhe.tipo)}) deixa de existir. Isso não pode ser desfeito. " +
                    "Para manter o outro como o principal, abra a ficha dele e mescle no sentido contrário.",
                rotuloDoBotao = "Juntar",
                dialogo = dialogo,
                aoConfirmar = viewModel::confirmarMesclagem,
                aoCancelar = viewModel::cancelarDialogo,
            )
        }
        TipoDeDialogoDaFicha.EDITAR_ELEMENTO -> DialogoEditarElemento(dialogo, detalhe, viewModel)
        TipoDeDialogoDaFicha.EDITAR_ESTADO -> {
            val estado = detalhe.estados.firstOrNull { it.id == dialogo.estadoId } ?: return
            DialogoDeTexto(
                titulo = "Editar aparência",
                rotulo = rotuloDoCapitulo(estado.titulo_do_capitulo, estado.ordem_do_capitulo),
                inicial = estado.descricao,
                dialogo = dialogo,
                aoSalvar = { viewModel.salvarEstado(estado.id, it) },
                aoCancelar = viewModel::cancelarDialogo,
            )
        }
        TipoDeDialogoDaFicha.ADICIONAR_ESTADO -> DialogoDeTexto(
            titulo = "Adicionar estado" + (capituloDoDialogo?.let { " — $it" } ?: " neste capítulo"),
            rotulo = "Como ${detalhe.nome} aparece aqui",
            inicial = "",
            dialogo = dialogo,
            aoSalvar = viewModel::adicionarEstado,
            aoCancelar = viewModel::cancelarDialogo,
        )
        TipoDeDialogoDaFicha.APAGAR_ESTADO -> {
            val estado = detalhe.estados.firstOrNull { it.id == dialogo.estadoId } ?: return
            DialogoDeConfirmacao(
                titulo = "Apagar este estado?",
                texto = "A aparência de ${rotuloDoCapitulo(estado.titulo_do_capitulo, estado.ordem_do_capitulo)} será apagada. " +
                    "Frames que usam esse estado perdem este participante.",
                rotuloDoBotao = "Apagar",
                dialogo = dialogo,
                aoConfirmar = viewModel::confirmarApagarEstado,
                aoCancelar = viewModel::cancelarDialogo,
            )
        }
        TipoDeDialogoDaFicha.APAGAR_ELEMENTO -> DialogoDeConfirmacao(
            titulo = "Apagar ${detalhe.nome}?",
            texto = "O elemento e todos os seus estados serão apagados. Frames que o usam perdem este participante. " +
                "Isso não pode ser desfeito.",
            rotuloDoBotao = "Apagar elemento",
            dialogo = dialogo,
            aoConfirmar = viewModel::confirmarApagarElemento,
            aoCancelar = viewModel::cancelarDialogo,
        )
    }
}

/** Nome, tipo e identidade, já com o que o servidor tem. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoEditarElemento(dialogo: DialogoDaFicha, detalhe: DetalheDoElemento, viewModel: FichaDoElementoViewModel) {
    var tipo by rememberSaveable { mutableStateOf(detalhe.tipo) }
    var nome by rememberSaveable { mutableStateOf(detalhe.nome) }
    var identidade by rememberSaveable { mutableStateOf(detalhe.descricao.orEmpty()) }
    var menuAberto by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!dialogo.salvando) viewModel.cancelarDialogo() },
        title = { Text("Editar elemento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                ExposedDropdownMenuBox(expanded = menuAberto, onExpandedChange = { menuAberto = it }) {
                    OutlinedTextField(
                        value = rotuloDoTipo(tipo),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuAberto) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                        TIPOS_DE_ELEMENTO.forEach { opcao ->
                            DropdownMenuItem(
                                text = { Text(rotuloDoTipo(opcao)) },
                                onClick = {
                                    tipo = opcao
                                    menuAberto = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = identidade,
                    onValueChange = { identidade = it },
                    label = { Text("Identidade inicial (quem ou o que é)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ErroEProgresso(dialogo)
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.salvarElemento(tipo, nome, identidade) }, enabled = !dialogo.salvando) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cancelarDialogo, enabled = !dialogo.salvando) { Text("Cancelar") }
        },
    )
}

@Composable
private fun DialogoDeTexto(
    titulo: String,
    rotulo: String,
    inicial: String,
    dialogo: DialogoDaFicha,
    aoSalvar: (String) -> Unit,
    aoCancelar: () -> Unit,
) {
    var texto by rememberSaveable { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = { if (!dialogo.salvando) aoCancelar() },
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text(rotulo) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ErroEProgresso(dialogo)
            }
        },
        confirmButton = { TextButton(onClick = { aoSalvar(texto) }, enabled = !dialogo.salvando) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = aoCancelar, enabled = !dialogo.salvando) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoDeConfirmacao(
    titulo: String,
    texto: String,
    rotuloDoBotao: String,
    dialogo: DialogoDaFicha,
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!dialogo.salvando) aoCancelar() },
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(texto)
                ErroEProgresso(dialogo)
            }
        },
        confirmButton = { TextButton(onClick = aoConfirmar, enabled = !dialogo.salvando) { Text(rotuloDoBotao) } },
        dismissButton = { TextButton(onClick = aoCancelar, enabled = !dialogo.salvando) { Text("Cancelar") } },
    )
}

@Composable
private fun ErroEProgresso(dialogo: DialogoDaFicha) {
    dialogo.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    // E36: nome repetido quase sempre é o mesmo elemento cadastrado duas vezes.
    if (dialogo.conflito) {
        Text(
            "Se são o mesmo elemento, use \"Mesclar com outro elemento\" (o ícone no alto da ficha).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (dialogo.salvando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}

/**
 * E35: a lista dos **outros** elementos do livro, com a busca já preenchida com o nome deste (o duplicado
 * quase sempre tem o mesmo nome) e o tipo de cada um à vista. Escolher pede confirmação em seguida.
 */
@Composable
private fun DialogoEscolherParaMesclar(
    detalhe: DetalheDoElemento,
    candidatos: CargaDaLista,
    dialogo: DialogoDaFicha,
    viewModel: FichaDoElementoViewModel,
) {
    var busca by rememberSaveable { mutableStateOf(detalhe.nome) }
    AlertDialog(
        onDismissRequest = viewModel::cancelarDialogo,
        title = { Text("Juntar «${detalhe.nome}» a…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Escolha o elemento que fica. Este será juntado a ele e deixará de existir.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it },
                    label = { Text("Buscar pelo nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (candidatos) {
                    CargaDaLista.Carregando ->
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                    is CargaDaLista.Erro -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(candidatos.motivo, color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::abrirMesclagem) { Text("Tentar de novo") }
                    }
                    is CargaDaLista.Pronta -> {
                        val filtrados = filtrarElementos(candidatos.elementos, busca, null)
                        if (filtrados.isEmpty()) Text("Nenhum elemento encontrado.", style = MaterialTheme.typography.bodyMedium)
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(filtrados, key = { it.id }) { candidato ->
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Text(candidato.nome, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        rotuloDoTipo(candidato.tipo),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        Button(onClick = { viewModel.escolherDestinoDaMesclagem(candidato.id) }) {
                                            Text("Juntar a este")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = viewModel::cancelarDialogo) { Text("Cancelar") } },
    )
}

/**
 * E39 e E40: a lista dos capítulos do livro, **pelo título**, com busca; escolher abre o diálogo do que se vai
 * acrescentar. Os arquivados aparecem marcados: acrescentar neles é permitido.
 */
@Composable
private fun DialogoEscolherCapitulo(
    capitulos: CargaDosCapitulos,
    dialogo: DialogoDaFicha,
    viewModel: FichaDoElementoViewModel,
) {
    var busca by rememberSaveable { mutableStateOf("") }
    val titulo = if (dialogo.depois == TipoDeDialogoDaFicha.ADICIONAR_ACRESCIMO) "Acrescentar identidade em…" else "Adicionar estado em…"
    AlertDialog(
        onDismissRequest = viewModel::cancelarDialogo,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it },
                    label = { Text("Buscar capítulo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (capitulos) {
                    CargaDosCapitulos.Carregando ->
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                    is CargaDosCapitulos.Erro -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(capitulos.motivo, color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::recarregarCapitulos) { Text("Tentar de novo") }
                    }
                    is CargaDosCapitulos.Pronta -> {
                        val filtrados = filtrarCapitulos(capitulos.capitulos, busca)
                        if (filtrados.isEmpty()) Text("Nenhum capítulo encontrado.", style = MaterialTheme.typography.bodyMedium)
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(filtrados, key = { it.id }) { capitulo ->
                                TextButton(
                                    onClick = { viewModel.escolherCapitulo(capitulo.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        rotuloParaEscolherCapitulo(capitulo.titulo, capitulo.ordem, capitulo.ignorado),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Start,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = viewModel::cancelarDialogo) { Text("Cancelar") } },
    )
}
