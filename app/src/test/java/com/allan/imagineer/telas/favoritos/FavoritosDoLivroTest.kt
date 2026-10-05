package com.allan.imagineer.telas.favoritos

import com.allan.imagineer.rede.AlvoDeFavorito
import com.allan.imagineer.rede.Favorito
import com.allan.imagineer.rede.RepositorioDeFavoritos
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.TipoDeFavorito
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun favorito(id: Int, tipo: String, capitulo: Int? = null, posicao: Int? = null, elemento: Int? = null, frame: Int? = null, imagem: Int? = null) =
    Favorito(id = id, livro_id = 1, tipo = tipo, rotulo = "r$id", capitulo_id = capitulo, posicao = posicao, elemento_id = elemento, frame_id = frame, imagem_id = imagem)

private class FavoritosFalsos(var existentes: List<Favorito> = emptyList(), var falhar: String? = null) : RepositorioDeFavoritos {
    val favoritados = mutableListOf<AlvoDeFavorito>()
    val desfavoritados = mutableListOf<Int>()
    private var proximo = 100

    override suspend fun listar(livroId: Int, tipo: TipoDeFavorito?) = ResultadoDaChamada.Sucesso(existentes)

    override suspend fun favoritar(livroId: Int, alvo: AlvoDeFavorito): ResultadoDaChamada<Favorito> {
        falhar?.let { return ResultadoDaChamada.Falha(it, 422) }
        favoritados += alvo
        return ResultadoDaChamada.Sucesso(favorito(proximo++, alvo.tipo.name))
    }

    override suspend fun desfavoritar(favoritoId: Int): ResultadoDaChamada<Unit> {
        falhar?.let { return ResultadoDaChamada.Falha(it) }
        desfavoritados += favoritoId
        return ResultadoDaChamada.Sucesso(Unit)
    }
}

