package com.allan.imagineer.telas.configuracao

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.rede.ResultadoDoTeste
import com.allan.imagineer.rede.ServidorImagineer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Armazenamento em memória: no lugar do DataStore, sem Android. */
private class ArmazenamentoFalso(inicial: String? = null) : ArmazenamentoDeConfiguracao {
    private val atual = MutableStateFlow(inicial)
    override val urlDoServidor: Flow<String?> = atual

    override suspend fun salvarUrlDoServidor(url: String) {
        atual.value = url
    }

    val salva: String? get() = atual.value
}

/** Servidor falso: responde o que o teste combinar, e registra o que foi pedido. */
private class ServidorFalso(var resposta: ResultadoDoTeste) : ServidorImagineer {
    val urlsTestadas = mutableListOf<String>()

    /** Se preenchido, o teste "trava" até o teste liberar — para simular demora. */
    var trava: CompletableDeferred<Unit>? = null

    override suspend fun testarConexao(urlBase: String): ResultadoDoTeste {
        urlsTestadas += urlBase
        trava?.await()
        return resposta
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConfiguracaoViewModelTest {

    @Before
    fun preparar() {
        // O ViewModel usa Dispatchers.Main, que não existe em teste de JVM.
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        armazenamento: ArmazenamentoFalso = ArmazenamentoFalso(),
        servidor: ServidorFalso = ServidorFalso(ResultadoDoTeste.Conectado(servidorTemChave = true)),
    ) = ConfiguracaoViewModel(armazenamento, servidor)

    @Test
    fun `comeca vazio quando nao ha url salva`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("", vm.estado.value.url)
        assertEquals(EstadoDoTeste.Nenhum, vm.estado.value.teste)
    }

    @Test
    fun `mostra a url ja salva no campo`() = runTest {
        val vm = viewModel(armazenamento = ArmazenamentoFalso("http://servidor:8000"))
        advanceUntilIdle()

        assertEquals("http://servidor:8000", vm.estado.value.url)
    }

    @Test
    fun `testar normaliza o endereco antes de chamar o servidor`() = runTest {
        val servidor = ServidorFalso(ResultadoDoTeste.Conectado(servidorTemChave = true))
        val vm = viewModel(servidor = servidor)
        advanceUntilIdle()

        vm.aoMudarUrl("  100.64.0.5:8000/ ")
        vm.testar()
        advanceUntilIdle()

        assertEquals(listOf("http://100.64.0.5:8000"), servidor.urlsTestadas)
        assertEquals(EstadoDoTeste.Conectado(servidorTemChave = true), vm.estado.value.teste)
    }

    @Test
    fun `url invalida nao chama o servidor e sinaliza o erro`() = runTest {
        val servidor = ServidorFalso(ResultadoDoTeste.Conectado(servidorTemChave = true))
        val vm = viewModel(servidor = servidor)
        advanceUntilIdle()

        vm.aoMudarUrl("ftp://servidor")
        vm.testar()
        advanceUntilIdle()

        assertTrue(servidor.urlsTestadas.isEmpty())
        assertTrue(vm.estado.value.urlInvalida)
    }

    @Test
    fun `falha do teste mostra o motivo`() = runTest {
        val servidor = ServidorFalso(ResultadoDoTeste.Falhou("Não consegui falar com o servidor."))
        val vm = viewModel(servidor = servidor)
        advanceUntilIdle()

        vm.aoMudarUrl("servidor:8000")
        vm.testar()
        advanceUntilIdle()

        assertEquals(
            EstadoDoTeste.Falhou("Não consegui falar com o servidor."),
            vm.estado.value.teste,
        )
    }

    @Test
    fun `salvar depois de conectar grava a url normalizada`() = runTest {
        val armazenamento = ArmazenamentoFalso()
        val vm = viewModel(armazenamento = armazenamento)
        advanceUntilIdle()

        vm.aoMudarUrl("100.64.0.5:8000/")
        vm.testar()
        advanceUntilIdle()
        vm.salvar()
        advanceUntilIdle()

        assertEquals("http://100.64.0.5:8000", armazenamento.salva)
        assertTrue(vm.estado.value.salvou)
    }

    @Test
    fun `salvar mesmo assim depois de falhar grava`() = runTest {
        val armazenamento = ArmazenamentoFalso()
        val vm = viewModel(
            armazenamento = armazenamento,
            servidor = ServidorFalso(ResultadoDoTeste.Falhou("Pi desligado")),
        )
        advanceUntilIdle()

        vm.aoMudarUrl("servidor:8000")
        vm.testar()
        advanceUntilIdle()
        vm.salvar()
        advanceUntilIdle()

        assertEquals("http://servidor:8000", armazenamento.salva)
        assertTrue(vm.estado.value.salvou)
    }

    @Test
    fun `nao salva sem testar antes`() = runTest {
        val armazenamento = ArmazenamentoFalso()
        val vm = viewModel(armazenamento = armazenamento)
        advanceUntilIdle()

        vm.aoMudarUrl("servidor:8000")
        vm.salvar()
        advanceUntilIdle()

        assertNull(armazenamento.salva)
        assertFalse(vm.estado.value.salvou)
    }

    @Test
    fun `mudar o texto invalida o teste anterior`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.aoMudarUrl("servidor:8000")
        vm.testar()
        advanceUntilIdle()
        assertTrue(vm.estado.value.teste is EstadoDoTeste.Conectado)

        vm.aoMudarUrl("outro:9000")

        assertEquals(EstadoDoTeste.Nenhum, vm.estado.value.teste)
    }

    @Test
    fun `resultado de um teste antigo nao sobrescreve o texto novo`() = runTest {
        val servidor = ServidorFalso(ResultadoDoTeste.Falhou("resposta velha"))
        servidor.trava = CompletableDeferred()
        val vm = viewModel(servidor = servidor)
        advanceUntilIdle()

        vm.aoMudarUrl("servidor:8000")
        vm.testar()
        advanceUntilIdle()
        assertEquals(EstadoDoTeste.Testando, vm.estado.value.teste)

        // O usuário edita o campo enquanto o teste ainda está no ar...
        vm.aoMudarUrl("outro:9000")
        // ...e só então o servidor responde.
        servidor.trava!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(EstadoDoTeste.Nenhum, vm.estado.value.teste)
    }
}
