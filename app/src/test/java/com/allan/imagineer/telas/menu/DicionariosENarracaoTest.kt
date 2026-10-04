package com.allan.imagineer.telas.menu

import com.allan.imagineer.rede.ConfiguracaoAtual
import com.allan.imagineer.rede.DicionarioDoServidor
import com.allan.imagineer.rede.PreferenciasDosDicionarios
import com.allan.imagineer.rede.RepositorioDeDicionario
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun dic(id: String, ativo: Boolean = true, padraoPara: List<String> = listOf("pt"), palavras: Int = 1500) =
    DicionarioDoServidor(id = id, nome = "Dicionário $id", padrao_para = padraoPara, palavras = palavras, ativo = ativo)

private class DicionariosFalsos(
    var lista: List<DicionarioDoServidor>,
    var falharAoGravar: String? = null,
) : RepositorioDeDicionario {
    val gravados = mutableListOf<PreferenciasDosDicionarios>()
    override suspend fun consultar(palavra: String, idioma: String?, todos: Boolean) =
        ResultadoDaChamada.Falha("não usado", null)

    override suspend fun listar() = ResultadoDaChamada.Sucesso(lista)

    override suspend fun gravarPreferencias(preferencias: PreferenciasDosDicionarios): ResultadoDaChamada<List<DicionarioDoServidor>> {
        falharAoGravar?.let { return ResultadoDaChamada.Falha(it, 500) }
        gravados += preferencias
        // Como o servidor: a lista volta na ordem pedida, com os desligados marcados.
        lista = preferencias.ordem.map { id -> lista.first { it.id == id }.copy(ativo = id !in preferencias.desativados) }
        return ResultadoDaChamada.Sucesso(lista)
    }
}

private fun configuracao(voz: String? = null, instrucoes: String? = null, motor: String = "APARELHO", modo: String = "UMA_VOZ") =
    ConfiguracaoAtual(
        tem_chave_api = true,
        origem_da_chave = "ambiente",
        prioridade_ia = "ECONOMIA",
        narracao_motor = motor,
        narracao_modo = modo,
        narracao_voz = voz,
        narracao_instrucoes = instrucoes,
    )

private class ConfiguracaoFalsa(var atual: ConfiguracaoAtual, var falharAoGravar: String? = null) : RepositorioDeModelos {
    val gravados = mutableListOf<Map<String, String>>()
    override suspend fun configuracao() = ResultadoDaChamada.Sucesso(atual)
    override suspend fun modelosDeTexto() = ResultadoDaChamada.Falha("não usado", null)
    override suspend fun catalogoDeImagem() = ResultadoDaChamada.Falha("não usado", null)
    override suspend fun informarPreco(modelo: String, preco: String?) = ResultadoDaChamada.Falha("não usado", null)
    override suspend fun testarImagem(modelo: String) = ResultadoDaChamada.Falha("não usado", null)
    override suspend fun definirModelosDeImagem(modelos: List<String>) = ResultadoDaChamada.Falha("não usado", null)
    override suspend fun escolher(campo: String, modelo: String?) = ResultadoDaChamada.Falha("não usado", null)

    override suspend fun gravar(campos: Map<String, String>): ResultadoDaChamada<ConfiguracaoAtual> {
        falharAoGravar?.let { return ResultadoDaChamada.Falha(it, 500) }
        gravados += campos
        atual = atual.copy(
            narracao_voz = campos["narracao_voz"]?.ifBlank { null },
            narracao_instrucoes = campos["narracao_instrucoes"]?.ifBlank { null },
        )
        return ResultadoDaChamada.Sucesso(atual)
    }
}

