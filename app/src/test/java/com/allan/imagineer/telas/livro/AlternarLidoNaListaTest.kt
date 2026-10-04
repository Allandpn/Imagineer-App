package com.allan.imagineer.telas.livro

import com.allan.imagineer.rede.CapituloAjuste
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private fun capLido(id: Int, lido: Boolean = false) = CapituloResumo(id, id, "C$id", false, 100, lido = lido)

private val LIVRO_DE_TRES = LivroDetalhe(
    id = 1, titulo = "A", nome_arquivo = "a.epub", data_importacao = "x", total_de_capitulos = 3, capitulos_ignorados = 0,
    capitulos = listOf(capLido(1, lido = true), capLido(2), capLido(3)), capitulos_lidos = 1,
)

/** Tocar no ícone de lido na lista do livro marca e desmarca (LE5). */
@OptIn(ExperimentalCoroutinesApi::class)
class AlternarLidoNaListaTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private suspend fun kotlinx.coroutines.test.TestScope.vmPronto(capitulos: CapitulosFalso): LivroViewModel {
        val vm = LivroViewModel(1, LivrosFalso(ResultadoDaChamada.Sucesso(LIVRO_DE_TRES)), capitulos, PerfisFalso())
        vm.carregar(); advanceUntilIdle()
        return vm
    }

    private fun lidos(vm: LivroViewModel) = (vm.estado.value as EstadoDoLivro.Pronto).livro.capitulos.filter { it.lido }.map { it.id }
    private fun contagem(vm: LivroViewModel) = (vm.estado.value as EstadoDoLivro.Pronto).livro.capitulos_lidos

    @Test
    fun `tocar num capitulo nao lido marca, na hora, e manda o servidor`() = runTest {
        val capitulos = CapitulosFalso()
        val vm = vmPronto(capitulos)

        vm.alternarLido(2)
        assertEquals(listOf(1, 2), lidos(vm)) // o ícone muda antes da resposta
        advanceUntilIdle()

        assertEquals(listOf(2 to CapituloAjuste(lido = true)), capitulos.ajustes)
        assertEquals(2, contagem(vm))
    }

    @Test
    fun `tocar num capitulo lido desmarca`() = runTest {
        val capitulos = CapitulosFalso()
        val vm = vmPronto(capitulos)

        vm.alternarLido(1); advanceUntilIdle()

        assertEquals(emptyList<Int>(), lidos(vm))
        assertEquals(listOf(1 to CapituloAjuste(lido = false)), capitulos.ajustes)
        assertEquals(0, contagem(vm))
    }

    @Test
    fun `se o servidor recusar o icone volta e um aviso explica`() = runTest {
        val capitulos = CapitulosFalso().also { it.resposta = { _, _ -> ResultadoDaChamada.Falha("Sem conexão.") } }
        val vm = vmPronto(capitulos)

        vm.alternarLido(2); advanceUntilIdle()

        assertEquals(listOf(1), lidos(vm))
        assertEquals("Não consegui marcar o capítulo.", vm.avisos.first().texto)
    }
}
