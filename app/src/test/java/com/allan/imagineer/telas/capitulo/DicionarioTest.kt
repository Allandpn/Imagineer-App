package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.rede.ConsultaDeDicionario
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.RepositorioDeDicionario
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.Verbete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

private fun verbete(dicionario: String, entrada: String = "casa", texto: String = "moradia") = Verbete("id-$dicionario", dicionario, entrada, texto)

private class DicionarioFalso(var resposta: (String, String?, Boolean) -> ResultadoDaChamada<ConsultaDeDicionario>) : RepositorioDeDicionario {
    val pedidos = mutableListOf<Triple<String, String?, Boolean>>()
    override suspend fun consultar(palavra: String, idioma: String?, todos: Boolean): ResultadoDaChamada<ConsultaDeDicionario> {
        pedidos += Triple(palavra, idioma, todos)
        return resposta(palavra, idioma, todos)
    }
}

/** Só `abrirLivro` é usada pelo dicionário (para saber o idioma); o resto do repositório não é chamado. */
private fun livrosComIdioma(idioma: String?): RepositorioDeLivros =
    Proxy.newProxyInstance(RepositorioDeLivros::class.java.classLoader, arrayOf(RepositorioDeLivros::class.java)) { _, metodo, _ ->
        if (metodo.name == "abrirLivro") {
            ResultadoDaChamada.Sucesso(
                LivroDetalhe(1, "L", null, idioma, "l.epub", "2026-01-01", 1, 0),
            )
        } else {
            throw UnsupportedOperationException(metodo.name)
        }
    } as RepositorioDeLivros

/** O dicionário por seleção de palavra (RL20). */
@OptIn(ExperimentalCoroutinesApi::class)
class DicionarioTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    @Test
    fun consulta_com_o_idioma_do_livro_e_mostra_os_verbetes() = runTest {
        val falso = DicionarioFalso { p, _, _ -> ResultadoDaChamada.Sucesso(ConsultaDeDicionario(p, listOf(verbete("Aurélio")))) }
        val vm = DicionarioViewModel("casa", 1, falso, livrosComIdioma("pt-BR"))

        vm.consultar()
        advanceUntilIdle()

        assertEquals(listOf(Triple("casa", "pt-BR", false)), falso.pedidos)
        val pronto = vm.estado.value as EstadoDoDicionario.Pronto
        assertEquals(1, pronto.resultados.size)
        assertEquals(false, pronto.consultouTodos)
    }

    @Test
    fun procurar_em_todos_repete_a_consulta_sem_filtrar_o_idioma_e_sem_reler_o_livro() = runTest {
        val falso = DicionarioFalso { p, _, todos ->
            ResultadoDaChamada.Sucesso(ConsultaDeDicionario(p, if (todos) listOf(verbete("A"), verbete("B")) else emptyList()))
        }
        val vm = DicionarioViewModel("casa", 1, falso, livrosComIdioma("en"))
        vm.consultar()
        advanceUntilIdle()
        assertTrue((vm.estado.value as EstadoDoDicionario.Pronto).resultados.isEmpty())

        vm.consultar(todos = true)
        advanceUntilIdle()

        val pronto = vm.estado.value as EstadoDoDicionario.Pronto
        assertEquals(2, pronto.resultados.size)
        assertTrue(pronto.consultouTodos)
        assertEquals(listOf(false, true), falso.pedidos.map { it.third })
    }

    @Test
    fun sem_servidor_explica_que_o_dicionario_precisa_dele() = runTest {
        val falso = DicionarioFalso { _, _, _ -> ResultadoDaChamada.Falha("sem rede") }
        val vm = DicionarioViewModel("casa", 1, falso, livrosComIdioma(null))

        vm.consultar()
        advanceUntilIdle()

        val erro = vm.estado.value as EstadoDoDicionario.Erro
        assertTrue(erro.motivo.contains("precisa do servidor"))
    }

    @Test
    fun erro_do_servidor_mostra_o_motivo_dele() = runTest {
        val falso = DicionarioFalso { _, _, _ -> ResultadoDaChamada.Falha("palavra inválida", 422) }
        val vm = DicionarioViewModel("casa", 1, falso, livrosComIdioma(null))

        vm.consultar()
        advanceUntilIdle()

        assertEquals("palavra inválida", (vm.estado.value as EstadoDoDicionario.Erro).motivo)
    }

    @Test
    fun agrupa_por_dicionario_na_ordem_do_servidor() {
        val grupos = agruparPorDicionario(listOf(verbete("A", "casa"), verbete("B"), verbete("A", "Casa")))

        assertEquals(listOf("A", "B"), grupos.map { it.first })
        assertEquals(listOf("casa", "Casa"), grupos.first().second.map { it.entrada })
    }

    @Test
    fun a_palavra_vem_da_selecao_sem_espacos_e_uma_frase_longa_nao_e_palavra() {
        assertEquals("casa", palavraParaProcurar("  casa \n"))
        assertEquals("Winter fell", palavraParaProcurar("Winter fell"))
        assertNull(palavraParaProcurar("   "))
        assertNull(palavraParaProcurar("uma frase com muitas palavras seguidas"))
        assertNull(palavraParaProcurar("a".repeat(LIMITE_DA_PALAVRA_DO_DICIONARIO + 1)))
    }
}
