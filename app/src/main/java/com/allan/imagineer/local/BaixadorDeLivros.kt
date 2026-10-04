package com.allan.imagineer.local

import com.allan.imagineer.rede.MidiasDoLivro
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.TextoDoCapitulo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File

// "Baixar para ler offline" (item 7.0a, passo 4; PL3 a PL5): o texto de todos os capítulos e as três versões de cada imagem.

/** Como o aparelho está ligado à internet agora (PL3: "Só em Wi-Fi"). */
enum class TipoDeRede { NENHUMA, MOVEL, WIFI }

/** De onde o aparelho sabe a [TipoDeRede]. Interface, para o baixador ser testado sem Android. */
fun interface EstadoDaRede {
    fun tipo(): TipoDeRede
}

/** O que o baixador pede ao servidor. Interface, para ser testada sem rede; a de verdade fala com a API (`ProvedorDeApi`). */
interface FonteDoDownload {
    /** A chave do cache do servidor em uso, ou `null` se não há endereço configurado. */
    suspend fun chave(): ChaveDoCache?

    /** `GET /livros/{id}/textos`: o texto de todos os capítulos, numa chamada. */
    suspend fun textos(livroId: Int): ResultadoDaChamada<List<TextoDoCapitulo>>

    /** `GET /livros/{id}/midias`: o manifesto das imagens, com o tamanho de cada original. */
    suspend fun midias(livroId: Int): ResultadoDaChamada<MidiasDoLivro>

    /** `GET /imagens/{id}/arquivo?tamanho=`: grava a imagem em [destino]. `Falha` se não deu. */
    suspend fun baixarImagem(imagemId: Int, tamanho: String, destino: File): ResultadoDaChamada<Unit>
}

/** O andamento de um download: arquivos feitos de arquivos totais, e os bytes já gravados. */
data class Progresso(val arquivosFeitos: Int, val arquivosTotal: Int, val bytesFeitos: Long) {
    /** De 0 a 1, pelos arquivos. */
    val fracao: Float get() = if (arquivosTotal <= 0) 0f else (arquivosFeitos.toFloat() / arquivosTotal).coerceIn(0f, 1f)
}

sealed interface EstadoDoDownload {
    data object NaoBaixado : EstadoDoDownload
    data class Baixando(val progresso: Progresso) : EstadoDoDownload
    /** Parado (por pedido ou porque não dá para seguir agora); tocar em baixar de novo **retoma**. [motivo] diz por quê. */
    data class Pausado(val progresso: Progresso, val motivo: String) : EstadoDoDownload
    data class Baixado(val em: Long, val bytes: Long) : EstadoDoDownload
    data class Falhou(val motivo: String) : EstadoDoDownload
}

/** O que falta baixar de um livro, para a confirmação "Baixar — 240 MB" (PL3). */
data class Estimativa(val imagens: Int, val bytesDosOriginais: Long, val bytesJaBaixados: Long) {
    /** O que ainda vem pela rede (só os originais; as reduzidas, pequenas, vêm por cima). */
    val bytesRestantes: Long get() = (bytesDosOriginais - bytesJaBaixados).coerceAtLeast(0)
}

/**
 * Baixa livros para ler sem conexão e guarda o estado de cada um. Vive no escopo do app: sair da tela não interrompe o download.
 *
 * - **Retoma:** o que já está gravado é pulado (A5: cada arquivo é gravado inteiro ou nada).
 * - **"Só em Wi-Fi":** em dados móveis, **para** dizendo isso; tocar de novo retoma.
 * - **Cancelar** apaga o que foi baixado (**Pausar** guarda).
 */
