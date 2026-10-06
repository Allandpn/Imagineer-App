package com.allan.imagineer.telas.capitulo.voz

import com.allan.imagineer.rede.ConfiguracaoAtual
import com.allan.imagineer.rede.EstadoDoAudio
import com.allan.imagineer.rede.EstimativaDaNarracao
import com.allan.imagineer.rede.ModeloDeNarracao
import com.allan.imagineer.rede.RepositorioDeNarracao
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SituacaoDoAudio
import com.allan.imagineer.rede.decimalOuNulo
import com.allan.imagineer.rede.descreverCustoEstimado
import com.allan.imagineer.rede.descreverEstimativa
import com.allan.imagineer.rede.descreverPrecoDoModelo
import com.allan.imagineer.rede.dolares
import com.allan.imagineer.rede.enderecoDaNarracao
import com.allan.imagineer.rede.jsonDoImagineer
import com.allan.imagineer.rede.narracaoPorIaDisponivel
import com.allan.imagineer.telas.menu.NarracaoEdicao
import com.allan.imagineer.telas.menu.edicaoComOModelo
import com.allan.imagineer.telas.menu.mudouANarracao
import com.allan.imagineer.telas.menu.paraNarracaoEdicao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun estimativa(custo: JsonPrimitive? = JsonPrimitive("0.2100"), caracteres: Int = 14_000, minutos: Double = 15.6) =
    EstimativaDaNarracao(caracteres = caracteres, minutos = minutos, custo_estimado = custo, modelo = "microsoft/mai-voice-2.1-flash", voz = "pt-BR-Luana:MAI-Voice-2.1-Flash")

private fun audio(situacao: String, erro: String? = null) = EstadoDoAudio(situacao = situacao, erro = erro)

private class NarracaoFalsa : RepositorioDeNarracao {
    var estados = mutableListOf<ResultadoDaChamada<EstadoDoAudio>>(ResultadoDaChamada.Sucesso(EstadoDoAudio(situacao = SituacaoDoAudio.NAO_GERADO)))
    var estimativaLida: ResultadoDaChamada<EstimativaDaNarracao> = ResultadoDaChamada.Sucesso(estimativa())
    var geracao: ResultadoDaChamada<EstadoDoAudio> = ResultadoDaChamada.Sucesso(audio(SituacaoDoAudio.GERANDO))
    var apagamento: ResultadoDaChamada<Unit> = ResultadoDaChamada.Sucesso(Unit)
    val pedidosDeGeracao = mutableListOf<Boolean>()
    var consultas = 0

    override suspend fun modelos(): ResultadoDaChamada<List<ModeloDeNarracao>> = ResultadoDaChamada.Sucesso(emptyList())
    override suspend fun estimativa(capituloId: Int) = estimativaLida

    override suspend fun estado(capituloId: Int): ResultadoDaChamada<EstadoDoAudio> {
        consultas++
        return if (estados.size > 1) estados.removeAt(0) else estados.first()
    }

    override suspend fun gerar(capituloId: Int, refazer: Boolean): ResultadoDaChamada<EstadoDoAudio> {
        pedidosDeGeracao += refazer
        return geracao
    }

    override suspend fun apagar(capituloId: Int) = apagamento
}

/** As regras da narração por IA (AN1, AN3) e o que o servidor devolve. */
class RegrasDaNarracaoPorIaTest {

    @Test
    fun o_decimal_do_servidor_chega_como_texto_ou_como_numero() {
        assertEquals(0.21, decimalOuNulo(JsonPrimitive("0.2100"))!!, 0.00001)
        assertEquals(0.21, decimalOuNulo(JsonPrimitive(0.21))!!, 0.00001)
        assertNull(decimalOuNulo(null))
        assertNull(decimalOuNulo(kotlinx.serialization.json.JsonNull))
        assertNull(decimalOuNulo(JsonPrimitive("abc")))
    }

