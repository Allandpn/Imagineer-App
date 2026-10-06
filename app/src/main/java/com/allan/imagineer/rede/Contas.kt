package com.allan.imagineer.rede

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// As contas e as chaves de IA (bloco K, AP1 a AP8): quem sou eu, os provedores e a administração do servidor (só o dono).

/** Um provedor de IA da lista fixa do servidor (`GET /configuracao/provedores`, CT24). [cabecalho] é o header em que a chave vai. */
@Serializable
@Suppress("PropertyName")
data class ProvedorDeIa(
    val id: String,
    val nome: String,
    val cabecalho: String,
    val usado_para: String = "",
    /** A pessoa pode usar a chave do servidor para este provedor: ela não precisa cadastrar a dela. */
    val servidor_fornece: Boolean = false,
)

/** A pessoa que o servidor reconhece neste pedido (`GET /eu`, CT12). */
@Serializable
@Suppress("PropertyName")
data class EuAtual(
    val id: Int,
    val login: String? = null,
    val nome: String? = null,
    val dono: Boolean = false,
    val usa_chaves_do_servidor: Boolean = false,
) {
    /** Como a tela diz quem está conectado: o nome, senão o login, senão "a conta do dono". */
    val rotulo: String get() = nome?.takeIf { it.isNotBlank() } ?: login?.takeIf { it.isNotBlank() } ?: if (dono) "o dono do servidor" else "conta $id"
}

/** Os limites do servidor e o espaço usado (`GET /admin/limites`, CT20). */
@Serializable
@Suppress("PropertyName")
data class LimitesDoServidor(
    val tamanho_maximo_do_video_mb: Int,
    val tamanho_maximo_do_epub_mb: Int,
    val tamanho_maximo_da_imagem_mb: Int,
    val caracteres_maximos_da_narracao: Int,
    val armazenamento_total_em_gb: Int,
    val cota_por_pessoa_em_gb: Int,
    val uso_da_aplicacao_em_bytes: Long = 0,
    val recusa_novos_arquivos_a_partir_de_bytes: Long = 0,
)

/** Uma conta como o dono a vê (`GET /admin/usuarios`, CT21). */
@Serializable
@Suppress("PropertyName")
data class ContaDoServidor(
    val id: Int,
    val login: String? = null,
    val nome: String? = null,
    val dono: Boolean = false,
    val usa_chaves_do_servidor: Boolean = false,
    /** A cota **própria** em GB; nulo = usa o padrão. */
    val cota_em_gb: Int? = null,
    /** A cota que vale agora; nulo para o dono, que não tem cota. */
    val cota_efetiva_em_gb: Int? = null,
    val livros: Int = 0,
    val uso_em_bytes: Long = 0,
    val criado_em: String = "",
)

/** Os seis campos de limite, na ordem da tela, com o nome do JSON, o rótulo e a unidade (AP7). */
enum class CampoDeLimite(val json: String, val rotulo: String, val unidade: String) {
    VIDEO("tamanho_maximo_do_video_mb", "Vídeo", "MB por arquivo"),
    EPUB("tamanho_maximo_do_epub_mb", "EPUB", "MB por arquivo"),
    IMAGEM("tamanho_maximo_da_imagem_mb", "Imagem", "MB por arquivo"),
    NARRACAO("caracteres_maximos_da_narracao", "Narração", "caracteres por capítulo"),
    ARMAZENAMENTO("armazenamento_total_em_gb", "Armazenamento total", "GB"),
    COTA("cota_por_pessoa_em_gb", "Cota por pessoa", "GB");

    fun valorDe(limites: LimitesDoServidor): Int = when (this) {
        VIDEO -> limites.tamanho_maximo_do_video_mb
        EPUB -> limites.tamanho_maximo_do_epub_mb
        IMAGEM -> limites.tamanho_maximo_da_imagem_mb
        NARRACAO -> limites.caracteres_maximos_da_narracao
        ARMAZENAMENTO -> limites.armazenamento_total_em_gb
        COTA -> limites.cota_por_pessoa_em_gb
    }
}

/** O tamanho como a pessoa lê: "1,5 GB", "12,4 MB" ou "512 KB". */
fun bytesParaLer(bytes: Long): String {
    val pt = java.util.Locale("pt", "BR")
    return when {
        bytes >= 1024L * 1024 * 1024 -> String.format(pt, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format(pt, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(pt, "%.0f KB", bytes / 1024.0)
    }
}

/**
 * Lê um número inteiro maior que zero do campo de texto; `null` se não é (a tela não manda o que o servidor recusaria).
 * Espaços nas pontas não contam.
 */
fun inteiroPositivoOuNulo(texto: String): Int? = texto.trim().toIntOrNull()?.takeIf { it > 0 }

/** O que o app faz com as contas. Interface, para os ViewModels serem testados com uma versão falsa. */
interface RepositorioDeContas {
    /** `GET /configuracao/provedores`. */
    suspend fun provedores(): ResultadoDaChamada<List<ProvedorDeIa>>

    /** `GET /eu`. Servidor antigo, sem a rota, dá falha. */
    suspend fun eu(): ResultadoDaChamada<EuAtual>

    /** `GET /admin/limites` (só o dono; para os demais, 404). */
    suspend fun limites(): ResultadoDaChamada<LimitesDoServidor>

    /** `PUT /admin/limites`: só os campos presentes mudam. */
    suspend fun gravarLimites(novos: Map<CampoDeLimite, Int>): ResultadoDaChamada<LimitesDoServidor>

    /** `GET /admin/usuarios`. */
    suspend fun contas(): ResultadoDaChamada<List<ContaDoServidor>>

    /** `PATCH /admin/usuarios/{id}` com `usa_chaves_do_servidor`. */
    suspend fun usarChavesDoServidor(contaId: Int, usa: Boolean): ResultadoDaChamada<ContaDoServidor>

    /** `PATCH /admin/usuarios/{id}` com `cota_em_gb`; `null` volta ao padrão. */
    suspend fun mudarCota(contaId: Int, gb: Int?): ResultadoDaChamada<ContaDoServidor>
}

class RepositorioDeContasPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeContas {
    override suspend fun provedores(): ResultadoDaChamada<List<ProvedorDeIa>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.provedoresDeIa() }
    }

    override suspend fun eu(): ResultadoDaChamada<EuAtual> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.eu() }
    }

    override suspend fun limites(): ResultadoDaChamada<LimitesDoServidor> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.limitesDoServidor() }
    }

    override suspend fun gravarLimites(novos: Map<CampoDeLimite, Int>): ResultadoDaChamada<LimitesDoServidor> {
        val api = provedor.obter() ?: return provedor.semServidor()
        val corpo: JsonObject = buildJsonObject { novos.forEach { (campo, valor) -> put(campo.json, valor) } }
        return chamarApi { api.gravarLimitesDoServidor(corpo) }
    }

    override suspend fun contas(): ResultadoDaChamada<List<ContaDoServidor>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.contasDoServidor() }
    }

    override suspend fun usarChavesDoServidor(contaId: Int, usa: Boolean): ResultadoDaChamada<ContaDoServidor> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.ajustarConta(contaId, buildJsonObject { put("usa_chaves_do_servidor", usa) }) }
    }

    override suspend fun mudarCota(contaId: Int, gb: Int?): ResultadoDaChamada<ContaDoServidor> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.ajustarConta(contaId, buildJsonObject { put("cota_em_gb", gb) }) }
    }
}
