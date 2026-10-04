package com.allan.imagineer.dados

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A aparência da leitura (RL1 a RL8): limites, o que se guarda e as cores dos temas. */
class PreferenciasDeLeituraTest {

    @Test
    fun o_padrao_e_o_de_hoje() {
        val p = PreferenciasDeLeitura()
        assertEquals(100, p.tamanho)
        assertEquals(1.6f, p.entrelinha, 0.001f)
        assertEquals(16, p.margem.dp)
        assertEquals(TemaDeLeitura.DO_APLICATIVO, p.tema)
        assertTrue(p.telaAcesa)
        assertNull(p.brilho)
    }

    @Test
    fun a_letra_anda_de_dez_em_dez_e_para_nos_limites() {
        var p = PreferenciasDeLeitura()
        repeat(20) { p = p.maisLetra() }
        assertEquals(220, p.tamanho)
        repeat(30) { p = p.menosLetra() }
        assertEquals(70, p.tamanho)
        assertEquals(110, PreferenciasDeLeitura().maisLetra().tamanho)
    }

    @Test
    fun a_entrelinha_anda_de_um_decimo_e_para_nos_limites() {
        var p = PreferenciasDeLeitura()
        repeat(20) { p = p.maisEntrelinha() }
        assertEquals(2.2f, p.entrelinha, 0.001f)
        repeat(30) { p = p.menosEntrelinha() }
        assertEquals(1.3f, p.entrelinha, 0.001f)
        assertEquals(1.7f, PreferenciasDeLeitura().maisEntrelinha().entrelinha, 0.001f)
    }

    @Test
    fun valores_fora_do_intervalo_voltam_para_dentro() {
        val estranha = PreferenciasDeLeitura(tamanho = 999, entrelinha = 9f, brilho = 5f).normalizada()
        assertEquals(220, estranha.tamanho)
        assertEquals(2.2f, estranha.entrelinha, 0.001f)
        assertEquals(1f, estranha.brilho!!, 0.001f)
        assertEquals(0.05f, PreferenciasDeLeitura(brilho = 0f).normalizada().brilho!!, 0.001f)
        assertEquals(70, PreferenciasDeLeitura(tamanho = 73).normalizada().tamanho - 0)  // 73 cai no degrau de baixo
    }

    @Test
    fun guarda_e_le_de_volta_sem_perder_nada() {
        val original = PreferenciasDeLeitura(
            tamanho = 130, familia = FamiliaDeLeitura.COM_SERIFA, entrelinha = 1.9f, margem = MargemDeLeitura.LARGA,
            justificado = true, tema = TemaDeLeitura.SEPIA, telaAcesa = false, brilho = 0.4f,
        )

        assertEquals(original, PreferenciasDeLeitura.deTexto(original.paraTexto()))
    }

    @Test
    fun texto_vazio_corrompido_ou_de_versao_antiga_cai_no_padrao_sem_quebrar() {
        assertEquals(PreferenciasDeLeitura(), PreferenciasDeLeitura.deTexto(null))
        assertEquals(PreferenciasDeLeitura(), PreferenciasDeLeitura.deTexto(""))
        assertEquals(PreferenciasDeLeitura(), PreferenciasDeLeitura.deTexto("{isso nao e json"))
        // Um campo novo que este app não conhece, e um valor de enum que não existe mais, não derrubam a leitura.
        val lida = PreferenciasDeLeitura.deTexto("""{"tamanho":120,"campoNovo":1,"tema":"NAO_EXISTE_MAIS"}""")
        assertEquals(120, lida.tamanho)
        assertEquals(TemaDeLeitura.DO_APLICATIVO, lida.tema)
    }

    @Test
    fun o_tamanho_da_letra_segue_o_percentual() {
        assertEquals(20f, tamanhoDaLetra(16f, PreferenciasDeLeitura(tamanho = 125)), 0.001f)
        assertEquals(16f, tamanhoDaLetra(16f, PreferenciasDeLeitura()), 0.001f)
    }

    @Test
    fun cada_tema_tem_fundo_e_texto_diferentes_e_o_do_aplicativo_nao_troca_nada() {
        assertNull(coresDoTemaDeLeitura(TemaDeLeitura.DO_APLICATIVO))
        val fundos = TemaDeLeitura.entries.mapNotNull { coresDoTemaDeLeitura(it)?.first }
        assertEquals(4, fundos.distinct().size)
        TemaDeLeitura.entries.mapNotNull { coresDoTemaDeLeitura(it) }.forEach { (fundo, texto) -> assertNotEquals(fundo, texto) }
        assertEquals(Color(0xFFF4ECD8), coresDoTemaDeLeitura(TemaDeLeitura.SEPIA)!!.first)
    }

    @Test
    fun o_esquema_de_leitura_troca_fundo_e_texto_e_mantem_o_resto() {
        val base = lightColorScheme(primary = Color(0xFFFFB300))

        val sepia = esquemaDeLeitura(base, TemaDeLeitura.SEPIA)

        assertEquals(Color(0xFFF4ECD8), sepia.background)
        assertEquals(Color(0xFF5B4636), sepia.onSurface)
        assertEquals(base.primary, sepia.primary)  // a cor de destaque da pessoa continua
        assertEquals(base, esquemaDeLeitura(base, TemaDeLeitura.DO_APLICATIVO))
    }
}
