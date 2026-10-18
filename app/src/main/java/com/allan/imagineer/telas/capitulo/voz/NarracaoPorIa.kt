package com.allan.imagineer.telas.capitulo.voz

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.produceState
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
import com.allan.imagineer.rede.AudioSalvo
import com.allan.imagineer.rede.EstadoDoAudio
import com.allan.imagineer.rede.EstimativaDaNarracao
import com.allan.imagineer.rede.RepositorioDeNarracao
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SituacaoDoAudio
import com.allan.imagineer.rede.descreverEstimativa
import com.allan.imagineer.rede.detalheDoAudioSalvo
import com.allan.imagineer.rede.enderecoDoAudioSalvo
import com.allan.imagineer.rede.narracaoPorIaDisponivel
import com.allan.imagineer.rede.tituloDoAudioSalvo
import com.allan.imagineer.telas.capitulo.painel.urlDoServidorEmUso
import com.allan.imagineer.telas.comum.BotaoDeIcone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// A narração com a voz de IA (bloco G, fase 1, AN2 a AN7; item 7.5g, AU2): o servidor gera e GUARDA o MP3; o app consulta, pede a geração e toca
// a que a pessoa escolher na folha do fone.

/** O que o diálogo de gerar está fazendo (AN3). */
sealed interface DialogoDaNarracao {
    data object Fechado : DialogoDaNarracao

    /** Lendo a estimativa. */
    data object Lendo : DialogoDaNarracao

    /** Mostrando a estimativa; [refazer] = já havia um áudio pronto e a pessoa quer outro. */
    data class Gerar(val estimativa: EstimativaDaNarracao, val refazer: Boolean) : DialogoDaNarracao

    /** Apagar UMA narração guardada, escolhida na folha do fone (item 7.5g, AU2). */
    data class ConfirmarApagarSalva(val audio: AudioSalvo) : DialogoDaNarracao
}

data class EstadoDaNarracaoPorIa(
    /** A situação do áudio de agora; `null` = ainda consultando. */
    val audio: EstadoDoAudio? = null,
    val dialogo: DialogoDaNarracao = DialogoDaNarracao.Fechado,
    /** Um recado curto (falha de rede, recusa do servidor, "Narração pronta"); nulo quando não há nada a dizer. */
    val aviso: String? = null,
    /** Uma chamada ao servidor em andamento (gerar, apagar). */
    val ocupado: Boolean = false,
    /** As narrações prontas guardadas deste capítulo, da mais nova para a mais antiga (AU2); `null` = ainda não lidas. */
    val audios: List<AudioSalvo>? = null,
    /** Falhou ao ler a lista das guardadas (o motivo do servidor). */
    val erroDosAudios: String? = null,
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
                // Consulta de fundo, feita sozinha a cada capítulo: sem servidor não há o que dizer (só as ações pedidas avisam).
                is ResultadoDaChamada.Falha -> Unit
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
                if (r.dado.situacao == SituacaoDoAudio.PRONTO) { _estado.update { it.copy(aviso = "Narração pronta.") }; carregarAudios(); break }
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

    fun fecharDialogo() = _estado.update { it.copy(dialogo = DialogoDaNarracao.Fechado) }

    /** **Gerar** (AN3): o servidor responde 202 (começou) ou 200 (já havia); os erros dele aparecem como estão. */
    fun gerar(refazer: Boolean) {
        if (_estado.value.ocupado) return
        _estado.update { it.copy(dialogo = DialogoDaNarracao.Fechado, ocupado = true) }
        viewModelScope.launch {
            when (val r = repositorio.gerar(capituloId, refazer)) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.update { it.copy(audio = r.dado, ocupado = false) }
                    if (r.dado.situacao == SituacaoDoAudio.PRONTO) carregarAudios() // já havia uma pronta: a lista pode ter mudado
                    acompanharSeGerando(r.dado)
                }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(ocupado = false, aviso = r.motivo) }
            }
        }
    }

    fun avisoLido() = _estado.update { it.copy(aviso = null) }

    /** Lê as narrações guardadas do capítulo (AU2, `GET /capitulos/{id}/audios`). Não gasta nada. Uma falha vira o motivo na folha, não um aviso. */
    fun carregarAudios() {
        viewModelScope.launch {
            when (val r = repositorio.audios(capituloId)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(audios = r.dado, erroDosAudios = null) }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(erroDosAudios = r.motivo) }
            }
        }
    }

    /** Apagar UMA narração guardada: pede confirmação (AU2), porque o áudio sai do servidor e refazê-lo gasta de novo. */
    fun pedirApagarSalva(audio: AudioSalvo) = _estado.update { it.copy(dialogo = DialogoDaNarracao.ConfirmarApagarSalva(audio)) }

    /** O "sim" do diálogo: `DELETE /audios/{id}`; depois relê a lista e o estado (a apagada pode ser a de agora). */
    fun apagarSalva(audioId: Int) {
        if (_estado.value.ocupado) return
        _estado.update { it.copy(dialogo = DialogoDaNarracao.Fechado, ocupado = true) }
        viewModelScope.launch {
            when (val r = repositorio.apagarAudio(audioId)) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.update { it.copy(ocupado = false, aviso = "Narração apagada.") }
                    carregarAudios()
                    consultar()
                }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(ocupado = false, aviso = r.motivo) }
            }
        }
    }
}

