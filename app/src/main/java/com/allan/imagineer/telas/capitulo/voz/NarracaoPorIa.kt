package com.allan.imagineer.telas.capitulo.voz

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.allan.imagineer.ImagineerApp
import com.allan.imagineer.rede.EstadoDoAudio
import com.allan.imagineer.rede.EstimativaDaNarracao
import com.allan.imagineer.rede.RepositorioDeNarracao
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SituacaoDoAudio
import com.allan.imagineer.rede.descreverEstimativa
import com.allan.imagineer.rede.enderecoDaNarracao
import com.allan.imagineer.telas.capitulo.painel.urlDoServidorEmUso
import com.allan.imagineer.telas.comum.BotaoDeIcone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// O botão Ouvir com a voz de IA (bloco G, fase 1, AN2 a AN7): o servidor gera o MP3 do capítulo; o app consulta, pede a geração e o toca.

/** O que o diálogo de gerar está fazendo (AN3). */
sealed interface DialogoDaNarracao {
    data object Fechado : DialogoDaNarracao

    /** Lendo a estimativa. */
    data object Lendo : DialogoDaNarracao

    /** Mostrando a estimativa; [refazer] = já havia um áudio pronto e a pessoa quer outro. */
    data class Gerar(val estimativa: EstimativaDaNarracao, val refazer: Boolean) : DialogoDaNarracao

    /** O que se pode fazer com uma narração pronta: refazer ou apagar. */
    data object Opcoes : DialogoDaNarracao

    data object ConfirmarApagar : DialogoDaNarracao
}

data class EstadoDaNarracaoPorIa(
    /** A situação do áudio de agora; `null` = ainda consultando. */
    val audio: EstadoDoAudio? = null,
    val dialogo: DialogoDaNarracao = DialogoDaNarracao.Fechado,
    /** Um recado curto (falha de rede, recusa do servidor, "Narração pronta"); nulo quando não há nada a dizer. */
    val aviso: String? = null,
    /** Uma chamada ao servidor em andamento (gerar, apagar). */
    val ocupado: Boolean = false,
)

class NarracaoPorIaViewModel(
    private val capituloId: Int,
    private val repositorio: RepositorioDeNarracao,
    private val intervaloDeConsultaEmMs: Long = 5_000L,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDaNarracaoPorIa())
    val estado: StateFlow<EstadoDaNarracaoPorIa> = _estado.asStateFlow()
    private var acompanhando: Job? = null

    /** Lê a situação do áudio; se está sendo gerado, passa a acompanhar até acabar (AN7). */
    fun consultar() {
        viewModelScope.launch {
            when (val r = repositorio.estado(capituloId)) {
                is ResultadoDaChamada.Sucesso -> { _estado.update { it.copy(audio = r.dado) }; acompanharSeGerando(r.dado) }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(aviso = r.motivo) }
            }
        }
    }

    private fun acompanharSeGerando(audio: EstadoDoAudio) {
        if (audio.situacao != SituacaoDoAudio.GERANDO || acompanhando?.isActive == true) return
        acompanhando = viewModelScope.launch {
            while (true) {
                delay(intervaloDeConsultaEmMs)
                val r = repositorio.estado(capituloId)
                if (r !is ResultadoDaChamada.Sucesso) continue  // uma falha de rede no meio não derruba o acompanhamento
                _estado.update { it.copy(audio = r.dado) }
                if (r.dado.situacao == SituacaoDoAudio.PRONTO) { _estado.update { it.copy(aviso = "Narração pronta.") }; break }
                if (r.dado.situacao != SituacaoDoAudio.GERANDO) { _estado.update { it.copy(aviso = r.dado.erro ?: "A narração falhou.") }; break }
            }
        }
    }

    /** Abre o diálogo de gerar com a estimativa lida do servidor. [refazer]: já há uma pronta. */
    fun abrirGerar(refazer: Boolean = false) {
        _estado.update { it.copy(dialogo = DialogoDaNarracao.Lendo) }
        viewModelScope.launch {
            when (val r = repositorio.estimativa(capituloId)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(dialogo = DialogoDaNarracao.Gerar(r.dado, refazer)) }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(dialogo = DialogoDaNarracao.Fechado, aviso = r.motivo) }
            }
        }
    }

    fun abrirOpcoes() = _estado.update { it.copy(dialogo = DialogoDaNarracao.Opcoes) }

    fun pedirApagar() = _estado.update { it.copy(dialogo = DialogoDaNarracao.ConfirmarApagar) }

    fun fecharDialogo() = _estado.update { it.copy(dialogo = DialogoDaNarracao.Fechado) }

    /** **Gerar** (AN3): o servidor responde 202 (começou) ou 200 (já havia); os erros dele aparecem como estão. */
    fun gerar(refazer: Boolean) {
        if (_estado.value.ocupado) return
        _estado.update { it.copy(dialogo = DialogoDaNarracao.Fechado, ocupado = true) }
        viewModelScope.launch {
            when (val r = repositorio.gerar(capituloId, refazer)) {
                is ResultadoDaChamada.Sucesso -> { _estado.update { it.copy(audio = r.dado, ocupado = false) }; acompanharSeGerando(r.dado) }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(ocupado = false, aviso = r.motivo) }
            }
        }
    }

    /** **Apagar** a narração e o arquivo (AN5). */
    fun apagar() {
        if (_estado.value.ocupado) return
        _estado.update { it.copy(dialogo = DialogoDaNarracao.Fechado, ocupado = true) }
        viewModelScope.launch {
            when (val r = repositorio.apagar(capituloId)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(audio = EstadoDoAudio(SituacaoDoAudio.NAO_GERADO), ocupado = false, aviso = "Narração apagada.") }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(ocupado = false, aviso = r.motivo) }
            }
        }
    }

    fun avisoLido() = _estado.update { it.copy(aviso = null) }
}

