package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.MINIMO_DO_TRECHO_DA_CENA
import com.allan.imagineer.rede.trechoParaOServidor
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** O trecho do livro em que a cena acontece (FD7): o que o app manda e o que mostra. */
class TrechoDaCenaTest {

    @Test
    fun a_selecao_vai_ao_servidor_sem_quebras_de_linha_nem_espacos_repetidos() {
        assertEquals(
            "Ned ergueu a espada diante do portão.",
            trechoParaOServidor("  Ned ergueu a espada\ndiante   do portão.\n"),
        )
    }

    @Test
    fun trecho_pequeno_demais_ou_vazio_nao_vai() {
        assertNull(trechoParaOServidor(null))
        assertNull(trechoParaOServidor("   "))
        assertNull(trechoParaOServidor("Ned"))
        assertEquals(MINIMO_DO_TRECHO_DA_CENA, trechoParaOServidor("1234567890")?.length)
    }

    @Test
    fun a_legenda_poe_o_trecho_entre_aspas_e_some_quando_nao_ha() {
        assertEquals("“Ned ergueu a espada.”", legendaDoTrecho("  Ned ergueu a espada. "))
        assertNull(legendaDoTrecho(null))
        assertNull(legendaDoTrecho("   "))
    }

    @Test
    fun a_cena_sugerida_traz_o_trecho_e_servidor_antigo_sem_o_campo_vira_nulo() {
        val json = Json { ignoreUnknownKeys = true }

        val nova = json.decodeFromString<CenaSugerida>("""{"id":1,"titulo":"A","trecho":"Ned ergueu a espada."}""")
        val antiga = json.decodeFromString<CenaSugerida>("""{"id":1,"titulo":"A"}""")

        assertEquals("Ned ergueu a espada.", nova.trecho)
        assertNull(antiga.trecho)
    }
}
