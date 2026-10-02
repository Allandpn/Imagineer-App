package com.allan.imagineer.telas.capitulo.painel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.analise.rotuloDoCapituloNoAviso
import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.motivoParaNaoImportar
import com.allan.imagineer.rede.PromptsSemServidor
import com.allan.imagineer.rede.RepositorioDeElementos
import com.allan.imagineer.rede.RepositorioDePrompts
import com.allan.imagineer.rede.RepositorioDeSugestoes
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
     * Os **modais empilhados** (C12), do de baixo para o de cima. Tocar num ícone do texto começa uma pilha nova com
     * aquele modal; "Revisar" num participante da cena **empilha** o do elemento por cima, e fechar o de cima
     * **revela o de baixo**, já atualizado. Moram aqui, e não na tela, pelo mesmo motivo de sempre: o estado
     * sobrevive a recomposições.
     */
    val modais: List<ModalAberto> = emptyList(),
    /** Cenas com uma ação em andamento (C7). */
    val cenasOcupadas: Set<Int> = emptySet(),
    /** O recado de cada cena, por id: o erro de uma ação, ou o aviso do que acabou de acontecer (C5). */
    val mensagensDeCena: Map<Int, MensagemDoElemento> = emptyMap(),
    /** O diálogo "Confirmar todos?" está aberto, com a conta do que vai acontecer (L2). */
    val confirmandoTodos: ResumoDoLote? = null,
    /** O lote de "Confirmar todos" está rodando (L3): os botões ficam desabilitados. */
    val executandoLote: Boolean = false,
    /** O resumo do último lote (L5); some quando a pessoa o dispensa ou começa outro. */
    val resultadoDoLote: String? = null,
    /** Os prompts de cada frame, por id do frame (G2). Só existe a entrada de quem já foi aberto. */
    val prompts: Map<Int, PromptsDoFrame> = emptyMap(),
    /** Frames com uma geração de prompt em andamento (G4): um por vez. */
    val gerandoPrompt: Set<Int> = emptySet(),
    /** O recado de cada frame sobre o prompt: o erro da geração, ou o aviso do que acabou de acontecer (G5, G7). */
    val mensagensDePrompt: Map<Int, MensagemDoElemento> = emptyMap(),
    /** O diálogo "Gerar o prompt gasta IA" está aberto para este frame (G3). */
    val confirmandoPrompt: Int? = null,
    /**
     * Quantos prompts foram gerados **nesta sessão** por frame. Só serve para o modal mostrar o aviso translúcido
     * "Prompt gerado." quando o número **sobe** (G13).
     */
    val promptsGerados: Map<Int, Int> = emptyMap(),
    /** Os retratos criados **nesta sessão**: sugestão de elemento -> frame (N4). O app os usa antes de reler os artefatos. */
    val retratosCriados: Map<Int, Int> = emptyMap(),
    /** Elementos com um retrato sendo criado (N5): um por vez. */
    val retratosOcupados: Set<Int> = emptySet(),
    /** O recado de cada elemento sobre o retrato: o erro da criação (N5). */
    val mensagensDeRetrato: Map<Int, MensagemDoElemento> = emptyMap(),
    /** Sobe a cada frame criado: a tela relê os artefatos quando muda, para o ícone no texto acompanhar (N4). */
    val versaoDosFrames: Int = 0,
    /** Prompts com uma imagem sendo enviada (J3): um envio por prompt. O valor é a fração enviada, ou `null` se o total é desconhecido. */
    val importandoImagem: Map<Int, Float?> = emptyMap(),
    /** O recado de cada prompt sobre a importação: o motivo da recusa ou da falha, ou "Imagem importada." (J2, J3). */
    val mensagensDeImagem: Map<Int, MensagemDoElemento> = emptyMap(),
    /** Como cada frame se chama nos avisos ("A partida", "Retrato de Jon"); guardado ao pedir o prompt (N6). */
    val rotulosDeFrame: Map<Int, String> = emptyMap(),
) {
    /** A sugestão de elemento que está num modal aberto (o de cima, se houver mais de um), ou `null`. */
    val emModal: Int? get() = modais.filterIsInstance<ModalAberto.DeElemento>().lastOrNull()?.sugestaoId

    /** A cena que está num modal aberto, ou `null`. */
    val emModalCena: Int? get() = modais.filterIsInstance<ModalAberto.DeCena>().lastOrNull()?.cenaId
}

