package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import kotlinx.coroutines.flow.first

/**
 * Entrega o cliente da API para o endereço salvo, montando-o só quando preciso.
 *
 * Montar um cliente HTTP a cada chamada jogaria fora o pool de conexões dele, então
 * guarda o último par (URL, API) e só remonta se a URL salva mudou. Existe para os
 * vários repositórios (livros, capítulos...) não repetirem esta lógica.
 */
class ProvedorDeApi(private val armazenamento: ArmazenamentoDeConfiguracao) {

    private var urlEmUso: String? = null
    private var apiEmUso: ApiImagineer? = null

    /** A API para o endereço salvo, ou `null` se o usuário ainda não configurou um. */
    suspend fun obter(): ApiImagineer? {
        val url = armazenamento.urlDoServidor.first() ?: return null
        if (url != urlEmUso) {
            apiEmUso = criarApi(url)
            urlEmUso = url
        }
        return apiEmUso
    }

    /** A falha padrão de quando não há endereço configurado. */
    fun semServidor() = ResultadoDaChamada.Falha("O endereço do servidor ainda não foi configurado.")
}
