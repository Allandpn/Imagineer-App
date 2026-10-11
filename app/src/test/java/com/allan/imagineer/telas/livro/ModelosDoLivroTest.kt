package com.allan.imagineer.telas.livro

import com.allan.imagineer.rede.CatalogoDeImagem
import com.allan.imagineer.rede.ConfiguracaoAtual
import com.allan.imagineer.rede.EstadoDoAudio
import com.allan.imagineer.rede.EstimativaDaNarracao
import com.allan.imagineer.rede.ModeloDeImagem
import com.allan.imagineer.rede.ModeloDeNarracao
import com.allan.imagineer.rede.ModeloDeTexto
import com.allan.imagineer.rede.ModeloEmUso
import com.allan.imagineer.rede.ModelosDoLivro
import com.allan.imagineer.rede.RepositorioDeModelos
import com.allan.imagineer.rede.RepositorioDeModelosDoLivro
import com.allan.imagineer.rede.RepositorioDeNarracao
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val TEXTO = TAREFAS_DO_LIVRO.first { it.campo == "modelo_prompt" }
private val IMAGEM = TAREFAS_DO_LIVRO.first { it.campo == "modelo_imagem" }
private val MODELO_DE_VOZ = TAREFAS_DO_LIVRO.first { it.campo == "modelo_narracao" }
private val VOZ = TAREFAS_DO_LIVRO.first { it.campo == "narracao_voz" }

/** O que o servidor devolveria: as [trocas] e, tarefa a tarefa, o que vale (o do livro ou o [globais]). */
private fun doServidor(trocas: Map<String, String> = emptyMap(), globais: Map<String, String?> = emptyMap()): ModelosDoLivro {
    val campos = TAREFAS_DO_LIVRO.map { it.campo }
    return ModelosDoLivro(
        trocas = trocas,
        valendo = campos.associateWith { campo ->
            if (campo in trocas) ModeloEmUso(trocas[campo], "livro") else ModeloEmUso(globais[campo], "global")
        },
    )
}

private class ModelosDoLivroFalsos(var atual: ModelosDoLivro, val globais: Map<String, String?> = emptyMap()) : RepositorioDeModelosDoLivro {
    var falha: String? = null
    val gravacoes = mutableListOf<Map<String, String>>()

    override suspend fun ver(livroId: Int) = ResultadoDaChamada.Sucesso(atual)

    override suspend fun trocar(livroId: Int, trocas: Map<String, String>): ResultadoDaChamada<ModelosDoLivro> {
        falha?.let { return ResultadoDaChamada.Falha(it) }
        gravacoes += trocas
        atual = doServidor(trocas, globais)
        return ResultadoDaChamada.Sucesso(atual)
    }
}

private class ModelosGlobaisFalsos : RepositorioDeModelos {
    override suspend fun configuracao(): ResultadoDaChamada<ConfiguracaoAtual> = ResultadoDaChamada.Falha("não usado")
    override suspend fun modelosDeTexto() = ResultadoDaChamada.Sucesso(
        listOf(ModeloDeTexto(id = "a/barato", nome = "Barato", gratuito = true), ModeloDeTexto(id = "b/caro", nome = "Caro", custo_saida = 0.00001)),
    )
    override suspend fun catalogoDeImagem() = ResultadoDaChamada.Sucesso(
        CatalogoDeImagem(modelos = listOf(ModeloDeImagem(id = "fal:flux", nome = "Flux", fornecedor = "fal.ai", preco_por_imagem = "0.003"))),
    )
    override suspend fun informarPreco(modelo: String, preco: String?): ResultadoDaChamada<CatalogoDeImagem> = ResultadoDaChamada.Falha("não usado")
    override suspend fun testarImagem(modelo: String): ResultadoDaChamada<com.allan.imagineer.rede.TesteDeImagem> = ResultadoDaChamada.Falha("não usado")
    override suspend fun definirModelosDeImagem(modelos: List<String>): ResultadoDaChamada<ConfiguracaoAtual> = ResultadoDaChamada.Falha("não usado")
    override suspend fun escolher(campo: String, modelo: String?): ResultadoDaChamada<ConfiguracaoAtual> = ResultadoDaChamada.Falha("não usado")
}

