package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.SugestoesDeCapitulo

// A geração de vários artefatos de uma vez, em fila (item 7.5e, GF1 a GF8). Só as regras, sem tela e sem rede: quem pode
// entrar, em que ordem, o que se pula, quando a fila para e como o resumo é escrito. Testadas na JVM (RegrasDaFilaTest).

/** O que a fila faz com cada item marcado (GF3). */
enum class AcaoDaFila(val rotulo: String) {
    /** Só o prompt: não gasta imagem, para a pessoa revisar antes de gastar. */
    SO_PROMPTS("Só os prompts"),

    /** O fluxo completo de um toque: o frame (se falta), o prompt (se falta) e a imagem. */
    PROMPTS_E_IMAGENS("Prompts e imagens"),
}

/**
 * Um artefato que pode ser marcado para a fila: um **retrato** de elemento ou uma **cena**.
 *
 * @property chave a mesma do fluxo de um toque (`retrato:ID` ou `frame:ID`), que também identifica a caixa de marcar.
 * @property frameId o frame, ou `null` num retrato que ainda não foi criado (a fila o cria, sem gastar IA).
 * @property elemento só nos retratos: é com ele que a fila cria o frame que falta.
 * @property situacao a do artefato (`SUGERIDO`, `CONFIRMADO`, `PROMPT_PRONTO` ou `ILUSTRADO`); decide o que se pula.
 * @property motivoDeNaoEntrar `null` = pode entrar; senão, o porquê (aparece em letra pequena, com a caixa desabilitada).
 */
data class ItemDaFila(
    val chave: String,
    val rotulo: String,
    val posicao: Int?,
    val frameId: Int?,
    val elemento: ElementoSugerido?,
    val situacao: String,
    val motivoDeNaoEntrar: String? = null,
) {
    val podeEntrar: Boolean get() = motivoDeNaoEntrar == null
}

/** Os itens da fila de um capítulo: os retratos dos elementos, as cenas da análise e as cenas criadas de um trecho (GF2). */
fun itensDaFila(sugestoes: SugestoesDeCapitulo, artefatos: List<Artefato>, retratosCriados: Map<Int, Int>): List<ItemDaFila> {
    val doElemento = sugestoes.elementos.filter { !it.descartada }.map { elemento ->
        val artefato = artefatos.firstOrNull { it.tipo == "ELEMENTO" && it.sugestao_id == elemento.id }
        val frameId = artefato?.frame_id ?: retratosCriados[elemento.id]
        ItemDaFila(
            chave = chaveDaFilaDoElemento(elemento),
            rotulo = rotuloDoRetrato(elemento),
            posicao = artefato?.posicao_no_texto,
            frameId = frameId,
            elemento = elemento,
            situacao = artefato?.situacao ?: "SUGERIDO",
            motivoDeNaoEntrar = if (podeTerRetrato(elemento)) null else MOTIVO_ELEMENTO_NAO_CONFIRMADO,
        )
    }
    val dasCenas = sugestoes.cenas.filter { !it.descartada }.map { cena ->
        val artefato = artefatos.firstOrNull { it.tipo == "CENA" && it.sugestao_id == cena.id }
        val frameId = cena.frame_id
        ItemDaFila(
            chave = chaveDaFilaDaCena(cena),
            rotulo = cena.titulo,
            posicao = artefato?.posicao_no_texto,
            frameId = frameId,
            elemento = null,
            situacao = artefato?.situacao ?: if (frameId != null) "CONFIRMADO" else "SUGERIDO",
            motivoDeNaoEntrar = if (frameId != null) null else MOTIVO_CENA_NAO_CONFIRMADA,
        )
    }
    val dosTrechos = cenasDeTrechos(artefatos).map { cena ->
        ItemDaFila(
            chave = chaveDoFluxoDoFrame(cena.frame_id!!),
            rotulo = cena.rotulo,
            posicao = cena.posicao_no_texto,
            frameId = cena.frame_id,
            elemento = null,
            situacao = cena.situacao,
        )
    }
    return doElemento + dasCenas + dosTrechos
}

/** A chave do retrato de um elemento na fila: a mesma do fluxo de um toque (vale antes e depois de o frame existir). */
fun chaveDaFilaDoElemento(elemento: ElementoSugerido): String = chaveDoFluxoDoRetrato(elemento.id)

/** A chave de uma cena da análise na fila: a do frame, ou `cena:ID` enquanto ela ainda não foi confirmada (não tem frame). */
fun chaveDaFilaDaCena(cena: CenaSugerida): String = cena.frame_id?.let { chaveDoFluxoDoFrame(it) } ?: "cena:${cena.id}"

