package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.rede.CapituloDetalhe
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.livro.LivrosFalso
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

private fun cap(id: Int, ordem: Int, ignorado: Boolean = false) =
    CapituloResumo(id = id, ordem = ordem, titulo = "C$ordem", ignorado = ignorado, tamanho_do_texto = 10)

class IdsDoLeitorTest {

    private val livro = listOf(cap(1, 1), cap(2, 2), cap(3, 3, ignorado = true), cap(4, 4), cap(5, 5))

    @Test
    fun `os capitulos vem em ordem de leitura e os arquivados ficam de fora`() {
        assertEquals(listOf(1, 2, 4, 5), idsDoLeitor(livro, atualId = 2))
    }

    @Test
    fun `um capitulo arquivado aberto entra na lista, no seu lugar`() {
        // Abre-se um arquivado pela área de arquivados: o leitor precisa poder começar nele.
        assertEquals(listOf(1, 2, 3, 4, 5), idsDoLeitor(livro, atualId = 3))
    }

    @Test
    fun `a lista fora de ordem e tratada na ordem do livro`() {
        assertEquals(listOf(1, 2, 5), idsDoLeitor(listOf(cap(5, 5), cap(1, 1), cap(2, 2)), atualId = 2))
    }

    @Test
    fun `o atual que nem consta da lista do livro vira a lista toda`() {
        assertEquals(listOf(99), idsDoLeitor(livro, atualId = 99))
        assertEquals(listOf(99), idsDoLeitor(emptyList(), atualId = 99))
    }

    @Test
    fun `livro de um capitulo so tem esse capitulo`() {
        assertEquals(listOf(1), idsDoLeitor(listOf(cap(1, 1)), atualId = 1))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListaDoLeitorViewModelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val atual = CapituloDetalhe(
        id = 2, ordem = 2, titulo = "C2", ignorado = false, tamanho_do_texto = 4, livro_id = 7, texto = "Texto",
    )

    private fun livro(vararg capitulos: CapituloResumo) = LivroDetalhe(
        id = 7, titulo = "L", nome_arquivo = "l.epub", data_importacao = "2026-09-30T00:00:00",
        total_de_capitulos = capitulos.size, capitulos_ignorados = 0, capitulos = capitulos.toList(),
    )

    @Test
    fun `comeca so com o capitulo aberto, sem esperar ninguem`() = runTest {
        val vm = ListaDoLeitorViewModel(2, LivrosFalso(ResultadoDaChamada.Sucesso(livro(cap(1, 1), cap(2, 2)))))

        val inicial = vm.estado.value

        assertEquals(listOf(2), inicial.ids)
        assertEquals(0, inicial.indiceInicial)
        assertFalse(inicial.completa)
    }

    @Test
    fun `ao receber o capitulo, le o livro e completa a lista com o indice do atual`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(livro(cap(1, 1), cap(2, 2), cap(3, 3))))
        val vm = ListaDoLeitorViewModel(2, livros)

        vm.carregar(atual)
        advanceUntilIdle()

        val lista = vm.estado.value
        assertEquals(listOf(1, 2, 3), lista.ids)
        assertEquals(1, lista.indiceInicial) // o capítulo 2 é a segunda página
        assertTrue(lista.completa)
        assertEquals(1, livros.chamadas)
    }

    @Test
    fun `falha ao ler o livro deixa so o capitulo aberto - o leitor segue sem vizinhos`() = runTest {
        val vm = ListaDoLeitorViewModel(2, LivrosFalso(ResultadoDaChamada.Falha("Não consegui falar com o servidor.")))

        vm.carregar(atual)
        advanceUntilIdle()

        assertEquals(listOf(2), vm.estado.value.ids)
        assertFalse(vm.estado.value.completa)
    }

    @Test
    fun `le o livro uma vez so - girar o tablet nao repete`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(livro(cap(1, 1), cap(2, 2))))
        val vm = ListaDoLeitorViewModel(2, livros)

        vm.carregar(atual)
        advanceUntilIdle()
        vm.carregar(atual)
        advanceUntilIdle()

        assertEquals(1, livros.chamadas)
    }

    @Test
    fun `um capitulo arquivado aberto comeca na sua propria pagina`() = runTest {
        val vm = ListaDoLeitorViewModel(3, LivrosFalso(ResultadoDaChamada.Sucesso(livro(cap(1, 1), cap(2, 2), cap(3, 3, ignorado = true), cap(4, 4)))))

        vm.carregar(atual.copy(id = 3, ordem = 3))
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3, 4), vm.estado.value.ids)
        assertEquals(2, vm.estado.value.indiceInicial)
    }
}
