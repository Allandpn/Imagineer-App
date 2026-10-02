package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.ModelosDeImagem
import com.allan.imagineer.rede.ImagemDoPrompt
import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.DetalheDoElemento
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.ParticipanteSugerido
import com.allan.imagineer.rede.PromptDeFrame
import com.allan.imagineer.rede.SugestoesDeCapitulo
import com.allan.imagineer.telas.elementos.rotuloDoCapitulo
import com.allan.imagineer.telas.elementos.semAcentos

// Funções puras das regras do painel de IA (item 7.5b, incremento 9). Ficam fora do Compose e
// do ViewModel para serem testadas na JVM, sem nada de tela.

/** O nome de um tipo de elemento para a tela. Tipo desconhecido (um novo no backend) não quebra. */
fun rotuloDoTipo(tipo: String): String = when (tipo) {
    "PERSONAGEM" -> "Personagem"
    "AMBIENTE" -> "Ambiente"
    "OBJETO" -> "Objeto"
    "CRIATURA" -> "Criatura"
    "GRUPO" -> "Grupo"
    "VEICULO" -> "Veículo"
    "EDIFICACAO" -> "Edificação"
    else -> tipo.lowercase().replaceFirstChar { it.uppercase() }
}

const val CASAMENTO_AUTOMATICO = "Casado automaticamente — confira"

/** O estado que o cartão mostra (E11): um rótulo e o texto. */
data class LinhaDoEstado(val rotulo: String, val texto: String)

/**
 * O estado de aparência que o cartão mostra, **dizendo de qual capítulo ele é, pelo título** (E11,
 * E19). Era isto que confundia: o estado do capítulo 1 aparecia no capítulo 4 como se fosse dele, e
 * o número era a posição no livro, não o que o usuário vê na lista de capítulos.
 *
 * - O estado vigente **é** o deste capítulo (`estado_id` igual): "Estado neste capítulo".
 * - Veio de um capítulo anterior: "Usa o estado de «Capítulo VI»".
 * - Nenhum: `null`.
 */
fun linhaDoEstado(elemento: ElementoSugerido): LinhaDoEstado? {
    val vigente = elemento.estado_vigente ?: return null
    return if (elemento.estado_id != null && elemento.estado_id == vigente.id) {
        LinhaDoEstado("Estado neste capítulo", vigente.descricao)
    } else {
        LinhaDoEstado(
            "Usa o estado de «${rotuloDoCapitulo(vigente.titulo_do_capitulo, vigente.ordem_do_capitulo)}»",
            vigente.descricao,
        )
    }
}

/** Os tipos de elemento que o servidor aceita (item 3.1), na ordem em que a tela os oferece. */
val TIPOS_DE_ELEMENTO = listOf("PERSONAGEM", "AMBIENTE", "OBJETO", "CRIATURA", "GRUPO", "VEICULO", "EDIFICACAO")

/** Em que pé está uma sugestão de elemento, derivado só dos campos que a API devolve (E1, E12). */
enum class SituacaoDoElemento {
    /** `elemento_id` nulo: a IA sugeriu, ninguém confirmou. */
    NOVA,

    /** Casada só pelo nome; ninguém revisou. */
    CASADA_AUTOMATICAMENTE,

    /** Casada e revisada, mas o elemento não tem estado nenhum até este capítulo. */
    CASADA_SEM_ESTADO,

    /** Casada, revisada e com um estado que vale aqui (deste capítulo ou vigente de um anterior). */
    CONFIRMADA,
}

fun situacaoDoElemento(elemento: ElementoSugerido): SituacaoDoElemento = when {
    elemento.elemento_id == null -> SituacaoDoElemento.NOVA
    elemento.casamento_automatico -> SituacaoDoElemento.CASADA_AUTOMATICAMENTE
    elemento.estado_vigente == null -> SituacaoDoElemento.CASADA_SEM_ESTADO
    else -> SituacaoDoElemento.CONFIRMADA
}

/** A etiqueta do cartão fechado (E24): uma só, curta. */
fun etiquetaDaSituacao(elemento: ElementoSugerido): String = when {
    elemento.descartada -> "Descartada"
    else -> when (situacaoDoElemento(elemento)) {
        SituacaoDoElemento.NOVA -> "Nova"
        SituacaoDoElemento.CASADA_AUTOMATICAMENTE -> "Casada — confira"
        SituacaoDoElemento.CASADA_SEM_ESTADO -> "Casada, sem estado"
        SituacaoDoElemento.CONFIRMADA -> "Confirmada"
    }
}