/**
 * O que a pessoa pediu para ouvir na folha do fone (item 7.5g, AU2). Sempre de **um capítulo**: um pedido de um capítulo não vale para outro
 * (passar a página não pode começar a narrar o capítulo vizinho).
 */
sealed interface PedidoDeOuvir {
    val capituloId: Int

    /** A voz do aparelho (bloco E): lê o capítulo em voz alta a partir do parágrafo em que a pessoa está. */
    data class Aparelho(override val capituloId: Int) : PedidoDeOuvir

    /** Uma narração de IA guardada no servidor, pelo id dela. */
    data class Narracao(override val capituloId: Int, val audioId: Int) : PedidoDeOuvir
}

/** O pedido vale para a página deste [capituloId]? (AU2): é o que impede a página vizinha de começar a falar. */
fun pedidoValeParaOCapitulo(pedido: PedidoDeOuvir?, capituloId: Int): Boolean = pedido != null && pedido.capituloId == capituloId

/**
 * A parte flutuante da narração com a voz de IA (AU2, AN4): toca a narração **guardada** que a pessoa escolheu na folha do fone e, enquanto
 * o servidor gera uma, mostra "Gerando a narração…". Mostra também o aviso curto quando a geração termina. Sem narração pedida nem sendo
 * gerada, não desenha nada: o botão de ouvir é o **fone da barra de cima**.
 *
 * @param audioId a narração a tocar; `null` = nenhuma. Para ao sair do capítulo ([ativa] falso) e quando a pessoa toca em parar ([aoTerminar]).
 */
