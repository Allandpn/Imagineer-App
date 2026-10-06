package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.ImagemDoPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.analise.rotuloDoCapituloNoAviso
import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.rede.VinculadoDoFrame
import com.allan.imagineer.rede.CapituloComElementos
import com.allan.imagineer.rede.ElementosParaVincular
import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.ModelosDeImagem
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.VideoImportado
import com.allan.imagineer.rede.motivoParaNaoImportar
import com.allan.imagineer.rede.motivoParaNaoImportarVideo
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
    /** [capitulos]: os elementos por capítulo, com as miniaturas (VM2); vazio se não deu para ler (aí a lista cai em "Sem capítulo"). */
    data class Pronta(val elementos: List<ElementoDoLivro>, val capitulos: List<CapituloComElementos> = emptyList()) : ListaParaVincular
    data class Erro(val motivo: String) : ListaParaVincular
}

/**
 * O que a pessoa está montando a partir de um **trecho selecionado** (TR1 a TR3): o [trecho], o [posicao] do parágrafo dele (nulo se a
 * seleção não coube num parágrafo), o que ela quer ver ([descricao]) e os elementos que a cena leva ([estadosEscolhidos]).
 */
data class TrechoParaImagem(
    val trecho: String,
    val posicao: Int?,
    val descricao: String = "",
    val estadosEscolhidos: Set<Int> = emptySet(),
    val criando: Boolean = false,
    val erro: String? = null,
    /** Os elementos que o seletor oferece à cena (identificados, outros do capítulo, de outros capítulos), lidos ao abrir (LV8). */
    val candidatos: CandidatosDoSeletor = CandidatosDoSeletor.Carregando,
)

/**
 * O modo **"toque no parágrafo"** (PM1): o artefato [rotulo] de [sugestaoId] (cena se [ehCena]) vai para o parágrafo que a pessoa tocar
 * no texto. [erro] é a recusa do servidor; o modo continua até a pessoa tocar de novo ou cancelar.
 */
data class PosicionandoArtefato(val ehCena: Boolean, val sugestaoId: Int?, val rotulo: String, val erro: String? = null, val frameId: Int? = null)

/** O diálogo "Apagar esta cena?" (a cena de um trecho): o frame, o nome, se está apagando e a recusa do servidor. */
/** A edição aberta do título e da descrição de uma cena: de uma **sugestão** ([sugestaoId]) ou de um frame sem sugestão ([frameId]). */
data class EdicaoDaCena(
    val sugestaoId: Int? = null,
    val frameId: Int? = null,
    val titulo: String = "",
    val descricao: String = "",
    val carregando: Boolean = false,
    val salvando: Boolean = false,
    val erro: String? = null,
)

data class ApagandoFrame(
    val frameId: Int,
    val rotulo: String,
    val apagando: Boolean = false,
    val erro: String? = null,
    /** O frame é o de uma **sugestão de cena** (o modal da cena): depois de apagado, a sugestão continua na lista como pendente. */
    val deSugestao: Boolean = false,
)

/** Os prompts de **vídeo** de um frame (item 4.8): lidos uma vez, e o novo entra na frente ao gerar. */
sealed interface VideosDoFrame {
    data object Lendo : VideosDoFrame
    data class Pronto(val lista: List<PromptDeFrame>) : VideosDoFrame
    data class Erro(val motivo: String) : VideosDoFrame
}

/** Os vídeos **importados** de um frame (item 4.8, VD16): lidos uma vez; o importado entra na frente. */
sealed interface VideosImportadosDoFrame {
    data object Lendo : VideosImportadosDoFrame
    data class Pronto(val lista: List<VideoImportado>) : VideosImportadosDoFrame
    data class Erro(val motivo: String) : VideosImportadosDoFrame
}

/** O frame (e o prompt de vídeo de origem, se foi dele que a pessoa tocou em importar) do vídeo que o seletor está escolhendo. */
data class AlvoDaImportacaoDeVideo(val frameId: Int, val promptId: Int?)

/** O diálogo "Prompt de vídeo" (VD9): as [imagens] do frame, qual é o primeiro quadro ([escolhida]), o comentário e o andamento. */
data class DialogoDeVideo(
    val frameId: Int,
    val rotulo: String,
    val imagens: List<ImagemDoPrompt>,
    val escolhida: Int?,
    val comentario: String = "",
    val gerando: Boolean = false,
    val erro: String? = null,
)

/** O livro por capítulo, dentro do seletor de "usar uma imagem que já existe" (VM3). */
sealed interface CargaPorCapitulo {
    data object Carregando : CargaPorCapitulo
    data class Pronta(val capitulos: List<CapituloComElementos>) : CargaPorCapitulo
    data class Erro(val motivo: String) : CargaPorCapitulo
}

/**
 * "Usar imagem existente" (VM3, VM4): o seletor das imagens que **o elemento** já tem, de qualquer capítulo, para o retrato dele
 * apontar uma (a canônica) sem gerar nada. [frameId] nulo = o retrato ainda não existe: é criado ao escolher.
 */
data class UsoDeImagemExistente(
    val elemento: ElementoSugerido,
    val frameId: Int?,
    val carga: CargaPorCapitulo,
    val aplicando: Boolean = false,
    val erro: String? = null,
)

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
    /** O capítulo aberto: o seletor por capítulo o mostra primeiro (VM2). */
    val capituloAtualId: Int? = null,
    /** O diálogo de apagar a cena de um trecho está aberto; `null` = fechado. */
    val apagandoFrame: ApagandoFrame? = null,
    /** A edição do título e da descrição de uma cena (LV6); `null` = fechada. */
    val editandoCena: EdicaoDaCena? = null,
    /** O diálogo de gerar imagem de um trecho está aberto (TR2); `null` = fechado. */
    val trechoParaImagem: TrechoParaImagem? = null,
    /** O modo de posicionar um artefato à mão está ligado (PM1); `null` = desligado. */
    val posicionando: PosicionandoArtefato? = null,
    /** O seletor de "usar imagem existente" está aberto (VM3); `null` = fechado. */
    val usandoImagemExistente: UsoDeImagemExistente? = null,
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
    /** Os prompts de vídeo de cada frame, por id do frame (item 4.8). Só existe a entrada de quem já foi aberto. */
    val videos: Map<Int, VideosDoFrame> = emptyMap(),
    /** O diálogo de gerar o prompt de vídeo; `null` = fechado. */
    val dialogoDeVideo: DialogoDeVideo? = null,
    /** Para onde vai o vídeo que o seletor do Android está escolhendo (VD18); `null` = nenhum seletor aberto. */
    val alvoDoVideo: AlvoDaImportacaoDeVideo? = null,
    /** Os vídeos importados de cada frame, por id do frame (VD16). Só existe a entrada de quem já foi aberto. */
    val videosImportados: Map<Int, VideosImportadosDoFrame> = emptyMap(),
    /** Frames com um vídeo sendo enviado (VD18): a fração enviada, ou `null` se ainda não se sabe. */
    val importandoVideo: Map<Int, Float?> = emptyMap(),
    /** O recado de cada frame sobre os vídeos: o erro do envio, ou o aviso do que acabou de acontecer. */
    val mensagensDeVideo: Map<Int, MensagemDoElemento> = emptyMap(),
    /**
     * A imagem canônica de cada frame, por id do frame (VM5). Existe porque ela pode ser de **outro** frame (a "imagem existente" que a
     * pessoa usou): ela não vem na lista de imagens dos prompts deste frame, e a miniatura não aparecia.
     */
    val canonicasDosFrames: Map<Int, Int> = emptyMap(),
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
    /**
     * Para onde vai a imagem que o seletor do Android vai devolver (J2): o frame e o prompt. Fica **aqui**, e não no botão,
     * porque o seletor tira o app da frente e o modal some enquanto ele está aberto; quando a escolha volta, quem a recebe
     * é a tela do capítulo, que consulta este alvo.
     */
    val alvoDaImportacao: AlvoDaImportacao? = null,
    /** O recado de cada prompt sobre a **importação**: a recusa, a falha ou "Imagem importada." (J2, J3). Separado do da geração (K3). */
    val mensagensDeImportacao: Map<Int, MensagemDoElemento> = emptyMap(),
    /**
     * O que o botão principal está fazendo agora (Q4), por chave (`chaveDoFluxoDoRetrato` / `chaveDoFluxoDoFrame`): um toque
     * por chave de cada vez. Sem entrada = parado.
     */
    val etapasDeImagem: Map<String, EtapaDaImagem> = emptyMap(),
    /** Os modelos de imagem que se pode escolher, lidos do servidor (Z2, Z6); `null` enquanto não vieram. */
    val modelosDeImagem: ModelosDeImagem? = null,
    /** O modelo que a pessoa escolheu para as próximas gerações (Z6); `null` = o padrão do servidor. Vale até trocar. */
    val modeloEscolhido: String? = null,
    /** O diálogo de escolher o modelo de imagem está aberto (Z6). */
    val escolhendoModelo: Boolean = false,
    /** As imagens de referência que a pessoa escolheu **por frame** (W10, EV7); não vão ao servidor nem sobrevivem ao app. */
    val referenciasEscolhidas: Map<Int, List<Int>> = emptyMap(),
    /** Os nomes dos elementos **vinculados** a cada retrato, por frame (EV8); lidos do servidor uma vez e atualizados ao usar o seletor. */
    val vinculadosPorFrame: Map<Int, List<String>> = emptyMap(),
    /** O seletor de elementos e imagens está aberto (EV1); `null` = fechado. */
    val escolhaDeElementos: EscolhaDeElementos? = null,
    /** Os frames cujos elementos mudaram, com **quantos prompts tinham** na hora (EV10): o aviso some quando nasce um prompt novo. */
    val mudancasPendentesDePrompt: Map<Int, Int> = emptyMap(),
    /** O diálogo "Excluir esta imagem?" está aberto para esta imagem (U3); `null` = sem diálogo. */
    val excluindoImagem: ImagemParaExcluir? = null,
    /** O diálogo de **editar o prompt** está aberto (R1); `null` = sem diálogo. */
    val edicaoDePrompt: EdicaoDePrompt? = null,
    /** Prompts com uma imagem sendo **gerada** pelo servidor (K2): um pedido por prompt. */
    val gerandoImagem: Set<Int> = emptySet(),
    /** O provedor recusou de novo e a pessoa pode editar o prompt para tentar outra vez (K4); `null` = sem diálogo. */
    val recusaDeImagem: RecusaDeImagem? = null,
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

    /** Um frame **sem sugestão por trás** (a cena de um trecho, TR4): só os prompts e as imagens dele. */
    data class DeFrame(val frameId: Int, val rotulo: String) : ModalAberto
}

