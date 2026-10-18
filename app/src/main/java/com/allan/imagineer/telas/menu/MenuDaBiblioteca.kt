package com.allan.imagineer.telas.menu

import androidx.compose.material.icons.filled.Insights
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Os itens da gaveta da biblioteca (MN2), na ordem em que aparecem. */
enum class ItemDoMenu(val rotulo: String, val icone: ImageVector, val valeSemServidor: Boolean = false) {
    PERFIL("Perfil", Icons.Filled.AccountCircle),
    CONFIGURACOES("Configurações", Icons.Filled.Settings, valeSemServidor = true),
    LIXEIRA("Lixeira", Icons.Filled.Delete),
    CUSTOS("Custos", Icons.Filled.Payments),
    ESTATISTICAS("Estatísticas", Icons.Filled.Insights),
}

/**
 * A gaveta lateral da biblioteca (MN1, MN2): o que antes ficava solto na barra de cima — perfis, lixeira, configuração — mora aqui,
 * junto do Perfil e dos Custos. Escolher um item **fecha** a gaveta e abre a tela dele.
 */
@Composable
fun GavetaDaBiblioteca(
    modoOffline: Boolean = false,
    aoAlternarModoOffline: () -> Unit = {},
    aoEscolher: (ItemDoMenu) -> Unit,
) {
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
                // PL14c: em modo offline, o que só o servidor entrega fica apagado e não faz nada.
                val vale = !modoOffline || item.valeSemServidor
                NavigationDrawerItem(
                    label = { Text(item.rotulo) },
                    icon = { Icon(item.icone, contentDescription = null) },
                    selected = false,
                    onClick = { if (vale) aoEscolher(item) },
                    modifier = Modifier.alpha(if (vale) 1f else 0.38f),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            // PL14a: o interruptor manual. Ligado, desliga sozinho quando o servidor volta; tocar com ele ligado sonda o servidor agora.
            NavigationDrawerItem(
                label = {
                    Column {
                        Text("Modo offline")
                        Text(
                            if (modoOffline) "Só os livros baixados. Toque para conferir o servidor." else "Ler os livros baixados, sem esperar o servidor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                icon = { Icon(Icons.Filled.CloudOff, contentDescription = null) },
                badge = { androidx.compose.material3.Switch(checked = modoOffline, onCheckedChange = { aoAlternarModoOffline() }) },
                selected = modoOffline,
                onClick = aoAlternarModoOffline,
            )
        }
    }
}
