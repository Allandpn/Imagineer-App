package com.allan.imagineer.telas.lixeira

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.LixeiraEsvaziada
import com.allan.imagineer.rede.FrameNaLixeira
import com.allan.imagineer.rede.RepositorioDaLixeiraDeFrames
import com.allan.imagineer.rede.RepositorioDaLixeiraDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.LivroNaLixeira
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// A lixeira de **itens** (LT1): o mesmo comportamento para livros (agora) e, a seguir, cenas e elementos — só muda a fonte.

/** O que a lixeira de itens sabe de cada item. */
interface ItemDaLixeira {
    val id: Int
}

/** Os itens da lixeira e o espaço que ocupam (a soma que ajuda a decidir se esvazia). */
data class ItensDaLixeira<T : ItemDaLixeira>(val itens: List<T>, val totalEmBytes: Long)

/** Os textos de um tipo (o gênero muda: "apagado" / "apagada"), para os recados e confirmações saírem certos. */
data class TextosDaLixeira(
    val restaurado: String,
    val apagadoDeVez: String,
    val tituloDeApagar: String,
    val avisoDeApagar: String,
    val tituloDeEsvaziar: String,
    val vazia: String,
    /** "1 livro · 3,2 MB" no alto da tela. */
    val resumo: (quantos: Int, bytes: Long) -> String,
    val avisoDeEsvaziar: (quantos: Int, bytes: Long) -> String,
    val recadoDeEsvaziada: (removidos: Int, bytes: Long) -> String,
)

/** De onde a lixeira de um tipo lê e o que ela comanda. */
interface FonteDaLixeira<T : ItemDaLixeira> {
    val textos: TextosDaLixeira
    suspend fun listar(): ResultadoDaChamada<ItensDaLixeira<T>>
    suspend fun restaurar(id: Int): ResultadoDaChamada<Unit>
    suspend fun apagarDeVez(id: Int): ResultadoDaChamada<Unit>
    suspend fun esvaziar(): ResultadoDaChamada<LixeiraEsvaziada>
}

sealed interface CargaDeItens<out T> {
    data object Carregando : CargaDeItens<Nothing>
    data class Pronta<T>(val itens: List<T>, val totalEmBytes: Long) : CargaDeItens<T>
    data class Erro(val motivo: String) : CargaDeItens<Nothing>
}

/** O que falta confirmar: apagar **um** de vez, ou esvaziar **tudo**. Nenhum dos dois tem volta. */
sealed interface ConfirmacaoDeItens<out T> {
    data class ApagarUm<T>(val item: T) : ConfirmacaoDeItens<T>
    data class EsvaziarTudo(val quantos: Int, val bytes: Long) : ConfirmacaoDeItens<Nothing>
}

data class EstadoDaLixeiraDeItens<T : ItemDaLixeira>(
    val carga: CargaDeItens<T> = CargaDeItens.Carregando,
    val confirmacao: ConfirmacaoDeItens<T>? = null,
    val recado: String? = null,
    val recadoEhErro: Boolean = false,
    val ocupados: Set<Int> = emptySet(),
    val esvaziando: Boolean = false,
)

/**
 * A lógica da lixeira de um tipo de item (LT1): ler, **restaurar**, **apagar de vez** (com confirmação) e **esvaziar** (com confirmação
 * que diz quanto vai embora). Nada some sozinho (LX6).
 */
