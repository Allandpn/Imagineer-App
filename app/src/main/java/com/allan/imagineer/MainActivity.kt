package com.allan.imagineer

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
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
                // Com a tela de ponta a ponta o Android não encolhe mais o app para o teclado: o recuo do teclado é feito aqui, na raiz,
                // para que nada que se digita fique escondido atrás dele.
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().imePadding()) { GrafoDeNavegacao() }
            }
        }
    }
}
