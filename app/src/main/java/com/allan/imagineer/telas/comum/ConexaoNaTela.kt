package com.allan.imagineer.telas.comum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.allan.imagineer.ImagineerApp

/**
 * O app consegue falar com o servidor agora? Falso sem rede **ou** em Modo offline (PL8, PL14). As telas usam isto para
 * apagar (ou esconder) o que só o servidor entrega, em vez de deixar a pessoa tocar e receber um erro (PL14d).
 */
@Composable
fun temServidor(): Boolean {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val online by aplicacao.conexao.online.collectAsState()
    return online
}