/** O que o usuário pode fazer com uma sugestão de elemento (E1, E14 a E16, E22). */
enum class AcaoDoElemento(val rotulo: String) {
    CRIAR("Criar elemento"),
    VINCULAR("Vincular a um existente"),
    DESCARTAR("Descartar"),
    CONFIRMAR("Confirmar"),
    TROCAR("Trocar"),
    DESFAZER("Desfazer"),
    REGISTRAR_ESTADO("Registrar estado"),

    /** Abre a tela da ficha do elemento casado (E22): é onde se edita. A tela a trata, não o ViewModel. */
    ABRIR_FICHA("Ver ficha"),
}

/**
 * As ações de cada situação, na ordem em que aparecem (tabela da regra E1, revista em E22). **Confirmar
 * nunca é permanente**: toda sugestão casada pode abrir a ficha, trocar de elemento e desfazer.
 */
fun acoesDoElemento(situacao: SituacaoDoElemento): List<AcaoDoElemento> {
    val sobreOCasamento = listOf(AcaoDoElemento.ABRIR_FICHA, AcaoDoElemento.TROCAR, AcaoDoElemento.DESFAZER)
    return when (situacao) {
        SituacaoDoElemento.NOVA -> listOf(AcaoDoElemento.CRIAR, AcaoDoElemento.VINCULAR, AcaoDoElemento.DESCARTAR)
        SituacaoDoElemento.CASADA_AUTOMATICAMENTE -> listOf(AcaoDoElemento.CONFIRMAR) + sobreOCasamento
        SituacaoDoElemento.CASADA_SEM_ESTADO -> listOf(AcaoDoElemento.REGISTRAR_ESTADO) + sobreOCasamento
        SituacaoDoElemento.CONFIRMADA -> sobreOCasamento
    }
}

/** Os três filtros do painel (E24). */
enum class FiltroDoPainel(val rotulo: String) {
    PENDENTES("Pendentes"),
    CONFIRMADOS("Confirmados"),
    DESCARTADOS("Descartados"),
}

/** Em qual filtro cai uma sugestão: descartada, confirmada, ou o resto (pendente: pede ação). */
fun filtroDoElemento(elemento: ElementoSugerido): FiltroDoPainel = when {
    elemento.descartada -> FiltroDoPainel.DESCARTADOS
    situacaoDoElemento(elemento) == SituacaoDoElemento.CONFIRMADA -> FiltroDoPainel.CONFIRMADOS
    else -> FiltroDoPainel.PENDENTES
}

/**
 * A chave que lembra se um cartão está aberto (E33): junta o **filtro** em que ele está e o id. Assim,
 * um cartão que muda de filtro (ao ser confirmado, por exemplo) deixa de ser "o mesmo" e volta compacto.
 */
fun chaveDoCartao(elemento: ElementoSugerido): String = "${filtroDoElemento(elemento).name}:${elemento.id}"

/**
 * Em qual filtro cai uma **cena** (defeito D2): descartada, confirmada (já virou frame) ou o resto (pendente).
 * Antes, a lista de cenas aparecia inteira em todos os filtros porque o servidor não dizia se a cena estava
 * confirmada; agora diz (`frame_id`, item 6.7).
 */
fun filtroDaCena(cena: CenaSugerida): FiltroDoPainel = when {
    cena.descartada -> FiltroDoPainel.DESCARTADOS
    cena.frame_id != null -> FiltroDoPainel.CONFIRMADOS
    else -> FiltroDoPainel.PENDENTES
}

/** As cenas de um filtro, na ordem em que a IA as listou. */
fun cenasDoFiltro(cenas: List<CenaSugerida>, filtro: FiltroDoPainel): List<CenaSugerida> =
    cenas.filter { filtroDaCena(it) == filtro }

/**
 * Quantas sugestões há em cada filtro, para os contadores (E24). Com [cenas], os contadores somam
 * elementos **e** cenas: o filtro vale para as duas listas (D2).
 */
fun contagemPorFiltro(
    elementos: List<ElementoSugerido>,
    cenas: List<CenaSugerida> = emptyList(),
): Map<FiltroDoPainel, Int> {
    val dosElementos = elementos.groupingBy(::filtroDoElemento).eachCount()
    val dasCenas = cenas.groupingBy(::filtroDaCena).eachCount()
    return FiltroDoPainel.entries.associateWith { (dosElementos[it] ?: 0) + (dasCenas[it] ?: 0) }
}

