package com.allan.imagineer.analise

import com.allan.imagineer.rede.ResultadoDaGeracao
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.PromptsSemServidor
import com.allan.imagineer.rede.RepositorioDePrompts
import com.allan.imagineer.rede.RepositorioDeSugestoes
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * O que aconteceu com uma análise de IA (defeito D1): o capítulo, em que livro estava e como terminou.
 *
 * @property rotuloDoCapitulo como o capítulo se chama para o usuário: "capítulo 3" ou "capítulo «O muro»".
 */
data class EventoDeAnalise(
    val capituloId: Int,
    val livroId: Int?,
    val rotuloDoCapitulo: String,
    val sucesso: Boolean,
    val motivo: String? = null,
    /** O que terminou: a análise do capítulo ou a geração do prompt de uma cena (item 10b, G13). */
    val tipo: TipoDeEvento = TipoDeEvento.ANALISE,
    /** Só no [TipoDeEvento.PROMPT]: o frame da cena e o nome dela, para o aviso. */
    val frameId: Int? = null,
    val rotuloDaCena: String? = null,
    /** Só no [TipoDeEvento.IMAGEM]: o provedor recusou o prompt (não é erro de rede: a pessoa precisa ajustar o prompt). */
    val recusada: Boolean = false,
)

/** O que terminou e merece um aviso. */
enum class TipoDeEvento { ANALISE, PROMPT, IMAGEM }

/**
 * As análises de IA em andamento — **vivem no escopo do app, e não no da tela**.
 *
 * Antes, a análise corria dentro do ViewModel do painel: quem saía da tela do capítulo antes de ela terminar
 * cancelava, sem saber, a espera pelo resultado, e nunca era avisado. Agora o trabalho é do app inteiro:
 * sobrevive à saída da tela, e quem volta ao capítulo reencontra a análise **em andamento** (ou já pronta).
 *
 * Ao terminar, o serviço emite um [EventoDeAnalise]; quem decide **se e como avisar** é o avisador global
 * (ver [descreverAviso]), que sabe onde o usuário está.
 *
 * Só **uma** análise por capítulo de cada vez: pedir outra enquanto uma roda devolve a mesma espera, em vez
 * de cobrar duas vezes (o servidor também recusa a segunda, com 409 — item 6.7).
 */
