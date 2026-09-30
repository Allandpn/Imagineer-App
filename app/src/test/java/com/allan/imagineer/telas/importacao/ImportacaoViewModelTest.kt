package com.allan.imagineer.telas.importacao

import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.dados.LeitorDeArquivos
import com.allan.imagineer.rede.LivroAjuste
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.RespostaImportacao
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream

// ---------------------------------------------------------------------- //
// Falsos e construtores de dados
// ---------------------------------------------------------------------- //

private const val URI = "content://falso/livro.epub"

private fun arquivo(nome: String = "livro.epub", tamanho: Long? = 1000) =
    ArquivoEscolhido(URI, nome, tamanho)

private fun resumo(id: Int, nome: String = "livro.epub", titulo: String = "Livro $id") = LivroResumo(
    id = id, titulo = titulo, autor = null, idioma = null, nome_arquivo = nome,
    data_importacao = "2026-09-30T01:11:16", total_de_capitulos = 2, capitulos_ignorados = 0,
)

private fun detalhe(id: Int, pendentes: List<String> = emptyList(), nome: String = "livro.epub") = LivroDetalhe(
    id = id, titulo = "Livro $id", autor = null, idioma = null, nome_arquivo = nome,
    data_importacao = "2026-09-30T01:11:16", total_de_capitulos = 2, capitulos_ignorados = 0,
    metadados_pendentes = pendentes,
)

/** Devolve o arquivo combinado para a URI; `null` simula um arquivo que não dá para consultar. */
private class LeitorFalso(var descricao: ArquivoEscolhido? = arquivo()) : LeitorDeArquivos {
    override fun descrever(uri: String): ArquivoEscolhido? = descricao
    override fun abrir(uri: String): InputStream? = null
}

/** Repositório falso: cada resposta é configurável, e cada chamada fica registrada. */
private class RepositorioFalso : RepositorioDeLivros {
    /** O que está no "servidor". `importarLivro` pode acrescentar aqui, como o servidor real. */
    val noServidor = mutableListOf<LivroResumo>()
    var respostaDaLista: ResultadoDaChamada<List<LivroResumo>>? = null

    var respostaDaImportacao: ResultadoDaChamada<RespostaImportacao> =
        ResultadoDaChamada.Sucesso(RespostaImportacao(detalhe(1)))
    var aoImportar: (() -> Unit)? = null
    var progressoASimular: List<Pair<Long, Long?>> = emptyList()
    var travaDaImportacao: CompletableDeferred<Unit>? = null
    var importacoes = 0

    var respostaDoAjuste: ResultadoDaChamada<LivroDetalhe> = ResultadoDaChamada.Sucesso(detalhe(1))
    val ajustes = mutableListOf<LivroAjuste>()

    var respostaDaRemocao: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)
    val removidos = mutableListOf<Int>()

    var respostaDoDetalhe: ResultadoDaChamada<LivroDetalhe>? = null
    val abertos = mutableListOf<Int>()

    override suspend fun listarLivros(): ResultadoDaChamada<List<LivroResumo>> =
        respostaDaLista ?: ResultadoDaChamada.Sucesso(noServidor.toList())

    override suspend fun abrirLivro(livroId: Int): ResultadoDaChamada<LivroDetalhe> {
        abertos += livroId
        return respostaDoDetalhe ?: ResultadoDaChamada.Sucesso(detalhe(livroId))
    }

    override suspend fun importarLivro(
        arquivo: ArquivoEscolhido,
        aoProgredir: (enviados: Long, total: Long?) -> Unit,
    ): ResultadoDaChamada<RespostaImportacao> {
        importacoes++
        progressoASimular.forEach { (enviados, total) -> aoProgredir(enviados, total) }
        travaDaImportacao?.await()
        aoImportar?.invoke()
        return respostaDaImportacao
    }

    override suspend fun ajustarLivro(livroId: Int, ajuste: LivroAjuste): ResultadoDaChamada<LivroDetalhe> {
        ajustes += ajuste
        return respostaDoAjuste
    }

    override suspend fun removerLivro(livroId: Int): ResultadoDaChamada<Unit> {
        removidos += livroId
        return respostaDaRemocao
    }
}