/** Quanto a pessoa pode escrever do que quer ver na cena de um trecho (TR2). */
const val LIMITE_DA_DESCRICAO_DO_TRECHO = 1000

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
/**
 * O que o diálogo da recusa precisa (K4): o prompt que o servidor enviou por último (agora editável), o motivo que o provedor
 * deu e o frame a que pertence (para a lista ser relida depois da nova tentativa).
 */
data class AlvoDaImportacao(val frameId: Int, val promptId: Int)

/** A imagem que a pessoa quer excluir (U3): o frame e o prompt a que pertence, o id e a origem (para o recado ir ao lugar certo). */
data class ImagemParaExcluir(val frameId: Int, val promptId: Int, val imagemId: Int, val origem: String)

/** O seletor de elementos e imagens (EV1): de que frame, o que o servidor devolveu, o que está marcado e se está gravando. */
data class EscolhaDeElementos(
    val frameId: Int,
    val ehCena: Boolean,
    val candidatos: CandidatosDoSeletor,
    val selecao: SelecaoNoSeletor,
    val salvando: Boolean = false,
    val erro: String? = null,
)

sealed interface CandidatosDoSeletor {
    data object Carregando : CandidatosDoSeletor
    data class Prontos(val dados: ElementosParaVincular) : CandidatosDoSeletor
    data class Erro(val motivo: String) : CandidatosDoSeletor
}

/** O que o diálogo de editar o prompt precisa (R1): o frame, o prompt e o texto de partida. */
data class EdicaoDePrompt(val frameId: Int, val promptId: Int, val texto: String, val emPortugues: Boolean = false)

