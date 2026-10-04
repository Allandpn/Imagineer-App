package com.allan.imagineer.dados

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// A aparência da leitura (item RL1 a RL8): guardada no aparelho, valendo para todos os livros.

/** A família da letra do texto (RL3). */
enum class FamiliaDeLeitura(val rotulo: String) {
    PADRAO("Padrão"),
    COM_SERIFA("Com serifa"),
    MONOESPACADA("Monoespaçada"),
}

/** As margens dos lados do texto (RL5), em dp. */
enum class MargemDeLeitura(val rotulo: String, val dp: Int) {
    ESTREITA("Estreita", 8),
    MEDIA("Média", 16),
    LARGA("Larga", 40),
}

/** O tema da área do texto (RL7). A barra e o resto do app seguem o tema do aparelho. */
enum class TemaDeLeitura(val rotulo: String) {
    DO_APLICATIVO("Do aplicativo"),
    CLARO("Claro"),
    ESCURO("Escuro"),
    SEPIA("Sépia"),
    PRETO("Preto"),
}

/**
 * Como o texto aparece (RL1 a RL8). Qualquer valor fora do intervalo é **trazido para dentro** por [normalizada] — um arquivo de
 * preferências corrompido ou de uma versão antiga nunca quebra a leitura.
 */
@Serializable
data class PreferenciasDeLeitura(
    /** Percentual do tamanho de hoje (RL2): 70 a 220, de 10 em 10. */
    val tamanho: Int = 100,
    val familia: FamiliaDeLeitura = FamiliaDeLeitura.PADRAO,
    /** Multiplicador da altura da linha (RL4): 1,3 a 2,2. */
    val entrelinha: Float = 1.6f,
    val margem: MargemDeLeitura = MargemDeLeitura.MEDIA,
    val justificado: Boolean = false,
    val tema: TemaDeLeitura = TemaDeLeitura.DO_APLICATIVO,
    /** Manter a tela acesa enquanto o capítulo está aberto (RL8). */
    val telaAcesa: Boolean = true,
    /** O brilho do app de 0,05 a 1; `null` = o do aparelho (RL8). */
    val brilho: Float? = null,
    /** Os nomes dos elementos do livro aparecem sublinhados e abrem a ficha ao toque (RL14, RL15f). */
    val nomesTocaveis: Boolean = true,
) {
    /** A mesma preferência com todos os valores dentro dos limites. */
    fun normalizada(): PreferenciasDeLeitura = copy(
        tamanho = (tamanho.coerceIn(TAMANHO_MINIMO, TAMANHO_MAXIMO) / PASSO_DO_TAMANHO) * PASSO_DO_TAMANHO,
        entrelinha = (Math.round(entrelinha.coerceIn(ENTRELINHA_MINIMA, ENTRELINHA_MAXIMA) * 10) / 10f),
        brilho = brilho?.coerceIn(BRILHO_MINIMO, 1f),
    )

    fun maisLetra(): PreferenciasDeLeitura = copy(tamanho = tamanho + PASSO_DO_TAMANHO).normalizada()
    fun menosLetra(): PreferenciasDeLeitura = copy(tamanho = tamanho - PASSO_DO_TAMANHO).normalizada()
    fun maisEntrelinha(): PreferenciasDeLeitura = copy(entrelinha = entrelinha + 0.1f).normalizada()
    fun menosEntrelinha(): PreferenciasDeLeitura = copy(entrelinha = entrelinha - 0.1f).normalizada()

    /** O texto que vai para o DataStore. */
    fun paraTexto(): String = JSON.encodeToString(serializer(), normalizada())

    companion object {
        const val TAMANHO_MINIMO = 70
        const val TAMANHO_MAXIMO = 220
        const val PASSO_DO_TAMANHO = 10
        const val ENTRELINHA_MINIMA = 1.3f
        const val ENTRELINHA_MAXIMA = 2.2f
        const val BRILHO_MINIMO = 0.05f

        private val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

        /** Lê o texto guardado; `null`, vazio ou ilegível dão o **padrão** (RL8). */
        fun deTexto(texto: String?): PreferenciasDeLeitura =
            if (texto.isNullOrBlank()) PreferenciasDeLeitura()
            else runCatching { JSON.decodeFromString(serializer(), texto).normalizada() }.getOrDefault(PreferenciasDeLeitura())
    }
}

/** O tamanho da letra do parágrafo, em sp, para o [percentual] sobre o tamanho base. */
fun tamanhoDaLetra(baseEmSp: Float, preferencias: PreferenciasDeLeitura): Float = baseEmSp * preferencias.tamanho / 100f

private val FUNDO_CLARO = Color(0xFFFFFFFF)
private val TEXTO_CLARO = Color(0xFF1C1B1F)
private val FUNDO_ESCURO = Color(0xFF121212)
private val TEXTO_ESCURO = Color(0xFFE3E3E3)
private val FUNDO_SEPIA = Color(0xFFF4ECD8)
private val TEXTO_SEPIA = Color(0xFF5B4636)
private val FUNDO_PRETO = Color(0xFF000000)
private val TEXTO_PRETO = Color(0xFFD0D0D0)

/** Fundo e texto de um tema de leitura; `null` no [TemaDeLeitura.DO_APLICATIVO] (valem as cores do app). */
fun coresDoTemaDeLeitura(tema: TemaDeLeitura): Pair<Color, Color>? = when (tema) {
    TemaDeLeitura.DO_APLICATIVO -> null
    TemaDeLeitura.CLARO -> FUNDO_CLARO to TEXTO_CLARO
    TemaDeLeitura.ESCURO -> FUNDO_ESCURO to TEXTO_ESCURO
    TemaDeLeitura.SEPIA -> FUNDO_SEPIA to TEXTO_SEPIA
    TemaDeLeitura.PRETO -> FUNDO_PRETO to TEXTO_PRETO
}

/**
 * O esquema de cores da **área do texto** (RL7): o do app com o fundo e os textos trocados pelos do tema, para tudo o que há dentro (o
 * cabeçalho do capítulo, as legendas) acompanhar o fundo e continuar legível. Sem tema de leitura, o esquema do app como está.
 */
fun esquemaDeLeitura(base: ColorScheme, tema: TemaDeLeitura): ColorScheme {
    val (fundo, texto) = coresDoTemaDeLeitura(tema) ?: return base
    val suave = texto.copy(alpha = 0.7f)
    return base.copy(
        background = fundo, surface = fundo, surfaceVariant = fundo, surfaceContainer = fundo,
        onBackground = texto, onSurface = texto, onSurfaceVariant = suave, outline = suave,
    )
}
