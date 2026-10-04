package com.allan.imagineer.telas.elementos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.Destaque
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.capitulo.corDeFundoDoDestaque

/**
 * A seção **"Passagens destacadas"** da ficha de um elemento (RL11): os trechos que a pessoa ligou a ele, com a nota de cada um. Tocar
 * leva ao capítulo naquele ponto. Sem passagens (ou sem servidor), a seção não aparece: é um extra, não faz falta na ficha.
 */
@Composable
fun SecaoDePassagensDaFicha(elementoId: Int, aoAbrir: (capituloId: Int, posicao: Int) -> Unit) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    // Relê ao voltar do capítulo: um destaque ligado ou desligado lá muda a lista.
    var leitura by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { leitura++ }
    val passagens by produceState(initialValue = emptyList<Destaque>(), elementoId, leitura) {
        (aplicacao.repositorioDeDestaques.doElemento(elementoId) as? ResultadoDaChamada.Sucesso)?.let { value = it.dado }
    }
    if (passagens.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Passagens destacadas", style = MaterialTheme.typography.titleMedium)
        passagens.forEach { d ->
            Row(modifier = Modifier.fillMaxWidth().clickable { aoAbrir(d.capitulo_id, d.inicio) }.padding(vertical = 4.dp)) {
                Row(modifier = Modifier.width(6.dp).fillMaxHeight().background(corDeFundoDoDestaque(d.corDoDestaque))) {}
                Column(modifier = Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("“${d.trecho}”", style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    d.nota?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
