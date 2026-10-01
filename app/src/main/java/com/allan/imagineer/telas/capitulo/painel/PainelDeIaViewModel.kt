package com.allan.imagineer.telas.capitulo.painel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.RepositorioDeElementos
import com.allan.imagineer.rede.RepositorioDeSugestoes
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** O que o painel de IA mostra (item 7.5b, incremento 9). */
sealed interface ConteudoDoPainel {
    /** O painel ainda não foi aberto: nada foi pedido ao servidor (P1). */
    data object NaoCarregado : ConteudoDoPainel

    /** Lendo o que está salvo (`GET`, que nunca gasta IA — P2). */
    data object Lendo : ConteudoDoPainel

    /** O capítulo nunca foi analisado. O único botão é "Analisar com IA" (P6). */
    data class NuncaAnalisado(val pendentesAnteriores: Int) : ConteudoDoPainel

    /** Há um resultado — inclusive uma análise que não achou nada (P14). */
    data class Pronto(val sugestoes: SugestoesDeCapitulo) : ConteudoDoPainel

    /** A leitura falhou. [motivo] já está escrito para o usuário. */
    data class Erro(val motivo: String) : ConteudoDoPainel
}

/** Um recado sobre um elemento: o erro de uma ação, ou um aviso do que acabou de acontecer (E5, E6, E9). */
data class MensagemDoElemento(val texto: String, val ehErro: Boolean)

/** A lista de elementos do livro, dentro do diálogo de "vincular" (E3). */
sealed interface ListaParaVincular {
    data object Carregando : ListaParaVincular
    data class Pronta(val elementos: List<ElementoDoLivro>) : ListaParaVincular
    data class Erro(val motivo: String) : ListaParaVincular
}

/** Um diálogo aberto sobre uma sugestão de elemento (E2, E3, E5, E15, E27). */
sealed interface DialogoDeElemento {
    val sugestao: ElementoSugerido

    /**
     * "Criar elemento", com tipo, nome e identidade editáveis (E2).
     * [conflito]: o servidor disse que já existe um igual (409), então oferece vincular.
     */
    data class Criando(
        override val sugestao: ElementoSugerido,
        val salvando: Boolean = false,
        val erro: String? = null,
        val conflito: Boolean = false,
    ) : DialogoDeElemento

    /** "Vincular a um existente" ([trocando] = false) ou "Trocar" o casamento (E3, E5, E23). */
    data class Vinculando(
        override val sugestao: ElementoSugerido,
        val trocando: Boolean,
        val lista: ListaParaVincular,
        val salvando: Boolean = false,
        val erro: String? = null,
    ) : DialogoDeElemento

    /**
     * Descartar quem **aparece em cenas sugeridas** pede confirmação (E27): descartar pode atrapalhar
     * a imagem dessas cenas depois. [cenas] são os títulos.
     */
    data class DescartandoEmCenas(
        override val sugestao: ElementoSugerido,
        val cenas: List<String>,
    ) : DialogoDeElemento

    /** "Desfazer confirmação" (E15): desliga a sugestão e, se quiser, apaga o estado daqui. */
    data class Desfazendo(
        override val sugestao: ElementoSugerido,
        val apagarEstado: Boolean = false,
        val salvando: Boolean = false,
        val erro: String? = null,
    ) : DialogoDeElemento
}

/**
 * Tudo o que o painel mostra. A análise é uma **camada por cima do conteúdo**
 * ([analisando], [erroDaAnalise]) e não um conteúdo próprio: enquanto a IA roda, e se ela
 * falhar, o conteúdo de antes continua ali (P8).
 */