    @Test
    fun a_estimativa_diz_caracteres_minutos_e_custo() {
        assertEquals("14.000 caracteres · cerca de 16 min · ≈ US$ 0,21", descreverEstimativa(estimativa()))
        assertEquals("14.000 caracteres · cerca de 16 min · grátis", descreverEstimativa(estimativa(custo = JsonPrimitive("0"))))
        assertEquals("14.000 caracteres · cerca de 16 min · sem estimativa de custo", descreverEstimativa(estimativa(custo = null)))
        assertEquals("300 caracteres · cerca de 1 min · ≈ US$ 0,0090", descreverEstimativa(estimativa(JsonPrimitive("0.0090"), caracteres = 300, minutos = 0.3)))
    }

    @Test
    fun dinheiro_pequeno_ganha_quatro_casas() {
        assertEquals("US$ 0,21", dolares(0.21))
        assertEquals("US$ 0,0090", dolares(0.009))
        assertEquals("grátis", descreverCustoEstimado(JsonPrimitive(0)))
    }

    @Test
    fun o_modelo_diz_o_preco_por_capitulo_de_referencia() {
        val barato = ModeloDeNarracao("hexgrad/kokoro-82m", "Kokoro", preco_por_caractere = JsonPrimitive("0.00000062"))
        val caro = ModeloDeNarracao("microsoft/mai", "MAI", preco_por_caractere = JsonPrimitive("0.000015"))

        assertEquals("≈ US$ 0,0087 por capítulo de 14 mil caracteres", descreverPrecoDoModelo(barato))
        assertEquals("≈ US$ 0,21 por capítulo de 14 mil caracteres", descreverPrecoDoModelo(caro))
        assertEquals("grátis", descreverPrecoDoModelo(ModeloDeNarracao("a", "A", gratuito = true)))
        assertTrue(descreverPrecoDoModelo(ModeloDeNarracao("g", "Gemini")).contains("sem estimativa"))
    }

    @Test
    fun a_voz_de_ia_so_se_oferece_com_modelo_e_chave() {
        assertTrue(narracaoPorIaDisponivel("microsoft/mai-voice-2.1-flash", temChave = true))
        assertFalse(narracaoPorIaDisponivel(null, temChave = true))
        assertFalse(narracaoPorIaDisponivel("  ", temChave = true))
        assertFalse(narracaoPorIaDisponivel("microsoft/mai-voice-2.1-flash", temChave = false))
    }

    @Test
    fun o_endereco_do_audio_e_o_do_capitulo() {
        assertEquals("http://100.1.1.1:8000/capitulos/12/audio", enderecoDaNarracao("http://100.1.1.1:8000/", 12))
    }

    @Test
    fun o_json_do_servidor_decodifica_com_decimal_em_texto_e_campos_a_mais() {
        val modelo = jsonDoImagineer.decodeFromString<ModeloDeNarracao>("""{"id":"x/y","nome":"Y","vozes":["a","b"],"preco_por_caractere":"0.000015","gratuito":false,"novo":1}""")
        val est = jsonDoImagineer.decodeFromString<EstimativaDaNarracao>("""{"caracteres":900,"minutos":1.0,"custo_estimado":null,"modelo":"x/y","voz":null,"ja_gerado":true}""")
        val estado = jsonDoImagineer.decodeFromString<EstadoDoAudio>("""{"situacao":"FALHOU","erro":"Sem saldo","custo":"0.0100"}""")

        assertEquals(listOf("a", "b"), modelo.vozes)
        assertEquals(0.000015, decimalOuNulo(modelo.preco_por_caractere)!!, 1e-9)
        assertNull(decimalOuNulo(est.custo_estimado))
        assertTrue(est.ja_gerado)
        assertEquals("Sem saldo", estado.erro)
    }

    @Test
    fun a_configuracao_antiga_sem_modelo_de_narracao_nao_quebra() {
        val antiga = jsonDoImagineer.decodeFromString<ConfiguracaoAtual>("""{"tem_chave_api":true,"origem_da_chave":"ambiente","prioridade_ia":"EQUILIBRADA"}""")

        assertNull(antiga.modelo_narracao)
        assertEquals("APARELHO", antiga.paraNarracaoEdicao().motor)
    }

