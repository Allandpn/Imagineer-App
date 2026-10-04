package com.allan.imagineer.telas.capitulo.voz

import com.allan.imagineer.telas.capitulo.ParagrafoDoTexto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// A leitura em voz alta de um capítulo (RL18).

/** O que o narrador precisa de uma voz: falar um trecho, parar e mudar a velocidade. A do Android está em [MotorDeVozDoAndroid]. */
interface MotorDeVoz {
    /** Fala [texto] (**trocando** o que estiver sendo falado). Ao terminar, o motor chama [NarradorDoCapitulo.aoTerminarDeFalar] com o [id]. */
    fun falar(id: String, texto: String)

    fun parar()

    fun definirVelocidade(velocidade: Float)

    fun encerrar()
}

enum class SituacaoDaNarracao { PARADA, FALANDO, PAUSADA }

/** Onde está a narração: [paragrafo] é o índice na lista de parágrafos do capítulo. */
data class EstadoDaNarracao(
    val situacao: SituacaoDaNarracao = SituacaoDaNarracao.PARADA,
    val paragrafo: Int = 0,
    val velocidade: Float = 1f,
)

/**
 * Lê o capítulo **um parágrafo por vez** (RL18). O Android não tem "pausar" de verdade na voz, então **pausar é parar e lembrar do
 * parágrafo**: retomar recomeça **desse parágrafo**. Cada fala leva um id com o número da **geração** (sobe a cada parar/trocar de
 * parágrafo): o aviso de "terminei" de uma fala que já foi interrompida chega depois e é **ignorado**, senão ele pularia um parágrafo.
 */
class NarradorDoCapitulo(
    private val motor: MotorDeVoz,
    private val paragrafos: List<ParagrafoDoTexto>,
    velocidade: Float = 1f,
) {
    private val _estado = MutableStateFlow(EstadoDaNarracao(velocidade = velocidade))
    val estado: StateFlow<EstadoDaNarracao> = _estado.asStateFlow()

    private var geracao = 0
    private var partes: List<String> = emptyList()

    /** Começa a ler do parágrafo que contém a posição [posicao] (UTF-16 desde o início do capítulo). */
    fun iniciarEm(posicao: Int) {
        iniciar(paragrafos.indexOfLast { it.inicio <= posicao }.coerceAtLeast(0))
    }

    fun iniciar(indice: Int) {
        if (paragrafos.isEmpty()) return
        falarParagrafo(indice.coerceIn(0, paragrafos.lastIndex), 0)
    }

    fun pausar() {
        if (_estado.value.situacao != SituacaoDaNarracao.FALANDO) return
        geracao++
        motor.parar()
        _estado.value = _estado.value.copy(situacao = SituacaoDaNarracao.PAUSADA)
    }

    /** Retoma **do começo do parágrafo** em que parou. */
    fun retomar() {
        if (_estado.value.situacao == SituacaoDaNarracao.PAUSADA) iniciar(_estado.value.paragrafo)
    }

    fun parar() {
        geracao++
        motor.parar()
        _estado.value = _estado.value.copy(situacao = SituacaoDaNarracao.PARADA)
    }

    fun proximo() = irPara(_estado.value.paragrafo + 1)

    fun anterior() = irPara(_estado.value.paragrafo - 1)

    /** Muda de parágrafo mantendo a situação: falando continua falando; pausada só muda o lugar. */
    private fun irPara(indice: Int) {
        if (indice !in paragrafos.indices) return
        if (_estado.value.situacao == SituacaoDaNarracao.FALANDO) falarParagrafo(indice, 0)
        else _estado.value = _estado.value.copy(paragrafo = indice)
    }

    fun mudarVelocidade(velocidade: Float) {
        val nova = velocidade.coerceIn(VELOCIDADE_MINIMA, VELOCIDADE_MAXIMA)
        motor.definirVelocidade(nova)
        _estado.value = _estado.value.copy(velocidade = nova)
        // A velocidade vale para a próxima fala; recomeça o parágrafo para a mudança ser ouvida na hora.
        if (_estado.value.situacao == SituacaoDaNarracao.FALANDO) falarParagrafo(_estado.value.paragrafo, 0)
    }

    /** O motor avisa que a fala [id] terminou: fala a próxima parte ou o próximo parágrafo; ao fim do capítulo, para. */
    fun aoTerminarDeFalar(id: String) {
        val (g, indice, parte) = decifrar(id) ?: return
        if (g != geracao || _estado.value.situacao != SituacaoDaNarracao.FALANDO) return
        if (parte + 1 < partes.size) falarParagrafo(indice, parte + 1, mesmaGeracao = true)
        else if (indice + 1 <= paragrafos.lastIndex) falarParagrafo(indice + 1, 0)
        else parar()
    }

    /** O motor não conseguiu falar: a narração para (quem chama avisa a pessoa). */
    fun aoFalhar() = parar()

    fun encerrar() {
        geracao++
        motor.encerrar()
    }

    private fun falarParagrafo(indice: Int, parte: Int, mesmaGeracao: Boolean = false) {
        if (!mesmaGeracao) {
            geracao++
            partes = dividirParaFalar(paragrafos[indice].texto)
        }
        if (partes.isEmpty()) {  // um parágrafo sem nada a falar (só símbolos): pula para o seguinte
            if (indice + 1 <= paragrafos.lastIndex) falarParagrafo(indice + 1, 0) else parar()
            return
        }
        _estado.value = _estado.value.copy(situacao = SituacaoDaNarracao.FALANDO, paragrafo = indice)
        motor.falar("$geracao:$indice:$parte", partes[parte])
    }

    private fun decifrar(id: String): Triple<Int, Int, Int>? {
        val p = id.split(":").mapNotNull { it.toIntOrNull() }
        return if (p.size == 3) Triple(p[0], p[1], p[2]) else null
    }

    companion object {
        const val VELOCIDADE_MINIMA = 0.5f
        const val VELOCIDADE_MAXIMA = 2.0f
        const val PASSO_DA_VELOCIDADE = 0.25f
    }
}
