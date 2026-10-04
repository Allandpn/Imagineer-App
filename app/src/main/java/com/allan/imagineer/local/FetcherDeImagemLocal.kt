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

    class Factory(private val armazem: ArmazemDeImagens) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val alvo = analisarEnderecoDeImagem(data.toString()) ?: return null
            val arquivo = armazem.arquivo(ChaveDoCache(servidor = alvo.servidor), alvo.imagemId, alvo.tamanho) ?: return null
            return FetcherDeImagemLocal(arquivo)
        }
    }
}
