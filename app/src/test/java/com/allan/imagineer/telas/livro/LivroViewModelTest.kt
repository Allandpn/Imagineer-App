package com.allan.imagineer.telas.livro

import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.rede.CapituloAjuste
import com.allan.imagineer.rede.CapituloDetalhe
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.LivroAjuste
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.PerfilRenderizacao
import com.allan.imagineer.rede.RepositorioDeCapitulos
import com.allan.imagineer.rede.RepositorioDePerfis
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.RespostaImportacao
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.util.Locale

// ---------------------------------------------------------------------- //
// Falsos e construtores de dados
// ---------------------------------------------------------------------- //

internal fun capitulo(id: Int, ignorado: Boolean = false, titulo: String? = "Cap $id") = CapituloResumo(
    id = id, ordem = id, titulo = titulo, ignorado = ignorado, tamanho_do_texto = 1000,
)

internal fun livro(vararg capitulos: CapituloResumo) = LivroDetalhe(
    id = 1, titulo = "Um Livro", autor = "Fulano", idioma = "pt-BR", nome_arquivo = "livro.epub",
    data_importacao = "2026-09-30T01:11:16", total_de_capitulos = capitulos.size,
    capitulos_ignorados = capitulos.count { it.ignorado }, capitulos = capitulos.toList(),
)

internal class LivrosFalso(var resposta: ResultadoDaChamada<LivroDetalhe>) : RepositorioDeLivros {
    var trava: CompletableDeferred<Unit>? = null
    var chamadas = 0

    /** O que `ajustarLivro` devolve (por padrão, o próprio livro), e o que foi pedido. */
    var respostaDoAjuste: ((LivroAjuste) -> ResultadoDaChamada<LivroDetalhe>)? = null
    val ajustes = mutableListOf<LivroAjuste>()

    var respostaDaRemocao: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)
    val removidos = mutableListOf<Int>()

    override suspend fun abrirLivro(livroId: Int): ResultadoDaChamada<LivroDetalhe> {
        chamadas++
        val respostaDestaChamada = resposta // a resposta vale no momento da chamada, não da entrega
        trava?.await()
        return respostaDestaChamada
    }

    // O resto não é usado pela tela de Livro; o falso só cumpre a interface.
    override suspend fun listarLivros(): ResultadoDaChamada<List<LivroResumo>> = error("não usado")
    override suspend fun importarLivro(
        arquivo: ArquivoEscolhido,
        aoProgredir: (enviados: Long, total: Long?) -> Unit,
    ): ResultadoDaChamada<RespostaImportacao> = error("não usado")
    override suspend fun ajustarLivro(livroId: Int, ajuste: LivroAjuste): ResultadoDaChamada<LivroDetalhe> {
        ajustes += ajuste
        return respostaDoAjuste?.invoke(ajuste) ?: resposta
    }

    override suspend fun removerLivro(livroId: Int): ResultadoDaChamada<Unit> {
        removidos += livroId
        return respostaDaRemocao
    }
}

/** Perfis falsos: a lista, e o que `abrirPerfil` devolve por id. */
internal class PerfisFalso(
    var lista: ResultadoDaChamada<List<PerfilRenderizacao>> = ResultadoDaChamada.Sucesso(emptyList()),
) : RepositorioDePerfis {
    var abrir: (Int) -> ResultadoDaChamada<PerfilRenderizacao> = { id ->
        ResultadoDaChamada.Sucesso(PerfilRenderizacao(id = id, nome = "Perfil $id"))
    }
    val abertos = mutableListOf<Int>()

    override suspend fun listarPerfis() = lista

    override suspend fun abrirPerfil(perfilId: Int): ResultadoDaChamada<PerfilRenderizacao> {
        abertos += perfilId
        return abrir(perfilId)
    }
}

internal class CapitulosFalso : RepositorioDeCapitulos {
    val ajustes = mutableListOf<Pair<Int, CapituloAjuste>>()
    var trava: CompletableDeferred<Unit>? = null

    // A tela de Livro não abre o texto do capítulo; o falso só cumpre a interface.
    override suspend fun abrirCapitulo(capituloId: Int): ResultadoDaChamada<CapituloDetalhe> =
        error("não usado pela tela de Livro")

    /** Por padrão o servidor "obedece": devolve o capítulo com o `ignorado` pedido. */
    var resposta: (Int, CapituloAjuste) -> ResultadoDaChamada<CapituloResumo> = { id, ajuste ->
        ResultadoDaChamada.Sucesso(capitulo(id, ignorado = ajuste.ignorado == true))
    }