class BaixadorDeLivros(
    private val fonte: FonteDoDownload,
    private val textos: ArmazemDeTextos,
    private val indice: IndiceLocal,
    private val imagens: ArmazemDeImagens,
    private val registro: RegistroDeDownloads,
    private val rede: EstadoDaRede,
    private val escopo: CoroutineScope,
    private val agora: () -> Long = System::currentTimeMillis,
) {
    private val _estados = MutableStateFlow<Map<Int, EstadoDoDownload>>(emptyMap())
    val estados: StateFlow<Map<Int, EstadoDoDownload>> = _estados.asStateFlow()
    private val trabalhos = mutableMapOf<Int, Job>()

    private fun definir(livroId: Int, estado: EstadoDoDownload) = _estados.update { it + (livroId to estado) }

    fun estadoDe(livroId: Int): EstadoDoDownload = _estados.value[livroId] ?: EstadoDoDownload.NaoBaixado

    /** Lê do registro se o livro já está Baixado (ao abrir a tela do livro). Não mexe num download em andamento. */
    suspend fun carregar(livroId: Int) {
        if (livroId in trabalhos && trabalhos[livroId]?.isActive == true) return
        val chave = fonte.chave() ?: return
        val salvo = runCatching { registro.baixado(chave, livroId) }.getOrNull()
        definir(livroId, if (salvo != null) EstadoDoDownload.Baixado(salvo.baixadoEm, salvo.bytes) else EstadoDoDownload.NaoBaixado)
    }

    /** Quanto ainda falta baixar do livro (PL3: "Baixar — 240 MB"). */
    suspend fun estimar(livroId: Int): ResultadoDaChamada<Estimativa> {
        val chave = fonte.chave() ?: return ResultadoDaChamada.Falha("O endereço do servidor ainda não foi configurado.")
        return when (val r = fonte.midias(livroId)) {
            is ResultadoDaChamada.Falha -> r
            is ResultadoDaChamada.Sucesso -> {
                val jaTem = r.dado.imagens.sumOf { imagens.arquivo(chave, it.imagem_id, "original")?.length() ?: 0L }
                ResultadoDaChamada.Sucesso(Estimativa(r.dado.imagens.size, r.dado.total_em_bytes, jaTem))
            }
        }
    }

    /** Começa (ou retoma) o download do livro. Um por livro de cada vez. */
    fun baixar(livroId: Int, somenteWifi: Boolean = true) {
        if (trabalhos[livroId]?.isActive == true) return
        trabalhos[livroId] = escopo.launch { executar(livroId, somenteWifi) }
    }

    /** Para o download guardando o que já veio; tocar em baixar retoma. */
    fun pausar(livroId: Int) {
        val atual = estadoDe(livroId)
        trabalhos.remove(livroId)?.cancel()
        if (atual is EstadoDoDownload.Baixando) definir(livroId, EstadoDoDownload.Pausado(atual.progresso, "Pausado"))
    }

    /** Para o download **e apaga** o que veio dele. */
    suspend fun cancelar(livroId: Int) {
        trabalhos.remove(livroId)?.cancel()
        remover(livroId)
    }

    /** "Remover download" (PL4): apaga as imagens e o texto baixados e a marca. O texto lido volta sozinho, ao abrir o capítulo. */
    suspend fun remover(livroId: Int) {
        trabalhos.remove(livroId)?.cancel()
        val chave = fonte.chave()
        if (chave != null) {
            val ids = runCatching { registro.baixado(chave, livroId)?.imagensIds }.getOrNull() ?: planejadas[livroId].orEmpty()
            runCatching { imagens.apagar(chave, ids) }
            runCatching { textos.apagarLivro(chave, livroId) }
            runCatching { registro.apagar(chave, livroId) }
        }
        planejadas.remove(livroId)
        definir(livroId, EstadoDoDownload.NaoBaixado)
    }

    /** As imagens do manifesto de cada livro durante o download, para um cancelamento saber o que apagar. */
    private val planejadas = mutableMapOf<Int, List<Int>>()

    /** PL5: um livro Baixado, aberto com conexão em Wi-Fi, baixa em segundo plano o que o servidor ganhou depois. Sem barulho se nada falta. */
    fun completarSeBaixado(livroId: Int) {
        if (estadoDe(livroId) !is EstadoDoDownload.Baixado || rede.tipo() != TipoDeRede.WIFI) return
        if (trabalhos[livroId]?.isActive == true) return
        trabalhos[livroId] = escopo.launch { executar(livroId, somenteWifi = true, silencioso = true) }
    }

    private suspend fun executar(livroId: Int, somenteWifi: Boolean, silencioso: Boolean = false) {
        val chave = fonte.chave()
        if (chave == null) {
            definir(livroId, EstadoDoDownload.Falhou("O endereço do servidor ainda não foi configurado."))
            return
        }
        val anterior = estadoDe(livroId)
        fun falhou(motivo: String) { if (!silencioso) definir(livroId, EstadoDoDownload.Falhou(motivo)) else definir(livroId, anterior) }
        if (!silencioso) definir(livroId, EstadoDoDownload.Baixando(Progresso(0, 0, 0)))

        // 1. O texto de todos os capítulos (uma chamada); o que já está gravado não se regrava.
        val lidos = when (val r = fonte.textos(livroId)) {
            is ResultadoDaChamada.Sucesso -> r.dado
            is ResultadoDaChamada.Falha -> return falhou(r.motivo)
        }
        for (capitulo in lidos) {
            if (textos.ler(chave, livroId, capitulo.capitulo_id) == null) {
                val bytes = runCatching { textos.gravar(chave, livroId, capitulo.capitulo_id, capitulo.texto) }.getOrNull()
                    ?: return falhou("Não consegui guardar o texto no aparelho.")
                runCatching { indice.registrarTexto(chave, TextoGuardado(capitulo.capitulo_id, livroId, bytes)) }
            }
        }

        // 2. As imagens: miniatura, leitura e original de cada uma, pulando o que já está gravado.
        val manifesto = when (val r = fonte.midias(livroId)) {
            is ResultadoDaChamada.Sucesso -> r.dado
            is ResultadoDaChamada.Falha -> return falhou(r.motivo)
        }
        val ids = manifesto.imagens.map { it.imagem_id }
        planejadas[livroId] = ids
        val tarefas = ids.flatMap { id -> TAMANHOS_DE_IMAGEM.map { id to it } }
        var feitos = 0
        var bytes = 0L
        for ((id, tamanho) in tarefas) {
            currentCoroutineContext().ensureActive()
            val existente = imagens.arquivo(chave, id, tamanho)
            if (existente != null) {
                feitos++
                bytes += existente.length()
                continue
            }
            val progresso = Progresso(feitos, tarefas.size, bytes)
            when (rede.tipo()) {
                TipoDeRede.NENHUMA -> return if (silencioso) definir(livroId, anterior) else definir(livroId, EstadoDoDownload.Pausado(progresso, "Sem conexão"))
                TipoDeRede.MOVEL -> if (somenteWifi) {
                    return if (silencioso) definir(livroId, anterior) else definir(livroId, EstadoDoDownload.Pausado(progresso, "Aguardando Wi-Fi"))
                }
                TipoDeRede.WIFI -> {}
            }
            if (!silencioso) definir(livroId, EstadoDoDownload.Baixando(progresso))
            var motivo: String? = null
            val gravou = imagens.gravar(chave, id, tamanho) { temporario ->
                when (val r = fonte.baixarImagem(id, tamanho, temporario)) {
                    is ResultadoDaChamada.Sucesso -> true
                    is ResultadoDaChamada.Falha -> { motivo = r.motivo; false }
                }
            }
            if (!gravou) return falhou(motivo ?: "Não consegui baixar uma imagem.")
            feitos++
            bytes += imagens.arquivo(chave, id, tamanho)?.length() ?: 0L
        }

        // 3. Pronto: marca o livro como Baixado.
        val total = imagens.tamanhoEmBytes(chave, ids) + lidos.sumOf { it.texto.toByteArray(Charsets.UTF_8).size.toLong() }
        val em = agora()
        runCatching { registro.guardar(chave, DownloadGuardado(livroId, em, total, ids)) }
        planejadas.remove(livroId)
        definir(livroId, EstadoDoDownload.Baixado(em, total))
    }
}