data class EstadoDoPainel(
    val conteudo: ConteudoDoPainel = ConteudoDoPainel.NaoCarregado,
    val analisando: Boolean = false,
    /** A falha da última análise, já na mensagem da API; some ao começar outra. */
    val erroDaAnalise: String? = null,
    /** O diálogo "Isso refaz as sugestões e gasta IA" está aberto (P7). */
    val confirmandoReanalise: Boolean = false,
    /** Qual das três listas o painel mostra (E24). */
    val filtro: FiltroDoPainel = FiltroDoPainel.PENDENTES,
    /** Sugestões com uma ação em andamento (E9). */
    val ocupados: Set<Int> = emptySet(),
    /** O recado de cada sugestão, por id (E9). */
    val mensagens: Map<Int, MensagemDoElemento> = emptyMap(),
    val dialogo: DialogoDeElemento? = null,
    /**
     * A sugestão aberta no **modal**: o usuário tocou no ícone dela no texto (item 7.5b, E42). Mora aqui, e
     * não na tela, para **sobreviver a uma ida à ficha de alguém**: ao voltar, o modal continua como estava.
     */
    val emModal: Int? = null,
)

const val AVISO_ESTADO_RASCUNHO =
    "Estado registrado. A descrição é um rascunho: a IA a refaz quando você gerar um prompt."
const val AVISO_ESTADO_ANTERIOR_FICOU =
    "O estado criado no elemento anterior não foi apagado."
const val ERRO_LIVRO_NAO_CARREGADO = "Aguarde o capítulo carregar e tente de novo."
const val ERRO_NOME_VAZIO = "Dê um nome ao elemento."

/**
 * A lógica do painel de IA de um capítulo: ler, analisar (incremento 9) e confirmar os
 * elementos sugeridos (incremento 10a). Cenas e prompts são do 10b.
 *
 * **Ler nunca custa, gerar custa.** [aoAbrirPainel] e [tentarDeNovo] usam o `GET`;
 * [analisar] e [confirmarReanalise] são os **únicos** caminhos até o `POST` — e portanto
 * os únicos que gastam IA (item 6.8). As ações de elemento (E1 a E18) são só rotas de
 * cadastro e nunca gastam IA.
 */
