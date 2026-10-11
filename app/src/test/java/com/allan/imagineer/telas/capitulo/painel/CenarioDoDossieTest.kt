package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.rede.CampoDoCenario
import com.allan.imagineer.rede.CenarioDaCena
import com.allan.imagineer.rede.DossieDaCena
import com.allan.imagineer.rede.DossieParaGravar
import com.allan.imagineer.rede.PresenteDaCena
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import com.allan.imagineer.rede.jsonDoImagineer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val cenarioDoServidor = CenarioDaCena(
    ambiente = CampoDoCenario("a taverna do Corvo Torto"),
    clima = CampoDoCenario("chuva fina lá fora", "anterior"),
    iluminacao = CampoDoCenario("uma vela no castiçal"),
    lente = CampoDoCenario("50 mm, fundo desfocado", "sugerido"),
)
private val dossieComCenario = DossieDaCena(
    presentes = listOf(PresenteDaCena(nome = "Auri", tipo = "PESSOA")),
    onde = "a taverna do Corvo Torto",
    cenario = cenarioDoServidor,
)

/** O bloco Cenário de "O que vai aparecer" (item 4.11, AF8): o que a tela mostra e o que vai ao servidor. */
@OptIn(ExperimentalCoroutinesApi::class)
class CenarioDoDossieTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    @Test
    fun `AF8 o rascunho traz o texto dos sete campos e vazio onde o servidor nao leu`() {
        val rascunho = rascunhoDe(dossieComCenario)

        assertEquals(7, rascunho.cenario.size)
        assertEquals("chuva fina lá fora", rascunho.cenario["clima"])
        assertEquals("", rascunho.cenario["atmosfera"])
        assertTrue(rascunhoDe(null).cenario.values.all { it.isEmpty() })
    }

    @Test
    fun `AF8 o bloco so aparece se o servidor leu um cenario`() {
        assertTrue(temCenario(dossieComCenario))
        assertFalse(temCenario(dossieComCenario.copy(cenario = null)))
        assertFalse(temCenario(null))
    }

    @Test
    fun `AF8 a etiqueta diz de onde veio e passa a editado por voce quando o texto muda`() {
        val rascunho = rascunhoDe(dossieComCenario)

        assertEquals("do texto", etiquetaDoCampoDoCenario(dossieComCenario, rascunho, "ambiente"))
        assertEquals("herdado do anterior", etiquetaDoCampoDoCenario(dossieComCenario, rascunho, "clima"))
        assertEquals("sugerido", etiquetaDoCampoDoCenario(dossieComCenario, rascunho, "lente"))
        assertNull(etiquetaDoCampoDoCenario(dossieComCenario, rascunho, "atmosfera")) // em branco: sem etiqueta
        val editado = rascunho.copy(cenario = rascunho.cenario + ("clima" to "sol forte"))
        assertEquals("editado por você", etiquetaDoCampoDoCenario(dossieComCenario, editado, "clima"))
    }

    @Test
    fun `AF8 o PUT leva cada campo com a origem do servidor e deixa de fora o que estava em branco`() {
        val corpo = rascunhoDe(dossieComCenario).paraGravar(dossieComCenario)

        assertEquals(CampoDoCenario("chuva fina lá fora", "anterior"), corpo.cenario?.clima)
        assertEquals(CampoDoCenario("50 mm, fundo desfocado", "sugerido"), corpo.cenario?.lente)
        assertNull(corpo.cenario?.atmosfera) // nada no servidor e nada no rascunho: não vai
    }

    @Test
    fun `AF8 apagar um campo que o servidor tinha manda texto nulo e escrever num campo vazio manda o texto`() {
        val rascunho = rascunhoDe(dossieComCenario).copy(
            cenario = rascunhoDe(dossieComCenario).cenario + ("clima" to "  ") + ("atmosfera" to "tons de âmbar"),
        )

        val corpo = rascunho.paraGravar(dossieComCenario)

        assertEquals(CampoDoCenario(null), corpo.cenario?.clima)
        assertEquals("tons de âmbar", corpo.cenario?.atmosfera?.texto)
    }

    @Test
    fun `AF8 servidor sem cenario nao recebe cenario`() {
        val semCenario = dossieComCenario.copy(cenario = null)

        assertNull(rascunhoDe(semCenario).paraGravar(semCenario).cenario)
    }

    @Test
    fun `AF8 o JSON do servidor com cenario e sem cenario le dos dois jeitos`() {
        val com = jsonDoImagineer.decodeFromString<DossieDaCena>(
            """{"presentes":[],"cenario":{"ambiente":{"texto":"um pátio","origem":"texto"},"clima":{"texto":null,"origem":"texto"}}}""",
        )
        val sem = jsonDoImagineer.decodeFromString<DossieDaCena>("""{"presentes":[],"onde":"um pátio"}""")

        assertEquals("um pátio", com.cenario?.ambiente?.texto)
        assertNull(com.cenario?.clima?.texto)
        assertNull(sem.cenario)
    }

    @Test
    fun `AF8 so os campos do cenario com texto vao no corpo do PUT`() {
        val json = jsonDoImagineer.encodeToString(DossieParaGravar.serializer(), rascunhoDe(dossieComCenario).paraGravar(dossieComCenario))

        assertTrue(json.contains("\"clima\":{\"texto\":\"chuva fina lá fora\",\"origem\":\"anterior\"}"))
        assertFalse(json.contains("atmosfera"))
    }

    @Test
    fun `AF8 a ficha do estado mostra as linhas do cenario com a origem`() {
        val linhas = linhasDoCenario(cenarioDoServidor)

        assertEquals("Ambiente: a taverna do Corvo Torto", linhas.first())
        assertTrue(linhas.contains("Clima: chuva fina lá fora (herdado do anterior)"))
        assertTrue(linhas.contains("Lente e profundidade de campo: 50 mm, fundo desfocado (sugerido)"))
        assertEquals(emptyList<String>(), linhasDoCenario(null))
        assertEquals(emptyList<String>(), linhasDoCenario(CenarioDaCena()))
    }

    // --- O ViewModel: editar o campo e confirmar manda o cenario ---

    @Test
    fun `AF8 editar um campo e confirmar manda o texto novo e mantem a origem dos outros`() = runTest {
        val repositorio = SugestoesFalso(leitura = ResultadoDaChamada.Sucesso(SugestoesDeCapitulo(gerado_em = "2026-10-08T10:00:00")))
        val prompts = PromptsFalso().also { it.dossieGuardado = ResultadoDaChamada.Sucesso(dossieComCenario) }
        val vm = PainelDeIaViewModel(5, repositorio, ElementosFalso(), prompts, ServicoDeAnalises(repositorio, CoroutineScope(SupervisorJob() + Dispatchers.Main), prompts))
        vm.carregarDossie(70); advanceUntilIdle()

        vm.mudarCampoDoCenario(70, "clima", "sol forte")
        vm.confirmarDossie(70); advanceUntilIdle()

        val enviado = prompts.dossiesConfirmados.single().second.cenario
        assertEquals(CampoDoCenario("sol forte", "anterior"), enviado?.clima) // o servidor decide que virou palavra da pessoa
        assertEquals("a taverna do Corvo Torto", enviado?.ambiente?.texto)
    }
}
