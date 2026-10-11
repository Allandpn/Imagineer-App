package com.allan.imagineer.telas.menu

import com.allan.imagineer.rede.ContaDoServidor
import com.allan.imagineer.rede.CampoDeLimite
import com.allan.imagineer.rede.EuAtual
import com.allan.imagineer.rede.LimitesDoServidor
import com.allan.imagineer.rede.ProvedorDeIa
import com.allan.imagineer.rede.RepositorioDeContas
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val GB = 1024L * 1024 * 1024

private fun eu(
    nome: String? = "Maria",
    login: String? = "maria@exemplo.com",
    dono: Boolean = false,
    servidor: Boolean = false,
    livros: Int = 3,
    uso: Long = GB + GB / 2,
    cota: Int? = 5,
) = EuAtual(id = 2, login = login, nome = nome, dono = dono, usa_chaves_do_servidor = servidor, livros = livros, uso_em_bytes = uso, cota_efetiva_em_gb = cota)

private class ContasDoPerfil(var resposta: ResultadoDaChamada<EuAtual>) : RepositorioDeContas {
    var leituras = 0
    override suspend fun eu(): ResultadoDaChamada<EuAtual> { leituras++; return resposta }
    override suspend fun provedores(): ResultadoDaChamada<List<ProvedorDeIa>> = error("não usado")
    override suspend fun limites(): ResultadoDaChamada<LimitesDoServidor> = error("não usado")
    override suspend fun gravarLimites(novos: Map<CampoDeLimite, Int>): ResultadoDaChamada<LimitesDoServidor> = error("não usado")
    override suspend fun contas(): ResultadoDaChamada<List<ContaDoServidor>> = error("não usado")
    override suspend fun usarChavesDoServidor(contaId: Int, usa: Boolean): ResultadoDaChamada<ContaDoServidor> = error("não usado")
    override suspend fun mudarCota(contaId: Int, gb: Int?): ResultadoDaChamada<ContaDoServidor> = error("não usado")
}

/** A tela Perfil (item 7.5g, PF1): quem sou eu, o papel, as chaves e o espaço. */
@OptIn(ExperimentalCoroutinesApi::class)
class PerfilDaContaTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    @Test
    fun PF1_a_inicial_vem_do_nome_depois_do_login_e_sem_nada_e_uma_interrogacao() {
        assertEquals("M", inicialDoPerfil(eu(nome = "maria")))
        assertEquals("M", inicialDoPerfil(eu(nome = null, login = "maria@exemplo.com")))
        assertEquals("A", inicialDoPerfil(eu(nome = "  ", login = "allan@exemplo.com")))
        assertEquals("?", inicialDoPerfil(eu(nome = null, login = null)))
    }

    @Test
    fun PF1_o_papel_e_dono_ou_convidado() {
        assertEquals("Dono do servidor", papelDoPerfil(eu(dono = true)))
        assertEquals("Convidado(a)", papelDoPerfil(eu(dono = false)))
    }

    @Test
    fun PF1_diz_de_quem_sao_as_chaves_de_ia() {
        assertTrue(chavesDoPerfil(eu(servidor = true)).contains("do servidor"))
        assertTrue(chavesDoPerfil(eu(servidor = false)).contains("suas próprias"))
    }

    @Test
    fun PF1_o_uso_diz_livros_espaco_e_a_cota_quando_ha() {
        assertEquals("3 livros · 1,5 GB de 5 GB", usoDoPerfil(eu()))
        assertEquals("1 livro · 12,0 MB", usoDoPerfil(eu(livros = 1, uso = 12L * 1024 * 1024, cota = null)))
        assertEquals("0 livros · 0 KB", usoDoPerfil(eu(livros = 0, uso = 0, cota = null)))
    }

    @Test
    fun PF1_a_fracao_da_cota_vai_de_zero_a_um_e_o_dono_nao_tem() {
        assertEquals(0.3f, fracaoDaCota(eu(uso = GB + GB / 2, cota = 5))!!, 0.001f)
        assertEquals(1f, fracaoDaCota(eu(uso = 9 * GB, cota = 5))!!, 0.001f)
        assertEquals(0f, fracaoDaCota(eu(uso = 0, cota = 5))!!, 0.001f)
        assertNull(fracaoDaCota(eu(cota = null)))
        assertNull(fracaoDaCota(eu(cota = 0)))
    }

    @Test
    fun PF1_o_viewmodel_traz_quem_sou_eu_do_servidor() = runTest {
        val contas = ContasDoPerfil(ResultadoDaChamada.Sucesso(eu(dono = true, cota = null)))
        val vm = PerfilViewModel(contas)
        assertEquals(CargaDoPerfil.Carregando, vm.carga.value)

        vm.carregar(); advanceUntilIdle()

        assertEquals(CargaDoPerfil.Pronta(eu(dono = true, cota = null)), vm.carga.value)
    }

    @Test
    fun PF1_sem_servidor_ou_com_servidor_antigo_diz_o_motivo_e_tentar_de_novo_funciona() = runTest {
        val contas = ContasDoPerfil(ResultadoDaChamada.Falha("Não consegui falar com o servidor."))
        val vm = PerfilViewModel(contas)
        vm.carregar(); advanceUntilIdle()
        assertEquals(CargaDoPerfil.Indisponivel("Não consegui falar com o servidor."), vm.carga.value)

        contas.resposta = ResultadoDaChamada.Sucesso(eu())
        vm.carregar(); advanceUntilIdle()

        assertTrue(vm.carga.value is CargaDoPerfil.Pronta)
        assertEquals(2, contas.leituras)
    }

    @Test
    fun PF1_um_servidor_antigo_sem_os_campos_novos_continua_decodificando() {
        val json = Json { ignoreUnknownKeys = true }

        val antigo = json.decodeFromString<EuAtual>("""{"id":1,"login":null,"nome":"Dono","dono":true,"usa_chaves_do_servidor":true}""")

        assertEquals(0, antigo.livros)
        assertEquals(0L, antigo.uso_em_bytes)
        assertNull(antigo.cota_efetiva_em_gb)
        assertEquals("0 livros · 0 KB", usoDoPerfil(antigo))
    }

    @Test
    fun PF1_o_servidor_novo_traz_livros_uso_e_cota() {
        val json = Json { ignoreUnknownKeys = true }

        val novo = json.decodeFromString<EuAtual>(
            """{"id":2,"login":"maria@exemplo.com","nome":"Maria","dono":false,"usa_chaves_do_servidor":false,"livros":4,"uso_em_bytes":1610612736,"cota_efetiva_em_gb":5}""",
        )

        assertEquals("4 livros · 1,5 GB de 5 GB", usoDoPerfil(novo))
    }
}