const val MOTIVO_ELEMENTO_NAO_CONFIRMADO = "Confirme o elemento antes."
const val MOTIVO_CENA_NAO_CONFIRMADA = "Confirme a cena antes."

/** Este item **ainda precisa** do que a [acao] faz? `false` = a fila o pula (GF3): o prompt já existe, ou a imagem já está pronta. */
fun precisaDaAcao(item: ItemDaFila, acao: AcaoDaFila): Boolean = when (acao) {
    AcaoDaFila.SO_PROMPTS -> item.situacao != "PROMPT_PRONTO" && item.situacao != "ILUSTRADO"
    AcaoDaFila.PROMPTS_E_IMAGENS -> item.situacao != "ILUSTRADO"
}

/** O atalho **Marcar todos os que faltam** (GF2): os que podem entrar e ainda não estão `ILUSTRADO`. */
fun chavesQueFaltam(itens: List<ItemDaFila>): Set<String> =
    itens.filter { it.podeEntrar && it.situacao != "ILUSTRADO" }.map { it.chave }.toSet()

/** Em ordem do texto (GF4): pela posição no capítulo; quem não tem posição vem depois, na ordem em que estava. */
fun emOrdemDoTexto(itens: List<ItemDaFila>): List<ItemDaFila> =
    itens.withIndex().sortedWith(compareBy({ it.value.posicao ?: Int.MAX_VALUE }, { it.index })).map { it.value }

/** O que a pessoa vai confirmar (GF3): o que a fila fará, em ordem, e o que ela pulará. */
data class PedidoDaFila(val acao: AcaoDaFila, val aFazer: List<ItemDaFila>, val pulados: List<ItemDaFila>) {
    val vazio: Boolean get() = aFazer.isEmpty()
}

/** Monta o pedido a partir dos [marcados]: ignora o que não pode entrar, separa o que se pula e põe o resto em ordem do texto. */
fun prepararPedido(acao: AcaoDaFila, itens: List<ItemDaFila>, marcados: Set<String>): PedidoDaFila {
    val escolhidos = emOrdemDoTexto(itens.filter { it.chave in marcados && it.podeEntrar })
    return PedidoDaFila(acao, escolhidos.filter { precisaDaAcao(it, acao) }, escolhidos.filter { !precisaDaAcao(it, acao) })
}

/** A pergunta do diálogo de confirmação (GF3). */
fun perguntaDaFila(pedido: PedidoDaFila): String {
    val n = pedido.aFazer.size
    val itens = if (n == 1) "1 item" else "$n itens"
    val custo = when (pedido.acao) {
        AcaoDaFila.SO_PROMPTS -> "Não gasta imagem; cada prompt gasta uma chamada de IA de texto."
        AcaoDaFila.PROMPTS_E_IMAGENS -> "Cada imagem gasta crédito do modelo escolhido, e cada prompt, uma chamada de IA de texto."
    }
    val pulo = pedido.pulados.size.let {
        when (it) {
            0 -> ""
            1 -> " 1 marcado já está pronto e será pulado."
            else -> " $it marcados já estão prontos e serão pulados."
        }
    }
    return "${pedido.acao.rotulo}: $itens, um de cada vez. $custo$pulo"
}

// ---------------------------------------------------------------------------------------------------------------- //
// Falhas e o fim da fila (GF5)
// ---------------------------------------------------------------------------------------------------------------- //

/** Quantas falhas **seguidas** param a fila: quase sempre é algo que vale para todos (chave, modelo, crédito). */
const val FALHAS_SEGUIDAS_PARA_PARAR = 3

/**
 * Esta falha vale para **todos** os itens? Sem resposta do servidor (sem conexão) ou 401 (ele não reconheceu a pessoa): repetir seria
 * o mesmo erro em cada item. As demais (chave ou modelo sem escolha, crédito, uma recusa só daquele prompt) param pelo contador de
 * [FALHAS_SEGUIDAS_PARA_PARAR], porque o app não sabe dizer se valem para os outros.
 */
fun ehFalhaGeral(falha: ResultadoDaChamada.Falha): Boolean = falha.codigoHttp == null || falha.codigoHttp == 401

/** O que aconteceu com um item da fila. */
sealed interface DesfechoDoItem {
    data object Gerado : DesfechoDoItem

    /** Já estava pronto, ou já estava em andamento por outro toque (a fila não passa por cima). */
    data class Pulado(val motivo: String) : DesfechoDoItem

