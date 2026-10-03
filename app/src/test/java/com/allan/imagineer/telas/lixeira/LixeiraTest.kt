package com.allan.imagineer.telas.lixeira

import com.allan.imagineer.rede.ImagemNaLixeira
import com.allan.imagineer.rede.Lixeira
import com.allan.imagineer.rede.LixeiraEsvaziada
import com.allan.imagineer.rede.RepositorioDaLixeira
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun imagem(
    id: Int,
    tipo: String = "PERSONAGEM",
    elemento: String? = "Foxen",
    modelo: String? = null,
    bytes: Long? = 2048,
    capitulo: String? = "Capítulo VI",
) = ImagemNaLixeira(
    id = id, frame_titulo = "Retrato de Foxen", frame_tipo = tipo, nome_do_elemento = elemento, titulo_do_capitulo = capitulo,
    ordem_do_capitulo = 6, titulo_do_livro = "O Livro", modelo = modelo, tamanho_em_bytes = bytes, apagada_em = "2026-10-03T14:05:00Z",
)

/** O que uma lixeira de mentira devolve e registra. */
private class LixeiraFalsa(var conteudo: Lixeira = Lixeira()) : RepositorioDaLixeira {
    var leituras = 0
    val restauradas = mutableListOf<Int>()
    val apagadasDeVez = mutableListOf<Int>()
    var esvaziadas = 0
    var respostaDaLeitura: ResultadoDaChamada<Lixeira>? = null
    var respostaDaAcao: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)

    override suspend fun listar(): ResultadoDaChamada<Lixeira> {
        leituras++
        return respostaDaLeitura ?: ResultadoDaChamada.Sucesso(conteudo)
    }

    override suspend fun restaurar(imagemId: Int): ResultadoDaChamada<Unit> {
        restauradas += imagemId
        if (respostaDaAcao is ResultadoDaChamada.Sucesso) conteudo = conteudo.copy(imagens = conteudo.imagens.filter { it.id != imagemId })
        return respostaDaAcao
    }

    override suspend fun apagarDeVez(imagemId: Int): ResultadoDaChamada<Unit> {
        apagadasDeVez += imagemId
        if (respostaDaAcao is ResultadoDaChamada.Sucesso) conteudo = conteudo.copy(imagens = conteudo.imagens.filter { it.id != imagemId })
        return respostaDaAcao
    }

    override suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada> {
        esvaziadas++
        val antes = conteudo
        conteudo = Lixeira()
        return ResultadoDaChamada.Sucesso(LixeiraEsvaziada(antes.imagens.size, antes.total_em_bytes))
    }
}

/** As regras puras da Lixeira (LX8). */
class RegrasDaLixeiraTest {

