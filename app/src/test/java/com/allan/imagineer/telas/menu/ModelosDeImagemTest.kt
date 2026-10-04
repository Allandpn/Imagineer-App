package com.allan.imagineer.telas.menu

import com.allan.imagineer.rede.CatalogoDeImagem
import com.allan.imagineer.rede.ConfiguracaoAtual
import com.allan.imagineer.rede.ModeloDeImagem
import com.allan.imagineer.rede.ModeloDeTexto
import com.allan.imagineer.rede.RepositorioDeModelos
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.TesteDeImagem
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

private fun modelo(
    id: String, nome: String = id, preco: String? = null, origem: String? = null, emUso: Boolean = false, disponivel: Boolean = false,
    fornecedor: String = "OpenRouter", porMilhao: String? = null,
) = ModeloDeImagem(
    id = id, nome = nome, fornecedor = fornecedor, preco_por_milhao_de_tokens = porMilhao, preco_por_imagem = preco,
    origem_do_preco = origem, moderacao = "moderado", em_uso = emUso, disponivel = disponivel,
)

private class CatalogoFalso(var modelos: List<ModeloDeImagem>) : RepositorioDeModelos {
    var falhaNoTeste: String? = null
    val testados = mutableListOf<String>()
    val listasGravadas = mutableListOf<List<String>>()
    val escolhas = mutableListOf<Pair<String, String?>>()
    private val config = ConfiguracaoAtual(tem_chave_api = true, origem_da_chave = "ambiente", prioridade_ia = "ECONOMIA")

    override suspend fun configuracao() = ResultadoDaChamada.Sucesso(config)
    override suspend fun modelosDeTexto() = ResultadoDaChamada.Sucesso(emptyList<ModeloDeTexto>())
    override suspend fun catalogoDeImagem() = ResultadoDaChamada.Sucesso(CatalogoDeImagem(modelos))
    override suspend fun testarImagem(modelo: String): ResultadoDaChamada<TesteDeImagem> {
        testados += modelo
        falhaNoTeste?.let { return ResultadoDaChamada.Falha(it) }
        return ResultadoDaChamada.Sucesso(TesteDeImagem(modelo = modelo, largura = 1024, altura = 1536, custo = "0.0123", segundos = 8.4))
    }
    override suspend fun definirModelosDeImagem(modelos: List<String>): ResultadoDaChamada<ConfiguracaoAtual> {
        listasGravadas += modelos
        this.modelos = this.modelos.map { it.copy(disponivel = it.id in modelos || it.em_uso) } +
            modelos.filter { id -> this.modelos.none { it.id == id } }.map { modelo(it, disponivel = true) }
        return ResultadoDaChamada.Sucesso(config)
    }
    override suspend fun escolher(campo: String, modelo: String?): ResultadoDaChamada<ConfiguracaoAtual> {
        escolhas += campo to modelo
        modelos = modelos.map { it.copy(em_uso = it.id == modelo) }
        return ResultadoDaChamada.Sucesso(config)
    }
}