data class RecusaDeImagem(val frameId: Int, val promptId: Int, val texto: String, val motivo: String, val modelo: String? = null)

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

    private val _estado = MutableStateFlow(EstadoDoPainel(capituloAtualId = capituloId))
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
    private var elementosPorCapitulo: List<CapituloComElementos>? = null

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
        elementosPorCapitulo = null
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
        val estadoId = reservarRetrato(elemento) ?: return
        viewModelScope.launch { concluirCriacaoDoRetrato(elemento, estadoId) }
    }

    /** Marca o retrato como ocupado (N5) e devolve o estado a usar; `null` se não pode (N1) ou se já está sendo criado. */
    private fun reservarRetrato(elemento: ElementoSugerido): Int? {
        val estadoId = elemento.estado_vigente?.id ?: return null
        if (!podeTerRetrato(elemento) || elemento.id in _estado.value.retratosOcupados) return null
        _estado.update {
            it.copy(retratosOcupados = it.retratosOcupados + elemento.id, mensagensDeRetrato = it.mensagensDeRetrato - elemento.id)
        }
        return estadoId
    }

    /** Cria o frame do retrato e guarda o resultado (N4). Devolve o id do frame, ou `null` se falhou (a mensagem já foi posta). */
    private suspend fun concluirCriacaoDoRetrato(elemento: ElementoSugerido, estadoId: Int): Int? {
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
        return (resultado as? ResultadoDaChamada.Sucesso)?.dado?.id
    }

    // ------------------------------------------------------------------ //
    // Gerar a imagem em um toque (incremento 12, quarta fatia: Q1 a Q9)
    // ------------------------------------------------------------------ //

    /**
     * O botão **"Gerar retrato"** de um elemento sem frame (Q1): cria o frame, gera o prompt e gera a imagem, **só o que
     * falta** (Q2). Se o retrato já foi criado nesta sessão, continua dele.
     */
    fun gerarRetrato(elemento: ElementoSugerido) = gerarRetrato(elemento, apenasPrompt = false)

    /** **"Só o prompt"** do retrato (GP2): cria o frame, se falta, e gera o prompt; **não gasta imagem**, para usar o prompt em outro app. */
    fun gerarSoOPromptDoRetrato(elemento: ElementoSugerido) = gerarRetrato(elemento, apenasPrompt = true)

    private fun gerarRetrato(elemento: ElementoSugerido, apenasPrompt: Boolean) {
        if (!podeTerRetrato(elemento)) return
        iniciarFluxoDeImagem(chaveDoFluxoDoRetrato(elemento.id), rotuloDoRetrato(elemento), _estado.value.retratosCriados[elemento.id], apenasPrompt) {
            reservarRetrato(elemento)?.let { concluirCriacaoDoRetrato(elemento, it) }
        }
    }

    /** O botão principal de um frame que já existe (a cena, ou o retrato com frame): gera o prompt, se não há, e a imagem (Q2, Q6). */
    fun gerarImagemDoFrame(chave: String, frameId: Int, rotulo: String) {
        iniciarFluxoDeImagem(chave, rotulo, frameId, false, null)
    }

    /** **"Só o prompt"** de um frame (GP2): gera o prompt, se não há nenhum, e para aí; **não gasta imagem**. */
    fun gerarSoOPromptDoFrame(chave: String, frameId: Int, rotulo: String) {
        iniciarFluxoDeImagem(chave, rotulo, frameId, true, null)
    }

    /**
     * A orquestração de Q2, com as três etapas e **sem rota nova**: (a) cria o frame, se falta; (b) gera o prompt, **se o
     * frame não tem nenhum**; (c) gera a imagem do prompt **mais recente**. Uma etapa que falha deixa as anteriores
     * gravadas e mostra a mensagem do servidor; tocar de novo **continua de onde parou** (Q5). Um toque por chave de cada vez.
     */
    private fun iniciarFluxoDeImagem(chave: String, rotulo: String, frameInicial: Int?, apenasPrompt: Boolean, criarFrame: (suspend () -> Int?)?) {
        if (chave in _estado.value.etapasDeImagem) return
        definirEtapa(chave, if (frameInicial == null) EtapaDaImagem.CRIANDO_O_RETRATO else EtapaDaImagem.GERANDO_A_IMAGEM)
        viewModelScope.launch {
            var frameId = frameInicial
            try {
                frameId = frameInicial ?: criarFrame?.invoke() ?: return@launch
                val existentes = when (val leitura = prompts.listar(frameId)) {
                    is ResultadoDaChamada.Sucesso -> leitura.dado
                    is ResultadoDaChamada.Falha -> {
                        _estado.update { it.copy(mensagensDePrompt = it.mensagensDePrompt + (frameId to MensagemDoElemento(leitura.motivo, ehErro = true))) }
                        return@launch
                    }
                }
                // O servidor entrega do mais antigo ao mais recente: o último é o que vale (Q2).
                val prompt = promptsComTexto(existentes).lastOrNull() ?: run {  // PI4: o prompt só da imagem não vale
                    definirEtapa(chave, EtapaDaImagem.MONTANDO_O_PROMPT)
                    gerarPromptDoFluxo(frameId, rotulo) ?: return@launch
                }
                if (apenasPrompt) return@launch // GP2: o prompt já está gravado; a imagem fica para quando o usuário quiser
                definirEtapa(chave, EtapaDaImagem.GERANDO_A_IMAGEM)
                if (reservarGeracaoDeImagem(prompt.id)) concluirGeracaoDeImagem(frameId, prompt.id, null, _estado.value.modeloEscolhido)
            } finally {
                _estado.update { it.copy(etapasDeImagem = it.etapasDeImagem - chave) }
                frameId?.let { relerPromptsSemPiscar(it) } // K6: o frame pode ter prompt novo e imagem nova
            }
        }
    }

    private fun definirEtapa(chave: String, etapa: EtapaDaImagem) {
        _estado.update { it.copy(etapasDeImagem = it.etapasDeImagem + (chave to etapa)) }
    }

    /** O passo "gerar o prompt" do fluxo, pelo serviço do app (como o botão Novo prompt, mas **sem confirmação**, Q3). */
    private suspend fun gerarPromptDoFluxo(frameId: Int, rotulo: String): PromptDeFrame? {
        _estado.update {
            it.copy(
                gerandoPrompt = it.gerandoPrompt + frameId,
                mensagensDePrompt = it.mensagensDePrompt - frameId,
                rotulosDeFrame = it.rotulosDeFrame + (frameId to rotulo),
            )
        }
        val resultado = servico.iniciarPrompt(frameId, capituloId, livroId, rotuloDoCapitulo, rotulo, null).await()
        aplicarResultadoDoPrompt(frameId, resultado)
        return (resultado as? ResultadoDaChamada.Sucesso)?.dado
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
            val canonica = lerAImagemCanonicaDoFrame(frameId)
            _estado.update { it.copy(prompts = it.prompts + (frameId to novo), canonicasDosFrames = it.canonicasDosFrames.com(frameId, canonica)) }
        }
    }

    /**
     * A listagem do frame só diz **quantas** imagens cada prompt tem; as imagens vêm em `GET /prompts/{id}` (J5). Busca-se
     * só dos prompts que têm, e uma falha ali não derruba a lista: o prompt aparece sem as miniaturas.
     */
    /**
     * Escolhe (ou, com [imagemId] nulo, tira) a **imagem canônica** do frame (CAN6): a que o capítulo mostra. Relê os prompts do
     * frame **sem piscar** para o selo mudar de miniatura e manda a tela reler os artefatos. A recusa vira recado do frame.
     */
    fun definirImagemCanonica(frameId: Int, imagemId: Int?) {
        viewModelScope.launch {
            aplicarMudancaDeImagemDoFrame(frameId, elementos.definirImagemCanonica(frameId, imagemId))
        }
    }

    /**
     * **Ocultar do capítulo** (OC1) ou **mostrar de novo** (OC3): o capítulo deixa de mostrar a imagem do frame, sem apagar nada. Depois
     * relê os prompts do frame e manda a tela reler os artefatos, como na canônica.
     */
    fun definirImagemOculta(frameId: Int, oculta: Boolean) {
        viewModelScope.launch {
            aplicarMudancaDeImagemDoFrame(frameId, elementos.definirImagemOculta(frameId, oculta))
        }
    }

    /** O que vem depois de uma mudança na imagem do frame (canônica ou oculta): relê os prompts sem piscar e a tela relê os artefatos. */
    /** O id da imagem canônica do frame; uma falha de leitura é "sem canônica" (a miniatura só não aparece, nada quebra). */
    private suspend fun lerAImagemCanonicaDoFrame(frameId: Int): Int? =
        (sugestoes.imagemCanonicaDoFrame(frameId) as? ResultadoDaChamada.Sucesso)?.dado

    private suspend fun aplicarMudancaDeImagemDoFrame(frameId: Int, resultado: ResultadoDaChamada<Unit>) {
        when (resultado) {
            is ResultadoDaChamada.Sucesso -> {
                val lista = (prompts.listar(frameId) as? ResultadoDaChamada.Sucesso)?.dado
                val canonica = lerAImagemCanonicaDoFrame(frameId)
                _estado.update { atual ->
                    atual.copy(
                        canonicasDosFrames = atual.canonicasDosFrames.com(frameId, canonica),
                        prompts = if (lista != null) atual.prompts + (frameId to PromptsDoFrame.Pronto(comAsImagensLidas(lista))) else atual.prompts,
                        mensagensDePrompt = atual.mensagensDePrompt - frameId,
                        versaoDosFrames = atual.versaoDosFrames + 1,
                    )
                }
            }
            is ResultadoDaChamada.Falha ->
                _estado.update { it.copy(mensagensDePrompt = it.mensagensDePrompt + (frameId to MensagemDoElemento(resultado.motivo, ehErro = true))) }
        }
    }

    private suspend fun comAsImagensLidas(lista: List<PromptDeFrame>): List<PromptDeFrame> = comAsImagens(lista).reversed()

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

    /** O botão **Importar imagem** foi tocado: guarda para onde a imagem vai antes de abrir o seletor (J2). */
    fun escolherImagemPara(frameId: Int, promptId: Int) {
        _estado.update { it.copy(alvoDaImportacao = AlvoDaImportacao(frameId, promptId)) }
    }

    /**
     * O seletor devolveu (ou não) um arquivo. [arquivo] `null` com [cancelou] `true` é só desistir (nada a dizer); `null` sem
     * cancelar é um arquivo que o app não conseguiu descrever.
     */
    fun imagemEscolhida(arquivo: ArquivoEscolhido?, cancelou: Boolean) {
        val alvo = _estado.value.alvoDaImportacao ?: return
        _estado.update { it.copy(alvoDaImportacao = null) }
        if (cancelou) return
        importarImagem(alvo.frameId, alvo.promptId, arquivo)
    }

    /**
     * Importar a imagem escolhida para um prompt (J1 a J6). [arquivo] é `null` quando o app não conseguiu descrever o
     * arquivo. Confere extensão e tamanho **antes** de enviar (J2); um envio por prompt, sem repetição automática (J3).
     */
    fun importarImagem(frameId: Int, promptId: Int, arquivo: ArquivoEscolhido?) {
        // PI1: sem prompt, a imagem vai para o frame (o servidor escolhe ou cria o prompt); o andamento e os recados usam a chave do frame.
        val paraOFrame = promptId == SEM_PROMPT
        val chave = if (paraOFrame) chaveDeImportacaoDoFrame(frameId) else promptId
        if (chave in _estado.value.importandoImagem || (!paraOFrame && promptId in _estado.value.gerandoImagem)) return
        val recusa = if (arquivo == null) "Não consegui abrir o arquivo escolhido." else motivoParaNaoImportar(arquivo)
        if (arquivo == null || recusa != null) {
            avisarSobreImagem(chave, recusa!!, ehErro = true)
            return
        }
        _estado.update {
            it.copy(importandoImagem = it.importandoImagem + (chave to null), mensagensDeImportacao = it.mensagensDeImportacao - chave)
        }
        viewModelScope.launch {
            val progresso: (Long, Long?) -> Unit = { enviados, total ->
                val fracao = if (total != null && total > 0) (enviados.toFloat() / total).coerceIn(0f, 1f) else null
                _estado.update { atual ->
                    if (chave in atual.importandoImagem) atual.copy(importandoImagem = atual.importandoImagem + (chave to fracao)) else atual
                }
            }
            val resultado = if (paraOFrame) prompts.importarImagemParaOFrame(frameId, arquivo, progresso) else prompts.importarImagem(promptId, arquivo, progresso)
            _estado.update { atual ->
                val semEnvio = atual.importandoImagem - chave
                when (resultado) {
                    is ResultadoDaChamada.Sucesso -> atual.copy(
                        importandoImagem = semEnvio,
                        // Para o frame, o prompt pode ser novo (o "só da imagem"): a lista é relida logo abaixo.
                        prompts = if (paraOFrame) atual.prompts else atual.prompts + (frameId to comAImagemNova(atual.prompts[frameId], promptId, resultado.dado)),
                        mensagensDeImportacao = atual.mensagensDeImportacao + (chave to MensagemDoElemento("Imagem importada.", ehErro = false)),
                        // J6: a tela relê os artefatos e o ícone no texto passa a ILUSTRADO.
                        versaoDosFrames = atual.versaoDosFrames + 1,
                    )
                    is ResultadoDaChamada.Falha -> atual.copy(
                        importandoImagem = semEnvio,
                        mensagensDeImportacao = atual.mensagensDeImportacao + (chave to MensagemDoElemento(resultado.motivo, ehErro = true)),
                    )
                }
            }
            if (paraOFrame && resultado is ResultadoDaChamada.Sucesso) relerPromptsSemPiscar(frameId)
        }
    }

    /**
     * **Importar imagem** num retrato **sem frame** (PI4): cria o frame do retrato (não gasta IA; é o passo que o "Gerar retrato"
     * também faz) e, com ele pronto, chama [aoCriar] com o id para a tela abrir o seletor. Se o retrato já foi criado, só segue.
     */
    fun criarRetratoParaImportar(elemento: ElementoSugerido, aoCriar: (frameId: Int) -> Unit) {
        _estado.value.retratosCriados[elemento.id]?.let { aoCriar(it); return }
        val estadoId = reservarRetrato(elemento) ?: return
        viewModelScope.launch { concluirCriacaoDoRetrato(elemento, estadoId)?.let(aoCriar) }
    }

    private fun avisarSobreImagem(promptId: Int, texto: String, ehErro: Boolean) {
        _estado.update { it.copy(mensagensDeImportacao = it.mensagensDeImportacao + (promptId to MensagemDoElemento(texto, ehErro))) }
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

    /** Relê a lista de prompts do frame **sem** voltar ao "Lendo…" (K6): a tela não pisca. Falha da leitura: mantém a lista. */
    private suspend fun relerPromptsSemPiscar(frameId: Int) {
        val resultado = prompts.listar(frameId)
        if (resultado is ResultadoDaChamada.Sucesso) {
            val lista = comAsImagens(resultado.dado).reversed()
            _estado.update { it.copy(prompts = it.prompts + (frameId to PromptsDoFrame.Pronto(lista))) }
        }
    }

    /**
     * **Gerar imagem** de um prompt (K1 a K9): pede ao servidor, que envia o prompt, suaviza se o provedor recusar e tenta de
     * novo. Sem confirmação (a imagem custa cerca de US$ 0,01). Um pedido por prompt, sem repetição automática (K2).
     * [textoEditado] vem do diálogo da recusa (K4) ou da edição (R2): o servidor o envia direto, sem suavizar.
     */
    fun gerarImagem(frameId: Int, promptId: Int, textoEditado: String? = null, modelo: String? = null, textoPt: String? = null) {
        if (!reservarGeracaoDeImagem(promptId)) return
        // Z9: escolher o modelo no diálogo da recusa o torna o modelo ativo, para as próximas gerações também.
        if (!modelo.isNullOrBlank()) _estado.update { it.copy(modeloEscolhido = modelo) }
        val modeloDoPedido = modelo?.takeIf { it.isNotBlank() } ?: _estado.value.modeloEscolhido
        viewModelScope.launch { concluirGeracaoDeImagem(frameId, promptId, textoEditado, modeloDoPedido, textoPt) }
    }

    /** "Ver em português" (PT2): traz a tradução do prompt (guardada, ou feita agora por uma chamada barata) e entrega a [aoTerminar]. */
    fun verEmPortugues(promptId: Int, aoTerminar: (ResultadoDaChamada<com.allan.imagineer.rede.Traducao>) -> Unit) {
        viewModelScope.launch { aoTerminar(prompts.traduzirParaPortugues(promptId)) }
    }

    /** "Traduzir para o inglês" (PT3): o português escrito vira o inglês que será enviado (só uma prévia; nada é gravado). */
    fun traduzirParaIngles(promptId: Int, texto: String, aoTerminar: (ResultadoDaChamada<com.allan.imagineer.rede.Traducao>) -> Unit) {
        viewModelScope.launch { aoTerminar(prompts.traduzirParaIngles(promptId, texto)) }
    }

    /** Como a cena (ou o retrato) se chama nos avisos: o rótulo guardado, o título da cena sugerida ou, no fim, "cena". */
    private fun rotuloDoFrameNoAviso(frameId: Int): String {
        val cena = (_estado.value.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes?.cenas?.firstOrNull { it.frame_id == frameId }
        return _estado.value.rotulosDeFrame[frameId] ?: cena?.titulo ?: "cena"
    }

    // ------------------------------------------------------------------ //
    // O seletor de elementos e imagens (EV1 a EV10)
    // ------------------------------------------------------------------ //

    /** Abre o seletor de uma **cena** (EV1): lê os elementos e as imagens (nunca gasta IA). */
    fun abrirSeletorDaCena(frameId: Int) {
        abrirSeletor(frameId, ehCena = true)
    }

    /**
     * Abre o seletor do **retrato** de um elemento (EV1, EV9). Só para quem aceita vínculos (personagem é individual, V2). Se o retrato
     * ainda **não tem frame**, o frame é criado antes (não gasta IA), para haver o que consultar.
     */
    fun abrirSeletorDoRetrato(elemento: ElementoSugerido, frameId: Int?) {
        if (!aceitaVinculos(elemento)) return
        if (frameId != null) {
            abrirSeletor(frameId, ehCena = false)
            return
        }
        val estadoId = reservarRetrato(elemento) ?: return
        viewModelScope.launch { concluirCriacaoDoRetrato(elemento, estadoId)?.let { abrirSeletor(it, ehCena = false) } }
    }

    private fun abrirSeletor(frameId: Int, ehCena: Boolean) {
        _estado.update { it.copy(escolhaDeElementos = EscolhaDeElementos(frameId, ehCena, CandidatosDoSeletor.Carregando, SelecaoNoSeletor())) }
        viewModelScope.launch {
            when (val resultado = prompts.elementosParaVincular(frameId)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { agora ->
                    val atual = agora.escolhaDeElementos?.takeIf { it.frameId == frameId } ?: return@update agora
                    atual.copy(
                        candidatos = CandidatosDoSeletor.Prontos(resultado.dado),
                        selecao = selecaoInicial(resultado.dado, agora.referenciasEscolhidas[frameId].orEmpty()),
                    ).let { agora.copy(escolhaDeElementos = it) }
                }
                is ResultadoDaChamada.Falha -> _estado.update { agora ->
                    val atual = agora.escolhaDeElementos?.takeIf { it.frameId == frameId } ?: return@update agora
                    agora.copy(escolhaDeElementos = atual.copy(candidatos = CandidatosDoSeletor.Erro(resultado.motivo)))
                }
            }
        }
    }

    private fun mexerNaSelecao(mudar: (ElementosParaVincular, SelecaoNoSeletor, Boolean) -> SelecaoNoSeletor) {
        _estado.update { agora ->
            val atual = agora.escolhaDeElementos ?: return@update agora
            val dados = (atual.candidatos as? CandidatosDoSeletor.Prontos)?.dados ?: return@update agora
            agora.copy(escolhaDeElementos = atual.copy(selecao = mudar(dados, atual.selecao, atual.ehCena), erro = null))
        }
    }

    /** Tocar numa imagem do carrossel (EV3, EV4): marca a imagem e o elemento dela, ou desmarca a imagem. */
    fun alternarImagemDoSeletor(imagemId: Int) = mexerNaSelecao { dados, selecao, ehCena -> alternarImagemNoSeletor(dados, selecao, imagemId, ehCena) }

    /** A caixa do nome (EV4): marcar coloca o elemento; desmarcar tira o elemento e as imagens dele. */
    fun alternarElementoDoSeletor(estadoId: Int) = mexerNaSelecao { dados, selecao, ehCena -> alternarElementoNoSeletor(dados, selecao, estadoId, ehCena) }

    /** "Limpar": sem imagens e sem os elementos acrescentados (EV4). */
    fun limparSeletor() = mexerNaSelecao { dados, _, _ -> limparSeletor(dados) }

    fun fecharSeletor() {
        _estado.update { if (it.escolhaDeElementos?.salvando == true) it else it.copy(escolhaDeElementos = null) }
    }

    /**
     * "Usar estes" (EV7): se os **elementos** mudaram, grava no servidor (o retrato: `PUT .../vinculos`; a cena: `PUT .../estados`
     * com o conjunto inteiro); as **imagens** ficam guardadas aqui, **por frame**, e só vão ao gerar (W3). A recusa do servidor
     * (a regra de personagem individual, por exemplo) aparece **no próprio seletor**, que fica aberto, e nada muda.
     */
    fun usarSeletor() {
        val escolha = _estado.value.escolhaDeElementos ?: return
        val dados = (escolha.candidatos as? CandidatosDoSeletor.Prontos)?.dados ?: return
        if (escolha.salvando) return
        val frameId = escolha.frameId
        val imagens = escolha.selecao.imagens.toList()
        val escolhidos = todosOsElementos(dados).filter { it.estado_id in escolha.selecao.elementos }

        fun aplicarConclusao(nomes: List<String>?, mudou: Boolean) = _estado.update { agora ->
            val referencias = if (imagens.isEmpty()) agora.referenciasEscolhidas - frameId else agora.referenciasEscolhidas + (frameId to imagens)
            val tinha = (agora.prompts[frameId] as? PromptsDoFrame.Pronto)?.lista?.size ?: 0
            agora.copy(
                escolhaDeElementos = null,
                referenciasEscolhidas = referencias,
                vinculadosPorFrame = if (nomes != null) agora.vinculadosPorFrame + (frameId to nomes) else agora.vinculadosPorFrame,
                mudancasPendentesDePrompt = if (mudou) agora.mudancasPendentesDePrompt + (frameId to tinha) else agora.mudancasPendentesDePrompt,
            )
        }

        fun concluir(nomes: List<String>?, mudou: Boolean) {
            // RS1: a escolha de imagens também vai para o servidor (sem travar o seletor: se falhar, vale só nesta sessão).
            if (imagens != (_estado.value.referenciasEscolhidas[frameId] ?: emptyList<Int>())) {
                viewModelScope.launch { sugestoes.guardarReferencias(frameId, imagens) }
            }
            aplicarConclusao(nomes, mudou)
        }

        if (!elementosMudaram(dados, escolha.selecao)) {
            concluir(null, mudou = false)
            return
        }
        _estado.update { it.copy(escolhaDeElementos = escolha.copy(salvando = true, erro = null)) }
        viewModelScope.launch {
            val estadosIds = escolhidos.map { it.estado_id }
            val resultado = if (escolha.ehCena) sugestoes.definirEstados(frameId, estadosIds) else sugestoes.definirVinculos(frameId, estadosIds).let {
                when (it) {
                    is ResultadoDaChamada.Sucesso -> ResultadoDaChamada.Sucesso(Unit)
                    is ResultadoDaChamada.Falha -> it
                }
            }
            when (resultado) {
                is ResultadoDaChamada.Sucesso -> concluir(if (escolha.ehCena) null else escolhidos.map { it.nome }, mudou = true)
                is ResultadoDaChamada.Falha -> _estado.update { agora ->
                    agora.copy(escolhaDeElementos = agora.escolhaDeElementos?.copy(salvando = false, erro = resultado.motivo))
                }
            }
        }
    }

    /** Lê **uma vez** os nomes dos vinculados de um retrato que já existe no servidor (EV8), para a linha dizer a verdade depois de reabrir o app. */
    fun carregarVinculados(frameId: Int) {
        if (frameId in _estado.value.vinculadosPorFrame || frameId in vinculadosJaLidos) return
        vinculadosJaLidos += frameId
        viewModelScope.launch {
            val resultado = sugestoes.vinculosDoFrame(frameId)
            if (resultado is ResultadoDaChamada.Sucesso) {
                _estado.update { if (frameId in it.vinculadosPorFrame) it else it.copy(vinculadosPorFrame = it.vinculadosPorFrame + (frameId to resultado.dado.map { v -> v.nome })) }
            }
        }
    }

    private val vinculadosJaLidos = mutableSetOf<Int>()
    private val referenciasJaLidas = mutableSetOf<Int>()

    /**
     * Lê **uma vez** as imagens de referência que o servidor guardou para o frame (RS1) e as põe na escolha, a não ser que o usuário já
     * tenha mexido nela nesta sessão. É o que faz a escolha sobreviver ao fechar o app e valer em outro aparelho.
     */
    fun carregarReferenciasGuardadas(frameId: Int) {
        if (frameId in referenciasJaLidas) return
        referenciasJaLidas += frameId
        viewModelScope.launch {
            val resultado = sugestoes.referenciasDoFrame(frameId)
            if (resultado is ResultadoDaChamada.Sucesso && resultado.dado.isNotEmpty()) {
                _estado.update { if (frameId in it.referenciasEscolhidas) it else it.copy(referenciasEscolhidas = it.referenciasEscolhidas + (frameId to resultado.dado)) }
            }
        }
    }

    /** Lê a lista de modelos de imagem **uma vez** (Z6); uma falha de leitura só deixa a escolha de modelo escondida. */
    fun carregarModelosDeImagem() {
        if (_estado.value.modelosDeImagem != null || carregandoModelos) return
        carregandoModelos = true
        viewModelScope.launch {
            val resultado = prompts.modelosDeImagem()
            carregandoModelos = false
            if (resultado is ResultadoDaChamada.Sucesso) _estado.update { it.copy(modelosDeImagem = resultado.dado) }
        }
    }

    private var carregandoModelos = false

    fun abrirEscolhaDeModelo() {
        if (_estado.value.modelosDeImagem != null) _estado.update { it.copy(escolhendoModelo = true) }
    }

    fun fecharEscolhaDeModelo() {
        _estado.update { it.copy(escolhendoModelo = false) }
    }

    /** A pessoa escolheu o modelo das próximas gerações (Z6). Não muda o padrão do servidor. */
    fun escolherModelo(modelo: String) {
        _estado.update { it.copy(modeloEscolhido = modelo.takeIf { escolhido -> escolhido.isNotBlank() }, escolhendoModelo = false) }
    }

    /** Marca o prompt como "gerando" (K2); `false` se já há um pedido ou uma importação dele em andamento. */
    private fun reservarGeracaoDeImagem(promptId: Int): Boolean {
        val atual = _estado.value
        if (promptId in atual.gerandoImagem || promptId in atual.importandoImagem) return false
        _estado.update {
            it.copy(
                gerandoImagem = it.gerandoImagem + promptId,
                mensagensDeImagem = it.mensagensDeImagem - promptId,
                recusaDeImagem = null,
                edicaoDePrompt = null,
            )
        }
        return true
    }

    /** Faz o pedido e aplica o desfecho (K3, K4, K6, K7). Quem chama já reservou o prompt. */
    private suspend fun concluirGeracaoDeImagem(frameId: Int, promptId: Int, textoEditado: String?, modelo: String?, textoPt: String? = null) {
        // F19: escolher um modelo da lista **sem filtro** é pedir a geração sem o filtro; com qualquer outro, o pedido é o de sempre.
        val modelos = _estado.value.modelosDeImagem
        // W10: as referências do frame só vão se o modelo em uso as aceita (senão ficam guardadas, desativadas).
        val referencias = if (modeloAceitaReferencia(modelo ?: modeloEmUso(_estado.value.modeloEscolhido, modelos), modelos)) {
            _estado.value.referenciasEscolhidas[frameId].orEmpty()
        } else {
            emptyList()
        }
        // O pedido roda no serviço do app (como o do prompt): sair do capítulo não o cancela, e ao terminar sai o aviso global.
        val resultado = servico.iniciarImagem(
            promptId = promptId,
            frameId = frameId,
            capituloId = capituloId,
            livroId = livroId,
            rotuloDoCapitulo = rotuloDoCapitulo,
            rotuloDaCena = rotuloDoFrameNoAviso(frameId),
            textoEditado = textoEditado,
            modelo = modelo,
            // F19: escolher um modelo da lista sem filtro é pedir a geração sem o filtro.
            semFiltro = modeloEstaSemFiltro(modelo, modelos),
            referencias = referencias,
            textoPt = textoPt,
        ).await()
        _estado.update { agora ->
            val semPedido = agora.gerandoImagem - promptId
            when (resultado) {
                is ResultadoDaChamada.Sucesso -> {
                    val geracao = resultado.dado
                    if (geracao.gerada) {
                        agora.copy(
                            gerandoImagem = semPedido,
                            mensagensDeImagem = agora.mensagensDeImagem + (promptId to MensagemDoElemento(avisoDaGeracao(geracao.suavizado), ehErro = false)),
                            // J6: a tela relê os artefatos e o ícone no texto passa a ILUSTRADO.
                            versaoDosFrames = agora.versaoDosFrames + 1,
                        )
                    } else {
                        // K4: recusou de novo; o prompt devolvido vai para a edição.
                        agora.copy(
                            gerandoImagem = semPedido,
                            recusaDeImagem = RecusaDeImagem(
                                frameId = frameId,
                                promptId = geracao.prompt.id,
                                texto = geracao.prompt.texto,
                                motivo = geracao.prompt.motivo_da_recusa ?: MOTIVO_PADRAO_DA_RECUSA,
                                modelo = geracao.prompt.modelo_imagem,
                            ),
                        )
                    }
                }
                is ResultadoDaChamada.Falha -> agora.copy(
                    gerandoImagem = semPedido,
                    mensagensDeImagem = agora.mensagensDeImagem + (promptId to MensagemDoElemento(resultado.motivo, ehErro = true)),
                )
            }
        }
        // K6: o original mudou de situação e pode haver um prompt novo (suavizado ou editado).
        if (resultado is ResultadoDaChamada.Sucesso) relerPromptsSemPiscar(frameId)
    }

    /** **Excluir** na tela cheia da imagem (U3): pede confirmação, porque apaga para sempre. */
    fun pedirExcluirImagem(frameId: Int, promptId: Int, imagemId: Int, origem: String) {
        _estado.update { it.copy(excluindoImagem = ImagemParaExcluir(frameId, promptId, imagemId, origem)) }
    }

    fun cancelarExclusaoDeImagem() {
        _estado.update { it.copy(excluindoImagem = null) }
    }

    /**
     * Confirmou: apaga no servidor. Se deu certo, a imagem **some da lista** (e a tela cheia, que só abre para imagens da
     * lista, fecha), os artefatos são relidos (o ícone pode deixar de ser `ILUSTRADO`) e um recado diz o que houve. Se
     * falhou, a mensagem do servidor aparece e nada some.
     */
    fun confirmarExclusaoDeImagem() {
        val alvo = _estado.value.excluindoImagem ?: return
        _estado.update { it.copy(excluindoImagem = null) }
        viewModelScope.launch {
            val resultado = prompts.removerImagem(alvo.imagemId)
            _estado.update { atual ->
                val recado = when (resultado) {
                    is ResultadoDaChamada.Sucesso -> MensagemDoElemento("Imagem excluída.", ehErro = false)
                    is ResultadoDaChamada.Falha -> MensagemDoElemento(resultado.motivo, ehErro = true)
                }
                val comRecado = if (alvo.origem == "GERADA") {
                    atual.copy(mensagensDeImagem = atual.mensagensDeImagem + (alvo.promptId to recado))
                } else {
                    atual.copy(mensagensDeImportacao = atual.mensagensDeImportacao + (alvo.promptId to recado))
                }
                if (resultado is ResultadoDaChamada.Sucesso) {
                    comRecado.copy(
                        prompts = comRecado.prompts + (alvo.frameId to semAImagem(comRecado.prompts[alvo.frameId], alvo.imagemId)),
                        versaoDosFrames = comRecado.versaoDosFrames + 1,
                    )
                } else {
                    comRecado
                }
            }
        }
    }

    /** A lista de prompts do frame sem a imagem apagada, com a contagem de cada prompt em dia. */
    private fun semAImagem(atual: PromptsDoFrame?, imagemId: Int): PromptsDoFrame {
        val lista = (atual as? PromptsDoFrame.Pronto)?.lista ?: return atual ?: PromptsDoFrame.Lendo
        return PromptsDoFrame.Pronto(
            lista.map { prompt ->
                if (prompt.imagens.none { it.id == imagemId }) {
                    prompt
                } else {
                    prompt.copy(imagens = prompt.imagens.filter { it.id != imagemId }, total_de_imagens = (prompt.total_de_imagens - 1).coerceAtLeast(0))
                }
            },
        )
    }

    /** **Editar** num prompt (R1): abre o diálogo com o texto dele. */
    fun editarPrompt(frameId: Int, promptId: Int, texto: String) {
        _estado.update { it.copy(edicaoDePrompt = EdicaoDePrompt(frameId, promptId, texto)) }
    }

    /** O ícone **Traduzir** do cartão do prompt (VD14): o mesmo diálogo de editar, já na aba Português. */
    fun traduzirPrompt(frameId: Int, promptId: Int, texto: String) {
        _estado.update { it.copy(edicaoDePrompt = EdicaoDePrompt(frameId, promptId, texto, emPortugues = true)) }
    }

    /** "Cancelar" no diálogo de edição (R2): nada muda. */
    fun fecharEdicaoDePrompt() {
        _estado.update { it.copy(edicaoDePrompt = null) }
    }

    /** "Fechar" no diálogo da recusa (K4): o prompt continua na lista, marcado. */
    fun fecharRecusaDeImagem() {
        _estado.update { it.copy(recusaDeImagem = null) }
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
        viewModelScope.launch { aplicarResultadoDoPrompt(frameId, trabalho.await()) }
    }

    /** Aplica o resultado de uma geração de prompt à lista do frame (G5, G7). */
    private fun aplicarResultadoDoPrompt(frameId: Int, resultado: ResultadoDaChamada<PromptDeFrame>) {
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
                    lista = if (conhecidos != null) ListaParaVincular.Pronta(conhecidos, elementosPorCapitulo.orEmpty()) else ListaParaVincular.Carregando,
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
                    // VM2: por capítulo, com as miniaturas; se não deu para ler, a lista cai em "Sem capítulo" e continua útil.
                    val porCapitulo = (elementos.elementosPorCapitulo(livro) as? ResultadoDaChamada.Sucesso)?.dado
                    elementosPorCapitulo = porCapitulo
                    atualizarLista(ListaParaVincular.Pronta(resultado.dado, porCapitulo.orEmpty()))
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

    // --- Imagem de um trecho selecionado (TR1 a TR7) ---------------------- //

    /**
     * A pessoa escolheu **"Gerar imagem deste trecho"** na barra de seleção: abre o diálogo com o [trecho]. Já marca os elementos
     * confirmados cujos nomes aparecem nele (TR2). Se o painel nunca foi aberto, lê as sugestões (só leitura) para ter os elementos.
     */
    fun abrirTrecho(trecho: String, posicao: Int?) {
        val limpo = trecho.trim()
        if (limpo.isEmpty()) return
        val opcoes = opcoesDeElementosDoTrecho((_estado.value.conteudo as? ConteudoDoPainel.Pronto)?.sugestoes?.elementos.orEmpty())
        _estado.update { it.copy(trechoParaImagem = TrechoParaImagem(limpo, posicao, estadosEscolhidos = estadosCitadosNoTrecho(opcoes, limpo))) }
        if (_estado.value.conteudo is ConteudoDoPainel.NaoCarregado) aoAbrirPainel()
        carregarCandidatosDoTrecho(limpo)
    }

    /** Lê o seletor de elementos da cena nova (LV8): o mesmo de qualquer cena. Os citados no trecho já vêm marcados. */
    private fun carregarCandidatosDoTrecho(trecho: String) {
        viewModelScope.launch {
            val lido = prompts.elementosParaCena(capituloId)
            _estado.update { atual ->
                val aberto = atual.trechoParaImagem?.takeIf { it.trecho == trecho } ?: return@update atual
                when (lido) {
                    is ResultadoDaChamada.Sucesso -> {
                        val opcoes = opcoesDoSeletorDoTrecho(lido.dado)
                        val validos = opcoes.map { it.estadoId }.toSet()
                        atual.copy(
                            trechoParaImagem = aberto.copy(
                                candidatos = CandidatosDoSeletor.Prontos(lido.dado),
                                // o que a pessoa já marcou fica; o resto vem dos citados no trecho
                                estadosEscolhidos = (aberto.estadosEscolhidos intersect validos) + estadosCitadosNoTrecho(opcoes, trecho),
                            ),
                        )
                    }
                    is ResultadoDaChamada.Falha -> atual.copy(trechoParaImagem = aberto.copy(candidatos = CandidatosDoSeletor.Erro(lido.motivo)))
                }
            }
        }
    }

    fun fecharTrecho() {
        if (_estado.value.trechoParaImagem?.criando == true) return
        _estado.update { it.copy(trechoParaImagem = null) }
    }

    fun alterarDescricaoDoTrecho(texto: String) {
        _estado.update { atual -> atual.trechoParaImagem?.let { atual.copy(trechoParaImagem = it.copy(descricao = texto.take(LIMITE_DA_DESCRICAO_DO_TRECHO))) } ?: atual }
    }

    fun alternarElementoDoTrecho(estadoId: Int) {
        _estado.update { atual ->
            atual.trechoParaImagem?.let {
                val novos = if (estadoId in it.estadosEscolhidos) it.estadosEscolhidos - estadoId else it.estadosEscolhidos + estadoId
                atual.copy(trechoParaImagem = it.copy(estadosEscolhidos = novos))
            } ?: atual
        }
    }

    /**
     * **"Criar a cena"** (TR3): cria a cena avulsa do trecho, **sem IA**, no parágrafo dele (TR4). Sucesso: fecha o diálogo, manda a tela
     * reler os artefatos e abre o modal da cena nova, onde ficam **Gerar imagem** e **Só o prompt** (aí sim gasta IA: a análise barata e
     * depois o modelo de prompt, TR5). Falha: a mensagem do servidor no diálogo, que continua aberto.
     */
    fun criarCenaDoTrecho() {
        val trecho = _estado.value.trechoParaImagem ?: return
        if (trecho.criando) return
        _estado.update { it.copy(trechoParaImagem = trecho.copy(criando = true, erro = null)) }
        viewModelScope.launch {
            val titulo = tituloDaCenaDoTrecho(trecho.descricao, trecho.trecho)
            val resultado = sugestoes.criarCenaDoTrecho(
                capituloId, titulo, descricaoDaCenaDoTrecho(trecho.descricao, trecho.trecho), trecho.posicao, trecho.estadosEscolhidos.toList().sorted(),
                trecho = trecho.trecho,
            )
            when (resultado) {
                is ResultadoDaChamada.Sucesso -> _estado.update {
                    it.copy(
                        trechoParaImagem = null,
                        versaoDosFrames = it.versaoDosFrames + 1,
                        modais = listOf(ModalAberto.DeFrame(resultado.dado.id, titulo)),
                        rotulosDeFrame = it.rotulosDeFrame + (resultado.dado.id to titulo),
                    )
                }
                is ResultadoDaChamada.Falha -> _estado.update { atual ->
                    atual.copy(trechoParaImagem = atual.trechoParaImagem?.copy(criando = false, erro = resultado.motivo))
                }
            }
        }
    }

    /** Tocou no ícone de um frame **sem sugestão** (a cena de um trecho): abre o modal dele. */
    fun abrirModalDeFrame(frameId: Int, rotulo: String) {
        _estado.update { it.copy(modais = listOf(ModalAberto.DeFrame(frameId, rotulo))) }
    }

    fun fecharModalDoFrame() {
        _estado.update { it.copy(modais = it.modais.filterNot { m -> m is ModalAberto.DeFrame }) }
    }

    // --- Posicionar à mão (PM1 a PM4) ------------------------------------- //

    /** Liga o modo "toque no parágrafo" para o artefato e fecha os modais, para o texto ficar à vista. Não gasta IA. */
    fun iniciarPosicionamento(ehCena: Boolean, sugestaoId: Int, rotulo: String) {
        _estado.update { it.copy(posicionando = PosicionandoArtefato(ehCena, sugestaoId, rotulo), modais = emptyList()) }
    }

    /** O mesmo modo, para um frame **sem sugestão** (a cena de um trecho): a posição se grava no próprio frame (PM3). */
    fun iniciarPosicionamentoDeFrame(ehCena: Boolean, frameId: Int, rotulo: String) {
        _estado.update { it.copy(posicionando = PosicionandoArtefato(ehCena, null, rotulo, frameId = frameId), modais = emptyList()) }
    }

    fun cancelarPosicionamento() {
        _estado.update { it.copy(posicionando = null) }
    }

    /**
     * **"Tirar a posição"** (PM3): o artefato volta a ser posicionado sozinho (pelo nome ou pela citação) ou, se não achar, à faixa
     * "Sem posição no texto". Vale para sugestão ([sugestaoId]) ou para um frame sem sugestão ([frameId]). Falha: o recado do item.
     */
    fun tirarPosicao(ehCena: Boolean, sugestaoId: Int?, frameId: Int?) {
        viewModelScope.launch {
            val resultado = when {
                frameId != null -> sugestoes.posicionarFrame(frameId, null)
                sugestaoId != null -> sugestoes.posicionarArtefato(ehCena, sugestaoId, null)
                else -> return@launch
            }
            when (resultado) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(versaoDosFrames = it.versaoDosFrames + 1) }
                is ResultadoDaChamada.Falha -> _estado.update {
                    val recado = MensagemDoElemento(resultado.motivo, ehErro = true)
                    when {
                        frameId != null -> it.copy(mensagensDePrompt = it.mensagensDePrompt + (frameId to recado))
                        ehCena -> it.copy(mensagensDeCena = it.mensagensDeCena + (sugestaoId!! to recado))
                        else -> it.copy(mensagens = it.mensagens + (sugestaoId!! to recado))
                    }
                }
            }
        }
    }

    // --- Editar o título e a descrição de uma cena (LV6) -------------------- //

    /** Abre a edição de uma cena **sugerida** (a lista já traz o título e a descrição). */
    fun abrirEdicaoDaCena(sugestaoId: Int, titulo: String, descricao: String?) {
        _estado.update { it.copy(editandoCena = EdicaoDaCena(sugestaoId = sugestaoId, titulo = titulo, descricao = descricao.orEmpty())) }
    }

    /** Abre a edição da cena de um trecho (só frame): lê o título e a descrição dele antes. */
    fun abrirEdicaoDeFrame(frameId: Int, rotulo: String) {
        _estado.update { it.copy(editandoCena = EdicaoDaCena(frameId = frameId, titulo = rotulo, carregando = true)) }
        viewModelScope.launch {
            val lido = sugestoes.textoDoFrame(frameId)
            _estado.update { atual ->
                val edicao = atual.editandoCena
                if (edicao == null || edicao.frameId != frameId) {
                    atual
                } else when (lido) {
                    is ResultadoDaChamada.Sucesso -> atual.copy(
                        editandoCena = edicao.copy(titulo = lido.dado.first.ifBlank { rotulo }, descricao = lido.dado.second, carregando = false),
                    )
                    is ResultadoDaChamada.Falha -> atual.copy(editandoCena = edicao.copy(carregando = false, erro = lido.motivo))
                }
            }
        }
    }

    fun fecharEdicaoDaCena() {
        if (_estado.value.editandoCena?.salvando == true) return
        _estado.update { it.copy(editandoCena = null) }
    }

    /** Grava o novo título e a nova descrição; o título não pode ficar vazio. */
    fun salvarEdicaoDaCena(titulo: String, descricao: String) {
        val alvo = _estado.value.editandoCena ?: return
        if (alvo.salvando || alvo.carregando) return
        val novoTitulo = titulo.trim()
        if (novoTitulo.isEmpty()) {
            _estado.update { it.copy(editandoCena = alvo.copy(erro = "O título não pode ficar vazio.")) }
            return
        }
        _estado.update { it.copy(editandoCena = alvo.copy(salvando = true, erro = null)) }
        viewModelScope.launch {
            val resultado = if (alvo.sugestaoId != null) {
                sugestoes.editarCena(alvo.sugestaoId, novoTitulo, descricao)
            } else {
                sugestoes.editarFrame(alvo.frameId ?: return@launch, novoTitulo, descricao)
            }
            when (resultado) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.update { atual ->
                        atual.copy(
                            editandoCena = null,
                            // o modal do frame mostra o título novo; o ícone no texto relê o rótulo
                            modais = atual.modais.map { m -> if (m is ModalAberto.DeFrame && m.frameId == alvo.frameId) m.copy(rotulo = novoTitulo) else m },
                            versaoDosFrames = atual.versaoDosFrames + 1,
                        )
                    }
                    if (alvo.sugestaoId != null) reler()
                }
                is ResultadoDaChamada.Falha -> _estado.update { atual ->
                    atual.copy(editandoCena = atual.editandoCena?.copy(salvando = false, erro = resultado.motivo))
                }
            }
        }
    }

    // --- Apagar a cena de um trecho ---------------------------------------- //

    fun pedirApagarFrame(frameId: Int, rotulo: String, deSugestao: Boolean = false) {
        _estado.update { it.copy(apagandoFrame = ApagandoFrame(frameId, rotulo, deSugestao = deSugestao)) }
    }

    fun cancelarApagarFrame() {
        if (_estado.value.apagandoFrame?.apagando == true) return
        _estado.update { it.copy(apagandoFrame = null) }
    }

    /** Apaga o frame (e os prompts e imagens dele, **sem volta**), fecha o modal dele e manda a tela reler os artefatos. */
    fun confirmarApagarFrame() {
        val alvo = _estado.value.apagandoFrame ?: return
        if (alvo.apagando) return
        _estado.update { it.copy(apagandoFrame = alvo.copy(apagando = true, erro = null)) }
        viewModelScope.launch {
            when (val resultado = sugestoes.apagarFrame(alvo.frameId)) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.update {
                        it.copy(
                            apagandoFrame = null,
                            modais = it.modais.filterNot { m -> m is ModalAberto.DeFrame && m.frameId == alvo.frameId },
                            prompts = it.prompts - alvo.frameId,
                            versaoDosFrames = it.versaoDosFrames + 1,
                        )
                    }
                    // A cena de uma sugestão perde o frame (o servidor a devolve a pendente): relê a lista para o modal refletir.
                    if (alvo.deSugestao) reler()
                }
                is ResultadoDaChamada.Falha -> _estado.update { atual ->
                    atual.copy(apagandoFrame = atual.apagandoFrame?.copy(apagando = false, erro = resultado.motivo))
                }
            }
        }
    }

    /** A pessoa tocou num parágrafo, que começa em [posicao] (UTF-16): grava no servidor e manda a tela reler os artefatos. */
    fun escolherParagrafo(posicao: Int) {
        val alvo = _estado.value.posicionando ?: return
        viewModelScope.launch {
            val resultado = if (alvo.frameId != null) {
                sugestoes.posicionarFrame(alvo.frameId, posicao)
            } else {
                sugestoes.posicionarArtefato(alvo.ehCena, alvo.sugestaoId ?: return@launch, posicao)
            }
            when (resultado) {
                is ResultadoDaChamada.Sucesso ->
                    _estado.update { it.copy(posicionando = null, versaoDosFrames = it.versaoDosFrames + 1) }
                is ResultadoDaChamada.Falha ->
                    _estado.update { atual -> atual.copy(posicionando = atual.posicionando?.copy(erro = resultado.motivo)) }
            }
        }
    }

    // --- Usar uma imagem que já existe (VM3, VM4) ------------------------- //

    /** Abre o seletor das imagens do [elemento] por capítulo; [frameId] nulo = o retrato ainda não existe. Não gasta IA. */
    fun abrirImagemExistente(elemento: ElementoSugerido, frameId: Int?) {
        val conhecidas = elementosPorCapitulo
        _estado.update {
            it.copy(
                usandoImagemExistente = UsoDeImagemExistente(
                    elemento, frameId, if (conhecidas != null) CargaPorCapitulo.Pronta(conhecidas) else CargaPorCapitulo.Carregando,
                ),
            )
        }
        if (conhecidas != null) return
        val livro = livroId
        if (livro == null) {
            atualizarUsoDeImagem { it.copy(carga = CargaPorCapitulo.Erro(ERRO_LIVRO_NAO_CARREGADO)) }
            return
        }
        viewModelScope.launch {
            when (val resultado = elementos.elementosPorCapitulo(livro)) {
                is ResultadoDaChamada.Sucesso -> {
                    elementosPorCapitulo = resultado.dado
                    atualizarUsoDeImagem { it.copy(carga = CargaPorCapitulo.Pronta(resultado.dado)) }
                }
                is ResultadoDaChamada.Falha -> atualizarUsoDeImagem { it.copy(carga = CargaPorCapitulo.Erro(resultado.motivo)) }
            }
        }
    }

    // --- O prompt de vídeo (item 4.8) ---------------------------------------- //

    /** Lê **uma vez** os prompts de vídeo do frame. Não gasta IA. */
    fun carregarVideos(frameId: Int) {
        if (_estado.value.videos[frameId] != null) return
        _estado.update { it.copy(videos = it.videos + (frameId to VideosDoFrame.Lendo)) }
        viewModelScope.launch {
            val lido = when (val r = prompts.listarVideos(frameId)) {
                is ResultadoDaChamada.Sucesso -> VideosDoFrame.Pronto(r.dado)
                is ResultadoDaChamada.Falha -> VideosDoFrame.Erro(r.motivo)
            }
            _estado.update { it.copy(videos = it.videos + (frameId to lido)) }
        }
    }

    /** Abre o diálogo com a canônica (ou a imagem mais recente) já escolhida como primeiro quadro. */
    fun abrirDialogoDeVideo(frameId: Int, rotulo: String, imagens: List<ImagemDoPrompt>) {
        if (imagens.isEmpty()) return
        _estado.update { it.copy(dialogoDeVideo = DialogoDeVideo(frameId, rotulo, imagens, escolhida = imagemDePartidaPadrao(imagens))) }
    }

    fun escolherImagemDoVideo(imagemId: Int) {
        _estado.update { atual -> atual.dialogoDeVideo?.let { atual.copy(dialogoDeVideo = it.copy(escolhida = imagemId, erro = null)) } ?: atual }
    }

    fun mudarComentarioDoVideo(texto: String) {
        _estado.update { atual -> atual.dialogoDeVideo?.let { atual.copy(dialogoDeVideo = it.copy(comentario = texto)) } ?: atual }
    }

    fun fecharDialogoDeVideo() {
        if (_estado.value.dialogoDeVideo?.gerando == true) return
        _estado.update { it.copy(dialogoDeVideo = null) }
    }

    /**
     * **Gera** o prompt de vídeo (uma chamada de IA). Sucesso: fecha o diálogo e o prompt novo entra na seção Vídeo. Falha (a imagem não
     * é do frame, sem chave, IA fora): a mensagem do servidor no próprio diálogo, que continua aberto.
     */
    fun gerarVideo() {
        val dialogo = _estado.value.dialogoDeVideo ?: return
        if (dialogo.gerando || dialogo.escolhida == null) return
        _estado.update { it.copy(dialogoDeVideo = dialogo.copy(gerando = true, erro = null)) }
        viewModelScope.launch {
            when (val r = prompts.gerarVideo(dialogo.frameId, dialogo.escolhida, dialogo.comentario)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { atual ->
                    val antes = (atual.videos[dialogo.frameId] as? VideosDoFrame.Pronto)?.lista.orEmpty()
                    atual.copy(dialogoDeVideo = null, videos = atual.videos + (dialogo.frameId to VideosDoFrame.Pronto(antes + r.dado)))
                }
                is ResultadoDaChamada.Falha -> _estado.update { atual ->
                    atual.copy(dialogoDeVideo = atual.dialogoDeVideo?.copy(gerando = false, erro = r.motivo))
                }
            }
        }
    }

    // --- O vídeo importado (item 4.8, VD12 a VD18) ---------------------------- //

    /** Lê **uma vez** os vídeos importados do frame. */
    fun carregarVideosImportados(frameId: Int) {
        if (_estado.value.videosImportados[frameId] != null) return
        _estado.update { it.copy(videosImportados = it.videosImportados + (frameId to VideosImportadosDoFrame.Lendo)) }
        viewModelScope.launch {
            val lido = when (val r = prompts.listarVideosImportados(frameId)) {
                is ResultadoDaChamada.Sucesso -> VideosImportadosDoFrame.Pronto(r.dado)
                is ResultadoDaChamada.Falha -> VideosImportadosDoFrame.Erro(r.motivo)
            }
            _estado.update { it.copy(videosImportados = it.videosImportados + (frameId to lido)) }
        }
    }

    private fun avisarSobreVideo(frameId: Int, texto: String, ehErro: Boolean) {
        _estado.update { it.copy(mensagensDeVideo = it.mensagensDeVideo + (frameId to MensagemDoElemento(texto, ehErro))) }
    }

    private fun mudarVideosImportados(frameId: Int, mudar: (List<VideoImportado>) -> List<VideoImportado>) {
        _estado.update { atual ->
            val lista = (atual.videosImportados[frameId] as? VideosImportadosDoFrame.Pronto)?.lista ?: return@update atual
            atual.copy(videosImportados = atual.videosImportados + (frameId to VideosImportadosDoFrame.Pronto(mudar(lista))))
        }
    }

    /** O botão **Importar vídeo** foi tocado: guarda para onde o vídeo vai antes de abrir o seletor (o modal some enquanto ele está aberto). */
    fun escolherVideoPara(frameId: Int, promptId: Int?) {
        _estado.update { it.copy(alvoDoVideo = AlvoDaImportacaoDeVideo(frameId, promptId)) }
    }

    /** O seletor devolveu (ou não) um arquivo: [cancelou] é só desistir; senão segue para [importarVideo]. */
    fun videoEscolhido(arquivo: ArquivoEscolhido?, cancelou: Boolean) {
        val alvo = _estado.value.alvoDoVideo ?: return
        _estado.update { it.copy(alvoDoVideo = null) }
        if (cancelou) return
        importarVideo(alvo.frameId, alvo.promptId, arquivo)
    }

    /**
     * **Importar vídeo** (VD18): [arquivo] `null` é um arquivo que o app não conseguiu descrever. Confere extensão e tamanho **antes** de
     * enviar; um envio por frame, sem repetição automática. [promptId] é o prompt de vídeo de origem, se a pessoa disse.
     */
    fun importarVideo(frameId: Int, promptId: Int?, arquivo: ArquivoEscolhido?) {
        if (frameId in _estado.value.importandoVideo) return
        val recusa = if (arquivo == null) "Não consegui abrir o arquivo escolhido." else motivoParaNaoImportarVideo(arquivo)
        if (arquivo == null || recusa != null) {
            avisarSobreVideo(frameId, recusa!!, ehErro = true)
            return
        }
        _estado.update { it.copy(importandoVideo = it.importandoVideo + (frameId to null), mensagensDeVideo = it.mensagensDeVideo - frameId) }
        viewModelScope.launch {
            val progresso: (Long, Long?) -> Unit = { enviados, total ->
                val fracao = if (total != null && total > 0) (enviados.toFloat() / total).coerceIn(0f, 1f) else null
                _estado.update { atual -> if (frameId in atual.importandoVideo) atual.copy(importandoVideo = atual.importandoVideo + (frameId to fracao)) else atual }
            }
            val resultado = prompts.importarVideo(frameId, arquivo, promptId, progresso)
            _estado.update { it.copy(importandoVideo = it.importandoVideo - frameId) }
            when (resultado) {
                is ResultadoDaChamada.Sucesso -> {
                    mudarVideosImportados(frameId) { listOf(resultado.dado) + it.filter { v -> v.id != resultado.dado.id } }
                    avisarSobreVideo(frameId, "Vídeo importado.", ehErro = false)
                }
                is ResultadoDaChamada.Falha -> avisarSobreVideo(frameId, resultado.motivo, ehErro = true)
            }
        }
    }

    /** **Apagar** o vídeo e o arquivo (VD18, sem lixeira). Se era o do texto, o texto volta à imagem: a tela relê os artefatos. */
    fun apagarVideo(frameId: Int, videoId: Int) {
        viewModelScope.launch {
            when (val r = prompts.apagarVideo(videoId)) {
                is ResultadoDaChamada.Sucesso -> {
                    val eraDoTexto = (_estado.value.videosImportados[frameId] as? VideosImportadosDoFrame.Pronto)?.lista?.any { it.id == videoId && it.no_texto } == true
                    mudarVideosImportados(frameId) { lista -> lista.filter { it.id != videoId } }
                    if (eraDoTexto) _estado.update { it.copy(versaoDosFrames = it.versaoDosFrames + 1) }
                }
                is ResultadoDaChamada.Falha -> avisarSobreVideo(frameId, r.motivo, ehErro = true)
            }
        }
    }

    /** **Mostrar no texto** (VD17): o texto passa a mostrar o vídeo [videoId]; `null` volta à imagem. Um só vídeo fica ligado por vez. */
    fun definirVideoNoTexto(frameId: Int, videoId: Int?) {
        viewModelScope.launch {
            when (val r = prompts.definirVideoNoTexto(frameId, videoId)) {
                is ResultadoDaChamada.Sucesso -> {
                    mudarVideosImportados(frameId) { lista -> lista.map { it.copy(no_texto = it.id == videoId) } }
                    _estado.update { it.copy(versaoDosFrames = it.versaoDosFrames + 1) }  // o artefato do texto muda: a tela o relê
                }
                is ResultadoDaChamada.Falha -> avisarSobreVideo(frameId, r.motivo, ehErro = true)
            }
        }
    }

    private fun trocarPromptDeVideo(frameId: Int, novo: PromptDeFrame) {
        _estado.update { atual ->
            val lista = (atual.videos[frameId] as? VideosDoFrame.Pronto)?.lista ?: return@update atual
            atual.copy(videos = atual.videos + (frameId to VideosDoFrame.Pronto(lista.map { if (it.id == novo.id) novo else it })))
        }
    }

    /** **Ocultar** (ou mostrar de novo) um prompt de vídeo (VD12): ele sai da lista sem ser apagado. */
    fun ocultarPromptDeVideo(frameId: Int, promptId: Int, oculto: Boolean) {
        viewModelScope.launch {
            when (val r = prompts.ajustarPromptDeVideo(promptId, oculto = oculto)) {
                is ResultadoDaChamada.Sucesso -> trocarPromptDeVideo(frameId, r.dado)
                is ResultadoDaChamada.Falha -> avisarSobreVideo(frameId, r.motivo, ehErro = true)
            }
        }
    }

    /** **Salvar** o texto editado de um prompt de vídeo (VD13); [textoPt] vai junto quando o inglês veio da tradução do português. */
    fun salvarPromptDeVideo(frameId: Int, promptId: Int, texto: String, textoPt: String?) {
        viewModelScope.launch {
            when (val r = prompts.ajustarPromptDeVideo(promptId, texto = texto, textoPt = textoPt)) {
                is ResultadoDaChamada.Sucesso -> trocarPromptDeVideo(frameId, r.dado)
                is ResultadoDaChamada.Falha -> avisarSobreVideo(frameId, r.motivo, ehErro = true)
            }
        }
    }

    fun fecharImagemExistente() {
        if (_estado.value.usandoImagemExistente?.aplicando == true) return
        _estado.update { it.copy(usandoImagemExistente = null) }
    }

    private fun atualizarUsoDeImagem(mudar: (UsoDeImagemExistente) -> UsoDeImagemExistente) {
        _estado.update { atual -> atual.usandoImagemExistente?.let { atual.copy(usandoImagemExistente = mudar(it)) } ?: atual }
    }

    /**
     * Escolheu uma imagem: o retrato do elemento a **aponta como canônica** (CAN, VM3), sem gerar nada. Se o retrato ainda não existe,
     * é criado antes (N2, sem IA). Falha: a mensagem do servidor no próprio seletor, que continua aberto.
     */
    fun usarImagemExistente(imagemId: Int) {
        val uso = _estado.value.usandoImagemExistente ?: return
        if (uso.aplicando) return
        atualizarUsoDeImagem { it.copy(aplicando = true, erro = null) }
        viewModelScope.launch {
            val frame = uso.frameId ?: _estado.value.retratosCriados[uso.elemento.id]
                ?: reservarRetrato(uso.elemento)?.let { concluirCriacaoDoRetrato(uso.elemento, it) }
            if (frame == null) {
                atualizarUsoDeImagem { it.copy(aplicando = false, erro = _estado.value.mensagensDeRetrato[uso.elemento.id]?.texto ?: "Não consegui criar o retrato.") }
                return@launch
            }
            val resultado = elementos.definirImagemCanonica(frame, imagemId)
            if (resultado is ResultadoDaChamada.Falha) {
                atualizarUsoDeImagem { it.copy(aplicando = false, erro = resultado.motivo) }
                return@launch
            }
            _estado.update { it.copy(usandoImagemExistente = null) }
            aplicarMudancaDeImagemDoFrame(frame, resultado)
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
