package com.allan.imagineer.telas.estatisticas

import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.rede.EstatisticasDeLeitura
import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.RepositorioDeEstatisticas
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.TempoDoDia
import com.allan.imagineer.rede.TempoDoLivro
import com.allan.imagineer.telas.capitulo.CronometroDeLeitura
import com.allan.imagineer.telas.capitulo.RegistroDeTempoDeLeitura
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val HOJE = LocalDate.of(2026, 10, 4)

private fun dia(atras: Int, segundos: Int) = TempoDoDia(HOJE.minusDays(atras.toLong()).toString(), segundos)

private fun livro(
    id: Int = 1, lidos: Int = 0, total: Int = 10, ignorados: Int = 0, caracteres: Int = 100_000,
) = LivroResumo(id, "Livro $id", null, null, "l$id.epub", "2026-01-01", total, ignorados, false, lidos, caracteres, 0)

private class ArmazenamentoEmMemoria : ArmazenamentoDeConfiguracao {
    override val urlDoServidor: Flow<String?> = MutableStateFlow(null)
    override suspend fun salvarUrlDoServidor(url: String) {}
    private val pendente = MutableStateFlow<String?>(null)
    override val tempoPendente: Flow<String?> get() = pendente
    override suspend fun salvarTempoPendente(texto: String) { pendente.value = texto }
    val guardado: String? get() = pendente.value
}

private class ServidorFalso(var resposta: (Int, String, Int) -> ResultadoDaChamada<Unit> = { _, _, _ -> ResultadoDaChamada.Sucesso(Unit) }) : RepositorioDeEstatisticas {
    val envios = mutableListOf<Triple<Int, String, Int>>()
    override suspend fun estatisticas() = ResultadoDaChamada.Sucesso(EstatisticasDeLeitura())
    override suspend fun somarTempo(livroId: Int, dia: String, segundos: Int): ResultadoDaChamada<Unit> {
        val r = resposta(livroId, dia, segundos)
        if (r is ResultadoDaChamada.Sucesso) envios += Triple(livroId, dia, segundos)
        return r
    }
}

/** As estatísticas de leitura (RL16, RL17): o cronômetro, o que fica pendente e as contas da tela. */
class EstatisticasDeLeituraTest {

    // ---- o cronômetro (RL16)

    @Test
    fun conta_o_tempo_enquanto_ha_movimento() {
        val c = CronometroDeLeitura(limiteParadoMs = 180_000)
        c.atividade(0)

        assertEquals(10_000, c.tique(10_000))   // 10 s depois da atividade, todos contam
        c.atividade(15_000)                     // movimento no meio do intervalo seguinte
        assertEquals(10_000, c.tique(20_000))   // de 10 s a 20 s, tudo dentro do limite
    }

    @Test
    fun pausa_depois_de_tres_minutos_sem_movimento() {
        val c = CronometroDeLeitura(limiteParadoMs = 180_000)
        c.atividade(0)

        // 10 minutos depois: só os 3 primeiros minutos (até o limite) contam.
        assertEquals(180_000, c.tique(600_000))
        // E o tempo parado não vai se acumulando para depois.
        assertEquals(0, c.tique(700_000))
    }

    @Test
    fun volta_a_contar_no_primeiro_movimento() {
        val c = CronometroDeLeitura(limiteParadoMs = 180_000)
        c.atividade(0)
        c.tique(600_000)

        c.atividade(700_000)

        assertEquals(5_000, c.tique(705_000))
    }

    @Test
    fun o_tempo_fora_da_tela_nao_conta_ao_retomar() {
        val c = CronometroDeLeitura(limiteParadoMs = 180_000)
        c.atividade(0)
        c.tique(60_000)

        c.retomar(1_000_000)  // ficou muito tempo fora

        assertEquals(5_000, c.tique(1_005_000))
    }

    // ---- o registro e o que fica pendente (RL16)

    @Test
    fun envia_o_tempo_ao_servidor_no_dia_de_hoje_do_aparelho() = runTest {
        val servidor = ServidorFalso()
        val registro = RegistroDeTempoDeLeitura(ArmazenamentoEmMemoria(), servidor) { HOJE }

        registro.registrar(7, 60)

        assertEquals(listOf(Triple(7, "2026-10-04", 60)), servidor.envios)
    }

    @Test
    fun sem_conexao_guarda_e_na_proxima_chance_envia_tudo_somado() = runTest {
        val armazenamento = ArmazenamentoEmMemoria()
        val servidor = ServidorFalso { _, _, _ -> ResultadoDaChamada.Falha("sem rede") }
        val registro = RegistroDeTempoDeLeitura(armazenamento, servidor) { HOJE }

        registro.registrar(7, 60)
        registro.registrar(7, 45)
        assertTrue(servidor.envios.isEmpty())
        assertTrue(armazenamento.guardado!!.contains("105"))  // 60 + 45 somados no mesmo livro e dia

        servidor.resposta = { _, _, _ -> ResultadoDaChamada.Sucesso(Unit) }
        registro.enviarPendentes()

        assertEquals(listOf(Triple(7, "2026-10-04", 105)), servidor.envios)
        assertEquals("{}", armazenamento.guardado)
    }