class PainelDeIaViewModel(
    private val capituloId: Int,
    private val sugestoes: RepositorioDeSugestoes,
    private val elementos: RepositorioDeElementos,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDoPainel())
    val estado: StateFlow<EstadoDoPainel> = _estado.asStateFlow()

    /** De qual livro é o capítulo: a tela o informa quando o capítulo carrega. */
    private var livroId: Int? = null

    /** Os elementos do livro, buscados uma vez para o diálogo de vincular (E3). */
    private var elementosDoLivro: List<ElementoDoLivro>? = null

    fun definirLivro(id: Int) {
        livroId = id
    }

    /**
     * O painel foi aberto. Na **primeira** vez, lê o que está salvo; depois guarda o
     * resultado e não relê a cada abrir e fechar (P1). Um erro de leitura só se refaz
     * por [tentarDeNovo], de propósito do usuário.
     */
    fun aoAbrirPainel() {
        if (_estado.value.conteudo is ConteudoDoPainel.NaoCarregado) ler()
    }

    /** "Tentar de novo" depois de um erro de leitura. */
    fun tentarDeNovo() {
        if (_estado.value.conteudo is ConteudoDoPainel.Erro) ler()
    }

    private fun ler() {
        _estado.update { it.copy(conteudo = ConteudoDoPainel.Lendo) }
        viewModelScope.launch {
            val resultado = sugestoes.ler(capituloId)
            _estado.update {
                it.copy(
                    conteudo = when (resultado) {
                        is ResultadoDaChamada.Sucesso -> conteudoDe(resultado.dado)
                        is ResultadoDaChamada.Falha -> ConteudoDoPainel.Erro(resultado.motivo)
                    },
                )
            }
        }
    }

    /**
     * Relê as sugestões **no lugar**, sem passar por "Lendo" (E8): depois de uma ação, a lista
     * mostra o que o servidor de fato tem, sem piscar nem perder a rolagem. Se a releitura
     * falhar, fica o que estava.
     */
    private suspend fun reler() {
        val resultado = sugestoes.ler(capituloId)
        if (resultado is ResultadoDaChamada.Sucesso) {
            _estado.update { it.copy(conteudo = conteudoDe(resultado.dado)) }
        }
    }

    /** Esquece a lista de elementos do livro: algo mudou. */
    private fun esquecerFichas() {
        elementosDoLivro = null
    }

    /**
     * Ao **voltar da ficha** (E31), o que foi editado lá pode mudar os cartões: relê no lugar. Só
     * faz algo se o painel já tem um resultado; fora isso, não há o que atualizar.
     */
    fun aoVoltarDaFicha() {
        if (_estado.value.conteudo !is ConteudoDoPainel.Pronto) return
        esquecerFichas()
        viewModelScope.launch { reler() }
    }

    /**
     * Abre a sugestão [sugestaoId] num modal — vindo do ícone dela no texto (E42). Se as sugestões ainda não
     * foram lidas (o painel nunca foi aberto), lê agora: é só o `GET`, que nunca gasta IA.
     */
    fun abrirModal(sugestaoId: Int) {
        _estado.update { it.copy(emModal = sugestaoId) }
        aoAbrirPainel()
    }

    /** Fecha o modal. */
    fun fecharModal() {
        _estado.update { it.copy(emModal = null) }
    }

    /** Escolhe qual lista mostrar: pendentes, confirmados ou descartados (E24). */
    fun escolherFiltro(filtro: FiltroDoPainel) {
        _estado.update { it.copy(filtro = filtro) }
    }

    // ------------------------------------------------------------------ //
    // Analisar (incremento 9)
    // ------------------------------------------------------------------ //

    /**
     * "Analisar com IA" — **só existe quando o capítulo nunca foi analisado** (P6). Não
     * faz nada em nenhum outro estado, nem se já há uma análise em andamento.
     */
    fun analisar() {
        val atual = _estado.value
        if (atual.conteudo !is ConteudoDoPainel.NuncaAnalisado || atual.analisando) return
        executarAnalise(forcar = false)
    }

    /** "Reanalisar": só depois de analisado, e **pede confirmação** antes de gastar (P7). */
    fun pedirReanalise() {
        val atual = _estado.value
        if (atual.conteudo !is ConteudoDoPainel.Pronto || atual.analisando) return
        _estado.update { it.copy(confirmandoReanalise = true) }
    }

    fun cancelarReanalise() {
        _estado.update { it.copy(confirmandoReanalise = false) }
    }

    /** O "sim" do diálogo: refaz as sugestões ainda não confirmadas (`forcar = true`). */
    fun confirmarReanalise() {
        val atual = _estado.value
        if (!atual.confirmandoReanalise || atual.analisando) return
        _estado.update { it.copy(confirmandoReanalise = false) }
        executarAnalise(forcar = true)
    }

    /**
     * O único ponto que chama o `POST`. **Sem repetição automática** (P8): repetir sozinho
     * cobraria duas vezes sem ninguém pedir. Se falhar, o conteúdo de antes continua e a
     * mensagem da API aparece.
     */
    private fun executarAnalise(forcar: Boolean) {
        _estado.update { it.copy(analisando = true, erroDaAnalise = null) }
        viewModelScope.launch {
            when (val resultado = sugestoes.analisar(capituloId, forcar)) {
                is ResultadoDaChamada.Sucesso ->
                    // Sugestões novas: os recados e ocupados de antes não valem mais.
                    _estado.update {
                        it.copy(
                            conteudo = conteudoDe(resultado.dado),
                            analisando = false,
                            erroDaAnalise = null,
                            mensagens = emptyMap(),
                            ocupados = emptySet(),
                            dialogo = null,
                        )
                    }
                is ResultadoDaChamada.Falha ->
                    _estado.update { it.copy(analisando = false, erroDaAnalise = resultado.motivo) }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Confirmar elementos (incremento 10a, E1 a E18)
    // ------------------------------------------------------------------ //

    /** Uma das ações da tabela E1, tocada no cartão de [elemento]. */
    fun executar(acao: AcaoDoElemento, elemento: ElementoSugerido) {
        when (acao) {
            AcaoDoElemento.CRIAR -> abrirDialogo(DialogoDeElemento.Criando(elemento))
            AcaoDoElemento.VINCULAR -> abrirVinculo(elemento, trocando = false)
            AcaoDoElemento.TROCAR -> abrirVinculo(elemento, trocando = true)
            // E22: a ficha é uma tela; quem a abre é a tela do capítulo, e não o ViewModel.
            AcaoDoElemento.ABRIR_FICHA -> Unit
            // E16: descartar é imediato e reversível; E27: só pede confirmação se aparece em cenas.
            AcaoDoElemento.DESCARTAR -> {
                val cenas = (_estado.value.conteudo as? ConteudoDoPainel.Pronto)
                    ?.let { cenasDoElemento(it.sugestoes)[elemento.id] }
                    .orEmpty()
                if (cenas.isEmpty()) {
                    rodar(elemento, concluiu = true) { elementos.descartar(elemento.id, true) }
                } else {
                    abrirDialogo(DialogoDeElemento.DescartandoEmCenas(elemento, cenas))
                }
            }
            // O mesmo elemento_id: o servidor só tira o "automático" (E4).
            AcaoDoElemento.CONFIRMAR ->
                rodar(elemento, concluiu = true) { elementos.ajustarCasamento(elemento.id, elemento.elemento_id) }
            // E15: havendo estado neste capítulo, pergunta se também o apaga; senão, desfaz direto.
            AcaoDoElemento.DESFAZER ->
                if (elemento.estado_id != null) {
                    abrirDialogo(DialogoDeElemento.Desfazendo(elemento))
                } else {
                    rodar(elemento) { elementos.ajustarCasamento(elemento.id, null) }
                }
            AcaoDoElemento.REGISTRAR_ESTADO -> {
                val elementoId = elemento.elemento_id ?: return
                rodar(elemento, aviso = AVISO_ESTADO_RASCUNHO, concluiu = true) {
                    elementos.registrarEstado(elementoId, elemento.id)
                }
            }
        }
    }

    /** "Restaurar", na lista de descartadas (E16). */
    fun restaurar(elemento: ElementoSugerido) {
        rodar(elemento) { elementos.descartar(elemento.id, false) }
    }

    /** O "sim" do aviso de E27: descarta mesmo aparecendo em cenas. */
    fun confirmarDescarte() {
        val dialogo = _estado.value.dialogo as? DialogoDeElemento.DescartandoEmCenas ?: return
        _estado.update { it.copy(dialogo = null) }
        rodar(dialogo.sugestao, concluiu = true) { elementos.descartar(dialogo.sugestao.id, true) }
    }

    /**
     * Roda uma ação **de uma vez por elemento** (E9): se já há uma em andamento naquele
     * cartão, ignora. Sucesso = relê a lista (E8) e, se houver, mostra o [aviso]; falha = a
     * mensagem da API no cartão, e nada mais muda (sem repetição automática).
     */
    private fun rodar(
        elemento: ElementoSugerido,
        aviso: String? = null,
        concluiu: Boolean = false,
        chamada: suspend () -> ResultadoDaChamada<Unit>,
    ) {
        if (elemento.id in _estado.value.ocupados) return
        _estado.update { it.copy(ocupados = it.ocupados + elemento.id, mensagens = it.mensagens - elemento.id) }
        viewModelScope.launch {
            val resultado = chamada()
            if (resultado is ResultadoDaChamada.Sucesso) {
                esquecerFichas()
                reler()
                // E44: a ação que conclui a decisão fecha o modal desta sugestão.
                if (concluiu) fecharModalSe(elemento.id)
            }
            _estado.update {
                val mensagem = when {
                    resultado is ResultadoDaChamada.Falha -> MensagemDoElemento(resultado.motivo, ehErro = true)
                    aviso != null -> MensagemDoElemento(aviso, ehErro = false)
                    else -> null
                }
                it.copy(
                    ocupados = it.ocupados - elemento.id,
                    mensagens = if (mensagem != null) it.mensagens + (elemento.id to mensagem) else it.mensagens,
                )
            }
        }
    }

    /** Fecha o modal **só se** for o da sugestão [sugestaoId] (E44): o de outra, aberto agora, não é tocado. */
    private fun fecharModalSe(sugestaoId: Int) {
        _estado.update { if (it.emModal == sugestaoId) it.copy(emModal = null) else it }
    }

    private fun abrirDialogo(dialogo: DialogoDeElemento) {
        _estado.update { it.copy(dialogo = dialogo) }
    }

    fun cancelarDialogo() {
        _estado.update { it.copy(dialogo = null) }
    }

    // --- Criar (E2) ---------------------------------------------------- //

    /** E2: cria o elemento (e o estado deste capítulo) a partir do que está no diálogo. */
    fun confirmarCriacao(tipo: String, nome: String, descricao: String) {
        val dialogo = _estado.value.dialogo as? DialogoDeElemento.Criando ?: return
        if (dialogo.salvando) return
        val livro = livroId
        when {
            livro == null -> comErroNaCriacao(dialogo, ERRO_LIVRO_NAO_CARREGADO)
            nome.isBlank() -> comErroNaCriacao(dialogo, ERRO_NOME_VAZIO)
            else -> {
                _estado.update { it.copy(dialogo = dialogo.copy(salvando = true, erro = null, conflito = false)) }
                viewModelScope.launch {
                    when (val resultado = elementos.criar(livro, tipo, nome.trim(), descricao.trim(), dialogo.sugestao.id)) {
                        is ResultadoDaChamada.Sucesso -> {
                            esquecerFichas()
                            _estado.update { it.copy(dialogo = null) }
                            reler()
                            fecharModalSe(dialogo.sugestao.id) // E44: criou, concluiu
                        }
                        is ResultadoDaChamada.Falha -> _estado.update {
                            it.copy(
                                dialogo = dialogo.copy(
                                    salvando = false,
                                    erro = resultado.motivo,
                                    conflito = resultado.codigoHttp == 409,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun comErroNaCriacao(dialogo: DialogoDeElemento.Criando, erro: String) {
        _estado.update { it.copy(dialogo = dialogo.copy(erro = erro)) }
    }

    /** O 409 do "criar" oferece este atalho: em vez de criar outro, vincular ao que já existe (E2). */
    fun trocarCriacaoPorVinculo() {
        val dialogo = _estado.value.dialogo as? DialogoDeElemento.Criando ?: return
        abrirVinculo(dialogo.sugestao, trocando = false)
    }

    // --- Vincular e Trocar (E3, E5, E13) ------------------------------- //

    /** E3 e E5: abre a lista dos elementos do livro, buscando-a só na primeira vez. */
    private fun abrirVinculo(elemento: ElementoSugerido, trocando: Boolean) {
        val conhecidos = elementosDoLivro
        _estado.update {
            it.copy(
                dialogo = DialogoDeElemento.Vinculando(
                    sugestao = elemento,
                    trocando = trocando,
                    lista = if (conhecidos != null) ListaParaVincular.Pronta(conhecidos) else ListaParaVincular.Carregando,
                ),
            )
        }
        if (conhecidos == null) carregarElementosDoLivro()
    }

    /** "Tentar de novo" na lista de vincular. */
    fun recarregarLista() {
        val dialogo = _estado.value.dialogo as? DialogoDeElemento.Vinculando ?: return
        _estado.update { it.copy(dialogo = dialogo.copy(lista = ListaParaVincular.Carregando)) }
        carregarElementosDoLivro()
    }

    private fun carregarElementosDoLivro() {
        val livro = livroId
        if (livro == null) {
            atualizarLista(ListaParaVincular.Erro(ERRO_LIVRO_NAO_CARREGADO))
            return
        }
        viewModelScope.launch {
            when (val resultado = elementos.listar(livro)) {
                is ResultadoDaChamada.Sucesso -> {
                    elementosDoLivro = resultado.dado
                    atualizarLista(ListaParaVincular.Pronta(resultado.dado))
                }
                is ResultadoDaChamada.Falha -> atualizarLista(ListaParaVincular.Erro(resultado.motivo))
            }
        }
    }

    private fun atualizarLista(lista: ListaParaVincular) {
        _estado.update {
            val dialogo = it.dialogo as? DialogoDeElemento.Vinculando ?: return@update it
            it.copy(dialogo = dialogo.copy(lista = lista))
        }
    }

    /**
     * O usuário escolheu um elemento na lista. Vincular liga e cria o estado (E3); trocar só
     * corrige o casamento (E5) — se precisar de estado, o cartão passa a oferecer "Registrar".
     */
    fun escolherElemento(elementoId: Int) {
        val dialogo = _estado.value.dialogo as? DialogoDeElemento.Vinculando ?: return
        if (dialogo.salvando) return
        val sugestao = dialogo.sugestao
        _estado.update { it.copy(dialogo = dialogo.copy(salvando = true, erro = null)) }
        viewModelScope.launch {
            val resultado = if (dialogo.trocando) {
                elementos.ajustarCasamento(sugestao.id, elementoId)
            } else {
                elementos.registrarEstado(elementoId, sugestao.id)
            }
            when (resultado) {
                is ResultadoDaChamada.Sucesso -> {
                    esquecerFichas()
                    _estado.update {
                        val avisar = dialogo.trocando && sugestao.estado_id != null
                        it.copy(
                            dialogo = null,
                            mensagens = if (avisar) {
                                it.mensagens + (sugestao.id to MensagemDoElemento(AVISO_ESTADO_ANTERIOR_FICOU, false))
                            } else {
                                it.mensagens
                            },
                        )
                    }
                    reler()
                    fecharModalSe(sugestao.id) // E44: escolheu o elemento, concluiu
                }
                is ResultadoDaChamada.Falha ->
                    _estado.update { it.copy(dialogo = dialogo.copy(salvando = false, erro = resultado.motivo)) }
            }
        }
    }

    // --- Desfazer confirmação (E15) ------------------------------------ //

    fun alternarApagarEstado() {
        val dialogo = _estado.value.dialogo as? DialogoDeElemento.Desfazendo ?: return
        _estado.update { it.copy(dialogo = dialogo.copy(apagarEstado = !dialogo.apagarEstado)) }
    }

    /**
     * E15: desliga a sugestão do elemento (`elemento_id: null`, que vale de verdade) e, **só se
     * o usuário marcou**, apaga o estado criado neste capítulo. O elemento nunca é apagado aqui.
     */
    fun confirmarDesfazer() {
        val dialogo = _estado.value.dialogo as? DialogoDeElemento.Desfazendo ?: return
        if (dialogo.salvando) return
        val sugestao = dialogo.sugestao
        _estado.update { it.copy(dialogo = dialogo.copy(salvando = true, erro = null)) }
        viewModelScope.launch {
            val desligar = elementos.ajustarCasamento(sugestao.id, null)
            if (desligar is ResultadoDaChamada.Falha) {
                _estado.update { it.copy(dialogo = dialogo.copy(salvando = false, erro = desligar.motivo)) }
                return@launch
            }
            var recado: MensagemDoElemento? = null
            val estadoId = sugestao.estado_id
            if (dialogo.apagarEstado && estadoId != null) {
                val apagar = elementos.removerEstado(estadoId)
                if (apagar is ResultadoDaChamada.Falha) {
                    recado = MensagemDoElemento(
                        "Desfeito, mas não consegui apagar o estado: ${apagar.motivo}",
                        ehErro = true,
                    )
                }
            } else if (estadoId != null) {
                recado = MensagemDoElemento(AVISO_ESTADO_ANTERIOR_FICOU, ehErro = false)
            }
            esquecerFichas()
            _estado.update {
                it.copy(
                    dialogo = null,
                    mensagens = if (recado != null) it.mensagens + (sugestao.id to recado) else it.mensagens,
                )
            }
            reler()
        }
    }

    /** `gerado_em` nulo = nunca analisado; qualquer outra coisa é um resultado (P14). */
    private fun conteudoDe(dado: SugestoesDeCapitulo): ConteudoDoPainel =
        if (dado.gerado_em == null) {
            ConteudoDoPainel.NuncaAnalisado(dado.sugestoes_pendentes_anteriores)
        } else {
            ConteudoDoPainel.Pronto(dado)
        }
}
