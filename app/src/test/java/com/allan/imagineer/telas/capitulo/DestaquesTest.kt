package com.allan.imagineer.telas.capitulo

import com.allan.imagineer.rede.CorDeDestaque
import com.allan.imagineer.rede.Destaque
import com.allan.imagineer.rede.RepositorioDeDestaques
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun destaque(id: Int, inicio: Int, fim: Int, cor: String = "AMARELO", elemento: Int? = null, nota: String? = null) =
    Destaque(id, 1, 10, inicio, fim, "trecho$id", cor, nota, elemento)

private class DestaquesFalsos(var guardados: List<Destaque> = emptyList(), var falhar: Boolean = false) : RepositorioDeDestaques {
    var proximoId = 100
    private fun <T> resposta(valor: T): ResultadoDaChamada<T> = if (falhar) ResultadoDaChamada.Falha("sem rede") else ResultadoDaChamada.Sucesso(valor)

    override suspend fun listar(livroId: Int, capituloId: Int?) = resposta(guardados)
    override suspend fun criar(livroId: Int, capituloId: Int, inicio: Int, fim: Int, cor: CorDeDestaque): ResultadoDaChamada<Destaque> {
        val novo = destaque(proximoId++, inicio, fim, cor.name)
        return resposta(novo)
    }
    // Como o servidor, guarda o que mudou: o ajuste seguinte parte do já ajustado.
    private fun trocar(id: Int, f: (Destaque) -> Destaque): ResultadoDaChamada<Destaque> {
        val novo = f(guardados.first { it.id == id })
        if (!falhar) guardados = guardados.map { if (it.id == id) novo else it }
        return resposta(novo)
    }
    override suspend fun ajustarCor(destaqueId: Int, cor: CorDeDestaque) = trocar(destaqueId) { it.copy(cor = cor.name) }
    override suspend fun ajustarNota(destaqueId: Int, nota: String?) = trocar(destaqueId) { it.copy(nota = nota) }
    override suspend fun ligarAoElemento(destaqueId: Int, elementoId: Int?) = trocar(destaqueId) { it.copy(elemento_id = elementoId) }
    override suspend fun remover(destaqueId: Int) = resposta(Unit)
    override suspend fun doElemento(elementoId: Int) = resposta(guardados.filter { it.elemento_id == elementoId })
}

