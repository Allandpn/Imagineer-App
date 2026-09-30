package com.allan.imagineer.rede

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * O resultado de uma chamada à API, já sem exceções: ou deu certo e trouxe o dado,
 * ou falhou e trouxe o motivo escrito para o usuário ler.
 *
 * O ViewModel só lida com isto — nunca vê `HttpException` nem `IOException`. Assim
 * ele não precisa saber nada de rede, e os testes não precisam fabricar exceções.
 */
sealed interface ResultadoDaChamada<out T> {
    data class Sucesso<T>(val dado: T) : ResultadoDaChamada<T>

    data class Falha(val motivo: String) : ResultadoDaChamada<Nothing>
}

/**
 * Executa uma chamada de rede e traduz as falhas, sempre do mesmo jeito.
 *
 * São três falhas com conserto diferente (item 7.3a), então três mensagens. Fica
 * numa função só, compartilhada por todas as telas, para elas nunca divergirem na
 * hora de dizer "não consegui falar com o servidor".
 *
 * Só captura as três exceções esperadas. Qualquer outra (um erro de programação,
 * por exemplo) continua estourando — esconder um bug atrás de "falha de rede"
 * seria pior. O cancelamento de corrotina também passa direto, de propósito.
 */
suspend fun <T> chamarApi(chamada: suspend () -> T): ResultadoDaChamada<T> {
    return try {
        ResultadoDaChamada.Sucesso(chamada())
    } catch (erro: HttpException) {
        ResultadoDaChamada.Falha("O servidor respondeu com erro ${erro.code()}.")
    } catch (erro: SerializationException) {
        // Chegou lá e respondeu, mas não é o formato do Imagineer — típico de
        // apontar para outro serviço (uma página HTML, por exemplo).
        ResultadoDaChamada.Falha(
            "Esse endereço responde, mas não parece o servidor do Imagineer.",
        )
    } catch (erro: IOException) {
        // Timeout, sem conexão, Pi desligado, Tailscale desconectado.
        ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
    }
}
