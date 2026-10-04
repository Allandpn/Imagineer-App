package com.allan.imagineer.telas.comum

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable

/**
 * O lugar dos avisos (Snackbar) das telas, com um gesto a mais: **deslizar para a direita fecha** o aviso (por exemplo o "Desfazer" de
 * um arquivamento), sem esperar os segundos dele passarem. Só o deslize para a direita fecha; para a esquerda o aviso volta ao lugar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostDeAvisos(estado: SnackbarHostState) {
    SnackbarHost(estado) { aviso ->
        val deslize = rememberSwipeToDismissBoxState(
            confirmValueChange = { valor ->
                if (valor == SwipeToDismissBoxValue.StartToEnd) {
                    aviso.dismiss()
                    true
                } else {
                    false
                }
            },
        )
        SwipeToDismissBox(
            state = deslize,
            backgroundContent = {},
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = false,
        ) {
            // AJ5: o texto clicável do aviso (Desfazer, Abrir) fica na cor do aviso, não na cor configurável, que pode ficar ilegível.
            Snackbar(aviso, actionColor = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}
