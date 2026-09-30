package com.allan.imagineer.rede

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** As três falhas de rede e o que cada uma diz ao usuário (item 7.3a). */
class ChamarApiTest {

    @Test
    fun `sucesso devolve o dado`() = runTest {
        val resultado = chamarApi { 42 }

        assertEquals(ResultadoDaChamada.Sucesso(42), resultado)
    }

    @Test
    fun `IOException vira nao consegui falar com o servidor`() = runTest {
        val resultado = chamarApi<Int> { throw IOException("timeout") }

        assertEquals(
            ResultadoDaChamada.Falha("Não consegui falar com o servidor."),
            resultado,
        )
    }

    @Test
    fun `erro HTTP diz o codigo`() = runTest {
        val resposta = Response.error<Int>(500, "".toResponseBody())

        val resultado = chamarApi<Int> { throw HttpException(resposta) }

        assertEquals(
            ResultadoDaChamada.Falha("O servidor respondeu com erro 500.", codigoHttp = 500),
            resultado,
        )
    }

    @Test
    fun `erro HTTP com detail da API mostra a mensagem da API`() = runTest {
        val corpo = """{"detail":"Não existe livro com id 999."}""".toResponseBody()
        val resposta = Response.error<Int>(404, corpo)

        val resultado = chamarApi<Int> { throw HttpException(resposta) }

        assertEquals(
            ResultadoDaChamada.Falha("Não existe livro com id 999.", codigoHttp = 404),
            resultado,
        )
    }

    @Test
    fun `erro HTTP com corpo que nao e JSON cai na mensagem generica`() = runTest {
        val resposta = Response.error<Int>(500, "Internal Server Error".toResponseBody())

        val resultado = chamarApi<Int> { throw HttpException(resposta) }

        assertEquals(
            ResultadoDaChamada.Falha("O servidor respondeu com erro 500.", codigoHttp = 500),
            resultado,
        )
    }

    @Test
    fun `resposta que nao e do Imagineer e distinguida da falta de conexao`() = runTest {
        val resultado = chamarApi<Int> { throw SerializationException("nao e JSON") }

        assertEquals(
            ResultadoDaChamada.Falha(
                "Esse endereço responde, mas não parece o servidor do Imagineer.",
            ),
            resultado,
        )
    }

    @Test
    fun `erro de programacao nao e escondido como falha de rede`() = runTest {
        // Uma exceção que não é de rede tem de estourar, para o bug aparecer.
        val erro = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { chamarApi<Int> { throw IllegalStateException("bug") } }
        }

        assertEquals("bug", erro.message)
    }
}
