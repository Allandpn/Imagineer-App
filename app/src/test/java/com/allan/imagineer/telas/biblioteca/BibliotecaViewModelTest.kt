package com.allan.imagineer.telas.biblioteca

import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.CompletableDeferred
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

private fun livro(id: Int, titulo: String = "Livro $id") = LivroResumo(
    id = id,
    titulo = titulo,
    autor = null,
    idioma = null,
    nome_arquivo = "livro$id.epub",
    data_importacao = "2026-09-30T01:11:16",
    total_de_capitulos = 10,
    capitulos_ignorados = 0,
)

/** Repositório falso: devolve o que o teste combinar, e conta as chamadas. */
private class RepositorioFalso(var resposta: ResultadoDaChamada<List<LivroResumo>>) : RepositorioDeLivros {
    var chamadas = 0

    /** Se preenchido, a chamada "trava" até o teste liberar — para simular demora. */
    var trava: CompletableDeferred<Unit>? = null

    override suspend fun listarLivros(): ResultadoDaChamada<List<LivroResumo>> {
        chamadas++
        trava?.await()
        return resposta
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BibliotecaViewModelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    @Test
    fun `comeca carregando`() = runTest {
        val vm = BibliotecaViewModel(RepositorioFalso(ResultadoDaChamada.Sucesso(emptyList())))

        assertEquals(EstadoDaBiblioteca.Carregando, vm.estado.value)
    }

    @Test
    fun `mostra a lista quando ha livros`() = runTest {
        val livros = listOf(livro(1), livro(2))
        val vm = BibliotecaViewModel(RepositorioFalso(ResultadoDaChamada.Sucesso(livros)))

        vm.carregar()
        advanceUntilIdle()

        assertEquals(EstadoDaBiblioteca.Lista(livros), vm.estado.value)
    }

    @Test
    fun `lista vazia vira o estado vazio, e nao erro`() = runTest {
        val vm = BibliotecaViewModel(RepositorioFalso(ResultadoDaChamada.Sucesso(emptyList())))

        vm.carregar()
        advanceUntilIdle()

        assertEquals(EstadoDaBiblioteca.Vazia, vm.estado.value)
    }

    @Test
    fun `falha vira o estado de erro com o motivo`() = runTest {
        val vm = BibliotecaViewModel(
            RepositorioFalso(ResultadoDaChamada.Falha("Não consegui falar com o servidor.")),
        )

        vm.carregar()
        advanceUntilIdle()

        assertEquals(
            EstadoDaBiblioteca.Erro("Não consegui falar com o servidor."),
            vm.estado.value,
        )
    }

    @Test
    fun `tentar de novo depois do erro recupera a lista`() = runTest {
        val repositorio = RepositorioFalso(ResultadoDaChamada.Falha("Pi desligado"))
        val vm = BibliotecaViewModel(repositorio)
        vm.carregar()
        advanceUntilIdle()
        assertTrue(vm.estado.value is EstadoDaBiblioteca.Erro)

        repositorio.resposta = ResultadoDaChamada.Sucesso(listOf(livro(1)))
        vm.carregar()
        advanceUntilIdle()

        assertEquals(EstadoDaBiblioteca.Lista(listOf(livro(1))), vm.estado.value)
    }

    @Test
    fun `recarregar com lista na tela mantem a lista visivel enquanto atualiza`() = runTest {
        val repositorio = RepositorioFalso(ResultadoDaChamada.Sucesso(listOf(livro(1))))
        val vm = BibliotecaViewModel(repositorio)
        vm.carregar()
        advanceUntilIdle()

        repositorio.trava = CompletableDeferred()
        vm.carregar()
        advanceUntilIdle()

        // A lista antiga continua ali, marcada como "atualizando".
        assertEquals(
            EstadoDaBiblioteca.Lista(listOf(livro(1)), atualizando = true),
            vm.estado.value,
        )

        repositorio.resposta = ResultadoDaChamada.Sucesso(listOf(livro(1), livro(2)))
        repositorio.trava!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            EstadoDaBiblioteca.Lista(listOf(livro(1), livro(2))),
            vm.estado.value,
        )
    }

    @Test
    fun `falha ao recarregar com lista na tela cai no erro`() = runTest {
        val repositorio = RepositorioFalso(ResultadoDaChamada.Sucesso(listOf(livro(1))))
        val vm = BibliotecaViewModel(repositorio)
        vm.carregar()
        advanceUntilIdle()

        repositorio.resposta = ResultadoDaChamada.Falha("sem conexão")
        vm.carregar()
        advanceUntilIdle()

        assertEquals(EstadoDaBiblioteca.Erro("sem conexão"), vm.estado.value)
    }

    @Test
    fun `duas recargas seguidas nao deixam a resposta velha sobrescrever a nova`() = runTest {
        val repositorio = RepositorioFalso(ResultadoDaChamada.Sucesso(listOf(livro(1))))
        repositorio.trava = CompletableDeferred()
        val vm = BibliotecaViewModel(repositorio)

        vm.carregar() // primeira chamada, travada
        advanceUntilIdle()
        // Nenhuma resposta ainda; o usuário puxa para atualizar de novo.
        repositorio.resposta = ResultadoDaChamada.Sucesso(listOf(livro(2)))
        vm.carregar() // cancela a primeira e começa outra
        advanceUntilIdle()
        repositorio.trava!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(EstadoDaBiblioteca.Lista(listOf(livro(2))), vm.estado.value)
    }

    @Test
    fun `descreve capitulos no singular e no plural`() {
        assertEquals("1 capítulo", descreverCapitulos(1, 0))
        assertEquals("12 capítulos", descreverCapitulos(12, 0))
    }

    @Test
    fun `so mostra ignorados quando ha algum`() {
        assertEquals("12 capítulos · 1 ignorado", descreverCapitulos(12, 1))
        assertEquals("12 capítulos · 3 ignorados", descreverCapitulos(12, 3))
    }
}