/**
 * O botão Ouvir **com a voz de IA** (AN2): conforme a situação do áudio, abre o diálogo de gerar, mostra o andamento ou toca o MP3 com
 * voltar 10 s, tocar/pausar, avançar 10 s, parar, velocidade e progresso (AN4). Para ao sair do capítulo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControleDeNarracaoPorIa(
    capituloId: Int,
    ativa: Boolean,
    velocidade: Float,
    aoMudarVelocidade: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val aplicacao = contexto.applicationContext as ImagineerApp
    val viewModel: NarracaoPorIaViewModel = viewModel(
        key = "narracao-ia-$capituloId",
        factory = viewModelFactory { initializer { NarracaoPorIaViewModel(capituloId, aplicacao.repositorioDeNarracao) } },
    )
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(capituloId) { viewModel.consultar() }
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let { Toast.makeText(contexto, it, Toast.LENGTH_LONG).show(); viewModel.avisoLido() }
    }
    val urlBase = urlDoServidorEmUso()
    var tocando by remember(capituloId) { mutableStateOf(false) }
    var menuAberto by remember { mutableStateOf(false) }
    LaunchedEffect(ativa) { if (!ativa) tocando = false }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        val audio = estado.audio
        when {
            tocando && urlBase != null -> PlayerDaNarracao(
                endereco = enderecoDaNarracao(urlBase, capituloId),
                velocidadeInicial = velocidade,
                aoMudarVelocidade = aoMudarVelocidade,
                aoParar = { tocando = false },
                aoApagarOuRefazer = { menuAberto = true },
                menuAberto = menuAberto,
                aoFecharMenu = { menuAberto = false },
                aoRefazer = { viewModel.abrirGerar(refazer = true) },
                aoApagar = viewModel::pedirApagar,
            )
            audio == null || estado.ocupado || estado.dialogo == DialogoDaNarracao.Lendo -> BotaoRedondo { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
            audio.situacao == SituacaoDoAudio.GERANDO -> Surface(
                shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp, shadowElevation = 3.dp,
                modifier = Modifier.padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Gerando a narração…", style = MaterialTheme.typography.labelLarge)
                }
            }
            audio.situacao == SituacaoDoAudio.PRONTO -> BotaoRedondo(
                aoTocar = { tocando = true },
                aoSegurar = viewModel::abrirOpcoes,
            ) { androidx.compose.material3.Icon(Icons.Filled.PlayArrow, contentDescription = "Ouvir a narração (segure para refazer ou apagar)") }
            else -> BotaoRedondo(aoTocar = { viewModel.abrirGerar() }) {
                androidx.compose.material3.Icon(Icons.Filled.VolumeUp, contentDescription = "Gerar a narração com voz de IA")
            }
        }
    }

    when (val dialogo = estado.dialogo) {
        is DialogoDaNarracao.Gerar -> AlertDialog(
            onDismissRequest = viewModel::fecharDialogo,
            title = { Text(if (dialogo.refazer) "Refazer a narração?" else "Gerar a narração?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(descreverEstimativa(dialogo.estimativa), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Modelo: ${dialogo.estimativa.modelo}" + (dialogo.estimativa.voz?.let { " · voz: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    estado.audio?.erro?.let { Text("A tentativa anterior falhou: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Text(
                        "O servidor gera o áudio uma vez e o guarda. Depois de gerado, ouvir não custa nada." +
                            if (dialogo.refazer) " Refazer gasta de novo." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.gerar(dialogo.refazer) }) { Text(if (dialogo.refazer) "Refazer" else "Gerar") } },
            dismissButton = { TextButton(onClick = viewModel::fecharDialogo) { Text("Cancelar") } },
        )
        DialogoDaNarracao.Opcoes -> AlertDialog(
            onDismissRequest = viewModel::fecharDialogo,
            title = { Text("Narração deste capítulo") },
            text = { Text("Ela já está pronta. Você pode gerar de novo (gasta de novo) ou apagá-la do servidor.") },
            confirmButton = { TextButton(onClick = { viewModel.abrirGerar(refazer = true) }) { Text("Refazer") } },
            dismissButton = { TextButton(onClick = viewModel::pedirApagar) { Text("Apagar") } },
        )
        DialogoDaNarracao.ConfirmarApagar -> AlertDialog(
            onDismissRequest = viewModel::fecharDialogo,
            title = { Text("Apagar a narração?") },
            text = { Text("O áudio sai do servidor. Para ouvir de novo, será preciso gerar (e pagar) outra vez.") },
            confirmButton = { TextButton(onClick = viewModel::apagar) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = viewModel::fecharDialogo) { Text("Cancelar") } },
        )
        DialogoDaNarracao.Fechado, DialogoDaNarracao.Lendo -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BotaoRedondo(aoTocar: () -> Unit = {}, aoSegurar: (() -> Unit)? = null, conteudo: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.padding(16.dp).size(48.dp).combinedClickable(onClick = aoTocar, onLongClick = aoSegurar),
    ) {
        Box(contentAlignment = Alignment.Center) { conteudo() }
    }
}

/** O player do MP3 da narração (AN4): em fluxo, com voltar 10 s, tocar/pausar, avançar 10 s, parar, velocidade e progresso. */
@Composable
private fun PlayerDaNarracao(
    endereco: String,
    velocidadeInicial: Float,
    aoMudarVelocidade: (Float) -> Unit,
    aoParar: () -> Unit,
    aoApagarOuRefazer: () -> Unit,
    menuAberto: Boolean,
    aoFecharMenu: () -> Unit,
    aoRefazer: () -> Unit,
    aoApagar: () -> Unit,
) {
    val contexto = LocalContext.current
    var falhou by remember { mutableStateOf(false) }
    var tocandoAgora by remember { mutableStateOf(true) }
    var progresso by remember { mutableFloatStateOf(0f) }
    var velocidade by remember { mutableFloatStateOf(velocidadeInicial) }
    val player = remember(endereco) {
        ExoPlayer.Builder(contexto).build().apply {
            setMediaItem(MediaItem.fromUri(endereco))
            setPlaybackSpeed(velocidadeInicial)
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { tocandoAgora = isPlaying }
                override fun onPlaybackStateChanged(estado: Int) { if (estado == Player.STATE_ENDED) { tocandoAgora = false } }
                override fun onPlayerError(error: PlaybackException) { falhou = true }
            })
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    LaunchedEffect(player) {
        while (true) {
            val duracao = player.duration
            progresso = if (duracao > 0) (player.currentPosition.toFloat() / duracao).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    LaunchedEffect(falhou) { if (falhou) { Toast.makeText(contexto, "Não consegui tocar a narração. Confira a conexão com o servidor.", Toast.LENGTH_LONG).show(); aoParar() } }

    Surface(
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp, shadowElevation = 3.dp,
        modifier = Modifier.padding(16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                BotaoDeIcone(Icons.Filled.Replay10, "Voltar 10 segundos", aoTocar = { player.seekTo(maxOf(0L, player.currentPosition - 10_000L)) })
                if (tocandoAgora) BotaoDeIcone(Icons.Filled.Pause, "Pausar", aoTocar = { player.pause() })
                else BotaoDeIcone(Icons.Filled.PlayArrow, "Continuar", aoTocar = {
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                    player.play()
                })
                BotaoDeIcone(Icons.Filled.Forward10, "Avançar 10 segundos", aoTocar = { player.seekTo(player.currentPosition + 10_000L) })
                BotaoDeIcone(Icons.Filled.Stop, "Parar de ouvir", aoTocar = aoParar)
                TextButton(onClick = { val nova = maisLenta(velocidade); velocidade = nova; player.setPlaybackSpeed(nova); aoMudarVelocidade(nova) }) { Text("−") }
                Text(descreverVelocidade(velocidade), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { val nova = maisRapida(velocidade); velocidade = nova; player.setPlaybackSpeed(nova); aoMudarVelocidade(nova) }) { Text("+") }
                Box {
                    BotaoDeIcone(Icons.Filled.MoreVert, "Mais opções da narração", aoTocar = aoApagarOuRefazer)
                    DropdownMenu(expanded = menuAberto, onDismissRequest = aoFecharMenu) {
                        DropdownMenuItem(text = { Text("Refazer a narração") }, onClick = { aoFecharMenu(); aoRefazer() })
                        DropdownMenuItem(text = { Text("Apagar a narração") }, onClick = { aoFecharMenu(); aoApagar() })
                    }
                }
            }
            LinearProgressIndicator(progress = { progresso }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).width(260.dp))
        }
    }
}