class LixeiraDeItensViewModel<T : ItemDaLixeira>(private val fonte: FonteDaLixeira<T>) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaLixeiraDeItens<T>())
    val estado: StateFlow<EstadoDaLixeiraDeItens<T>> = _estado.asStateFlow()
    val textos: TextosDaLixeira get() = fonte.textos

    /** Lê a lixeira; uma leitura que falha **não apaga** o que já estava na tela. */
    fun carregar() {
        viewModelScope.launch {
            when (val resultado = fonte.listar()) {
                is ResultadoDaChamada.Sucesso -> _estado.update {
                    it.copy(carga = CargaDeItens.Pronta(resultado.dado.itens, resultado.dado.totalEmBytes))
                }
                is ResultadoDaChamada.Falha -> _estado.update {
                    if (it.carga is CargaDeItens.Pronta) it.copy(recado = resultado.motivo, recadoEhErro = true)
                    else it.copy(carga = CargaDeItens.Erro(resultado.motivo))
                }
            }
        }
    }

    fun tentarDeNovo() {
        _estado.update { it.copy(carga = CargaDeItens.Carregando) }
        carregar()
    }

    fun restaurar(id: Int) {
        if (id in _estado.value.ocupados) return
        _estado.update { it.copy(ocupados = it.ocupados + id, recado = null) }
        viewModelScope.launch {
            when (val resultado = fonte.restaurar(id)) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.update { it.copy(recado = fonte.textos.restaurado, recadoEhErro = false) }
                    carregar()
                }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(recado = resultado.motivo, recadoEhErro = true) }
            }
            _estado.update { it.copy(ocupados = it.ocupados - id) }
        }
    }

    fun pedirApagarDeVez(item: T) {
        _estado.update { it.copy(confirmacao = ConfirmacaoDeItens.ApagarUm(item)) }
    }

    fun pedirEsvaziar() {
        val pronta = _estado.value.carga as? CargaDeItens.Pronta ?: return
        if (pronta.itens.isEmpty()) return
        _estado.update { it.copy(confirmacao = ConfirmacaoDeItens.EsvaziarTudo(pronta.itens.size, pronta.totalEmBytes)) }
    }

    fun cancelarConfirmacao() {
        _estado.update { it.copy(confirmacao = null) }
    }

    /** O usuário confirmou: apaga de vez o item ou esvazia tudo, conforme o que foi pedido. */
    fun confirmar() {
        val pedido = _estado.value.confirmacao ?: return
        _estado.update { it.copy(confirmacao = null, recado = null) }
        when (pedido) {
            is ConfirmacaoDeItens.ApagarUm -> apagarDeVez(pedido.item.id)
            is ConfirmacaoDeItens.EsvaziarTudo -> esvaziar()
        }
    }

    private fun apagarDeVez(id: Int) {
        _estado.update { it.copy(ocupados = it.ocupados + id) }
        viewModelScope.launch {
            when (val resultado = fonte.apagarDeVez(id)) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.update { it.copy(recado = fonte.textos.apagadoDeVez, recadoEhErro = false) }
                    carregar()
                }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(recado = resultado.motivo, recadoEhErro = true) }
            }
            _estado.update { it.copy(ocupados = it.ocupados - id) }
        }
    }

    private fun esvaziar() {
        _estado.update { it.copy(esvaziando = true) }
        viewModelScope.launch {
            when (val resultado = fonte.esvaziar()) {
                is ResultadoDaChamada.Sucesso -> {
                    val r = resultado.dado
                    _estado.update { it.copy(recado = fonte.textos.recadoDeEsvaziada(r.removidas, r.liberados_em_bytes), recadoEhErro = false) }
                    carregar()
                }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(recado = resultado.motivo, recadoEhErro = true) }
            }
            _estado.update { it.copy(esvaziando = false) }
        }
    }
}

// --------------------------------------------------------------------------- //
// Livros (LT2)
// --------------------------------------------------------------------------- //

/** Um livro da lixeira como item. */
data class LivroDaLixeira(val livro: LivroNaLixeira) : ItemDaLixeira {
    override val id: Int get() = livro.id
}

/** A fonte da lixeira de **livros**. */
class FonteDaLixeiraDeLivros(private val repositorio: RepositorioDaLixeiraDeLivros) : FonteDaLixeira<LivroDaLixeira> {
    override val textos = TextosDaLixeira(
        restaurado = "Livro restaurado.",
        apagadoDeVez = "Livro apagado de vez.",
        tituloDeApagar = "Apagar este livro de vez?",
        avisoDeApagar = "O livro será apagado de vez, com os capítulos, elementos, cenas, prompts e imagens (inclusive os arquivos no servidor). Não tem volta.",
        tituloDeEsvaziar = "Apagar de vez todos os livros da lixeira?",
        vazia = "Nenhum livro na lixeira. Os livros que você apagar ficam aqui até você decidir apagá-los de vez.",
        resumo = { quantos, bytes -> (if (quantos == 1) "1 livro" else "$quantos livros") + " · " + descreverTamanho(bytes) },
        avisoDeEsvaziar = { quantos, bytes ->
            val livros = if (quantos == 1) "1 livro" else "$quantos livros"
            "$livros serão apagados de vez, com tudo o que há neles (imagens: ${descreverTamanho(bytes)}). Não tem volta."
        },
        recadoDeEsvaziada = { removidos, bytes ->
            (if (removidos == 1) "1 livro apagado" else "$removidos livros apagados") + " de vez; ${descreverTamanho(bytes)} liberados."
        },
    )

    override suspend fun listar(): ResultadoDaChamada<ItensDaLixeira<LivroDaLixeira>> =
        when (val r = repositorio.listar()) {
            is ResultadoDaChamada.Sucesso -> ResultadoDaChamada.Sucesso(ItensDaLixeira(r.dado.livros.map(::LivroDaLixeira), r.dado.total_em_bytes))
            is ResultadoDaChamada.Falha -> r
        }

    override suspend fun restaurar(id: Int) = repositorio.restaurar(id)
    override suspend fun apagarDeVez(id: Int) = repositorio.apagarDeVez(id)
    override suspend fun esvaziar() = repositorio.esvaziar()
}