/** A escolha do modelo de imagem (MI6). */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelosDeImagemTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private val a = modelo("meta/muse-image", "Muse", emUso = true, disponivel = true)
    private val b = modelo("fal:fal-ai/flux/dev", "Flux Dev", preco = "0.025", origem = "tabela", fornecedor = "fal.ai")

    private fun pronto(vm: ModelosDeImagemViewModel) = (vm.estado.value.carga as CargaDoCatalogo.Pronta).modelos

    @Test
    fun o_preco_diz_de_onde_veio_ou_que_falta_medir() {
        assertEquals("Sem preço por imagem ainda: teste para medir", precoDaImagem(a))
        assertEquals("~US$ 0,025 por imagem (estimado pela tabela)", precoDaImagem(b))
        assertEquals("US$ 0,020 por imagem (média do que já custou)", precoDaImagem(modelo("x", preco = "0.02", origem = "medido")))
    }

    @Test
    fun o_preco_de_token_so_aparece_no_openrouter_e_avisa_que_nao_e_por_imagem() {
        assertNull(precoPorTokenDeImagem(a))
        assertTrue(precoPorTokenDeImagem(modelo("y", porMilhao = "2.40"))!!.endsWith("(a imagem usa vários)"))
    }

    @Test
    fun a_busca_acha_por_nome_id_ou_fornecedor() {
        val todos = listOf(a, b)
        assertEquals(listOf(b), filtrarCatalogo(todos, "flux"))
        assertEquals(listOf(b), filtrarCatalogo(todos, "FAL.ai"))
        assertEquals(todos, filtrarCatalogo(todos, " "))
    }

    @Test
    fun alternar_acrescenta_ou_tira_da_lista_de_escolha() {
        assertEquals(listOf("meta/muse-image", "fal:fal-ai/flux/dev"), listaAoAlternar(listOf(a, b), "fal:fal-ai/flux/dev"))
        assertEquals(emptyList<String>(), listaAoAlternar(listOf(a, b), "meta/muse-image"))
    }

    @Test
    fun usar_grava_o_modelo_padrao_e_relê_o_catalogo() = runTest {
        val repositorio = CatalogoFalso(listOf(a, b))
        val vm = ModelosDeImagemViewModel(repositorio)
        vm.carregar()
        advanceUntilIdle()

        vm.usar(b)
        advanceUntilIdle()

        assertEquals(listOf("modelo_imagem" to "fal:fal-ai/flux/dev"), repositorio.escolhas)
        assertEquals(listOf("fal:fal-ai/flux/dev"), pronto(vm).filter { it.em_uso }.map { it.id })
    }

    @Test
    fun mostrar_ao_gerar_grava_a_lista_nova_e_o_padrao_nao_sai() = runTest {
        val repositorio = CatalogoFalso(listOf(a, b))
        val vm = ModelosDeImagemViewModel(repositorio)
        vm.carregar()
        advanceUntilIdle()

        vm.alternarDisponivel(b)
        advanceUntilIdle()
        assertEquals(listOf(listOf("meta/muse-image", "fal:fal-ai/flux/dev")), repositorio.listasGravadas)

        vm.alternarDisponivel(a)  // o padrão: nada é gravado
        advanceUntilIdle()
        assertEquals(1, repositorio.listasGravadas.size)
        assertEquals("O modelo padrão sempre aparece ao gerar.", vm.estado.value.recado)
    }

    @Test
    fun adicionar_pelo_id_inclui_na_lista_sem_repetir() = runTest {
        val repositorio = CatalogoFalso(listOf(a))
        val vm = ModelosDeImagemViewModel(repositorio)
        vm.carregar()
        advanceUntilIdle()

        vm.adicionar("  replicate:outro/modelo ")
        advanceUntilIdle()

        assertEquals(listOf(listOf("meta/muse-image", "replicate:outro/modelo")), repositorio.listasGravadas)
        assertTrue(pronto(vm).any { it.id == "replicate:outro/modelo" })
    }

    @Test
    fun o_teste_so_roda_depois_de_confirmar_e_mostra_resolucao_e_custo() = runTest {
        val repositorio = CatalogoFalso(listOf(a, b))
        val vm = ModelosDeImagemViewModel(repositorio)
        vm.carregar()
        advanceUntilIdle()

        vm.pedirTeste(b)
        advanceUntilIdle()
        assertTrue(repositorio.testados.isEmpty())  // nada foi cobrado ainda
        assertTrue(vm.estado.value.teste is TesteDeModelo.Confirmando)

        vm.confirmarTeste()
        advanceUntilIdle()

        assertEquals(listOf("fal:fal-ai/flux/dev"), repositorio.testados)
        val resultado = (vm.estado.value.teste as TesteDeModelo.Pronto).resultado
        assertEquals("1024×1536", resolucaoDoTeste(resultado))
        assertEquals("0.0123", resultado.custo)
    }

    @Test
    fun cancelar_a_confirmacao_nao_gera_nada() = runTest {
        val repositorio = CatalogoFalso(listOf(a))
        val vm = ModelosDeImagemViewModel(repositorio)
        vm.carregar()
        advanceUntilIdle()

        vm.pedirTeste(a)
        vm.fecharTeste()
        vm.confirmarTeste()  // sem pedido em aberto: não faz nada
        advanceUntilIdle()

        assertTrue(repositorio.testados.isEmpty())
        assertNull(vm.estado.value.teste)
    }

    @Test
    fun falha_no_teste_mostra_o_motivo() = runTest {
        val repositorio = CatalogoFalso(listOf(a)).also { it.falhaNoTeste = "O provedor recusou." }
        val vm = ModelosDeImagemViewModel(repositorio)
        vm.carregar()
        advanceUntilIdle()

        vm.pedirTeste(a)
        vm.confirmarTeste()
        advanceUntilIdle()

        assertEquals("O provedor recusou.", (vm.estado.value.teste as TesteDeModelo.Falhou).motivo)
    }

    @Test
    fun a_confirmacao_avisa_o_preco_conhecido_ou_que_nao_se_sabe() {
        assertTrue(avisoDeCustoDoTeste(b).contains("~US$ 0,025"))
        assertTrue(avisoDeCustoDoTeste(a).contains("ainda não é conhecido"))
    }
}
