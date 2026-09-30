package com.allan.imagineer.telas.livro

import com.allan.imagineer.rede.CapituloAjuste
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/**
 * As regras de negócio de arquivar e restaurar **em lote, por seleção** (item 7.5a,
 * segunda revisão do incremento 6). Os comentários "R#" apontam para a regra numerada
 * na especificação. Reaproveita os falsos do `LivroViewModelTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LivroViewModelLoteTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    /** Cinco capítulos: 1, 2, 3 e 4 ativos ("Bran", "Catelyn", "Eddard", "Jon") e o 5 arquivado. */
    private val cinco: LivroDetalhe = livro(
        capitulo(1, titulo = "Bran"),
        capitulo(2, titulo = "Catelyn"),
        capitulo(3, titulo = "Eddard"),
        capitulo(4, titulo = "Jon"),
        capitulo(5, ignorado = true, titulo = "Glossário"),
    )

    private fun <T> TestScope.coletar(fluxo: Flow<T>): List<T> {
        val recebidos = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fluxo.toList(recebidos) }
        return recebidos
    }

    private fun TestScope.vmPronto(
        livro: LivroDetalhe = cinco,
        livros: LivrosFalso = LivrosFalso(ResultadoDaChamada.Sucesso(livro)),
        capitulos: CapitulosFalso = CapitulosFalso(),
    ): LivroViewModel {
        val vm = LivroViewModel(1, livros, capitulos, PerfisFalso())
        vm.carregar()
        advanceUntilIdle()
        return vm
    }

    private fun LivroViewModel.ignorados() =
        (estado.value as EstadoDoLivro.Pronto).livro.capitulos.filter { it.ignorado }.map { it.id }

    /** O servidor "obedece" — mas falha para os ids dados. */
    private fun CapitulosFalso.falhandoEm(vararg ids: Int, motivo: String = "Não consegui falar com o servidor.") {
        resposta = { id, ajuste ->
            if (id in ids) ResultadoDaChamada.Falha(motivo)
            else ResultadoDaChamada.Sucesso(capitulo(id, ignorado = ajuste.ignorado == true))
        }
    }

    // ------------------------------------------------------------------ //
    // Entrar e sair do modo de seleção (R1 a R5)
    // ------------------------------------------------------------------ //

    @Test
    fun `R1 o botao do topo entra no modo com zero marcados`() = runTest {
        val vm = vmPronto()

        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)

        assertEquals(Selecao(ModoDeSelecao.ARQUIVAR, emptySet()), vm.selecao.value)
    }

    @Test
    fun `R1 o toque longo entra no modo ja com a linha marcada`() = runTest {
        val vm = vmPronto()

        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR, idInicial = 3)

        assertEquals(Selecao(ModoDeSelecao.ARQUIVAR, setOf(3)), vm.selecao.value)
    }

    @Test
    fun `R1 nao entra no modo sem capitulos elegiveis`() = runTest {
        val todosArquivados = livro(capitulo(1, ignorado = true), capitulo(2, ignorado = true))
        val vm = vmPronto(todosArquivados)

        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)

        assertNull(vm.selecao.value)
    }

    @Test
    fun `R1 restaurar so entra se ha arquivados`() = runTest {
        val vm = vmPronto(livro(capitulo(1), capitulo(2)))

        vm.iniciarSelecao(ModoDeSelecao.RESTAURAR)

        assertNull(vm.selecao.value)
    }

    @Test
    fun `R5 o toque longo num capitulo que o modo nao permite nao entra`() = runTest {
        val vm = vmPronto()

        // O capítulo 5 está arquivado: não pode ser marcado no modo ARQUIVAR.
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR, idInicial = 5)

        assertNull(vm.selecao.value)
    }

    @Test
    fun `entrar de novo com o modo ativo e ignorado`() = runTest {
        val vm = vmPronto()
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR, idInicial = 1)

        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR, idInicial = 2)

        assertEquals(setOf(1), vm.selecao.value!!.ids)
    }

    @Test
    fun `nao entra no modo antes de o livro estar na tela`() = runTest {
        val vm = LivroViewModel(1, LivrosFalso(ResultadoDaChamada.Sucesso(cinco)), CapitulosFalso(), PerfisFalso())

        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)

        assertNull(vm.selecao.value)
    }

    @Test
    fun `alternar marca e desmarca`() = runTest {
        val vm = vmPronto()
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)

        vm.alternarSelecao(1)
        vm.alternarSelecao(3)
        assertEquals(setOf(1, 3), vm.selecao.value!!.ids)

        vm.alternarSelecao(1)
        assertEquals(setOf(3), vm.selecao.value!!.ids)
    }

    @Test
    fun `R2 desmarcar o ultimo nao sai do modo`() = runTest {
        val vm = vmPronto()
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR, idInicial = 2)

        vm.alternarSelecao(2)

        // Quem entrou pelo botão do topo começa com zero marcados: sair sozinho seria incoerente.
        assertEquals(Selecao(ModoDeSelecao.ARQUIVAR, emptySet()), vm.selecao.value)
    }

    @Test
    fun `R5 alternar nao marca o que o modo nao permite`() = runTest {
        val vm = vmPronto()
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)

        vm.alternarSelecao(5) // arquivado
        vm.alternarSelecao(99) // não existe

        assertTrue(vm.selecao.value!!.ids.isEmpty())
    }

    @Test
    fun `R5 nao se marca um capitulo com chamada em andamento`() = runTest {
        val capitulos = CapitulosFalso().apply { trava = CompletableDeferred() }
        val vm = vmPronto(capitulos = capitulos)
        vm.arquivar(1) // deixa o capítulo 1 "em andamento"
        advanceUntilIdle()
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)

        vm.alternarSelecao(1)

        assertTrue(vm.selecao.value!!.ids.isEmpty())
    }

    @Test
    fun `R2 cancelar sai do modo sem fazer nada`() = runTest {
        val capitulos = CapitulosFalso()
        val vm = vmPronto(capitulos = capitulos)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR, idInicial = 1)

        vm.cancelarSelecao()

        assertNull(vm.selecao.value)
        assertTrue(capitulos.ajustes.isEmpty())
    }

    // ------------------------------------------------------------------ //
    // Confirmar (R6 a R8)
    // ------------------------------------------------------------------ //

    @Test
    fun `R6 confirmar com zero marcados nao faz nada`() = runTest {
        val capitulos = CapitulosFalso()
        val vm = vmPronto(capitulos = capitulos)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)

        vm.confirmarSelecao()
        advanceUntilIdle()

        assertTrue(capitulos.ajustes.isEmpty())
        assertEquals(Selecao(ModoDeSelecao.ARQUIVAR, emptySet()), vm.selecao.value)
    }

    @Test
    fun `R7 arquiva uma chamada por capitulo, na ordem da lista e nao na da marcacao`() = runTest {
        val capitulos = CapitulosFalso()
        val vm = vmPronto(capitulos = capitulos)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)
        vm.alternarSelecao(4)
        vm.alternarSelecao(1)
        vm.alternarSelecao(3)

        vm.confirmarSelecao()
        advanceUntilIdle()

        assertEquals(listOf(1, 3, 4), capitulos.ajustes.map { it.first })
        assertTrue(capitulos.ajustes.all { it.second == CapituloAjuste(ignorado = true) })
        assertEquals(listOf(1, 3, 4, 5), vm.ignorados())
    }

    @Test
    fun `R11 tudo certo sai do modo e avisa com Desfazer para exatamente o lote`() = runTest {
        val vm = vmPronto()
        val avisos = coletar(vm.avisos)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)
        vm.alternarSelecao(2)
        vm.alternarSelecao(3)

        vm.confirmarSelecao()
        advanceUntilIdle()

        assertNull(vm.selecao.value)
        assertEquals(listOf(Aviso("2 capítulos arquivados", desfazerCapitulos = listOf(2, 3))), avisos)
    }

    @Test
    fun `R11 um capitulo so nomeia o capitulo`() = runTest {
        val vm = vmPronto()
        val avisos = coletar(vm.avisos)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR, idInicial = 2)

        vm.confirmarSelecao()
        advanceUntilIdle()

        assertEquals(listOf(Aviso("Arquivado: Catelyn", desfazerCapitulos = listOf(2))), avisos)
    }

    @Test
    fun `R8 nao e otimista, durante o lote os marcados mostram progresso e nada muda de lista`() = runTest {
        val capitulos = CapitulosFalso().apply { trava = CompletableDeferred() }
        val vm = vmPronto(capitulos = capitulos)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)
        vm.alternarSelecao(1)
        vm.alternarSelecao(2)

        vm.confirmarSelecao()
        advanceUntilIdle()

        val pronto = vm.estado.value as EstadoDoLivro.Pronto
        assertEquals(setOf(1, 2), pronto.ajustando)
        assertEquals(listOf(5), vm.ignorados()) // ainda só o que já era arquivado
        assertEquals(true, vm.selecao.value!!.executando)
    }

    @Test
    fun `com o lote no ar nao se marca, nem cancela, nem confirma de novo`() = runTest {
        val capitulos = CapitulosFalso().apply { trava = CompletableDeferred() }
        val vm = vmPronto(capitulos = capitulos)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)
        vm.alternarSelecao(1)
        vm.confirmarSelecao()
        advanceUntilIdle()

        vm.alternarSelecao(3)
        vm.cancelarSelecao()
        vm.confirmarSelecao()
        advanceUntilIdle()

        assertEquals(setOf(1), vm.selecao.value!!.ids)
        assertEquals(1, capitulos.ajustes.size) // um único PATCH
    }

    // ------------------------------------------------------------------ //
    // Quando algo falha (R9 e R10)
    // ------------------------------------------------------------------ //

    @Test
    fun `R9 para no primeiro erro, e o resto continua marcado`() = runTest {
        val capitulos = CapitulosFalso().apply { falhandoEm(3) }
        val vm = vmPronto(capitulos = capitulos)
        val avisos = coletar(vm.avisos)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)
        listOf(1, 2, 3, 4).forEach(vm::alternarSelecao)

        vm.confirmarSelecao()
        advanceUntilIdle()

        // O 4 nem chegou a ser tentado: com o servidor fora, seriam N timeouts.
        assertEquals(listOf(1, 2, 3), capitulos.ajustes.map { it.first })
        assertEquals(listOf(1, 2, 5), vm.ignorados()) // os dois primeiros ficaram arquivados
        // O que falhou e o que não foi tentado continuam marcados, e o modo continua.
        assertEquals(Selecao(ModoDeSelecao.ARQUIVAR, setOf(3, 4)), vm.selecao.value)
        assertTrue((vm.estado.value as EstadoDoLivro.Pronto).ajustando.isEmpty())
        // R10: aviso "K de N", com Desfazer só para os que deram certo.
        assertEquals(
            listOf(Aviso("2 de 4 arquivados. Não consegui falar com o servidor.", desfazerCapitulos = listOf(1, 2))),
            avisos,
        )
    }

    @Test
    fun `R10 se nenhum deu certo, so o motivo e sem Desfazer`() = runTest {
        val capitulos = CapitulosFalso().apply { falhandoEm(1, 2) }
        val vm = vmPronto(capitulos = capitulos)
        val avisos = coletar(vm.avisos)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)
        vm.alternarSelecao(1)
        vm.alternarSelecao(2)

        vm.confirmarSelecao()
        advanceUntilIdle()

        assertEquals(listOf(Aviso("Não consegui falar com o servidor.")), avisos)
        assertEquals(listOf(1), capitulos.ajustes.map { it.first }) // parou no primeiro
        assertEquals(setOf(1, 2), vm.selecao.value!!.ids)
    }

    @Test
    fun `R9 tentar de novo com o mesmo botao termina o que sobrou`() = runTest {
        val capitulos = CapitulosFalso().apply { falhandoEm(3) }
        val vm = vmPronto(capitulos = capitulos)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)
        listOf(1, 3, 4).forEach(vm::alternarSelecao)
        vm.confirmarSelecao()
        advanceUntilIdle()
        assertEquals(setOf(3, 4), vm.selecao.value!!.ids)

        capitulos.falhandoEm() // o servidor voltou
        vm.confirmarSelecao()
        advanceUntilIdle()

        assertNull(vm.selecao.value)
        assertEquals(listOf(1, 3, 4, 5), vm.ignorados())
    }

    // ------------------------------------------------------------------ //
    // Desfazer (R12) e restaurar (R13)
    // ------------------------------------------------------------------ //

    @Test
    fun `R12 desfazer restaura exatamente os do lote e nao gera outro aviso`() = runTest {
        val capitulos = CapitulosFalso()
        val vm = vmPronto(capitulos = capitulos)
        val avisos = coletar(vm.avisos)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)
        vm.alternarSelecao(1)
        vm.alternarSelecao(2)
        vm.confirmarSelecao()
        advanceUntilIdle()
        val doLote = avisos.single().desfazerCapitulos

        vm.desfazerArquivamento(doLote)
        advanceUntilIdle()

        // O 5, que já estava arquivado antes do lote, NÃO é tocado.
        assertEquals(listOf(5), vm.ignorados())
        assertEquals(1, avisos.size) // só o aviso do arquivamento; o desfazer não gera outro
    }

    @Test
    fun `R13 restaurar em lote sai do modo sem aviso de sucesso`() = runTest {
        val dois = livro(capitulo(1, ignorado = true), capitulo(2, ignorado = true), capitulo(3))
        val capitulos = CapitulosFalso()
        val vm = vmPronto(dois, capitulos = capitulos)
        val avisos = coletar(vm.avisos)
        vm.iniciarSelecao(ModoDeSelecao.RESTAURAR)
        vm.alternarSelecao(1)
        vm.alternarSelecao(2)

        vm.confirmarSelecao()
        advanceUntilIdle()

        assertTrue(capitulos.ajustes.all { it.second == CapituloAjuste(ignorado = false) })
        assertEquals(emptyList<Int>(), vm.ignorados())
        assertNull(vm.selecao.value)
        assertTrue(avisos.isEmpty())
    }

    @Test
    fun `R13 falha ao restaurar avisa, sem Desfazer`() = runTest {
        val dois = livro(capitulo(1, ignorado = true), capitulo(2, ignorado = true))
        val capitulos = CapitulosFalso().apply { falhandoEm(2) }
        val vm = vmPronto(dois, capitulos = capitulos)
        val avisos = coletar(vm.avisos)
        vm.iniciarSelecao(ModoDeSelecao.RESTAURAR)
        vm.alternarSelecao(1)
        vm.alternarSelecao(2)

        vm.confirmarSelecao()
        advanceUntilIdle()

        assertEquals(
            listOf(Aviso("1 de 2 restaurados. Não consegui falar com o servidor.")),
            avisos,
        )
        assertEquals(setOf(2), vm.selecao.value!!.ids)
    }

    @Test
    fun `no modo restaurar so se marca arquivado`() = runTest {
        val vm = vmPronto()
        vm.iniciarSelecao(ModoDeSelecao.RESTAURAR)

        vm.alternarSelecao(1) // ativo
        vm.alternarSelecao(5) // arquivado

        assertEquals(setOf(5), vm.selecao.value!!.ids)
    }

    // ------------------------------------------------------------------ //
    // Consistência (R15)
    // ------------------------------------------------------------------ //

    @Test
    fun `R15 ao recarregar, desmarca o que deixou de ser elegivel`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(cinco))
        val vm = vmPronto(livros = livros)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)
        listOf(1, 2, 3).forEach(vm::alternarSelecao)

        // Por outro caminho, o 2 foi arquivado e o 3 foi removido do livro.
        livros.resposta = ResultadoDaChamada.Sucesso(
            livro(capitulo(1), capitulo(2, ignorado = true), capitulo(4), capitulo(5, ignorado = true)),
        )
        vm.carregar()
        advanceUntilIdle()

        assertEquals(setOf(1), vm.selecao.value!!.ids)
    }

    @Test
    fun `R15 a poda nao mexe num lote em andamento`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(cinco))
        val capitulos = CapitulosFalso().apply { trava = CompletableDeferred() }
        val vm = vmPronto(livros = livros, capitulos = capitulos)
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR)
        vm.alternarSelecao(1)
        vm.confirmarSelecao()
        advanceUntilIdle()

        vm.carregar()
        advanceUntilIdle()

        assertEquals(Selecao(ModoDeSelecao.ARQUIVAR, setOf(1), executando = true), vm.selecao.value)
    }
}

/** Os textos pequenos da barra do modo de seleção. */
class DescreverSelecionadosTest {

    @Test
    fun `zero, um e varios`() {
        assertEquals("Nenhum selecionado", descreverSelecionados(0))
        assertEquals("1 selecionado", descreverSelecionados(1))
        assertEquals("3 selecionados", descreverSelecionados(3))
    }
}