/** Um modal da pilha (C12): ou uma sugestão de elemento, ou uma cena. */
sealed interface ModalAberto {
    data class DeElemento(val sugestaoId: Int) : ModalAberto
    data class DeCena(val cenaId: Int) : ModalAberto
}

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
    /**
     * Onde as análises de IA **de fato rodam** (defeito D1): no escopo do app, e não neste ViewModel, para
     * sobreviverem a quem sai da tela antes de elas terminarem. O padrão cria um serviço próprio (testes).
     */
    private val prompts: RepositorioDePrompts = PromptsSemServidor,
    private val servico: ServicoDeAnalises = ServicoDeAnalises(
        sugestoes,
        CoroutineScope(SupervisorJob() + Dispatchers.Main),
        prompts,
    ),
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDoPainel())
    val estado: StateFlow<EstadoDoPainel> = _estado.asStateFlow()

    /** De qual livro é o capítulo: a tela o informa quando o capítulo carrega. */
    private var livroId: Int? = null

    /** Como o capítulo se chama nos avisos de análise ("capítulo 3"): a tela informa quando o capítulo carrega. */
    private var rotuloDoCapitulo: String = "capítulo"

    init {
        // Voltou à tela com uma análise ainda rodando (ou que acabou de terminar): reencontra o "analisando".
        servico.emAndamento(capituloId)?.let { trabalho ->
            _estado.update { it.copy(analisando = true, erroDaAnalise = null) }
            aguardar(trabalho)
        }
    }

    /** Os elementos do livro, buscados uma vez para o diálogo de vincular (E3). */
    private var elementosDoLivro: List<ElementoDoLivro>? = null

    fun definirLivro(id: Int) {
        livroId = id
        servico.registrarLivro(capituloId, id)
    }

    /** O nome do capítulo para o aviso de análise concluída (D1). */
    fun definirRotuloDoCapitulo(ordem: Int, titulo: String?) {
        rotuloDoCapitulo = rotuloDoCapituloNoAviso(ordem, titulo)
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
        // Vindo do ícone no texto, começa uma pilha nova (C12).
        _estado.update { it.copy(modais = listOf(ModalAberto.DeElemento(sugestaoId))) }
        aoAbrirPainel()
    }

    /**
     * "Ver ficha" a partir do modal **fecha o modal** (decisão do Allan após o teste de 01/10/2026): ao voltar,
     * quem lê cai no texto onde estava, sem o modal reabrindo sozinho "instantes depois", o que parecia um defeito.
     * Os **diálogos** (vincular, criar...) não fecham: estão no meio de uma tarefa e voltam como estavam (E43).
     */
    fun fecharModalAoAbrirFicha() {
        _estado.update { it.copy(modais = emptyList()) } // todos: ao voltar, quem lê cai no texto
    }

    /** Fecha o modal. */
    fun fecharModal() {
        _estado.update { it.copy(modais = it.modais.filterNot { m -> m is ModalAberto.DeElemento }) }
    }

    // ------------------------------------------------------------------ //
    // Novo retrato (incremento 10b, terceira fatia, N1 a N8)
    // ------------------------------------------------------------------ //

    /**
     * "Novo retrato" (N2): cria o frame `PERSONAGEM` do [elemento] com o **estado que vale neste capítulo**. Só para
     * elemento confirmado (N1); **uma criação por elemento de cada vez, sem repetição automática** (N5); **não gasta IA**.
     * Sucesso: o app guarda o frame novo (N4) e a tela relê os artefatos. Falha: a mensagem do servidor no modal.
     */
    fun criarRetrato(elemento: ElementoSugerido) {
        val estadoId = elemento.estado_vigente?.id ?: return
        if (!podeTerRetrato(elemento) || elemento.id in _estado.value.retratosOcupados) return
        _estado.update {
            it.copy(retratosOcupados = it.retratosOcupados + elemento.id, mensagensDeRetrato = it.mensagensDeRetrato - elemento.id)
        }
        viewModelScope.launch {
            val resultado = sugestoes.criarRetrato(capituloId, estadoId)
            _estado.update { atual ->
                when (resultado) {
                    is ResultadoDaChamada.Sucesso -> atual.copy(
                        retratosOcupados = atual.retratosOcupados - elemento.id,
                        retratosCriados = atual.retratosCriados + (elemento.id to resultado.dado.id),
                        rotulosDeFrame = atual.rotulosDeFrame + (resultado.dado.id to rotuloDoRetrato(elemento)),
                        versaoDosFrames = atual.versaoDosFrames + 1,
                    )
                    is ResultadoDaChamada.Falha -> atual.copy(
                        retratosOcupados = atual.retratosOcupados - elemento.id,
                        mensagensDeRetrato = atual.mensagensDeRetrato + (elemento.id to MensagemDoElemento(resultado.motivo, ehErro = true)),
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Gerar o prompt e copiar (incremento 10b, segunda fatia, G1 a G10)
    // ------------------------------------------------------------------ //

    /**
     * Lê os prompts já gerados do frame (G2) — **só o `GET`, nunca gasta IA**. Lê **uma vez**: o que já foi lido (ou
     * está sendo lido) não se pede de novo; um erro de leitura se refaz por [recarregarPrompts].
     */
    fun carregarPrompts(frameId: Int) {
        // Qualquer entrada (lendo, pronta ou com erro) segura a leitura: o erro só se refaz por [recarregarPrompts].
        if (_estado.value.prompts[frameId] != null) return
        lerPrompts(frameId)
        // Voltou ao modal com uma geração ainda rodando (G8, revisto): reencontra o "gerando" e recebe o resultado.
        servico.promptEmAndamento(frameId)?.let { trabalho ->
            _estado.update { it.copy(gerandoPrompt = it.gerandoPrompt + frameId) }
            aguardarPrompt(frameId, trabalho)
        }
    }

    /** "Tentar de novo" depois de um erro de leitura. */
    fun recarregarPrompts(frameId: Int) {
        if (_estado.value.prompts[frameId] is PromptsDoFrame.Erro) lerPrompts(frameId)
    }

    private fun lerPrompts(frameId: Int) {
        _estado.update { it.copy(prompts = it.prompts + (frameId to PromptsDoFrame.Lendo)) }
        viewModelScope.launch {
            val novo = when (val resultado = prompts.listar(frameId)) {
                // Do mais novo para o mais antigo (G2): o servidor entrega do mais antigo ao mais recente.
                is ResultadoDaChamada.Sucesso -> PromptsDoFrame.Pronto(comAsImagens(resultado.dado).reversed())
                is ResultadoDaChamada.Falha -> PromptsDoFrame.Erro(resultado.motivo)
            }
            _estado.update { it.copy(prompts = it.prompts + (frameId to novo)) }
        }
    }

    /**
     * A listagem do frame só diz **quantas** imagens cada prompt tem; as imagens vêm em `GET /prompts/{id}` (J5). Busca-se
     * só dos prompts que têm, e uma falha ali não derruba a lista: o prompt aparece sem as miniaturas.
     */
    private suspend fun comAsImagens(lista: List<PromptDeFrame>): List<PromptDeFrame> = lista.map { prompt ->
        if (prompt.total_de_imagens == 0) {
            prompt
        } else {
            when (val detalhe = prompts.detalhar(prompt.id)) {
                is ResultadoDaChamada.Sucesso -> prompt.copy(imagens = detalhe.dado.imagens)
                is ResultadoDaChamada.Falha -> prompt
            }
        }
    }

    /**
     * Importar a imagem escolhida para um prompt (J1 a J6). [arquivo] é `null` quando o app não conseguiu descrever o
     * arquivo. Confere extensão e tamanho **antes** de enviar (J2); um envio por prompt, sem repetição automática (J3).
     */
    fun importarImagem(frameId: Int, promptId: Int, arquivo: ArquivoEscolhido?) {
        if (promptId in _estado.value.importandoImagem) return
        val recusa = if (arquivo == null) "Não consegui abrir o arquivo escolhido." else motivoParaNaoImportar(arquivo)
        if (arquivo == null || recusa != null) {
            avisarSobreImagem(promptId, recusa!!, ehErro = true)
            return
        }
        _estado.update {
            it.copy(importandoImagem = it.importandoImagem + (promptId to null), mensagensDeImagem = it.mensagensDeImagem - promptId)
        }
        viewModelScope.launch {
            val resultado = prompts.importarImagem(promptId, arquivo) { enviados, total ->
                val fracao = if (total != null && total > 0) (enviados.toFloat() / total).coerceIn(0f, 1f) else null
                _estado.update { atual ->
                    if (promptId in atual.importandoImagem) atual.copy(importandoImagem = atual.importandoImagem + (promptId to fracao)) else atual
                }
            }
            _estado.update { atual ->
                val semEnvio = atual.importandoImagem - promptId
                when (resultado) {
                    is ResultadoDaChamada.Sucesso -> atual.copy(
                        importandoImagem = semEnvio,
                        prompts = atual.prompts + (frameId to comAImagemNova(atual.prompts[frameId], promptId, resultado.dado)),
                        mensagensDeImagem = atual.mensagensDeImagem + (promptId to MensagemDoElemento("Imagem importada.", ehErro = false)),
                        // J6: a tela relê os artefatos e o ícone no texto passa a ILUSTRADO.
                        versaoDosFrames = atual.versaoDosFrames + 1,
                    )
                    is ResultadoDaChamada.Falha -> atual.copy(
                        importandoImagem = semEnvio,
                        mensagensDeImagem = atual.mensagensDeImagem + (promptId to MensagemDoElemento(resultado.motivo, ehErro = true)),
                    )
                }
            }
        }
    }

    private fun avisarSobreImagem(promptId: Int, texto: String, ehErro: Boolean) {
        _estado.update { it.copy(mensagensDeImagem = it.mensagensDeImagem + (promptId to MensagemDoElemento(texto, ehErro))) }
    }

    /** Põe a imagem recém-importada **na frente** (a mais nova primeiro, J4) do prompt dela, sem duplicar. */
    private fun comAImagemNova(atual: PromptsDoFrame?, promptId: Int, imagem: com.allan.imagineer.rede.ImagemDoPrompt): PromptsDoFrame {
        val lista = (atual as? PromptsDoFrame.Pronto)?.lista ?: return atual ?: PromptsDoFrame.Lendo
        return PromptsDoFrame.Pronto(
            lista.map { prompt ->
                if (prompt.id != promptId || prompt.imagens.any { it.id == imagem.id }) {
                    prompt
                } else {
                    prompt.copy(imagens = listOf(imagem) + prompt.imagens, total_de_imagens = prompt.total_de_imagens + 1)
                }
            },
        )
    }

    /** "Gerar prompt": **pede confirmação** antes de gastar IA (G3). Ignora se já há uma geração rodando para o frame. */
    fun pedirGerarPrompt(frameId: Int, rotulo: String? = null) {
        if (frameId in _estado.value.gerandoPrompt) return
        _estado.update {
            it.copy(
                confirmandoPrompt = frameId,
                mensagensDePrompt = it.mensagensDePrompt - frameId,
                rotulosDeFrame = if (rotulo != null) it.rotulosDeFrame + (frameId to rotulo) else it.rotulosDeFrame,
            )
        }
    }

    fun cancelarGerarPrompt() {
        _estado.update { it.copy(confirmandoPrompt = null) }
    }

    /**
     * O "sim" do diálogo (G4): gera o prompt, com o [ajuste] opcional (em branco = sem comentário). **Sem repetição
     * automática** e **uma geração por frame de cada vez**. Sucesso: o prompt novo vai para o topo da lista (G5).
     * Falha: a mensagem do servidor no modal, e a lista de antes continua (G7).
     */
    fun gerarPrompt(frameId: Int, ajuste: String) {
        if (_estado.value.confirmandoPrompt != frameId || frameId in _estado.value.gerandoPrompt) return
        _estado.update {
            it.copy(confirmandoPrompt = null, gerandoPrompt = it.gerandoPrompt + frameId, mensagensDePrompt = it.mensagensDePrompt - frameId)
        }
        val comentario = ajuste.trim().ifBlank { null }
        // O trabalho roda no serviço do app (G8, revisto): sair do capítulo não o cancela, e ao terminar sai o aviso.
        val cena = (_estado.value.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes?.cenas?.firstOrNull { it.frame_id == frameId }
        val rotulo = _estado.value.rotulosDeFrame[frameId] ?: cena?.titulo ?: "cena"
        aguardarPrompt(frameId, servico.iniciarPrompt(frameId, capituloId, livroId, rotuloDoCapitulo, rotulo, comentario))
    }

    /** Espera o resultado de uma geração (a nossa, ou a que já estava rodando) e o aplica à lista (G5, G7). */
    private fun aguardarPrompt(frameId: Int, trabalho: Deferred<ResultadoDaChamada<PromptDeFrame>>) {
        viewModelScope.launch {
            val resultado = trabalho.await()
            _estado.update { atual ->
                val daLista = (atual.prompts[frameId] as? PromptsDoFrame.Pronto)?.lista.orEmpty()
                when (resultado) {
                    is ResultadoDaChamada.Sucesso -> atual.copy(
                        gerandoPrompt = atual.gerandoPrompt - frameId,
                        // Sem repetir: se a leitura já trouxe este prompt (gravado antes de ela terminar), não duplica.
                        prompts = atual.prompts + (frameId to PromptsDoFrame.Pronto(
                            if (daLista.any { it.id == resultado.dado.id }) daLista else listOf(resultado.dado) + daLista,
                        )),
                        promptsGerados = atual.promptsGerados + (frameId to ((atual.promptsGerados[frameId] ?: 0) + 1)),
                    )
                    is ResultadoDaChamada.Falha -> atual.copy(
                        gerandoPrompt = atual.gerandoPrompt - frameId,
                        mensagensDePrompt = atual.mensagensDePrompt + (frameId to MensagemDoElemento(resultado.motivo, ehErro = true)),
                    )
                }
            }
        }
    }

    /** O modal de cena deste frame está na tela (ou deixou de estar, com `null`): o aviso global se cala (G13). */
    fun definirModalDoFrameVisivel(frameId: Int?) {
        servico.definirModalDoFrameVisivel(frameId)
    }

    // ------------------------------------------------------------------ //
    // Confirmar todos (pedido do Allan, 01/10/2026)
    // ------------------------------------------------------------------ //

    /** "Confirmar todos": abre o diálogo com a conta do que vai acontecer — só se há algo a confirmar (L1, L2). */
    fun pedirConfirmarTodos() {
        val atual = _estado.value
        val sugestoesDeAgora = (atual.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes ?: return
        if (atual.executandoLote || atual.analisando) return
        val resumo = resumoParaConfirmarTodos(sugestoesDeAgora)
        if (!resumo.temAlgoParaConfirmar) return
        _estado.update { it.copy(confirmandoTodos = resumo) }
    }

    fun cancelarConfirmarTodos() {
        _estado.update { it.copy(confirmandoTodos = null) }
    }

    fun dispensarResultadoDoLote() {
        _estado.update { it.copy(resultadoDoLote = null) }
    }

    /**
     * O "sim" do diálogo (L3): roda o lote **em ordem e uma chamada por vez** — (1) confirma os casamentos
     * automáticos, (2) registra o estado de quem ainda não tem, (3) tenta confirmar as cenas pendentes. Relê as
     * sugestões entre os passos, porque cada um muda o que o seguinte encontra.
     *
     * **Nunca cria elemento novo, nunca descarta e nunca gasta IA** (L6). Uma chamada que o servidor recusa deixa o
     * item pendente e o lote **continua**; já uma falha de **conexão** (sem código HTTP) o **interrompe** (L4), para
     * não esperar N tempos limites seguidos. Sem repetição automática.
     */
    fun confirmarTodos() {
        val atual = _estado.value
        if (atual.confirmandoTodos == null || atual.executandoLote) return
        _estado.update { it.copy(confirmandoTodos = null, executandoLote = true, resultadoDoLote = null) }

        viewModelScope.launch {
            val falhas = mutableListOf<String>()
            var interrompidoPor: String? = null
            var casamentos = 0
            var estados = 0
            var cenas = 0

            /** Registra o resultado de uma chamada; devolve `true` se deu certo (ou se já estava feito, 409). */
            fun deuCerto(resultado: ResultadoDaChamada<*>, rotulo: String): Boolean {
                if (resultado is ResultadoDaChamada.Sucesso) return true
                val falha = resultado as ResultadoDaChamada.Falha
                when {
                    falha.codigoHttp == 409 -> return true
                    falha.codigoHttp == null -> interrompidoPor = falha.motivo
                    else -> falhas += "$rotulo: ${falha.motivo}"
                }
                return false
            }

            fun sugestoesAgora(): SugestoesDeCapitulo? = (_estado.value.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes

            // 1) os casamentos automáticos
            for (e in sugestoesAgora()?.elementos.orEmpty().filter { !it.descartada && situacaoDoElemento(it) == SituacaoDoElemento.CASADA_AUTOMATICAMENTE }) {
                if (interrompidoPor != null) break
                if (deuCerto(elementos.ajustarCasamento(e.id, e.elemento_id), e.nome)) casamentos++
            }
            reler()
            esquecerFichas()

            // 2) o estado de quem ainda não tem (inclusive quem acabou de ter o casamento confirmado)
            for (e in sugestoesAgora()?.elementos.orEmpty().filter { !it.descartada && situacaoDoElemento(it) == SituacaoDoElemento.CASADA_SEM_ESTADO }) {
                if (interrompidoPor != null) break
                val elementoId = e.elemento_id ?: continue
                if (deuCerto(elementos.registrarEstado(elementoId, e.id), e.nome)) estados++
            }
            if (interrompidoPor == null) reler()

            // 3) as cenas pendentes
            if (interrompidoPor == null) {
                for (c in cenasDoFiltro(sugestoesAgora()?.cenas.orEmpty(), FiltroDoPainel.PENDENTES)) {
                    if (interrompidoPor != null) break
                    if (deuCerto(sugestoes.confirmarCena(capituloId, c.id), c.titulo)) cenas++
                }
                reler()
            }

            val novosRestantes = sugestoesAgora()?.elementos.orEmpty()
                .count { !it.descartada && situacaoDoElemento(it) == SituacaoDoElemento.NOVA }
            _estado.update {
                it.copy(
                    executandoLote = false,
                    resultadoDoLote = descreverResultadoDoLote(casamentos, estados, cenas, falhas, interrompidoPor, novosRestantes),
                )
            }
        }
    }

    // ------------------------------------------------------------------ //
    // A cena (incremento 10b, primeira fatia, C1 a C10)
    // ------------------------------------------------------------------ //

    /** Abre a cena [cenaId] no modal (C1); fecha o modal de elemento, se estava aberto (C2). */
    fun abrirModalDeCena(cenaId: Int) {
        _estado.update { it.copy(modais = listOf(ModalAberto.DeCena(cenaId))) } // pilha nova (C12)
        aoAbrirPainel()
    }

    fun fecharModalDaCena() {
        _estado.update { it.copy(modais = it.modais.filterNot { m -> m is ModalAberto.DeCena }) }
    }

    /**
     * "Revisar" num participante sem elemento (C3): fecha o modal da cena e abre o **do elemento** dele, onde se
     * confirma, vincula ou cria — o caminho mais curto para destravar a cena.
     */
    fun revisarParticipante(sugestaoElementoId: Int) {
        // C12: **empilha** o modal do elemento por cima do da cena; fechar o de cima revela o de baixo.
        _estado.update {
            it.copy(modais = it.modais.filterNot { m -> m == ModalAberto.DeElemento(sugestaoElementoId) } + ModalAberto.DeElemento(sugestaoElementoId))
        }
    }

    /** Uma das ações de decisão da cena (C4): confirmar, descartar ou restaurar. Nenhuma gasta IA (C8). */
    fun executarCena(acao: AcaoDaCena, cena: CenaSugerida) {
        when (acao) {
            AcaoDaCena.CONFIRMAR ->
                rodarCena(cena, aviso = AVISO_CENA_CONFIRMADA) { sugestoes.confirmarCena(capituloId, cena.id) }
            AcaoDaCena.DESCARTAR -> rodarCena(cena) { sugestoes.descartarCena(cena.id, true) }
            AcaoDaCena.RESTAURAR -> rodarCena(cena) { sugestoes.descartarCena(cena.id, false) }
        }
    }

    /**
     * Roda uma ação **de uma vez por cena** (C7): se já há uma em andamento naquela cena, ignora. Sucesso = relê as
     * sugestões, fecha o modal da cena (como o E44) e mostra o [aviso], se houver. Falha = a mensagem do servidor
     * **no modal, sem fechá-lo** (C5, 422); **409** (a cena já estava confirmada) relê, fecha e avisa. **Sem
     * repetição automática.**
     */
    private fun rodarCena(
        cena: CenaSugerida,
        aviso: String? = null,
        chamada: suspend () -> ResultadoDaChamada<Any?>,
    ) {
        if (cena.id in _estado.value.cenasOcupadas) return
        _estado.update {
            it.copy(cenasOcupadas = it.cenasOcupadas + cena.id, mensagensDeCena = it.mensagensDeCena - cena.id)
        }
        viewModelScope.launch {
            val resultado = chamada()
            val jaConfirmada = resultado is ResultadoDaChamada.Falha && resultado.codigoHttp == 409
            if (resultado is ResultadoDaChamada.Sucesso || jaConfirmada) reler()

            _estado.update { atual ->
                val recado = when {
                    resultado is ResultadoDaChamada.Sucesso -> aviso?.let { MensagemDoElemento(it, ehErro = false) }
                    jaConfirmada -> MensagemDoElemento(AVISO_CENA_JA_CONFIRMADA, ehErro = false)
                    else -> MensagemDoElemento((resultado as ResultadoDaChamada.Falha).motivo, ehErro = true)
                }
                val concluiu = resultado is ResultadoDaChamada.Sucesso || jaConfirmada
                atual.copy(
                    cenasOcupadas = atual.cenasOcupadas - cena.id,
                    mensagensDeCena = if (recado != null) atual.mensagensDeCena + (cena.id to recado) else atual.mensagensDeCena,
                    modais = if (concluiu) atual.modais.filterNot { it == ModalAberto.DeCena(cena.id) } else atual.modais,
                )
            }
        }
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

    /**
     * O "sim" do diálogo: refaz as sugestões ainda não confirmadas (`forcar = true`).
     *
     * [orientacaoDigitada] é o texto do campo "o que a análise não pegou?" (item 6.7, M1). Só vai ao servidor
     * quando **mudou** em relação à orientação que já vale no capítulo: igual (ou campo não mostrado, `null`)
     * = `null`, e o servidor reaproveita a guardada; diferente = o texto novo, e **vazio apaga** a guardada.
     */
    fun confirmarReanalise(orientacaoDigitada: String? = null) {
        val atual = _estado.value
        if (!atual.confirmandoReanalise || atual.analisando) return
        _estado.update { it.copy(confirmandoReanalise = false) }
        executarAnalise(forcar = true, orientacao = orientacaoAEnviar(orientacaoDigitada, orientacaoVigente(atual)))
    }

    /** A orientação que já vale neste capítulo (a do servidor), ou `null` se não há. */
    private fun orientacaoVigente(estado: EstadoDoPainel): String? =
        (estado.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes?.orientacao

    /**
     * O único ponto que chama o `POST`. **Sem repetição automática** (P8): repetir sozinho
     * cobraria duas vezes sem ninguém pedir. Se falhar, o conteúdo de antes continua e a
     * mensagem da API aparece.
     */
    private fun executarAnalise(forcar: Boolean, orientacao: String? = null) {
        _estado.update { it.copy(analisando = true, erroDaAnalise = null) }
        // O trabalho roda no serviço do app (D1): se o usuário sair da tela, a análise continua e ele é avisado.
        aguardar(servico.iniciar(capituloId, livroId, rotuloDoCapitulo, forcar, orientacao))
    }

    /** Espera o resultado de uma análise (a nossa, ou a que já estava rodando) e o aplica ao painel. */
    private fun aguardar(trabalho: Deferred<ResultadoDaChamada<SugestoesDeCapitulo>>) {
        viewModelScope.launch {
            when (val resultado = trabalho.await()) {
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
        _estado.update { it.copy(modais = it.modais.filterNot { m -> m == ModalAberto.DeElemento(sugestaoId) }) }
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
