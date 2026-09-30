package com.allan.imagineer.local

import java.security.MessageDigest

/**
 * De quem é uma cópia guardada no aparelho: **servidor + conta** (item 7.0a, preparação
 * "a" para contas de usuário).
 *
 * O id de um livro só vale dentro do seu servidor: o livro 3 de um endereço não é o
 * livro 3 de outro. Por isso toda linha do índice e toda pasta de texto leva esta chave,
 * e trocar o endereço do servidor no app nunca mistura os dois acervos. A [conta] hoje é
 * sempre a mesma; o espaço já existe para quando houver login.
 */
data class ChaveDoCache(val servidor: String, val conta: String = CONTA_UNICA) {

    /**
     * Um nome curto e seguro para usar como coluna e como nome de pasta: os 12 primeiros
     * caracteres do SHA-256 de "servidor|conta". A URL crua tem `:` e `/`, que não servem
     * em nome de pasta; 48 bits bastam para dois servidores nunca colidirem.
     */
    val identificador: String by lazy {
        MessageDigest.getInstance("SHA-256")
            .digest("$servidor|$conta".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
    }

    companion object {
        /** Enquanto não há contas, todo mundo é a mesma "conta". */
        const val CONTA_UNICA = "unica"
    }
}
