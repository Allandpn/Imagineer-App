package com.allan.imagineer.telas.menu

import com.allan.imagineer.dados.CofreEmMemoria
import com.allan.imagineer.rede.CampoDeLimite
import com.allan.imagineer.rede.ContaDoServidor
import com.allan.imagineer.rede.EuAtual
import com.allan.imagineer.rede.LimitesDoServidor
import com.allan.imagineer.rede.ProvedorDeIa
import com.allan.imagineer.rede.RepositorioDeContas
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.bytesParaLer
import com.allan.imagineer.rede.inteiroPositivoOuNulo
import com.allan.imagineer.telas.configuracao.EstadoDoTeste
import com.allan.imagineer.telas.configuracao.textoDaConexao
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

private val OPENROUTER = ProvedorDeIa("OPENROUTER", "OpenRouter", "X-Chave-API-OpenRouter", "texto, voz e imagem", servidor_fornece = true)
private val FAL = ProvedorDeIa("FAL", "fal.ai", "X-Chave-API-Fal", "imagem", servidor_fornece = false)

private fun limitesPadrao() = LimitesDoServidor(50, 60, 15, 100_000, 20, 5, uso_da_aplicacao_em_bytes = 1_288_490_189, recusa_novos_arquivos_a_partir_de_bytes = 19_327_352_832)

private fun conta(id: Int, dono: Boolean = false, chaves: Boolean = false, cota: Int? = null, efetiva: Int? = 5) =
    ContaDoServidor(
        id = id, login = "p$id@exemplo.com", nome = "Pessoa $id", dono = dono, usa_chaves_do_servidor = chaves, cota_em_gb = cota,
        cota_efetiva_em_gb = if (dono) null else cota ?: efetiva, livros = 2, uso_em_bytes = 3L * 1024 * 1024,
    )

private class ContasFalsas(
    var provedores: ResultadoDaChamada<List<ProvedorDeIa>> = ResultadoDaChamada.Sucesso(listOf(OPENROUTER, FAL)),
    var limitesLidos: ResultadoDaChamada<LimitesDoServidor> = ResultadoDaChamada.Sucesso(limitesPadrao()),
    var contasLidas: List<ContaDoServidor> = listOf(conta(1, dono = true, chaves = true), conta(2)),
) : RepositorioDeContas {
    val limitesEnviados = mutableListOf<Map<CampoDeLimite, Int>>()
    val cotasEnviadas = mutableListOf<Pair<Int, Int?>>()
    var recusa: String? = null

    override suspend fun provedores() = provedores
    override suspend fun eu(): ResultadoDaChamada<EuAtual> = ResultadoDaChamada.Sucesso(EuAtual(1, dono = true))
    override suspend fun limites() = limitesLidos

    override suspend fun gravarLimites(novos: Map<CampoDeLimite, Int>): ResultadoDaChamada<LimitesDoServidor> {
        limitesEnviados += novos
        return ResultadoDaChamada.Sucesso(limitesPadrao().copy(tamanho_maximo_do_video_mb = novos[CampoDeLimite.VIDEO] ?: 50))
    }

    override suspend fun contas(): ResultadoDaChamada<List<ContaDoServidor>> = ResultadoDaChamada.Sucesso(contasLidas)

    override suspend fun usarChavesDoServidor(contaId: Int, usa: Boolean): ResultadoDaChamada<ContaDoServidor> =
        recusa?.let { ResultadoDaChamada.Falha(it, 422) } ?: ResultadoDaChamada.Sucesso(contasLidas.first { it.id == contaId }.copy(usa_chaves_do_servidor = usa))

    override suspend fun mudarCota(contaId: Int, gb: Int?): ResultadoDaChamada<ContaDoServidor> {
        cotasEnviadas += contaId to gb
        return recusa?.let { ResultadoDaChamada.Falha(it, 422) } ?: ResultadoDaChamada.Sucesso(contasLidas.first { it.id == contaId }.copy(cota_em_gb = gb))
    }
}

/** As regras puras das telas de contas (AP4, AP5, AP7). */
class RegrasDasContasTest {

    @Test
    fun a_chave_local_vale_e_sem_ela_a_do_servidor_se_ele_fornece() {
        assertEquals(SituacaoDaChave.NO_APARELHO, situacaoDaChave(OPENROUTER, temChaveLocal = true))
        assertEquals(SituacaoDaChave.NO_APARELHO, situacaoDaChave(FAL, temChaveLocal = true))
        assertEquals(SituacaoDaChave.DO_SERVIDOR, situacaoDaChave(OPENROUTER, temChaveLocal = false))
        assertEquals(SituacaoDaChave.SEM_CHAVE, situacaoDaChave(FAL, temChaveLocal = false))
    }

