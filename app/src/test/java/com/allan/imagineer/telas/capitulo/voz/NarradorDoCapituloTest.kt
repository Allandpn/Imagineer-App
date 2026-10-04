package com.allan.imagineer.telas.capitulo.voz

import com.allan.imagineer.dados.PreferenciasDeLeitura
import com.allan.imagineer.telas.capitulo.ParagrafoDoTexto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class MotorFalso : MotorDeVoz {
    val falas = mutableListOf<Pair<String, String>>()
    var paradas = 0
    var velocidade = 1f
    var encerrado = false
    override fun falar(id: String, texto: String) { falas += id to texto }
    override fun parar() { paradas++ }
    override fun definirVelocidade(velocidade: Float) { this.velocidade = velocidade }
    override fun encerrar() { encerrado = true }
    val ultimoId get() = falas.last().first
}

private val PARAGRAFOS = listOf(
    ParagrafoDoTexto(0, "Primeiro parágrafo."),
    ParagrafoDoTexto(21, "Segundo parágrafo."),
    ParagrafoDoTexto(41, "Terceiro."),
)

/** Ouvir o capítulo (RL18): o narrador, as regras de dividir o texto e a velocidade. */
class NarradorDoCapituloTest {

    private fun narrador(motor: MotorFalso = MotorFalso(), paragrafos: List<ParagrafoDoTexto> = PARAGRAFOS) = NarradorDoCapitulo(motor, paragrafos) to motor

    // ---- o narrador

    @Test
    fun comeca_pelo_paragrafo_que_contem_a_posicao() {
        val (n, motor) = narrador()

        n.iniciarEm(30)  // dentro do segundo

        assertEquals(1, n.estado.value.paragrafo)
        assertEquals("Segundo parágrafo.", motor.falas.last().second)
        assertEquals(SituacaoDaNarracao.FALANDO, n.estado.value.situacao)
    }

    @Test
    fun ao_terminar_um_paragrafo_fala_o_seguinte_e_ao_fim_do_capitulo_para() {
        val (n, motor) = narrador()
        n.iniciar(0)

        n.aoTerminarDeFalar(motor.ultimoId)
        assertEquals(1, n.estado.value.paragrafo)
        n.aoTerminarDeFalar(motor.ultimoId)
        assertEquals(2, n.estado.value.paragrafo)
        n.aoTerminarDeFalar(motor.ultimoId)

        assertEquals(SituacaoDaNarracao.PARADA, n.estado.value.situacao)
        assertEquals(3, motor.falas.size)  // não inventa um quarto parágrafo
    }

    @Test
    fun pausar_para_a_voz_e_continuar_recomeca_do_mesmo_paragrafo() {
        val (n, motor) = narrador()
        n.iniciar(1)

        n.pausar()
        assertEquals(SituacaoDaNarracao.PAUSADA, n.estado.value.situacao)
        assertEquals(1, motor.paradas)

        n.retomar()

        assertEquals(SituacaoDaNarracao.FALANDO, n.estado.value.situacao)
        assertEquals(1, n.estado.value.paragrafo)
        assertEquals("Segundo parágrafo.", motor.falas.last().second)
    }

    @Test
    fun o_aviso_de_uma_fala_ja_interrompida_e_ignorado() {
        val (n, motor) = narrador()
        n.iniciar(0)
        val idAntigo = motor.ultimoId

        n.proximo()  // interrompe a fala do primeiro e vai para o segundo
        n.aoTerminarDeFalar(idAntigo)  // o "terminei" velho chega atrasado

        assertEquals(1, n.estado.value.paragrafo)  // não pulou para o terceiro
        assertEquals(2, motor.falas.size)
    }

    @Test
    fun aviso_depois_de_pausar_ou_parar_nao_faz_nada() {
        val (n, motor) = narrador()
        n.iniciar(0)
        val id = motor.ultimoId
        n.pausar()

        n.aoTerminarDeFalar(id)

        assertEquals(0, n.estado.value.paragrafo)
        assertEquals(SituacaoDaNarracao.PAUSADA, n.estado.value.situacao)
    }

    @Test
    fun proximo_e_anterior_andam_e_param_nas_pontas() {
        val (n, _) = narrador()
        n.iniciar(0)

        n.anterior()
        assertEquals(0, n.estado.value.paragrafo)
        n.proximo(); n.proximo(); n.proximo()
        assertEquals(2, n.estado.value.paragrafo)
    }

    @Test
    fun pausado_proximo_so_muda_o_lugar_sem_falar() {
        val (n, motor) = narrador()
        n.iniciar(0)
        n.pausar()
        val falasAntes = motor.falas.size

        n.proximo()

        assertEquals(1, n.estado.value.paragrafo)
        assertEquals(falasAntes, motor.falas.size)
        assertEquals(SituacaoDaNarracao.PAUSADA, n.estado.value.situacao)
    }

