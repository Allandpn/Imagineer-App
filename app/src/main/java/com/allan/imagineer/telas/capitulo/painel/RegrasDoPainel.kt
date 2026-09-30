package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.ElementoSugerido
import com.allan.imagineer.rede.ParticipanteSugerido

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
const val CASADO_SEM_ESTADO = "Casado, mas ainda sem estado neste capítulo"
const val JA_CADASTRADO = "Já cadastrado"
const val MANTEM_O_ESTADO = "Mantém o estado conhecido"

/**
 * Os destaques de um elemento, **na ordem de importância** (P12):
 *
 * 1. **Casado automaticamente — confira:** ninguém revisou esse casamento por nome (item 4.6).
 * 2. **Casado, mas ainda sem estado neste capítulo:** `elemento_id` existe e `estado_id` não
 *    (item 3.4e) — falta virar um Estado de verdade.
 * 3. **Já cadastrado:** `elemento_id` existe e o casamento foi revisado.
 * 4. **Mantém o estado conhecido:** a IA acha que o estado vigente continua valendo.
 *
 * Cada um aparece **se se aplica**; vários podem valer ao mesmo tempo.
 */
fun destaquesDoElemento(elemento: ElementoSugerido): List<String> = buildList {
    if (elemento.casamento_automatico) add(CASAMENTO_AUTOMATICO)
    if (elemento.elemento_id != null && elemento.estado_id == null) add(CASADO_SEM_ESTADO)
    if (elemento.elemento_id != null && !elemento.casamento_automatico) add(JA_CADASTRADO)
    if (elemento.manter_estado_atual) add(MANTEM_O_ESTADO)
}

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
