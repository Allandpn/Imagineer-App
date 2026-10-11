package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/** O que vale para uma tarefa neste livro e de onde vem (item 7.5g, MV3): [origem] é `livro` (ele trocou) ou `global`. */
@Serializable
data class ModeloEmUso(val modelo: String? = null, val origem: String = "global")

/**
 * `GET /livros/{id}/modelos` (MV3): só o que o livro **trocou** ([trocas], com as chaves da configuração) e o que **vale** em cada tarefa
 * ([valendo]), para a tela dizer "Padrão (global): X" ou o modelo do livro.
 */
@Serializable
data class ModelosDoLivro(
    val trocas: Map<String, String> = emptyMap(),
    val valendo: Map<String, ModeloEmUso> = emptyMap(),
)

/** Os modelos de IA por livro (item 7.5g, MV4). **Nada aqui gasta IA.** */
interface RepositorioDeModelosDoLivro {
    /** `GET /livros/{id}/modelos`. */
    suspend fun ver(livroId: Int): ResultadoDaChamada<ModelosDoLivro>

    /** `PUT /livros/{id}/modelos`: o servidor **substitui** as trocas do livro por estas; o que não vem volta ao global. */
    suspend fun trocar(livroId: Int, trocas: Map<String, String>): ResultadoDaChamada<ModelosDoLivro>
}

class RepositorioDeModelosDoLivroPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeModelosDoLivro {
    override suspend fun ver(livroId: Int): ResultadoDaChamada<ModelosDoLivro> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.modelosDoLivro(livroId) }
    }

    override suspend fun trocar(livroId: Int, trocas: Map<String, String>): ResultadoDaChamada<ModelosDoLivro> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.trocarModelosDoLivro(livroId, trocas) }
    }
}
