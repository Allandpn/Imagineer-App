package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.dados.LeitorDeArquivos
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody

/**
 * O que as telas precisam saber fazer com livros (item 7.2 em diante).
 *
 * Interface, e não classe direta, para o ViewModel ser testado com uma versão
 * falsa que devolve o que o teste combinar — sem rede e sem aparelho.
 */
interface RepositorioDeLivros {
    /** `GET /livros`, na ordem do servidor (alfabética por título, item 6.2). */
    suspend fun listarLivros(): ResultadoDaChamada<List<LivroResumo>>

    /** `GET /livros/{id}`: o livro com a lista de capítulos, sem o texto. */
    suspend fun abrirLivro(livroId: Int): ResultadoDaChamada<LivroDetalhe>

    /**
     * `POST /livros`: importa o EPUB, lendo o arquivo aos poucos.
     *
     * @param aoProgredir (bytes enviados, total ou nulo), chamada de uma thread de rede.
     */
    suspend fun importarLivro(
        arquivo: ArquivoEscolhido,
        aoProgredir: (enviados: Long, total: Long?) -> Unit,
    ): ResultadoDaChamada<RespostaImportacao>

    /** `PATCH /livros/{id}`: corrige título e/ou autor; devolve o livro completo. */
    suspend fun ajustarLivro(livroId: Int, ajuste: LivroAjuste): ResultadoDaChamada<LivroDetalhe>

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
    private val provedor: ProvedorDeApi,
    private val leitor: LeitorDeArquivos,
) : RepositorioDeLivros {

    override suspend fun listarLivros(): ResultadoDaChamada<List<LivroResumo>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.livros() }
    }

    override suspend fun abrirLivro(livroId: Int): ResultadoDaChamada<LivroDetalhe> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.livro(livroId) }
    }

    override suspend fun importarLivro(
        arquivo: ArquivoEscolhido,
        aoProgredir: (enviados: Long, total: Long?) -> Unit,
    ): ResultadoDaChamada<RespostaImportacao> {
        val api = provedor.obter() ?: return provedor.semServidor()

        // Abre o arquivo ANTES do pedido: se a permissão se perdeu ou o arquivo foi
        // movido, a mensagem tem de ser essa — e não a de falha de rede, que seria
        // enganosa.
        val entrada = leitor.abrir(arquivo.uri)
            ?: return ResultadoDaChamada.Falha("Não consegui abrir o arquivo escolhido.")

        val corpo = CorpoComProgresso(
            entrada = entrada,
            tipo = "application/epub+zip".toMediaType(),
            tamanho = arquivo.tamanho,
            aoProgredir = aoProgredir,
        )
        return try {
            chamarApi {
                api.importarLivro(MultipartBody.Part.createFormData("arquivo", arquivo.nome, corpo))
            }
        } finally {
            // Se o pedido falhou antes de ler o corpo, o fluxo ficaria aberto.
            entrada.close()
        }
    }

    override suspend fun ajustarLivro(livroId: Int, ajuste: LivroAjuste): ResultadoDaChamada<LivroDetalhe> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.ajustarLivro(livroId, ajuste) }
    }

    override suspend fun removerLivro(livroId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return interpretarRemocao(chamarApi { api.removerLivro(livroId) })
    }
}