/** A linha de detalhes de um livro na lixeira: "12 capítulos · 4 imagens (3,2 MB)" e quando foi apagado. */
fun detalhesDoLivroNaLixeira(livro: LivroNaLixeira): String {
    val capitulos = if (livro.total_de_capitulos == 1) "1 capítulo" else "${livro.total_de_capitulos} capítulos"
    val imagens = when (livro.total_de_imagens) {
        0 -> "sem imagens"
        1 -> "1 imagem (${descreverTamanho(livro.tamanho_das_imagens_em_bytes)})"
        else -> "${livro.total_de_imagens} imagens (${descreverTamanho(livro.tamanho_das_imagens_em_bytes)})"
    }
    return "$capitulos · $imagens\nApagado em ${dataDaLixeira(livro.apagado_em)}"
}

// --------------------------------------------------------------------------- //
// Cenas e retratos (LT3)
// --------------------------------------------------------------------------- //

/** Uma cena ou um retrato da lixeira como item. */
data class FrameDaLixeira(val frame: FrameNaLixeira) : ItemDaLixeira {
    override val id: Int get() = frame.id
}

/** A fonte da lixeira de **cenas e retratos**. */
class FonteDaLixeiraDeFrames(private val repositorio: RepositorioDaLixeiraDeFrames) : FonteDaLixeira<FrameDaLixeira> {
    override val textos = TextosDaLixeira(
        restaurado = "Restaurada: voltou ao capítulo com os prompts e as imagens.",
        apagadoDeVez = "Apagada de vez, com os prompts e as imagens.",
        tituloDeApagar = "Apagar de vez esta cena?",
        avisoDeApagar = "A cena (ou o retrato) será apagada de vez, com os prompts e as imagens (inclusive os arquivos no servidor). Não tem volta.",
        tituloDeEsvaziar = "Apagar de vez todas as cenas e retratos da lixeira?",
        vazia = "Nenhuma cena ou retrato na lixeira. O que você apagar nos capítulos fica aqui até você decidir apagar de vez.",
        resumo = { quantos, bytes -> (if (quantos == 1) "1 item" else "$quantos itens") + " · " + descreverTamanho(bytes) },
        avisoDeEsvaziar = { quantos, bytes ->
            val itens = if (quantos == 1) "1 cena ou retrato" else "$quantos cenas e retratos"
            "$itens serão apagados de vez, com os prompts e as imagens (${descreverTamanho(bytes)}). Não tem volta."
        },
        recadoDeEsvaziada = { removidos, bytes ->
            (if (removidos == 1) "1 apagada" else "$removidos apagadas") + " de vez; ${descreverTamanho(bytes)} liberados."
        },
    )

    override suspend fun listar(): ResultadoDaChamada<ItensDaLixeira<FrameDaLixeira>> =
        when (val r = repositorio.listar()) {
            is ResultadoDaChamada.Sucesso -> ResultadoDaChamada.Sucesso(ItensDaLixeira(r.dado.frames.map(::FrameDaLixeira), r.dado.total_em_bytes))
            is ResultadoDaChamada.Falha -> r
        }

    override suspend fun restaurar(id: Int) = repositorio.restaurar(id)
    override suspend fun apagarDeVez(id: Int) = repositorio.apagarDeVez(id)
    override suspend fun esvaziar() = repositorio.esvaziar()
}

/** O nome que reconhece o frame: o título; no retrato, "Retrato de Fulano". */
fun tituloDoFrameNaLixeira(frame: FrameNaLixeira): String =
    if (frame.tipo == "PERSONAGEM") "Retrato de ${frame.nome_do_elemento ?: frame.titulo}" else frame.titulo

/** "Livro · Capítulo 3: Título", "2 prompts · 1 imagem (120 KB)" e quando foi apagado. */
fun detalhesDoFrameNaLixeira(frame: FrameNaLixeira): String {
    val capitulo = frame.titulo_do_capitulo?.let { "Capítulo ${frame.ordem_do_capitulo}: $it" } ?: "Capítulo ${frame.ordem_do_capitulo}"
    val prompts = if (frame.total_de_prompts == 1) "1 prompt" else "${frame.total_de_prompts} prompts"
    val imagens = when (frame.total_de_imagens) {
        0 -> "sem imagens"
        1 -> "1 imagem (${descreverTamanho(frame.tamanho_em_bytes)})"
        else -> "${frame.total_de_imagens} imagens (${descreverTamanho(frame.tamanho_em_bytes)})"
    }
    return "${frame.titulo_do_livro} · $capitulo\n$prompts · $imagens\nApagada em ${dataDaLixeira(frame.apagado_em)}"
}
