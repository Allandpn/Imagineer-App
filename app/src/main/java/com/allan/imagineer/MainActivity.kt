package com.allan.imagineer

import com.allan.imagineer.ui.theme.DestaqueEscolhido
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.allan.imagineer.navegacao.GrafoDeNavegacao
import com.allan.imagineer.ui.theme.ImagineerTheme

/**
 * A única Activity do app. Toda a navegação entre telas acontece dentro do
 * [GrafoDeNavegacao], em Compose — não há uma Activity por tela.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val aplicacao = applicationContext as ImagineerApp
            val nomeDoDestaque by aplicacao.armazenamento.corDeDestaque.collectAsState(initial = null)
            ImagineerTheme(destaque = DestaqueEscolhido.deNome(nomeDoDestaque)) {
                GrafoDeNavegacao()
            }
        }
    }
}
