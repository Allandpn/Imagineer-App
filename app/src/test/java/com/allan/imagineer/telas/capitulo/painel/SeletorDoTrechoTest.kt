package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.ElementoParaVincular
import com.allan.imagineer.rede.ElementosParaVincular
import com.allan.imagineer.rede.RepositorioDePrompts
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

private fun candidato(estado: Int, nome: String) = ElementoParaVincular(elemento_id = estado, estado_id = estado, nome = nome, tipo = "PERSONAGEM")

private val DADOS = ElementosParaVincular(
    identificados = listOf(candidato(9, "Jon")),
    outros = listOf(candidato(10, "Prato")),
    de_outros_capitulos = listOf(candidato(11, "Escudo")),
)

/** A cena de um trecho escolhe entre os mesmos elementos de qualquer cena (LV8). */
@OptIn(ExperimentalCoroutinesApi::class)
class SeletorDoTrechoTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private fun prompts(resposta: ResultadoDaChamada<ElementosParaVincular>): RepositorioDePrompts =
        object : RepositorioDePrompts by PromptsFalso() {
            override suspend fun elementosParaCena(capituloId: Int) = resposta
        }

    @Test
    fun `LV8 as opcoes juntam identificados, outros e os de outros capitulos`() {
        assertEquals(listOf("Jon", "Prato", "Escudo"), opcoesDoSeletorDoTrecho(DADOS).map { it.nome })
    }

    @Test
    fun `LV8 abrir o trecho le o seletor e marca os citados`() = runTest {
        val vm = PainelDeIaViewModel(5, SugestoesFalso(), ElementosFalso(), prompts(ResultadoDaChamada.Sucesso(DADOS)))

        vm.abrirTrecho("Jon trouxe o Escudo.", posicao = 0); advanceUntilIdle()

        val trecho = vm.estado.value.trechoParaImagem!!
        assertEquals(CandidatosDoSeletor.Prontos(DADOS), trecho.candidatos)
        assertEquals(setOf(9, 11), trecho.estadosEscolhidos) // Jon (identificado) e Escudo (de outro capítulo), ambos citados
    }

    @Test
    fun `LV8 o que a pessoa escolheu entre os outros elementos vai para a cena`() = runTest {
        val vm = PainelDeIaViewModel(5, SugestoesFalso(), ElementosFalso(), prompts(ResultadoDaChamada.Sucesso(DADOS)))
        vm.abrirTrecho("Uma tarde calma.", posicao = 0); advanceUntilIdle()

        vm.alternarElementoDoTrecho(10) // o Prato, que o texto não cita

        assertEquals(setOf(10), vm.estado.value.trechoParaImagem!!.estadosEscolhidos)
    }

    @Test
    fun `LV8 se o seletor falhar o dialogo continua com o que ja havia`() = runTest {
        val vm = PainelDeIaViewModel(5, SugestoesFalso(), ElementosFalso(), prompts(ResultadoDaChamada.Falha("Sem conexão.")))

        vm.abrirTrecho("Jon chegou.", posicao = 0); advanceUntilIdle()

        val trecho = vm.estado.value.trechoParaImagem!!
        assertEquals(CandidatosDoSeletor.Erro("Sem conexão."), trecho.candidatos)
        assertTrue(trecho.estadosEscolhidos.isEmpty())
    }
}
