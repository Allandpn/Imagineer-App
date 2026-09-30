package com.allan.imagineer.rede

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Tira a mensagem do corpo de um erro do backend (item 7.3a, incremento 5).
 *
 * O backend devolve `{"detail": "Não existe livro com id 999."}` em 404/413/422,
 * e essa frase já foi escrita para o usuário. Mostrá-la é melhor do que "o
 * servidor respondeu com erro 404".
 *
 * Devolve `null` — e o chamador cai na mensagem genérica — quando não há um
 * `detail` em **texto**: o `detail` de um erro de validação do FastAPI é uma
 * lista de objetos técnicos, e um erro 500 pode vir como texto puro, sem JSON.
 */
fun extrairDetalhe(corpo: String?): String? {
    if (corpo.isNullOrBlank()) return null

    val raiz: JsonElement = try {
        jsonDoImagineer.parseToJsonElement(corpo)
    } catch (erro: kotlinx.serialization.SerializationException) {
        return null
    }

    val detalhe = (raiz as? JsonObject)?.get("detail") as? JsonPrimitive ?: return null
    // Só texto de verdade: um número ou booleano em "detail" não é uma mensagem.
    if (!detalhe.isString) return null
    return detalhe.contentOrNull?.takeIf { it.isNotBlank() }
}
