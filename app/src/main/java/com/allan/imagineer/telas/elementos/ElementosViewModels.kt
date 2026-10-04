package com.allan.imagineer.telas.elementos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.DetalheDoElemento
import com.allan.imagineer.rede.GaleriaDoElemento
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.RepositorioDeElementos
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// A lista de elementos do livro (item 7.8, E20)
// ---------------------------------------------------------------------------------------------

sealed interface CargaDaLista {
    data object Carregando : CargaDaLista
    data class Pronta(val elementos: List<ElementoDoLivro>) : CargaDaLista
    data class Erro(val motivo: String) : CargaDaLista
}

/** [busca] e [tipo] (nulo = todos) só filtram o que já foi lido; nada vai ao servidor por causa deles. */
data class EstadoDaLista(
    val carga: CargaDaLista = CargaDaLista.Carregando,
    val busca: String = "",
    val tipo: String? = null,
    /** Em que capítulos cada elemento aparece (LV3); lido à parte: se falhar, a lista só fica sem os chips. */
    val capitulosDosElementos: Map<Int, List<CapituloDoElemento>> = emptyMap(),
)

/** A lógica da lista de elementos: ler, buscar e filtrar por tipo. */
class ListaDeElementosViewModel(
    private val livroId: Int,
    private val elementos: RepositorioDeElementos,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaLista())
    val estado: StateFlow<EstadoDaLista> = _estado.asStateFlow()

    /**
     * Lê a lista. **Na primeira vez** mostra "carregando"; nas seguintes (ao voltar da ficha, onde
     * algo pode ter sido editado) relê **no lugar**, sem piscar, e se falhar deixa o que estava.
     */
    fun carregar() {
        val jaTemLista = _estado.value.carga is CargaDaLista.Pronta
        if (!jaTemLista) _estado.update { it.copy(carga = CargaDaLista.Carregando) }
        viewModelScope.launch {
            when (val resultado = elementos.listar(livroId)) {
                is ResultadoDaChamada.Sucesso ->
                    _estado.update { it.copy(carga = CargaDaLista.Pronta(resultado.dado)) }
                is ResultadoDaChamada.Falha ->
                    if (!jaTemLista) _estado.update { it.copy(carga = CargaDaLista.Erro(resultado.motivo)) }
            }
            val porCapitulo = elementos.elementosPorCapitulo(livroId)
            if (porCapitulo is ResultadoDaChamada.Sucesso) {
                _estado.update { it.copy(capitulosDosElementos = capitulosPorElemento(porCapitulo.dado)) }
            }
        }
    }

    fun buscar(texto: String) {
        _estado.update { it.copy(busca = texto) }
    }

    /** Um toque no mesmo tipo de novo limpa o filtro. */
    fun filtrarPorTipo(tipo: String?) {
        _estado.update { it.copy(tipo = if (it.tipo == tipo) null else tipo) }
    }
}

// ---------------------------------------------------------------------------------------------
// A ficha de um elemento (item 7.8, E21)
// ---------------------------------------------------------------------------------------------

/** A galeria da ficha (FI1): lida **à parte** do resto, para uma falha dela nunca esconder a ficha. */
sealed interface CargaDaGaleria {
    data object Carregando : CargaDaGaleria
    data class Pronta(val galeria: GaleriaDoElemento) : CargaDaGaleria
    data class Erro(val motivo: String) : CargaDaGaleria
}

sealed interface CargaDaFicha {
    data object Carregando : CargaDaFicha
    data class Pronta(val detalhe: DetalheDoElemento) : CargaDaFicha
    data class Erro(val motivo: String) : CargaDaFicha
}

/** Os diálogos da ficha. */
enum class TipoDeDialogoDaFicha {
    EDITAR_ELEMENTO,
    EDITAR_ESTADO,
    ADICIONAR_ESTADO,
    APAGAR_ESTADO,
    APAGAR_ELEMENTO,

    /** Escolher, na lista, o elemento em que este será juntado (E35). */
    ESCOLHER_PARA_MESCLAR,

