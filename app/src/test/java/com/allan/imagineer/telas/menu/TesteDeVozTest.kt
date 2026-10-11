package com.allan.imagineer.telas.menu

import com.allan.imagineer.rede.AmostraDeVoz
import com.allan.imagineer.rede.ConfiguracaoAtual
import com.allan.imagineer.rede.EstadoDoAudio
import com.allan.imagineer.rede.EstimativaDaNarracao
import com.allan.imagineer.rede.EstimativaDoTesteDeVoz
import com.allan.imagineer.rede.ModeloDeNarracao
import com.allan.imagineer.rede.RepositorioDeModelos
import com.allan.imagineer.rede.RepositorioDeNarracao
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

private fun estimativaDoTeste(custo: JsonPrimitive? = JsonPrimitive("0.0016")) = EstimativaDoTesteDeVoz(
    frase = "Esta é a voz que vai narrar os seus livros.", caracteres = 44, custo_estimado = custo, modelo = "microsoft/mai-voice-2.1-flash", voz = "pt-BR-Luana",
)

private class NarracaoDoTeste : RepositorioDeNarracao {
    var estimativa: ResultadoDaChamada<EstimativaDoTesteDeVoz> = ResultadoDaChamada.Sucesso(estimativaDoTeste())
    var amostra: ResultadoDaChamada<AmostraDeVoz> = ResultadoDaChamada.Sucesso(AmostraDeVoz(byteArrayOf(1, 2, 3), "0.0016"))
    val estimativasPedidas = mutableListOf<Pair<String?, String?>>()
    val testesPedidos = mutableListOf<Pair<String?, String?>>()

    override suspend fun estimativaDoTeste(modelo: String?, voz: String?): ResultadoDaChamada<EstimativaDoTesteDeVoz> {
        estimativasPedidas += modelo to voz
        return estimativa
    }

    override suspend fun testarVoz(modelo: String?, voz: String?): ResultadoDaChamada<AmostraDeVoz> {
        testesPedidos += modelo to voz
        return amostra
    }

    override suspend fun modelos() = ResultadoDaChamada.Sucesso(emptyList<ModeloDeNarracao>())
    override suspend fun estimativa(capituloId: Int): ResultadoDaChamada<EstimativaDaNarracao> = ResultadoDaChamada.Falha("não usado")
    override suspend fun estado(capituloId: Int): ResultadoDaChamada<EstadoDoAudio> = ResultadoDaChamada.Falha("não usado")
    override suspend fun gerar(capituloId: Int, refazer: Boolean): ResultadoDaChamada<EstadoDoAudio> = ResultadoDaChamada.Falha("não usado")
    override suspend fun apagar(capituloId: Int): ResultadoDaChamada<Unit> = ResultadoDaChamada.Falha("não usado")
}

private class ConfiguracaoParaOTeste : RepositorioDeModelos {
    val atual = ConfiguracaoAtual(
        tem_chave_api = true, origem_da_chave = "ambiente", prioridade_ia = "ECONOMIA",
        narracao_motor = "IA", modelo_narracao = "microsoft/mai-voice-2.1-flash", narracao_voz = "pt-BR-Luana",
    )
    override suspend fun configuracao() = ResultadoDaChamada.Sucesso(atual)
    override suspend fun modelosDeTexto() = ResultadoDaChamada.Falha("não usado", null)
    override suspend fun catalogoDeImagem() = ResultadoDaChamada.Falha("não usado", null)
    override suspend fun informarPreco(modelo: String, preco: String?) = ResultadoDaChamada.Falha("não usado", null)
    override suspend fun testarImagem(modelo: String) = ResultadoDaChamada.Falha("não usado", null)
    override suspend fun definirModelosDeImagem(modelos: List<String>) = ResultadoDaChamada.Falha("não usado", null)
    override suspend fun escolher(campo: String, modelo: String?) = ResultadoDaChamada.Falha("não usado", null)
    override suspend fun gravar(campos: Map<String, String>) = ResultadoDaChamada.Sucesso(atual)
}

