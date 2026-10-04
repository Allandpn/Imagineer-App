package com.allan.imagineer.local

/** O registro de que um livro está **Baixado** (PL4): quando, quanto ocupa e quais imagens foram baixadas (para poder removê-las). */
data class DownloadGuardado(val livroId: Int, val baixadoEm: Long, val bytes: Long, val imagensIds: List<Int>)

/**
 * Quais livros estão Baixados. Interface, para o baixador ser testado com uma versão em memória; a de verdade é
 * [RegistroDeDownloadsPeloRoom]. Qualquer função pode lançar exceção (banco corrompido): quem chama trata como "não tenho" (A10).
 */
interface RegistroDeDownloads {
    suspend fun baixado(chave: ChaveDoCache, livroId: Int): DownloadGuardado?
    suspend fun guardar(chave: ChaveDoCache, download: DownloadGuardado)
    suspend fun apagar(chave: ChaveDoCache, livroId: Int)

    /** Todos os livros Baixados deste servidor. */
    suspend fun todos(chave: ChaveDoCache): List<DownloadGuardado>
}

class RegistroDeDownloadsPeloRoom(private val dao: DaoLocal) : RegistroDeDownloads {

    override suspend fun baixado(chave: ChaveDoCache, livroId: Int): DownloadGuardado? {
        val linha = dao.download(chave.identificador, livroId) ?: return null
        return DownloadGuardado(linha.livroId, linha.baixadoEm, linha.bytes, linha.imagensIds.split(",").mapNotNull { it.toIntOrNull() })
    }

    override suspend fun guardar(chave: ChaveDoCache, download: DownloadGuardado) {
        dao.guardarDownload(
            DownloadLocal(chave.identificador, download.livroId, download.baixadoEm, download.bytes, download.imagensIds.joinToString(",")),
        )
    }

    override suspend fun apagar(chave: ChaveDoCache, livroId: Int) {
        dao.apagarDownload(chave.identificador, livroId)
    }

    override suspend fun todos(chave: ChaveDoCache): List<DownloadGuardado> =
        dao.downloads(chave.identificador).map {
            DownloadGuardado(it.livroId, it.baixadoEm, it.bytes, it.imagensIds.split(",").mapNotNull { id -> id.toIntOrNull() })
        }
}

/** O [EspacoLocal] de verdade, sobre o Room. */
class EspacoLocalPeloRoom(private val dao: DaoLocal) : EspacoLocal {
    override suspend fun livros(chave: ChaveDoCache): List<Pair<Int, String>> =
        dao.livros(chave.identificador).map { linha ->
            linha.livroId to (runCatching { com.allan.imagineer.rede.jsonDoImagineer.decodeFromString<com.allan.imagineer.rede.LivroDetalhe>(linha.detalheJson).titulo }.getOrNull() ?: "Livro ${linha.livroId}")
        }

    override suspend fun bytesDeTextos(chave: ChaveDoCache): Map<Int, Long> =
        dao.textos(chave.identificador).groupBy { it.livroId }.mapValues { (_, linhas) -> linhas.sumOf { it.bytes } }

    override suspend fun apagarRegistrosDeTexto(chave: ChaveDoCache, livroId: Int) {
        dao.apagarTextosDoLivro(chave.identificador, livroId)
    }
}
