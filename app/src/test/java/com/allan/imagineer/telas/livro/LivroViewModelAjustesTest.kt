package com.allan.imagineer.telas.livro

import com.allan.imagineer.rede.LivroAjuste
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.PerfilRenderizacao
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.comum.EstadoDaRemocao
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

private val AQUARELA = PerfilRenderizacao(id = 7, nome = "Aquarela sombria")
private val MINIMO = PerfilRenderizacao(id = 8, nome = "Mínimo")

/**
 * Editar, escolher o perfil padrão e apagar o livro (item 7.5a, incremento 7).
 * Reaproveita os falsos do `LivroViewModelTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LivroViewModelAjustesTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    /** O livro-base dos testes: "Um Livro", de "Fulano", em "pt-BR", sem perfil e sem pendências. */
    private val base: LivroDetalhe = livro(capitulo(1), capitulo(2))

    private fun <T> TestScope.coletar(fluxo: Flow<T>): List<T> {
        val recebidos = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fluxo.toList(recebidos) }
        return recebidos
    }

    private fun TestScope.vmPronto(
        livro: LivroDetalhe = base,
        livros: LivrosFalso = LivrosFalso(ResultadoDaChamada.Sucesso(livro)),
        perfis: PerfisFalso = PerfisFalso(),
    ): LivroViewModel {
        val vm = LivroViewModel(1, livros, CapitulosFalso(), perfis)
        vm.carregar()
        advanceUntilIdle()
        return vm
    }

    private fun LivroViewModel.livroNaTela() = (estado.value as EstadoDoLivro.Pronto).livro

    // ------------------------------------------------------------------ //
    // Editar
    // ------------------------------------------------------------------ //

    @Test
    fun `nao abre a edicao antes de o livro estar na tela`() = runTest {
        val vm = LivroViewModel(1, LivrosFalso(ResultadoDaChamada.Sucesso(base)), CapitulosFalso(), PerfisFalso())

        vm.abrirEdicao()

        assertEquals(EstadoDaEdicao.Nenhuma, vm.edicao.value)
    }

    @Test
    fun `nada mudou, so fecha e nao chama o servidor`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        val vm = vmPronto(livros = livros)
        vm.abrirEdicao()

        vm.salvarEdicao("Um Livro", "Fulano", "pt-BR")
        advanceUntilIdle()

        assertTrue(livros.ajustes.isEmpty())
        assertEquals(EstadoDaEdicao.Nenhuma, vm.edicao.value)
    }

    @Test
    fun `so o que mudou e enviado, e o livro na tela e atualizado`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        livros.respostaDoAjuste = { ResultadoDaChamada.Sucesso(base.copy(titulo = "Novo Título")) }
        val vm = vmPronto(livros = livros)
        vm.abrirEdicao()

        vm.salvarEdicao("Novo Título", "Fulano", "pt-BR")
        advanceUntilIdle()

        // Autor e idioma não foram tocados: nem vão no ajuste.
        assertEquals(listOf(LivroAjuste(titulo = "Novo Título")), livros.ajustes)
        assertEquals("Novo Título", vm.livroNaTela().titulo)
        assertEquals(EstadoDaEdicao.Nenhuma, vm.edicao.value)
    }

    @Test
    fun `espacos nas pontas sao aparados`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        val vm = vmPronto(livros = livros)
        vm.abrirEdicao()

        vm.salvarEdicao("  Um Livro  ", "  Outro Autor ", "pt-BR")
        advanceUntilIdle()

        // "Um Livro" com espaços é o mesmo título: só o autor mudou.
        assertEquals(listOf(LivroAjuste(autor = "Outro Autor")), livros.ajustes)
    }

    @Test
    fun `titulo em branco bloqueia sem chamar o servidor`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        val vm = vmPronto(livros = livros)
        vm.abrirEdicao()

        vm.salvarEdicao("   ", "Fulano", "pt-BR")
        advanceUntilIdle()

        assertTrue(livros.ajustes.isEmpty())
        assertEquals(EstadoDaEdicao.Editando(erro = "Preencha: título."), vm.edicao.value)
    }

    @Test
    fun `autor em branco bloqueia, para nao gravar string vazia no lugar de nulo`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        val vm = vmPronto(livros = livros)
        vm.abrirEdicao()

        vm.salvarEdicao("Um Livro", "", "pt-BR")
        advanceUntilIdle()

        assertTrue(livros.ajustes.isEmpty())
        assertEquals(EstadoDaEdicao.Editando(erro = "Preencha: autor."), vm.edicao.value)
    }

    @Test
    fun `os dois em branco sao apontados juntos`() = runTest {
        val vm = vmPronto()
        vm.abrirEdicao()

        vm.salvarEdicao("", "", "pt-BR")

        assertEquals(EstadoDaEdicao.Editando(erro = "Preencha: título e autor."), vm.edicao.value)
    }

    @Test
    fun `esvaziar o idioma manda null explicito para limpar`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        livros.respostaDoAjuste = { ResultadoDaChamada.Sucesso(base.copy(idioma = null)) }
        val vm = vmPronto(livros = livros)
        vm.abrirEdicao()

        vm.salvarEdicao("Um Livro", "Fulano", "")
        advanceUntilIdle()

        assertEquals(listOf(LivroAjuste(limparIdioma = true)), livros.ajustes)
        assertNull(vm.livroNaTela().idioma)
    }

    @Test
    fun `trocar o idioma manda o valor novo`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        val vm = vmPronto(livros = livros)
        vm.abrirEdicao()

        vm.salvarEdicao("Um Livro", "Fulano", "en")
        advanceUntilIdle()

        assertEquals(listOf(LivroAjuste(idioma = "en")), livros.ajustes)
    }

    @Test
    fun `idioma em branco num livro que ja nao tinha idioma nao e mudanca`() = runTest {
        val semIdioma = base.copy(idioma = null)
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(semIdioma))
        val vm = vmPronto(livro = semIdioma, livros = livros)
        vm.abrirEdicao()

        vm.salvarEdicao("Um Livro", "Fulano", "")
        advanceUntilIdle()

        assertTrue(livros.ajustes.isEmpty())
    }

    @Test
    fun `titulo pendente e enviado mesmo sem mudar, porque confirma-lo e o que se quer`() = runTest {
        val pendente = base.copy(metadados_pendentes = listOf("titulo"))
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(pendente))
        livros.respostaDoAjuste = { ResultadoDaChamada.Sucesso(base) }
        val vm = vmPronto(livro = pendente, livros = livros)
        vm.abrirEdicao()

        vm.salvarEdicao("Um Livro", "Fulano", "pt-BR")
        advanceUntilIdle()

        assertEquals(listOf(LivroAjuste(titulo = "Um Livro")), livros.ajustes)
        assertTrue(vm.livroNaTela().metadados_pendentes.isEmpty())
    }

    @Test
    fun `falha ao salvar mantem o dialogo aberto com o motivo`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        livros.respostaDoAjuste = { ResultadoDaChamada.Falha("Não consegui falar com o servidor.") }
        val vm = vmPronto(livros = livros)
        vm.abrirEdicao()

        vm.salvarEdicao("Outro", "Fulano", "pt-BR")
        advanceUntilIdle()

        assertEquals(
            EstadoDaEdicao.Editando(salvando = false, erro = "Não consegui falar com o servidor."),
            vm.edicao.value,
        )
        assertEquals("Um Livro", vm.livroNaTela().titulo) // a tela não mudou
    }

    @Test
    fun `cancelar fecha, mas nao enquanto salva`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        val vm = vmPronto(livros = livros)
        vm.abrirEdicao()
        vm.cancelarEdicao()
        assertEquals(EstadoDaEdicao.Nenhuma, vm.edicao.value)

        // Agora com o salvamento no ar (o teste ainda não avançou o relógio, então a
        // chamada não terminou): o cancelamento é ignorado.
        livros.respostaDoAjuste = { ResultadoDaChamada.Sucesso(base) }
        vm.abrirEdicao()
        vm.salvarEdicao("Mudou", "Fulano", "pt-BR")
        vm.cancelarEdicao()
        assertEquals(EstadoDaEdicao.Editando(salvando = true), vm.edicao.value)
        advanceUntilIdle()
    }

    // ------------------------------------------------------------------ //
    // Perfil padrão
    // ------------------------------------------------------------------ //

    private val comPerfil = base.copy(perfil_renderizacao_padrao_id = 7)

    @Test
    fun `mostra o nome do perfil padrao, buscado a parte`() = runTest {
        val perfis = PerfisFalso().apply { abrir = { ResultadoDaChamada.Sucesso(AQUARELA) } }
        val vm = vmPronto(livro = comPerfil, perfis = perfis)

        assertEquals(AQUARELA, (vm.estado.value as EstadoDoLivro.Pronto).perfil)
        assertEquals(listOf(7), perfis.abertos)
    }

    @Test
    fun `livro sem perfil padrao nao busca perfil nenhum`() = runTest {
        val perfis = PerfisFalso()

        vmPronto(perfis = perfis)

        assertTrue(perfis.abertos.isEmpty())
    }

    @Test
    fun `se a busca do nome falha, o livro continua na tela sem o nome`() = runTest {
        val perfis = PerfisFalso().apply { abrir = { ResultadoDaChamada.Falha("sem conexão") } }

        val vm = vmPronto(livro = comPerfil, perfis = perfis)

        val pronto = vm.estado.value as EstadoDoLivro.Pronto
        assertNull(pronto.perfil)
        assertEquals(7, pronto.livro.perfil_renderizacao_padrao_id)
    }

    @Test
    fun `recarregar com o mesmo perfil nao busca de novo`() = runTest {
        val perfis = PerfisFalso().apply { abrir = { ResultadoDaChamada.Sucesso(AQUARELA) } }
        val vm = vmPronto(livro = comPerfil, perfis = perfis)

        vm.carregar()
        advanceUntilIdle()

        assertEquals(listOf(7), perfis.abertos)
        assertEquals(AQUARELA, (vm.estado.value as EstadoDoLivro.Pronto).perfil)
    }

    @Test
    fun `abrir a escolha lista os perfis`() = runTest {
        val perfis = PerfisFalso(ResultadoDaChamada.Sucesso(listOf(AQUARELA, MINIMO)))
        val vm = vmPronto(perfis = perfis)

        vm.abrirEscolhaDePerfil()
        advanceUntilIdle()

        assertEquals(EstadoDaEscolhaDePerfil.Lista(listOf(AQUARELA, MINIMO)), vm.escolhaDePerfil.value)
    }

    @Test
    fun `sem perfis criados, a lista vem vazia`() = runTest {
        val vm = vmPronto()

        vm.abrirEscolhaDePerfil()
        advanceUntilIdle()

        assertEquals(EstadoDaEscolhaDePerfil.Lista(emptyList()), vm.escolhaDePerfil.value)
    }

    @Test
    fun `falha ao listar mostra o motivo`() = runTest {
        val perfis = PerfisFalso(ResultadoDaChamada.Falha("Não consegui falar com o servidor."))
        val vm = vmPronto(perfis = perfis)

        vm.abrirEscolhaDePerfil()
        advanceUntilIdle()

        assertEquals(
            EstadoDaEscolhaDePerfil.Falhou("Não consegui falar com o servidor."),
            vm.escolhaDePerfil.value,
        )
    }

    @Test
    fun `escolher um perfil grava na hora, fecha e mostra o nome sem nova busca`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        livros.respostaDoAjuste = { ResultadoDaChamada.Sucesso(base.copy(perfil_renderizacao_padrao_id = 7)) }
        val perfis = PerfisFalso(ResultadoDaChamada.Sucesso(listOf(AQUARELA, MINIMO)))
        val vm = vmPronto(livros = livros, perfis = perfis)
        vm.abrirEscolhaDePerfil()
        advanceUntilIdle()

        vm.escolherPerfil(AQUARELA)
        advanceUntilIdle()

        assertEquals(listOf(LivroAjuste(perfil_renderizacao_padrao_id = 7)), livros.ajustes)
        assertEquals(EstadoDaEscolhaDePerfil.Nenhuma, vm.escolhaDePerfil.value)
        val pronto = vm.estado.value as EstadoDoLivro.Pronto
        assertEquals(AQUARELA, pronto.perfil)
        // O objeto do perfil já era conhecido da lista: não precisa buscá-lo de novo.
        assertTrue(perfis.abertos.isEmpty())
    }

    @Test
    fun `escolher Nenhum manda null explicito para limpar o perfil`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(comPerfil))
        livros.respostaDoAjuste = { ResultadoDaChamada.Sucesso(base) }
        val perfis = PerfisFalso(ResultadoDaChamada.Sucesso(listOf(AQUARELA))).apply {
            abrir = { ResultadoDaChamada.Sucesso(AQUARELA) }
        }
        val vm = vmPronto(livro = comPerfil, livros = livros, perfis = perfis)
        vm.abrirEscolhaDePerfil()
        advanceUntilIdle()

        vm.escolherPerfil(null)
        advanceUntilIdle()

        assertEquals(listOf(LivroAjuste(limparPerfilPadrao = true)), livros.ajustes)
        val pronto = vm.estado.value as EstadoDoLivro.Pronto
        assertNull(pronto.livro.perfil_renderizacao_padrao_id)
        assertNull(pronto.perfil)
    }

    @Test
    fun `escolher o perfil que ja esta escolhido so fecha`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(comPerfil))
        val perfis = PerfisFalso(ResultadoDaChamada.Sucesso(listOf(AQUARELA)))
        val vm = vmPronto(livro = comPerfil, livros = livros, perfis = perfis)
        vm.abrirEscolhaDePerfil()
        advanceUntilIdle()

        vm.escolherPerfil(AQUARELA)
        advanceUntilIdle()

        assertTrue(livros.ajustes.isEmpty())
        assertEquals(EstadoDaEscolhaDePerfil.Nenhuma, vm.escolhaDePerfil.value)
    }

    @Test
    fun `falha ao escolher mantem a lista aberta com o motivo e nao muda o livro`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        livros.respostaDoAjuste = { ResultadoDaChamada.Falha("Não existe perfil de renderização com id 7.") }
        val perfis = PerfisFalso(ResultadoDaChamada.Sucesso(listOf(AQUARELA)))
        val vm = vmPronto(livros = livros, perfis = perfis)
        vm.abrirEscolhaDePerfil()
        advanceUntilIdle()

        vm.escolherPerfil(AQUARELA)
        advanceUntilIdle()

        assertEquals(
            EstadoDaEscolhaDePerfil.Lista(
                listOf(AQUARELA), salvando = false, erro = "Não existe perfil de renderização com id 7.",
            ),
            vm.escolhaDePerfil.value,
        )
        assertNull(vm.livroNaTela().perfil_renderizacao_padrao_id)
    }

    @Test
    fun `fechar a escolha de perfil`() = runTest {
        val vm = vmPronto()
        vm.abrirEscolhaDePerfil()
        advanceUntilIdle()

        vm.fecharEscolhaDePerfil()

        assertEquals(EstadoDaEscolhaDePerfil.Nenhuma, vm.escolhaDePerfil.value)
    }

    // ------------------------------------------------------------------ //
    // Apagar o livro
    // ------------------------------------------------------------------ //

    @Test
    fun `pedir a remocao abre a confirmacao com o resumo do livro`() = runTest {
        val vm = vmPronto()

        vm.pedirRemocao()

        assertEquals(EstadoDaRemocao.Confirmando(base.comoResumo()), vm.remocao.value)
    }

    @Test
    fun `remover apaga no servidor e avisa a tela para voltar`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        val vm = vmPronto(livros = livros)
        val saidas = coletar(vm.livroRemovido)

        vm.pedirRemocao()
        vm.confirmarRemocao()
        advanceUntilIdle()

        assertEquals(listOf(1), livros.removidos)
        assertEquals(1, saidas.size)
        assertEquals(EstadoDaRemocao.Nenhuma, vm.remocao.value)
    }

    @Test
    fun `falha ao remover nao volta e mostra o motivo`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        livros.respostaDaRemocao = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        val vm = vmPronto(livros = livros)
        val saidas = coletar(vm.livroRemovido)

        vm.pedirRemocao()
        vm.confirmarRemocao()
        advanceUntilIdle()

        assertTrue(saidas.isEmpty())
        assertEquals(
            EstadoDaRemocao.Falhou(base.comoResumo(), "Não consegui falar com o servidor."),
            vm.remocao.value,
        )
    }

    @Test
    fun `cancelar a remocao nao apaga nada`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(base))
        val vm = vmPronto(livros = livros)

        vm.pedirRemocao()
        vm.cancelarRemocao()

        assertEquals(EstadoDaRemocao.Nenhuma, vm.remocao.value)
        assertTrue(livros.removidos.isEmpty())
    }
}