class ServicoDeAnalises(
    private val sugestoes: RepositorioDeSugestoes,
    private val escopo: CoroutineScope,
    /** Os prompts: a geração também vive aqui (G8, revisto), para sobreviver à saída da tela e gerar o aviso final. */
    private val prompts: RepositorioDePrompts = PromptsSemServidor,
) {
    private val trava = Any()
    private val emAndamento = mutableMapOf<Int, Deferred<ResultadoDaChamada<SugestoesDeCapitulo>>>()
    private val livrosDosCapitulos = mutableMapOf<Int, Int>()

    private val _eventos = MutableSharedFlow<EventoDeAnalise>(extraBufferCapacity = 16)

    /** Um evento por análise que termina, com sucesso ou não. */
    val eventos: SharedFlow<EventoDeAnalise> = _eventos.asSharedFlow()

    private val promptsEmAndamento = mutableMapOf<Int, Deferred<ResultadoDaChamada<PromptDeFrame>>>()

    private val _modalDoFrameVisivel = MutableStateFlow<Int?>(null)

    /** O frame cujo modal de cena está **na tela agora** (ou `null`): ele mostra o próprio aviso, então o global se cala. */
    val modalDoFrameVisivel: StateFlow<Int?> = _modalDoFrameVisivel.asStateFlow()

    fun definirModalDoFrameVisivel(frameId: Int?) {
        _modalDoFrameVisivel.value = frameId
    }

    /** A geração de prompt deste frame que está rodando agora, se houver. */
    fun promptEmAndamento(frameId: Int): Deferred<ResultadoDaChamada<PromptDeFrame>>? =
        synchronized(trava) { promptsEmAndamento[frameId] }

    /**
     * Começa a geração do prompt do frame — ou devolve a que já está rodando (**uma por frame de cada vez**, G4).
     * Como a análise, **vive no escopo do app**: sair do capítulo não a cancela, e ao terminar sai um aviso.
     */
    fun iniciarPrompt(
        frameId: Int,
        capituloId: Int,
        livroId: Int?,
        rotuloDoCapitulo: String,
        rotuloDaCena: String,
        comentario: String?,
    ): Deferred<ResultadoDaChamada<PromptDeFrame>> = synchronized(trava) {
        promptsEmAndamento[frameId]?.let { return it }
        val trabalho = escopo.async {
            // O que o frame já tinha antes: se a conexão cair no meio, é por aqui que se descobre que o servidor terminou mesmo assim.
            val ultimoAntes = ultimoPromptDoFrame(frameId)
            var resultado = prompts.gerar(frameId, comentario)
            if (resultado is ResultadoDaChamada.Falha && resultado.codigoHttp == null && ultimoAntes != null) {
                resultado = esperarOPromptQueOServidorTerminou(frameId, ultimoAntes) ?: resultado
            }
            synchronized(trava) { promptsEmAndamento.remove(frameId) }
            _eventos.emit(
                EventoDeAnalise(
                    capituloId = capituloId,
                    livroId = livroId,
                    rotuloDoCapitulo = rotuloDoCapitulo,
                    sucesso = resultado is ResultadoDaChamada.Sucesso,
                    motivo = (resultado as? ResultadoDaChamada.Falha)?.motivo,
                    tipo = TipoDeEvento.PROMPT,
                    frameId = frameId,
                    rotuloDaCena = rotuloDaCena,
                ),
            )
            resultado
        }
        promptsEmAndamento[frameId] = trabalho
        trabalho
    }

    /** O id do prompt mais recente do frame (0 se não há nenhum), ou `null` se não deu para ler (sem conexão). */
    private suspend fun ultimoPromptDoFrame(frameId: Int): Int? =
        (prompts.listar(frameId) as? ResultadoDaChamada.Sucesso)?.dado?.let { lista -> lista.maxOfOrNull { it.id } ?: 0 }

    /**
     * A conexão caiu **durante** a geração do prompt (a tela apagou, o app foi para o fundo, o Tailscale oscilou), mas o servidor **não**
     * para por isso: ele termina, grava o prompt e só não tem a quem responder. Então, em vez de dar a geração por falha, o app **procura**
     * o prompt que passou a existir (id maior que o último de antes), a cada poucos segundos, por até ~2 minutos. Sem achar, vale a falha.
     */
    private suspend fun esperarOPromptQueOServidorTerminou(frameId: Int, ultimoAntes: Int): ResultadoDaChamada<PromptDeFrame>? {
        repeat(TENTATIVAS_DE_CONFERIR_O_PROMPT) {
            delay(INTERVALO_DE_CONFERIR_O_PROMPT_MS)
            val lista = (prompts.listar(frameId) as? ResultadoDaChamada.Sucesso)?.dado ?: return@repeat
            lista.filter { it.id > ultimoAntes && !it.so_imagem && it.tipo == "IMAGEM" }.maxByOrNull { it.id }?.let { return ResultadoDaChamada.Sucesso(it) }
        }
        return null
    }

    private val imagensEmAndamento = mutableMapOf<Int, Deferred<ResultadoDaChamada<ResultadoDaGeracao>>>()

    /** A geração de imagem deste prompt que está rodando agora, se houver. */
    fun imagemEmAndamento(promptId: Int): Deferred<ResultadoDaChamada<ResultadoDaGeracao>>? =
        synchronized(trava) { imagensEmAndamento[promptId] }

    /**
     * Começa a geração da **imagem** do prompt (K1) — ou devolve a que já está rodando (uma por prompt de cada vez, K2). Como o
     * prompt, **vive no escopo do app**: sair do capítulo não a cancela e, ao terminar, sai um aviso de qualquer tela, com "Abrir".
     * Uma **recusa** do provedor (`RECUSADA`) também avisa, como "precisa de você".
     */
    fun iniciarImagem(
        promptId: Int,
        frameId: Int,
        capituloId: Int,
        livroId: Int?,
        rotuloDoCapitulo: String,
        rotuloDaCena: String,
        textoEditado: String?,
        modelo: String?,
        semFiltro: Boolean,
        referencias: List<Int>,
        textoPt: String? = null,
    ): Deferred<ResultadoDaChamada<ResultadoDaGeracao>> = synchronized(trava) {
        imagensEmAndamento[promptId]?.let { return it }
        val trabalho = escopo.async {
            val resultado = when {
                semFiltro -> prompts.gerarImagem(promptId, textoEditado, modelo, semFiltro = true, referencias = referencias, textoPt = textoPt)
                referencias.isNotEmpty() -> prompts.gerarImagem(promptId, textoEditado, modelo, referencias = referencias, textoPt = textoPt)
                else -> prompts.gerarImagem(promptId, textoEditado, modelo, textoPt = textoPt)
            }
            synchronized(trava) { imagensEmAndamento.remove(promptId) }
            val geracao = (resultado as? ResultadoDaChamada.Sucesso)?.dado
            _eventos.emit(
                EventoDeAnalise(
                    capituloId = capituloId,
                    livroId = livroId,
                    rotuloDoCapitulo = rotuloDoCapitulo,
                    sucesso = geracao?.gerada == true,
                    motivo = (resultado as? ResultadoDaChamada.Falha)?.motivo,
                    tipo = TipoDeEvento.IMAGEM,
                    frameId = frameId,
                    rotuloDaCena = rotuloDaCena,
                    recusada = geracao != null && !geracao.gerada,
                ),
            )
            resultado
        }
        imagensEmAndamento[promptId] = trabalho
        trabalho
    }

    private val _painelVisivel = MutableStateFlow<Int?>(null)

    /** O capítulo cujo painel de IA está **na tela agora** (ou `null`): quem o vê não precisa de aviso. */
    val painelVisivel: StateFlow<Int?> = _painelVisivel.asStateFlow()

    fun definirPainelVisivel(capituloId: Int?) {
        _painelVisivel.value = capituloId
    }

    /** Guarda de qual livro é o capítulo, para a navegação saber se o usuário ainda está "dentro do livro". */
    fun registrarLivro(capituloId: Int, livroId: Int) {
        synchronized(trava) { livrosDosCapitulos[capituloId] = livroId }
    }

    fun livroDoCapitulo(capituloId: Int): Int? = synchronized(trava) { livrosDosCapitulos[capituloId] }

    /** A análise deste capítulo que está rodando agora, se houver. */
    fun emAndamento(capituloId: Int): Deferred<ResultadoDaChamada<SugestoesDeCapitulo>>? =
        synchronized(trava) { emAndamento[capituloId] }

    /**
     * Começa a análise do capítulo — ou devolve a que já está rodando. É o **único** caminho até o `POST`
     * que gasta IA (itens 6.7 e 6.8).
     *
     * @param rotuloDoCapitulo o nome do capítulo para o aviso final ("capítulo 3").
     * @param orientacao ver [RepositorioDeSugestoes.analisar].
     */
    fun iniciar(
        capituloId: Int,
        livroId: Int?,
        rotuloDoCapitulo: String,
        forcar: Boolean,
        orientacao: String?,
    ): Deferred<ResultadoDaChamada<SugestoesDeCapitulo>> = synchronized(trava) {
        emAndamento[capituloId]?.let { return it }
        if (livroId != null) livrosDosCapitulos[capituloId] = livroId

        val trabalho = escopo.async {
            val resultado = sugestoes.analisar(capituloId, forcar, orientacao)
            synchronized(trava) { emAndamento.remove(capituloId) }
            _eventos.emit(
                EventoDeAnalise(
                    capituloId = capituloId,
                    livroId = livroId,
                    rotuloDoCapitulo = rotuloDoCapitulo,
                    sucesso = resultado is ResultadoDaChamada.Sucesso,
                    motivo = (resultado as? ResultadoDaChamada.Falha)?.motivo,
                ),
            )
            resultado
        }
        emAndamento[capituloId] = trabalho
        trabalho
    }
}

