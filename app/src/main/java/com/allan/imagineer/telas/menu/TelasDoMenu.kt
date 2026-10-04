package com.allan.imagineer.telas.menu

import com.allan.imagineer.rede.GastoAgrupado
import com.allan.imagineer.rede.CustosDoMes
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import com.allan.imagineer.ui.theme.DestaqueEscolhido
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allan.imagineer.ImagineerApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/** A moldura das telas do menu: barra com Voltar, conteúdo centrado (largura máxima de tablet) e rolável. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TelaDoMenu(titulo: String, aoVoltar: () -> Unit, conteudo: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { margens ->
        Box(modifier = Modifier.padding(margens).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { conteudo() }
        }
    }
}

/** Um aviso discreto de que a parte ainda não funciona (as telas do menu nascem como interface; a lógica vem depois). */
@Composable
private fun AvisoDeEmBreve(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// --------------------------------------------------------------------------- //
// MN3 — Perfil
// --------------------------------------------------------------------------- //

/** O perfil da conta (MN3): só a interface. Nome e e-mail vazios; entrar/criar conta desligado. */
@Composable
fun TelaPerfil(aoVoltar: () -> Unit) {
    TelaDoMenu("Perfil", aoVoltar) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("?", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("Nome") }, enabled = false, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("E-mail") }, enabled = false, modifier = Modifier.fillMaxWidth())
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Entrar ou criar conta") }
        AvisoDeEmBreve("A conta chega em breve. Por enquanto o Imagineer vale para este aparelho e para o servidor configurado.")
    }
}

// --------------------------------------------------------------------------- //
// MN4 — Configurações
// --------------------------------------------------------------------------- //

/**
 * As configurações (MN4): **Servidor** e **Perfis de renderização** levam às telas que já existem; **Modelos de IA** é só interface; a
 * **exibição da biblioteca** (capas ou lista) funciona e fica guardada no aparelho.
 */
@Composable
fun TelaConfiguracoes(
    aoVoltar: () -> Unit,
    aoAbrirServidor: () -> Unit,
    aoAbrirPerfisDeRenderizacao: () -> Unit,
    aoAbrirModelos: () -> Unit,
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val escopo = rememberCoroutineScope()
    val url by aplicacao.armazenamento.urlDoServidor.collectAsState(initial = null)
    val modo by aplicacao.armazenamento.modoDaBiblioteca.collectAsState(initial = null)
    val destaqueGuardado by aplicacao.armazenamento.corDeDestaque.collectAsState(initial = null)
    val atual = ModoDaBiblioteca.deTexto(modo)

    TelaDoMenu("Configurações", aoVoltar) {
        LinhaDeConfiguracao("Servidor", url ?: "Não configurado", aoAbrirServidor)
        HorizontalDivider()
        LinhaDeConfiguracao("Perfis de renderização", "O estilo visual das imagens de cada livro", aoAbrirPerfisDeRenderizacao)
        HorizontalDivider()
        LinhaDeConfiguracao("Modelos de IA", "Extração, prompt e imagem", aoAbrirModelos)
        HorizontalDivider()
        Text("Cor de destaque", style = MaterialTheme.typography.titleMedium)
        val escolhida = DestaqueEscolhido.deNome(destaqueGuardado)
        val escuro = isSystemInDarkTheme()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            DestaqueEscolhido.entries.forEach { opcao ->
                AmostraDeCor(
                    cor = opcao.cor(escuro),
                    nome = opcao.rotulo,
                    escolhida = opcao == escolhida,
                    aoEscolher = { escopo.launch { aplicacao.armazenamento.salvarCorDeDestaque(opcao.name) } },
                )
            }
        }
        HorizontalDivider()
        Text("Exibição da biblioteca", style = MaterialTheme.typography.titleMedium)
        ModoDaBiblioteca.entries.forEach { opcao ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = atual == opcao, role = Role.RadioButton) {
                        escopo.launch { aplicacao.armazenamento.salvarModoDaBiblioteca(opcao.name) }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = atual == opcao, onClick = null)
                Text(opcao.rotulo, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Uma amostra da cor de destaque: um círculo da cor; o escolhido ganha um anel e um ✓. */
@Composable
private fun AmostraDeCor(cor: androidx.compose.ui.graphics.Color, nome: String, escolhida: Boolean, aoEscolher: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(cor, CircleShape)
            .then(if (escolhida) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClickLabel = nome, onClick = aoEscolher)
            .semantics { contentDescription = if (escolhida) "$nome (escolhida)" else nome },
        contentAlignment = Alignment.Center,
    ) {
        if (escolhida) Icon(Icons.Filled.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f))
    }
}