    @Test
    fun capitulo_sem_texto_nao_comeca() {
        val (n, motor) = narrador(paragrafos = emptyList())

        n.iniciar(0)

        assertEquals(SituacaoDaNarracao.PARADA, n.estado.value.situacao)
        assertTrue(motor.falas.isEmpty())
    }

    @Test
    fun paragrafo_so_de_simbolos_e_pulado() {
        val (n, motor) = narrador(paragrafos = listOf(ParagrafoDoTexto(0, "* * *"), ParagrafoDoTexto(6, "Depois.")))

        n.iniciar(0)

        assertEquals(1, n.estado.value.paragrafo)
        assertEquals("Depois.", motor.falas.single().second)
    }

    @Test
    fun paragrafo_grande_e_falado_em_partes_na_ordem_antes_de_passar_ao_seguinte() {
        val grande = "Frase um. " + "b".repeat(3000) + ". Frase três. " + "c".repeat(3000) + "."
        val (n, motor) = narrador(paragrafos = listOf(ParagrafoDoTexto(0, grande), ParagrafoDoTexto(10_000, "Fim.")))

        n.iniciar(0)
        val partes = dividirParaFalar(grande).size
        repeat(partes - 1) { n.aoTerminarDeFalar(motor.ultimoId) }
        assertEquals(0, n.estado.value.paragrafo)  // ainda no mesmo parágrafo, nas partes
        n.aoTerminarDeFalar(motor.ultimoId)

        assertEquals(1, n.estado.value.paragrafo)
        assertTrue(partes >= 2)
    }

    @Test
    fun a_falha_da_voz_para_a_narracao() {
        val (n, _) = narrador()
        n.iniciar(0)

        n.aoFalhar()

        assertEquals(SituacaoDaNarracao.PARADA, n.estado.value.situacao)
    }

    @Test
    fun encerrar_libera_a_voz() {
        val (n, motor) = narrador()
        n.iniciar(0)

        n.encerrar()

        assertTrue(motor.encerrado)
    }

    // ---- a velocidade

    @Test
    fun a_velocidade_vai_de_um_quarto_em_um_quarto_dentro_dos_limites() {
        assertEquals(1.25f, maisRapida(1f), 0.001f)
        assertEquals(2f, maisRapida(2f), 0.001f)
        assertEquals(0.75f, maisLenta(1f), 0.001f)
        assertEquals(0.5f, maisLenta(0.5f), 0.001f)
    }

    @Test
    fun mudar_a_velocidade_vale_na_hora_e_recomeca_o_paragrafo_que_esta_sendo_falado() {
        val (n, motor) = narrador()
        n.iniciar(0)

        n.mudarVelocidade(1.5f)

        assertEquals(1.5f, motor.velocidade, 0.001f)
        assertEquals(1.5f, n.estado.value.velocidade, 0.001f)
        assertEquals(2, motor.falas.size)  // o parágrafo foi falado de novo, já na velocidade nova
        n.mudarVelocidade(9f)
        assertEquals(2f, n.estado.value.velocidade, 0.001f)
    }

    @Test
    fun descreve_a_velocidade() {
        assertEquals("1×", descreverVelocidade(1f))
        assertEquals("1,25×", descreverVelocidade(1.25f))
        assertEquals("0,5×", descreverVelocidade(0.5f))
        assertEquals("2×", descreverVelocidade(2f))
    }

    @Test
    fun a_velocidade_guardada_fica_dentro_dos_limites() {
        assertEquals(2f, PreferenciasDeLeitura(velocidadeDaVoz = 9f).normalizada().velocidadeDaVoz, 0.001f)
        assertEquals(1f, PreferenciasDeLeitura().velocidadeDaVoz, 0.001f)
    }

    // ---- dividir o texto para a voz

    @Test
    fun paragrafo_que_cabe_vai_inteiro_e_com_espacos_arrumados() {
        assertEquals(listOf("Olá, tudo bem?"), dividirParaFalar("  Olá,\n tudo   bem?  "))
    }

    @Test
    fun sem_letra_nem_numero_nao_ha_o_que_falar() {
        assertTrue(dividirParaFalar("   ").isEmpty())
        assertTrue(dividirParaFalar("* * * —").isEmpty())
        assertEquals(listOf("1984"), dividirParaFalar("1984"))
    }

    @Test
    fun corta_no_fim_de_uma_frase_quando_nao_cabe() {
        val partes = dividirParaFalar("Primeira frase aqui. Segunda frase aqui. Terceira frase aqui.", maximo = 45)

        assertEquals(listOf("Primeira frase aqui. Segunda frase aqui.", "Terceira frase aqui."), partes)
    }

    @Test
    fun frase_maior_que_o_limite_corta_no_ultimo_espaco_e_nao_perde_texto() {
        val texto = (1..40).joinToString(" ") { "palavra$it" }

        val partes = dividirParaFalar(texto, maximo = 100)

        assertTrue(partes.all { it.length <= 100 })
        assertEquals(texto, partes.joinToString(" "))
    }
}