private class VozesFalsas : RepositorioDeNarracao {
    override suspend fun modelos() = ResultadoDaChamada.Sucesso(
        listOf(
            ModeloDeNarracao(id = "voz/um", nome = "Voz Um", vozes = listOf("ana", "bia")),
            ModeloDeNarracao(id = "voz/dois", nome = "Voz Dois", vozes = listOf("bia", "caio")),
        ),
    )
    override suspend fun estimativa(capituloId: Int): ResultadoDaChamada<EstimativaDaNarracao> = ResultadoDaChamada.Falha("não usado")
    override suspend fun estado(capituloId: Int): ResultadoDaChamada<EstadoDoAudio> = ResultadoDaChamada.Falha("não usado")
    override suspend fun gerar(capituloId: Int, refazer: Boolean): ResultadoDaChamada<EstadoDoAudio> = ResultadoDaChamada.Falha("não usado")
    override suspend fun apagar(capituloId: Int): ResultadoDaChamada<Unit> = ResultadoDaChamada.Falha("não usado")
}

/** Os modelos de IA por livro (item 7.5g, MV4): o que a linha diz, o que se manda ao servidor e a escolha. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelosDoLivroTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(falsos: ModelosDoLivroFalsos) = ModelosDoLivroViewModel(7, falsos, ModelosGlobaisFalsos(), VozesFalsas())

    @Test
    fun MV4_a_linha_diz_o_modelo_do_livro_o_global_ou_que_nao_ha() {
        val modelos = doServidor(trocas = mapOf("modelo_prompt" to "x/livro"), globais = mapOf("modelo_prompt" to "g/global", "modelo_imagem" to "g/imagem"))

        assertEquals("Neste livro: x/livro", descricaoDaTarefaNoLivro(modelos, TEXTO))
        assertEquals("Padrão (global): g/imagem", descricaoDaTarefaNoLivro(modelos, IMAGEM))
        assertEquals("Padrão (nenhum escolhido ainda)", descricaoDaTarefaNoLivro(modelos, MODELO_DE_VOZ))
        assertTrue(tarefaTrocada(modelos, TEXTO))
        assertFalse(tarefaTrocada(modelos, IMAGEM))
    }

    @Test
    fun MV4_as_tarefas_do_livro_sao_as_de_texto_mais_imagem_modelo_de_voz_e_voz() {
        val campos = TAREFAS_DO_LIVRO.map { it.campo }

        assertTrue(campos.containsAll(listOf("modelo_extracao", "modelo_prompt", "modelo_conferencia", "modelo_imagem", "modelo_narracao", "narracao_voz")))
        assertEquals(campos.size, campos.toSet().size)
    }

    @Test
    fun MV4_escolher_manda_as_trocas_de_antes_inclusive_as_que_a_tela_nao_mostra() {
        val antes = mapOf("modelo_leitura" to "oculta/leitura", "modelo_imagem" to "fal:flux")

        val depois = trocasComEscolha(antes, "modelo_prompt", "b/caro")

        assertEquals(mapOf("modelo_leitura" to "oculta/leitura", "modelo_imagem" to "fal:flux", "modelo_prompt" to "b/caro"), depois)
        assertEquals(mapOf("modelo_leitura" to "oculta/leitura"), trocasComEscolha(antes, "modelo_imagem", null))
    }

    @Test
    fun MV4_trocar_o_modelo_de_voz_desfaz_a_voz_a_menos_que_o_novo_modelo_tenha_a_mesma() {
        val antes = mapOf("modelo_narracao" to "voz/um", "narracao_voz" to "ana")

        assertEquals(mapOf("modelo_narracao" to "voz/dois"), trocasComEscolha(antes, "modelo_narracao", "voz/dois", listOf("bia", "caio")))
        assertEquals(
            mapOf("modelo_narracao" to "voz/tres", "narracao_voz" to "ana"),
            trocasComEscolha(antes, "modelo_narracao", "voz/tres", listOf("ana", "zeca")),
        )
        assertEquals(emptyMap<String, String>(), trocasComEscolha(antes, "modelo_narracao", null))
    }

    @Test
    fun MV4_a_busca_acha_pelo_titulo_pelo_id_e_pelo_detalhe() {
        val opcoes = listOf(OpcaoDeEscolha("a/barato", "Barato", "grátis"), OpcaoDeEscolha("b/caro", "Caro", "US$ 10 / 1M saída"))

        assertEquals(2, filtrarOpcoes(opcoes, "  ").size)
        assertEquals(listOf("a/barato"), filtrarOpcoes(opcoes, "BARATO").map { it.id })
        assertEquals(listOf("b/caro"), filtrarOpcoes(opcoes, "b/").map { it.id })
        assertEquals(listOf("a/barato"), filtrarOpcoes(opcoes, "grátis").map { it.id })
    }

    @Test
    fun MV4_o_viewmodel_traz_o_que_vale_no_livro() = runTest {
        val falsos = ModelosDoLivroFalsos(doServidor(trocas = mapOf("modelo_prompt" to "x/livro")))
        val vm = viewModel(falsos)

        vm.carregar(); advanceUntilIdle()

        assertEquals(CargaDosModelosDoLivro.Pronta(falsos.atual), vm.estado.value.carga)
    }

    @Test
    fun MV4_escolher_grava_so_neste_livro_e_fecha_a_escolha() = runTest {
        val falsos = ModelosDoLivroFalsos(doServidor(trocas = mapOf("modelo_leitura" to "oculta/leitura")))
        val vm = viewModel(falsos)
        vm.carregar(); advanceUntilIdle()
        vm.abrirEscolha(TEXTO); advanceUntilIdle()

        vm.escolher(TEXTO, "b/caro"); advanceUntilIdle()

        assertEquals(listOf(mapOf("modelo_leitura" to "oculta/leitura", "modelo_prompt" to "b/caro")), falsos.gravacoes)
        assertEquals(null, vm.estado.value.escolhendo)
        assertEquals("Prompt de imagem: trocado só neste livro.", vm.estado.value.recado)
        assertFalse(vm.estado.value.recadoEhErro)
        assertFalse(vm.estado.value.salvando)
    }

    @Test
    fun MV4_usar_o_padrao_tira_a_troca() = runTest {
        val falsos = ModelosDoLivroFalsos(doServidor(trocas = mapOf("modelo_prompt" to "x/livro", "modelo_imagem" to "fal:flux")))
        val vm = viewModel(falsos)
        vm.carregar(); advanceUntilIdle()

        vm.escolher(TEXTO, null); advanceUntilIdle()

        assertEquals(listOf(mapOf("modelo_imagem" to "fal:flux")), falsos.gravacoes)
        assertEquals("Prompt de imagem: voltou ao padrão.", vm.estado.value.recado)
    }

    @Test
    fun MV4_falha_ao_gravar_deixa_o_motivo_e_nada_muda() = runTest {
        val falsos = ModelosDoLivroFalsos(doServidor()).also { it.falha = "Servidor fora do ar." }
        val vm = viewModel(falsos)
        vm.carregar(); advanceUntilIdle()

        vm.escolher(TEXTO, "b/caro"); advanceUntilIdle()

        assertEquals("Servidor fora do ar.", vm.estado.value.recado)
        assertTrue(vm.estado.value.recadoEhErro)
        assertEquals(CargaDosModelosDoLivro.Pronta(doServidor()), vm.estado.value.carga)
        assertFalse(vm.estado.value.salvando)
    }

    @Test
    fun MV4_as_opcoes_de_texto_e_de_imagem_vem_do_servidor() = runTest {
        val vm = viewModel(ModelosDoLivroFalsos(doServidor()))
        vm.carregar(); advanceUntilIdle()

        vm.abrirEscolha(TEXTO); advanceUntilIdle()
        val texto = vm.estado.value.opcoes as OpcoesDaEscolha.Prontas
        vm.abrirEscolha(IMAGEM); advanceUntilIdle()
        val imagem = vm.estado.value.opcoes as OpcoesDaEscolha.Prontas

        assertEquals(listOf("a/barato", "b/caro"), texto.opcoes.map { it.id })
        assertTrue(texto.opcoes.first().detalhe.contains("grátis"))
        assertEquals(listOf("fal:flux"), imagem.opcoes.map { it.id })
        assertTrue(imagem.opcoes.single().detalhe.contains("fal.ai"))
    }

    @Test
    fun MV4_a_voz_lista_as_vozes_do_modelo_que_vale_no_livro() = runTest {
        val falsos = ModelosDoLivroFalsos(doServidor(trocas = mapOf("modelo_narracao" to "voz/dois")))
        val vm = viewModel(falsos)
        vm.carregar(); advanceUntilIdle()

        vm.abrirEscolha(VOZ); advanceUntilIdle()

        assertEquals(listOf("bia", "caio"), (vm.estado.value.opcoes as OpcoesDaEscolha.Prontas).opcoes.map { it.id })
    }

    @Test
    fun MV4_a_voz_usa_o_modelo_global_quando_o_livro_nao_trocou_o_modelo() = runTest {
        val globais = mapOf<String, String?>("modelo_narracao" to "voz/um")
        val vm = viewModel(ModelosDoLivroFalsos(doServidor(globais = globais), globais))
        vm.carregar(); advanceUntilIdle()

        vm.abrirEscolha(VOZ); advanceUntilIdle()

        assertEquals(listOf("ana", "bia"), (vm.estado.value.opcoes as OpcoesDaEscolha.Prontas).opcoes.map { it.id })
    }

    @Test
    fun MV4_sem_modelo_de_voz_nenhum_a_escolha_da_voz_pede_para_escolher_o_modelo_antes() = runTest {
        val vm = viewModel(ModelosDoLivroFalsos(doServidor()))
        vm.carregar(); advanceUntilIdle()

        vm.abrirEscolha(VOZ); advanceUntilIdle()

        assertTrue((vm.estado.value.opcoes as OpcoesDaEscolha.Erro).motivo.contains("modelo de voz"))
    }

    @Test
    fun MV4_trocar_o_modelo_de_voz_pelo_app_desfaz_a_voz_que_o_novo_modelo_nao_tem() = runTest {
        val falsos = ModelosDoLivroFalsos(doServidor(trocas = mapOf("modelo_narracao" to "voz/um", "narracao_voz" to "ana")))
        val vm = viewModel(falsos)
        vm.carregar(); advanceUntilIdle()
        vm.abrirEscolha(MODELO_DE_VOZ); advanceUntilIdle() // lê os modelos de voz e as vozes de cada um

        vm.escolher(MODELO_DE_VOZ, "voz/dois"); advanceUntilIdle()

        assertEquals(listOf(mapOf("modelo_narracao" to "voz/dois")), falsos.gravacoes)
    }

    @Test
    fun MV4_so_as_tarefas_com_estrela_tem_favoritos() {
        assertEquals("texto", TEXTO.fonte.categoriaDeFavoritos)
        assertEquals("imagem", IMAGEM.fonte.categoriaDeFavoritos)
        assertEquals("narracao", MODELO_DE_VOZ.fonte.categoriaDeFavoritos)
        assertEquals(null, VOZ.fonte.categoriaDeFavoritos)
    }
}
