package com.allan.imagineer.rede

import java.time.Instant
import kotlinx.serialization.Serializable

/** Onde a pessoa parou num livro: o capítulo e o início do parágrafo (UTF-16, como o servidor conta). Espelha o `MarcadorResposta`. */
@Serializable
@Suppress("PropertyName")
data class Marcador(
    val capitulo_id: Int,
    val posicao_no_texto: Int,
    val lido_em: String = "",
)

/** O que `GET /livros/{id}/marcador` devolve: `marcador` nulo = ainda não leu nada. */
@Serializable
data class MarcadorDoLivro(val marcador: Marcador? = null)

/** O corpo de `PUT /livros/{id}/marcador`: [lido_em] é a hora **do aparelho**, com fuso; o mais recente vence. */
@Serializable
@Suppress("PropertyName")
data class MarcadorGravacao(
    val capitulo_id: Int,
    val posicao_no_texto: Int,
    val lido_em: String,
)

/** O marcador de leitura (LE2, LE3). Interface, para os ViewModels serem testados com uma versão falsa. */
interface RepositorioDeMarcador {
    /** `GET /livros/{id}/marcador`: onde parou, ou `null` se nunca leu. */
    suspend fun ler(livroId: Int): ResultadoDaChamada<Marcador?>

    /** `PUT /livros/{id}/marcador`: grava onde a pessoa está agora. Falhar não atrapalha a leitura. */
    suspend fun gravar(livroId: Int, capituloId: Int, posicao: Int): ResultadoDaChamada<Unit>
}

class RepositorioDeMarcadorPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeMarcador {
    override suspend fun ler(livroId: Int): ResultadoDaChamada<Marcador?> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.marcador(livroId).marcador }
    }

    override suspend fun gravar(livroId: Int, capituloId: Int, posicao: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.gravarMarcador(livroId, MarcadorGravacao(capituloId, posicao, Instant.now().toString())); Unit }
    }
}

/** Sem servidor/sem marcador: o padrão dos ViewModels que não recebem um repositório (testes antigos). */
object MarcadorSemServidor : RepositorioDeMarcador {
    override suspend fun ler(livroId: Int): ResultadoDaChamada<Marcador?> = ResultadoDaChamada.Sucesso(null)
    override suspend fun gravar(livroId: Int, capituloId: Int, posicao: Int): ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)
}