    override suspend fun ajustarCapitulo(
        capituloId: Int,
        ajuste: CapituloAjuste,
    ): ResultadoDaChamada<CapituloResumo> {
        ajustes += capituloId to ajuste
        trava?.await()
        return resposta(capituloId, ajuste)
    }
}

// ---------------------------------------------------------------------- //
// Funções puras
// ---------------------------------------------------------------------- //

class FormatacaoDoLivroTest {

    private val br = Locale("pt", "BR")

    @Test
    fun `titulo do capitulo usa o titulo quando existe`() {
        assertEquals("Bran", tituloDoCapitulo("Bran", 1))
    }

    @Test
    fun `capitulo sem titulo mostra Capitulo e a ordem`() {
        assertEquals("Capítulo 7", tituloDoCapitulo(null, 7))
        assertEquals("Capítulo 7", tituloDoCapitulo("", 7))
        assertEquals("Capítulo 7", tituloDoCapitulo("   ", 7))
    }

    @Test
    fun `sem sugestoes pendentes nao mostra nada`() {
        assertNull(descreverSugestoes(0))
        // Dado inconsistente do servidor não vira texto estranho na tela.
        assertNull(descreverSugestoes(-1))
    }

    @Test
    fun `sugestoes pendentes no singular e no plural, com rotulo`() {
        assertEquals("1 sugestão a confirmar", descreverSugestoes(1))
        assertEquals("13 sugestões a confirmar", descreverSugestoes(13))
    }

    @Test
    fun `tamanho pequeno em caracteres`() {
        assertEquals("1 caractere", descreverTamanho(1, br))
        assertEquals("850 caracteres", descreverTamanho(850, br))
        assertEquals("999 caracteres", descreverTamanho(999, br))
    }

    @Test
    fun `tamanho medio com uma casa decimal e virgula`() {
        assertEquals("3,4 mil caracteres", descreverTamanho(3400, br))
        assertEquals("27,8 mil caracteres", descreverTamanho(27_830, br))
    }

    @Test
    fun `mil redondo nao mostra a casa decimal`() {
        assertEquals("1 mil caracteres", descreverTamanho(1000, br))
        assertEquals("5 mil caracteres", descreverTamanho(5000, br))
    }

    @Test
    fun `acima de cem mil a casa decimal e ruido`() {
        assertEquals("112 mil caracteres", descreverTamanho(112_400, br))
    }

    @Test
    fun `comCapituloAtualizado troca o capitulo e recalcula os ignorados`() {
        val antes = livro(capitulo(1), capitulo(2, ignorado = true), capitulo(3))

        val depois = antes.comCapituloAtualizado(capitulo(3, ignorado = true))

        assertEquals(listOf(false, true, true), depois.capitulos.map { it.ignorado })
        assertEquals(2, depois.capitulos_ignorados)
        // O que não foi tocado continua igual.
        assertEquals(antes.capitulos[0], depois.capitulos[0])
        assertEquals(3, depois.total_de_capitulos)
    }
}

// ---------------------------------------------------------------------- //
// O ViewModel
// ---------------------------------------------------------------------- //

