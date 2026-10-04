package com.allan.imagineer.telas.elementos

import com.allan.imagineer.rede.CapituloComElementos
import com.allan.imagineer.rede.ElementoDoCapitulo
import com.allan.imagineer.rede.ElementoCasado
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import com.allan.imagineer.telas.capitulo.painel.ElementosFalso
import com.allan.imagineer.telas.capitulo.painel.sugestaoDoElemento
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
import org.junit.Before
import org.junit.Test

private fun noCapitulo(id: Int, ordem: Int, vararg elementos: Int) = CapituloComElementos(
    capitulo_id = id, ordem = ordem, titulo = null,
    elementos = elementos.map { ElementoDoCapitulo(elemento_id = it, nome = "E$it") },
)

/** Os capítulos de cada elemento, na lista de elementos (LV3). */
@OptIn(ExperimentalCoroutinesApi::class)
class CapitulosDoElementoTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    @Test
    fun `LV3 inverte capitulo-elementos em elemento-capitulos, na ordem do livro`() {
        val mapa = capitulosPorElemento(listOf(noCapitulo(50, 5, 1, 2), noCapitulo(30, 3, 1)))

        assertEquals(listOf(3, 5), mapa.getValue(1).map { it.ordem })
        assertEquals(listOf(30, 50), mapa.getValue(1).map { it.capituloId })
        assertEquals(listOf("Cap. 5"), mapa.getValue(2).map { it.rotulo })
        assertNull(mapa[99])
    }

    @Test
    fun `LV3 a lista carrega os capitulos de cada elemento junto`() = runTest {
        val falso = ElementosFalso().apply {
            lista = ResultadoDaChamada.Sucesso(listOf(ElementoDoLivro(1, "PERSONAGEM", "Jon")))
            porCapitulo = ResultadoDaChamada.Sucesso(listOf(noCapitulo(30, 3, 1)))
        }
        val vm = ListaDeElementosViewModel(4, falso)

        vm.carregar(); advanceUntilIdle()

        assertEquals(listOf(30), vm.estado.value.capitulosDosElementos[1]?.map { it.capituloId })
    }

    @Test
    fun `LV3 falhar em ler os capitulos nao esconde a lista`() = runTest {
        val falso = ElementosFalso().apply {
            lista = ResultadoDaChamada.Sucesso(listOf(ElementoDoLivro(1, "PERSONAGEM", "Jon")))
            porCapitulo = ResultadoDaChamada.Falha("sem rede")
        }
        val vm = ListaDeElementosViewModel(4, falso)

        vm.carregar(); advanceUntilIdle()

        assertEquals(1, (vm.estado.value.carga as CargaDaLista.Pronta).elementos.size)
        assertEquals(emptyMap<Int, List<CapituloDoElemento>>(), vm.estado.value.capitulosDosElementos)
    }

    @Test
    fun `LV3 a sugestao do elemento no capitulo e achada pelo elemento casado`() {
        val sugestoes = SugestoesDeCapitulo(
            elementos = listOf(
                ElementoSugerido(id = 7, tipo = "PERSONAGEM", nome = "Jon", elemento_id = 1, elemento_casado = ElementoCasado(id = 1, tipo = "PERSONAGEM", nome = "Jon")),
                ElementoSugerido(id = 8, tipo = "PERSONAGEM", nome = "Arya"),
            ),
        )
        assertEquals(7, sugestaoDoElemento(sugestoes, 1))
        assertNull(sugestaoDoElemento(sugestoes, 2))
    }
}
