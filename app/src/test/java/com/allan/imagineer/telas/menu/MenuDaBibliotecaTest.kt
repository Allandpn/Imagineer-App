package com.allan.imagineer.telas.menu

import org.junit.Assert.assertEquals
import org.junit.Test

/** O menu da biblioteca (MN1 a MN6): as regras que não precisam de tela. */
class MenuDaBibliotecaTest {

    @Test
    fun `MN2 a gaveta tem perfil, configuracoes, lixeira e custos, nessa ordem`() {
        assertEquals(listOf("Perfil", "Configurações", "Lixeira", "Custos"), ItemDoMenu.entries.map { it.rotulo })
    }

    @Test
    fun `MN4 sem nada guardado ou com texto desconhecido a biblioteca mostra capas`() {
        assertEquals(ModoDaBiblioteca.CAPAS, ModoDaBiblioteca.deTexto(null))
        assertEquals(ModoDaBiblioteca.CAPAS, ModoDaBiblioteca.deTexto("QUALQUER_COISA"))
    }

    @Test
    fun `MN4 o modo guardado volta como estava`() {
        assertEquals(ModoDaBiblioteca.LISTA, ModoDaBiblioteca.deTexto(ModoDaBiblioteca.LISTA.name))
        assertEquals(ModoDaBiblioteca.CAPAS, ModoDaBiblioteca.deTexto(ModoDaBiblioteca.CAPAS.name))
    }
}
