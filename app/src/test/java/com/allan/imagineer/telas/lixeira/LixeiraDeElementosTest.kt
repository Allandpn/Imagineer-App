package com.allan.imagineer.telas.lixeira

import com.allan.imagineer.rede.ElementoNaLixeira
import com.allan.imagineer.rede.ElementosDaLixeira
import com.allan.imagineer.rede.LixeiraEsvaziada
import com.allan.imagineer.rede.RepositorioDaLixeiraDeElementos
import com.allan.imagineer.rede.RepositorioDaLixeiraDeElementosDoLivro
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

private fun elemento(id: Int, livro: Int = 1, tipo: String = "PERSONAGEM", retratos: Int = 1, imagens: Int = 1, bytes: Long = 1024) = ElementoNaLixeira(
    id = id, nome = "Elemento $id", tipo = tipo, livro_id = livro, titulo_do_livro = "O livro $livro", apagado_em = "2026-10-04T10:00:00Z",
    total_de_estados = 2, total_de_retratos = retratos, total_de_imagens = imagens, tamanho_em_bytes = bytes, imagem_id = if (imagens > 0) 9 else null,
)

private class LixeiraDeElementosFalsa(var elementos: List<ElementoNaLixeira>) : RepositorioDaLixeiraDeElementos {
    val restaurados = mutableListOf<Int>()
    val apagados = mutableListOf<Int>()
    var esvaziados = 0

    override suspend fun listar(): ResultadoDaChamada<ElementosDaLixeira> =
        ResultadoDaChamada.Sucesso(ElementosDaLixeira(elementos, elementos.sumOf { it.tamanho_em_bytes }))

    override suspend fun restaurar(elementoId: Int): ResultadoDaChamada<Unit> {
        restaurados += elementoId
        elementos = elementos.filterNot { it.id == elementoId }
        return ResultadoDaChamada.Sucesso(Unit)
    }

    override suspend fun apagarDeVez(elementoId: Int): ResultadoDaChamada<Unit> {
        apagados += elementoId
        elementos = elementos.filterNot { it.id == elementoId }
        return ResultadoDaChamada.Sucesso(Unit)
    }

    override suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada> {
        esvaziados++
        val feitos = elementos.size
        elementos = emptyList()
        return ResultadoDaChamada.Sucesso(LixeiraEsvaziada(feitos, 2048))
    }
}

/** A lixeira de elementos (LT4) sobre o ViewModel genérico de itens (LT1). */
@OptIn(ExperimentalCoroutinesApi::class)
class LixeiraDeElementosTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private fun ids(vm: LixeiraDeItensViewModel<ElementoDaLixeira>) = (vm.estado.value.carga as CargaDeItens.Pronta).itens.map { it.id }

    @Test
    fun carregar_traz_os_elementos_e_o_espaco() = runTest {
        val vm = LixeiraDeItensViewModel(FonteDaLixeiraDeElementos(LixeiraDeElementosFalsa(listOf(elemento(1), elemento(2, bytes = 2048)))))

        vm.carregar()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), ids(vm))
        assertEquals(3072L, (vm.estado.value.carga as CargaDeItens.Pronta).totalEmBytes)
    }

    @Test
    fun restaurar_tira_da_lista_e_avisa_que_voltou_com_estados_e_retratos() = runTest {
        val repositorio = LixeiraDeElementosFalsa(listOf(elemento(1), elemento(2)))
        val vm = LixeiraDeItensViewModel(FonteDaLixeiraDeElementos(repositorio))
        vm.carregar()
        advanceUntilIdle()

        vm.restaurar(1)
        advanceUntilIdle()

        assertEquals(listOf(1), repositorio.restaurados)
        assertEquals(listOf(2), ids(vm))
        assertTrue(vm.estado.value.recado!!.contains("estados e os retratos"))
    }

    @Test
    fun apagar_de_vez_so_depois_de_confirmar() = runTest {
        val repositorio = LixeiraDeElementosFalsa(listOf(elemento(1)))
        val vm = LixeiraDeItensViewModel(FonteDaLixeiraDeElementos(repositorio))
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
    fun esvaziar_diz_quantos_elementos_vao_embora() = runTest {
        val repositorio = LixeiraDeElementosFalsa(listOf(elemento(1), elemento(2)))
        val vm = LixeiraDeItensViewModel(FonteDaLixeiraDeElementos(repositorio))
        vm.carregar()
        advanceUntilIdle()

        vm.pedirEsvaziar()
        val pedido = vm.estado.value.confirmacao as ConfirmacaoDeItens.EsvaziarTudo
        assertTrue(vm.textos.avisoDeEsvaziar(pedido.quantos, pedido.bytes).startsWith("2 elementos"))

        vm.confirmar()
        advanceUntilIdle()
        assertEquals(1, repositorio.esvaziados)
    }

    @Test
    fun a_lixeira_do_livro_so_traz_os_elementos_dele_e_soma_so_o_espaco_deles() = runTest {
        val base = LixeiraDeElementosFalsa(listOf(elemento(1, livro = 1, bytes = 100), elemento(2, livro = 2, bytes = 5000), elemento(3, livro = 1, bytes = 200)))

        val lista = (RepositorioDaLixeiraDeElementosDoLivro(base, 1).listar() as ResultadoDaChamada.Sucesso).dado

        assertEquals(listOf(1, 3), lista.elementos.map { it.id })
        assertEquals(300L, lista.total_em_bytes)
    }

    @Test
    fun os_detalhes_dizem_tipo_livro_e_o_que_leva() {
        val texto = detalhesDoElementoNaLixeira(elemento(1, tipo = "CRIATURA", retratos = 1, imagens = 0))

        assertTrue(texto, texto.startsWith("Criatura · O livro 1\n2 estados · 1 retrato · sem imagens"))
    }
}