@Composable
private fun LinhaDeConfiguracao(titulo: String, descricao: String, aoTocar: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = aoTocar).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/**
 * Os modelos de IA (MN4, MT1): o modelo de cada tarefa de **texto** — extração, prompt, perfil, suavização e **tradução** —, com a
 * troca feita aqui mesmo, a partir da lista do servidor (do mais barato ao mais caro, com busca). A escolha do modelo de **imagem**
 * (preço por imagem, moderado ou não) vem no item seguinte.
 */
@Composable
fun TelaModelos(aoVoltar: () -> Unit, aoEscolherModeloDeImagem: () -> Unit = {}) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: ModelosDeIaViewModel = viewModel(
        factory = viewModelFactory { initializer { ModelosDeIaViewModel(aplicacao.repositorioDeModelos) } },
    )
    val estado by viewModel.estado.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    TelaDoMenu("Modelos de IA", aoVoltar) {
        when (val carga = estado.carga) {
            CargaDosModelos.Carregando -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
            is CargaDosModelos.Erro -> {
                Text(carga.motivo, color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::carregar) { Text("Tentar de novo") }
            }
            is CargaDosModelos.Pronta -> {
                estado.recado?.let { Text(it, color = if (estado.recadoEhErro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary) }
                TarefaDeTexto.entries.forEach { tarefa ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.abrirEscolha(tarefa) }) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tarefa.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(tarefa.descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(descricaoDoModeloAtual(carga.configuracao, tarefa), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Imagem", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Gera a imagem do prompt (OpenRouter, fal.ai ou Replicate).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Modelo atual: ${carga.configuracao.modelo_imagem ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                        // MI6: o preço por imagem, a moderação e o teste de resolução moram na tela do catálogo.
                        TextButton(onClick = aoEscolherModeloDeImagem) { Text("Escolher o modelo de imagem") }
                    }
                }
                estado.escolhendo?.let { tarefa ->
                    DialogoDeEscolhaDeModelo(tarefa, carga.configuracao, estado, viewModel)
                }
            }
        }
    }
}

