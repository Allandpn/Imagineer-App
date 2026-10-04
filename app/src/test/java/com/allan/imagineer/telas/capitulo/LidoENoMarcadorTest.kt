package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.rede.CapituloAjuste
import com.allan.imagineer.rede.CapituloDetalhe
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.Marcador
import com.allan.imagineer.rede.RepositorioDeCapitulos
import com.allan.imagineer.rede.RepositorioDeMarcador
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class CapitulosQueMarcam(var lido: Boolean = false) : RepositorioDeCapitulos {
    val ajustes = mutableListOf<CapituloAjuste>()
    var resposta: ResultadoDaChamada<CapituloResumo> = ResultadoDaChamada.Sucesso(CapituloResumo(5, 2, "Catelyn", false, 100))

    override suspend fun abrirCapitulo(capituloId: Int): ResultadoDaChamada<CapituloDetalhe> = ResultadoDaChamada.Sucesso(
        CapituloDetalhe(id = 5, ordem = 2, titulo = "Catelyn", ignorado = false, tamanho_do_texto = 10, lido = lido, livro_id = 1, texto = "Um.\n\nDois."),
    )

    override suspend fun ajustarCapitulo(capituloId: Int, ajuste: CapituloAjuste): ResultadoDaChamada<CapituloResumo> {
        ajustes += ajuste
        return resposta
    }
}

private class MarcadorFalso : RepositorioDeMarcador {
    val gravados = mutableListOf<Triple<Int, Int, Int>>()
    override suspend fun ler(livroId: Int): ResultadoDaChamada<Marcador?> = ResultadoDaChamada.Sucesso(null)
    override suspend fun gravar(livroId: Int, capituloId: Int, posicao: Int): ResultadoDaChamada<Unit> {
        gravados += Triple(livroId, capituloId, posicao)
        return ResultadoDaChamada.Sucesso(Unit)
    }
}

/** O capítulo lido e onde a pessoa parou (LE1, LE2, LE4, LE5). */
@OptIn(ExperimentalCoroutinesApi::class)
class LidoENoMarcadorTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private fun lido(vm: CapituloViewModel) = (vm.estado.value as EstadoDoCapitulo.Pronto).capitulo.lido

    private suspend fun kotlinx.coroutines.test.TestScope.vmPronto(capitulos: CapitulosQueMarcam, marcador: MarcadorFalso = MarcadorFalso()): CapituloViewModel {
        val vm = CapituloViewModel(5, capitulos, ArtefatosFalso(), marcador)
        vm.carregar(); advanceUntilIdle()
        return vm
    }

    @Test
    fun `LE4 chegar ao fim marca como lido na hora e manda o servidor`() = runTest {
        val capitulos = CapitulosQueMarcam()
        val vm = vmPronto(capitulos)

        vm.chegouAoFim()
        assertTrue(lido(vm)) // o ✓ aparece antes da resposta
        advanceUntilIdle()

        assertEquals(listOf(CapituloAjuste(lido = true)), capitulos.ajustes)
    }

    @Test
    fun `LE4 chegar ao fim de novo (reler) nao chama o servidor nem desmarca`() = runTest {
        val capitulos = CapitulosQueMarcam(lido = true)
        val vm = vmPronto(capitulos)

        vm.chegouAoFim(); advanceUntilIdle()

        assertTrue(lido(vm))
        assertTrue(capitulos.ajustes.isEmpty())
    }

    @Test
    fun `LE5 desmarcar a mao manda lido falso`() = runTest {
        val capitulos = CapitulosQueMarcam(lido = true)
        val vm = vmPronto(capitulos)

        vm.marcarLido(false); advanceUntilIdle()

        assertEquals(false, lido(vm))
        assertEquals(listOf(CapituloAjuste(lido = false)), capitulos.ajustes)
    }

    @Test
    fun `LE1 se o servidor recusar o lido volta ao que era`() = runTest {
        val capitulos = CapitulosQueMarcam().also { it.resposta = ResultadoDaChamada.Falha("Sem conexão.") }
        val vm = vmPronto(capitulos)

        vm.chegouAoFim(); advanceUntilIdle()

        assertEquals(false, lido(vm))
    }

    @Test
    fun `LE2 gravar a posicao manda o livro, o capitulo e o inicio do paragrafo`() = runTest {
        val marcador = MarcadorFalso()
        val vm = vmPronto(CapitulosQueMarcam(), marcador)

        vm.gravarPosicao(1234); advanceUntilIdle()

        assertEquals(listOf(Triple(1, 5, 1234)), marcador.gravados)
    }

    private fun titulo(vm: CapituloViewModel) = (vm.estado.value as EstadoDoCapitulo.Pronto).capitulo.titulo

    @Test
    fun `RN2 renomear muda o titulo na hora e manda o servidor`() = runTest {
        val capitulos = CapitulosQueMarcam()
        val vm = vmPronto(capitulos)

        vm.renomear("  O muro  ")
        assertEquals("O muro", titulo(vm))
        advanceUntilIdle()

        assertEquals(listOf(CapituloAjuste(titulo = "O muro")), capitulos.ajustes)
    }

    @Test
    fun `RN3 titulo em branco volta ao padrao (nulo na tela, vazio no servidor)`() = runTest {
        val capitulos = CapitulosQueMarcam()
        val vm = vmPronto(capitulos)

        vm.renomear("   "); advanceUntilIdle()

        assertEquals(null, titulo(vm))
        assertEquals(listOf(CapituloAjuste(titulo = "")), capitulos.ajustes)
    }

    @Test
    fun `RN2 se o servidor recusar o titulo volta ao que era e o motivo chega a quem pediu`() = runTest {
        val capitulos = CapitulosQueMarcam().also { it.resposta = ResultadoDaChamada.Falha("Sem conexão.") }
        val vm = vmPronto(capitulos)
        val motivos = mutableListOf<String>()

        vm.renomear("Novo") { motivos += it }; advanceUntilIdle()

        assertEquals("Catelyn", titulo(vm))
        assertEquals(listOf("Sem conexão."), motivos)
    }

    @Test
    fun `renomear para o mesmo titulo nao chama o servidor`() = runTest {
        val capitulos = CapitulosQueMarcam()
        val vm = vmPronto(capitulos)

        vm.renomear("Catelyn"); advanceUntilIdle()

        assertTrue(capitulos.ajustes.isEmpty())
    }
}

