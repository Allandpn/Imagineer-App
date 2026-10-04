package com.allan.imagineer.telas.lixeira

import com.allan.imagineer.rede.FrameNaLixeira
import com.allan.imagineer.rede.FramesDaLixeira
import com.allan.imagineer.rede.LixeiraEsvaziada
import com.allan.imagineer.rede.RepositorioDaLixeiraDeFrames
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

private fun frame(id: Int, tipo: String = "CENA", elemento: String? = null, imagens: Int = 1, bytes: Long = 1024) = FrameNaLixeira(
    id = id, titulo = "Cena $id", tipo = tipo, nome_do_elemento = elemento,
    capitulo_id = 5, titulo_do_capitulo = "A chegada", ordem_do_capitulo = 3,
    livro_id = 1, titulo_do_livro = "O livro", apagado_em = "2026-10-04T10:00:00Z",
    total_de_prompts = 2, total_de_imagens = imagens, tamanho_em_bytes = bytes, imagem_id = if (imagens > 0) 9 else null,
)

private class LixeiraDeFramesFalsa(var frames: List<FrameNaLixeira>) : RepositorioDaLixeiraDeFrames {
    val restaurados = mutableListOf<Int>()
    val apagados = mutableListOf<Int>()
    var esvaziados = 0

    override suspend fun listar(): ResultadoDaChamada<FramesDaLixeira> =
        ResultadoDaChamada.Sucesso(FramesDaLixeira(frames, frames.sumOf { it.tamanho_em_bytes }))

    override suspend fun restaurar(frameId: Int): ResultadoDaChamada<Unit> {
        restaurados += frameId
        frames = frames.filterNot { it.id == frameId }
        return ResultadoDaChamada.Sucesso(Unit)
    }

    override suspend fun apagarDeVez(frameId: Int): ResultadoDaChamada<Unit> {
        apagados += frameId
        frames = frames.filterNot { it.id == frameId }
        return ResultadoDaChamada.Sucesso(Unit)
    }

    override suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada> {
        esvaziados++
        val feitos = frames.size
        frames = emptyList()
        return ResultadoDaChamada.Sucesso(LixeiraEsvaziada(feitos, 2048))
    }
}

/** A lixeira de cenas e retratos (LT3) sobre o ViewModel genérico de itens (LT1). */
@OptIn(ExperimentalCoroutinesApi::class)
class LixeiraDeCenasTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private fun vm(repositorio: LixeiraDeFramesFalsa) = LixeiraDeItensViewModel(FonteDaLixeiraDeFrames(repositorio))

    private fun ids(vm: LixeiraDeItensViewModel<FrameDaLixeira>) =
        (vm.estado.value.carga as CargaDeItens.Pronta).itens.map { it.id }

    @Test
    fun carregar_traz_as_cenas_e_o_espaco() = runTest {
        val vm = vm(LixeiraDeFramesFalsa(listOf(frame(1), frame(2, bytes = 2048))))

        vm.carregar()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), ids(vm))
        assertEquals(3072L, (vm.estado.value.carga as CargaDeItens.Pronta).totalEmBytes)
    }

    @Test
    fun restaurar_tira_da_lista_e_avisa() = runTest {
        val repositorio = LixeiraDeFramesFalsa(listOf(frame(1), frame(2)))
        val vm = vm(repositorio)
        vm.carregar()
        advanceUntilIdle()

        vm.restaurar(1)
        advanceUntilIdle()

        assertEquals(listOf(1), repositorio.restaurados)
        assertEquals(listOf(2), ids(vm))
        assertTrue(vm.estado.value.recado!!.startsWith("Restaurada"))
    }

    @Test
    fun apagar_de_vez_so_depois_de_confirmar() = runTest {
        val repositorio = LixeiraDeFramesFalsa(listOf(frame(1)))
        val vm = vm(repositorio)
        vm.carregar()
        advanceUntilIdle()

        vm.pedirApagarDeVez((vm.estado.value.carga as CargaDeItens.Pronta).itens.first())
        advanceUntilIdle()
        assertTrue(repositorio.apagados.isEmpty())

        vm.confirmar()
        advanceUntilIdle()
        assertEquals(listOf(1), repositorio.apagados)
        assertTrue(ids(vm).isEmpty())
    }

    @Test
    fun esvaziar_pede_confirmacao_e_diz_quanto_vai_embora() = runTest {
        val repositorio = LixeiraDeFramesFalsa(listOf(frame(1), frame(2)))
        val vm = vm(repositorio)
        vm.carregar()
        advanceUntilIdle()

        vm.pedirEsvaziar()
        val pedido = vm.estado.value.confirmacao as ConfirmacaoDeItens.EsvaziarTudo
        assertEquals(2, pedido.quantos)
        assertTrue(vm.textos.avisoDeEsvaziar(pedido.quantos, pedido.bytes).startsWith("2 cenas e retratos"))

        vm.confirmar()
        advanceUntilIdle()
        assertEquals(1, repositorio.esvaziados)
    }

    @Test
    fun o_retrato_e_chamado_pelo_nome_do_elemento_e_a_cena_pelo_titulo() {
        assertEquals("Retrato de Aragorn", tituloDoFrameNaLixeira(frame(1, tipo = "PERSONAGEM", elemento = "Aragorn")))
        assertEquals("Cena 2", tituloDoFrameNaLixeira(frame(2)))
    }

    @Test
    fun os_detalhes_dizem_onde_estava_e_o_que_leva() {
        val texto = detalhesDoFrameNaLixeira(frame(1, imagens = 1, bytes = 2048))

        assertTrue(texto, texto.startsWith("O livro · Capítulo 3: A chegada\n2 prompts · 1 imagem ("))
        assertTrue(texto, detalhesDoFrameNaLixeira(frame(1, imagens = 0)).contains("sem imagens"))
    }
}
