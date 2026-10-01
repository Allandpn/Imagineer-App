package com.allan.imagineer.telas.capitulo.painel

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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** "Ver ficha" a partir do modal fecha o modal: ao voltar, ele não reabre sozinho (feedback do Allan, 01/10/2026). */
@OptIn(ExperimentalCoroutinesApi::class)
class FichaFechaOModalTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    @Test
    fun `abrir a ficha fecha o modal`() = runTest {
        val vm = PainelDeIaViewModel(5, SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(analisado)), ElementosFalso())
        vm.abrirModal(1)
        advanceUntilIdle()
        assertEquals(1, vm.estado.value.emModal)

        vm.fecharModalAoAbrirFicha()

        assertNull(vm.estado.value.emModal)
    }

    @Test
    fun `fechar o modal para abrir a ficha nao mexe no resto do painel`() = runTest {
        val vm = PainelDeIaViewModel(5, SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(analisado)), ElementosFalso())
        vm.abrirModal(1)
        advanceUntilIdle()
        val antes = vm.estado.value

        vm.fecharModalAoAbrirFicha()

        assertEquals(antes.copy(emModal = null), vm.estado.value) // conteúdo, filtro e diálogo continuam como estavam
    }
}
