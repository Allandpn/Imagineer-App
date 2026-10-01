package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.DetalheDoElemento
import com.allan.imagineer.rede.ElementoDoLivro
import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.ParticipanteSugerido
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
