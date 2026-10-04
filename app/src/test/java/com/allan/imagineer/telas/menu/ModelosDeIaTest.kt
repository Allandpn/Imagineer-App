package com.allan.imagineer.telas.menu

import com.allan.imagineer.rede.ConfiguracaoAtual
import com.allan.imagineer.rede.ModeloDeTexto
import com.allan.imagineer.rede.RepositorioDeModelos
import com.allan.imagineer.rede.ResultadoDaChamada
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun config(
    extracao: String? = "g/extrai", prompt: String? = "g/prompt", suavizacao: String? = null, traducao: String? = null,
) = ConfiguracaoAtual(
    tem_chave_api = true, origem_da_chave = "ambiente", modelo_extracao = extracao, modelo_prompt = prompt,
    modelo_suavizacao = suavizacao, modelo_traducao = traducao, prioridade_ia = "ECONOMIA",
)

private val MODELOS = listOf(
    ModeloDeTexto("google/gemini-flash", "Gemini Flash", custo_saida = 0.0000004),
    ModeloDeTexto("openai/gpt-4o-mini", "GPT-4o mini", custo_saida = 0.0000006, moderado = true),
    ModeloDeTexto("livre/modelo", "Modelo Livre", gratuito = true),
)

private class ModelosFalsos(var configuracao: ConfiguracaoAtual) : RepositorioDeModelos {
    var falhaAoGravar: String? = null
    val gravados = mutableListOf<Pair<String, String?>>()

    override suspend fun configuracao() = ResultadoDaChamada.Sucesso(configuracao)
    override suspend fun modelosDeTexto() = ResultadoDaChamada.Sucesso(MODELOS)
    override suspend fun catalogoDeImagem() = ResultadoDaChamada.Sucesso(com.allan.imagineer.rede.CatalogoDeImagem())
    override suspend fun informarPreco(modelo: String, preco: String?): ResultadoDaChamada<com.allan.imagineer.rede.CatalogoDeImagem> = ResultadoDaChamada.Sucesso(com.allan.imagineer.rede.CatalogoDeImagem())
    override suspend fun testarImagem(modelo: String): ResultadoDaChamada<com.allan.imagineer.rede.TesteDeImagem> = ResultadoDaChamada.Falha("não usado")
    override suspend fun definirModelosDeImagem(modelos: List<String>): ResultadoDaChamada<ConfiguracaoAtual> = ResultadoDaChamada.Sucesso(configuracao)
    override suspend fun escolher(campo: String, modelo: String?): ResultadoDaChamada<ConfiguracaoAtual> {
        falhaAoGravar?.let { return ResultadoDaChamada.Falha(it) }
        gravados += campo to modelo
        configuracao = if (campo == "modelo_traducao") configuracao.copy(modelo_traducao = modelo) else configuracao
        return ResultadoDaChamada.Sucesso(configuracao)
    }
}

/** A escolha dos modelos de texto, com a tradução (MT1). */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelosDeIaTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    @Test
    fun a_traducao_sem_escolha_mostra_o_padrao_que_o_servidor_usaria() {
        assertEquals("Padrão: g/extrai", descricaoDoModeloAtual(config(), TarefaDeTexto.TRADUCAO))
        assertEquals("Padrão: barato/s", descricaoDoModeloAtual(config(suavizacao = "barato/s"), TarefaDeTexto.TRADUCAO))
        assertEquals("so/traduz", descricaoDoModeloAtual(config(traducao = "so/traduz"), TarefaDeTexto.TRADUCAO))
    }

    @Test
    fun tarefa_obrigatoria_sem_modelo_diz_que_falta_escolher() {
        assertEquals("Nenhum escolhido", descricaoDoModeloAtual(config(extracao = null), TarefaDeTexto.EXTRACAO))
        assertEquals("Padrão: g/prompt", descricaoDoModeloAtual(config(), TarefaDeTexto.SUAVIZACAO))
    }

    @Test
    fun a_busca_acha_pelo_nome_ou_pelo_id_sem_ligar_para_maiusculas() {
        assertEquals(listOf("openai/gpt-4o-mini"), filtrarModelos(MODELOS, "GPT").map { it.id })
        assertEquals(listOf("google/gemini-flash"), filtrarModelos(MODELOS, " google ").map { it.id })
        assertEquals(3, filtrarModelos(MODELOS, "").size)
    }

    @Test
    fun o_preco_sai_por_milhao_de_tokens_ou_gratis() {
        assertEquals("US$ 0,400 / 1M saída", precoDoModelo(MODELOS[0]))
        assertEquals("grátis", precoDoModelo(MODELOS[2]))
    }

    @Test
    fun escolher_o_modelo_da_traducao_grava_o_campo_certo_e_fecha_a_escolha() = runTest {
        val repositorio = ModelosFalsos(config())
        val vm = ModelosDeIaViewModel(repositorio)
        vm.carregar()
        vm.abrirEscolha(TarefaDeTexto.TRADUCAO)
        advanceUntilIdle()
        assertTrue(vm.estado.value.lista is ListaDeModelos.Pronta)

        vm.escolher(TarefaDeTexto.TRADUCAO, "openai/gpt-4o-mini")
        advanceUntilIdle()

        assertEquals(listOf("modelo_traducao" to "openai/gpt-4o-mini"), repositorio.gravados)
        assertNull(vm.estado.value.escolhendo)
        assertEquals("Tradução: modelo trocado.", vm.estado.value.recado)
    }

    @Test
    fun usar_o_padrao_manda_limpar_o_campo() = runTest {
        val repositorio = ModelosFalsos(config(traducao = "so/traduz"))
        val vm = ModelosDeIaViewModel(repositorio)
        vm.carregar()
        advanceUntilIdle()

        vm.escolher(TarefaDeTexto.TRADUCAO, null)
        advanceUntilIdle()

        assertEquals(listOf("modelo_traducao" to null), repositorio.gravados)
        assertEquals("Tradução: voltou ao padrão.", vm.estado.value.recado)
    }

    @Test
    fun falha_ao_gravar_mantem_a_escolha_aberta_e_a_antiga_vale() = runTest {
        val repositorio = ModelosFalsos(config(traducao = "so/traduz")).also { it.falhaAoGravar = "Sem rede." }
        val vm = ModelosDeIaViewModel(repositorio)
        vm.carregar()
        vm.abrirEscolha(TarefaDeTexto.TRADUCAO)
        advanceUntilIdle()

        vm.escolher(TarefaDeTexto.TRADUCAO, "outro/modelo")
        advanceUntilIdle()

        assertEquals(TarefaDeTexto.TRADUCAO, vm.estado.value.escolhendo)
        assertTrue(vm.estado.value.recadoEhErro)
        assertEquals("so/traduz", modeloEscolhido((vm.estado.value.carga as CargaDosModelos.Pronta).configuracao, TarefaDeTexto.TRADUCAO))
    }
}
