package com.allan.imagineer.telas.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Os itens da gaveta da biblioteca (MN2), na ordem em que aparecem. */
enum class ItemDoMenu(val rotulo: String, val icone: ImageVector) {
    PERFIL("Perfil", Icons.Filled.AccountCircle),
    CONFIGURACOES("Configurações", Icons.Filled.Settings),
    LIXEIRA("Lixeira", Icons.Filled.Delete),
    CUSTOS("Custos", Icons.Filled.Payments),
}

/**
 * A gaveta lateral da biblioteca (MN1, MN2): o que antes ficava solto na barra de cima — perfis, lixeira, configuração — mora aqui,
 * junto do Perfil e dos Custos. Escolher um item **fecha** a gaveta e abre a tela dele.
 */
@Composable
fun GavetaDaBiblioteca(aoEscolher: (ItemDoMenu) -> Unit) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Imagineer",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
            )
            Text(
                "Leitura com imagens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
            ItemDoMenu.entries.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.rotulo) },
                    icon = { Icon(item.icone, contentDescription = null) },
                    selected = false,
                    onClick = { aoEscolher(item) },
                )
            }
        }
    }
}
