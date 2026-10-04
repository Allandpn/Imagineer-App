package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/**
 * O gasto de um grupo (um provedor, uma operação, um livro ou um modelo). Os valores em dólares chegam **como texto** ("0.0250"), porque
 * o servidor não perde casas decimais; [total] vira número só na hora de mostrar.
 */
@Serializable
@Suppress("PropertyName")
data class GastoAgrupado(
    val nome: String,
    val livro_id: Int? = null,
    val total: String = "0",
    val chamadas: Int = 0,
    /** Chamadas do grupo sem custo conhecido (o fornecedor não informou e a tabela de preços não tem o modelo). */
    val sem_custo: Int = 0,
)

/** Os custos de um mês (`GET /custos`), em dólares (CU4). */
@Serializable
@Suppress("PropertyName")
data class CustosDoMes(
    val mes: String,
    val total: String = "0",
    /** A parte do [total] que veio da tabela de preços (fal.ai, Replicate), e não do fornecedor. */
    val estimado: String = "0",
    val chamadas: Int = 0,
    val sem_custo: Int = 0,
    val por_provedor: List<GastoAgrupado> = emptyList(),
    val por_operacao: List<GastoAgrupado> = emptyList(),
    val por_livro: List<GastoAgrupado> = emptyList(),
    val por_modelo: List<GastoAgrupado> = emptyList(),
    val meses_com_gasto: List<String> = emptyList(),
)

/** Os custos de IA (CU5). Interface, para o ViewModel ser testado com uma versão falsa. Só leitura. */
interface RepositorioDeCustos {
    /** `GET /custos`: os custos do [mes] (`AAAA-MM`); `null` = o mês atual. */
    suspend fun custos(mes: String?): ResultadoDaChamada<CustosDoMes>
}

class RepositorioDeCustosPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeCustos {
    override suspend fun custos(mes: String?): ResultadoDaChamada<CustosDoMes> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.custos(mes) }
    }
}