    @Test
    fun `o tamanho em texto usa a unidade certa e virgula decimal`() {
        assertEquals("0 B", descreverTamanho(0))
        assertEquals("512 B", descreverTamanho(512))
        assertEquals("1,5 KB", descreverTamanho(1536))
        assertEquals("3,0 MB", descreverTamanho(3L * 1024 * 1024))
        assertEquals("2,5 GB", descreverTamanho((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `o nome do frame diz retrato de quem, ou o titulo da cena`() {
        assertEquals("Retrato de Foxen", nomeDoFrameNaLixeira(imagem(1)))
        assertEquals("No pátio", nomeDoFrameNaLixeira(imagem(1, tipo = "CENA", elemento = null).copy(frame_titulo = "No pátio")))
    }

    @Test
    fun `a origem junta o livro e o capitulo, com o numero se nao ha titulo`() {
        assertEquals("O Livro · Capítulo VI", origemDaImagemNaLixeira(imagem(1)))
        assertEquals("O Livro · Capítulo 6", origemDaImagemNaLixeira(imagem(1, capitulo = null)))
    }

    @Test
    fun `a data vem em dia mes ano, e o que nao e data volta como veio`() {
        assertEquals("03/10/2026", dataDaLixeira("2026-10-03T14:05:00Z"))
        assertEquals("sem data", dataDaLixeira("sem data"))
    }

    @Test
    fun `a legenda tem o frame, a origem, o modelo se gerada, a data e o tamanho`() {
        val legenda = legendaDaLixeira(imagem(1, modelo = "replicate:seedream", bytes = 1536))

        assertTrue(legenda.contains("Retrato de Foxen"))
        assertTrue(legenda.contains("O Livro · Capítulo VI"))
        assertTrue(legenda.contains("replicate:seedream"))
        assertTrue(legenda.contains("Apagada em 03/10/2026 · 1,5 KB"))
        assertFalse("importada não tem modelo", legendaDaLixeira(imagem(1)).contains("replicate"))
    }

    @Test
    fun `os avisos dizem quantas imagens e quanto espaco vao embora`() {
        assertEquals("1 imagem (2,0 KB) serão apagadas de vez, inclusive os arquivos no servidor. Não tem volta.", avisoDeEsvaziar(1, 2048))
        assertTrue(avisoDeEsvaziar(3, 3L * 1024 * 1024).startsWith("3 imagens (3,0 MB)"))
        assertEquals("2 imagens apagadas de vez; 4,0 KB liberados.", recadoDeEsvaziada(2, 4096))
        assertEquals("1 imagem apagada de vez; 1,0 KB liberados.", recadoDeEsvaziada(1, 1024))
        assertEquals("2 imagens · 4,0 KB", resumoDaLixeira(2, 4096))
    }
}

/** O ViewModel da Lixeira (LX8). */
@OptIn(ExperimentalCoroutinesApi::class)
class LixeiraViewModelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun dois() = Lixeira(imagens = listOf(imagem(2), imagem(1)), total_em_bytes = 4096)

    private fun pronta(vm: LixeiraViewModel) = (vm.estado.value.carga as CargaDaLixeira.Pronta).lixeira

    @Test
    fun `carregar mostra o que a lixeira guarda`() = runTest {
        val falsa = LixeiraFalsa(dois())
        val vm = LixeiraViewModel(falsa)

        vm.carregar(); advanceUntilIdle()

        assertEquals(listOf(2, 1), pronta(vm).imagens.map { it.id })
    }

    @Test
    fun `uma leitura que falha na primeira vez vira erro, e depois dela nao apaga o que ja estava na tela`() = runTest {
        val falsa = LixeiraFalsa(dois()).also { it.respostaDaLeitura = ResultadoDaChamada.Falha("fora do ar") }
        val vm = LixeiraViewModel(falsa)
        vm.carregar(); advanceUntilIdle()
        assertEquals(CargaDaLixeira.Erro("fora do ar"), vm.estado.value.carga)

        falsa.respostaDaLeitura = null
        vm.tentarDeNovo(); advanceUntilIdle()
        assertEquals(2, pronta(vm).imagens.size)

        falsa.respostaDaLeitura = ResultadoDaChamada.Falha("fora do ar de novo")
        vm.carregar(); advanceUntilIdle()
        assertEquals("a lista fica", 2, pronta(vm).imagens.size)
        assertEquals("fora do ar de novo", vm.estado.value.recado)
        assertTrue(vm.estado.value.recadoEhErro)
    }

    @Test
    fun `restaurar tira a imagem da lista e avisa`() = runTest {
        val falsa = LixeiraFalsa(dois())
        val vm = LixeiraViewModel(falsa)
        vm.carregar(); advanceUntilIdle()

        vm.restaurar(2); advanceUntilIdle()

        assertEquals(listOf(2), falsa.restauradas)
        assertEquals(listOf(1), pronta(vm).imagens.map { it.id })
        assertEquals("Imagem restaurada.", vm.estado.value.recado)
        assertFalse(vm.estado.value.recadoEhErro)
        assertTrue(vm.estado.value.ocupadas.isEmpty())
    }

    @Test
    fun `apagar de vez so acontece depois de confirmar, e cancelar nao apaga`() = runTest {
        val falsa = LixeiraFalsa(dois())
        val vm = LixeiraViewModel(falsa)
        vm.carregar(); advanceUntilIdle()

        vm.pedirApagarDeVez(imagem(1))
        assertEquals(ConfirmacaoDaLixeira.ApagarUma(imagem(1)), vm.estado.value.confirmacao)
        assertTrue("nada foi ao servidor", falsa.apagadasDeVez.isEmpty())

        vm.cancelarConfirmacao(); advanceUntilIdle()
        assertNull(vm.estado.value.confirmacao)
        assertTrue(falsa.apagadasDeVez.isEmpty())

        vm.pedirApagarDeVez(imagem(1)); vm.confirmar(); advanceUntilIdle()
        assertEquals(listOf(1), falsa.apagadasDeVez)
        assertEquals(listOf(2), pronta(vm).imagens.map { it.id })
        assertEquals("Imagem apagada de vez.", vm.estado.value.recado)
    }

    @Test
    fun `esvaziar pede confirmacao dizendo quantas e quanto, e so depois esvazia`() = runTest {
        val falsa = LixeiraFalsa(dois())
        val vm = LixeiraViewModel(falsa)
        vm.carregar(); advanceUntilIdle()

        vm.pedirEsvaziar()
        assertEquals(ConfirmacaoDaLixeira.EsvaziarTudo(2, 4096), vm.estado.value.confirmacao)
        assertEquals(0, falsa.esvaziadas)

        vm.confirmar(); advanceUntilIdle()

        assertEquals(1, falsa.esvaziadas)
        assertTrue(pronta(vm).imagens.isEmpty())
        assertEquals("2 imagens apagadas de vez; 4,0 KB liberados.", vm.estado.value.recado)
        assertFalse(vm.estado.value.esvaziando)
    }

    @Test
    fun `lixeira vazia nao tem o que esvaziar`() = runTest {
        val vm = LixeiraViewModel(LixeiraFalsa(Lixeira()))
        vm.carregar(); advanceUntilIdle()

        vm.pedirEsvaziar()

        assertNull(vm.estado.value.confirmacao)
    }

    @Test
    fun `a recusa do servidor vira recado de erro e a imagem continua na lista`() = runTest {
        val falsa = LixeiraFalsa(dois()).also { it.respostaDaAcao = ResultadoDaChamada.Falha("Não há imagem com id 2 na lixeira.") }
        val vm = LixeiraViewModel(falsa)
        vm.carregar(); advanceUntilIdle()

        vm.restaurar(2); advanceUntilIdle()

        assertEquals("Não há imagem com id 2 na lixeira.", vm.estado.value.recado)
        assertTrue(vm.estado.value.recadoEhErro)
        assertEquals(2, pronta(vm).imagens.size)
        assertTrue(vm.estado.value.ocupadas.isEmpty())
    }
}