    @Test
    fun a_conexao_diz_quem_e_de_onde_vem_a_chave() {
        val dono = EuAtual(1, login = "allan@exemplo.com", nome = "Allan", dono = true)

        assertEquals("Conectado como Allan (dono). As chamadas de IA usam a chave do servidor.", textoDaConexao(EstadoDoTeste.Conectado(true, dono)))
        assertEquals(
            "Conectado como ana@exemplo.com. As chamadas de IA precisam da sua chave: cadastre em Configurações → Chaves de IA.",
            textoDaConexao(EstadoDoTeste.Conectado(false, EuAtual(2, login = "ana@exemplo.com"))),
        )
        assertEquals("Conectado. As chamadas de IA usam a chave do servidor.", textoDaConexao(EstadoDoTeste.Conectado(true)))  // servidor antigo, sem /eu
    }

    @Test
    fun o_rotulo_de_quem_cai_do_nome_para_o_login_e_para_a_conta() {
        assertEquals("Allan", EuAtual(1, login = "a@b.c", nome = "Allan").rotulo)
        assertEquals("a@b.c", EuAtual(1, login = "a@b.c", nome = " ").rotulo)
        assertEquals("o dono do servidor", EuAtual(1, dono = true).rotulo)
        assertEquals("conta 7", EuAtual(7).rotulo)
    }

    @Test
    fun bytes_e_numeros_se_leem_em_portugues() {
        assertEquals("1,2 GB", bytesParaLer(1_288_490_189))
        assertEquals("3,0 MB", bytesParaLer(3L * 1024 * 1024))
        assertEquals("512 KB", bytesParaLer(512L * 1024))
        assertEquals(10, inteiroPositivoOuNulo(" 10 "))
        assertNull(inteiroPositivoOuNulo("0"))
        assertNull(inteiroPositivoOuNulo("-3"))
        assertNull(inteiroPositivoOuNulo("dez"))
        assertNull(inteiroPositivoOuNulo(""))
    }

