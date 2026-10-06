package com.allan.imagineer.rede

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.local.ChaveDoCache
import kotlinx.coroutines.flow.first

/**
 * A API de um servidor e a [chave] do que se guarda no aparelho sobre ele (item 7.0a).
 * Vêm juntas, lidas de uma vez, para nunca descasarem se o endereço mudar no meio.
 */
class ServidorEmUso(val api: ApiImagineer, val chave: ChaveDoCache)

/**
 * Entrega o cliente da API para o endereço salvo, montando-o só quando preciso.
 *
 * Montar um cliente HTTP a cada chamada jogaria fora o pool de conexões dele, então
 * guarda o último par (URL, API) e só remonta se a URL salva mudou. Existe para os
 * vários repositórios (livros, capítulos...) não repetirem esta lógica.
 */
class ProvedorDeApi(
    private val armazenamento: ArmazenamentoDeConfiguracao,
    /** As chaves de IA da pessoa (AP2): vão em header a cada chamada. */
    private val cofre: com.allan.imagineer.dados.CofreDeChaves = com.allan.imagineer.dados.CofreEmMemoria(),
) {

    private var urlEmUso: String? = null
    private var apiEmUso: ApiImagineer? = null

    /** A API para o endereço salvo, ou `null` se o usuário ainda não configurou um. */
    suspend fun obter(): ApiImagineer? = emUso()?.api

    /**
     * A API **e** a chave do cache para o endereço salvo, ou `null` se não há endereço.
     * Usada pelos repositórios que guardam cópias no aparelho.
     */
    suspend fun emUso(): ServidorEmUso? {
        val url = armazenamento.urlDoServidor.first() ?: return null
        if (url != urlEmUso) {
            apiEmUso = criarApi(url, chaves = cofre::chaves)
            urlEmUso = url
        }
        return ServidorEmUso(apiEmUso!!, ChaveDoCache(servidor = url))
    }

    /** A falha padrão de quando não há endereço configurado. */
    fun semServidor() = ResultadoDaChamada.Falha("O endereço do servidor ainda não foi configurado.")
}
