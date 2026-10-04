package com.allan.imagineer.local

/** Um livro com algo guardado no aparelho (PL10): o texto do nível Leve e o que foi Baixado. */
data class EspacoPorLivro(
    val livroId: Int,
    val titulo: String,
    /** O texto guardado de passagem (nível Leve); 0 se o livro está Baixado (aí o texto conta no Baixado). */
    val bytesLeve: Long,
    /** Texto e imagens baixados (nível Baixado); 0 se não está Baixado. */
    val bytesBaixado: Long,
    val baixado: Boolean,
) {
    val total: Long get() = bytesLeve + bytesBaixado
}

/** O que o índice local sabe, para a tela de armazenamento. Interface, para testar sem Room. */
interface EspacoLocal {
    /** Os livros que o aparelho já viu, com o título. */
    suspend fun livros(chave: ChaveDoCache): List<Pair<Int, String>>

    /** Quantos bytes de texto cada livro tem guardados. */
    suspend fun bytesDeTextos(chave: ChaveDoCache): Map<Int, Long>

    /** Esquece os registros de texto do livro (os arquivos o [ArmazemDeTextos] apaga). */
    suspend fun apagarRegistrosDeTexto(chave: ChaveDoCache, livroId: Int)
}

/**
 * A tela **Armazenamento** (PL10, regra A11): quanto cada livro ocupa no aparelho, em dois níveis, e como liberar. **Limpar cache** só mexe
 * no nível Leve de um livro que **não** está Baixado; o Baixado só sai por **Remover download** (nunca sozinho).
 */
class GerenteDeArmazenamento(
    private val fonte: FonteDoDownload,
    private val espaco: EspacoLocal,
    private val textos: ArmazemDeTextos,
    private val registro: RegistroDeDownloads,
    private val baixador: BaixadorDeLivros,
) {
    /** O espaço de cada livro, do que mais ocupa para o que menos. Livros sem nada no aparelho ficam de fora. */
    suspend fun listar(): List<EspacoPorLivro> {
        val chave = fonte.chave() ?: return emptyList()
        val titulos = espaco.livros(chave).toMap()
        val textosPorLivro = espaco.bytesDeTextos(chave)
        val baixados = registro.todos(chave).associateBy { it.livroId }
        return (titulos.keys + baixados.keys + textosPorLivro.keys).distinct().map { id ->
            val baixado = baixados[id]
            EspacoPorLivro(
                livroId = id,
                titulo = titulos[id] ?: "Livro $id",
                bytesLeve = if (baixado == null) textosPorLivro[id] ?: 0L else 0L,
                bytesBaixado = baixado?.bytes ?: 0L,
                baixado = baixado != null,
            )
        }.filter { it.total > 0 }.sortedByDescending { it.total }
    }

    /** Apaga o texto guardado de passagem do livro. Devolve quantos bytes liberou; 0 se o livro está Baixado (nada é apagado). */
    suspend fun limparCache(livroId: Int): Long {
        val chave = fonte.chave() ?: return 0L
        if (registro.baixado(chave, livroId) != null) return 0L
        val antes = espaco.bytesDeTextos(chave)[livroId] ?: 0L
        textos.apagarLivro(chave, livroId)
        espaco.apagarRegistrosDeTexto(chave, livroId)
        return antes
    }

    /** Limpa o nível Leve de **todos** os livros que não estão Baixados. Devolve o total liberado. */
    suspend fun limparTodoOCache(): Long = listar().filter { !it.baixado }.sumOf { limparCache(it.livroId) }

    /** "Remover download" de um livro Baixado. Devolve quantos bytes liberou. */
    suspend fun removerDownload(livroId: Int): Long {
        val chave = fonte.chave() ?: return 0L
        val tinha = registro.baixado(chave, livroId)?.bytes ?: 0L
        baixador.remover(livroId)
        return tinha
    }
}
