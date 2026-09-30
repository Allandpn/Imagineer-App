package com.allan.imagineer.dados

import java.net.URI

/**
 * Transforma o que o usuário digitou no endereço do servidor num formato único.
 *
 * Regras (item 7.3a da especificação):
 * - apara espaços;
 * - sem esquema (`://`), assume `http://`;
 * - só aceita `http` e `https`;
 * - remove barras no final;
 * - recusa texto vazio ou sem endereço (host).
 *
 * É uma função pura — não mexe em rede nem em disco — justamente para poder ser
 * testada de forma barata, com muitos exemplos.
 *
 * @return a URL normalizada, sem barra final (ex.: `http://100.64.0.5:8000`), ou
 * `null` se o texto não for um endereço utilizável.
 */
fun normalizarUrl(texto: String): String? {
    val aparado = texto.trim()
    if (aparado.isEmpty()) return null

    val comEsquema = if ("://" in aparado) aparado else "http://$aparado"

    val uri = try {
        URI(comEsquema)
    } catch (erro: java.net.URISyntaxException) {
        return null
    }

    val esquema = uri.scheme?.lowercase()
    if (esquema != "http" && esquema != "https") return null
    if (uri.host.isNullOrBlank()) return null

    return comEsquema.trimEnd('/')
}