/**
 * As sugestões de um filtro, em ordem (E29): nos pendentes, o que pede mais ação primeiro (nova,
 * casada automaticamente, casada sem estado); em empate, a ordem em que a IA listou.
 */
fun elementosDoFiltro(elementos: List<ElementoSugerido>, filtro: FiltroDoPainel): List<ElementoSugerido> {
    val doFiltro = elementos.filter { filtroDoElemento(it) == filtro }
    // O que o texto do capítulo não traz (`achado_no_texto = false`) vai para o fim: provavelmente a IA o
    // inventou, e não deve passar na frente do que é certo. A ordenação é estável: o resto mantém a ordem.
    val naoAchadosPorUltimo = doFiltro.sortedBy { !it.achado_no_texto }
    return if (filtro == FiltroDoPainel.PENDENTES) {
        naoAchadosPorUltimo.sortedWith(compareBy({ !it.achado_no_texto }, { situacaoDoElemento(it).ordinal }))
    } else {
        naoAchadosPorUltimo
    }
}

/**
 * Em quais cenas (não descartadas) cada elemento aparece, pelo título delas (E26): o cruzamento
 * dos participantes das cenas com as sugestões da mesma resposta. Quem não aparece em nenhuma não
 * entra no mapa.
 */
fun cenasDoElemento(sugestoes: SugestoesDeCapitulo): Map<Int, List<String>> {
    val mapa = mutableMapOf<Int, MutableList<String>>()
    sugestoes.cenas.filter { !it.descartada }.forEach { cena ->
        cena.participantes.forEach { mapa.getOrPut(it.sugestao_elemento_id) { mutableListOf() } += cena.titulo }
    }
    return mapa
}

/** "Aparece em 1 cena" / "Aparece em 3 cenas" (E26). */
fun descreverCenasDoElemento(titulos: List<String>): String? = when (titulos.size) {
    0 -> null
    1 -> "Aparece em 1 cena"
    else -> "Aparece em ${titulos.size} cenas"
}

/**
 * A lista de "vincular a um existente" (E3, E23): **só do mesmo tipo** da sugestão, a não ser que o
 * usuário peça [incluirOutrosTipos]; filtrada pelo que foi digitado (sem ligar para maiúsculas nem
 * acentos) e, com outros tipos, os do mesmo tipo primeiro; dentro de cada grupo, em ordem alfabética.
 */
fun filtrarParaVincular(
    elementos: List<ElementoDoLivro>,
    tipoDaSugestao: String,
    busca: String,
    incluirOutrosTipos: Boolean = false,
): List<ElementoDoLivro> {
    val termo = semAcentos(busca.trim())
    return elementos
        .filter { incluirOutrosTipos || it.tipo == tipoDaSugestao }
        .filter { termo.isEmpty() || semAcentos(it.nome).contains(termo) }
        .sortedWith(compareBy({ it.tipo != tipoDaSugestao }, { semAcentos(it.nome) }))
}

/**
 * A identidade vigente de um elemento: a descrição inicial mais o que cada capítulo acrescentou,
 * em ordem narrativa (item 3.4f) — o "compilado" de quem ele é. `null` se não há nada.
 */
fun identidadeVigente(detalhe: DetalheDoElemento): String? =
    (listOfNotNull(detalhe.descricao?.takeIf { it.isNotBlank() }) +
        detalhe.historico_identidade.sortedBy { it.ordem_do_capitulo ?: Int.MAX_VALUE }.map { it.descricao })
        .joinToString(" ")
        .ifBlank { null }

/** O destaque de um participante de cena (P13): só o casamento automático, que é o que pede conferência. */
fun destaqueDoParticipante(participante: ParticipanteSugerido): String? =
    if (participante.casamento_automatico) CASAMENTO_AUTOMATICO else null

/**
 * O aviso de sugestões não confirmadas em capítulos anteriores (P11) — ou `null` se não há
 * nenhuma. **Não bloqueia nada**: só diz que o contexto desta análise está mais pobre.
 */
fun descreverPendentesAnteriores(quantidade: Int): String? = when {
    quantidade <= 0 -> null
    quantidade == 1 ->
        "Você tem 1 sugestão não confirmada em capítulos anteriores — confirmar primeiro " +
            "deixa esta análise mais precisa."
    else ->
        "Você tem $quantidade sugestões não confirmadas em capítulos anteriores — confirmar " +
            "primeiro deixa esta análise mais precisa."
}

/**
 * A partir de que largura o painel é um **aside** ao lado do texto, e não a tela inteira (P4).
 * 840 dp é a largura a partir da qual o Material 3 considera a tela "expandida" (tablet).
 */