@Composable
fun ControleDeNarracaoPorIa(
    capituloId: Int,
    audioId: Int?,
    ativa: Boolean,
    velocidade: Float,
    aoMudarVelocidade: (Float) -> Unit,
    aoTerminar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val aplicacao = contexto.applicationContext as ImagineerApp
    val viewModel: NarracaoPorIaViewModel = viewModel(
        key = "narracao-ia-$capituloId",
        factory = viewModelFactory { initializer { NarracaoPorIaViewModel(capituloId, aplicacao.repositorioDeNarracao) } },
    )
    val estado by viewModel.estado.collectAsState()
    // Só o capítulo na frente consulta: a página vizinha, montada por baixo, não precisa.
    // PL14d: sem servidor, nem consulta.
    val servidor = com.allan.imagineer.telas.comum.temServidor()
    LaunchedEffect(capituloId, ativa, servidor) { if (ativa && servidor) viewModel.consultar() }
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let { Toast.makeText(contexto, it, Toast.LENGTH_LONG).show(); viewModel.avisoLido() }
    }
    val urlBase = urlDoServidorEmUso()
    LaunchedEffect(ativa) { if (!ativa && audioId != null) aoTerminar() }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        when {
            audioId != null && ativa && urlBase != null -> PlayerDaNarracao(
                endereco = enderecoDoAudioSalvo(urlBase, audioId),
                velocidadeInicial = velocidade,
                aoMudarVelocidade = aoMudarVelocidade,
                aoParar = aoTerminar,
            )
            ativa && estado.audio?.situacao == SituacaoDoAudio.GERANDO -> Surface(
                shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp, shadowElevation = 3.dp,
                modifier = Modifier.padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Gerando a narração…", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * A folha que o **fone de ouvido** da barra de cima abre (item 7.5g, AU2): a voz do aparelho; e a voz de IA, com as **narrações guardadas**
 * do capítulo (tocar ouve; o lixo apaga), **Gerar nova narração** (o diálogo com a estimativa) e **Configurar modelo e voz**.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FolhaDeOuvir(
    capituloId: Int,
    aoEscolher: (PedidoDeOuvir) -> Unit,
    aoConfigurar: () -> Unit,
    aoFechar: () -> Unit,
) {
    val aplicacao = LocalContext.current.applicationContext as ImagineerApp
    val viewModel: NarracaoPorIaViewModel = viewModel(
        key = "narracao-ia-$capituloId",
        factory = viewModelFactory { initializer { NarracaoPorIaViewModel(capituloId, aplicacao.repositorioDeNarracao) } },
    )
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(capituloId) {
        viewModel.consultar()
        viewModel.carregarAudios()
    }
    val configuracao by produceState<com.allan.imagineer.rede.ConfiguracaoAtual?>(initialValue = null) {
        value = (aplicacao.repositorioDeModelos.configuracao() as? ResultadoDaChamada.Sucesso)?.dado
    }

    ModalBottomSheet(onDismissRequest = aoFechar) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Ouvir o capítulo", style = MaterialTheme.typography.titleLarge)

            LinhaDaFolhaDeOuvir(
                icone = Icons.Filled.PhoneAndroid,
                titulo = "Voz do aparelho",
                detalhe = "Grátis, sem internet. Acompanha o texto parágrafo a parágrafo.",
                aoTocar = { aoEscolher(PedidoDeOuvir.Aparelho(capituloId)) },
            )
            HorizontalDivider()

            Text("Voz de IA", style = MaterialTheme.typography.titleMedium)
            val atual = configuracao
            when {
                atual == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                !narracaoPorIaDisponivel(atual.modelo_narracao, atual.tem_chave_api) -> {
                    Text(
                        "Para gerar narrações com voz de IA, escolha um modelo de voz (e confira a chave do OpenRouter) em Configurações → Narração.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = aoConfigurar) { Text("Configurar modelo e voz") }
                }
                else -> ConteudoDaVozDeIa(estado, viewModel, capituloId, aoEscolher, aoConfigurar)
            }
        }
    }
    DialogosDaNarracao(estado, viewModel)
}

@Composable
private fun ConteudoDaVozDeIa(
    estado: EstadoDaNarracaoPorIa,
    viewModel: NarracaoPorIaViewModel,
    capituloId: Int,
    aoEscolher: (PedidoDeOuvir) -> Unit,
    aoConfigurar: () -> Unit,
) {
    val gerando = estado.audio?.situacao == SituacaoDoAudio.GERANDO
    val audios = estado.audios
    // PD13: qual modelo e qual voz estão valendo (e que a próxima narração vai usar), à vista no topo.
    descreverModeloEmUso(estado.audio?.modelo, estado.audio?.voz)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    when {
        audios == null && estado.erroDosAudios == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        audios == null -> {
            Text(estado.erroDosAudios.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = viewModel::carregarAudios) { Text("Tentar de novo") }
        }
        audios.isEmpty() -> Text("Nenhuma narração gerada ainda. Depois de gerada, ouvir não custa nada.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            Text("Narrações guardadas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            audios.forEach { audio ->
                LinhaDaFolhaDeOuvir(
                    icone = Icons.Filled.PlayArrow,
                    titulo = tituloDoAudioSalvo(audio),
                    detalhe = detalheDoAudioSalvo(audio),
                    aoTocar = { aoEscolher(PedidoDeOuvir.Narracao(capituloId, audio.id)) },
                    acaoDoFim = {
                        BotaoDeIcone(Icons.Filled.Delete, "Apagar esta narração", aoTocar = { viewModel.pedirApagarSalva(audio) }, cor = MaterialTheme.colorScheme.error)
                    },
                )
            }
        }
    }
    if (gerando) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Gerando a narração…", style = MaterialTheme.typography.bodyMedium)
        }
    }
    val jaTemDeAgora = audios?.any { it.de_agora } == true
    Button(onClick = { viewModel.abrirGerar(refazer = jaTemDeAgora) }, enabled = !gerando && !estado.ocupado && audios != null) {
        Text(rotuloDeGerarNarracao(jaTemDeAgora))
    }
    TextButton(onClick = aoConfigurar) { Text("Configurar modelo e voz") }
}

