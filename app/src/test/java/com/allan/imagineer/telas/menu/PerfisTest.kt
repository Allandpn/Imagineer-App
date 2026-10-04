package com.allan.imagineer.telas.menu

import com.allan.imagineer.rede.CategoriaDeEstilo
import com.allan.imagineer.rede.PerfilEdicao
import com.allan.imagineer.rede.PerfilRenderizacao
import com.allan.imagineer.rede.RepositorioDePerfis
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.corpoDoPerfil
import com.allan.imagineer.rede.paraEdicao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun perfil(id: Int, nome: String, categoria: String? = null, estilo: String? = null, deFabrica: Boolean = false) =
    PerfilRenderizacao(id = id, nome = nome, estilo = estilo, categoria_estilo = categoria, de_fabrica = deFabrica)

private class PerfisFalsos(var perfis: List<PerfilRenderizacao> = emptyList(), var falharAoSalvar: String? = null) : RepositorioDePerfis {
    val criados = mutableListOf<PerfilEdicao>()
    val ajustados = mutableListOf<Pair<Int, PerfilEdicao>>()
    val removidos = mutableListOf<Int>()
    override suspend fun listarPerfis() = ResultadoDaChamada.Sucesso(perfis)
    override suspend fun abrirPerfil(perfilId: Int) = ResultadoDaChamada.Sucesso(perfis.first { it.id == perfilId })
    override suspend fun criarPerfil(edicao: PerfilEdicao): ResultadoDaChamada<PerfilRenderizacao> {
        falharAoSalvar?.let { return ResultadoDaChamada.Falha(it, 409) }
        criados += edicao
        val novo = perfil(100 + criados.size, edicao.nome, edicao.categoria?.name)
        perfis = perfis + novo
        return ResultadoDaChamada.Sucesso(novo)
    }
    override suspend fun ajustarPerfil(perfilId: Int, edicao: PerfilEdicao): ResultadoDaChamada<PerfilRenderizacao> {
        falharAoSalvar?.let { return ResultadoDaChamada.Falha(it, 409) }
        ajustados += perfilId to edicao
        val novo = perfil(perfilId, edicao.nome, edicao.categoria?.name)
        perfis = perfis.map { if (it.id == perfilId) novo else it }
        return ResultadoDaChamada.Sucesso(novo)
    }
    override suspend fun removerPerfil(perfilId: Int): ResultadoDaChamada<Unit> {
        removidos += perfilId
        perfis = perfis.filterNot { it.id == perfilId }
        return ResultadoDaChamada.Sucesso(Unit)
    }
}