/** Os favoritos: o alvo, a estrela e a lista do livro (RL31 a RL37). */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritosDoLivroTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    // ---- o alvo

    @Test
    fun cada_alvo_monta_o_corpo_do_servidor() {
        assertEquals("LIVRO", AlvoDeFavorito.Livro.corpo()["tipo"]!!.jsonPrimitive.content)
        val paragrafo = AlvoDeFavorito.Paragrafo(capituloId = 5, posicao = 120).corpo()
        assertEquals(listOf("tipo", "capitulo_id", "posicao"), paragrafo.keys.toList())
        assertEquals(5, paragrafo["capitulo_id"]!!.jsonPrimitive.int)
        assertEquals(120, paragrafo["posicao"]!!.jsonPrimitive.int)
        assertEquals(7, AlvoDeFavorito.Elemento(7).corpo()["elemento_id"]!!.jsonPrimitive.int)
        assertEquals(8, AlvoDeFavorito.Cena(8).corpo()["frame_id"]!!.jsonPrimitive.int)
        assertEquals(9, AlvoDeFavorito.Imagem(9).corpo()["imagem_id"]!!.jsonPrimitive.int)
    }

    @Test
    fun o_alvo_reconhece_o_favorito_dele_e_so_ele() {
        val lista = listOf(
            favorito(1, "PARAGRAFO", capitulo = 5, posicao = 120),
            favorito(2, "ELEMENTO", elemento = 7),
            favorito(3, "CENA", frame = 8),
            favorito(4, "IMAGEM", imagem = 9, frame = 8),
            favorito(5, "LIVRO"),
        )

        assertEquals(1, favoritoDe(lista, AlvoDeFavorito.Paragrafo(5, 120))?.id)
        assertNull(favoritoDe(lista, AlvoDeFavorito.Paragrafo(5, 121)))  // outro parágrafo
        assertNull(favoritoDe(lista, AlvoDeFavorito.Paragrafo(6, 120)))  // outro capítulo
        assertEquals(2, favoritoDe(lista, AlvoDeFavorito.Elemento(7))?.id)
        assertEquals(3, favoritoDe(lista, AlvoDeFavorito.Cena(8))?.id)  // a imagem 9 tem frame 8, mas não é a cena
        assertEquals(4, favoritoDe(lista, AlvoDeFavorito.Imagem(9))?.id)
        assertEquals(5, favoritoDe(lista, AlvoDeFavorito.Livro)?.id)
        assertNull(favoritoDe(lista, AlvoDeFavorito.Elemento(99)))
    }

    @Test
    fun o_favorito_de_um_tipo_que_o_app_nao_conhece_nao_quebra() {
        val futuro = favorito(1, "CAPITULO")

        assertNull(futuro.tipoDoFavorito)
        assertEquals(TipoDeFavorito.CENA, favorito(2, "CENA").tipoDoFavorito)
        assertEquals(listOf("Livros", "Parágrafos", "Elementos", "Cenas", "Imagens"), TipoDeFavorito.entries.map { it.plural })
    }

    @Test
    fun o_servidor_antigo_ou_novo_decodifica_com_ignoreUnknownKeys() {
        val json = Json { ignoreUnknownKeys = true }

        val f = json.decodeFromString<Favorito>("""{"id":1,"livro_id":2,"tipo":"PARAGRAFO","rotulo":"Era uma vez","capitulo_id":5,"posicao":0,"novo":1}""")

        assertEquals("Era uma vez", f.rotulo)
        assertEquals(TipoDeFavorito.PARAGRAFO, f.tipoDoFavorito)
        assertNull(f.elemento_id)
    }

    // ---- a tela de Favoritos

    @Test
    fun a_tela_lista_so_o_de_dentro_do_livro_e_filtra_por_tipo() {
        val lista = listOf(
            favorito(1, "PARAGRAFO", capitulo = 5, posicao = 0),
            favorito(2, "ELEMENTO", elemento = 7),
            favorito(3, "LIVRO"),
            favorito(4, "CAPITULO"),  // um tipo de um servidor mais novo
            favorito(5, "CENA", frame = 8),
        )

        assertEquals(listOf(1, 2, 5), favoritosParaMostrar(lista, null).map { it.id })  // sem o livro e sem o tipo desconhecido
        assertEquals(listOf(2), favoritosParaMostrar(lista, TipoDeFavorito.ELEMENTO).map { it.id })
        assertTrue(favoritosParaMostrar(lista, TipoDeFavorito.IMAGEM).isEmpty())
        assertTrue(favoritosParaMostrar(lista, TipoDeFavorito.LIVRO).isEmpty())  // o livro favorito aparece na biblioteca, não aqui
    }

    @Test
    fun o_local_do_favorito_diz_o_capitulo() {
        val f = favorito(1, "PARAGRAFO", capitulo = 5, posicao = 0)

        assertEquals("Capítulo 3 · A chegada", localDoFavorito(f.copy(ordem_do_capitulo = 3, titulo_do_capitulo = "A chegada")))
        assertEquals("Capítulo 3", localDoFavorito(f.copy(ordem_do_capitulo = 3, titulo_do_capitulo = " ")))
        assertEquals("A chegada", localDoFavorito(f.copy(titulo_do_capitulo = "A chegada")))
        assertNull(localDoFavorito(favorito(2, "ELEMENTO", elemento = 7)))
    }

    @Test
    fun so_abre_o_favorito_que_sabe_para_onde_ir() {
        assertTrue(podeAbrirOFavorito(favorito(1, "PARAGRAFO", capitulo = 5, posicao = 0)))
        assertFalse(podeAbrirOFavorito(favorito(2, "PARAGRAFO", capitulo = 5)))  // sem a posição
        assertTrue(podeAbrirOFavorito(favorito(3, "ELEMENTO", elemento = 7)))
        assertTrue(podeAbrirOFavorito(favorito(4, "CENA", capitulo = 5, frame = 8)))
        assertFalse(podeAbrirOFavorito(favorito(5, "IMAGEM", imagem = 9)))  // sem o frame nem o capítulo
        assertFalse(podeAbrirOFavorito(favorito(6, "CAPITULO")))
    }

    @Test
    fun remover_tira_da_lista_e_volta_se_o_servidor_falhar() = runTest {
        val falso = FavoritosFalsos(listOf(favorito(1, "ELEMENTO", elemento = 7), favorito(2, "CENA", frame = 8)))
        val vm = FavoritosDoLivroViewModel(1, falso)
        vm.carregar(); advanceUntilIdle()

        vm.remover(vm.estado.value.favoritos.first()); advanceUntilIdle()
        assertEquals(listOf(2), vm.estado.value.favoritos.map { it.id })
        assertEquals(listOf(1), falso.desfavoritados)

        falso.falhar = "fora do ar"
        vm.remover(vm.estado.value.favoritos.single()); advanceUntilIdle()
        assertEquals(listOf(2), vm.estado.value.favoritos.map { it.id })  // voltou
        assertFalse(vm.estado.value.aviso.isNullOrBlank())
    }

    // ---- o ViewModel

    @Test
    fun carrega_os_favoritos_do_livro() = runTest {
        val vm = FavoritosDoLivroViewModel(1, FavoritosFalsos(listOf(favorito(1, "ELEMENTO", elemento = 7))))

        vm.carregar(); advanceUntilIdle()

        assertTrue(vm.estado.value.carregados)
        assertEquals(1, vm.estado.value.favoritos.size)
    }

    @Test
    fun alternar_favorita_o_que_nao_e_e_desfavorita_o_que_ja_e() = runTest {
        val falso = FavoritosFalsos()
        val vm = FavoritosDoLivroViewModel(1, falso)
        vm.carregar(); advanceUntilIdle()

        vm.alternar(AlvoDeFavorito.Elemento(7)); advanceUntilIdle()
        assertEquals(listOf<AlvoDeFavorito>(AlvoDeFavorito.Elemento(7)), falso.favoritados)
        assertEquals(1, vm.estado.value.favoritos.size)

        val criado = vm.estado.value.favoritos.single()
        // o falso devolve um favorito sem o elemento_id; para o segundo toque achar o favorito, a lista traz o que o servidor devolveu:
        falso.existentes = listOf(favorito(criado.id, "ELEMENTO", elemento = 7))
        vm.carregar(); advanceUntilIdle()
        vm.alternar(AlvoDeFavorito.Elemento(7)); advanceUntilIdle()

        assertEquals(listOf(criado.id), falso.desfavoritados)
        assertTrue(vm.estado.value.favoritos.isEmpty())
    }

    @Test
    fun se_o_servidor_recusa_a_estrela_nao_fica_e_avisa() = runTest {
        val vm = FavoritosDoLivroViewModel(1, FavoritosFalsos(falhar = "fora do ar"))
        vm.carregar(); advanceUntilIdle()

        vm.alternar(AlvoDeFavorito.Elemento(7)); advanceUntilIdle()

        assertTrue(vm.estado.value.favoritos.isEmpty())
        assertTrue(vm.estado.value.aviso!!.contains("fora do ar"))
        vm.avisoLido()
        assertNull(vm.estado.value.aviso)
    }

    @Test
    fun se_nao_consegue_desfavoritar_a_estrela_volta() = runTest {
        val falso = FavoritosFalsos(listOf(favorito(3, "ELEMENTO", elemento = 7)), falhar = "fora do ar")
        val vm = FavoritosDoLivroViewModel(1, falso)
        vm.carregar(); advanceUntilIdle()

        vm.alternar(AlvoDeFavorito.Elemento(7)); advanceUntilIdle()

        assertEquals(listOf(3), vm.estado.value.favoritos.map { it.id })  // voltou
        assertFalse(vm.estado.value.aviso.isNullOrBlank())
    }

    @Test
    fun a_resposta_idempotente_nao_duplica_a_estrela() = runTest {
        val falso = FavoritosFalsos()
        val vm = FavoritosDoLivroViewModel(1, falso)
        vm.carregar(); advanceUntilIdle()

        vm.alternar(AlvoDeFavorito.Livro); advanceUntilIdle()

        assertEquals(1, vm.estado.value.favoritos.size)
        assertEquals("LIVRO", vm.estado.value.favoritos.single().tipo)
    }
}
