package com.allan.imagineer.telas.livro

import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.ArtefatosDeUmCapitulo
import com.allan.imagineer.rede.ArtefatosDoLivro
import com.allan.imagineer.rede.RepositorioDeArtefatosDoLivro
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

private fun cena(titulo: String, vararg elementos: String) = Artefato(tipo = "CENA", rotulo = titulo, situacao = "CONFIRMADO", elementos = elementos.toList())

/** Capítulo 1: "A chegada" (Arya e Jon) e "O pátio" (só Jon). Capítulo 2: "A fuga" (Arya). Capítulo 3: "A calma" (ninguém). */
private val livro = ArtefatosDoLivro(
    total = 4,
    capitulos = listOf(
        ArtefatosDeUmCapitulo(10, 1, "Início", listOf(cena("A chegada", "Arya", "Jon"), cena("O pátio", "Jon"))),
        ArtefatosDeUmCapitulo(20, 2, "Fuga", listOf(cena("A fuga", "Arya"))),
        ArtefatosDeUmCapitulo(30, 3, null, listOf(cena("A calma"))),
    ),
)

private fun titulos(dados: ArtefatosDoLivro) = dados.capitulos.flatMap { it.artefatos }.map { it.rotulo }

private class CenasFalsas(private val dados: ArtefatosDoLivro) : RepositorioDeArtefatosDoLivro {
    override suspend fun pendencias(livroId: Int) = ResultadoDaChamada.Sucesso(dados)
    override suspend fun cenas(livroId: Int) = ResultadoDaChamada.Sucesso(dados)
}

/** O filtro da lista de cenas por capítulo e por elemento (item 7.5b, LF1 a LF4). */
@OptIn(ExperimentalCoroutinesApi::class)
class FiltroDasCenasTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    @Test
    fun sem_filtro_a_lista_fica_como_veio() {
        assertEquals(livro, filtrarCenas(livro, FiltroDasCenas()))
    }

    @Test
    fun filtrar_por_capitulo_deixa_so_as_cenas_dele() {
        val filtrado = filtrarCenas(livro, FiltroDasCenas(capitulos = setOf(20)))

        assertEquals(listOf("A fuga"), titulos(filtrado))
        assertEquals(1, filtrado.total)
    }

    @Test
    fun varios_capitulos_somam() {
        assertEquals(listOf("A chegada", "O pátio", "A calma"), titulos(filtrarCenas(livro, FiltroDasCenas(capitulos = setOf(10, 30)))))
    }

    @Test
    fun filtrar_por_elemento_deixa_as_cenas_em_que_ele_participa_e_tira_o_capitulo_que_ficou_vazio() {
        val filtrado = filtrarCenas(livro, FiltroDasCenas(elementos = setOf("Jon")))

        assertEquals(listOf("A chegada", "O pátio"), titulos(filtrado))
        assertEquals(listOf(10), filtrado.capitulos.map { it.capitulo_id })
        assertEquals(2, filtrado.total)
    }

    @Test
    fun varios_elementos_valem_pelo_menos_um_deles() {
        // Arya ou Jon: todas as cenas que têm algum dos dois.
        assertEquals(listOf("A chegada", "O pátio", "A fuga"), titulos(filtrarCenas(livro, FiltroDasCenas(elementos = setOf("Arya", "Jon")))))
    }

    @Test
    fun capitulo_e_elemento_se_combinam() {
        val filtrado = filtrarCenas(livro, FiltroDasCenas(capitulos = setOf(10), elementos = setOf("Arya")))

        assertEquals(listOf("A chegada"), titulos(filtrado))
    }

    @Test
    fun combinacao_sem_nenhuma_cena_devolve_lista_vazia() {
        val filtrado = filtrarCenas(livro, FiltroDasCenas(capitulos = setOf(20), elementos = setOf("Jon")))

        assertTrue(filtrado.capitulos.isEmpty())
        assertEquals(0, filtrado.total)
    }

    @Test
    fun as_opcoes_de_elemento_vem_sem_repetir_e_em_ordem_alfabetica() {
        assertEquals(listOf("Arya", "Jon"), elementosDasCenas(livro))
        assertTrue("servidor antigo, sem participantes: nada para filtrar", elementosDasCenas(ArtefatosDoLivro(1, listOf(ArtefatosDeUmCapitulo(1, 1, null, listOf(cena("x")))))).isEmpty())
    }

    @Test
    fun os_textos_dos_botoes_e_da_contagem() {
        assertEquals("Capítulos", rotuloDoFiltro("Capítulos", 0))
        assertEquals("Elementos (2)", rotuloDoFiltro("Elementos", 2))
        assertEquals("3 de 12 cenas", textoDasCenasQueSobraram(3, 12))
        assertEquals("1 de 1 cena", textoDasCenasQueSobraram(1, 1))
    }

    @Test
    fun o_viewmodel_marca_desmarca_e_limpa() = runTest {
        val viewModel = ArtefatosDoLivroViewModel(1, TipoDaListaDoLivro.CENAS, CenasFalsas(livro))

        viewModel.alternarCapitulo(10)
        viewModel.alternarElemento("Arya")
        assertEquals(FiltroDasCenas(setOf(10), setOf("Arya")), viewModel.filtro.value)
        assertEquals(2, viewModel.filtro.value.ativos)

        viewModel.alternarCapitulo(10)
        assertEquals(FiltroDasCenas(emptySet(), setOf("Arya")), viewModel.filtro.value)

        viewModel.limparFiltro()
        assertTrue(viewModel.filtro.value.vazio)
    }

    @Test
    fun recarregar_a_lista_nao_perde_o_filtro() = runTest {
        val viewModel = ArtefatosDoLivroViewModel(1, TipoDaListaDoLivro.CENAS, CenasFalsas(livro))
        viewModel.alternarElemento("Jon")

        viewModel.carregar()
        advanceUntilIdle()

        assertEquals(setOf("Jon"), viewModel.filtro.value.elementos)
    }
}