    @Test
    fun envia_em_pedacos_de_no_maximo_3600_segundos() = runTest {
        val servidor = ServidorFalso()
        val registro = RegistroDeTempoDeLeitura(ArmazenamentoEmMemoria(), servidor) { HOJE }

        registro.registrar(7, 7500)

        assertEquals(listOf(3600, 3600, 300), servidor.envios.map { it.third })
    }

    @Test
    fun livro_que_o_servidor_nao_tem_mais_e_descartado_em_vez_de_tentado_para_sempre() = runTest {
        val armazenamento = ArmazenamentoEmMemoria()
        val servidor = ServidorFalso { _, _, _ -> ResultadoDaChamada.Falha("não existe", 404) }
        val registro = RegistroDeTempoDeLeitura(armazenamento, servidor) { HOJE }

        registro.registrar(7, 60)

        assertEquals("{}", armazenamento.guardado)
    }

    @Test
    fun segundos_zero_ou_negativos_nao_viram_nada() = runTest {
        val servidor = ServidorFalso()
        RegistroDeTempoDeLeitura(ArmazenamentoEmMemoria(), servidor) { HOJE }.registrar(7, 0)

        assertTrue(servidor.envios.isEmpty())
    }

    // ---- o resumo (RL17)

    @Test
    fun resume_hoje_sete_dias_e_total() {
        val r = resumirALeitura(listOf(dia(0, 600), dia(3, 300), dia(9, 900)), HOJE, totalDosLivros = 5000)

        assertEquals(600, r.hoje)
        assertEquals(900, r.ultimosSeteDias)  // o do dia 9 fica de fora
        assertEquals(5000, r.total)
    }

    @Test
    fun a_sequencia_conta_dias_seguidos_a_partir_de_hoje() {
        val r = resumirALeitura(listOf(dia(0, 120), dia(1, 60), dia(2, 600), dia(4, 600)), HOJE, 0)

        assertEquals(3, r.sequenciaDeDias)  // o dia 3 faltou
    }

    @Test
    fun hoje_sem_leitura_nao_zera_a_sequencia_que_vinha_de_ontem() {
        val r = resumirALeitura(listOf(dia(1, 600), dia(2, 600)), HOJE, 0)

        assertEquals(2, r.sequenciaDeDias)
    }

    @Test
    fun dia_com_menos_de_um_minuto_nao_conta_para_a_sequencia() {
        assertEquals(0, resumirALeitura(listOf(dia(0, 59), dia(1, 59)), HOJE, 0).sequenciaDeDias)
        assertEquals(0, resumirALeitura(emptyList(), HOJE, 0).sequenciaDeDias)
    }

    // ---- a estimativa para terminar (RL17)

    @Test
    fun estima_o_fim_pelo_ritmo_do_proprio_livro() {
        // 5 de 10 capítulos = 50 000 caracteres lidos em 1000 s (50 por segundo); faltam 50 000 → 1000 s.
        val estimativa = estimarOTermino(livro(lidos = 5), segundos = 1000)

        assertEquals(EstimativaDeTermino.Faltam(1000), estimativa)
    }

    @Test
    fun com_menos_de_cinco_minutos_medidos_nao_chuta() {
        assertEquals(EstimativaDeTermino.SemDados, estimarOTermino(livro(lidos = 5), segundos = 299))
        assertEquals(EstimativaDeTermino.SemDados, estimarOTermino(livro(lidos = 0), segundos = 5000))
    }

    @Test
    fun livro_todo_lido_esta_terminado_e_capitulos_ignorados_nao_contam() {
        assertEquals(EstimativaDeTermino.Terminado, estimarOTermino(livro(lidos = 8, total = 10, ignorados = 2), segundos = 10))
    }

    @Test
    fun as_linhas_so_trazem_livros_com_leitura_o_mais_recente_primeiro() {
        val livros = listOf(livro(id = 1), livro(id = 2, lidos = 3), livro(id = 3), livro(id = 4, lidos = 1))
        val tempos = listOf(
            TempoDoLivro(3, "Livro 3", 600, 2, "2026-10-04"),
            TempoDoLivro(2, "Livro 2", 900, 3, "2026-10-01"),
        )

        val linhas = linhasPorLivro(livros, tempos)

        assertEquals(listOf(3, 2, 4), linhas.map { it.livroId })  // o 1 não tem leitura nenhuma
        assertEquals(600, linhas.first().segundos)
    }

    @Test
    fun descreve_a_duracao_em_linguagem_de_gente() {
        assertEquals("menos de 1 min", descreverDuracao(30))
        assertEquals("23 min", descreverDuracao(23 * 60 + 10))
        assertEquals("3 h 05 min", descreverDuracao(3 * 3600 + 5 * 60))
        assertEquals("Livro lido", descreverEstimativa(EstimativaDeTermino.Terminado))
        assertTrue(descreverEstimativa(EstimativaDeTermino.Faltam(7200)).contains("2 h 00 min"))
    }
}