/** O teste de voz da tela de Narração (item 7.5g, AU4). */
@OptIn(ExperimentalCoroutinesApi::class)
class TesteDeVozTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private suspend fun vmPronto(falso: NarracaoDoTeste): NarracaoViewModel {
        val vm = NarracaoViewModel(ConfiguracaoParaOTeste(), falso)
        vm.carregar()
        return vm
    }

    @Test
    fun AU4_so_se_testa_a_voz_de_ia_com_um_modelo_escolhido() {
        assertTrue(podeTestarAVoz(NarracaoEdicao(motor = "IA", modelo = "a/b")))
        assertFalse(podeTestarAVoz(NarracaoEdicao(motor = "APARELHO", modelo = "a/b")))
        assertFalse(podeTestarAVoz(NarracaoEdicao(motor = "IA", modelo = "  ")))
    }

    @Test
    fun AU4_abrir_le_a_estimativa_com_o_modelo_e_a_voz_do_formulario_sem_gastar() = runTest {
        val falso = NarracaoDoTeste()
        val vm = vmPronto(falso); advanceUntilIdle()
        vm.mudar(vm.estado.value.edicao.copy(modelo = "fish-audio/s2.1-pro-free:free", voz = "x")) // ainda não salvou

        vm.abrirTesteDeVoz()
        assertEquals(TesteDeVoz.Lendo, vm.estado.value.teste)
        advanceUntilIdle()

        assertEquals(listOf("fish-audio/s2.1-pro-free:free" to "x"), falso.estimativasPedidas)
        assertTrue(vm.estado.value.teste is TesteDeVoz.Confirmar)
        assertTrue("ler o custo não gera nada", falso.testesPedidos.isEmpty())
    }

    @Test
    fun AU4_ouvir_pede_o_mp3_so_depois_da_confirmacao_e_entrega_a_amostra() = runTest {
        val falso = NarracaoDoTeste()
        val vm = vmPronto(falso); advanceUntilIdle()
        vm.abrirTesteDeVoz(); advanceUntilIdle()

        vm.gerarTesteDeVoz()
        assertTrue(vm.estado.value.teste is TesteDeVoz.Gerando)
        advanceUntilIdle()

        assertEquals(listOf("microsoft/mai-voice-2.1-flash" to "pt-BR-Luana"), falso.testesPedidos)
        val tocando = vm.estado.value.teste as TesteDeVoz.Tocando
        assertEquals(3, tocando.amostra.mp3.size)
        assertEquals("0.0016", tocando.amostra.custoEstimado)
    }

    @Test
    fun AU4_gerar_sem_ter_aberto_o_dialogo_nao_faz_nada() = runTest {
        val falso = NarracaoDoTeste()
        val vm = vmPronto(falso); advanceUntilIdle()

        vm.gerarTesteDeVoz(); advanceUntilIdle()

        assertEquals(TesteDeVoz.Fechado, vm.estado.value.teste)
        assertTrue(falso.testesPedidos.isEmpty())
    }

    @Test
    fun AU4_a_recusa_do_servidor_na_estimativa_ou_no_audio_vira_falha_com_o_motivo() = runTest {
        val falso = NarracaoDoTeste().also { it.estimativa = ResultadoDaChamada.Falha("Nenhum modelo de narração foi escolhido.", 422) }
        val vm = vmPronto(falso); advanceUntilIdle()
        vm.abrirTesteDeVoz(); advanceUntilIdle()
        assertEquals(TesteDeVoz.Falhou("Nenhum modelo de narração foi escolhido."), vm.estado.value.teste)

        falso.estimativa = ResultadoDaChamada.Sucesso(estimativaDoTeste())
        falso.amostra = ResultadoDaChamada.Falha("Não há chave do OpenRouter para narrar.", 422)
        vm.abrirTesteDeVoz(); advanceUntilIdle()
        vm.gerarTesteDeVoz(); advanceUntilIdle()
        assertEquals(TesteDeVoz.Falhou("Não há chave do OpenRouter para narrar."), vm.estado.value.teste)
    }

    @Test
    fun AU4_um_mp3_vazio_vira_falha_e_nao_toca_nada() = runTest {
        val falso = NarracaoDoTeste().also { it.amostra = ResultadoDaChamada.Sucesso(AmostraDeVoz(ByteArray(0), null)) }
        val vm = vmPronto(falso); advanceUntilIdle()
        vm.abrirTesteDeVoz(); advanceUntilIdle()

        vm.gerarTesteDeVoz(); advanceUntilIdle()

        assertEquals(TesteDeVoz.Falhou("O servidor não mandou áudio."), vm.estado.value.teste)
    }

    @Test
    fun AU4_fechar_enquanto_gera_espera_mas_nos_outros_passos_fecha() = runTest {
        val falso = NarracaoDoTeste()
        val vm = vmPronto(falso); advanceUntilIdle()
        vm.abrirTesteDeVoz(); advanceUntilIdle()
        vm.fecharTesteDeVoz()
        assertEquals(TesteDeVoz.Fechado, vm.estado.value.teste)

        vm.abrirTesteDeVoz(); advanceUntilIdle()
        vm.gerarTesteDeVoz() // fica Gerando até o próximo passo do agendador
        vm.fecharTesteDeVoz()
        assertTrue("já gastou: espera o resultado", vm.estado.value.teste is TesteDeVoz.Gerando)
        advanceUntilIdle()
        assertTrue(vm.estado.value.teste is TesteDeVoz.Tocando)
        vm.fecharTesteDeVoz()
        assertEquals(TesteDeVoz.Fechado, vm.estado.value.teste)
    }

    @Test
    fun AU4_o_custo_do_teste_diz_gratis_valor_ou_sem_estimativa() {
        assertEquals("Custa cerca de US$ 0,0016.", descreverCustoDoTesteDeVoz(estimativaDoTeste(JsonPrimitive("0.0016"))))
        assertEquals("Grátis: este modelo não cobra.", descreverCustoDoTesteDeVoz(estimativaDoTeste(JsonPrimitive("0"))))
        assertTrue(descreverCustoDoTesteDeVoz(estimativaDoTeste(null)).startsWith("Sem estimativa de custo"))
    }

    @Test
    fun AU4_o_custo_que_veio_no_cabecalho_da_amostra() {
        assertEquals("Custo estimado: US$ 0,0016.", descreverCustoDaAmostra(AmostraDeVoz(byteArrayOf(1), "0.0016")))
        assertEquals("Não custou nada.", descreverCustoDaAmostra(AmostraDeVoz(byteArrayOf(1), "0")))
        assertNull(descreverCustoDaAmostra(AmostraDeVoz(byteArrayOf(1), null)))
        assertNull(descreverCustoDaAmostra(AmostraDeVoz(byteArrayOf(1), "abc")))
    }
}
