package com.allan.imagineer.rede

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** O resultado do botão "Testar" da tela de Configuração. */
sealed interface ResultadoDoTeste {

    /** O servidor respondeu como o Imagineer. [servidorTemChave]: se ele tem chave de API própria. */
    data class Conectado(val servidorTemChave: Boolean) : ResultadoDoTeste

    /** Não deu certo; [motivo] já está escrito para o usuário ler. */
    data class Falhou(val motivo: String) : ResultadoDoTeste
}

/**
 * O que o app precisa saber fazer com o servidor. É uma interface para o
 * ViewModel poder ser testado sem rede (item 7.3a) — a mesma ideia do
 * `ProvedorFalso` do backend.
 */
interface ServidorImagineer {
    /** Testa um endereço, ainda não salvo, chamando `GET /configuracao`. */
    suspend fun testarConexao(urlBase: String): ResultadoDoTeste
}

/** A implementação de verdade, sobre o Retrofit. */
class ServidorPeloRetrofit : ServidorImagineer {

    override suspend fun testarConexao(urlBase: String): ResultadoDoTeste {
        // Três falhas com conserto diferente (item 7.3a) — por isso três mensagens.
        return try {
            val configuracao = criarApi(urlBase).configuracao()
            ResultadoDoTeste.Conectado(servidorTemChave = configuracao.tem_chave_api)
        } catch (erro: HttpException) {
            ResultadoDoTeste.Falhou("O servidor respondeu com erro ${erro.code()}.")
        } catch (erro: SerializationException) {
            // Chegou lá e respondeu, mas não é o formato do Imagineer — típico de
            // apontar para outro serviço (uma página HTML, por exemplo).
            ResultadoDoTeste.Falhou(
                "Esse endereço responde, mas não parece o servidor do Imagineer.",
            )
        } catch (erro: IOException) {
            // Timeout, sem conexão, Pi desligado, Tailscale desconectado.
            ResultadoDoTeste.Falhou("Não consegui falar com o servidor.")
        }
    }
}