/**
 * Onde o usuário está agora, para decidir **como** avisar (D1).
 *
 * @property livroId o livro cuja área está aberta (Livro, Capítulo, Elementos, Ficha, Arquivados), ou `null`
 * na Biblioteca, na Configuração e nas demais.
 */
data class LocalDoUsuario(val livroId: Int?)

/**
 * O texto do aviso de uma análise que terminou, ou `null` se **não deve haver aviso**.
 *
 * Regras (decididas pelo Allan em 01/10/2026):
 * - **Dentro do livro** da análise: só o capítulo — "Análise do capítulo 3 concluída.";
 * - **Fora do livro** (Biblioteca, outro livro, configuração...): o livro e o capítulo —
 *   "Análise concluída em «O Alienista», capítulo 3.";
 * - **Falhou:** o mesmo, dizendo o motivo;
 * - **Sem aviso** quando o usuário está **olhando o painel daquele capítulo** ([painelVisivel]): o resultado
 *   já aparece lá, e o aviso seria ruído.
 *
 * @param tituloDoLivro o nome do livro, se já se conseguiu descobrir (só importa fora do livro).
 */
fun descreverAviso(
    evento: EventoDeAnalise,
    local: LocalDoUsuario,
    painelVisivel: Int?,
    tituloDoLivro: String?,
    modalDoFrameVisivel: Int? = null,
): String? {
    if (evento.tipo == TipoDeEvento.PROMPT) return descreverAvisoDePrompt(evento, local, tituloDoLivro, modalDoFrameVisivel)
    if (evento.tipo == TipoDeEvento.IMAGEM) return descreverAvisoDeImagem(evento, local, painelVisivel, tituloDoLivro, modalDoFrameVisivel)
    if (evento.capituloId == painelVisivel) return null

    val dentroDoLivro = evento.livroId != null && local.livroId == evento.livroId
    val onde = if (dentroDoLivro || tituloDoLivro == null) {
        evento.rotuloDoCapitulo
    } else {
        "«$tituloDoLivro», ${evento.rotuloDoCapitulo}"
    }
    return if (evento.sucesso) {
        if (dentroDoLivro) "Análise do ${evento.rotuloDoCapitulo} concluída." else "Análise concluída em $onde."
    } else {
        val motivo = evento.motivo?.let { ": $it" }.orEmpty()
        if (dentroDoLivro) {
            "A análise do ${evento.rotuloDoCapitulo} falhou$motivo"
        } else {
            "A análise de $onde falhou$motivo"
        }
    }
}