private fun sucesso(livro: LivroDetalhe, semelhantes: List<LivroResumo> = emptyList()) =
    ResultadoDaChamada.Sucesso(RespostaImportacao(livro, semelhantes))

// ---------------------------------------------------------------------- //
// Funções puras
// ---------------------------------------------------------------------- //

class ValidarArquivoTest {

    @Test
    fun `um epub de tamanho normal serve`() {
        assertNull(validarArquivo("livro.epub", 5_000_000))
        assertNull(validarArquivo("LIVRO.EPUB", 5_000_000))
    }

    @Test
    fun `tamanho desconhecido nao impede`() {
        assertNull(validarArquivo("livro.epub", null))
    }

    @Test
    fun `recusa o que nao e epub`() {
        assertEquals("Escolha um arquivo .epub.", validarArquivo("livro.pdf", 1000))
        assertEquals("Escolha um arquivo .epub.", validarArquivo("livro", 1000))
        assertEquals("Escolha um arquivo .epub.", validarArquivo("livro.epub.zip", 1000))
    }

    @Test
    fun `recusa arquivo vazio`() {
        assertEquals("O arquivo está vazio.", validarArquivo("livro.epub", 0))
    }

    @Test
    fun `o limite de 60 MB e inclusivo`() {
        assertNull(validarArquivo("livro.epub", LIMITE_DO_EPUB_EM_BYTES))
        assertEquals(
            "O arquivo passa do limite de 60 MB.",
            validarArquivo("livro.epub", LIMITE_DO_EPUB_EM_BYTES + 1),
        )
    }

    @Test
    fun `fracao enviada`() {
        assertEquals(0.5f, fracaoEnviada(50, 100)!!, 0.0001f)
        assertEquals(1f, fracaoEnviada(100, 100)!!, 0.0001f)
        // Nunca passa de 100%, mesmo que o provedor de arquivos tenha informado um tamanho errado.
        assertEquals(1f, fracaoEnviada(150, 100)!!, 0.0001f)
        assertNull(fracaoEnviada(50, null))
        assertNull(fracaoEnviada(50, 0))
    }

    @Test
    fun `nomes dos campos em portugues`() {
        assertEquals("título e autor", nomesDosCampos(listOf("titulo", "autor")))
        assertEquals("autor", nomesDosCampos(listOf("autor")))
    }
}

// ---------------------------------------------------------------------- //
// O ViewModel
// ---------------------------------------------------------------------- //

