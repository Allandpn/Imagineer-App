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

/** Um dicionário que o servidor encontrou na pasta dele (RL19), com a escolha da pessoa (RL29). Espelha o `DicionarioDisponivel`. */
@Serializable
@Suppress("PropertyName")
data class DicionarioDoServidor(
    val id: String,
    val nome: String,
    val idioma_das_entradas: String? = null,
    val palavras: Int = 0,
    /** Os idiomas de livro para os quais ele é consultado por padrão; vazio = só aparece em "procurar em todos". */
    val padrao_para: List<String> = emptyList(),
    /** Ligado ou desligado pela pessoa: desligado, o servidor nunca o consulta. */
    val ativo: Boolean = true,
)

/** O corpo de `PUT /dicionario/preferencias` (RL29): a ordem, do preferido ao último, e os que ficam desligados. */
@Serializable
data class PreferenciasDosDicionarios(val ordem: List<String>, val desativados: List<String>)

/** O dicionário (RL19, RL20, RL28). Interface, para o ViewModel ser testado com uma versão falsa. */
interface RepositorioDeDicionario {
    /** `GET /dicionario/verbete`: [idioma] é o do livro (decide quais dicionários valem); [todos] ignora o idioma. */
    suspend fun consultar(palavra: String, idioma: String?, todos: Boolean): ResultadoDaChamada<ConsultaDeDicionario>

    /** `GET /dicionario/dicionarios`: os dicionários do servidor **na ordem de preferência**, cada um com `ativo` (RL29). */
    suspend fun listar(): ResultadoDaChamada<List<DicionarioDoServidor>> = ResultadoDaChamada.Falha("Sem servidor.")

    /** `PUT /dicionario/preferencias`: grava a ordem e os desligados e devolve a lista nova (RL29). */
    suspend fun gravarPreferencias(preferencias: PreferenciasDosDicionarios): ResultadoDaChamada<List<DicionarioDoServidor>> =
        ResultadoDaChamada.Falha("Sem servidor.")
}

class RepositorioDeDicionarioPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeDicionario {
    override suspend fun consultar(palavra: String, idioma: String?, todos: Boolean): ResultadoDaChamada<ConsultaDeDicionario> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.verbete(palavra, idioma, todos) }
    }

    override suspend fun listar(): ResultadoDaChamada<List<DicionarioDoServidor>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.dicionarios() }
    }

    override suspend fun gravarPreferencias(preferencias: PreferenciasDosDicionarios): ResultadoDaChamada<List<DicionarioDoServidor>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.gravarPreferenciasDosDicionarios(preferencias) }
    }
}