/**
 * O aviso de um **prompt** gerado (G13), no mesmo espírito do da análise: dentro do livro, só a cena; fora, o livro e o
 * capítulo também. **Sem aviso** quando o modal daquela cena está na tela: ele mostra o próprio aviso, por cima do texto.
 */
private fun descreverAvisoDePrompt(
    evento: EventoDeAnalise,
    local: LocalDoUsuario,
    tituloDoLivro: String?,
    modalDoFrameVisivel: Int?,
): String? {
    if (evento.frameId != null && evento.frameId == modalDoFrameVisivel) return null
    val cena = "«${evento.rotuloDaCena ?: "cena"}»"
    val dentroDoLivro = evento.livroId != null && local.livroId == evento.livroId
    val onde = if (dentroDoLivro || tituloDoLivro == null) "" else " em «$tituloDoLivro», ${evento.rotuloDoCapitulo}"
    return if (evento.sucesso) {
        "Prompt gerado$onde: $cena."
    } else {
        val motivo = evento.motivo?.let { ": $it" }.orEmpty()
        "A geração do prompt$onde de $cena falhou$motivo"
    }
}

/**
 * O aviso de uma **imagem** gerada: no mesmo espírito do de prompt. **Sem aviso** quando o modal daquela cena está na tela ou o painel
 * de IA do capítulo está aberto (os dois já mostram o próprio recado, "Imagem gerada.").
 */
private fun descreverAvisoDeImagem(
    evento: EventoDeAnalise,
    local: LocalDoUsuario,
    painelVisivel: Int?,
    tituloDoLivro: String?,
    modalDoFrameVisivel: Int?,
): String? {
    if (evento.frameId != null && evento.frameId == modalDoFrameVisivel) return null
    if (evento.capituloId == painelVisivel) return null
    val cena = "«${evento.rotuloDaCena ?: "cena"}»"
    val dentroDoLivro = evento.livroId != null && local.livroId == evento.livroId
    val onde = if (dentroDoLivro || tituloDoLivro == null) "" else " em «$tituloDoLivro», ${evento.rotuloDoCapitulo}"
    return when {
        evento.sucesso -> "Imagem gerada$onde: $cena."
        evento.recusada -> "A imagem$onde de $cena foi recusada pelo provedor; abra para ajustar o prompt."
        else -> "A geração da imagem$onde de $cena falhou${evento.motivo?.let { ": $it" }.orEmpty()}"
    }
}

/** "capítulo 3" ou "capítulo «O muro»": como o capítulo aparece nos avisos. */
fun rotuloDoCapituloNoAviso(ordem: Int, titulo: String?): String =
    if (titulo.isNullOrBlank()) "capítulo $ordem" else "capítulo «$titulo»"

/** Quantas vezes o app confere se o servidor terminou o prompt depois de a conexão cair (a cada [INTERVALO_DE_CONFERIR_O_PROMPT_MS]). */
const val TENTATIVAS_DE_CONFERIR_O_PROMPT = 24

const val INTERVALO_DE_CONFERIR_O_PROMPT_MS = 5_000L