/**
 * O modelo e a voz que **valem para este capítulo** (PD13), na folha de ouvir: o que o servidor diz que vale agora (o do livro, se ele trocou, ou
 * o de Configurações → Narração). `null` sem modelo. Sem voz escolhida, diz "voz padrão do modelo": é a que o servidor usa.
 */
fun descreverModeloEmUso(modelo: String?, voz: String?): String? {
    if (modelo.isNullOrBlank()) return null
    return "Modelo: $modelo · voz: ${voz?.takeIf { it.isNotBlank() } ?: "padrão do modelo"}"
}

/** O botão de gerar da folha (AU2): já existindo uma feita com o modelo e a voz de agora, gerar é **refazer** (gasta de novo). */
fun rotuloDeGerarNarracao(jaTemDeAgora: Boolean): String =
    if (jaTemDeAgora) "Refazer com o modelo e a voz de agora" else "Gerar nova narração"

/** Uma linha tocável da folha: o ícone, o título, o detalhe e, no fim, uma ação (apagar). */
@Composable
private fun LinhaDaFolhaDeOuvir(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    titulo: String,
    detalhe: String,
    aoTocar: () -> Unit,
    acaoDoFim: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = aoTocar).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            if (detalhe.isNotBlank()) Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        acaoDoFim?.invoke()
    }
}

/** Os diálogos da narração por IA: gerar (com a estimativa, AN3) e apagar uma guardada (AU2). */
@Composable
private fun DialogosDaNarracao(estado: EstadoDaNarracaoPorIa, viewModel: NarracaoPorIaViewModel) {
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
        is DialogoDaNarracao.ConfirmarApagarSalva -> AlertDialog(
            onDismissRequest = viewModel::fecharDialogo,
            title = { Text("Apagar esta narração?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tituloDoAudioSalvo(dialogo.audio), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "O áudio sai do servidor. Para ouvir de novo, será preciso gerar (e pagar) outra vez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.apagarSalva(dialogo.audio.id) }) { Text("Apagar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::fecharDialogo) { Text("Cancelar") } },
        )
        DialogoDaNarracao.Fechado, DialogoDaNarracao.Lendo -> Unit
    }
}

/** O player do MP3 da narração (AN4): em fluxo, com voltar 10 s, tocar/pausar, avançar 10 s, parar, velocidade e progresso. */
@Composable
private fun PlayerDaNarracao(
    endereco: String,
    velocidadeInicial: Float,
    aoMudarVelocidade: (Float) -> Unit,
    aoParar: () -> Unit,
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
            }
            LinearProgressIndicator(progress = { progresso }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).width(260.dp))
        }
    }
}
