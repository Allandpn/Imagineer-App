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

private fun artefato(tipo: String = "ELEMENTO", rotulo: String = "Arya", situacao: String = "SUGERIDO", frameId: Int? = null) =
    Artefato(tipo = tipo, tipo_do_elemento = if (tipo == "ELEMENTO") "PERSONAGEM" else null, rotulo = rotulo, situacao = situacao, frame_id = frameId)

private class ArtefatosFalsos(var pendencias: ResultadoDaChamada<ArtefatosDoLivro>, var cenas: ResultadoDaChamada<ArtefatosDoLivro>) : RepositorioDeArtefatosDoLivro {
    override suspend fun pendencias(livroId: Int) = pendencias
    override suspend fun cenas(livroId: Int) = cenas
}

private fun resposta(vararg artefatos: Artefato) =
    ResultadoDaChamada.Sucesso(ArtefatosDoLivro(artefatos.size, listOf(ArtefatosDeUmCapitulo(5, 1, "A chegada", artefatos.toList()))))

/** As telas Pendências e Cenas do livro e a barra de baixo (LY1, LY7, LY8). */
@OptIn(ExperimentalCoroutinesApi::class)
class ListasDoLivroTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    @Test
    fun a_barra_de_baixo_tem_as_quatro_telas_na_ordem_da_especificacao() {
        assertEquals(listOf("Elementos", "Cenas", "Pendências", "Arquivados"), DestinoDoLivro.entries.map { it.descricao })
    }

    @Test
    fun o_grupo_diz_o_capitulo_com_ou_sem_titulo() {
        assertEquals("Capítulo 3 · A chegada", tituloDoGrupo(3, "A chegada"))
        assertEquals("Capítulo 3", tituloDoGrupo(3, null))
        assertEquals("Capítulo 3", tituloDoGrupo(3, "  "))
    }

    @Test
    fun cena_com_frame_abre_o_modal_dela_e_o_resto_abre_o_painel_do_capitulo() {
        assertEquals(AlvoNoCapitulo(5, 70, "O vento"), alvoDoToque(5, artefato("CENA", "O vento", "CONFIRMADO", frameId = 70)))
        assertEquals(AlvoNoCapitulo(5, abrirPainel = true), alvoDoToque(5, artefato("CENA", "O vento", "SUGERIDO")))
        assertEquals(AlvoNoCapitulo(5, abrirPainel = true), alvoDoToque(5, artefato("ELEMENTO", "Arya")))
    }

    @Test
    fun a_situacao_se_le_em_portugues() {
        assertEquals(listOf("sugerida", "confirmada", "prompt pronto", "ilustrada"), listOf("SUGERIDO", "CONFIRMADO", "PROMPT_PRONTO", "ILUSTRADO").map(::rotuloDaSituacao))
    }

    @Test
    fun as_pendencias_e_as_cenas_vem_da_rota_certa() = runTest {
        val repositorio = ArtefatosFalsos(resposta(artefato(rotulo = "Arya")), resposta(artefato("CENA", "O vento")))
        val pendencias = ArtefatosDoLivroViewModel(1, TipoDaListaDoLivro.PENDENCIAS, repositorio)
        val cenas = ArtefatosDoLivroViewModel(1, TipoDaListaDoLivro.CENAS, repositorio)

        pendencias.carregar(); cenas.carregar()
        advanceUntilIdle()

        assertEquals("Arya", ((pendencias.estado.value as EstadoDaListaDoLivro.Pronto).dados.capitulos[0].artefatos[0]).rotulo)
        assertEquals("O vento", ((cenas.estado.value as EstadoDaListaDoLivro.Pronto).dados.capitulos[0].artefatos[0]).rotulo)
    }

    @Test
    fun falha_na_primeira_leitura_vira_erro_e_na_releitura_nao_apaga_o_que_ja_estava() = runTest {
        val repositorio = ArtefatosFalsos(ResultadoDaChamada.Falha("Sem rede."), resposta())
        val vm = ArtefatosDoLivroViewModel(1, TipoDaListaDoLivro.PENDENCIAS, repositorio)

        vm.carregar()
        advanceUntilIdle()
        assertEquals("Sem rede.", (vm.estado.value as EstadoDaListaDoLivro.Erro).motivo)

        repositorio.pendencias = resposta(artefato())
        vm.tentarDeNovo()
        advanceUntilIdle()
        assertTrue(vm.estado.value is EstadoDaListaDoLivro.Pronto)

        repositorio.pendencias = ResultadoDaChamada.Falha("Sem rede de novo.")
        vm.carregar()
        advanceUntilIdle()
        assertTrue("a lista que já estava na tela continua", vm.estado.value is EstadoDaListaDoLivro.Pronto)
    }
}
