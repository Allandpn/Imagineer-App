package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.CenaComImagens
import com.allan.imagineer.rede.ElementoParaVincular
import com.allan.imagineer.rede.ElementosParaVincular
import com.allan.imagineer.rede.ImagemCandidata
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun elemento(estado: Int, nome: String, vararg imagens: Int) =
    ElementoParaVincular(
        elemento_id = estado * 10, estado_id = estado, nome = nome, tipo = "PERSONAGEM", no_frame = false, removivel = false,
        imagens = imagens.map { ImagemCandidata(id = it) },
    )

private fun cena(frame: Int, titulo: String, vararg imagens: Int) =
    CenaComImagens(frame_id = frame, titulo = titulo, capitulo_id = 5, ordem_do_capitulo = 2, titulo_do_capitulo = "A descida", imagens = imagens.map { ImagemCandidata(id = it) })

private val dados = ElementosParaVincular(
    identificados = listOf(elemento(1, "Auri", 11, 12)),
    cenas = listOf(cena(70, "A partida", 101, 102), cena(71, "O poço", 103)),
)

/** As imagens de cenas como referência de outra cena (EV15). */
class ImagensDeCenasComoReferenciaTest {

    @Test
    fun marcar_a_imagem_de_uma_cena_so_poe_a_imagem_e_nao_mexe_nos_elementos() {
        val depois = alternarImagemNoSeletor(dados, SelecaoNoSeletor(), 101, ehCena = true)

        assertEquals(setOf(101), depois.imagens)
        assertTrue(depois.elementos.isEmpty())
    }

    @Test
    fun marcar_de_novo_desmarca_e_o_limite_de_imagens_vale_para_as_de_cena_tambem() {
        val marcada = alternarImagemNoSeletor(dados, SelecaoNoSeletor(), 101, ehCena = true)
        assertTrue(alternarImagemNoSeletor(dados, marcada, 101, ehCena = true).imagens.isEmpty())

        val cheia = SelecaoNoSeletor(imagens = setOf(1, 2, 3, 4))
        assertEquals(cheia, alternarImagemNoSeletor(dados, cheia, 103, ehCena = true))
    }

    @Test
    fun a_imagem_de_um_elemento_continua_colocando_o_elemento_junto() {
        val depois = alternarImagemNoSeletor(dados, SelecaoNoSeletor(), 11, ehCena = true)

        assertEquals(setOf(1), depois.elementos)
        assertEquals(setOf(11), depois.imagens)
    }

    @Test
    fun uma_imagem_que_nao_e_de_ninguem_nao_marca_nada() {
        assertEquals(SelecaoNoSeletor(), alternarImagemNoSeletor(dados, SelecaoNoSeletor(), 999, ehCena = true))
    }

    @Test
    fun ao_abrir_as_referencias_de_cena_que_ainda_existem_ficam_marcadas() {
        val selecao = selecaoInicial(dados, listOf(101, 11, 555))  // 555 sumiu (foi para a lixeira)

        assertEquals(setOf(101, 11), selecao.imagens)
    }

    @Test
    fun limpar_tira_as_imagens_de_cenas_tambem() {
        assertTrue(limparSeletor(dados).imagens.isEmpty())
    }

    @Test
    fun o_servidor_antigo_sem_cenas_vira_lista_vazia() {
        val json = Json { ignoreUnknownKeys = true }

        val antigo = json.decodeFromString<ElementosParaVincular>("""{"identificados":[],"outros":[]}""")
        val novo = json.decodeFromString<ElementosParaVincular>(
            """{"identificados":[],"outros":[],"cenas":[{"frame_id":70,"titulo":"A partida","capitulo_id":5,"ordem_do_capitulo":2,"titulo_do_capitulo":null,"imagens":[{"id":101}]}]}""",
        )

        assertTrue(antigo.cenas.isEmpty())
        assertEquals(listOf(101), novo.cenas.single().imagens.map { it.id })
    }
}