const val LARGURA_MINIMA_PARA_ASIDE_EM_DP = 840

fun usarAside(larguraEmDp: Int): Boolean = larguraEmDp >= LARGURA_MINIMA_PARA_ASIDE_EM_DP

/**
 * Quando o botão de IA aparece (P3): **some ao rolar para baixo, reaparece ao rolar para
 * cima, e fica visível no topo e no fim do texto** — para nunca haver um ponto da tela sem
 * acesso a ele.
 *
 * Só a **direção** da rolagem decide, e só depois de acumular um [limiar]: sem isso, um
 * tremor do dedo esconderia o botão a cada pixel. Mudar de direção recomeça a contagem.
 */
class VisibilidadeDoBotao(private val limiar: Float = 24f) {

    var visivel: Boolean = true
        private set

    private var acumulado = 0f

    /**
     * @param delta positivo = rolando **para baixo** (o texto sobe); negativo = para cima.
     */
    fun aoRolar(delta: Float, noTopo: Boolean, noFim: Boolean) {
        if (noTopo || noFim) {
            visivel = true
            acumulado = 0f
            return
        }
        if (delta == 0f) return

        // Mudou de direção: a contagem recomeça do zero.
        if (acumulado != 0f && (delta > 0f) != (acumulado > 0f)) acumulado = 0f
        acumulado += delta

        if (acumulado >= limiar) {
            visivel = false
        } else if (acumulado <= -limiar) {
            visivel = true
        }
    }
}

/** O limite do texto da orientação (item 6.7, M1): o servidor recusa acima disso. */
const val LIMITE_DA_ORIENTACAO = 1000

/**
 * O que mandar ao servidor, dado o que o usuário [digitou] e a orientação que já [vigente] no capítulo.
 *
 * - `null` quando não há o que dizer de novo (campo não mostrado, ou texto igual ao que já vale): o servidor
 *   reaproveita a guardada;
 * - o texto aparado quando mudou — e **vazio quando o usuário apagou** o que havia: é o pedido de "tirar a
 *   orientação", e o servidor a apaga.
 */
fun orientacaoAEnviar(digitada: String?, vigente: String?): String? {
    val texto = digitada?.trim() ?: return null
    return if (texto == (vigente ?: "")) null else texto
}

// ---------------------------------------------------------------------------------------------------------------- //
// A cena (incremento 10b, primeira fatia: C1 a C10)
// ---------------------------------------------------------------------------------------------------------------- //

/** As ações de decisão de uma cena (C4). "Gerar prompt" e "Novo retrato" chegam nas fatias seguintes. */
enum class AcaoDaCena(val rotulo: String) {
    CONFIRMAR("Confirmar cena"),
    DESCARTAR("Descartar"),
    RESTAURAR("Restaurar"),
}

/**
 * O que se pode decidir sobre a cena, na ordem em que aparecem (a primeira é a principal) — C4.
 * **Pendente:** confirmar ou descartar. **Confirmada** (já virou frame): nada a decidir aqui. **Descartada:** restaurar.
 */
fun acoesDaCena(cena: CenaSugerida): List<AcaoDaCena> = when (filtroDaCena(cena)) {
    FiltroDoPainel.PENDENTES -> listOf(AcaoDaCena.CONFIRMAR, AcaoDaCena.DESCARTAR)
    FiltroDoPainel.CONFIRMADOS -> emptyList()
    FiltroDoPainel.DESCARTADOS -> listOf(AcaoDaCena.RESTAURAR)
}

/** A etiqueta de situação do cartão da cena (C9): uma só, curta. */
fun etiquetaDaCena(cena: CenaSugerida): String = when (filtroDaCena(cena)) {
    FiltroDoPainel.PENDENTES -> "Pendente"
    FiltroDoPainel.CONFIRMADOS -> "Confirmada"
    FiltroDoPainel.DESCARTADOS -> "Descartada"
}

/**
 * Como um participante da cena aparece no modal (C3 e C11).
 *
 * @property precisaRevisar `true` quando ainda **não é um elemento cadastrado**: o servidor recusa confirmar a cena
 * (422) enquanto isso, e o modal oferece "Revisar".
 * @property acaoRapida a ação principal do elemento do participante, que o modal da cena executa **ali mesmo**, sem
 * sair dele (C11): "Confirmar" o casamento automático ou "Registrar estado". `null` quando não há o que fazer sem
 * escolher (um elemento novo pede criar ou vincular) ou quando já está tudo certo.
 */
