package com.allan.imagineer.telas.livro

import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.PerfilRenderizacao

/**
 * O diálogo de editar título, autor e idioma (item 7.5a, incremento 7). Começa com os
 * valores atuais; título e autor são mandatórios, o idioma é opcional.
 */
@Composable
fun DialogoDeEdicao(
    livro: LivroDetalhe,
    estado: EstadoDaEdicao.Editando,
    aoSalvar: (titulo: String, autor: String, idioma: String) -> Unit,
    aoCancelar: () -> Unit,
    /** O perfil de renderização padrão do livro, para mostrar o nome (nulo = nenhum definido). */
    perfil: PerfilRenderizacao? = null,
    /** Abrem o seletor de capa e a escolha do perfil; nulos = a linha não aparece (a biblioteca não usa este diálogo). */
    aoDefinirCapa: (() -> Unit)? = null,
    aoEscolherPerfil: (() -> Unit)? = null,
) {
    var titulo by remember(livro.id) { mutableStateOf(livro.titulo) }
    var autor by remember(livro.id) { mutableStateOf(livro.autor.orEmpty()) }
    var idioma by remember(livro.id) { mutableStateOf(livro.idioma.orEmpty()) }

    AlertDialog(
        onDismissRequest = { if (!estado.salvando) aoCancelar() },
        title = { Text("Editar livro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoDeTexto("Título", titulo, { titulo = it }, !estado.salvando)
                CampoDeTexto("Autor", autor, { autor = it }, !estado.salvando)
                CampoDeTexto("Idioma (opcional)", idioma, { idioma = it }, !estado.salvando, maiusculas = false)
                // Capa e perfil de renderização padrão moram aqui (antes eram itens do ⋮): são coisas que se ajustam ao editar o livro.
                if (aoDefinirCapa != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Capa", modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = aoDefinirCapa, enabled = !estado.salvando) { Text("Definir capa") }
                    }
                }
                if (aoEscolherPerfil != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Perfil de renderização", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(perfil?.nome ?: "Nenhum definido")
                        }
                        OutlinedButton(onClick = aoEscolherPerfil, enabled = !estado.salvando) { Text(if (perfil == null) "Escolher" else "Trocar") }
                    }
                }
                if (estado.erro != null) {
                    Text(estado.erro, color = MaterialTheme.colorScheme.error)
                }
                if (estado.salvando) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { aoSalvar(titulo, autor, idioma) }, enabled = !estado.salvando) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = aoCancelar, enabled = !estado.salvando) { Text("Cancelar") }
        },
    )
}

@Composable
private fun CampoDeTexto(
    rotulo: String,
    valor: String,
    aoMudar: (String) -> Unit,
    habilitado: Boolean,
    maiusculas: Boolean = true,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        enabled = habilitado,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            capitalization = if (maiusculas) KeyboardCapitalization.Words else KeyboardCapitalization.None,
        ),
    )
}

/**
 * O diálogo de escolher o perfil padrão. Escolher grava na hora — a escolha é o
 * gesto, não há botão "Salvar". Só escolhe entre perfis **existentes** (os 10 de fábrica e
 * os próprios); criar perfis próprios é da tela de Perfis, nas Configurações.
 */
@Composable
fun DialogoDePerfilPadrao(
    estado: EstadoDaEscolhaDePerfil,
    perfilAtualId: Int?,
    aoEscolher: (PerfilRenderizacao?) -> Unit,
    aoFechar: () -> Unit,
) {
    if (estado == EstadoDaEscolhaDePerfil.Nenhuma) return
    val salvando = (estado as? EstadoDaEscolhaDePerfil.Lista)?.salvando == true

    AlertDialog(
        onDismissRequest = { if (!salvando) aoFechar() },
        title = { Text("Perfil de renderização padrão") },
        text = {
            when (estado) {
                EstadoDaEscolhaDePerfil.Nenhuma -> Unit
                EstadoDaEscolhaDePerfil.Carregando ->
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                is EstadoDaEscolhaDePerfil.Falhou ->
                    Text(estado.motivo, color = MaterialTheme.colorScheme.error)
                is EstadoDaEscolhaDePerfil.Lista -> Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (estado.perfis.isEmpty()) {
                        Text(
                            "Nenhum perfil disponível.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    OpcaoDePerfil("Nenhum", null, perfilAtualId == null, !estado.salvando) { aoEscolher(null) }
                    estado.perfis.forEach { perfil ->
                        OpcaoDePerfil(perfil.nome, perfil.categoria?.dica, perfil.id == perfilAtualId, !estado.salvando) { aoEscolher(perfil) }
                    }
                    if (estado.erro != null) {
                        Text(estado.erro, color = MaterialTheme.colorScheme.error)
                    }
                    if (estado.salvando) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = aoFechar, enabled = !salvando) { Text("Fechar") } },
    )
}

@Composable
private fun OpcaoDePerfil(nome: String, dica: String?, marcado: Boolean, habilitado: Boolean, aoEscolher: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = habilitado, onClick = aoEscolher)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = marcado, onClick = null, enabled = habilitado)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(nome)
            if (dica != null) {
                Text(dica, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
