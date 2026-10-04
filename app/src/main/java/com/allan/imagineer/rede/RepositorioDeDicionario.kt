package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/** Um verbete achado, de um dicionário (RL19, RL20). Espelha o `VerbeteResposta` do servidor. */
@Serializable
@Suppress("PropertyName")
data class Verbete(
    val dicionario_id: String,
    val dicionario: String,
    val entrada: String,
    val texto: String,
)

/** O que `GET /dicionario/verbete` devolve: a [palavra] já limpa pelo servidor e os [resultados] (vazio = nada achado). */
@Serializable
data class ConsultaDeDicionario(val palavra: String, val resultados: List<Verbete> = emptyList())

/** O dicionário (RL19, RL20). Interface, para o ViewModel ser testado com uma versão falsa. */
interface RepositorioDeDicionario {
    /** `GET /dicionario/verbete`: [idioma] é o do livro (decide quais dicionários valem); [todos] ignora o idioma. */
    suspend fun consultar(palavra: String, idioma: String?, todos: Boolean): ResultadoDaChamada<ConsultaDeDicionario>
}

class RepositorioDeDicionarioPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeDicionario {
    override suspend fun consultar(palavra: String, idioma: String?, todos: Boolean): ResultadoDaChamada<ConsultaDeDicionario> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.verbete(palavra, idioma, todos) }
    }
}
