package com.allan.imagineer.rede

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** As cores de destaque do servidor (RL9). O nome guardado é o do enum; [rotulo] é o que a pessoa lê. */
enum class CorDeDestaque(val rotulo: String) {
    AMARELO("Amarelo"),
    VERDE("Verde"),
    AZUL("Azul"),
    ROSA("Rosa"),
    ;

    companion object {
        /** Cor guardada → enum; um nome que este app não conhece cai no amarelo, nunca quebra a leitura. */
        fun de(nome: String): CorDeDestaque = entries.firstOrNull { it.name == nome } ?: AMARELO
    }
}

/** Um trecho destacado (RL9 a RL13). Espelha o `DestaqueResposta`; [inicio] e [fim] são UTF-16 desde o início do texto do capítulo. */
@Serializable
@Suppress("PropertyName")
data class Destaque(
    val id: Int,
    val livro_id: Int,
    val capitulo_id: Int,
    val inicio: Int,
    val fim: Int,
    val trecho: String,
    val cor: String = "AMARELO",
    val nota: String? = null,
    val elemento_id: Int? = null,
) {
    val corDoDestaque: CorDeDestaque get() = CorDeDestaque.de(cor)
}

/** Os destaques (RL9 a RL13). Interface, para os ViewModels serem testados com uma versão falsa. */
interface RepositorioDeDestaques {
    /** `GET /livros/{id}/destaques`: todos do livro, na ordem do livro; com [capituloId], só os dele. */
    suspend fun listar(livroId: Int, capituloId: Int? = null): ResultadoDaChamada<List<Destaque>>

    /** `POST /livros/{id}/destaques`: o servidor copia o trecho do capítulo. */
    suspend fun criar(livroId: Int, capituloId: Int, inicio: Int, fim: Int, cor: CorDeDestaque): ResultadoDaChamada<Destaque>

    /** `PATCH /destaques/{id}`: só o que for passado muda; [nota] e [elementoId] **nulos desfazem**. */
    suspend fun ajustarCor(destaqueId: Int, cor: CorDeDestaque): ResultadoDaChamada<Destaque>
    suspend fun ajustarNota(destaqueId: Int, nota: String?): ResultadoDaChamada<Destaque>
    suspend fun ligarAoElemento(destaqueId: Int, elementoId: Int?): ResultadoDaChamada<Destaque>

    /** `DELETE /destaques/{id}`. */
    suspend fun remover(destaqueId: Int): ResultadoDaChamada<Unit>

    /** `GET /elementos/{id}/destaques`: as passagens ligadas a um elemento (a ficha). */
    suspend fun doElemento(elementoId: Int): ResultadoDaChamada<List<Destaque>>
}

class RepositorioDeDestaquesPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeDestaques {
    override suspend fun listar(livroId: Int, capituloId: Int?): ResultadoDaChamada<List<Destaque>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.destaques(livroId, capituloId) }
    }

    override suspend fun criar(livroId: Int, capituloId: Int, inicio: Int, fim: Int, cor: CorDeDestaque): ResultadoDaChamada<Destaque> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo = buildJsonObject {
            put("capitulo_id", capituloId)
            put("inicio", inicio)
            put("fim", fim)
            put("cor", cor.name)
        }
        return chamarApi { api.criarDestaque(livroId, corpo) }
    }

    override suspend fun ajustarCor(destaqueId: Int, cor: CorDeDestaque): ResultadoDaChamada<Destaque> =
        ajustar(destaqueId, buildJsonObject { put("cor", cor.name) })

    override suspend fun ajustarNota(destaqueId: Int, nota: String?): ResultadoDaChamada<Destaque> =
        // `null` tem de ir no corpo (e não ficar de fora): é ele que apaga a nota.
        ajustar(destaqueId, buildJsonObject { if (nota == null) put("nota", JsonNull) else put("nota", nota) })

    override suspend fun ligarAoElemento(destaqueId: Int, elementoId: Int?): ResultadoDaChamada<Destaque> =
        ajustar(destaqueId, buildJsonObject { if (elementoId == null) put("elemento_id", JsonNull) else put("elemento_id", elementoId) })

    private suspend fun ajustar(destaqueId: Int, corpo: JsonObject): ResultadoDaChamada<Destaque> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.ajustarDestaque(destaqueId, corpo) }
    }

    override suspend fun remover(destaqueId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.removerDestaque(destaqueId) }
    }

    override suspend fun doElemento(elementoId: Int): ResultadoDaChamada<List<Destaque>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.destaquesDoElemento(elementoId) }
    }
}

/** Sem servidor: o padrão dos ViewModels que não recebem um repositório (testes antigos). */
object DestaquesSemServidor : RepositorioDeDestaques {
    private val vazio = ResultadoDaChamada.Sucesso(emptyList<Destaque>())
    override suspend fun listar(livroId: Int, capituloId: Int?): ResultadoDaChamada<List<Destaque>> = vazio
    override suspend fun criar(livroId: Int, capituloId: Int, inicio: Int, fim: Int, cor: CorDeDestaque): ResultadoDaChamada<Destaque> =
        ResultadoDaChamada.Falha("Sem servidor.")
    override suspend fun ajustarCor(destaqueId: Int, cor: CorDeDestaque): ResultadoDaChamada<Destaque> = ResultadoDaChamada.Falha("Sem servidor.")
    override suspend fun ajustarNota(destaqueId: Int, nota: String?): ResultadoDaChamada<Destaque> = ResultadoDaChamada.Falha("Sem servidor.")
    override suspend fun ligarAoElemento(destaqueId: Int, elementoId: Int?): ResultadoDaChamada<Destaque> = ResultadoDaChamada.Falha("Sem servidor.")
    override suspend fun remover(destaqueId: Int): ResultadoDaChamada<Unit> = ResultadoDaChamada.Falha("Sem servidor.")
    override suspend fun doElemento(elementoId: Int): ResultadoDaChamada<List<Destaque>> = vazio
}
