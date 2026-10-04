package com.allan.imagineer.telas.menu

import com.allan.imagineer.rede.CustosDoMes
import com.allan.imagineer.rede.GastoAgrupado
import com.allan.imagineer.rede.RepositorioDeCustos
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun custos(mes: String, meses: List<String> = listOf("2026-10", "2026-09", "2026-07")) = CustosDoMes(
    mes = mes, total = "1.5", chamadas = 3, meses_com_gasto = meses,
    por_provedor = listOf(GastoAgrupado("fal", total = "0.025", chamadas = 1)),
)

private class CustosFalso(var resposta: (String?) -> ResultadoDaChamada<CustosDoMes> = { ResultadoDaChamada.Sucesso(custos(it ?: "2026-10")) }) : RepositorioDeCustos {
    val pedidos = mutableListOf<String?>()
    override suspend fun custos(mes: String?): ResultadoDaChamada<CustosDoMes> {
        pedidos += mes
        return resposta(mes)
    }
}

/** A tela de custos (CU5, CU6): navegar entre meses e mostrar os valores. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustosTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private fun mes(vm: CustosViewModel) = ((vm.estado.value as EstadoDosCustos.Pronto).dados).mes

    @Test
    fun `abre no mes atual do servidor`() = runTest {
        val repositorio = CustosFalso()
        val vm = CustosViewModel(repositorio)

        vm.carregar(null); advanceUntilIdle()

        assertEquals(listOf<String?>(null), repositorio.pedidos)
        assertEquals("2026-10", mes(vm))
    }

    @Test
    fun `as setas andam so pelos meses que tem gasto`() = runTest {
        val vm = CustosViewModel(CustosFalso())
        vm.carregar(null); advanceUntilIdle()

        vm.mesAnterior(); advanceUntilIdle()
        assertEquals("2026-09", mes(vm))
        vm.mesAnterior(); advanceUntilIdle()
        assertEquals("2026-07", mes(vm)) // pulou agosto, que não tem gasto
        vm.mesAnterior(); advanceUntilIdle()
        assertEquals("2026-07", mes(vm)) // o mais antigo: não anda
        vm.mesSeguinte(); advanceUntilIdle()
        assertEquals("2026-09", mes(vm))
    }

    @Test
    fun `um mes sem gasto tambem entra na navegacao quando e o da tela`() {
        val dados = custos("2026-11", meses = listOf("2026-10"))

        assertEquals(listOf("2026-11", "2026-10"), mesesNavegaveis(dados))
        assertTrue(temMesAnterior(dados))
        assertFalse(temMesSeguinte(dados))
    }

    @Test
    fun `servidor sem a rota vira indisponivel, outra falha vira erro`() = runTest {
        val antigo = CustosViewModel(CustosFalso { ResultadoDaChamada.Falha("Não existe.", codigoHttp = 404) })
        antigo.carregar(null); advanceUntilIdle()
        assertEquals(EstadoDosCustos.Indisponivel, antigo.estado.value)

        val quebrado = CustosViewModel(CustosFalso { ResultadoDaChamada.Falha("Sem conexão.") })
        quebrado.carregar(null); advanceUntilIdle()
        assertEquals(EstadoDosCustos.Erro("Sem conexão."), quebrado.estado.value)
    }

    @Test
    fun `dolares abaixo de um mostram tres casas, acima duas, e a estimativa leva til`() {
        assertEquals("US$ 0,025", formatarDolar("0.0250"))
        assertEquals("US$ 0,0004", formatarDolar("0.0004")) // uma tradução: não pode virar 0,000
        assertEquals("US$ 0,010", formatarDolar("0.01"))
        assertEquals("US$ 12,40", formatarDolar("12.4"))
        assertEquals("US$ 0,00", formatarDolar("0"))
        assertEquals("~US$ 1,50", formatarDolar("1.5", estimado = true))
        assertEquals("US$ 0,00", formatarDolar("lixo")) // valor ilegível não derruba a tela
    }

    @Test
    fun `nomes do mes, do provedor e da operacao em portugues`() {
        assertEquals("outubro de 2026", nomeDoMes("2026-10"))
        assertEquals("mal-escrito", nomeDoMes("mal-escrito"))
        assertEquals("fal.ai", nomeDoProvedor("fal"))
        assertEquals("Análise do capítulo", nomeDaOperacao("extracao"))
        assertEquals("Outra", nomeDaOperacao("outra"))
    }
}