    @Test
    fun as_linhas_da_administracao_dizem_o_disco_a_conta_e_a_cota() {
        assertEquals("Em uso: 1,2 GB de 20 GB (novos arquivos são recusados a partir de 18,0 GB).", descreverUsoDoDisco(limitesPadrao()))
        assertEquals("Pessoa 2 · 2 livro(s) · 3,0 MB", descreverConta(conta(2)))
        assertEquals("Pessoa 1 (dono) · 2 livro(s) · 3,0 MB", descreverConta(conta(1, dono = true)))
        assertEquals("Sem cota (dono)", descreverCota(conta(1, dono = true)))
        assertEquals("Cota padrão: 5 GB", descreverCota(conta(2)))
        assertEquals("Cota própria: 10 GB", descreverCota(conta(2, cota = 10)))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChavesDeIaViewModelTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    @Test
    fun a_lista_vem_do_servidor_e_a_situacao_do_cofre() = runTest {
        val cofre = CofreEmMemoria(mapOf("X-Chave-API-Fal" to "fal-chave-comprida-1234"))
        val vm = ChavesDeIaViewModel(ContasFalsas(), cofre)

        vm.carregar(); advanceUntilIdle()

        assertFalse(vm.estado.value.carregando)
        assertEquals(listOf(SituacaoDaChave.DO_SERVIDOR, SituacaoDaChave.NO_APARELHO), vm.estado.value.linhas.map { it.situacao })
        assertEquals("fal-c…1234", vm.estado.value.linhas[1].mascara)
        assertNull(vm.estado.value.linhas[0].mascara)
    }

    @Test
    fun salvar_guarda_no_cofre_com_o_cabecalho_do_provedor_e_remover_tira() = runTest {
        val cofre = CofreEmMemoria()
        val vm = ChavesDeIaViewModel(ContasFalsas(), cofre)
        vm.carregar(); advanceUntilIdle()

        assertTrue(vm.salvar(FAL, "  fal-chave-comprida-1234  "))
        assertEquals(mapOf("X-Chave-API-Fal" to "fal-chave-comprida-1234"), cofre.chaves())  // sem os espaços das pontas
        assertEquals(SituacaoDaChave.NO_APARELHO, vm.estado.value.linhas[1].situacao)

        vm.remover(FAL)
        assertTrue(cofre.chaves().isEmpty())
        assertEquals(SituacaoDaChave.SEM_CHAVE, vm.estado.value.linhas[1].situacao)
    }

    @Test
    fun uma_chave_que_nao_cabe_no_header_nao_e_guardada() = runTest {
        val cofre = CofreEmMemoria()
        val vm = ChavesDeIaViewModel(ContasFalsas(), cofre)
        vm.carregar(); advanceUntilIdle()

        assertFalse(vm.salvar(FAL, "chave com espaço no meio"))

        assertTrue(cofre.chaves().isEmpty())
        assertTrue(vm.estado.value.aviso!!.contains("espaço"))
    }

    @Test
    fun sem_servidor_a_lista_fica_vazia_e_avisa() = runTest {
        val vm = ChavesDeIaViewModel(ContasFalsas(provedores = ResultadoDaChamada.Falha("fora do ar")), CofreEmMemoria())

        vm.carregar(); advanceUntilIdle()

        assertTrue(vm.estado.value.linhas.isEmpty())
        assertTrue(vm.estado.value.aviso!!.contains("fora do ar"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdministracaoViewModelTest {

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpar() = Dispatchers.resetMain()

    private fun textosIguaisAosAtuais() = CampoDeLimite.entries.associateWith { it.valorDe(limitesPadrao()).toString() }.toMutableMap()

    @Test
    fun o_dono_ve_os_limites_e_as_contas() = runTest {
        val vm = AdministracaoViewModel(ContasFalsas())

        vm.carregar(); advanceUntilIdle()

        assertTrue(vm.estado.value.ehDono)
        assertEquals(50, vm.estado.value.limites!!.tamanho_maximo_do_video_mb)
        assertEquals(listOf(1, 2), vm.estado.value.contas.map { it.id })
    }

    @Test
    fun quem_nao_e_dono_recebe_404_e_so_ve_o_aviso_de_acesso() = runTest {
        val vm = AdministracaoViewModel(ContasFalsas(limitesLidos = ResultadoDaChamada.Falha("Not Found", 404)))

        vm.carregar(); advanceUntilIdle()

        assertFalse(vm.estado.value.ehDono)
        assertNull(vm.estado.value.limites)
        assertNull(vm.estado.value.aviso)
    }

    @Test
    fun outra_falha_nao_e_confundida_com_nao_ser_dono() = runTest {
        val vm = AdministracaoViewModel(ContasFalsas(limitesLidos = ResultadoDaChamada.Falha("Não consegui falar com o servidor.")))

        vm.carregar(); advanceUntilIdle()

        assertTrue(vm.estado.value.ehDono)
        assertEquals("Não consegui falar com o servidor.", vm.estado.value.aviso)
    }

    @Test
    fun so_os_limites_que_mudaram_vao_ao_servidor() = runTest {
        val falso = ContasFalsas()
        val vm = AdministracaoViewModel(falso)
        vm.carregar(); advanceUntilIdle()
        val textos = textosIguaisAosAtuais().also { it[CampoDeLimite.VIDEO] = "80" }

        assertTrue(vm.gravarLimites(textos)); advanceUntilIdle()

        assertEquals(listOf(mapOf(CampoDeLimite.VIDEO to 80)), falso.limitesEnviados)
        assertEquals(80, vm.estado.value.limites!!.tamanho_maximo_do_video_mb)
    }

    @Test
    fun um_limite_que_nao_e_inteiro_positivo_nem_chega_ao_servidor() = runTest {
        val falso = ContasFalsas()
        val vm = AdministracaoViewModel(falso)
        vm.carregar(); advanceUntilIdle()
        val textos = textosIguaisAosAtuais().also { it[CampoDeLimite.COTA] = "0" }

        assertFalse(vm.gravarLimites(textos)); advanceUntilIdle()

        assertTrue(falso.limitesEnviados.isEmpty())
        assertTrue(vm.estado.value.aviso!!.startsWith("Cota por pessoa"))
    }

    @Test
    fun a_cota_em_branco_volta_ao_padrao_e_o_numero_vira_cota_propria() = runTest {
        val falso = ContasFalsas()
        val vm = AdministracaoViewModel(falso)
        vm.carregar(); advanceUntilIdle()

        vm.mudarCota(vm.estado.value.contas[1], "10"); advanceUntilIdle()
        vm.mudarCota(vm.estado.value.contas[1], "  "); advanceUntilIdle()
        vm.mudarCota(vm.estado.value.contas[1], "abc"); advanceUntilIdle()

        assertEquals(listOf(2 to 10, 2 to null), falso.cotasEnviadas)  // "abc" nem foi
        assertTrue(vm.estado.value.aviso!!.startsWith("Cota:"))
    }

    @Test
    fun a_recusa_do_servidor_ao_mexer_no_dono_aparece_e_a_lista_nao_muda() = runTest {
        val falso = ContasFalsas().also { it.recusa = "O dono não pode ficar sem as chaves do servidor." }
        val vm = AdministracaoViewModel(falso)
        vm.carregar(); advanceUntilIdle()

        vm.usarChavesDoServidor(vm.estado.value.contas[0], false); advanceUntilIdle()

        assertEquals("O dono não pode ficar sem as chaves do servidor.", vm.estado.value.aviso)
        assertTrue(vm.estado.value.contas[0].usa_chaves_do_servidor)
    }

    @Test
    fun liberar_as_chaves_troca_a_conta_na_lista() = runTest {
        val vm = AdministracaoViewModel(ContasFalsas())
        vm.carregar(); advanceUntilIdle()

        vm.usarChavesDoServidor(vm.estado.value.contas[1], true); advanceUntilIdle()

        assertTrue(vm.estado.value.contas[1].usa_chaves_do_servidor)
    }
}