/** A escolha do modelo de uma tarefa: busca, a lista (nome, id, preço de saída, "moderado") e, nas opcionais, "Usar o padrão". */
@Composable
private fun DialogoDeEscolhaDeModelo(tarefa: TarefaDeTexto, config: com.allan.imagineer.rede.ConfiguracaoAtual, estado: EstadoDosModelos, viewModel: ModelosDeIaViewModel) {
    var busca by rememberSaveable { mutableStateOf("") }
    val atual = modeloEscolhido(config, tarefa)
    AlertDialog(
        onDismissRequest = viewModel::fecharEscolha,
        title = { Text(tarefa.titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = busca, onValueChange = { busca = it }, label = { Text("Buscar modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                estado.recado?.takeIf { estado.recadoEhErro }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                when (val lista = estado.lista) {
                    ListaDeModelos.Nao, ListaDeModelos.Carregando -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                    is ListaDeModelos.Erro -> {
                        Text(lista.motivo, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = viewModel::carregarLista) { Text("Tentar de novo") }
                    }
                    is ListaDeModelos.Pronta -> {
                        val modelos = filtrarModelos(lista.modelos, busca)
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(modelos, key = { it.id }) { modelo ->
                                Column(
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = !estado.salvando) { viewModel.escolher(tarefa, modelo.id) }.padding(vertical = 8.dp),
                                ) {
                                    Text(modelo.nome, style = MaterialTheme.typography.bodyMedium, fontWeight = if (modelo.id == atual) FontWeight.Bold else FontWeight.Normal)
                                    Text(
                                        listOfNotNull(modelo.id, precoDoModelo(modelo), if (modelo.moderado) "moderado" else null, if (modelo.id == atual) "em uso" else null).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                        if (modelos.isEmpty()) Text("Nenhum modelo combina com a busca.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (estado.salvando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            if (tarefa.opcional) TextButton(onClick = { viewModel.escolher(tarefa, null) }, enabled = !estado.salvando && atual != null) { Text("Usar o padrão") }
        },
        dismissButton = { TextButton(onClick = viewModel::fecharEscolha) { Text("Fechar") } },
    )
}

// --------------------------------------------------------------------------- //
// MN6 — Custos
// --------------------------------------------------------------------------- //

/**
 * Os custos de IA (MN6, CU5): o mês escolhido (setas para os meses com gasto), o total — com "~" quando parte dele é estimada — e o
 * gasto por provedor, por operação, por livro e por modelo. Tudo em dólares. Os valores do fal.ai e do Replicate são **estimados**
 * por uma tabela de preços (eles não informam o custo).
 */
@Composable
fun TelaCustos(aoVoltar: () -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: CustosViewModel = viewModel(
        factory = viewModelFactory { initializer { CustosViewModel(aplicacao.repositorioDeCustos) } },
    )
    val estado by viewModel.estado.collectAsState()
    // Relê ao abrir (e ao voltar para a tela): o gasto muda a cada imagem gerada.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    TelaDoMenu("Custos", aoVoltar) {
        when (val atual = estado) {
            EstadoDosCustos.Carregando -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
            EstadoDosCustos.Indisponivel -> AvisoDeEmBreve("Os custos não estão disponíveis neste servidor. Atualize o servidor para ver os gastos.")
            is EstadoDosCustos.Erro -> {
                Text(atual.motivo, color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.carregar(null) }) { Text("Tentar de novo") }
            }
            is EstadoDosCustos.Pronto -> ConteudoDosCustos(atual.dados, viewModel::mesAnterior, viewModel::mesSeguinte)
        }
    }
}

@Composable
private fun ConteudoDosCustos(dados: CustosDoMes, aoVoltarUmMes: () -> Unit, aoAvancarUmMes: () -> Unit) {
    val estimado = (dados.estimado.toDoubleOrNull() ?: 0.0) > 0.0
    // O mês, com as setas.
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = aoVoltarUmMes, enabled = temMesAnterior(dados)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mês anterior")
        }
        Text(nomeDoMes(dados.mes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = aoAvancarUmMes, enabled = temMesSeguinte(dados)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo mês")
        }
    }
    // O total.
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Total do mês", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatarDolar(dados.total, estimado), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${dados.chamadas} chamadas de IA" + if (dados.sem_custo > 0) " · ${dados.sem_custo} sem custo conhecido" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (estimado) {
                Text(
                    "Inclui ${formatarDolar(dados.estimado)} estimados pela tabela de preços (o fal.ai e o Replicate não informam o custo).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (dados.chamadas == 0) {
        AvisoDeEmBreve("Nenhuma chamada de IA neste mês.")
        return
    }
    CartaoDeCusto("Por provedor", dados.por_provedor.map { it.copy(nome = nomeDoProvedor(it.nome)) })
    CartaoDeCusto("Por tipo de chamada", dados.por_operacao.map { it.copy(nome = nomeDaOperacao(it.nome)) })
    CartaoDeCusto("Por livro", dados.por_livro)
    CartaoDeCusto("Por modelo", dados.por_modelo.take(8))
    AvisoDeEmBreve("Valores em dólares. As chamadas sem custo conhecido não entram na soma.")
}

@Composable
private fun CartaoDeCusto(titulo: String, linhas: List<GastoAgrupado>) {
    if (linhas.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            linhas.forEach { linha ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(linha.nome, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${linha.chamadas} chamadas" + if (linha.sem_custo > 0) " · ${linha.sem_custo} sem custo" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(formatarDolar(linha.total), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