data class SituacaoDoParticipante(
    val texto: String,
    val precisaRevisar: Boolean,
    val acaoRapida: AcaoDoElemento? = null,
)

/**
 * A situação de um participante. Com o [elemento] (a sugestão de elemento que o participante aponta, que vem na
 * mesma resposta), dá para dizer **tudo** o que falta para a cena poder ser confirmada: casamento a conferir,
 * estado a registrar. Sem ele, só se sabe se o participante já está ligado a um elemento.
 */
fun situacaoDoParticipante(
    participante: ParticipanteSugerido,
    elemento: ElementoSugerido? = null,
): SituacaoDoParticipante {
    if (elemento != null) {
        return when (situacaoDoElemento(elemento)) {
            SituacaoDoElemento.NOVA -> SituacaoDoParticipante("Sem elemento — revise", precisaRevisar = true)
            SituacaoDoElemento.CASADA_AUTOMATICAMENTE ->
                SituacaoDoParticipante("Casamento automático — confira", precisaRevisar = false, acaoRapida = AcaoDoElemento.CONFIRMAR)
            SituacaoDoElemento.CASADA_SEM_ESTADO ->
                SituacaoDoParticipante("Sem estado neste capítulo", precisaRevisar = false, acaoRapida = AcaoDoElemento.REGISTRAR_ESTADO)
            SituacaoDoElemento.CONFIRMADA -> SituacaoDoParticipante("Elemento confirmado", precisaRevisar = false)
        }
    }
    return when {
        participante.elemento_id == null -> SituacaoDoParticipante("Sem elemento — revise", precisaRevisar = true)
        participante.casamento_automatico -> SituacaoDoParticipante("Casamento automático — confira", precisaRevisar = false)
        else -> SituacaoDoParticipante("Elemento confirmado", precisaRevisar = false)
    }
}

/** O aviso quando a cena foi confirmada (C5). */
const val AVISO_CENA_CONFIRMADA = "Cena confirmada."

/** O aviso quando o servidor diz que a cena já estava confirmada, por exemplo em outro aparelho (C5, 409). */
const val AVISO_CENA_JA_CONFIRMADA = "Esta cena já estava confirmada."

// ---------------------------------------------------------------------------------------------------------------- //
// Confirmar todos (pedido do Allan, 01/10/2026)
// ---------------------------------------------------------------------------------------------------------------- //

/**
 * O que "Confirmar todos" vai fazer, para o diálogo de confirmação dizer **antes** (L2).
 *
 * @property casamentos elementos casados automaticamente, que ninguém conferiu: o lote os **confirma**.
 * @property estados elementos casados e revisados que **ainda não têm estado** até este capítulo: o lote **registra** o
 * estado (um rascunho). Os que acabam de ter o casamento confirmado também podem precisar de estado; esses o lote só
 * descobre depois do primeiro passo, então a conta aqui é um **mínimo**.
 * @property cenas cenas pendentes: o lote **tenta confirmar** cada uma (o servidor recusa as que ainda falta algo).
 * @property novos elementos **novos**: o lote **não toca neles**, porque criar ou vincular exige uma escolha.
 */
data class ResumoDoLote(val casamentos: Int, val estados: Int, val cenas: Int, val novos: Int) {
    /** Há algo que o lote consiga confirmar? (Só elementos novos não bastam: eles ficam para a pessoa.) */
    val temAlgoParaConfirmar: Boolean get() = casamentos + estados + cenas > 0
}

/** Conta o que "Confirmar todos" faria com as sugestões de agora. Descartadas ficam de fora (L7). */
fun resumoParaConfirmarTodos(sugestoes: SugestoesDeCapitulo): ResumoDoLote {
    val vivos = sugestoes.elementos.filter { !it.descartada }
    fun quantos(situacao: SituacaoDoElemento) = vivos.count { situacaoDoElemento(it) == situacao }
    return ResumoDoLote(
        casamentos = quantos(SituacaoDoElemento.CASADA_AUTOMATICAMENTE),
        estados = quantos(SituacaoDoElemento.CASADA_SEM_ESTADO),
        cenas = cenasDoFiltro(sugestoes.cenas, FiltroDoPainel.PENDENTES).size,
        novos = quantos(SituacaoDoElemento.NOVA),
    )
}

