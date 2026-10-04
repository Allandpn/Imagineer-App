package com.allan.imagineer.local

import kotlin.coroutines.cancellation.CancellationException

/**
 * Executa uma operação **local** (banco ou arquivo) que pode falhar sem que isso importe:
 * devolve o resultado, ou `null` se deu qualquer erro (disco cheio, banco corrompido,
 * arquivo ilegível).
 *
 * É a tradução em código da regra "a cópia local é descartável" (A10, L8): um problema
 * no aparelho nunca pode impedir a leitura, então vale como "não tenho" e o app vai à
 * rede. O **cancelamento** da corrotina passa direto, de propósito — engoli-lo deixaria
 * uma tela já fechada continuando a trabalhar.
 */
suspend fun <T> melhorEsforco(operacao: suspend () -> T): T? =
    try {
        operacao()
    } catch (cancelamento: CancellationException) {
        throw cancelamento
    } catch (erro: Exception) {
        null
    }
