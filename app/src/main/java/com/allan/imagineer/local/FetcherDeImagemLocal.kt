package com.allan.imagineer.local

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * O passo **antes da rede** do carregador de imagens (PL2, regra A3): se a imagem pedida (`.../imagens/{id}/arquivo?tamanho=T`) está
 * no armazém, entrega o **arquivo local**; senão devolve `null` e o carregador segue para a rede, como sempre. As telas não mudam.
 */
class FetcherDeImagemLocal(private val arquivo: File) : Fetcher {

    override suspend fun fetch(): FetchResult = SourceFetchResult(
        source = ImageSource(file = arquivo.toOkioPath(), fileSystem = FileSystem.SYSTEM),
        mimeType = null,
        dataSource = DataSource.DISK,
    )

    class Factory(
        private val armazem: ArmazemDeImagens,
        /** Sem conexão (PL19), a capa de qualquer revisão vale; com conexão só a da revisão pedida, para uma capa trocada não aparecer velha. */
        private val semConexao: () -> Boolean = { false },
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val endereco = data.toString()
            val chave: ChaveDoCache
            val arquivo: File?
            val daImagem = analisarEnderecoDeImagem(endereco)
            if (daImagem != null) {
                chave = ChaveDoCache(servidor = daImagem.servidor)
                arquivo = armazem.arquivo(chave, daImagem.imagemId, daImagem.tamanho)
            } else {
                // A capa do livro (PL15) entra pelo mesmo caminho: o "tamanho" dela é o da revisão pedida.
                val daCapa = analisarEnderecoDeCapa(endereco) ?: return null
                chave = ChaveDoCache(servidor = daCapa.servidor)
                arquivo = armazem.arquivo(chave, daCapa.imagemId, daCapa.tamanho)
                    ?: if (semConexao()) armazem.qualquerCapa(chave, daCapa.imagemId) else null
            }
            if (arquivo == null) return null
            return FetcherDeImagemLocal(arquivo)
        }
    }
}