@OptIn(ExperimentalCoroutinesApi::class)
class LivroViewModelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private val doisCapitulos = livro(capitulo(1), capitulo(2))

    /** Junta os avisos que o ViewModel emitir, numa lista, para o teste conferir. */
    private fun TestScope.coletarAvisos(vm: LivroViewModel): List<Aviso> {
        val recebidos = mutableListOf<Aviso>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.avisos.toList(recebidos) }
        return recebidos
    }

    private fun vm(
        livros: LivrosFalso = LivrosFalso(ResultadoDaChamada.Sucesso(doisCapitulos)),
        capitulos: CapitulosFalso = CapitulosFalso(),
    ) = LivroViewModel(1, livros, capitulos, PerfisFalso())

    @Test
    fun `comeca carregando`() = runTest {
        assertEquals(EstadoDoLivro.Carregando, vm().estado.value)
    }

    @Test
    fun `carregar mostra o livro`() = runTest {
        val vm = vm()

        vm.carregar()
        advanceUntilIdle()

        assertEquals(EstadoDoLivro.Pronto(doisCapitulos), vm.estado.value)
    }

    @Test
    fun `falha ao carregar mostra o motivo e tentar de novo recupera`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Falha("Não existe livro com id 1.", codigoHttp = 404))
        val vm = vm(livros)
        vm.carregar()
        advanceUntilIdle()
        assertEquals(EstadoDoLivro.Erro("Não existe livro com id 1."), vm.estado.value)

        livros.resposta = ResultadoDaChamada.Sucesso(doisCapitulos)
        vm.carregar()
        advanceUntilIdle()

        assertEquals(EstadoDoLivro.Pronto(doisCapitulos), vm.estado.value)
    }

    @Test
    fun `recarregar com o livro na tela nao volta ao carregando`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(doisCapitulos))
        val vm = vm(livros)
        vm.carregar()
        advanceUntilIdle()

        livros.trava = CompletableDeferred()
        vm.carregar()
        advanceUntilIdle()

        // Silenciosa: o livro antigo continua na tela enquanto a busca está no ar.
        assertEquals(EstadoDoLivro.Pronto(doisCapitulos), vm.estado.value)
    }

    @Test
    fun `recarga que falha com o livro na tela mantem o livro e avisa`() = runTest {
        // Ao voltar de um capítulo a tela recarrega; se o servidor sumiu nesse instante,
        // o livro que já estava na tela continua valendo (item 7.5a, recarga que falha).
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(doisCapitulos))
        val vm = vm(livros)
        val recebidos = coletarAvisos(vm)
        vm.carregar()
        advanceUntilIdle()

        livros.resposta = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        vm.carregar()
        advanceUntilIdle()

        assertEquals(EstadoDoLivro.Pronto(doisCapitulos), vm.estado.value)
        assertEquals(listOf(Aviso("Não consegui atualizar o livro.")), recebidos)
    }

    @Test
    fun `recarga que recebe 404 cai em Erro, porque o livro nao existe mais`() = runTest {
        // Apagado por outro caminho (outro aparelho, o /docs): não há livro a preservar.
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(doisCapitulos))
        val vm = vm(livros)
        val recebidos = coletarAvisos(vm)
        vm.carregar()
        advanceUntilIdle()

        livros.resposta = ResultadoDaChamada.Falha("Não existe livro com id 1.", codigoHttp = 404)
        vm.carregar()
        advanceUntilIdle()

        assertEquals(EstadoDoLivro.Erro("Não existe livro com id 1."), vm.estado.value)
        assertTrue(recebidos.isEmpty())
    }

    @Test
    fun `recarga que recebe erro 500 continua preservando o livro`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(doisCapitulos))
        val vm = vm(livros)
        vm.carregar()
        advanceUntilIdle()

        livros.resposta = ResultadoDaChamada.Falha("O servidor respondeu com erro 500.", codigoHttp = 500)
        vm.carregar()
        advanceUntilIdle()

        assertEquals(EstadoDoLivro.Pronto(doisCapitulos), vm.estado.value)
    }

    @Test
    fun `recarga que falha nao desfaz a selecao em andamento`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(doisCapitulos))
        val vm = vm(livros)
        vm.carregar()
        advanceUntilIdle()
        vm.iniciarSelecao(ModoDeSelecao.ARQUIVAR, idInicial = 1)

        livros.resposta = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")
        vm.carregar()
        advanceUntilIdle()

        assertEquals(Selecao(ModoDeSelecao.ARQUIVAR, setOf(1)), vm.selecao.value)
    }

    @Test
    fun `falha na primeira abertura continua indo para o erro, sem aviso`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Falha("Não consegui falar com o servidor."))
        val vm = vm(livros)
        val recebidos = coletarAvisos(vm)

        vm.carregar()
        advanceUntilIdle()

        assertEquals(EstadoDoLivro.Erro("Não consegui falar com o servidor."), vm.estado.value)
        assertTrue(recebidos.isEmpty())
    }

    // --- arquivar e restaurar ----------------------------------------------

    @Test
    fun `arquivar manda ignorado verdadeiro e atualiza a contagem`() = runTest {
        val capitulos = CapitulosFalso()
        val vm = vm(capitulos = capitulos)
        vm.carregar()
        advanceUntilIdle()

        vm.arquivar(2)
        advanceUntilIdle()

        assertEquals(listOf(2 to CapituloAjuste(ignorado = true)), capitulos.ajustes)
        val pronto = vm.estado.value as EstadoDoLivro.Pronto
        assertEquals(listOf(false, true), pronto.livro.capitulos.map { it.ignorado })
        assertEquals(1, pronto.livro.capitulos_ignorados)
        assertTrue(pronto.ajustando.isEmpty())
    }

    @Test
    fun `restaurar um capitulo arquivado manda ignorado falso`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(livro(capitulo(1, ignorado = true), capitulo(2))))
        val capitulos = CapitulosFalso()
        val vm = vm(livros, capitulos)
        vm.carregar()
        advanceUntilIdle()

        vm.restaurar(1)
        advanceUntilIdle()

        assertEquals(listOf(1 to CapituloAjuste(ignorado = false)), capitulos.ajustes)
        assertEquals(0, (vm.estado.value as EstadoDoLivro.Pronto).livro.capitulos_ignorados)
    }

    @Test
    fun `nao e otimista, o capitulo so troca de lista quando o servidor confirma`() = runTest {
        val capitulos = CapitulosFalso().apply { trava = CompletableDeferred() }
        val vm = vm(capitulos = capitulos)
        vm.carregar()
        advanceUntilIdle()

        vm.arquivar(2)
        advanceUntilIdle()

        val pronto = vm.estado.value as EstadoDoLivro.Pronto
        // Na tela ainda está como antes, e o interruptor daquela linha está bloqueado.
        assertEquals(false, pronto.livro.capitulos[1].ignorado)
        assertEquals(setOf(2), pronto.ajustando)
    }

    @Test
    fun `duplo toque nao dispara duas chamadas`() = runTest {
        val capitulos = CapitulosFalso().apply { trava = CompletableDeferred() }
        val vm = vm(capitulos = capitulos)
        vm.carregar()
        advanceUntilIdle()

        vm.arquivar(2)
        vm.arquivar(2)
        advanceUntilIdle()

        assertEquals(1, capitulos.ajustes.size)
    }

    @Test
    fun `capitulos diferentes podem ser ajustados ao mesmo tempo`() = runTest {
        val capitulos = CapitulosFalso().apply { trava = CompletableDeferred() }
        val vm = vm(capitulos = capitulos)
        vm.carregar()
        advanceUntilIdle()

        vm.arquivar(1)
        vm.arquivar(2)
        advanceUntilIdle()

        assertEquals(2, capitulos.ajustes.size)
        assertEquals(setOf(1, 2), (vm.estado.value as EstadoDoLivro.Pronto).ajustando)
    }

    @Test
    fun `falha ao arquivar mantem o estado, libera a linha e avisa`() = runTest {
        val capitulos = CapitulosFalso().apply {
            resposta = { _, _ -> ResultadoDaChamada.Falha("Não consegui falar com o servidor.") }
        }
        val vm = vm(capitulos = capitulos)
        val recebidos = coletarAvisos(vm)
        vm.carregar()
        advanceUntilIdle()

        vm.arquivar(2)
        advanceUntilIdle()

        val pronto = vm.estado.value as EstadoDoLivro.Pronto
        assertEquals(false, pronto.livro.capitulos[1].ignorado) // ficou onde estava
        assertTrue(pronto.ajustando.isEmpty())
        assertEquals(listOf(Aviso("Não consegui falar com o servidor.")), recebidos)
    }

    @Test
    fun `cada falha gera um aviso, mesmo com a mesma mensagem`() = runTest {
        // Regressão: com o servidor fora do ar toda falha tem a mesma mensagem, e o
        // aviso, sendo um estado, não era emitido de novo — os toques seguintes ao
        // primeiro ficavam sem nenhum retorno.
        val capitulos = CapitulosFalso().apply {
            resposta = { _, _ -> ResultadoDaChamada.Falha("Não consegui falar com o servidor.") }
        }
        val vm = vm(capitulos = capitulos)
        val recebidos = coletarAvisos(vm)
        vm.carregar()
        advanceUntilIdle()

        vm.arquivar(2)
        advanceUntilIdle()
        vm.arquivar(2)
        advanceUntilIdle()
        vm.arquivar(2)
        advanceUntilIdle()

        assertEquals(
            List(3) { Aviso("Não consegui falar com o servidor.") },
            recebidos,
        )
    }

    @Test
    fun `arquivar com sucesso avisa qual capitulo foi, com Desfazer`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(livro(capitulo(1, titulo = "Bran"), capitulo(2))))
        val vm = vm(livros)
        val recebidos = coletarAvisos(vm)
        vm.carregar()
        advanceUntilIdle()

        vm.arquivar(1)
        advanceUntilIdle()

        assertEquals(listOf(Aviso("Arquivado: Bran", desfazerCapitulos = listOf(1))), recebidos)
    }

    @Test
    fun `o aviso de arquivado usa Capitulo N quando nao ha titulo`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(livro(capitulo(1, titulo = null), capitulo(2))))
        val vm = vm(livros)
        val recebidos = coletarAvisos(vm)
        vm.carregar()
        advanceUntilIdle()

        vm.arquivar(1)
        advanceUntilIdle()

        assertEquals("Arquivado: Capítulo 1", recebidos.single().texto)
    }

    @Test
    fun `restaurar com sucesso nao avisa nada`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(livro(capitulo(1, ignorado = true), capitulo(2))))
        val vm = vm(livros)
        val recebidos = coletarAvisos(vm)
        vm.carregar()
        advanceUntilIdle()

        vm.restaurar(1)
        advanceUntilIdle()

        assertTrue(recebidos.isEmpty())
    }

    @Test
    fun `falha ao arquivar avisa o erro, sem Desfazer`() = runTest {
        val capitulos = CapitulosFalso().apply {
            resposta = { _, _ -> ResultadoDaChamada.Falha("Não consegui falar com o servidor.") }
        }
        val vm = vm(capitulos = capitulos)
        val recebidos = coletarAvisos(vm)
        vm.carregar()
        advanceUntilIdle()

        vm.arquivar(1)
        advanceUntilIdle()

        assertEquals(listOf(Aviso("Não consegui falar com o servidor.")), recebidos)
    }

    @Test
    fun `desfazer e restaurar o capitulo do aviso, pela mesma rota`() = runTest {
        // A tela chama restaurar(aviso.desfazerCapitulo) ao tocar em Desfazer.
        val capitulos = CapitulosFalso()
        val vm = vm(capitulos = capitulos)
        val recebidos = coletarAvisos(vm)
        vm.carregar()
        advanceUntilIdle()
        vm.arquivar(2)
        advanceUntilIdle()

        vm.desfazerArquivamento(recebidos.single().desfazerCapitulos)
        advanceUntilIdle()

        assertEquals(
            listOf(2 to CapituloAjuste(ignorado = true), 2 to CapituloAjuste(ignorado = false)),
            capitulos.ajustes,
        )
        val pronto = vm.estado.value as EstadoDoLivro.Pronto
        assertEquals(false, pronto.livro.capitulos[1].ignorado)
        assertEquals(0, pronto.livro.capitulos_ignorados)
    }

    @Test
    fun `arquivar sem o livro na tela ou com capitulo inexistente nao faz nada`() = runTest {
        val capitulos = CapitulosFalso()
        val vm = vm(capitulos = capitulos)

        vm.arquivar(1) // ainda carregando
        vm.carregar()
        advanceUntilIdle()
        vm.arquivar(99) // não existe

        assertTrue(capitulos.ajustes.isEmpty())
    }

    @Test
    fun `uma recarga que estava no ar nao desfaz o que o usuario acabou de arquivar`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(doisCapitulos))
        val vm = vm(livros)
        vm.carregar()
        advanceUntilIdle()

        // Uma recarga começa (e "vê" o capítulo 2 ainda catalogado)...
        livros.trava = CompletableDeferred()
        vm.carregar()
        advanceUntilIdle()
        // ...o usuário ignora o capítulo 2 e o servidor confirma...
        vm.arquivar(2)
        advanceUntilIdle()
        // ...e só então a resposta velha da recarga chega.
        livros.trava!!.complete(Unit)
        advanceUntilIdle()

        val pronto = vm.estado.value as EstadoDoLivro.Pronto
        assertEquals(true, pronto.livro.capitulos[1].ignorado)
    }

    @Test
    fun `arquivar o que ja esta arquivado nao chama o servidor`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(livro(capitulo(1, ignorado = true), capitulo(2))))
        val capitulos = CapitulosFalso()
        val vm = vm(livros, capitulos)
        vm.carregar()
        advanceUntilIdle()

        vm.arquivar(1)
        advanceUntilIdle()

        assertTrue(capitulos.ajustes.isEmpty())
    }

    @Test
    fun `restaurar o que ja esta ativo nao chama o servidor`() = runTest {
        val capitulos = CapitulosFalso()
        val vm = vm(capitulos = capitulos)
        vm.carregar()
        advanceUntilIdle()

        vm.restaurar(1)
        advanceUntilIdle()

        assertTrue(capitulos.ajustes.isEmpty())
    }

    @Test
    fun `restaurar tira o capitulo dos arquivados e a contagem acompanha`() = runTest {
        val livros = LivrosFalso(ResultadoDaChamada.Sucesso(livro(capitulo(1, ignorado = true), capitulo(2, ignorado = true), capitulo(3))))
        val vm = vm(livros)
        vm.carregar()
        advanceUntilIdle()

        vm.restaurar(2)
        advanceUntilIdle()

        val pronto = vm.estado.value as EstadoDoLivro.Pronto
        assertEquals(listOf(1), pronto.livro.capitulos.filter { it.ignorado }.map { it.id })
        assertEquals(1, pronto.livro.capitulos_ignorados)
    }
}
