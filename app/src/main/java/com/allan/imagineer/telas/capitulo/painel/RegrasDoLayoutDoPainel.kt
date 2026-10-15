package com.allan.imagineer.telas.capitulo.painel

import com.allan.imagineer.rede.CenaSugerida
import com.allan.imagineer.rede.ElementoSugerido

// O layout do painel de IA (item 7.5f, PT, PC, PM, PX): só as regras, sem tela. O que cada cartão mostra à vista, o que vai para o ⋮ e como
// os títulos se escrevem. Testadas na JVM (RegrasDoLayoutDoPainelTest); o desenho em si (Compose) é validado no tablet.

// ---------------------------------------------------------------------------------------------------------------- //
// O topo (PT3)
// ---------------------------------------------------------------------------------------------------------------- //

/** Os itens do ⋮ **Mais** do topo do painel (PT3). Confirmar todos fica fora, à vista. */
enum class ItemDoMenuDoTopo(val rotulo: String) {
    REANALISAR("Reanalisar"),
    SELECIONAR("Selecionar vários"),
}

/** O texto do item de selecionar: vira "Sair da seleção" enquanto o modo está ligado (PT3). */
fun rotuloDeSelecionar(selecionando: Boolean): String = if (selecionando) "Sair da seleção" else ItemDoMenuDoTopo.SELECIONAR.rotulo

// ---------------------------------------------------------------------------------------------------------------- //
// Os cartões (PC1 a PC3)
// ---------------------------------------------------------------------------------------------------------------- //

/** Os itens do ⋮ de um cartão de elemento ou de cena (PC2). O alfinete deixou de ser um ícone solto: está aqui. */
enum class ItemDoMenuDoCartao(val rotulo: String, val perigoso: Boolean = false) {
    POSICIONAR(ROTULO_POSICIONAR),
    VOLTAR_A_POSICAO_AUTOMATICA(ROTULO_TIRAR_POSICAO),
    EDITAR_A_CENA(ROTULO_EDITAR_A_CENA),

    /** Separado dos outros, em vermelho, e com a confirmação que já existia (PC2). */
    APAGAR_A_CENA(ROTULO_APAGAR_A_CENA, perigoso = true),
}

/** O ⋮ do cartão de um elemento: posicionar e voltar à posição automática. O descartado não tem posição, então não tem menu. */
fun itensDoMenuDoElemento(elemento: ElementoSugerido): List<ItemDoMenuDoCartao> =
    if (elemento.descartada) emptyList() else listOf(ItemDoMenuDoCartao.POSICIONAR, ItemDoMenuDoCartao.VOLTAR_A_POSICAO_AUTOMATICA)

/** O ⋮ do cartão de uma cena: o do elemento, mais editar e, se a cena já virou frame, apagar (que leva os prompts e as imagens). */
fun itensDoMenuDaCena(temFrame: Boolean): List<ItemDoMenuDoCartao> =
    listOf(ItemDoMenuDoCartao.POSICIONAR, ItemDoMenuDoCartao.VOLTAR_A_POSICAO_AUTOMATICA, ItemDoMenuDoCartao.EDITAR_A_CENA) +
        if (temFrame) listOf(ItemDoMenuDoCartao.APAGAR_A_CENA) else emptyList()

/**
 * A decisão que o cartão **fechado** mostra à vista (PC3): a ação principal da situação, para decidir sem abrir. Só o item **pendente** (nem
 * confirmado, nem descartado): *Criar elemento*, *Confirmar* ou *Registrar estado*. `null` = sem botão.
 */
fun acaoPrincipalDoElemento(elemento: ElementoSugerido): AcaoDoElemento? {
    if (elemento.descartada) return null
    val situacao = situacaoDoElemento(elemento)
    if (situacao == SituacaoDoElemento.CONFIRMADA) return null
    return acoesDoElemento(situacao).firstOrNull()
}

/** O mesmo para a cena (PC3): *Confirmar cena* quando está pendente; a confirmada e a descartada não ganham botão. */
fun acaoPrincipalDaCena(cena: CenaSugerida): AcaoDaCena? =
    if (filtroDaCena(cena) == FiltroDoPainel.PENDENTES) acoesDaCena(cena).firstOrNull() else null

// ---------------------------------------------------------------------------------------------------------------- //
// O cartão de prompt e as seções (PM1, PM2, PX1)
// ---------------------------------------------------------------------------------------------------------------- //

/** O título da seção dos prompts de **imagem** (PM1, PD5): "Prompts de imagem" sem nenhum, "Prompts de imagem (3)" com três. */
fun tituloDaSecaoDePrompts(quantos: Int): String = if (quantos <= 0) "Prompts de imagem" else "Prompts de imagem ($quantos)"

/** O título da seção dos prompts de **vídeo** (PD5), no mesmo formato da dos de imagem. */
fun tituloDaSecaoDePromptsDeVideo(quantos: Int): String = if (quantos <= 0) "Prompts de vídeo" else "Prompts de vídeo ($quantos)"

/** Os títulos das seções do corpo de um frame (PM1). */
const val TITULO_DA_SECAO_DE_IMAGENS = "Imagens"
