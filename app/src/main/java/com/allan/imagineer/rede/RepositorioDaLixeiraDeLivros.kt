package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/** Um livro movido para a lixeira, com o que ele leva junto (LT2). Espelha o `LivroNaLixeira` do servidor. */
@Serializable
@Suppress("PropertyName")
data class LivroNaLixeira(
    val id: Int,
    val titulo: String,
    val autor: String? = null,
    val apagado_em: String,
    val total_de_capitulos: Int = 0,
    val total_de_imagens: Int = 0,
    val tamanho_das_imagens_em_bytes: Long = 0,
    val tem_capa: Boolean = false,
)

/** Os livros da lixeira, do apagado mais recentemente para o mais antigo. */
@Serializable
@Suppress("PropertyName")
data class LivrosDaLixeira(
    val livros: List<LivroNaLixeira> = emptyList(),
    val total_em_bytes: Long = 0,
)

/** A lixeira de livros (LT2). Interface, para os ViewModels serem testados com uma versão falsa. **Nada aqui gasta IA.** */
interface RepositorioDaLixeiraDeLivros {
    /** `GET /lixeira/livros`. */
    suspend fun listar(): ResultadoDaChamada<LivrosDaLixeira>

    /** `POST /lixeira/livros/{id}/restaurar`: o livro volta à biblioteca inteiro. */
    suspend fun restaurar(livroId: Int): ResultadoDaChamada<Unit>

    /** `DELETE /lixeira/livros/{id}`: apaga de vez, com capítulos, elementos, frames, prompts e imagens. **Não tem volta.** */
    suspend fun apagarDeVez(livroId: Int): ResultadoDaChamada<Unit>

    /** `DELETE /lixeira/livros`: apaga de vez **todos** os livros da lixeira. */
    suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada>
}

class RepositorioDaLixeiraDeLivrosPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDaLixeiraDeLivros {
    override suspend fun listar(): ResultadoDaChamada<LivrosDaLixeira> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.lixeiraDeLivros() }
    }

    override suspend fun restaurar(livroId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.restaurarLivroDaLixeira(livroId); Unit }
    }

    override suspend fun apagarDeVez(livroId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.apagarLivroDeVez(livroId) }
    }

    override suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.esvaziarLixeiraDeLivros() }
    }
}
