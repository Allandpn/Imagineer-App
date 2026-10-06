package com.allan.imagineer.telas.pins

import com.allan.imagineer.rede.PinDoLivro
import com.allan.imagineer.rede.RepositorioDePins
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun pin(id: Int, nota: String? = null, capitulo: Int = 5, ordem: Int? = 3, titulo: String? = "A chegada") =
    PinDoLivro(id = id, livro_id = 1, capitulo_id = capitulo, posicao_no_texto = id * 10, nota = nota, trecho = "Era uma vez", ordem_do_capitulo = ordem, titulo_do_capitulo = titulo)

private class PinsFalsos(var existentes: List<PinDoLivro> = emptyList(), var falhar: String? = null) : RepositorioDePins {
    val notasEnviadas = mutableListOf<Pair<Int, String?>>()
    val apagados = mutableListOf<Int>()

    override suspend fun listar(livroId: Int): ResultadoDaChamada<List<PinDoLivro>> =
        falhar?.let { ResultadoDaChamada.Falha(it) } ?: ResultadoDaChamada.Sucesso(existentes)

    override suspend fun criar(livroId: Int, capituloId: Int, posicao: Int, nota: String?): ResultadoDaChamada<PinDoLivro> =
        ResultadoDaChamada.Falha("não usado aqui")

    override suspend fun ajustarNota(pinId: Int, nota: String?): ResultadoDaChamada<PinDoLivro> {
        falhar?.let { return ResultadoDaChamada.Falha(it) }
        notasEnviadas += pinId to nota
        return ResultadoDaChamada.Sucesso(existentes.first { it.id == pinId }.copy(nota = nota?.trim()?.takeIf { it.isNotEmpty() }))
    }

    override suspend fun apagar(pinId: Int): ResultadoDaChamada<Unit> {
        falhar?.let { return ResultadoDaChamada.Falha(it) }
        apagados += pinId
        return ResultadoDaChamada.Sucesso(Unit)
    }
}

/** Os pins do livro: a lista, a nota e o apagar (bloco J). */
@OptIn(ExperimentalCoroutinesApi::class)
class PinsDoLivroTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    @Test
    fun a_linha_de_cima_diz_o_capitulo() {
        assertEquals("Capítulo 3 · A chegada", localDoPin(pin(1)))
        assertEquals("Capítulo 3", localDoPin(pin(1, titulo = " ")))
        assertEquals("A chegada", localDoPin(pin(1, ordem = null)))
        assertEquals("Capítulo 9", localDoPin(pin(1, capitulo = 9, ordem = null, titulo = null)))  // servidor antigo, sem os campos novos
    }

    @Test
    fun o_pin_do_servidor_decodifica_e_o_antigo_nao_traz_o_trecho() {
        val json = Json { ignoreUnknownKeys = true }

        val novo = json.decodeFromString<PinDoLivro>("""{"id":1,"livro_id":2,"capitulo_id":5,"posicao_no_texto":120,"nota":"voltar","criado_em":"2026-10-06T10:00:00","trecho":"Era uma vez","ordem_do_capitulo":3,"titulo_do_capitulo":"A chegada","extra":1}""")
        val antigo = json.decodeFromString<PinDoLivro>("""{"id":1,"livro_id":2,"capitulo_id":5,"posicao_no_texto":120,"nota":null,"criado_em":"2026-10-06T10:00:00"}""")

        assertEquals("Era uma vez", novo.trecho)
        assertEquals(3, novo.ordem_do_capitulo)
        assertEquals("", antigo.trecho)
        assertNull(antigo.ordem_do_capitulo)
    }

    @Test
    fun carrega_os_pins_na_ordem_que_o_servidor_manda() = runTest {
        val vm = PinsDoLivroViewModel(1, PinsFalsos(listOf(pin(2), pin(1))))

        vm.carregar(); advanceUntilIdle()

        assertTrue(vm.estado.value.carregados)
        assertEquals(listOf(2, 1), vm.estado.value.pins.map { it.id })
    }

    @Test
    fun sem_servidor_a_lista_fica_vazia_e_avisa() = runTest {
        val vm = PinsDoLivroViewModel(1, PinsFalsos(falhar = "fora do ar"))

        vm.carregar(); advanceUntilIdle()

        assertTrue(vm.estado.value.carregados)
        assertTrue(vm.estado.value.pins.isEmpty())
        assertTrue(vm.estado.value.aviso!!.contains("fora do ar"))
        vm.avisoLido()
        assertNull(vm.estado.value.aviso)
    }

    @Test
    fun editar_a_nota_troca_o_pin_da_lista_e_em_branco_apaga_a_nota() = runTest {
        val falso = PinsFalsos(listOf(pin(1, nota = "antiga"), pin(2)))
        val vm = PinsDoLivroViewModel(1, falso)
        vm.carregar(); advanceUntilIdle()

        vm.editarNota(vm.estado.value.pins.first(), "nova nota"); advanceUntilIdle()
        assertEquals(listOf("nova nota", null), vm.estado.value.pins.map { it.nota })

        vm.editarNota(vm.estado.value.pins.first(), "   "); advanceUntilIdle()
        assertNull(vm.estado.value.pins.first().nota)
        assertEquals(listOf(1 to "nova nota", 1 to "   "), falso.notasEnviadas)
    }

    @Test
    fun apagar_tira_da_lista_so_quando_o_servidor_confirma() = runTest {
        val falso = PinsFalsos(listOf(pin(1), pin(2)))
        val vm = PinsDoLivroViewModel(1, falso)
        vm.carregar(); advanceUntilIdle()

        vm.apagar(vm.estado.value.pins.first()); advanceUntilIdle()
        assertEquals(listOf(2), vm.estado.value.pins.map { it.id })
        assertEquals(listOf(1), falso.apagados)

        falso.falhar = "fora do ar"
        vm.apagar(vm.estado.value.pins.single()); advanceUntilIdle()
        assertEquals(listOf(2), vm.estado.value.pins.map { it.id })  // continua
        assertFalse(vm.estado.value.aviso.isNullOrBlank())
    }

    @Test
    fun a_falha_ao_editar_a_nota_nao_muda_a_lista() = runTest {
        val falso = PinsFalsos(listOf(pin(1, nota = "antiga")))
        val vm = PinsDoLivroViewModel(1, falso)
        vm.carregar(); advanceUntilIdle()
        falso.falhar = "fora do ar"

        vm.editarNota(vm.estado.value.pins.single(), "nova"); advanceUntilIdle()

        assertEquals("antiga", vm.estado.value.pins.single().nota)
        assertTrue(vm.estado.value.aviso!!.contains("fora do ar"))
    }
}