/** O texto do diálogo de confirmação (L2): diz o que vai acontecer **e o que não vai**. */
fun descreverLoteParaConfirmar(resumo: ResumoDoLote): String {
    val partes = mutableListOf<String>()
    if (resumo.casamentos > 0) {
        partes += "confirmar o casamento de ${resumo.casamentos} ${if (resumo.casamentos == 1) "elemento casado" else "elementos casados"} automaticamente (que você ainda não conferiu)"
    }
    if (resumo.estados > 0) partes += "registrar o estado de ${resumo.estados} ${if (resumo.estados == 1) "elemento" else "elementos"} que ainda não ${if (resumo.estados == 1) "tem" else "têm"} (um rascunho, que a IA refaz ao gerar o prompt)"
    if (resumo.cenas > 0) partes += "tentar confirmar ${resumo.cenas} ${if (resumo.cenas == 1) "cena" else "cenas"}"
    val faz = "Isto vai " + partes.joinToString("; ") + "."
    val naoFaz = buildString {
        append(" Não gasta IA e não descarta nada.")
        if (resumo.novos > 0) {
            append(" ${resumo.novos} ${if (resumo.novos == 1) "elemento novo fica" else "elementos novos ficam"} para você: criar ou vincular exige uma escolha.")
        }
    }
    return faz + naoFaz
}

/**
 * O resumo do que o lote fez (L5), em uma ou duas frases. [falhas] são as chamadas que o servidor recusou (ficaram
 * pendentes); [interrompidoPor] é o motivo, se a conexão caiu e o lote parou no meio (L4); [novosRestantes] são os
 * elementos novos que seguem esperando a pessoa.
 */
fun descreverResultadoDoLote(
    casamentos: Int,
    estados: Int,
    cenas: Int,
    falhas: List<String>,
    interrompidoPor: String?,
    novosRestantes: Int,
): String {
    val feitos = buildList {
        if (casamentos > 0) add("$casamentos ${if (casamentos == 1) "casamento" else "casamentos"}")
        if (estados > 0) add("$estados ${if (estados == 1) "estado" else "estados"}")
        if (cenas > 0) add("$cenas ${if (cenas == 1) "cena" else "cenas"}")
    }
    val primeira = if (feitos.isEmpty()) "Nada foi confirmado." else "Confirmado: ${feitos.joinToString(", ")}."
    val pendencias = buildList {
        if (novosRestantes > 0) add("$novosRestantes ${if (novosRestantes == 1) "elemento novo espera" else "elementos novos esperam"} sua decisão")
        if (falhas.isNotEmpty()) add("${falhas.size} ${if (falhas.size == 1) "item ficou" else "itens ficaram"} pendente${if (falhas.size == 1) "" else "s"}: ${falhas.first()}")
    }
    // O motivo do servidor já pode terminar em ponto: tira antes de pôr o nosso, para não sair "..".
    val segunda = if (pendencias.isEmpty()) "" else " " + pendencias.joinToString("; ").replaceFirstChar { it.uppercase() }.trimEnd('.') + "."
    val parou = interrompidoPor?.let { " Parou no meio: $it" }.orEmpty()
    return primeira + segunda + parou
}

// ---------------------------------------------------------------------------------------------------------------- //
// Gerar o prompt e copiar (incremento 10b, segunda fatia: G1 a G10)
// ---------------------------------------------------------------------------------------------------------------- //

/** O limite do "ajuste" do prompt (G3): o servidor recusa acima disso. */
const val LIMITE_DO_AJUSTE_DO_PROMPT = 2000

/** O rótulo do botão (G3): "Gerar prompt" na primeira vez, "Gerar outro prompt" depois. */
fun rotuloDoBotaoDePrompt(jaTemPrompts: Boolean): String = if (jaTemPrompts) "Novo prompt" else "Gerar prompt"

/** O que o diálogo de confirmação diz (G3): que **gasta IA**, e o que o campo "Ajuste" faz. */
const val TEXTO_DO_DIALOGO_DE_PROMPT =
    "Gerar o prompt gasta IA: ela relê o capítulo e monta o texto para você colar na ferramenta de imagem. " +
        "Se quiser corrigir algo (ou refinar um prompt anterior), escreva no campo abaixo; senão, deixe em branco."

/**
 * O aviso das imagens-âncora (G5): o fluxo é manual, então a API não as anexa; só avisa que existem. `null` se não há.
 */
fun descreverReferenciasVisuais(quantas: Int): String? = when {
    quantas <= 0 -> null
    quantas == 1 -> "Anexe também a imagem de referência deste elemento, para manter a aparência."
    else -> "Anexe também as $quantas imagens de referência dos elementos, para manter a aparência."
}

