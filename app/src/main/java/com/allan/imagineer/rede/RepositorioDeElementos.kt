package com.allan.imagineer.rede

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Os nomes dos campos são os do JSON do backend (item 7.3a).

/** Um elemento já cadastrado no livro, como `GET /livros/{id}/elementos` o lista (item 6.3). */
@Serializable
@Suppress("PropertyName")
data class ElementoDoLivro(
    val id: Int,
    val tipo: String,
    val nome: String,
    val descricao: String? = null,
    val total_de_estados: Int = 0,
    /** O estado mais recente do elemento no livro, para a linha da lista (E20). */
    val estado_vigente: EstadoDoElemento? = null,
    /** A imagem que ilustra o elemento na lista (FI4): a âncora ou a mais recente do retrato; nulo sem imagem. */
    val imagem_de_capa_id: Int? = null,
)

/** Uma imagem de um retrato do elemento, na ficha dele (FI2). [ancora] = a referência principal. */
@Serializable
@Suppress("PropertyName")
data class ImagemDoElemento(
    val id: Int,
    val prompt_id: Int = 0,
    val frame_id: Int = 0,
    val capitulo_id: Int = 0,
    val titulo_do_capitulo: String? = null,
    val ordem_do_capitulo: Int? = null,
    val orientacao: String? = null,
    val modelo: String? = null,
    val origem: String = "IMPORTADA",
    val sem_filtro_de_seguranca: Boolean = false,
    val ancora: Boolean = false,
)

/** Uma cena em que o elemento participa, na ficha dele (FI3); [imagem_id] nulo = ainda sem imagem. */
@Serializable
@Suppress("PropertyName")
data class CenaDoElemento(
    val frame_id: Int,
    val titulo: String,
    val descricao: String? = null,
    val capitulo_id: Int = 0,
    val titulo_do_capitulo: String? = null,
    val ordem_do_capitulo: Int? = null,
    val participantes: List<String> = emptyList(),
    val total_de_imagens: Int = 0,
    val imagem_id: Int? = null,
    val imagem_orientacao: String? = null,
)

/** O que a ficha mostra além dos textos: as imagens do elemento e as cenas em que ele aparece (FI1). */
@Serializable
data class GaleriaDoElemento(
    val imagens: List<ImagemDoElemento> = emptyList(),
    val cenas: List<CenaDoElemento> = emptyList(),
)

/** O que o app lê do elemento recém-criado; o resto da resposta é ignorado. */
@Serializable
data class ElementoCriado(
    val id: Int,
    val tipo: String,
    val nome: String,
)

/** Um Estado criado ou ajustado; o app só confere que deu certo. */
@Serializable
@Suppress("PropertyName")
data class EstadoRegistrado(
    val id: Int,
    val elemento_id: Int,
    val capitulo_id: Int,
)

/**
 * O elemento inteiro, como `GET /elementos/{id}` o devolve: a identidade, os estados de
 * aparência por capítulo e o histórico de identidade (item 3.4f). É o que o usuário consulta
 * para decidir a qual personagem associar uma sugestão (item 7.5b, E13).
 */
@Serializable
@Suppress("PropertyName")
data class DetalheDoElemento(
    val id: Int,
    val livro_id: Int = 0,
    val tipo: String,
    val nome: String,
    /** A identidade inicial; a vigente soma a ela o [historico_identidade]. */
    val descricao: String? = null,
    val estados: List<EstadoDoElemento> = emptyList(),
    val historico_identidade: List<IdentidadeDoCapitulo> = emptyList(),
)

/** A aparência do elemento num capítulo. */
@Serializable
@Suppress("PropertyName")
data class EstadoDoElemento(
    val id: Int,
    val capitulo_id: Int,
    val ordem_do_capitulo: Int? = null,
    val titulo_do_capitulo: String? = null,
    val descricao: String,
)

/** O que um capítulo acrescentou sobre **quem** o elemento é. */
@Serializable
@Suppress("PropertyName")
data class IdentidadeDoCapitulo(
    val id: Int,
    val capitulo_id: Int,
    val ordem_do_capitulo: Int? = null,
    val titulo_do_capitulo: String? = null,
    val descricao: String,
)

