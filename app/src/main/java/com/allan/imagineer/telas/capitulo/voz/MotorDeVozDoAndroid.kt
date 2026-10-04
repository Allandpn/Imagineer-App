package com.allan.imagineer.telas.capitulo.voz

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * A voz do Android (`TextToSpeech`) para o [NarradorDoCapitulo] (RL18). Os avisos da voz chegam em outra thread: aqui voltam para a
 * **thread principal** antes de tocar no narrador, que não é feito para dois lugares ao mesmo tempo.
 *
 * @param idioma o idioma do livro (ex.: `pt`, `en-US`); sem ele, ou sem a voz dele instalada, vale o idioma do aparelho.
 * @param aoPronto `true` quando a voz ficou pronta; `false` se o aparelho não tem voz nenhuma instalada.
 * @param aoAvisar um recado para a pessoa ("voz do idioma não instalada, usando a padrão").
 */
class MotorDeVozDoAndroid(
    contexto: Context,
    private val idioma: String?,
    private val aoPronto: (Boolean) -> Unit,
    private val aoAvisar: (String) -> Unit,
) : MotorDeVoz {

    private val principal = Handler(Looper.getMainLooper())
    private var narrador: NarradorDoCapitulo? = null
    private var pronta = false
    private var velocidade = 1f
    private var pedidoAntesDeEstarPronta: Pair<String, String>? = null

    private val voz: TextToSpeech = TextToSpeech(contexto.applicationContext) { situacao ->
        principal.post {
            pronta = situacao == TextToSpeech.SUCCESS
            if (pronta) escolherIdioma()
            aoPronto(pronta)
            if (pronta) pedidoAntesDeEstarPronta?.let { (id, texto) -> falar(id, texto) }
            pedidoAntesDeEstarPronta = null
        }
    }

    init {
        voz.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                principal.post { utteranceId?.let { narrador?.aoTerminarDeFalar(it) } }
            }

            @Deprecated("A versão com o código do erro é a usada nas versões novas do Android.")
            override fun onError(utteranceId: String?) {
                principal.post { narrador?.aoFalhar() }
            }
        })
    }

    /** O narrador que recebe o "terminei". Ligado depois de criado, porque um precisa do outro. */
    fun ligar(narrador: NarradorDoCapitulo) {
        this.narrador = narrador
    }

    private fun escolherIdioma() {
        val desejado = idioma?.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it.replace('_', '-')) } ?: return
        val resultado = voz.setLanguage(desejado)
        if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
            voz.language = Locale.getDefault()
            aoAvisar("A voz de ${desejado.displayLanguage} não está instalada; usando a padrão do aparelho.")
        }
    }

    override fun falar(id: String, texto: String) {
        if (!pronta) {  // ainda iniciando: guarda o último pedido e fala assim que ficar pronta
            pedidoAntesDeEstarPronta = id to texto
            return
        }
        voz.setSpeechRate(velocidade)
        voz.speak(texto, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
    }

    override fun parar() {
        pedidoAntesDeEstarPronta = null
        voz.stop()
    }

    override fun definirVelocidade(velocidade: Float) {
        this.velocidade = velocidade
    }

    override fun encerrar() {
        narrador = null
        voz.stop()
        voz.shutdown()
    }
}