/** A tela de dicionários (RL28 a RL30) e a configuração da narração (RL26). */
@OptIn(ExperimentalCoroutinesApi::class)
class DicionariosENarracaoTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    // ---- as regras da lista de dicionários

    @Test
    fun alternar_liga_e_desliga_so_o_escolhido() {
        val lista = listOf(dic("a"), dic("b"))

        val depois = alternarDicionario(lista, "b")

        assertTrue(depois[0].ativo)
        assertFalse(depois[1].ativo)
        assertTrue(alternarDicionario(depois, "b")[1].ativo)
    }

    @Test
    fun mover_sobe_e_desce_uma_posicao() {
        val lista = listOf(dic("a"), dic("b"), dic("c"))

        assertEquals(listOf("b", "a", "c"), moverDicionario(lista, "b", -1).map { it.id })
        assertEquals(listOf("a", "c", "b"), moverDicionario(lista, "b", +1).map { it.id })
    }

    @Test
    fun mover_alem_do_limite_ou_um_id_desconhecido_nao_muda_nada() {
        val lista = listOf(dic("a"), dic("b"))

        assertEquals(lista, moverDicionario(lista, "a", -1))
        assertEquals(lista, moverDicionario(lista, "b", +1))
        assertEquals(lista, moverDicionario(lista, "x", +1))
    }

    @Test
    fun as_preferencias_levam_a_ordem_inteira_e_os_desligados() {
        val lista = listOf(dic("a", ativo = false), dic("b"), dic("c", ativo = false))

        val preferencias = preferenciasDe(lista)

        assertEquals(listOf("a", "b", "c"), preferencias.ordem)
        assertEquals(listOf("a", "c"), preferencias.desativados)
    }

    @Test
    fun a_linha_diz_em_que_livros_o_dicionario_aparece() {
        assertEquals("livros em português · 1 mil palavras", usoDoDicionario(dic("a", palavras = 1500)))
        assertEquals("livros em inglês, espanhol · 200 palavras", usoDoDicionario(dic("a", padraoPara = listOf("en", "es"), palavras = 200)))
        assertEquals("só em \"Procurar em todos\" · 2,5 milhão de palavras", usoDoDicionario(dic("a", padraoPara = emptyList(), palavras = 2_500_000)))
    }

    // ---- o ViewModel dos dicionários

    @Test
    fun carrega_a_lista_na_ordem_que_o_servidor_mandou() = runTest {
        val vm = DicionariosViewModel(DicionariosFalsos(listOf(dic("b"), dic("a"))))

        vm.carregar()
        advanceUntilIdle()

        assertEquals(listOf("b", "a"), (vm.estado.value.carga as CargaDosDicionarios.Pronta).dicionarios.map { it.id })
    }

    @Test
    fun cada_toque_grava_na_hora_a_lista_nova() = runTest {
        val falso = DicionariosFalsos(listOf(dic("a"), dic("b"), dic("c")))
        val vm = DicionariosViewModel(falso)
        vm.carregar()
        advanceUntilIdle()

        vm.subir("c")
        advanceUntilIdle()
        vm.alternar("a")
        advanceUntilIdle()

        assertEquals(2, falso.gravados.size)
        assertEquals(listOf("a", "c", "b"), falso.gravados[0].ordem)
        assertEquals(listOf("a"), falso.gravados[1].desativados)
        val tela = (vm.estado.value.carga as CargaDosDicionarios.Pronta).dicionarios
        assertEquals(listOf("a", "c", "b"), tela.map { it.id })
        assertFalse(tela[0].ativo)
    }

    @Test
    fun tocar_no_limite_nao_chama_o_servidor() = runTest {
        val falso = DicionariosFalsos(listOf(dic("a"), dic("b")))
        val vm = DicionariosViewModel(falso)
        vm.carregar()
        advanceUntilIdle()

        vm.subir("a")
        advanceUntilIdle()

        assertTrue(falso.gravados.isEmpty())
    }

    @Test
    fun se_o_servidor_falhar_a_lista_volta_ao_que_estava_e_avisa() = runTest {
        val falso = DicionariosFalsos(listOf(dic("a"), dic("b")), falharAoGravar = "fora do ar")
        val vm = DicionariosViewModel(falso)
        vm.carregar()
        advanceUntilIdle()

        vm.descer("a")
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), (vm.estado.value.carga as CargaDosDicionarios.Pronta).dicionarios.map { it.id })
        assertTrue(vm.estado.value.aviso!!.contains("fora do ar"))
        vm.avisoLido()
        assertNull(vm.estado.value.aviso)
    }

    @Test
    fun sem_carga_pronta_um_toque_nao_faz_nada() = runTest {
        val falso = DicionariosFalsos(listOf(dic("a")))
        val vm = DicionariosViewModel(falso)

        vm.alternar("a")
        advanceUntilIdle()

        assertTrue(falso.gravados.isEmpty())
    }

    // ---- a narração

    @Test
    fun a_configuracao_antiga_sem_os_campos_da_narracao_vale_o_aparelho() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        val antiga = json.decodeFromString<ConfiguracaoAtual>("""{"tem_chave_api":true,"origem_da_chave":"ambiente","prioridade_ia":"ECONOMIA"}""")

        assertEquals("APARELHO", antiga.narracao_motor)
        assertEquals("UMA_VOZ", antiga.narracao_modo)
        assertNull(antiga.narracao_voz)
        assertEquals("Voz do aparelho", rotuloDoMotor(antiga.narracao_motor))
        assertEquals("Voz de IA", rotuloDoMotor("IA"))
    }

    @Test
    fun a_edicao_parte_do_que_o_servidor_tem_e_so_vale_salvar_quando_mudou() {
        val atual = configuracao(voz = "alloy", instrucoes = "grave")

        val edicao = atual.paraNarracaoEdicao()

        assertEquals(NarracaoEdicao("alloy", "grave"), edicao)
        assertFalse(mudouANarracao(edicao, atual))
        assertFalse(mudouANarracao(edicao.copy(voz = " alloy "), atual))  // espaço nas pontas não conta
        assertTrue(mudouANarracao(edicao.copy(instrucoes = "grave e calma"), atual))
        assertTrue(mudouANarracao(NarracaoEdicao(), atual))  // apagar também é mudar
    }

    @Test
    fun salvar_grava_a_voz_e_as_instrucoes_sem_espacos_nas_pontas() = runTest {
        val falso = ConfiguracaoFalsa(configuracao())
        val vm = NarracaoViewModel(falso)
        vm.carregar()
        advanceUntilIdle()

        vm.mudar(NarracaoEdicao(voz = "  nova ", instrucoes = " com suspense  "))
        vm.salvar()
        advanceUntilIdle()

        assertEquals(mapOf("narracao_voz" to "nova", "narracao_instrucoes" to "com suspense"), falso.gravados.single())
        assertEquals("Salvo.", vm.estado.value.aviso)
        assertFalse(vm.estado.value.salvando)
        assertEquals(NarracaoEdicao("nova", "com suspense"), vm.estado.value.edicao)
    }

    @Test
    fun apagar_o_texto_vai_como_vazio_para_o_servidor_limpar() = runTest {
        val falso = ConfiguracaoFalsa(configuracao(voz = "alloy", instrucoes = "grave"))
        val vm = NarracaoViewModel(falso)
        vm.carregar()
        advanceUntilIdle()

        vm.mudar(NarracaoEdicao())
        vm.salvar()
        advanceUntilIdle()

        assertEquals(mapOf("narracao_voz" to "", "narracao_instrucoes" to ""), falso.gravados.single())
        assertEquals(NarracaoEdicao(), vm.estado.value.edicao)
    }

    @Test
    fun falha_ao_salvar_mantem_o_que_foi_digitado_e_avisa() = runTest {
        val falso = ConfiguracaoFalsa(configuracao(), falharAoGravar = "fora do ar")
        val vm = NarracaoViewModel(falso)
        vm.carregar()
        advanceUntilIdle()

        vm.mudar(NarracaoEdicao(instrucoes = "meu tom"))
        vm.salvar()
        advanceUntilIdle()

        assertEquals("meu tom", vm.estado.value.edicao.instrucoes)
        assertTrue(vm.estado.value.aviso!!.contains("fora do ar"))
        assertFalse(vm.estado.value.salvando)
    }
}