    /** Confirmar a mesclagem com o [DialogoDaFicha.destinoId] escolhido (E35). */
    CONFIRMAR_MESCLAR,

    /** Escolher o capítulo onde acrescentar (E39, E40); depois abre o diálogo de [DialogoDaFicha.depois]. */
    ESCOLHER_CAPITULO,
    ADICIONAR_ACRESCIMO,
    EDITAR_ACRESCIMO,
    APAGAR_ACRESCIMO,
}

/**
 * Um diálogo aberto. [estadoId] vale para editar/apagar um estado; [destinoId] para a mesclagem.
 * [conflito]: o servidor respondeu 409 (nome repetido) — o diálogo aponta a saída (E36).
 */
data class DialogoDaFicha(
    val tipo: TipoDeDialogoDaFicha,
    val estadoId: Int? = null,
    val destinoId: Int? = null,
    /** O capítulo onde se acrescenta (estado ou identidade). */
    val capituloId: Int? = null,
    val acrescimoId: Int? = null,
    /** Em [TipoDeDialogoDaFicha.ESCOLHER_CAPITULO]: o que abrir depois de escolhido o capítulo. */
    val depois: TipoDeDialogoDaFicha? = null,
    val salvando: Boolean = false,
    val erro: String? = null,
    val conflito: Boolean = false,
)

data class EstadoDaFicha(
    val carga: CargaDaFicha = CargaDaFicha.Carregando,
    val dialogo: DialogoDaFicha? = null,
    /** O elemento foi apagado ou mesclado em outro: a tela volta para a lista. */
    val apagado: Boolean = false,
    /** Os outros elementos do livro, para escolher onde mesclar (E35). */
    val candidatos: CargaDaLista = CargaDaLista.Carregando,
    /** Os capítulos do livro, para escolher onde acrescentar (E39, E40). */
    val capitulos: CargaDosCapitulos = CargaDosCapitulos.Carregando,
    /** As imagens e as cenas do elemento (FI1). */
    val galeria: CargaDaGaleria = CargaDaGaleria.Carregando,
    /** O recado de uma falha ao definir a referência principal (FI6); nulo = nenhum. */
    val recadoDaGaleria: String? = null,
)

sealed interface CargaDosCapitulos {
    data object Carregando : CargaDosCapitulos
    data class Pronta(val capitulos: List<CapituloResumo>) : CargaDosCapitulos
    data class Erro(val motivo: String) : CargaDosCapitulos
}

const val ERRO_FICHA_NOME_VAZIO = "Dê um nome ao elemento."
const val ERRO_FICHA_ESTADO_VAZIO = "A descrição do estado não pode ficar vazia."
const val ERRO_FICHA_ACRESCIMO_VAZIO = "O acréscimo não pode ficar vazio."

/**
 * A lógica da ficha: ler o elemento inteiro e editá-lo — nome, tipo, identidade e os estados de
 * cada capítulo —, apagar um estado ou o elemento. Toda alteração **relê** a ficha no fim, para a
 * tela mostrar o que o servidor de fato tem.
 *
 * @param capituloId quando a ficha foi aberta a partir de uma sugestão, o capítulo dela: se ainda
 * não tem estado, a tela oferece "Adicionar estado neste capítulo".
 */