/** A tela de perfis e a categoria de estilo (BT5, BT6). */
@OptIn(ExperimentalCoroutinesApi::class)
class PerfisTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    // ---- as categorias

    @Test
    fun as_categorias_do_app_sao_exatamente_as_dez_do_servidor() {
        assertEquals(
            setOf(
                "FOTORREALISTA_CINEMATOGRAFICO", "PINTURA_A_OLEO", "AQUARELA", "ARTE_DIGITAL_CONCEITUAL", "QUADRINHOS",
                "CARTOON_ANIMACAO", "ANIME", "PIXEL_ART", "GRAVURA_CLASSICA", "ANIMACAO_3D",
            ),
            CategoriaDeEstilo.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun categoria_desconhecida_de_um_servidor_mais_novo_vira_sem_categoria() {
        assertEquals(CategoriaDeEstilo.ANIME, CategoriaDeEstilo.de("ANIME"))
        assertNull(CategoriaDeEstilo.de("CATEGORIA_FUTURA"))
        assertNull(CategoriaDeEstilo.de(null))
        assertEquals(CategoriaDeEstilo.PIXEL_ART, perfil(1, "x", "PIXEL_ART").categoria)
    }

    // ---- o corpo enviado

    @Test
    fun campos_em_branco_vao_como_nulo_explicito_para_apagar_o_que_havia() {
        val corpo = corpoDoPerfil(PerfilEdicao(nome = "  Gravura  ", estilo = "   ", paleta = "sépia", categoria = null))

        assertEquals("Gravura", corpo.getValue("nome").jsonPrimitive.content)
        assertEquals(JsonNull, corpo.getValue("estilo"))
        assertEquals(JsonNull, corpo.getValue("categoria_estilo"))  // sem categoria desfaz a que havia
        assertEquals("sépia", corpo.getValue("paleta").jsonPrimitive.content)
        assertTrue(corpo.containsKey("iluminacao") && corpo.containsKey("formato") && corpo.containsKey("artista_referencia"))
    }

    @Test
    fun a_categoria_vai_pelo_nome_da_api() {
        val corpo = corpoDoPerfil(PerfilEdicao(nome = "x", categoria = CategoriaDeEstilo.ANIMACAO_3D))

        assertEquals("ANIMACAO_3D", corpo.getValue("categoria_estilo").jsonPrimitive.content)
    }

    @Test
    fun editar_parte_do_que_o_perfil_ja_tem() {
        val edicao = PerfilRenderizacao(1, "Óleo", estilo = "pincelada", paleta = "ocre", categoria_estilo = "PINTURA_A_OLEO").paraEdicao()

        assertEquals("Óleo", edicao.nome)
        assertEquals("pincelada", edicao.estilo)
        assertEquals("", edicao.iluminacao)
        assertEquals(CategoriaDeEstilo.PINTURA_A_OLEO, edicao.categoria)
    }

    // ---- o ViewModel

    @Test
    fun carrega_a_lista_de_perfis() = runTest {
        val vm = PerfisViewModel(PerfisFalsos(listOf(perfil(1, "A"), perfil(2, "B"))))

        vm.carregar()
        advanceUntilIdle()

        assertEquals(2, (vm.estado.value.carga as CargaDosPerfis.Pronta).perfis.size)
    }

    @Test
    fun cria_um_perfil_novo_com_categoria_e_recarrega_a_lista() = runTest {
        val falso = PerfisFalsos()
        val vm = PerfisViewModel(falso)
        vm.carregar()
        advanceUntilIdle()

        vm.novo()
        vm.mudarFormulario(PerfilEdicao(nome = "Pixel noir", categoria = CategoriaDeEstilo.PIXEL_ART))
        vm.salvar()
        advanceUntilIdle()

        assertEquals(CategoriaDeEstilo.PIXEL_ART, falso.criados.single().categoria)
        assertNull(vm.estado.value.formulario)
        assertEquals("Pixel noir", (vm.estado.value.carga as CargaDosPerfis.Pronta).perfis.single().nome)
    }

    @Test
    fun edita_um_perfil_que_ja_existe_trocando_a_categoria() = runTest {
        val falso = PerfisFalsos(listOf(perfil(5, "Óleo", "PINTURA_A_OLEO")))
        val vm = PerfisViewModel(falso)
        vm.carregar()
        advanceUntilIdle()

        vm.editar(perfil(5, "Óleo", "PINTURA_A_OLEO"))
        vm.mudarFormulario(vm.estado.value.formulario!!.edicao.copy(categoria = CategoriaDeEstilo.GRAVURA_CLASSICA))
        vm.salvar()
        advanceUntilIdle()

        assertEquals(5, falso.ajustados.single().first)
        assertEquals(CategoriaDeEstilo.GRAVURA_CLASSICA, falso.ajustados.single().second.categoria)
    }

    @Test
    fun nome_em_branco_nao_chega_ao_servidor() = runTest {
        val falso = PerfisFalsos()
        val vm = PerfisViewModel(falso)
        vm.novo()

        vm.salvar()
        advanceUntilIdle()

        assertTrue(falso.criados.isEmpty())
        assertEquals("Dê um nome ao perfil.", vm.estado.value.formulario!!.erro)
        assertFalse(podeSalvarOPerfil(PerfilEdicao(nome = "   ")))
    }

    @Test
    fun nome_repetido_volta_no_formulario_que_continua_aberto_com_o_que_foi_digitado() = runTest {
        val vm = PerfisViewModel(PerfisFalsos(falharAoSalvar = "Já existe um perfil de renderização chamado 'X'."))
        vm.novo()
        vm.mudarFormulario(PerfilEdicao(nome = "X", estilo = "meu texto"))

        vm.salvar()
        advanceUntilIdle()

        val formulario = vm.estado.value.formulario!!
        assertTrue(formulario.erro!!.contains("Já existe"))
        assertEquals("meu texto", formulario.edicao.estilo)
        assertFalse(formulario.salvando)
    }

    @Test
    fun apagar_pede_confirmacao_e_so_entao_remove() = runTest {
        val falso = PerfisFalsos(listOf(perfil(1, "A"), perfil(2, "B")))
        val vm = PerfisViewModel(falso)
        vm.carregar()
        advanceUntilIdle()

        vm.pedirParaApagar(perfil(1, "A"))
        assertNotNull(vm.estado.value.apagando)
        assertTrue(falso.removidos.isEmpty())  // só pediu

        vm.confirmarApagar()
        advanceUntilIdle()

        assertEquals(listOf(1), falso.removidos)
        assertEquals(listOf(2), (vm.estado.value.carga as CargaDosPerfis.Pronta).perfis.map { it.id })
    }

    @Test
    fun cancelar_nao_apaga() = runTest {
        val falso = PerfisFalsos(listOf(perfil(1, "A")))
        val vm = PerfisViewModel(falso)

        vm.pedirParaApagar(perfil(1, "A"))
        vm.cancelarApagar()
        advanceUntilIdle()

        assertTrue(falso.removidos.isEmpty())
        assertNull(vm.estado.value.apagando)
    }

    @Test
    fun o_resumo_da_lista_mostra_a_categoria_e_o_estilo() {
        assertEquals("Anime · contorno fino", resumoDoPerfil(perfil(1, "x", "ANIME", "contorno fino")))
        assertEquals("Anime", resumoDoPerfil(perfil(1, "x", "ANIME")))
        assertEquals("só o estilo", resumoDoPerfil(perfil(1, "x", null, "só o estilo")))
        assertEquals("Sem categoria nem estilo", resumoDoPerfil(perfil(1, "x")))
    }

    // ---- os perfis de fábrica (PF3, PF4)

    @Test
    fun ver_abre_o_perfil_em_detalhes_e_fechar_volta_para_a_lista() = runTest {
        val vm = PerfisViewModel(PerfisFalsos(listOf(perfil(1, "Anime", "ANIME", deFabrica = true))))

        vm.ver(perfil(1, "Anime", "ANIME", deFabrica = true))
        assertEquals("Anime", vm.estado.value.detalhe!!.nome)

        vm.fecharDetalhe()
        assertNull(vm.estado.value.detalhe)
    }

    @Test
    fun o_perfil_de_fabrica_nao_abre_para_edicao_nem_para_apagar() = runTest {
        val vm = PerfisViewModel(PerfisFalsos())
        val anime = perfil(1, "Anime", "ANIME", deFabrica = true)

        vm.editar(anime)
        vm.pedirParaApagar(anime)

        assertNull(vm.estado.value.formulario)
        assertNull(vm.estado.value.apagando)
    }

    @Test
    fun criar_a_partir_de_um_de_fabrica_copia_os_textos_com_outro_nome_e_salva_como_novo() = runTest {
        val falso = PerfisFalsos()
        val vm = PerfisViewModel(falso)
        val anime = PerfilRenderizacao(1, "Anime", estilo = "cel-shading", paleta = "vibrante", categoria_estilo = "ANIME", de_fabrica = true)
        vm.ver(anime)

        vm.criarAPartirDe(anime)

        val formulario = vm.estado.value.formulario!!
        assertNull(vm.estado.value.detalhe)
        assertNull(formulario.perfilId)  // vai para o POST: não mexe no original
        assertEquals("Anime (cópia)", formulario.edicao.nome)
        assertEquals("cel-shading", formulario.edicao.estilo)
        assertEquals(CategoriaDeEstilo.ANIME, formulario.edicao.categoria)

        vm.salvar()
        advanceUntilIdle()
        assertEquals("Anime (cópia)", falso.criados.single().nome)
    }

    @Test
    fun editar_um_perfil_proprio_fecha_o_detalhe_e_abre_o_formulario() = runTest {
        val vm = PerfisViewModel(PerfisFalsos())
        val proprio = perfil(7, "Meu", "AQUARELA")
        vm.ver(proprio)

        vm.editar(proprio)

        assertNull(vm.estado.value.detalhe)
        assertEquals(7, vm.estado.value.formulario!!.perfilId)
    }

    @Test
    fun a_marca_de_fabrica_e_o_bloco_tecnico_vem_da_api() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val perfil = json.decodeFromString<PerfilRenderizacao>(
            """{"id":3,"nome":"Anime","categoria_estilo":"ANIME","de_fabrica":true,"bloco_tecnico":"Japanese anime illustration."}""",
        )

        assertTrue(perfil.de_fabrica)
        assertEquals("Japanese anime illustration.", perfil.bloco_tecnico)
        assertFalse(json.decodeFromString<PerfilRenderizacao>("""{"id":1,"nome":"x"}""").de_fabrica)  // servidor antigo
    }
}
