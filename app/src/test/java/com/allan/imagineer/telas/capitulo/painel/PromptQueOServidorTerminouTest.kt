package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.EventoDeAnalise
import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.analise.TENTATIVAS_DE_CONFERIR_O_PROMPT
import com.allan.imagineer.analise.TipoDeEvento
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A conexão cai **durante** a geração do prompt (a tela apagou, o app foi para o fundo), mas o servidor termina e grava o prompt.
 * O app não pode dizer "não consegui falar com o servidor" de um prompt que existe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptQueOServidorTerminouTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() = Dispatchers.setMain(agendador)

    @After
    fun limpar() = Dispatchers.resetMain()

    private fun escopo() = CoroutineScope(SupervisorJob() + agendador)

    private val semConexao = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")

    private fun novoPrompt(id: Int) = PromptDeFrame(id = id, frame_id = 70, texto = "o prompt $id")

    private fun iniciar(prompts: PromptsFalso): Pair<ServicoDeAnalises, MutableList<EventoDeAnalise>> {
        val servico = ServicoDeAnalises(SugestoesFalso(), escopo(), prompts)
        val eventos = mutableListOf<EventoDeAnalise>()
        escopo().launch { servico.eventos.collect { eventos += it } }
        return servico to eventos
    }

    @Test
    fun a_conexao_caiu_mas_o_prompt_existe_vira_sucesso() = runTest {
        val prompts = PromptsFalso().also {
            it.geracao = semConexao
            // antes: o frame tinha o prompt 3; depois da queda, o servidor já gravou o 4.
            it.sequenciaDeListagens += ResultadoDaChamada.Sucesso(listOf(novoPrompt(3)))
            it.sequenciaDeListagens += ResultadoDaChamada.Sucesso(listOf(novoPrompt(3), novoPrompt(4)))
        }
        val (servico, eventos) = iniciar(prompts)
        runCurrent()

        val resultado = servico.iniciarPrompt(70, 5, 2, "capítulo 3", "A partida", null)
        advanceUntilIdle()

        assertEquals(4, (resultado.await() as ResultadoDaChamada.Sucesso).dado.id)
        assertTrue(eventos.single { it.tipo == TipoDeEvento.PROMPT }.sucesso)
    }

    @Test
    fun enquanto_o_servidor_ainda_trabalha_o_app_espera_e_acha_depois() = runTest {
        val prompts = PromptsFalso().also {
            it.geracao = semConexao
            it.sequenciaDeListagens += ResultadoDaChamada.Sucesso(emptyList())
            it.sequenciaDeListagens += ResultadoDaChamada.Sucesso(emptyList())  // ainda não terminou
            it.sequenciaDeListagens += semConexao  // a rede oscilou
            it.sequenciaDeListagens += ResultadoDaChamada.Sucesso(listOf(novoPrompt(1)))
        }
        val (servico, _) = iniciar(prompts)
        runCurrent()

        val resultado = servico.iniciarPrompt(70, 5, 2, "capítulo 3", "A partida", null)
        advanceUntilIdle()

        assertEquals(1, (resultado.await() as ResultadoDaChamada.Sucesso).dado.id)
    }

    @Test
    fun se_nada_aparece_depois_de_esperar_vale_a_falha() = runTest {
        val prompts = PromptsFalso().also {
            it.geracao = semConexao
            it.lista = ResultadoDaChamada.Sucesso(listOf(novoPrompt(3)))  // sempre o mesmo: nenhum prompt novo
        }
        val (servico, eventos) = iniciar(prompts)
        runCurrent()

        val resultado = servico.iniciarPrompt(70, 5, 2, "capítulo 3", "A partida", null)
        advanceUntilIdle()

        assertTrue(resultado.await() is ResultadoDaChamada.Falha)
        assertEquals("Não consegui falar com o servidor.", eventos.single { it.tipo == TipoDeEvento.PROMPT }.motivo)
        assertEquals(1 + TENTATIVAS_DE_CONFERIR_O_PROMPT, prompts.listagens)  // a de antes e uma conferência por tentativa
    }

    @Test
    fun erro_que_o_servidor_respondeu_nao_e_conferido() = runTest {
        val prompts = PromptsFalso().also {
            it.geracao = ResultadoDaChamada.Falha("Nenhum modelo de prompt foi escolhido.", 422)
            it.sequenciaDeListagens += ResultadoDaChamada.Sucesso(emptyList())
        }
        val (servico, _) = iniciar(prompts)
        runCurrent()

        val resultado = servico.iniciarPrompt(70, 5, 2, "capítulo 3", "A partida", null)
        advanceUntilIdle()

        assertEquals("Nenhum modelo de prompt foi escolhido.", (resultado.await() as ResultadoDaChamada.Falha).motivo)
        assertEquals(1, prompts.listagens)  // só a de antes: não adianta esperar por uma recusa
    }

    @Test
    fun sem_ler_o_que_havia_antes_nao_da_para_conferir_e_vale_a_falha() = runTest {
        val prompts = PromptsFalso().also {
            it.geracao = semConexao
            it.sequenciaDeListagens += semConexao  // nem a leitura de antes funcionou
        }
        val (servico, _) = iniciar(prompts)
        runCurrent()

        val resultado = servico.iniciarPrompt(70, 5, 2, "capítulo 3", "A partida", null)
        advanceUntilIdle()

        assertTrue(resultado.await() is ResultadoDaChamada.Falha)
        assertEquals(1, prompts.listagens)
        assertNull(servico.promptEmAndamento(70))
    }
}
