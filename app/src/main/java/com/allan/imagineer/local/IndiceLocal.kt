package com.allan.imagineer.local

import com.allan.imagineer.rede.LivroDetalhe

/** Um livro guardado no aparelho: a última versão vista e a revisão dela. */
data class LivroGuardado(val revisao: Int, val detalhe: LivroDetalhe)

/** O registro de que o texto de um capítulo está no aparelho. */
data class TextoGuardado(val capituloId: Int, val livroId: Int, val bytes: Long)

/**
 * O índice do que há no aparelho (item 7.0a, passo 1): quais livros e quais textos.
 *
 * Interface, como os demais pontos de contato com o mundo de fora, para a regra de
 * "quando usar o aparelho e quando ir à rede" ser testada com uma versão em memória.
 * A versão de verdade é [IndiceLocalPeloRoom].
 *
 * Toda função pode lançar exceção (banco corrompido, disco cheio): quem chama trata
 * como "não tenho" — a cópia local é descartável (regras A10 e L8).
 */
interface IndiceLocal {

    /** O livro guardado, ou `null` se nunca foi visto neste servidor. */
    suspend fun livro(chave: ChaveDoCache, livroId: Int): LivroGuardado?

    /** Guarda (ou substitui) o livro visto. */
    suspend fun guardarLivro(chave: ChaveDoCache, detalhe: LivroDetalhe)

    /** O registro do texto do capítulo, ou `null` se não foi guardado. */
    suspend fun texto(chave: ChaveDoCache, capituloId: Int): TextoGuardado?

    /** Registra que o texto do capítulo já está gravado em arquivo. */
    suspend fun registrarTexto(chave: ChaveDoCache, texto: TextoGuardado)

    /** Esquece o livro e os registros de texto dele. */
    suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int)
}