class FichaDoElementoViewModel(
    private val elementoId: Int,
    val capituloId: Int?,
    private val elementos: RepositorioDeElementos,
    private val livros: RepositorioDeLivros,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaFicha())
    val estado: StateFlow<EstadoDaFicha> = _estado.asStateFlow()

    /** Lê a ficha, a não ser que já esteja lida (a rotação do tablet não repete o pedido). */
    fun carregar() {
        if (_estado.value.carga !is CargaDaFicha.Pronta) buscar()
    }

    fun tentarDeNovo() {
        if (_estado.value.carga is CargaDaFicha.Erro) buscar()
    }

    private fun buscar() {
        _estado.update { it.copy(carga = CargaDaFicha.Carregando) }
        viewModelScope.launch { lerFicha(mostrarErro = true) }
    }

    private suspend fun lerFicha(mostrarErro: Boolean) {
        when (val resultado = elementos.detalhar(elementoId)) {
            is ResultadoDaChamada.Sucesso ->
                _estado.update { it.copy(carga = CargaDaFicha.Pronta(resultado.dado)) }
            is ResultadoDaChamada.Falha ->
                if (mostrarErro) _estado.update { it.copy(carga = CargaDaFicha.Erro(resultado.motivo)) }
        }
        // FI7: a galeria é relida junto com a ficha (ao abrir e depois de cada edição).
        lerGaleria()
    }

    /** Lê as imagens e as cenas do elemento. Uma falha só aparece **na seção**, nunca derruba a ficha (FI5). */
    private suspend fun lerGaleria() {
        when (val resultado = elementos.galeria(elementoId)) {
            is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(galeria = CargaDaGaleria.Pronta(resultado.dado)) }
            is ResultadoDaChamada.Falha -> _estado.update {
                // Se já havia uma galeria lida, ela fica: uma releitura que falha não a apaga.
                if (it.galeria is CargaDaGaleria.Pronta) it else it.copy(galeria = CargaDaGaleria.Erro(resultado.motivo))
            }
        }
    }

    /** Tenta ler de novo a galeria, depois de uma falha (FI5). */
    fun tentarDeNovoAGaleria() {
        _estado.update { it.copy(galeria = CargaDaGaleria.Carregando) }
        viewModelScope.launch { lerGaleria() }
    }

    /**
     * **Definir como canônica** (CAN6, que substituiu o *Usar como referência principal* do FI6): escolhe a imagem como a canônica do
     * retrato dela (`PUT /frames/{id}/imagem-canonica`), que é também a âncora do estado, e relê a galeria, para o selo mudar de
     * imagem. A recusa do servidor aparece como recado da galeria.
     */
    fun definirImagemCanonica(frameId: Int, imagemId: Int) {
        _estado.update { it.copy(recadoDaGaleria = null) }
        viewModelScope.launch {
            when (val resultado = elementos.definirImagemCanonica(frameId, imagemId)) {
                is ResultadoDaChamada.Sucesso -> lerGaleria()
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(recadoDaGaleria = resultado.motivo) }
            }
        }
    }

    private fun fichaPronta(): DetalheDoElemento? = (_estado.value.carga as? CargaDaFicha.Pronta)?.detalhe

    // --- diálogos ---------------------------------------------------------------------------

    fun abrirDialogo(tipo: TipoDeDialogoDaFicha, estadoId: Int? = null, acrescimoId: Int? = null) {
        if (fichaPronta() == null) return
        _estado.update { it.copy(dialogo = DialogoDaFicha(tipo, estadoId = estadoId, acrescimoId = acrescimoId)) }
    }

    fun cancelarDialogo() {
        _estado.update { it.copy(dialogo = null) }
    }

    private fun falhou(motivo: String, conflito: Boolean = false) {
        _estado.update { it.copy(dialogo = it.dialogo?.copy(salvando = false, erro = motivo, conflito = conflito)) }
    }

    private fun comErro(motivo: String) = falhou(motivo)

    /** Roda [chamada]; se der certo, fecha o diálogo e relê a ficha; se falhar, mostra o motivo nele. */
    private fun rodar(chamada: suspend () -> ResultadoDaChamada<Unit>) {
        val dialogo = _estado.value.dialogo ?: return
        if (dialogo.salvando) return
        _estado.update { it.copy(dialogo = dialogo.copy(salvando = true, erro = null)) }
        viewModelScope.launch {
            when (val resultado = chamada()) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.update { it.copy(dialogo = null) }
                    lerFicha(mostrarErro = false)
                }
                is ResultadoDaChamada.Falha -> falhou(resultado.motivo, resultado.codigoHttp == 409)
            }
        }
    }

    // --- editar o elemento -------------------------------------------------------------------

    /** Salva **só o que mudou** do elemento (nome, tipo, identidade). */
    fun salvarElemento(tipo: String, nome: String, identidade: String) {
        val ficha = fichaPronta() ?: return
        if (nome.isBlank()) return comErro(ERRO_FICHA_NOME_VAZIO)
        val novoNome = nome.trim()
        val novaIdentidade = identidade.trim()
        val mudouTipo = tipo != ficha.tipo
        val mudouNome = novoNome != ficha.nome
        val mudouIdentidade = novaIdentidade != ficha.descricao.orEmpty()
        if (!mudouTipo && !mudouNome && !mudouIdentidade) {
            cancelarDialogo()
            return
        }
        rodar {
            elementos.ajustarElemento(
                ficha.id,
                tipo = tipo.takeIf { mudouTipo },
                nome = novoNome.takeIf { mudouNome },
                identidade = novaIdentidade.takeIf { mudouIdentidade },
            )
        }
    }

    // --- estados -----------------------------------------------------------------------------

    fun salvarEstado(estadoId: Int, texto: String) {
        val ficha = fichaPronta() ?: return
        val atual = ficha.estados.firstOrNull { it.id == estadoId } ?: return
        if (texto.isBlank()) return comErro(ERRO_FICHA_ESTADO_VAZIO)
        if (texto.trim() == atual.descricao) {
            cancelarDialogo()
            return
        }
        rodar { elementos.ajustarEstado(estadoId, texto.trim()) }
    }

    /**
     * Começa uma adição de estado ou de acréscimo (E39, E40). Com [noCapituloDaSugestao] e a ficha
     * aberta a partir de um capítulo, esse capítulo já vem escolhido; senão, pede o capítulo primeiro.
     */
    fun abrirAdicao(depois: TipoDeDialogoDaFicha, noCapituloDaSugestao: Boolean) {
        if (fichaPronta() == null) return
        val doCapitulo = capituloId
        if (noCapituloDaSugestao && doCapitulo != null) {
            _estado.update { it.copy(dialogo = DialogoDaFicha(depois, capituloId = doCapitulo)) }
        } else {
            _estado.update {
                it.copy(
                    dialogo = DialogoDaFicha(TipoDeDialogoDaFicha.ESCOLHER_CAPITULO, depois = depois),
                    capitulos = CargaDosCapitulos.Carregando,
                )
            }
            carregarCapitulos()
        }
    }

    /** "Tentar de novo" na lista de capítulos. */
    fun recarregarCapitulos() {
        _estado.update { it.copy(capitulos = CargaDosCapitulos.Carregando) }
        carregarCapitulos()
    }

    private fun carregarCapitulos() {
        val ficha = fichaPronta() ?: return
        viewModelScope.launch {
            // O repositório de livros responde do aparelho quando pode (abre sem pedir tudo de novo).
            val carga = when (val resultado = livros.abrirLivro(ficha.livro_id)) {
                is ResultadoDaChamada.Sucesso -> CargaDosCapitulos.Pronta(resultado.dado.capitulos)
                is ResultadoDaChamada.Falha -> CargaDosCapitulos.Erro(resultado.motivo)
            }
            _estado.update { it.copy(capitulos = carga) }
        }
    }

    /** Escolhido o capítulo, abre o diálogo de texto do que se vai acrescentar. */
    fun escolherCapitulo(capituloEscolhido: Int) {
        val depois = _estado.value.dialogo?.takeIf { it.tipo == TipoDeDialogoDaFicha.ESCOLHER_CAPITULO }?.depois ?: return
        _estado.update { it.copy(dialogo = DialogoDaFicha(depois, capituloId = capituloEscolhido)) }
    }

    /** Acrescenta um estado de aparência no capítulo do diálogo (E34, E40). */
    fun adicionarEstado(texto: String) {
        val ficha = fichaPronta() ?: return
        val capitulo = _estado.value.dialogo?.capituloId ?: return
        if (texto.isBlank()) return comErro(ERRO_FICHA_ESTADO_VAZIO)
        rodar { elementos.criarEstado(ficha.id, capitulo, texto.trim()) }
    }

    // --- acréscimos de identidade (E38, E39) -------------------------------------------------

    fun adicionarAcrescimo(texto: String) {
        val ficha = fichaPronta() ?: return
        val capitulo = _estado.value.dialogo?.capituloId ?: return
        if (texto.isBlank()) return comErro(ERRO_FICHA_ACRESCIMO_VAZIO)
        rodar { elementos.criarAcrescimo(ficha.id, capitulo, texto.trim()) }
    }

    fun salvarAcrescimo(acrescimoId: Int, texto: String) {
        val ficha = fichaPronta() ?: return
        val atual = ficha.historico_identidade.firstOrNull { it.id == acrescimoId } ?: return
        if (texto.isBlank()) return comErro(ERRO_FICHA_ACRESCIMO_VAZIO)
        if (texto.trim() == atual.descricao) {
            cancelarDialogo()
            return
        }
        rodar { elementos.ajustarAcrescimo(acrescimoId, texto.trim()) }
    }

    fun confirmarApagarAcrescimo() {
        val acrescimoId = _estado.value.dialogo?.acrescimoId ?: return
        rodar { elementos.removerAcrescimo(acrescimoId) }
    }

    fun confirmarApagarEstado() {
        val estadoId = _estado.value.dialogo?.estadoId ?: return
        rodar { elementos.removerEstado(estadoId) }
    }

    // --- mesclar (E35) ---------------------------------------------------------------------------

    /**
     * Abre a lista dos **outros** elementos do livro para escolher em qual deles este será juntado.
     * Sem o livro da ficha (servidor antigo), não há como listar: o diálogo mostra o motivo.
     */
    fun abrirMesclagem() {
        val ficha = fichaPronta() ?: return
        _estado.update {
            it.copy(dialogo = DialogoDaFicha(TipoDeDialogoDaFicha.ESCOLHER_PARA_MESCLAR), candidatos = CargaDaLista.Carregando)
        }
        viewModelScope.launch {
            val carga = when (val resultado = elementos.listar(ficha.livro_id)) {
                is ResultadoDaChamada.Sucesso -> CargaDaLista.Pronta(resultado.dado.filter { it.id != ficha.id })
                is ResultadoDaChamada.Falha -> CargaDaLista.Erro(resultado.motivo)
            }
            _estado.update { it.copy(candidatos = carga) }
        }
    }

    /** Escolhido o destino, pede a confirmação — mesclar não se desfaz. */
    fun escolherDestinoDaMesclagem(destinoId: Int) {
        if (_estado.value.dialogo?.tipo != TipoDeDialogoDaFicha.ESCOLHER_PARA_MESCLAR) return
        _estado.update { it.copy(dialogo = DialogoDaFicha(TipoDeDialogoDaFicha.CONFIRMAR_MESCLAR, destinoId = destinoId)) }
    }

    /** O "sim": junta este elemento ao destino. Dando certo, este elemento deixou de existir: a tela volta. */
    fun confirmarMesclagem() {
        val dialogo = _estado.value.dialogo ?: return
        val destinoId = dialogo.destinoId ?: return
        if (dialogo.salvando) return
        _estado.update { it.copy(dialogo = dialogo.copy(salvando = true, erro = null)) }
        viewModelScope.launch {
            when (val resultado = elementos.mesclar(elementoId, destinoId)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(dialogo = null, apagado = true) }
                is ResultadoDaChamada.Falha -> falhou(resultado.motivo)
            }
        }
    }

    // --- apagar o elemento ---------------------------------------------------------------------

    fun confirmarApagarElemento() {
        val dialogo = _estado.value.dialogo ?: return
        if (dialogo.salvando) return
        _estado.update { it.copy(dialogo = dialogo.copy(salvando = true, erro = null)) }
        viewModelScope.launch {
            when (val resultado = elementos.excluir(elementoId)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(dialogo = null, apagado = true) }
                is ResultadoDaChamada.Falha -> falhou(resultado.motivo)
            }
        }
    }
}
