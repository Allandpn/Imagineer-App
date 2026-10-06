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

    /**
     * @param codigoHttp o código da resposta do servidor, quando ele chegou a
     * responder (`404`, `500`...). Nulo para falhas em que não houve resposta
     * (sem conexão, resposta que não é do Imagineer). Existe para o repositório
     * poder decidir, por exemplo, que um `404` ao remover não é um problema.
     */
    data class Falha(val motivo: String, val codigoHttp: Int? = null) : ResultadoDaChamada<Nothing>
}

/** O que a pessoa lê quando o servidor responde 401 (modo `tailscale`: ele não a identificou) — AP6. */
const val MOTIVO_DO_401 =
    "O servidor não reconheceu você. Confira se o aparelho foi compartilhado com a sua conta do Tailscale."

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
        // O Retrofit já deixa o corpo do erro em memória, então ler aqui não trava a tela.
        val corpo = try {
            erro.response()?.errorBody()?.string()
        } catch (leitura: IOException) {
            null
        }
        ResultadoDaChamada.Falha(
            motivo = if (erro.code() == 401) MOTIVO_DO_401 else extrairDetalhe(corpo) ?: "O servidor respondeu com erro ${erro.code()}.",
            codigoHttp = erro.code(),
        )
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