/** O que o app sabe dos prompts de um frame (G2). */
sealed interface PromptsDoFrame {
    data object Lendo : PromptsDoFrame
    data class Pronto(val lista: List<PromptDeFrame>) : PromptsDoFrame
    data class Erro(val motivo: String) : PromptsDoFrame
}

/** O aviso de que o prompt foi copiado (G6). */
const val AVISO_PROMPT_COPIADO = "Copiado."

/** O aviso translúcido que o modal mostra quando o prompt fica pronto (G13). */
const val AVISO_PROMPT_GERADO = "Prompt gerado."

/** Quanto tempo o aviso fica na tela (G13). */
const val DURACAO_DO_AVISO_DE_PROMPT_EM_MS = 2500L

// ---------------------------------------------------------------------------------------------------------------- //
// Gerar a imagem no app (incremento 12, terceira fatia: K1 a K10)
// ---------------------------------------------------------------------------------------------------------------- //

/** O texto da barra de progresso enquanto o servidor gera a imagem (K2). */
const val AVISO_GERANDO_IMAGEM = "Gerando a imagem… pode levar mais de um minuto."

/** As etapas do botão principal (Q4): o que o toque está fazendo agora. */
enum class EtapaDaImagem { CRIANDO_O_RETRATO, MONTANDO_O_PROMPT, GERANDO_A_IMAGEM }

/** O texto de cada etapa, na barra de progresso (Q4). */
fun descreverEtapa(etapa: EtapaDaImagem): String = when (etapa) {
    EtapaDaImagem.CRIANDO_O_RETRATO -> "Criando o retrato…"
    EtapaDaImagem.MONTANDO_O_PROMPT -> "Montando o prompt… pode levar mais de um minuto."
    EtapaDaImagem.GERANDO_A_IMAGEM -> AVISO_GERANDO_IMAGEM
}

/** A chave do fluxo de um toque do **retrato** de um elemento: vale antes e depois de o frame existir (Q5). */
fun chaveDoFluxoDoRetrato(elementoId: Int): String = "retrato:$elementoId"

/** A chave do fluxo de um toque de um **frame** (a cena). */
fun chaveDoFluxoDoFrame(frameId: Int): String = "frame:$frameId"

/** A linha fixa sob o botão principal (Q3): diz o que o toque faz e que gasta IA. */
fun avisoDoBotaoPrincipal(jaTemPrompt: Boolean): String =
    if (jaTemPrompt) "Gera a imagem (gasta IA)." else "Gera o prompt e a imagem (gasta IA)."

/** O rótulo do botão que recolhe e mostra os prompts (Q7). */
fun rotuloDeVerPrompts(aberto: Boolean, quantos: Int): String = when {
    aberto -> "Ocultar prompts"
    quantos > 0 -> "Ver prompts ($quantos)"
    else -> "Ver prompts"
}

/** O aviso depois de salvar a imagem na galeria (U2). */
const val AVISO_SALVA_NA_GALERIA = "Salva na galeria."

/** O aviso quando o Android não deixou salvar a imagem na galeria (U2). */
const val AVISO_NAO_SALVOU_NA_GALERIA = "Não consegui salvar na galeria."

/** O aviso quando a imagem saiu do prompt como estava (K3). */
const val AVISO_IMAGEM_GERADA = "Imagem gerada."

/** O aviso quando o servidor precisou suavizar o prompt para o provedor aceitar (K3). */
const val AVISO_IMAGEM_GERADA_SUAVIZADA =
    "O provedor recusou o prompt original; a imagem saiu de uma versão mais suave, que ficou salva como outro prompt."

/** O motivo mostrado quando o provedor recusou mas não disse por quê (K4). */
const val MOTIVO_PADRAO_DA_RECUSA = "O provedor recusou o conteúdo do prompt."

/** O tamanho máximo do prompt editado à mão: o mesmo que o servidor aceita (item 6.6). */
const val LIMITE_DO_PROMPT_EDITADO = 8000

/** O aviso de uma geração bem-sucedida (K3): diz se o servidor precisou suavizar. */
fun avisoDaGeracao(suavizado: Boolean): String = if (suavizado) AVISO_IMAGEM_GERADA_SUAVIZADA else AVISO_IMAGEM_GERADA

/**
 * As etiquetas de um prompt na lista (K5, Z7): de onde ele veio e como terminou a última tentativa de gerar a imagem, **com o
 * modelo**. **Versão suavizada** é a que o sistema reescreveu (o servidor preenche o `modelo_ia` só nela); **versão editada**,
 * a que a pessoa reescreveu. **Recusado por X** / **Gerado com X** dizem qual modelo de imagem foi; sem o modelo (prompt
 * antigo), fica **Recusado pelo provedor**. `NAO_TENTADO` não leva etiqueta.
 */
