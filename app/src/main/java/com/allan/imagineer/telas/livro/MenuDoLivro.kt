package com.allan.imagineer.telas.livro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * O menu ⋮ **de um livro**, o mesmo na **biblioteca** (no cartão e sobre a capa) e dentro do **livro** (barra de cima). Cada item só
 * aparece se a tela der a ação dele: na biblioteca ficam os que não dependem de abrir o livro (**Definir capa** e **Apagar livro**);
 * dentro do livro entram também **Editar**, **Perfil de Renderização** e **Arquivo**. As configurações do aplicativo (perfis de renderização)
 * não estão aqui: moram no menu da biblioteca.
 *
 * @param sobreACapa o ⋮ desenhado sobre a imagem de uma capa: um círculo escuro translúcido, com o ícone branco.
 */
@Composable
fun MenuDoLivro(
    modifier: Modifier = Modifier,
    sobreACapa: Boolean = false,
    aoEditar: (() -> Unit)? = null,
    aoDefinirCapa: (() -> Unit)? = null,
    aoEscolherPerfilPadrao: (() -> Unit)? = null,
    aoAbrirArquivo: (() -> Unit)? = null,
    aoAbrirLixeira: (() -> Unit)? = null,
    aoPesquisar: (() -> Unit)? = null,
    /** PL3: o item de ler offline (nulo = sem o item) e o texto dele conforme o estado do download. */
    aoAbrirOffline: (() -> Unit)? = null,
    rotuloDoOffline: String = "Baixar para ler offline",
    aoApagar: (() -> Unit)? = null,
) {
    var aberto by remember { mutableStateOf(false) }
    Box(modifier) {
        if (sobreACapa) {
            Surface(color = Color.Black.copy(alpha = 0.35f), shape = CircleShape, modifier = Modifier.padding(4.dp).size(28.dp)) {
                IconButton(onClick = { aberto = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            IconButton(onClick = { aberto = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções") }
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            aoPesquisar?.let { DropdownMenuItem(text = { Text("Pesquisar no livro") }, onClick = { aberto = false; it() }) }
            aoAbrirOffline?.let { DropdownMenuItem(text = { Text(rotuloDoOffline) }, onClick = { aberto = false; it() }) }
            aoEditar?.let { DropdownMenuItem(text = { Text("Editar") }, onClick = { aberto = false; it() }) }
            aoDefinirCapa?.let { DropdownMenuItem(text = { Text("Definir capa") }, onClick = { aberto = false; it() }) }
            aoEscolherPerfilPadrao?.let { DropdownMenuItem(text = { Text("Perfil de Renderização") }, onClick = { aberto = false; it() }) }
            aoAbrirArquivo?.let { DropdownMenuItem(text = { Text("Arquivo") }, onClick = { aberto = false; it() }) }
            aoAbrirLixeira?.let { DropdownMenuItem(text = { Text("Lixeira") }, onClick = { aberto = false; it() }) }
            aoApagar?.let { DropdownMenuItem(text = { Text("Apagar livro") }, onClick = { aberto = false; it() }) }
        }
    }
}
