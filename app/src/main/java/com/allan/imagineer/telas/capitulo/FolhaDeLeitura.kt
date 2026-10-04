package com.allan.imagineer.telas.capitulo

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.allan.imagineer.dados.FamiliaDeLeitura
import com.allan.imagineer.dados.MargemDeLeitura
import com.allan.imagineer.dados.PreferenciasDeLeitura
import com.allan.imagineer.dados.TemaDeLeitura
import com.allan.imagineer.dados.esquemaDeLeitura

// A folha "Aa" do capítulo (RL1 a RL8): cada mudança vale na hora e fica guardada.

/** A família de letra do Compose para a escolha (RL3). */
fun familiaDaLetra(familia: FamiliaDeLeitura): FontFamily = when (familia) {
    FamiliaDeLeitura.PADRAO -> FontFamily.Default
    FamiliaDeLeitura.COM_SERIFA -> FontFamily.Serif
    FamiliaDeLeitura.MONOESPACADA -> FontFamily.Monospace
}

/** Troca as cores da área do texto pelas do tema de leitura (RL7); no tema "Do aplicativo", não mexe em nada. */
@Composable
fun ComTemaDeLeitura(tema: TemaDeLeitura, conteudo: @Composable () -> Unit) {
    if (tema == TemaDeLeitura.DO_APLICATIVO) {
        conteudo()
        return
    }
    val esquema = esquemaDeLeitura(MaterialTheme.colorScheme, tema)
    MaterialTheme(colorScheme = esquema, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Surface(color = esquema.background, contentColor = esquema.onBackground, modifier = Modifier.fillMaxWidth()) { conteudo() }
    }
}

/**
 * Mantém a tela acesa e aplica o brilho do app **enquanto o capítulo está na tela** (RL8); ao sair, tudo volta ao do aparelho.
 */
@Composable
fun EfeitosDaLeitura(telaAcesa: Boolean, brilho: Float?) {
    val janela = (LocalView.current.context as? Activity)?.window ?: return
    DisposableEffect(telaAcesa) {
        if (telaAcesa) janela.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { janela.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(brilho) {
        val parametros = janela.attributes
        parametros.screenBrightness = brilho ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        janela.attributes = parametros
        onDispose {
            val voltar = janela.attributes
            voltar.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            janela.attributes = voltar
        }
    }
}

/** A folha de baixo com os controles de leitura. [aoMudar] recebe a preferência nova a cada toque. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FolhaDeLeitura(preferencias: PreferenciasDeLeitura, aoMudar: (PreferenciasDeLeitura) -> Unit, aoFechar: () -> Unit) {
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Leitura", style = MaterialTheme.typography.titleLarge)

            Linha("Letra") {
                TextButton(onClick = { aoMudar(preferencias.menosLetra()) }, enabled = preferencias.tamanho > PreferenciasDeLeitura.TAMANHO_MINIMO) { Text("A−") }
                Text("${preferencias.tamanho}%", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { aoMudar(preferencias.maisLetra()) }, enabled = preferencias.tamanho < PreferenciasDeLeitura.TAMANHO_MAXIMO) { Text("A+") }
            }
            Linha("Família") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FamiliaDeLeitura.entries.forEach { f ->
                        FilterChip(selected = preferencias.familia == f, onClick = { aoMudar(preferencias.copy(familia = f)) }, label = { Text(f.rotulo, fontFamily = familiaDaLetra(f)) })
                    }
                }
            }
            Linha("Entrelinha") {
                TextButton(onClick = { aoMudar(preferencias.menosEntrelinha()) }, enabled = preferencias.entrelinha > PreferenciasDeLeitura.ENTRELINHA_MINIMA) { Text("−") }
                Text(String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", preferencias.entrelinha), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { aoMudar(preferencias.maisEntrelinha()) }, enabled = preferencias.entrelinha < PreferenciasDeLeitura.ENTRELINHA_MAXIMA) { Text("+") }
            }
            Linha("Margens") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MargemDeLeitura.entries.forEach { m ->
                        FilterChip(selected = preferencias.margem == m, onClick = { aoMudar(preferencias.copy(margem = m)) }, label = { Text(m.rotulo) })
                    }
                }
            }
            Linha("Justificar o texto") {
                Switch(checked = preferencias.justificado, onCheckedChange = { aoMudar(preferencias.copy(justificado = it)) })
            }
            Linha("Tema") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TemaDeLeitura.entries.forEach { tema ->
                        FilterChip(selected = preferencias.tema == tema, onClick = { aoMudar(preferencias.copy(tema = tema)) }, label = { Text(tema.rotulo) })
                    }
                }
            }
            Linha("Manter a tela acesa") {
                Switch(checked = preferencias.telaAcesa, onCheckedChange = { aoMudar(preferencias.copy(telaAcesa = it)) })
            }
            Linha("Brilho do aplicativo") {
                Switch(checked = preferencias.brilho != null, onCheckedChange = { aoMudar(preferencias.copy(brilho = if (it) 0.6f else null)) })
            }
            preferencias.brilho?.let { atual ->
                Slider(value = atual, onValueChange = { aoMudar(preferencias.copy(brilho = it)) }, valueRange = PreferenciasDeLeitura.BRILHO_MINIMO..1f)
            } ?: Text("Usando o brilho do aparelho.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            TextButton(onClick = { aoMudar(PreferenciasDeLeitura()) }) { Text("Voltar ao padrão") }
        }
    }
}

@Composable
private fun Linha(titulo: String, conteudo: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { conteudo() }
    }
}