    @Test
    fun trocar_de_modelo_so_mantem_a_voz_que_o_novo_tambem_tem() {
        val mai = ModeloDeNarracao("microsoft/mai", "MAI", vozes = listOf("pt-BR-Luana", "pt-BR-Caio"))
        val kokoro = ModeloDeNarracao("hexgrad/kokoro", "Kokoro", vozes = listOf("pf_dora"))
        val edicao = NarracaoEdicao(motor = "IA", modelo = "microsoft/mai", voz = "pt-BR-Luana")

        assertEquals("pt-BR-Luana", edicaoComOModelo(edicao, mai).voz)
        assertEquals(NarracaoEdicao(motor = "IA", modelo = "hexgrad/kokoro", voz = ""), edicaoComOModelo(edicao, kokoro))
    }

    @Test
    fun mudar_o_motor_ou_o_modelo_e_mudar_a_configuracao() {
        val atual = ConfiguracaoAtual(tem_chave_api = true, origem_da_chave = "ambiente", prioridade_ia = "EQUILIBRADA", narracao_motor = "IA", modelo_narracao = "a/b")
        val edicao = atual.paraNarracaoEdicao()

        assertFalse(mudouANarracao(edicao, atual))
        assertTrue(mudouANarracao(edicao.copy(motor = "APARELHO"), atual))
        assertTrue(mudouANarracao(edicao.copy(modelo = "c/d"), atual))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NarracaoPorIaViewModelTest {

    private val agendador = StandardTestDispatcher()

    @Before
    fun preparar() = Dispatchers.setMain(agendador)

    @After
    fun limpar() = Dispatchers.resetMain()

    private fun vm(falso: NarracaoFalsa) = NarracaoPorIaViewModel(5, falso, intervaloDeConsultaEmMs = 1_000L)

    @Test
    fun consultar_traz_a_situacao_do_audio() = runTest {
        val falso = NarracaoFalsa().also { it.estados = mutableListOf(ResultadoDaChamada.Sucesso(audio(SituacaoDoAudio.PRONTO))) }
        val vm = vm(falso)

        vm.consultar(); advanceUntilIdle()

        assertEquals(SituacaoDoAudio.PRONTO, vm.estado.value.audio?.situacao)
    }

    @Test
    fun abrir_gerar_traz_a_estimativa_e_o_dialogo() = runTest {
        val vm = vm(NarracaoFalsa())

        vm.abrirGerar(); advanceUntilIdle()

        val dialogo = vm.estado.value.dialogo as DialogoDaNarracao.Gerar
        assertEquals(14_000, dialogo.estimativa.caracteres)
        assertFalse(dialogo.refazer)
    }

    @Test
    fun sem_modelo_escolhido_o_servidor_recusa_a_estimativa_e_a_mensagem_aparece() = runTest {
        val falso = NarracaoFalsa().also { it.estimativaLida = ResultadoDaChamada.Falha("Nenhum modelo de narração foi escolhido.", 422) }
        val vm = vm(falso)

        vm.abrirGerar(); advanceUntilIdle()

        assertEquals(DialogoDaNarracao.Fechado, vm.estado.value.dialogo)
        assertEquals("Nenhum modelo de narração foi escolhido.", vm.estado.value.aviso)
    }

    @Test
    fun gerar_pede_ao_servidor_fecha_o_dialogo_e_passa_a_acompanhar_ate_ficar_pronto() = runTest {
        val falso = NarracaoFalsa().also {
            it.estados = mutableListOf(
                ResultadoDaChamada.Sucesso(audio(SituacaoDoAudio.GERANDO)),
                ResultadoDaChamada.Sucesso(audio(SituacaoDoAudio.GERANDO)),
                ResultadoDaChamada.Sucesso(audio(SituacaoDoAudio.PRONTO)),
            )
        }
        val vm = vm(falso)
        vm.abrirGerar(); advanceUntilIdle()

        vm.gerar(refazer = false)
        advanceTimeBy(500)
        assertEquals(SituacaoDoAudio.GERANDO, vm.estado.value.audio?.situacao)
        advanceTimeBy(1_000); advanceTimeBy(1_000); advanceTimeBy(1_000)

        assertEquals(listOf(false), falso.pedidosDeGeracao)
        assertEquals(DialogoDaNarracao.Fechado, vm.estado.value.dialogo)
        assertEquals(SituacaoDoAudio.PRONTO, vm.estado.value.audio?.situacao)
        assertEquals("Narração pronta.", vm.estado.value.aviso)
    }

    @Test
    fun se_a_geracao_falha_o_motivo_do_servidor_aparece() = runTest {
        val falso = NarracaoFalsa().also {
            it.estados = mutableListOf(
                ResultadoDaChamada.Sucesso(audio(SituacaoDoAudio.GERANDO)),
                ResultadoDaChamada.Sucesso(audio(SituacaoDoAudio.FALHOU, erro = "Sem saldo no OpenRouter.")),
            )
        }
        val vm = vm(falso)

        vm.gerar(refazer = false)
        advanceTimeBy(2_500)

        assertEquals(SituacaoDoAudio.FALHOU, vm.estado.value.audio?.situacao)
        assertEquals("Sem saldo no OpenRouter.", vm.estado.value.aviso)
    }

    @Test
    fun a_recusa_ao_pedir_a_geracao_vira_aviso_e_nada_fica_ocupado() = runTest {
        val falso = NarracaoFalsa().also {
            it.estados = mutableListOf(ResultadoDaChamada.Sucesso(audio(SituacaoDoAudio.NAO_GERADO)))
            it.geracao = ResultadoDaChamada.Falha("Você já tem uma narração sendo gerada.", 409)
        }
        val vm = vm(falso)
        vm.consultar(); advanceUntilIdle()

        vm.gerar(refazer = false); advanceUntilIdle()

        assertEquals("Você já tem uma narração sendo gerada.", vm.estado.value.aviso)
        assertFalse(vm.estado.value.ocupado)
        assertEquals(SituacaoDoAudio.NAO_GERADO, vm.estado.value.audio?.situacao)
    }

    @Test
    fun uma_falha_de_rede_no_meio_do_acompanhamento_nao_o_derruba() = runTest {
        val falso = NarracaoFalsa().also {
            it.estados = mutableListOf(
                ResultadoDaChamada.Sucesso(audio(SituacaoDoAudio.GERANDO)),
                ResultadoDaChamada.Falha("Não consegui falar com o servidor."),
                ResultadoDaChamada.Sucesso(audio(SituacaoDoAudio.PRONTO)),
            )
        }
        val vm = vm(falso)

        vm.gerar(refazer = false)
        advanceTimeBy(500); advanceTimeBy(1_000); advanceTimeBy(1_000); advanceTimeBy(1_000)

        assertEquals(SituacaoDoAudio.PRONTO, vm.estado.value.audio?.situacao)
    }

    @Test
    fun refazer_manda_refazer_verdadeiro() = runTest {
        val falso = NarracaoFalsa()
        val vm = vm(falso)
        vm.abrirGerar(refazer = true); advanceUntilIdle()

        assertTrue((vm.estado.value.dialogo as DialogoDaNarracao.Gerar).refazer)
        vm.gerar(refazer = true)
        advanceTimeBy(100)

        assertEquals(listOf(true), falso.pedidosDeGeracao)
    }

    @Test
    fun apagar_volta_para_nao_gerado_e_a_falha_do_servidor_aparece() = runTest {
        val falso = NarracaoFalsa().also { it.estados = mutableListOf(ResultadoDaChamada.Sucesso(audio(SituacaoDoAudio.PRONTO))) }
        val vm = vm(falso)
        vm.consultar(); advanceUntilIdle()

        vm.pedirApagar()
        assertEquals(DialogoDaNarracao.ConfirmarApagar, vm.estado.value.dialogo)
        vm.apagar(); advanceUntilIdle()
        assertEquals(SituacaoDoAudio.NAO_GERADO, vm.estado.value.audio?.situacao)
        assertEquals("Narração apagada.", vm.estado.value.aviso)

        falso.apagamento = ResultadoDaChamada.Falha("fora do ar")
        vm.apagar(); advanceUntilIdle()
        assertEquals("fora do ar", vm.estado.value.aviso)
    }
}
