package com.allan.imagineer.telas.lixeira

import com.allan.imagineer.rede.LivroNaLixeira
import com.allan.imagineer.rede.LivrosDaLixeira
import com.allan.imagineer.rede.LixeiraEsvaziada
import com.allan.imagineer.rede.RepositorioDaLixeiraDeLivros
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

private fun livro(id: Int, imagens: Int = 0, bytes: Long = 0) = LivroNaLixeira(
    id = id, titulo = "Livro $id", autor = "Autor", apagado_em = "2026-10-04T10:00:00Z",
    total_de_capitulos = 12, total_de_imagens = imagens, tamanho_das_imagens_em_bytes = bytes,
)

private class LixeiraDeLivrosFalsa(var livros: List<LivroNaLixeira>) : RepositorioDaLixeiraDeLivros {
    var falhaAoListar: String? = null
    val restaurados = mutableListOf<Int>()
    val apagados = mutableListOf<Int>()
    var esvaziados = 0

    override suspend fun listar(): ResultadoDaChamada<LivrosDaLixeira> =
        falhaAoListar?.let { ResultadoDaChamada.Falha(it) }
            ?: ResultadoDaChamada.Sucesso(LivrosDaLixeira(livros, livros.sumOf { it.tamanho_das_imagens_em_bytes }))

    override suspend fun restaurar(livroId: Int): ResultadoDaChamada<Unit> {
        restaurados += livroId
        livros = livros.filterNot { it.id == livroId }
        return ResultadoDaChamada.Sucesso(Unit)
    }

    override suspend fun apagarDeVez(livroId: Int): ResultadoDaChamada<Unit> {
        apagados += livroId
        livros = livros.filterNot { it.id == livroId }
        return ResultadoDaChamada.Sucesso(Unit)
    }

    override suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada> {
        esvaziados++
        val feitos = livros.size
        livros = emptyList()
        return ResultadoDaChamada.Sucesso(LixeiraEsvaziada(feitos, 2048))
    }
}

/** A lixeira de livros (LT2) sobre o ViewModel genérico de itens (LT1). */
@OptIn(ExperimentalCoroutinesApi::class)
class LixeiraDeLivrosTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private fun vm(repositorio: LixeiraDeLivrosFalsa) = LixeiraDeItensViewModel(FonteDaLixeiraDeLivros(repositorio))

    private fun ids(vm: LixeiraDeItensViewModel<LivroDaLixeira>) =
        (vm.estado.value.carga as CargaDeItens.Pronta).itens.map { it.id }

    @Test
    fun `carregar mostra os livros e o espaco das imagens`() = runTest {
        val vm = vm(LixeiraDeLivrosFalsa(listOf(livro(1, 2, 1000), livro(2, 1, 500))))

        vm.carregar(); advanceUntilIdle()

        assertEquals(listOf(1, 2), ids(vm))
        assertEquals(1500L, (vm.estado.value.carga as CargaDeItens.Pronta).totalEmBytes)
    }

    @Test
    fun `restaurar tira o livro da lista e avisa`() = runTest {
        val repositorio = LixeiraDeLivrosFalsa(listOf(livro(1), livro(2)))
        val vm = vm(repositorio)
        vm.carregar(); advanceUntilIdle()

        vm.restaurar(1); advanceUntilIdle()

        assertEquals(listOf(1), repositorio.restaurados)
        assertEquals(listOf(2), ids(vm))
        assertEquals("Livro restaurado.", vm.estado.value.recado)
    }

    @Test
    fun `apagar de vez pede confirmacao e so apaga depois dela`() = runTest {
        val repositorio = LixeiraDeLivrosFalsa(listOf(livro(1)))
        val vm = vm(repositorio)
        vm.carregar(); advanceUntilIdle()

        vm.pedirApagarDeVez(LivroDaLixeira(livro(1)))
        advanceUntilIdle()
        assertTrue(repositorio.apagados.isEmpty()) // pedir não apaga
        assertTrue(vm.estado.value.confirmacao is ConfirmacaoDeItens.ApagarUm)

        vm.confirmar(); advanceUntilIdle()

        assertEquals(listOf(1), repositorio.apagados)
        assertEquals("Livro apagado de vez.", vm.estado.value.recado)
        assertEquals(emptyList<Int>(), ids(vm))
    }

    @Test
    fun `cancelar a confirmacao nao apaga nada`() = runTest {
        val repositorio = LixeiraDeLivrosFalsa(listOf(livro(1)))
        val vm = vm(repositorio)
        vm.carregar(); advanceUntilIdle()
        vm.pedirApagarDeVez(LivroDaLixeira(livro(1)))

        vm.cancelarConfirmacao(); advanceUntilIdle()

        assertTrue(repositorio.apagados.isEmpty())
        assertEquals(null, vm.estado.value.confirmacao)
    }

    @Test
    fun `esvaziar diz quantos livros e quanto espaco, e so esvazia ao confirmar`() = runTest {
        val repositorio = LixeiraDeLivrosFalsa(listOf(livro(1, 1, 3000), livro(2, 1, 1000)))
        val vm = vm(repositorio)
        vm.carregar(); advanceUntilIdle()

        vm.pedirEsvaziar()
        assertEquals(ConfirmacaoDeItens.EsvaziarTudo(2, 4000), vm.estado.value.confirmacao)
        assertEquals(0, repositorio.esvaziados)

        vm.confirmar(); advanceUntilIdle()

        assertEquals(1, repositorio.esvaziados)
        assertEquals("2 livros apagados de vez; 2,0 KB liberados.", vm.estado.value.recado)
    }

    @Test
    fun `esvaziar uma lixeira vazia nao pede confirmacao`() = runTest {
        val vm = vm(LixeiraDeLivrosFalsa(emptyList()))
        vm.carregar(); advanceUntilIdle()

        vm.pedirEsvaziar()

        assertEquals(null, vm.estado.value.confirmacao)
    }

    @Test
    fun `uma leitura que falha depois de pronta nao apaga a lista da tela`() = runTest {
        val repositorio = LixeiraDeLivrosFalsa(listOf(livro(1)))
        val vm = vm(repositorio)
        vm.carregar(); advanceUntilIdle()

        repositorio.falhaAoListar = "Sem conexão."
        vm.carregar(); advanceUntilIdle()

        assertEquals(listOf(1), ids(vm))
        assertEquals("Sem conexão.", vm.estado.value.recado)
    }

    @Test
    fun `a falha da primeira leitura vira erro, que pode tentar de novo`() = runTest {
        val repositorio = LixeiraDeLivrosFalsa(listOf(livro(1))).also { it.falhaAoListar = "Sem conexão." }
        val vm = vm(repositorio)

        vm.carregar(); advanceUntilIdle()
        assertEquals(CargaDeItens.Erro("Sem conexão."), vm.estado.value.carga)

        repositorio.falhaAoListar = null
        vm.tentarDeNovo(); advanceUntilIdle()
        assertEquals(listOf(1), ids(vm))
    }

    @Test
    fun `os textos do livro e a linha de detalhes`() {
        val textos = FonteDaLixeiraDeLivros(LixeiraDeLivrosFalsa(emptyList())).textos

        assertEquals("1 livro · 1,0 KB", textos.resumo(1, 1024))
        assertEquals("3 livros · 0 B", textos.resumo(3, 0))
        assertEquals("12 capítulos · 2 imagens (1,0 KB)\nApagado em 04/10/2026", detalhesDoLivroNaLixeira(livro(1, 2, 1024)))
        assertEquals("12 capítulos · sem imagens\nApagado em 04/10/2026", detalhesDoLivroNaLixeira(livro(1)))
    }
}