fun etiquetasDoPrompt(prompt: PromptDeFrame): List<String> = buildList {
    if (prompt.prompt_original_id != null) add(if (prompt.modelo_ia != null) "Versão suavizada" else "Versão editada")
    when (prompt.situacao_da_geracao) {
        "RECUSADO" -> add(prompt.modelo_imagem?.let { "Recusado por $it" } ?: "Recusado pelo provedor")
        "COM_SUCESSO" -> prompt.modelo_imagem?.let { add("Gerado com $it") }
    }
    // F16: a pessoa nunca fica em dúvida de como a imagem nasceu.
    if (prompt.sem_filtro_de_seguranca) add(ETIQUETA_SEM_FILTRO)
}

/** A etiqueta de uma tentativa feita com o filtro de segurança do modelo desligado (F16). */
const val ETIQUETA_SEM_FILTRO = "Sem filtro"


/**
 * Este modelo é um dos **sem filtro de segurança** (F19)? Escolhê-lo no modal de modelos **é** pedir a geração sem o filtro:
 * o app manda `sem_filtro_de_seguranca` quando (e só quando) isto é verdadeiro.
 */
fun modeloEstaSemFiltro(modelo: String?, modelos: ModelosDeImagem?): Boolean =
    !modelo.isNullOrBlank() && modelos?.semFiltro.orEmpty().any { it.trim() == modelo.trim() }

/** Os modelos **sem filtro** do modal (F13, F19): só os da lista do servidor, sem repetir nem vazios; **nunca o padrão**. */
fun modelosSemFiltroParaEscolher(modelos: ModelosDeImagem): List<String> =
    modelos.semFiltro.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** Os modelos que a pessoa pode escolher (Z2, Z6): o **padrão primeiro**, mesmo que não esteja na lista, sem repetir. */
fun modelosParaEscolher(modelos: ModelosDeImagem): List<String> =
    (listOf(modelos.padrao) + modelos.lista).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** O modelo que a próxima geração vai usar (Z6): o que a pessoa escolheu, ou o padrão do servidor; `null` se ainda não se sabe. */
fun modeloEmUso(escolhido: String?, modelos: ModelosDeImagem?): String? =
    escolhido?.takeIf { it.isNotBlank() } ?: modelos?.padrao?.takeIf { it.isNotBlank() }

/** O modelo sugerido depois de uma recusa (Z9): o primeiro da lista que **não** foi o que recusou. */
fun alternativaAoModelo(recusado: String?, opcoes: List<String>): String? =
    opcoes.firstOrNull { it != recusado } ?: opcoes.firstOrNull()

/** O que a tela cheia diz da imagem (Z8): quem a gerou, ou que foi importada. */
fun descreverOrigemDaImagem(imagem: ImagemDoPrompt): String =
    (imagem.modelo?.let { "Gerada por $it" } ?: "Importada") + if (imagem.sem_filtro_de_seguranca) " (sem filtro)" else ""

// ---------------------------------------------------------------------------------------------------------------- //
// Novo retrato (incremento 10b, terceira fatia: N1 a N8)
// ---------------------------------------------------------------------------------------------------------------- //

/**
 * Esta sugestão pode ter retrato? (N1) Só o elemento **confirmado** — casado, revisado e com um estado que vale neste
 * capítulo — e não descartado. É o estado dele que o retrato usa.
 */
fun podeTerRetrato(elemento: ElementoSugerido): Boolean =
    !elemento.descartada && situacaoDoElemento(elemento) == SituacaoDoElemento.CONFIRMADA && elemento.estado_vigente != null

/** O rótulo do retrato nos avisos e nos prompts (N6): "Retrato de Jon". */
fun rotuloDoRetrato(elemento: ElementoSugerido): String = "Retrato de ${elemento.elemento_casado?.nome ?: elemento.nome}"

/**
 * Qual frame é o retrato de cada sugestão de elemento neste capítulo (N4): o `frame_id` do **artefato** do elemento. Só
 * entram os artefatos de elemento que já têm retrato.
 */
fun retratosPorSugestao(artefatos: List<Artefato>): Map<Int, Int> =
    artefatos
        .filter { it.tipo == "ELEMENTO" && it.sugestao_id != null && it.frame_id != null }
        .associate { it.sugestao_id!! to it.frame_id!! }
