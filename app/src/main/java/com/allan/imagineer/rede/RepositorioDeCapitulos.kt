package com.allan.imagineer.rede

import com.allan.imagineer.local.ArmazemDeTextos
import com.allan.imagineer.local.ChaveDoCache
import com.allan.imagineer.local.IndiceLocal
import com.allan.imagineer.local.TextoGuardado
import com.allan.imagineer.local.melhorEsforco
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * O que as telas precisam saber fazer com capítulos. Interface, para o ViewModel
 * ser testado com uma versão falsa, sem rede.
 */
interface RepositorioDeCapitulos {

    /**
     * `GET /capitulos/{id}`: o capítulo com o texto inteiro. Se o texto já está no aparelho,
     * sai de lá, sem rede (item 7.0a).
     */
    suspend fun abrirCapitulo(capituloId: Int): ResultadoDaChamada<CapituloDetalhe>

    /**
     * `PATCH /capitulos/{id}`: muda o título e/ou marca o capítulo como ignorado.
     *
     * O servidor devolve o capítulo **com o texto**; o app lê só o resumo (os campos
     * extras são ignorados) e descarta o resto.
     */
    suspend fun ajustarCapitulo(
        capituloId: Int,
        ajuste: CapituloAjuste,
    ): ResultadoDaChamada<CapituloResumo>
}

/**
 * A implementação de verdade, sobre o Retrofit, com o texto guardado no aparelho
 * (item 7.0a, passo 1, regras L1 a L4 e L7).
 *
 * @param escopo onde roda o adiantamento do capítulo seguinte: um escopo que vive tanto
 * quanto o app, e não o da tela — o leitor pode sair do capítulo antes de o download acabar.
 */
class RepositorioDeCapitulosPeloRetrofit(
    private val provedor: ProvedorDeApi,
    private val indice: IndiceLocal,
    private val textos: ArmazemDeTextos,
    private val escopo: CoroutineScope,
) : RepositorioDeCapitulos {

    private var adiantamento: Job? = null

    override suspend fun abrirCapitulo(capituloId: Int): ResultadoDaChamada<CapituloDetalhe> {
        val servidor = provedor.emUso() ?: return provedor.semServidor()

        val doAparelho = lerDoAparelho(servidor.chave, capituloId)
        if (doAparelho != null) {
            adiantarOSeguinte(servidor, doAparelho)
            return ResultadoDaChamada.Sucesso(doAparelho)
        }

        val resultado = chamarApi { servidor.api.capitulo(capituloId) }
        if (resultado is ResultadoDaChamada.Sucesso) {
            // Guardar é cortesia (L2): se falhar, o capítulo é entregue do mesmo jeito.
            guardar(servidor.chave, resultado.dado)
            adiantarOSeguinte(servidor, resultado.dado)
        }
        return resultado
    }

    override suspend fun ajustarCapitulo(
        capituloId: Int,
        ajuste: CapituloAjuste,
    ): ResultadoDaChamada<CapituloResumo> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.ajustarCapitulo(capituloId, ajuste) }
    }

    /**
     * Regra L1: monta o capítulo só com o que há no aparelho — o registro do texto, o
     * arquivo, e o livro guardado (de onde vêm título, ordem e demais metadados). Falta
     * qualquer peça: `null`, e o chamador vai à rede.
     */
    private suspend fun lerDoAparelho(chave: ChaveDoCache, capituloId: Int): CapituloDetalhe? =
        melhorEsforco {
            val registro = indice.texto(chave, capituloId) ?: return@melhorEsforco null
            val livro = indice.livro(chave, registro.livroId) ?: return@melhorEsforco null
            val resumo = livro.detalhe.capitulos.firstOrNull { it.id == capituloId }
                ?: return@melhorEsforco null
            val texto = textos.ler(chave, registro.livroId, capituloId) ?: return@melhorEsforco null
            CapituloDetalhe(
                id = resumo.id,
                ordem = resumo.ordem,
                titulo = resumo.titulo,
                ignorado = resumo.ignorado,
                tamanho_do_texto = resumo.tamanho_do_texto,
                sugestoes_pendentes = resumo.sugestoes_pendentes,
                livro_id = registro.livroId,
                texto = texto,
            )
        }

    /** Regra L3: o arquivo primeiro, o registro depois — o registro só existe com arquivo. */
    private suspend fun guardar(chave: ChaveDoCache, capitulo: CapituloDetalhe) {
        melhorEsforco {
            val bytes = textos.gravar(chave, capitulo.livro_id, capitulo.id, capitulo.texto)
            indice.registrarTexto(chave, TextoGuardado(capitulo.id, capitulo.livro_id, bytes))
        }
    }

    /**
     * Regra L4: baixa em segundo plano o próximo capítulo não arquivado, para a virada de
     * página não esperar. Melhor esforço: falhas são ignoradas; se um adiantamento ainda
     * está rodando, não começa outro.
     */
    private suspend fun adiantarOSeguinte(servidor: ServidorEmUso, atual: CapituloDetalhe) {
        if (adiantamento?.isActive == true) return
        val livro = melhorEsforco { indice.livro(servidor.chave, atual.livro_id) } ?: return
        val seguinte = livro.detalhe.capitulos
            .filter { it.ordem > atual.ordem && !it.ignorado }
            .minByOrNull { it.ordem }
            ?: return

        // Tudo dentro de melhorEsforco: uma exceção solta num escopo de fundo derrubaria o
        // app por causa de algo que o leitor nem pediu.
        adiantamento = escopo.launch {
            melhorEsforco {
                if (indice.texto(servidor.chave, seguinte.id) != null) return@melhorEsforco
                val resultado = chamarApi { servidor.api.capitulo(seguinte.id) }
                if (resultado is ResultadoDaChamada.Sucesso) guardar(servidor.chave, resultado.dado)
            }
        }
    }
}
