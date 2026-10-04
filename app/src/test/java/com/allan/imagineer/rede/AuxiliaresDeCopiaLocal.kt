package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** Um endereço de servidor fixo, para montar o [ProvedorDeApi] dos testes da cópia local. */
class ArmazenamentoComUrl(url: String) : ArmazenamentoDeConfiguracao {
    override val urlDoServidor: Flow<String?> = flowOf(url)
    override suspend fun salvarUrlDoServidor(url: String) = Unit
}

/**
 * Um endereço onde **ninguém escuta** (a porta 1): qualquer chamada falha na hora com
 * "conexão recusada", que é como o app enxerga "sem conexão".
 */
const val URL_SEM_SERVIDOR = "http://127.0.0.1:1"

/** Um livro de três capítulos: o 2 está arquivado (`ignorado`). */
fun livroDeTeste(
    id: Int = 1,
    revisao: Int = 1,
    titulo: String = "Livro",
    tituloDoCapitulo1: String? = "Um",
): LivroDetalhe = LivroDetalhe(
    id = id,
    titulo = titulo,
    autor = "Autor",
    nome_arquivo = "livro.epub",
    data_importacao = "2026-09-30T00:00:00",
    total_de_capitulos = 3,
    capitulos_ignorados = 1,
    capitulos = listOf(
        CapituloResumo(id = 10, ordem = 1, titulo = tituloDoCapitulo1, ignorado = false, tamanho_do_texto = 5),
        CapituloResumo(id = 20, ordem = 2, titulo = "Dois", ignorado = true, tamanho_do_texto = 5),
        CapituloResumo(id = 30, ordem = 3, titulo = "Três", ignorado = false, tamanho_do_texto = 5, sugestoes_pendentes = 2),
    ),
    revisao = revisao,
)

fun LivroDetalhe.comoJson(): String = jsonDoImagineer.encodeToString(LivroDetalhe.serializer(), this)

/** O JSON que o servidor devolve em `GET /capitulos/{id}`. */
fun capituloJson(id: Int, ordem: Int, texto: String, livroId: Int = 1, titulo: String = "T$id") =
    """{"id":$id,"ordem":$ordem,"titulo":"$titulo","ignorado":false,"tamanho_do_texto":${texto.length},"sugestoes_pendentes":0,"livro_id":$livroId,"texto":"$texto"}"""

/**
 * Espera (em tempo **real**, porque a rede do teste é de verdade) até [condicao] ficar
 * verdadeira, por no máximo cerca de 5 segundos. Serve para o adiantamento em segundo plano.
 */
suspend fun esperarAte(condicao: () -> Boolean): Boolean = withContext(Dispatchers.Default) {
    repeat(100) {
        if (condicao()) return@withContext true
        delay(50)
    }
    condicao()
}
