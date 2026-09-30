package com.allan.imagineer.local

import com.allan.imagineer.rede.LivroDetalhe

/**
 * Versões em memória do que, no aparelho, é banco e disco — para testar a regra de "quando
 * usar o aparelho e quando ir à rede" sem Android. [quebrado] simula banco corrompido.
 */
class IndiceEmMemoria : IndiceLocal {
    val livros = mutableMapOf<Pair<String, Int>, LivroGuardado>()
    val textos = mutableMapOf<Pair<String, Int>, TextoGuardado>()
    var quebrado = false

    private fun conferir() {
        if (quebrado) throw IllegalStateException("banco corrompido")
    }

    override suspend fun livro(chave: ChaveDoCache, livroId: Int): LivroGuardado? {
        conferir()
        return livros[chave.identificador to livroId]
    }

    override suspend fun guardarLivro(chave: ChaveDoCache, detalhe: LivroDetalhe) {
        conferir()
        livros[chave.identificador to detalhe.id] = LivroGuardado(detalhe.revisao, detalhe)
    }

    override suspend fun texto(chave: ChaveDoCache, capituloId: Int): TextoGuardado? {
        conferir()
        return textos[chave.identificador to capituloId]
    }

    override suspend fun registrarTexto(chave: ChaveDoCache, texto: TextoGuardado) {
        conferir()
        textos[chave.identificador to texto.capituloId] = texto
    }

    override suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int) {
        conferir()
        livros.remove(chave.identificador to livroId)
        textos.entries.removeAll { it.key.first == chave.identificador && it.value.livroId == livroId }
    }
}

class ArmazemEmMemoria : ArmazemDeTextos {
    val arquivos = mutableMapOf<Triple<String, Int, Int>, String>()
    var discoCheio = false

    override suspend fun ler(chave: ChaveDoCache, livroId: Int, capituloId: Int): String? =
        arquivos[Triple(chave.identificador, livroId, capituloId)]

    override suspend fun gravar(chave: ChaveDoCache, livroId: Int, capituloId: Int, texto: String): Long {
        if (discoCheio) throw java.io.IOException("disco cheio")
        arquivos[Triple(chave.identificador, livroId, capituloId)] = texto
        return texto.toByteArray().size.toLong()
    }

    override suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int) {
        arquivos.keys.removeAll { it.first == chave.identificador && it.second == livroId }
    }
}