/** Os destaques do leitor (RL9 a RL13): achar o lugar do trecho, recortar para o parágrafo na tela e o ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
class DestaquesTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private val paragrafos = listOf(
        ParagrafoDoTexto(0, "Harry abriu a porta."),
        ParagrafoDoTexto(22, "Hermione sorriu\npara ele. Harry abriu de novo."),
    )

    // ---- localizar o trecho

    @Test
    fun acha_o_trecho_dentro_do_paragrafo_contando_do_inicio_do_capitulo() {
        assertEquals(LugarDoTrecho(6, 11), localizarTrecho(paragrafos, "abriu"))
        assertEquals(LugarDoTrecho(22 + 16, 22 + 20), localizarTrecho(paragrafos, "para"))
    }

    @Test
    fun se_o_trecho_se_repete_vale_o_primeiro() {
        assertEquals(LugarDoTrecho(0, 5), localizarTrecho(paragrafos, "Harry"))
    }

    @Test
    fun aceita_espaco_no_lugar_da_quebra_de_linha() {
        val lugar = localizarTrecho(paragrafos, "sorriu para ele")
        assertEquals(LugarDoTrecho(22 + 9, 22 + 9 + "sorriu\npara ele".length), lugar)
    }

    @Test
    fun trecho_vazio_ou_que_nao_existe_nao_tem_lugar() {
        assertNull(localizarTrecho(paragrafos, "   "))
        assertNull(localizarTrecho(paragrafos, "Dumbledore"))
    }

    // ---- recortar para o que está na tela

    @Test
    fun o_destaque_vira_posicao_dentro_do_texto_mostrado() {
        val d = destaque(1, 6, 11, "VERDE")  // "abriu" no parágrafo que começa em 0

        val marcas = destaquesDaFatia(listOf(d), inicioDoParagrafo = 0, de = 0, esquerdaCortada = 0, mostrado = 20)

        assertEquals(listOf(DestaqueNoTexto(1, 6, 11, CorDeDestaque.VERDE)), marcas)
    }

    @Test
    fun a_fatia_cortada_so_mostra_a_parte_do_destaque_que_cabe_nela() {
        val d = destaque(1, 10, 30)
        // O pedaço mostrado cobre as posições 20 a 40 do capítulo (parágrafo em 0, corte em 20): só 20..30 aparece.
        val marcas = destaquesDaFatia(listOf(d), 0, de = 20, esquerdaCortada = 0, mostrado = 20)

        assertEquals(listOf(DestaqueNoTexto(1, 0, 10, CorDeDestaque.AMARELO)), marcas)
    }

    @Test
    fun o_espaco_cortado_do_comeco_desloca_as_posicoes() {
        val d = destaque(1, 5, 8)
        // O corte começa em 2 e o trim tirou 1 espaço: o primeiro caractere mostrado é o da posição 3.
        val marcas = destaquesDaFatia(listOf(d), 0, de = 2, esquerdaCortada = 1, mostrado = 10)

        assertEquals(listOf(DestaqueNoTexto(1, 2, 5, CorDeDestaque.AMARELO)), marcas)
    }

    @Test
    fun destaque_de_fora_do_pedaco_nao_aparece() {
        assertTrue(destaquesDaFatia(listOf(destaque(1, 100, 110)), 0, 0, 0, 20).isEmpty())
    }

    @Test
    fun cor_desconhecida_cai_no_amarelo() {
        assertEquals(CorDeDestaque.AMARELO, CorDeDestaque.de("LILAS"))
        assertEquals(CorDeDestaque.ROSA, CorDeDestaque.de("ROSA"))
    }

    // ---- o ViewModel

    @Test
    fun carrega_os_destaques_do_capitulo() = runTest {
        val vm = DestaquesDoCapituloViewModel(1, 10, DestaquesFalsos(listOf(destaque(1, 0, 5))))

        vm.carregar()
        advanceUntilIdle()

        assertEquals(listOf(1), vm.destaques.value.map { it.id })
    }

    @Test
    fun destacar_acrescenta_na_ordem_do_texto_e_avisa_quem_pediu() = runTest {
        val vm = DestaquesDoCapituloViewModel(1, 10, DestaquesFalsos(listOf(destaque(1, 20, 25))))
        vm.carregar()
        advanceUntilIdle()
        var criado: Destaque? = null

        vm.destacar(LugarDoTrecho(0, 5)) { criado = it }
        advanceUntilIdle()

        assertEquals(listOf(0, 20), vm.destaques.value.map { it.inicio })
        assertNotNull(criado)
    }

    @Test
    fun mudar_cor_nota_e_elemento_atualiza_so_aquele_destaque() = runTest {
        val vm = DestaquesDoCapituloViewModel(1, 10, DestaquesFalsos(listOf(destaque(1, 0, 5), destaque(2, 10, 15))))
        vm.carregar()
        advanceUntilIdle()

        vm.mudarCor(1, CorDeDestaque.AZUL)
        vm.mudarNota(1, "  importante  ")
        vm.ligarAoElemento(1, 7)
        advanceUntilIdle()

        val primeiro = vm.destaques.value.first { it.id == 1 }
        assertEquals("AZUL", primeiro.cor)
        assertEquals(7, primeiro.elemento_id)
        assertEquals("AMARELO", vm.destaques.value.first { it.id == 2 }.cor)
    }

    @Test
    fun nota_em_branco_apaga_a_nota() = runTest {
        val vm = DestaquesDoCapituloViewModel(1, 10, DestaquesFalsos(listOf(destaque(1, 0, 5, nota = "velha"))))
        vm.carregar()
        advanceUntilIdle()

        vm.mudarNota(1, "   ")
        advanceUntilIdle()

        assertNull(vm.destaques.value.first().nota)
    }

    @Test
    fun remover_tira_da_lista() = runTest {
        val vm = DestaquesDoCapituloViewModel(1, 10, DestaquesFalsos(listOf(destaque(1, 0, 5), destaque(2, 10, 15))))
        vm.carregar()
        advanceUntilIdle()

        vm.remover(1)
        advanceUntilIdle()

        assertEquals(listOf(2), vm.destaques.value.map { it.id })
    }

    @Test
    fun sem_servidor_nao_finge_que_gravou_e_avisa() = runTest {
        val falso = DestaquesFalsos(falhar = true)
        val vm = DestaquesDoCapituloViewModel(1, 10, falso)

        vm.destacar(LugarDoTrecho(0, 5))
        advanceUntilIdle()

        assertTrue(vm.destaques.value.isEmpty())
        assertTrue(vm.aviso.value!!.contains("sem rede"))
        vm.avisoLido()
        assertNull(vm.aviso.value)
    }
}
