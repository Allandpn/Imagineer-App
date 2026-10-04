package com.allan.imagineer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * As cores de destaque que a pessoa pode escolher em **Configurações → Aparência** (PA6). Cada uma tem uma variante para o tema
 * **escuro** (mais clara, para se ver no preto) e outra para o **claro** (mais escura, para se ver no branco). O âmbar é o padrão.
 * Para acrescentar uma cor, é só acrescentar uma linha aqui (o teste de contraste a confere nos dois temas).
 */
enum class DestaqueEscolhido(val rotulo: String, val noEscuro: Color, val noClaro: Color) {
    AMBAR("Âmbar", DestaqueNoEscuro, DestaqueNoClaro),
    AZUL("Azul", Color(0xFF7AB8FF), Color(0xFF0B57B7)),
    ROXO("Roxo", Color(0xFFC3A6FF), Color(0xFF6A3FC4)),
    VERDE("Verde", Color(0xFF7FD6A0), Color(0xFF1F7A45)),
    ROSA("Rosa", Color(0xFFFF9FC4), Color(0xFFB0306A)),
    VERMELHO("Vermelho", Color(0xFFFF8A80), Color(0xFFB3261E)),
    TURQUESA("Turquesa", Color(0xFF6FD6D0), Color(0xFF00696B));

    /** A cor desta escolha para o tema (escuro ou claro). */
    fun cor(escuro: Boolean): Color = if (escuro) noEscuro else noClaro

    companion object {
        /** O destaque guardado pelo nome; sem nada guardado (ou com um nome que não conhecemos) vale o âmbar. */
        fun deNome(nome: String?): DestaqueEscolhido = entries.firstOrNull { it.name == nome } ?: AMBAR
    }
}

/** Preto ou branco, o que tiver **mais contraste** com [fundo]: a cor do texto e dos ícones sobre o destaque. */
internal fun corSobre(fundo: Color): Color {
    fun razao(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
    val quaseBranco = Color(0xFFFFFFFF)
    val quasePreto = Color(0xFF14110A)
    return if (razao(quaseBranco, fundo) >= razao(quasePreto, fundo)) quaseBranco else quasePreto
}

/**
 * O [base] com o destaque trocado: `primary`, o texto sobre ele (`onPrimary`) e o tom suave (`primaryContainer` e o texto sobre ele),
 * todos derivados da cor escolhida. O resto da paleta (leitura, barras, texto) não muda.
 */
internal fun comDestaque(base: ColorScheme, destaque: Color, escuro: Boolean): ColorScheme {
    val suave = if (escuro) lerpDe(Color.Black, destaque, 0.28f) else lerpDe(Color.White, destaque, 0.22f)
    val sobreSuave = if (escuro) lerpDe(destaque, Color.White, 0.6f) else lerpDe(destaque, Color.Black, 0.65f)
    return base.copy(
        primary = destaque,
        onPrimary = corSobre(destaque),
        primaryContainer = suave,
        onPrimaryContainer = sobreSuave,
    )
}

private fun lerpDe(de: Color, para: Color, fracao: Float): Color = lerp(de, para, fracao)
