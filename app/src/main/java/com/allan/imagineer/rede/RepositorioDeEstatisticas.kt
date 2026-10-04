package com.allan.imagineer.rede

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** O tempo lido num dia, todos os livros somados (RL17). [dia] é `AAAA-MM-DD`. */
@Serializable
data class TempoDoDia(val dia: String, val segundos: Int)

/** O resumo de um livro: o tempo total, quantos dias lidos e o último (RL17). Espelha o `TempoDoLivro` do servidor. */
@Serializable
@Suppress("PropertyName")
data class TempoDoLivro(
    val livro_id: Int,
    val titulo: String,
    val segundos: Int,
    val dias_lidos: Int,
    val ultimo_dia: String,
)

/** O que `GET /estatisticas/leitura` devolve. */
@Serializable
data class EstatisticasDeLeitura(val dias: List<TempoDoDia> = emptyList(), val livros: List<TempoDoLivro> = emptyList())

/** O tempo de leitura (RL16, RL17). Interface, para o registro e a tela serem testados com uma versão falsa. */
interface RepositorioDeEstatisticas {
    /** `GET /estatisticas/leitura`. */
    suspend fun estatisticas(): ResultadoDaChamada<EstatisticasDeLeitura>

    /** `POST /livros/{id}/leitura/tempo`: **soma** [segundos] ao [dia] (`AAAA-MM-DD`, o do aparelho). */
    suspend fun somarTempo(livroId: Int, dia: String, segundos: Int): ResultadoDaChamada<Unit>
}

class RepositorioDeEstatisticasPeloRetrofit(private val provedor: ProvedorDeApi) : RepositorioDeEstatisticas {
    override suspend fun estatisticas(): ResultadoDaChamada<EstatisticasDeLeitura> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.estatisticasDeLeitura() }
    }

    override suspend fun somarTempo(livroId: Int, dia: String, segundos: Int): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.somarTempoDeLeitura(livroId, buildJsonObject { put("dia", dia); put("segundos", segundos) }); Unit }
    }
}
