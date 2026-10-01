package com.allan.imagineer.analise

import com.allan.imagineer.rede.RepositorioDeSugestoes
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
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
)

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
) {
    private val trava = Any()
    private val emAndamento = mutableMapOf<Int, Deferred<ResultadoDaChamada<SugestoesDeCapitulo>>>()
    private val livrosDosCapitulos = mutableMapOf<Int, Int>()

    private val _eventos = MutableSharedFlow<EventoDeAnalise>(extraBufferCapacity = 16)

    /** Um evento por análise que termina, com sucesso ou não. */
    val eventos: SharedFlow<EventoDeAnalise> = _eventos.asSharedFlow()

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
): String? {
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

/** "capítulo 3" ou "capítulo «O muro»": como o capítulo aparece nos avisos. */
fun rotuloDoCapituloNoAviso(ordem: Int, titulo: String?): String =
    if (titulo.isNullOrBlank()) "capítulo $ordem" else "capítulo «$titulo»"