/**
 * O que o painel de IA e a tela de Elementos fazem para confirmar elementos sugeridos (item 7.5b, incremento 10a).
 * Interface, para o ViewModel ser testado com uma versão falsa. Nenhuma destas chamadas gasta IA.
 */
interface RepositorioDeElementos {

    /** `GET /livros/{id}/elementos`: para a lista de "vincular a um existente". */
    suspend fun listar(livroId: Int): ResultadoDaChamada<List<ElementoDoLivro>>

    /** `GET /elementos/{id}`: a identidade e o histórico, para o usuário conferir (E13, E14). */
    suspend fun detalhar(elementoId: Int): ResultadoDaChamada<DetalheDoElemento>

    /** `GET /elementos/{id}/galeria`: as imagens dos retratos dele e as cenas em que aparece (FI1). Nunca gasta IA. */
    suspend fun galeria(elementoId: Int): ResultadoDaChamada<GaleriaDoElemento>

    /** Cadastra o elemento **e** o estado do capítulo da sugestão, ligando-a (E2). */
    suspend fun criar(
        livroId: Int,
        tipo: String,
        nome: String,
        descricao: String?,
        sugestaoId: Int,
    ): ResultadoDaChamada<ElementoCriado>

    /** Liga a sugestão a um elemento existente **e** cria o estado do capítulo (E3, E6). */
    suspend fun registrarEstado(elementoId: Int, sugestaoId: Int): ResultadoDaChamada<Unit>

    /**
     * Corrige só o casamento da sugestão, sem criar estado (E4, E5): o mesmo `elemento_id`
     * confirma o casamento automático, outro o troca e `null` o desfaz **de verdade**.
     */
    suspend fun ajustarCasamento(sugestaoId: Int, elementoId: Int?): ResultadoDaChamada<Unit>

    /** Descarta (`true`) ou restaura (`false`) uma sugestão ainda sem elemento (E16). */
    suspend fun descartar(sugestaoId: Int, descartada: Boolean): ResultadoDaChamada<Unit>

    /** `PATCH /elementos/{id}`: só os campos não nulos vão (E14). */
    suspend fun ajustarElemento(
        elementoId: Int,
        tipo: String? = null,
        nome: String? = null,
        identidade: String? = null,
        ancoraPadraoId: Int? = null,
    ): ResultadoDaChamada<Unit>

    /** `POST /elementos/{id}/estados`: um estado novo **neste** capítulo (E14). */
    suspend fun criarEstado(elementoId: Int, capituloId: Int, descricao: String): ResultadoDaChamada<Unit>

    /** `PATCH /estados/{id}` (E14). */
    suspend fun ajustarEstado(estadoId: Int, descricao: String): ResultadoDaChamada<Unit>

    /** `DELETE /estados/{id}` (E15). */
    suspend fun removerEstado(estadoId: Int): ResultadoDaChamada<Unit>

    /** `DELETE /elementos/{id}`: apaga o elemento e todos os estados dele (E21). */
    suspend fun excluir(elementoId: Int): ResultadoDaChamada<Unit>

    /** `POST /elementos/{id}/historico-identidade`: um acréscimo de identidade escrito à mão (E38, E39). */
    suspend fun criarAcrescimo(elementoId: Int, capituloId: Int, descricao: String): ResultadoDaChamada<Unit>

    /** `PATCH /historico-identidade/{id}` (E38). */
    suspend fun ajustarAcrescimo(acrescimoId: Int, descricao: String): ResultadoDaChamada<Unit>

    /** `DELETE /historico-identidade/{id}` (E38). */
    suspend fun removerAcrescimo(acrescimoId: Int): ResultadoDaChamada<Unit>

    /**
     * `POST /elementos/{id}/mesclar`: junta a **origem** ao **destino**, que fica (E35). Para
     * quando o mesmo elemento foi cadastrado duas vezes (por exemplo, com tipos diferentes).
     */
    suspend fun mesclar(origemId: Int, destinoId: Int): ResultadoDaChamada<Unit>
}

