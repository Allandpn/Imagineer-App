package com.allan.imagineer

import android.util.Log

/**
 * Rastro **temporário** para achar o defeito D3 (a seta nativa de voltar da ficha do personagem "pisca" e volta à
 * ficha): registra a navegação e o abrir e fechar do modal da sugestão, com a hora, para ver a ordem exata.
 *
 * No Android Studio, filtre o Logcat por `ImagineerNav` e reproduza o erro. **Sai quando o D3 for resolvido.**
 * Nunca registra texto de livro nem chaves: só nomes de telas e ids.
 */
object Rastro {
    const val TAG = "ImagineerNav"

    /** Registra [mensagem]; com [comPilha], também quem chamou (para saber *quem* mandou fechar, por exemplo). */
    fun d(mensagem: String, comPilha: Boolean = false) {
        try {
            if (comPilha) Log.d(TAG, mensagem, Throwable("chamada por")) else Log.d(TAG, mensagem)
        } catch (_: RuntimeException) {
            // Nos testes unitários o Log do Android não existe: o rastro é só do aparelho.
        }
    }
}
