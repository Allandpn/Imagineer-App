package com.allan.imagineer.rede

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * O que as telas precisam saber fazer com perfis de renderização. Interface, para
 * o ViewModel ser testado com uma versão falsa, sem rede.
 */
interface RepositorioDePerfis {
    /** `GET /perfis-renderizacao`. */
    suspend fun listarPerfis(): ResultadoDaChamada<List<PerfilRenderizacao>>

    /** `GET /perfis-renderizacao/{id}`. */
    suspend fun abrirPerfil(perfilId: Int): ResultadoDaChamada<PerfilRenderizacao>

    /** `POST /perfis-renderizacao`. Nome repetido volta como falha 409, com a mensagem do servidor. */
    suspend fun criarPerfil(edicao: PerfilEdicao): ResultadoDaChamada<PerfilRenderizacao> = ResultadoDaChamada.Falha("Sem servidor.")

    /** `PATCH /perfis-renderizacao/{id}`. Vai **tudo** o que o formulário tem; campo em branco vai nulo e apaga o que havia. */
    suspend fun ajustarPerfil(perfilId: Int, edicao: PerfilEdicao): ResultadoDaChamada<PerfilRenderizacao> = ResultadoDaChamada.Falha("Sem servidor.")

    /** `DELETE /perfis-renderizacao/{id}`. Os livros que o usavam ficam sem perfil padrão; os prompts continuam no histórico. */
    suspend fun removerPerfil(perfilId: Int): ResultadoDaChamada<Unit> = ResultadoDaChamada.Falha("Sem servidor.")
}

/**
 * O corpo de `POST` e `PATCH` de um perfil. **O nulo vai explícito** (`JsonNull`) para os campos em branco: no `PATCH` só o que vem é
 * aplicado, então omitir um campo deixaria o valor antigo, e apagar um texto ou desfazer a categoria precisa mandar `null`.
 */
fun corpoDoPerfil(edicao: PerfilEdicao): JsonObject = buildJsonObject {
    put("nome", edicao.nome.trim())
    fun texto(campo: String, valor: String) {
        val limpo = valor.trim()
        if (limpo.isEmpty()) put(campo, JsonNull) else put(campo, limpo)
    }
    texto("estilo", edicao.estilo)
    texto("artista_referencia", edicao.artistaDeReferencia)
    texto("iluminacao", edicao.iluminacao)
    texto("paleta", edicao.paleta)
    texto("formato", edicao.formato)
    if (edicao.categoria == null) put("categoria_estilo", JsonNull) else put("categoria_estilo", edicao.categoria.name)
}

/** A implementação de verdade, sobre o Retrofit. */
class RepositorioDePerfisPeloRetrofit(
    private val provedor: ProvedorDeApi,
) : RepositorioDePerfis {

    override suspend fun listarPerfis(): ResultadoDaChamada<List<PerfilRenderizacao>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.perfis() }
    }

    override suspend fun abrirPerfil(perfilId: Int): ResultadoDaChamada<PerfilRenderizacao> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.perfil(perfilId) }
    }

    override suspend fun criarPerfil(edicao: PerfilEdicao): ResultadoDaChamada<PerfilRenderizacao> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.criarPerfil(corpoDoPerfil(edicao)) }
    }

    override suspend fun ajustarPerfil(perfilId: Int, edicao: PerfilEdicao): ResultadoDaChamada<PerfilRenderizacao> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.ajustarPerfil(perfilId, corpoDoPerfil(edicao)) }
    }

    override suspend fun removerPerfil(perfilId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.removerPerfil(perfilId) }
    }
}