@OptIn(ExperimentalCoroutinesApi::class)
class ImportacaoViewModelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun vm(
        repositorio: RepositorioFalso = RepositorioFalso(),
        leitor: LeitorFalso = LeitorFalso(),
    ) = ImportacaoViewModel(repositorio, leitor)

    // --- escolher o arquivo e validar -----------------------------------

    @Test
    fun `cancelar o seletor nao faz nada`() = runTest {
        val repositorio = RepositorioFalso()
        val vm = vm(repositorio)

        vm.escolherArquivo(null)
        advanceUntilIdle()

        assertEquals(EstadoDaImportacao.Nenhuma, vm.estado.value)
        assertEquals(0, repositorio.importacoes)
    }

    @Test
    fun `arquivo que nao da para consultar falha sem oferecer tentar de novo`() = runTest {
        val vm = vm(leitor = LeitorFalso(descricao = null))

        vm.escolherArquivo(URI)

        assertEquals(
            EstadoDaImportacao.Falhou("Não consegui abrir o arquivo escolhido."),
            vm.estado.value,
        )
    }

    @Test
    fun `extensao errada e recusada antes de subir qualquer byte`() = runTest {
        val repositorio = RepositorioFalso()
        val vm = vm(repositorio, LeitorFalso(arquivo(nome = "livro.pdf")))

        vm.escolherArquivo(URI)
        advanceUntilIdle()

        assertEquals(EstadoDaImportacao.Falhou("Escolha um arquivo .epub."), vm.estado.value)
        assertEquals(0, repositorio.importacoes)
    }

    @Test
    fun `arquivo acima de 60 MB e recusado antes de subir`() = runTest {
        val repositorio = RepositorioFalso()
        val vm = vm(repositorio, LeitorFalso(arquivo(tamanho = LIMITE_DO_EPUB_EM_BYTES + 1)))

        vm.escolherArquivo(URI)
        advanceUntilIdle()

        assertEquals(EstadoDaImportacao.Falhou("O arquivo passa do limite de 60 MB."), vm.estado.value)
        assertEquals(0, repositorio.importacoes)
    }

    // --- caminho feliz e progresso --------------------------------------

    @Test
    fun `sem semelhantes e sem pendentes, conclui e manda abrir o livro`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.respostaDaImportacao = sucesso(detalhe(7))
        val vm = vm(repositorio)

        vm.escolherArquivo(URI)
        advanceUntilIdle()

        assertEquals(EstadoDaImportacao.Nenhuma, vm.estado.value)
        assertEquals(7, vm.irParaLivro.value)
        assertEquals(1, vm.versaoDaBiblioteca.value)
    }

    @Test
    fun `consumir a navegacao zera o evento`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.respostaDaImportacao = sucesso(detalhe(7))
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.consumirNavegacao()

        assertNull(vm.irParaLivro.value)
    }

    @Test
    fun `mostra o progresso do envio`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.progressoASimular = listOf(500L to 1000L)
        repositorio.travaDaImportacao = CompletableDeferred()
        val vm = vm(repositorio)

        vm.escolherArquivo(URI)
        advanceUntilIdle()

        assertEquals(EstadoDaImportacao.Enviando("livro.epub", 500, 1000), vm.estado.value)
    }

    // --- livros semelhantes ---------------------------------------------

    @Test
    fun `semelhantes abre o aviso antes de qualquer outra coisa`() = runTest {
        val repositorio = RepositorioFalso()
        val parecidos = listOf(resumo(1, titulo = "Original"))
        repositorio.respostaDaImportacao = sucesso(detalhe(2, pendentes = listOf("autor")), parecidos)
        val vm = vm(repositorio)

        vm.escolherArquivo(URI)
        advanceUntilIdle()

        // Semelhantes vem ANTES do formulário de metadados, mesmo havendo pendência.
        assertEquals(
            EstadoDaImportacao.Semelhantes(detalhe(2, listOf("autor")), parecidos),
            vm.estado.value,
        )
    }

    @Test
    fun `seguir mesmo assim sem pendencias conclui`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.respostaDaImportacao = sucesso(detalhe(2), listOf(resumo(1)))
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.seguirMesmoAssim()

        assertEquals(2, vm.irParaLivro.value)
        assertEquals(EstadoDaImportacao.Nenhuma, vm.estado.value)
    }

    @Test
    fun `seguir mesmo assim com pendencias abre o formulario`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.respostaDaImportacao = sucesso(detalhe(2, listOf("autor")), listOf(resumo(1)))
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.seguirMesmoAssim()

        assertEquals(
            EstadoDaImportacao.MetadadosPendentes(detalhe(2, listOf("autor"))),
            vm.estado.value,
        )
    }

    @Test
    fun `abrir o existente vai para o primeiro parecido e mantem o novo`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.respostaDaImportacao = sucesso(detalhe(2), listOf(resumo(1), resumo(5)))
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.abrirExistente()

        assertEquals(1, vm.irParaLivro.value)
        assertTrue(repositorio.removidos.isEmpty())
    }

    @Test
    fun `remover o novo apaga o livro recem importado e recarrega a biblioteca`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.respostaDaImportacao = sucesso(detalhe(2), listOf(resumo(1)))
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()
        val versaoAntes = vm.versaoDaBiblioteca.value

        vm.removerONovo()
        advanceUntilIdle()

        assertEquals(listOf(2), repositorio.removidos)
        assertEquals(EstadoDaImportacao.Nenhuma, vm.estado.value)
        assertNull(vm.irParaLivro.value)
        assertEquals(versaoAntes + 1, vm.versaoDaBiblioteca.value)
    }

    @Test
    fun `falha ao remover o novo mostra o erro no proprio dialogo`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.respostaDaImportacao = sucesso(detalhe(2), listOf(resumo(1)))
        repositorio.respostaDaRemocao = ResultadoDaChamada.Falha("sem conexão")
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.removerONovo()
        advanceUntilIdle()

        assertEquals(
            EstadoDaImportacao.Semelhantes(detalhe(2), listOf(resumo(1)), removendo = false, erro = "sem conexão"),
            vm.estado.value,
        )
    }

    // --- formulário de metadados ----------------------------------------

    private fun RepositorioFalso.comPendentes(vararg campos: String) {
        respostaDaImportacao = sucesso(detalhe(3, campos.toList()))
    }

    @Test
    fun `pendentes abre o formulario`() = runTest {
        val repositorio = RepositorioFalso().apply { comPendentes("titulo", "autor") }
        val vm = vm(repositorio)

        vm.escolherArquivo(URI)
        advanceUntilIdle()

        assertTrue(vm.estado.value is EstadoDaImportacao.MetadadosPendentes)
        assertNull(vm.irParaLivro.value)
    }

    @Test
    fun `campo vazio nunca e enviado`() = runTest {
        val repositorio = RepositorioFalso().apply { comPendentes("titulo", "autor") }
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.salvarMetadados(titulo = "Um Título", autor = "   ")
        advanceUntilIdle()

        assertTrue(repositorio.ajustes.isEmpty())
        assertEquals(
            "Preencha: autor.",
            (vm.estado.value as EstadoDaImportacao.MetadadosPendentes).erro,
        )
    }

    @Test
    fun `dois campos vazios sao apontados juntos`() = runTest {
        val repositorio = RepositorioFalso().apply { comPendentes("titulo", "autor") }
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.salvarMetadados("", "")

        assertEquals(
            "Preencha: título e autor.",
            (vm.estado.value as EstadoDaImportacao.MetadadosPendentes).erro,
        )
    }

    @Test
    fun `salvar os dois campos manda os dois, aparados, e conclui`() = runTest {
        val repositorio = RepositorioFalso().apply { comPendentes("titulo", "autor") }
        repositorio.respostaDoAjuste = ResultadoDaChamada.Sucesso(detalhe(3))
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.salvarMetadados("  Um Título ", " Fulano  ")
        advanceUntilIdle()

        assertEquals(listOf(LivroAjuste(titulo = "Um Título", autor = "Fulano")), repositorio.ajustes)
        assertEquals(3, vm.irParaLivro.value)
        assertEquals(EstadoDaImportacao.Nenhuma, vm.estado.value)
    }

    @Test
    fun `so o campo pendente e enviado`() = runTest {
        // Só o autor está pendente: o título já foi confirmado e não deve ser tocado.
        val repositorio = RepositorioFalso().apply { comPendentes("autor") }
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.salvarMetadados(titulo = "Qualquer coisa digitada", autor = "Fulano")
        advanceUntilIdle()

        assertEquals(listOf(LivroAjuste(titulo = null, autor = "Fulano")), repositorio.ajustes)
    }

    @Test
    fun `se ainda falta algo depois de salvar, o formulario continua com aviso`() = runTest {
        val repositorio = RepositorioFalso().apply { comPendentes("titulo", "autor") }
        repositorio.respostaDoAjuste = ResultadoDaChamada.Sucesso(detalhe(3, listOf("autor")))
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.salvarMetadados("Título", "Autor")
        advanceUntilIdle()

        val estado = vm.estado.value as EstadoDaImportacao.MetadadosPendentes
        assertEquals("Ainda falta: autor.", estado.erro)
        assertNull(vm.irParaLivro.value)
    }

    @Test
    fun `falha ao salvar mostra o motivo e libera os botoes`() = runTest {
        val repositorio = RepositorioFalso().apply { comPendentes("autor") }
        repositorio.respostaDoAjuste = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.salvarMetadados("", "Fulano")
        advanceUntilIdle()

        val estado = vm.estado.value as EstadoDaImportacao.MetadadosPendentes
        assertEquals("Não consegui falar com o servidor.", estado.erro)
        assertEquals(false, estado.salvando)
    }

    @Test
    fun `remover livro no formulario evita ficar preso`() = runTest {
        val repositorio = RepositorioFalso().apply { comPendentes("titulo") }
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.removerLivroDoFormulario()
        advanceUntilIdle()

        assertEquals(listOf(3), repositorio.removidos)
        assertEquals(EstadoDaImportacao.Nenhuma, vm.estado.value)
        assertNull(vm.irParaLivro.value)
    }

    // --- falha e tentar de novo -----------------------------------------

    @Test
    fun `falha na importacao guarda o arquivo para tentar de novo`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.respostaDaImportacao = ResultadoDaChamada.Falha("O arquivo passa do limite de 60 MB.")
        val vm = vm(repositorio)

        vm.escolherArquivo(URI)
        advanceUntilIdle()

        assertEquals(
            EstadoDaImportacao.Falhou("O arquivo passa do limite de 60 MB.", arquivo()),
            vm.estado.value,
        )
    }

    @Test
    fun `fechar a falha volta ao estado inicial`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.respostaDaImportacao = ResultadoDaChamada.Falha("erro")
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.fechar()

        assertEquals(EstadoDaImportacao.Nenhuma, vm.estado.value)
    }

    @Test
    fun `tentar de novo reenvia quando o servidor nao gravou nada`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.respostaDaImportacao = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        repositorio.respostaDaImportacao = sucesso(detalhe(9))
        vm.tentarDeNovo()
        advanceUntilIdle()

        assertEquals(2, repositorio.importacoes)
        assertEquals(9, vm.irParaLivro.value)
    }

    @Test
    fun `se a rede caiu mas o servidor gravou, nao reenvia e segue do livro gravado`() = runTest {
        val repositorio = RepositorioFalso()
        // O servidor grava o livro, mas a resposta se perde: o app só vê a falha.
        repositorio.aoImportar = { repositorio.noServidor += resumo(4, nome = "livro.epub") }
        repositorio.respostaDaImportacao = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        vm.tentarDeNovo()
        advanceUntilIdle()

        // Um único envio: a segunda tentativa achou o livro e não duplicou.
        assertEquals(1, repositorio.importacoes)
        assertEquals(listOf(4), repositorio.abertos)
        assertEquals(4, vm.irParaLivro.value)
    }

    @Test
    fun `livro de mesmo nome que ja existia antes nao conta como o recem gravado`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.noServidor += resumo(1, nome = "livro.epub") // já existia antes do envio
        repositorio.respostaDaImportacao = ResultadoDaChamada.Falha("erro")
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        repositorio.respostaDaImportacao = sucesso(detalhe(2))
        vm.tentarDeNovo()
        advanceUntilIdle()

        // O id 1 estava no retrato de antes: reenviou, e o livro novo é o 2.
        assertEquals(2, repositorio.importacoes)
        assertTrue(repositorio.abertos.isEmpty())
        assertEquals(2, vm.irParaLivro.value)
    }

    @Test
    fun `sem o retrato inicial nao ha como conferir, entao reenvia`() = runTest {
        val repositorio = RepositorioFalso()
        // O servidor estava fora do ar quando o retrato foi tirado.
        repositorio.respostaDaLista = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        repositorio.respostaDaImportacao = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        repositorio.respostaDaImportacao = sucesso(detalhe(9))
        vm.tentarDeNovo()
        advanceUntilIdle()

        assertEquals(2, repositorio.importacoes)
    }

    @Test
    fun `gravou mas nao deu para abrir o livro, falha em vez de duplicar`() = runTest {
        val repositorio = RepositorioFalso()
        repositorio.aoImportar = { repositorio.noServidor += resumo(4, nome = "livro.epub") }
        repositorio.respostaDaImportacao = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        val vm = vm(repositorio)
        vm.escolherArquivo(URI)
        advanceUntilIdle()

        repositorio.respostaDoDetalhe = ResultadoDaChamada.Falha("o servidor oscilou")
        vm.tentarDeNovo()
        advanceUntilIdle()

        assertEquals(1, repositorio.importacoes)
        assertEquals(
            EstadoDaImportacao.Falhou("o servidor oscilou", arquivo()),
            vm.estado.value,
        )
    }

    @Test
    fun `tentar de novo sem falha anterior nao faz nada`() = runTest {
        val repositorio = RepositorioFalso()
        val vm = vm(repositorio)

        vm.tentarDeNovo()
        advanceUntilIdle()

        assertEquals(0, repositorio.importacoes)
    }
}
