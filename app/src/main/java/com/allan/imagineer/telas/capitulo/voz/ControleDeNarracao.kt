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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    capituloId: Int,
    paragrafos: List<ParagrafoDoTexto>,
    ativa: Boolean,
    posicaoAtual: () -> Int,
    velocidade: Float,
    aoMudarVelocidade: (Float) -> Unit,
    aoSeguirParagrafo: (Int?) -> Unit,
    /** O que a pessoa escolheu na folha do fone (AU2); só vale para o capítulo dele. `null` = nada tocando. */
    pedido: PedidoDeOuvir? = null,
    /** Acabou, a pessoa parou ou o capítulo saiu da frente: o pedido deve ser limpo. */
    aoTerminar: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vale = pedidoValeParaOCapitulo(pedido, capituloId)
    // A parte da voz de IA fica sempre montada: é ela que mostra "Gerando a narração…" e avisa quando termina, mesmo com a folha fechada.
    ControleDeNarracaoPorIa(
        capituloId = capituloId,
        audioId = (pedido as? PedidoDeOuvir.Narracao)?.takeIf { vale }?.audioId,
        ativa = ativa,
        velocidade = velocidade,
        aoMudarVelocidade = aoMudarVelocidade,
        aoTerminar = aoTerminar,
        modifier = modifier,
    )
    if (vale && pedido is PedidoDeOuvir.Aparelho) {
        ControleDeNarracaoDoAparelho(livroId, paragrafos, ativa, posicaoAtual, velocidade, aoMudarVelocidade, aoSeguirParagrafo, aoTerminar, modifier)
    }
}

/** A leitura com a **voz do aparelho** (RL18, bloco E). Começa sozinha quando a pessoa a escolhe na folha do fone (AU2). */
@Composable
private fun ControleDeNarracaoDoAparelho(
    livroId: Int,
    paragrafos: List<ParagrafoDoTexto>,
    ativa: Boolean,
    posicaoAtual: () -> Int,
    velocidade: Float,
    aoMudarVelocidade: (Float) -> Unit,
    aoSeguirParagrafo: (Int?) -> Unit,
    aoTerminar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val aplicacao = contexto.applicationContext as ImagineerApp
    // O idioma do livro escolhe a voz; só se começa a falar depois de lê-lo (acerte ou falhe a leitura), para não falar na voz errada.
    val idiomaLido by produceState(initialValue = false to (null as String?), livroId) {
        value = true to (aplicacao.repositorioDeLivros.abrirLivro(livroId) as? ResultadoDaChamada.Sucesso)?.dado?.idioma
    }
    val idioma = idiomaLido.second
    var narrador by remember { mutableStateOf<NarradorDoCapitulo?>(null) }
    var motor by remember { mutableStateOf<MotorDeVozDoAndroid?>(null) }
    val seguir by rememberUpdatedState(aoSeguirParagrafo)

    fun encerrar() {
        narrador?.encerrar()
        narrador = null
        motor = null
        seguir(null)
        aoTerminar()
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

    // AU2: o pedido já veio da folha do fone; começa assim que o idioma do livro foi lido.
    LaunchedEffect(idiomaLido.first) { if (idiomaLido.first && narrador == null) comecar() }

    val atual = narrador
    val estado by (atual?.estado ?: remember { kotlinx.coroutines.flow.MutableStateFlow(EstadoDaNarracao()) }).collectAsState()

    // A página acompanha o parágrafo falado; ao parar ou pausar, deixa de puxar.
    LaunchedEffect(estado.situacao, estado.paragrafo) {
        seguir(if (estado.situacao == SituacaoDaNarracao.FALANDO) paragrafos.getOrNull(estado.paragrafo)?.inicio else null)
        if (estado.situacao == SituacaoDaNarracao.PARADA && narrador != null) encerrar()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        if (atual != null) {
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
                    BotaoDeIcone(Icons.Filled.Remove, "Mais devagar", aoTocar = { val nova = maisLenta(estado.velocidade); atual.mudarVelocidade(nova); aoMudarVelocidade(nova) })
                    Text(descreverVelocidade(estado.velocidade), style = MaterialTheme.typography.labelLarge)
                    BotaoDeIcone(Icons.Filled.Add, "Mais rápido", aoTocar = { val nova = maisRapida(estado.velocidade); atual.mudarVelocidade(nova); aoMudarVelocidade(nova) })
                }
            }
        }
    }
}
