package com.allan.imagineer.telas.menu

import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.CategoriaDeEstilo
import com.allan.imagineer.rede.PerfilEdicao
import com.allan.imagineer.rede.PerfilRenderizacao
import com.allan.imagineer.telas.comum.BotaoFlutuante
import com.allan.imagineer.telas.comum.BotaoDeIcone

/** O aviso que acompanha a escolha da categoria (BT6): o bloco fixo só rende quando o texto do perfil combina com ela. */
const val AVISO_DA_CATEGORIA =
    "A categoria escolhe uma técnica fixa que o servidor cola ao fim de cada prompt. O estilo, a iluminação e a paleta abaixo " +
        "devem combinar com ela: se o texto disser \"óleo\" e a categoria for anime, o modelo pode ignorar a categoria."

/** A explicação no alto da lista: o caminho simples (escolher um de fábrica) e o avançado (criar um próprio). */
const val EXPLICACAO_DOS_PERFIS =
    "Os perfis de fábrica já trazem a técnica do estilo pronta: no livro, é só escolher um deles. " +
        "Crie um perfil próprio apenas se precisar de algo sob medida."

/** A linha de resumo de um perfil na lista. */
fun resumoDoPerfil(perfil: PerfilRenderizacao): String =
    listOfNotNull(perfil.categoria?.rotulo, perfil.estilo?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "Sem categoria nem estilo" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaPerfis(aoVoltar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: PerfisViewModel = viewModel(
        factory = viewModelFactory { initializer { PerfisViewModel(aplicacao.repositorioDePerfis) } },
    )
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(viewModel) { viewModel.carregar() }
    val contexto = LocalContext.current
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let { android.widget.Toast.makeText(contexto, it, android.widget.Toast.LENGTH_LONG).show(); viewModel.avisoLido() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfis de renderização") },
                navigationIcon = { BotaoDeIcone(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", aoVoltar, cor = LocalContentColor.current) },
            )
        },
        floatingActionButton = {
            // Só o "+", como na Biblioteca (PB5); "Criar novo perfil" fica na dica e para leitor de tela.
            BotaoFlutuante(Icons.Filled.Add, "Criar novo perfil", viewModel::novo)
        },
    ) { margens ->
        Box(modifier = Modifier.padding(margens).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val carga = estado.carga) {
                CargaDosPerfis.Carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is CargaDosPerfis.Erro -> Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(carga.motivo, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = viewModel::tentarDeNovo, modifier = Modifier.padding(top = 12.dp)) { Text("Tentar de novo") }
                }
                is CargaDosPerfis.Pronta -> if (carga.perfis.isEmpty()) {
                    Text(
                        "Nenhum perfil ainda. Toque em \"Criar novo perfil\" para criar o primeiro.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(EXPLICACAO_DOS_PERFIS, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(carga.perfis, key = { it.id }) { perfil ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.ver(perfil) }) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(perfil.nome, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                                        // Só o perfil próprio é sinalizado: os de fábrica são a regra e não precisam de marca.
                                        if (!perfil.de_fabrica) {
                                            Icon(
                                                Icons.Filled.Person,
                                                contentDescription = "Perfil próprio",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        resumoDoPerfil(perfil),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    estado.detalhe?.let { perfil ->
        DetalheDoPerfil(
            perfil = perfil,
            aoCopiar = { viewModel.criarAPartirDe(perfil) },
            aoEditar = { viewModel.editar(perfil) },
            aoFechar = viewModel::fecharDetalhe,
        )
    }
    estado.formulario?.let { formulario ->
        FormularioDoPerfilNaTela(
            formulario = formulario,
            aoMudar = viewModel::mudarFormulario,
            aoSalvar = viewModel::salvar,
            aoApagar = (carregadosPara(estado, formulario.perfilId))?.let { perfil -> { viewModel.pedirParaApagar(perfil) } },
            aoFechar = viewModel::fecharFormulario,
        )
    }
    estado.apagando?.let { perfil ->
        AlertDialog(
            onDismissRequest = viewModel::cancelarApagar,
            title = { Text("Apagar o perfil?") },
            text = {
                Text(
                    "“${perfil.nome}” some da lista. Os livros que o usavam ficam sem perfil padrão, e os prompts já gerados continuam no histórico.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmarApagar) { Text("Apagar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelarApagar) { Text("Cancelar") } },
        )
    }
}

/** O perfil em detalhes (PF4): tudo o que ele leva ao prompt, inclusive o bloco técnico em inglês. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetalheDoPerfil(perfil: PerfilRenderizacao, aoCopiar: () -> Unit, aoEditar: () -> Unit, aoFechar: () -> Unit) {
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(perfil.nome, style = MaterialTheme.typography.titleLarge)
            Text(
                if (perfil.de_fabrica) "Perfil de fábrica: já vem pronto e não pode ser editado." else "Perfil próprio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CampoDoDetalhe("Categoria", perfil.categoria?.rotulo ?: "Sem categoria")
            CampoDoDetalhe("Estilo", perfil.estilo)
            CampoDoDetalhe("Iluminação", perfil.iluminacao)
            CampoDoDetalhe("Paleta", perfil.paleta)
            CampoDoDetalhe("Artista de referência", perfil.artista_referencia)
            CampoDoDetalhe("Formato", perfil.formato)
            CampoDoDetalhe(
                "Texto técnico colado ao fim de cada prompt",
                perfil.bloco_tecnico ?: "Sem categoria, o prompt não ganha o bloco técnico.",
            )
            Button(onClick = aoCopiar, modifier = Modifier.fillMaxWidth()) { Text("Criar a partir deste") }
            if (!perfil.de_fabrica) {
                // Alternativa da principal ("Criar a partir deste"), logo abaixo dela: botão de contorno (PB1).
                OutlinedButton(onClick = aoEditar, modifier = Modifier.fillMaxWidth()) { Text("Editar") }
            }
        }
    }
}

@Composable
private fun CampoDoDetalhe(rotulo: String, valor: String?) {
    if (valor.isNullOrBlank()) return
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun carregadosPara(estado: EstadoDosPerfis, perfilId: Int?): PerfilRenderizacao? =
    perfilId?.let { id -> (estado.carga as? CargaDosPerfis.Pronta)?.perfis?.firstOrNull { it.id == id } }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FormularioDoPerfilNaTela(
    formulario: FormularioDoPerfil,
    aoMudar: (PerfilEdicao) -> Unit,
    aoSalvar: () -> Unit,
    aoApagar: (() -> Unit)?,
    aoFechar: () -> Unit,
) {
    val e = formulario.edicao
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (formulario.perfilId == null) "Novo perfil" else "Editar perfil", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(value = e.nome, onValueChange = { aoMudar(e.copy(nome = it)) }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("Categoria de estilo", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = e.categoria == null, onClick = { aoMudar(e.copy(categoria = null)) }, label = { Text("Sem categoria") })
                CategoriaDeEstilo.entries.forEach { c ->
                    FilterChip(selected = e.categoria == c, onClick = { aoMudar(e.copy(categoria = c)) }, label = { Text(c.rotulo) })
                }
            }
            Text(
                e.categoria?.dica ?: "Sem categoria, o prompt não ganha o bloco técnico fixo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(AVISO_DA_CATEGORIA, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(value = e.estilo, onValueChange = { aoMudar(e.copy(estilo = it)) }, label = { Text("Estilo") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = e.iluminacao, onValueChange = { aoMudar(e.copy(iluminacao = it)) }, label = { Text("Iluminação") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = e.paleta, onValueChange = { aoMudar(e.copy(paleta = it)) }, label = { Text("Paleta") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = e.artistaDeReferencia, onValueChange = { aoMudar(e.copy(artistaDeReferencia = it)) }, label = { Text("Artista de referência (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = e.formato, onValueChange = { aoMudar(e.copy(formato = it)) }, label = { Text("Formato (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            formulario.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = aoSalvar, enabled = !formulario.salvando && podeSalvarOPerfil(e), modifier = Modifier.fillMaxWidth()) {
                Text(if (formulario.salvando) "Salvando…" else "Salvar")
            }
            aoApagar?.let {
                TextButton(onClick = it) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("  Apagar perfil", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
