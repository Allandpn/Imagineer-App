package com.allan.imagineer.telas.capitulo.voz

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.capitulo.ParagrafoDoTexto
import com.allan.imagineer.telas.comum.BotaoDeIcone

/**
 * O controle de **ouvir o capítulo** (RL18): no canto de baixo, um botão **Ouvir**; ao tocar, a leitura em voz alta começa do parágrafo
 * em que a pessoa está e o botão vira uma barra com anterior, pausar/continuar, próximo, parar e a velocidade.
 *
 * Só funciona **com o capítulo na tela**: ao sair dele (ou passar a página), a voz para e é liberada.
 *
 * @param aoSeguirParagrafo o início do parágrafo que está sendo falado (UTF-16), ou `null` quando não está falando: a página rola até ele.
 */
@Composable
fun ControleDeNarracao(
    livroId: Int,
    paragrafos: List<ParagrafoDoTexto>,
    ativa: Boolean,
    posicaoAtual: () -> Int,
    velocidade: Float,
    aoMudarVelocidade: (Float) -> Unit,
    aoSeguirParagrafo: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val aplicacao = contexto.applicationContext as ImagineerApp
    val idioma by produceState<String?>(initialValue = null, livroId) {
        value = (aplicacao.repositorioDeLivros.abrirLivro(livroId) as? ResultadoDaChamada.Sucesso)?.dado?.idioma
    }
    var narrador by remember { mutableStateOf<NarradorDoCapitulo?>(null) }
    var motor by remember { mutableStateOf<MotorDeVozDoAndroid?>(null) }
    val seguir by rememberUpdatedState(aoSeguirParagrafo)

    fun encerrar() {
        narrador?.encerrar()
        narrador = null
        motor = null
        seguir(null)
    }

    // Saiu da frente (outro capítulo, outra tela): a voz para e é liberada.
    DisposableEffect(Unit) { onDispose { narrador?.encerrar() } }
    LaunchedEffect(ativa) { if (!ativa && narrador != null) encerrar() }

    fun comecar() {
        val novoMotor = MotorDeVozDoAndroid(
            contexto,
            idioma,
            aoPronto = { pronta ->
                if (!pronta) {
                    Toast.makeText(contexto, "Este aparelho não tem uma voz instalada. Instale uma nas configurações de texto para voz.", Toast.LENGTH_LONG).show()
                    encerrar()
                }
            },
            aoAvisar = { Toast.makeText(contexto, it, Toast.LENGTH_LONG).show() },
        )
        val novo = NarradorDoCapitulo(novoMotor, paragrafos, velocidade)
        novoMotor.ligar(novo)
        novoMotor.definirVelocidade(velocidade)
        motor = novoMotor
        narrador = novo
        novo.iniciarEm(posicaoAtual())
    }

    val atual = narrador
    val estado by (atual?.estado ?: remember { kotlinx.coroutines.flow.MutableStateFlow(EstadoDaNarracao()) }).collectAsState()

    // A página acompanha o parágrafo falado; ao parar ou pausar, deixa de puxar.
    LaunchedEffect(estado.situacao, estado.paragrafo) {
        seguir(if (estado.situacao == SituacaoDaNarracao.FALANDO) paragrafos.getOrNull(estado.paragrafo)?.inicio else null)
        if (estado.situacao == SituacaoDaNarracao.PARADA && narrador != null) encerrar()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        if (atual == null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
                modifier = Modifier.padding(16.dp),
            ) {
                BotaoDeIcone(Icons.Filled.VolumeUp, "Ouvir o capítulo", aoTocar = { comecar() })
            }
        } else {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
                modifier = Modifier.padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    BotaoDeIcone(Icons.Filled.SkipPrevious, "Parágrafo anterior", aoTocar = atual::anterior)
                    if (estado.situacao == SituacaoDaNarracao.FALANDO) {
                        BotaoDeIcone(Icons.Filled.Pause, "Pausar", aoTocar = atual::pausar)
                    } else {
                        BotaoDeIcone(Icons.Filled.PlayArrow, "Continuar", aoTocar = atual::retomar)
                    }
                    BotaoDeIcone(Icons.Filled.SkipNext, "Próximo parágrafo", aoTocar = atual::proximo)
                    BotaoDeIcone(Icons.Filled.Stop, "Parar de ouvir", aoTocar = atual::parar)
                    TextButton(onClick = { val nova = maisLenta(estado.velocidade); atual.mudarVelocidade(nova); aoMudarVelocidade(nova) }) { Text("−") }
                    Text(descreverVelocidade(estado.velocidade), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { val nova = maisRapida(estado.velocidade); atual.mudarVelocidade(nova); aoMudarVelocidade(nova) }) { Text("+") }
                }
            }
        }
    }
}
