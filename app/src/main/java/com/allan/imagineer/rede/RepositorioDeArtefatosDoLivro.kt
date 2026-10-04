package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/** Os artefatos de **um capítulo** dentro da resposta de um livro (LY7, LY8). */
@Serializable
@Suppress("PropertyName")
data class ArtefatosDeUmCapitulo(
    val capitulo_id: Int,
    val ordem: Int,
    val titulo: String? = null,
    val artefatos: List<Artefato> = emptyList(),
)

/** Os artefatos do livro inteiro, por capítulo, na ordem do livro. Só entram os capítulos que têm algum. */
@Serializable
data class ArtefatosDoLivro(
    val total: Int = 0,
    val capitulos: List<ArtefatosDeUmCapitulo> = emptyList(),
)

/** As telas **Pendências** e **Cenas** do livro (LY7, LY8). Interface, para os ViewModels serem testados com uma versão falsa. Só leitura. */
interface RepositorioDeArtefatosDoLivro {
    /** `GET /livros/{id}/pendencias`: as sugestões ainda não confirmadas, por capítulo. */
    suspend fun pendencias(livroId: Int): ResultadoDaChamada<ArtefatosDoLivro>

    /** `GET /livros/{id}/cenas`: as cenas, em qualquer situação, por capítulo. */
    suspend fun cenas(livroId: Int): ResultadoDaChamada<ArtefatosDoLivro>
}

class RepositorioDeArtefatosDoLivroPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeArtefatosDoLivro {
    override suspend fun pendencias(livroId: Int): ResultadoDaChamada<ArtefatosDoLivro> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.pendenciasDoLivro(livroId) }
    }

    override suspend fun cenas(livroId: Int): ResultadoDaChamada<ArtefatosDoLivro> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.cenasDoLivro(livroId) }
    }
}