/** A implementação de verdade, sobre o Retrofit. */
class RepositorioDeElementosPeloRetrofit(
    private val provedor: ProvedorDeApi,
) : RepositorioDeElementos {

    override suspend fun listar(livroId: Int): ResultadoDaChamada<List<ElementoDoLivro>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.elementosDoLivro(livroId) }
    }

    override suspend fun detalhar(elementoId: Int): ResultadoDaChamada<DetalheDoElemento> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.elemento(elementoId) }
    }

    override suspend fun galeria(elementoId: Int): ResultadoDaChamada<GaleriaDoElemento> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.galeriaDoElemento(elementoId) }
    }

    override suspend fun criar(
        livroId: Int,
        tipo: String,
        nome: String,
        descricao: String?,
        sugestaoId: Int,
    ): ResultadoDaChamada<ElementoCriado> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo = buildJsonObject {
            put("tipo", tipo)
            put("nome", nome)
            // Sem descrição, o campo nem vai: o servidor trata ausente como "não informado".
            if (!descricao.isNullOrBlank()) put("descricao", descricao)
            put("sugestoes_elemento_ids", buildJsonArray { add(sugestaoId) })
        }
        return chamarApi { api.criarElemento(livroId, corpo) }
    }

    override suspend fun registrarEstado(elementoId: Int, sugestaoId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo = buildJsonObject { put("sugestoes_elemento_ids", buildJsonArray { add(sugestaoId) }) }
        return chamarApi { api.registrarEstados(elementoId, corpo); Unit }
    }

    override suspend fun ajustarCasamento(sugestaoId: Int, elementoId: Int?): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        // `null` tem de ir no corpo (e não ficar de fora): é ele que desfaz o casamento.
        val corpo = buildJsonObject {
            if (elementoId == null) put("elemento_id", JsonNull) else put("elemento_id", elementoId)
        }
        return chamarApi { api.ajustarCasamento(sugestaoId, corpo); Unit }
    }

    override suspend fun descartar(sugestaoId: Int, descartada: Boolean): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        // Uma coisa por pedido: aqui só `descartada`, nunca junto de `elemento_id`.
        val corpo: JsonObject = buildJsonObject { put("descartada", descartada) }
        return chamarApi { api.ajustarCasamento(sugestaoId, corpo); Unit }
    }

    override suspend fun ajustarElemento(
        elementoId: Int,
        tipo: String?,
        nome: String?,
        identidade: String?,
        ancoraPadraoId: Int?,
    ): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo = buildJsonObject {
            tipo?.let { put("tipo", it) }
            nome?.let { put("nome", it) }
            identidade?.let { put("descricao", it) }
            // FI6: a referência principal do elemento (item 4.5).
            ancoraPadraoId?.let { put("imagem_ancora_padrao_id", it) }
        }
        return chamarApi { api.ajustarElemento(elementoId, corpo); Unit }
    }

    override suspend fun criarEstado(elementoId: Int, capituloId: Int, descricao: String): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo = buildJsonObject {
            put("capitulo_id", capituloId)
            put("descricao", descricao)
        }
        return chamarApi { api.criarEstado(elementoId, corpo); Unit }
    }

    override suspend fun ajustarEstado(estadoId: Int, descricao: String): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo = buildJsonObject { put("descricao", descricao) }
        return chamarApi { api.ajustarEstado(estadoId, corpo); Unit }
    }

    override suspend fun removerEstado(estadoId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.removerEstado(estadoId) }
    }

    override suspend fun criarAcrescimo(elementoId: Int, capituloId: Int, descricao: String): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo = buildJsonObject {
            put("capitulo_id", capituloId)
            put("descricao", descricao)
        }
        return chamarApi { api.criarAcrescimo(elementoId, corpo); Unit }
    }

    override suspend fun ajustarAcrescimo(acrescimoId: Int, descricao: String): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo = buildJsonObject { put("descricao", descricao) }
        return chamarApi { api.ajustarAcrescimo(acrescimoId, corpo); Unit }
    }

    override suspend fun removerAcrescimo(acrescimoId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.removerAcrescimo(acrescimoId) }
    }

    override suspend fun mesclar(origemId: Int, destinoId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo = buildJsonObject { put("destino_id", destinoId) }
        return chamarApi { api.mesclarElemento(origemId, corpo); Unit }
    }

    override suspend fun excluir(elementoId: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.removerElemento(elementoId) }
    }
}