    /** Não deu certo; [geral] = vale para todos, a fila para na hora (GF5). */
    data class Falhou(val motivo: String, val geral: Boolean = false) : DesfechoDoItem
}

/** O fim da fila, em números e em listas (GF5). */
data class ResumoDaFila(
    val acao: AcaoDaFila,
    val total: Int,
    val gerados: Int,
    val pulados: Int,
    val falhas: List<Pair<ItemDaFila, String>>,
    /** Os que não chegaram a começar porque a fila parou (a pessoa tocou em Parar, ou uma falha geral). */
    val naoComecaram: List<ItemDaFila>,
    /** Por que a fila parou antes do fim por falha; `null` = foi até o fim ou a pessoa parou. */
    val motivoDaParada: String?,
    val parouAPedido: Boolean,
) {
    /** Os que valem "Tentar de novo" (GF5): os que falharam e os que nem começaram. */
    val paraRepetir: List<ItemDaFila> get() = falhas.map { it.first } + naoComecaram
}

/** Junta os desfechos no resumo. [pedido] traz os itens; [desfechos] o que cada um teve (a chave é a do item). */
fun resumirFila(pedido: PedidoDaFila, desfechos: Map<String, DesfechoDoItem>, motivoDaParada: String?, parouAPedido: Boolean): ResumoDaFila {
    val feitos = pedido.aFazer.filter { it.chave in desfechos }
    return ResumoDaFila(
        acao = pedido.acao,
        total = pedido.aFazer.size + pedido.pulados.size,
        gerados = feitos.count { desfechos[it.chave] is DesfechoDoItem.Gerado },
        pulados = pedido.pulados.size + feitos.count { desfechos[it.chave] is DesfechoDoItem.Pulado },
        falhas = feitos.mapNotNull { item -> (desfechos[item.chave] as? DesfechoDoItem.Falhou)?.let { item to it.motivo } },
        naoComecaram = pedido.aFazer.filter { it.chave !in desfechos },
        motivoDaParada = motivoDaParada,
        parouAPedido = parouAPedido,
    )
}

/** O resumo em uma frase: "8 itens: 6 gerados, 1 pulado, 1 falhou" (GF5). */
fun descreverResumoDaFila(resumo: ResumoDaFila): String {
    val partes = mutableListOf<String>()
    partes += if (resumo.gerados == 1) "1 gerado" else "${resumo.gerados} gerados"
    if (resumo.pulados > 0) partes += if (resumo.pulados == 1) "1 pulado" else "${resumo.pulados} pulados"
    if (resumo.falhas.isNotEmpty()) partes += if (resumo.falhas.size == 1) "1 falhou" else "${resumo.falhas.size} falharam"
    if (resumo.naoComecaram.isNotEmpty()) {
        partes += if (resumo.naoComecaram.size == 1) "1 não começou" else "${resumo.naoComecaram.size} não começaram"
    }
    val itens = if (resumo.total == 1) "1 item" else "${resumo.total} itens"
    return "$itens: ${partes.joinToString(", ")}"
}

/** A linha de progresso (GF4): "Gerando 3 de 8 — Auri". [indice] começa em 0. */
fun descreverProgressoDaFila(indice: Int, total: Int, rotulo: String): String = "Gerando ${indice + 1} de $total — $rotulo"

// ---------------------------------------------------------------------------------------------------------------- //
// O estado da fila na tela
// ---------------------------------------------------------------------------------------------------------------- //

/** A fila rodando: o pedido, qual item está em curso (a partir de 0) e se a pessoa já tocou em **Parar** (termina o item atual). */
data class AndamentoDaFila(val pedido: PedidoDaFila, val indice: Int, val parando: Boolean = false) {
    val itemAtual: ItemDaFila? get() = pedido.aFazer.getOrNull(indice)
}

/**
 * Tudo o que a tela precisa saber da fila (GF2 a GF5): o modo **Selecionar** e as caixas marcadas, o diálogo de confirmação, a fila
 * em andamento e o resumo do fim. Um assunto de cada vez: com a fila rodando não se marca nada; com o resumo aberto, o modo
 * Selecionar está desligado.
 */
data class EstadoDaFila(
    val selecionando: Boolean = false,
    val marcados: Set<String> = emptySet(),
    val confirmando: PedidoDaFila? = null,
    val andamento: AndamentoDaFila? = null,
    val resumo: ResumoDaFila? = null,
) {
    val rodando: Boolean get() = andamento != null
}
