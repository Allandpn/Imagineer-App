package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.dados.LeitorDeArquivos
import com.allan.imagineer.local.ArmazemDeTextos
import com.allan.imagineer.local.IndiceLocal
import com.allan.imagineer.local.melhorEsforco
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import retrofit2.HttpException

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
     * `GET /livros/{id}`: o livro com a lista de capítulos, sem o texto. Consulta a cópia do
     * aparelho antes (item 7.0a): se o servidor confirma que nada mudou, ou se não há
     * conexão, devolve a cópia.
     */
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

/**
 * A implementação de verdade: lê a URL salva e conversa com o servidor pelo Retrofit,
 * guardando no aparelho (item 7.0a, passo 1) o que já viu.
 */
class RepositorioDeLivrosPeloRetrofit(
    private val provedor: ProvedorDeApi,
    private val leitor: LeitorDeArquivos,
    private val indice: IndiceLocal,
    private val textos: ArmazemDeTextos,
) : RepositorioDeLivros {

    override suspend fun listarLivros(): ResultadoDaChamada<List<LivroResumo>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.livros() }
    }

    /**
     * Regras L5 e L6 do item 7.0a: pergunta ao servidor "mudou desde a revisão N?". Se não
     * mudou (`304`, sem corpo) ou se não há conexão, entrega a cópia do aparelho; se mudou,
     * guarda a nova e entrega.
     */
    override suspend fun abrirLivro(livroId: Int): ResultadoDaChamada<LivroDetalhe> {
        val servidor = provedor.emUso() ?: return provedor.semServidor()
        val guardado = melhorEsforco { indice.livro(servidor.chave, livroId) }

        val resposta = chamarApi {
            // O ETag do servidor é a revisão entre aspas.
            val resposta = servidor.api.livroSeMudou(livroId, guardado?.let { "\"${it.revisao}\"" })
            // 304 não é erro; qualquer outro código que não seja 2xx é, e segue o caminho de sempre.
            if (!resposta.isSuccessful && resposta.code() != 304) throw HttpException(resposta)
            resposta
        }

        return when (resposta) {
            is ResultadoDaChamada.Sucesso -> {
                val livro = resposta.dado.body()
                when {
                    resposta.dado.code() == 304 && guardado != null -> ResultadoDaChamada.Sucesso(guardado.detalhe)
                    livro != null -> {
                        melhorEsforco { indice.guardarLivro(servidor.chave, livro) }
                        ResultadoDaChamada.Sucesso(livro)
                    }
                    else -> ResultadoDaChamada.Falha("O servidor respondeu de um jeito inesperado.")
                }
            }
            // Sem código HTTP = o servidor nem respondeu (sem conexão, Pi desligado...): lê-se
            // o que está no aparelho. Com código (404, 500...), a falha é real e aparece.
            is ResultadoDaChamada.Falha ->
                if (resposta.codigoHttp == null && guardado != null) {
                    ResultadoDaChamada.Sucesso(guardado.detalhe)
                } else {
                    resposta
                }
        }
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
        return chamarApi { api.ajustarLivro(livroId, ajuste.paraJson()) }
    }

    override suspend fun removerLivro(livroId: Int): ResultadoDaChamada<Unit> {
        val servidor = provedor.emUso() ?: return provedor.semServidor()
        val resultado = interpretarRemocao(chamarApi { servidor.api.removerLivro(livroId) })
        if (resultado is ResultadoDaChamada.Sucesso) {
            // O livro deixou de existir: a cópia do aparelho não tem mais o que mostrar (L6).
            melhorEsforco { indice.apagarLivro(servidor.chave, livroId) }
            melhorEsforco { textos.apagarLivro(servidor.chave, livroId) }
        }
        return resultado
    }
}
