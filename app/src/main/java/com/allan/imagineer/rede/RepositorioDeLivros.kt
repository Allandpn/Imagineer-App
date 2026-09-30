package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import kotlinx.coroutines.flow.first

/**
 * O que as telas precisam saber fazer com livros (item 7.2 em diante).
 *
 * Interface, e não classe direta, para o ViewModel ser testado com uma versão
 * falsa que devolve o que o teste combinar — sem rede e sem aparelho.
 */
interface RepositorioDeLivros {
    /** `GET /livros`, na ordem do servidor (alfabética por título, item 6.2). */
    suspend fun listarLivros(): ResultadoDaChamada<List<LivroResumo>>

    /**
     * `DELETE /livros/{id}`: remove o livro e tudo que depende dele. Um `404`
     * (o livro já não existe) conta como sucesso — ver [interpretarRemocao].
     */
    suspend fun removerLivro(livroId: Int): ResultadoDaChamada<Unit>
}

/**
 * Decide o que a resposta de uma remoção significa.
 *
 * O objetivo do usuário é "este livro não existir mais". Se o servidor diz que ele
 * já não existe (`404` — removido de outro lugar, por exemplo pelo `/docs`), o
 * objetivo está cumprido, e mostrar erro seria só ruído. Função pura, fora do
 * repositório, para poder ser testada sem rede.
 */
fun interpretarRemocao(resultado: ResultadoDaChamada<Unit>): ResultadoDaChamada<Unit> {
    val naoExiste = resultado is ResultadoDaChamada.Falha && resultado.codigoHttp == 404
    return if (naoExiste) ResultadoDaChamada.Sucesso(Unit) else resultado
}

/** A implementação de verdade: lê a URL salva e conversa com o servidor pelo Retrofit. */
class RepositorioDeLivrosPeloRetrofit(
    private val armazenamento: ArmazenamentoDeConfiguracao,
) : RepositorioDeLivros {

    // Montar um cliente HTTP a cada chamada jogaria fora o pool de conexões dele.
    // Guarda o último par (URL, API) e só remonta se a URL salva mudou.
    private var urlEmUso: String? = null
    private var apiEmUso: ApiImagineer? = null

    override suspend fun listarLivros(): ResultadoDaChamada<List<LivroResumo>> {
        val api = obterApi()
            ?: return ResultadoDaChamada.Falha("O endereço do servidor ainda não foi configurado.")
        return chamarApi { api.livros() }
    }

    override suspend fun removerLivro(livroId: Int): ResultadoDaChamada<Unit> {
        val api = obterApi()
            ?: return ResultadoDaChamada.Falha("O endereço do servidor ainda não foi configurado.")
        return interpretarRemocao(chamarApi { api.removerLivro(livroId) })
    }

    private suspend fun obterApi(): ApiImagineer? {
        val url = armazenamento.urlDoServidor.first() ?: return null
        if (url != urlEmUso) {
            apiEmUso = criarApi(url)
            urlEmUso = url
        }
        return apiEmUso
    }
}
