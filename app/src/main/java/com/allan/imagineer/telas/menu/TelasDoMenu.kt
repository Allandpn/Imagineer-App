package com.allan.imagineer.telas.menu

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
import kotlinx.coroutines.launch

/** A moldura das telas do menu: barra com Voltar, conteúdo centrado (largura máxima de tablet) e rolável. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelaDoMenu(titulo: String, aoVoltar: () -> Unit, conteudo: @Composable () -> Unit) {
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
    val atual = ModoDaBiblioteca.deTexto(modo)

    TelaDoMenu("Configurações", aoVoltar) {
        LinhaDeConfiguracao("Servidor", url ?: "Não configurado", aoAbrirServidor)
        HorizontalDivider()
        LinhaDeConfiguracao("Perfis de renderização", "O estilo visual das imagens de cada livro", aoAbrirPerfisDeRenderizacao)
        HorizontalDivider()
        LinhaDeConfiguracao("Modelos de IA", "Extração, prompt e imagem", aoAbrirModelos)
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

/** Os modelos de IA (MN4): só a interface — os três papéis, sem escolha ainda. */
@Composable
fun TelaModelos(aoVoltar: () -> Unit) {
    TelaDoMenu("Modelos de IA", aoVoltar) {
        listOf(
            "Extração e análise" to "Lê o capítulo e sugere elementos e cenas. Barato e rápido.",
            "Prompt de imagem" to "Escreve o prompt de cada imagem a partir do texto. É o passo mais caro.",
            "Imagem" to "Gera a imagem do prompt (OpenRouter, fal.ai ou Replicate), com preço por imagem.",
        ).forEach { (nome, descricao) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Modelo atual: —", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        AvisoDeEmBreve("Escolher o modelo, ver o preço por imagem e se é moderado vem em breve. Por enquanto vale o que está configurado no servidor.")
    }
}

// --------------------------------------------------------------------------- //
// MN6 — Custos
// --------------------------------------------------------------------------- //

/** Os custos (MN6): só a interface — cartões com "—" até a soma dos três provedores existir no servidor. */
@Composable
fun TelaCustos(aoVoltar: () -> Unit) {
    TelaDoMenu("Custos", aoVoltar) {
        CartaoDeCusto("Este mês", listOf("Total" to "—"))
        CartaoDeCusto("Por provedor", listOf("OpenRouter" to "—", "fal.ai" to "—", "Replicate" to "—"))
        CartaoDeCusto("Por livro", listOf("Nenhum gasto contado ainda" to ""))
        AvisoDeEmBreve("Você paga em três lugares; o total junto, por mês, provedor e livro, vem em breve.")
    }
}

@Composable
private fun CartaoDeCusto(titulo: String, linhas: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            linhas.forEach { (nome, valor) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(nome, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
                    Text(valor, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
