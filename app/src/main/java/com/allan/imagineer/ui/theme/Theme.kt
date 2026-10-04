package com.allan.imagineer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * O esquema **escuro**: leitura em preto, barras e menus em cinza levemente mais claro (`surface` e as camadas `surfaceContainer*`: a
 * barra de cima usa `surface`; menus, diálogos, cartões e a gaveta usam as camadas).
 */
internal val EsquemaEscuro: ColorScheme = darkColorScheme(
    primary = DestaqueNoEscuro,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF3D2E0A),
    onPrimaryContainer = Color(0xFFFFDFA0),
    secondary = Color(0xFFC9B99A),
    onSecondary = Color(0xFF241C0C),
    tertiary = Color(0xFF8FC7BD),
    onTertiary = Color(0xFF00201C),
    background = LeituraEscura,
    onBackground = TextoEscuro,
    surface = BarraEscura,
    onSurface = TextoEscuro,
    surfaceVariant = CamadaMaisAltaEscura,
    onSurfaceVariant = TextoSecundarioEscuro,
    surfaceContainerLowest = LeituraEscura,
    surfaceContainerLow = CamadaBaixaEscura,
    surfaceContainer = CamadaEscura,
    surfaceContainerHigh = CamadaAltaEscura,
    surfaceContainerHighest = CamadaMaisAltaEscura,
    outline = ContornoEscuro,
    outlineVariant = ContornoSuaveEscuro,
)

/** O esquema **claro**: leitura em branco, barras e menus em cinza bem leve. */
internal val EsquemaClaro: ColorScheme = lightColorScheme(
    primary = DestaqueNoClaro,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2A8),
    onPrimaryContainer = Color(0xFF2C1A00),
    secondary = Color(0xFF6B5B3A),
    onSecondary = Color.White,
    tertiary = Color(0xFF2F6F68),
    onTertiary = Color.White,
    background = LeituraClara,
    onBackground = TextoClaro,
    surface = BarraClara,
    onSurface = TextoClaro,
    surfaceVariant = CamadaMaisAltaClara,
    onSurfaceVariant = TextoSecundarioClaro,
    surfaceContainerLowest = LeituraClara,
    surfaceContainerLow = CamadaBaixaClara,
    surfaceContainer = CamadaClara,
    surfaceContainerHigh = CamadaAltaClara,
    surfaceContainerHighest = CamadaMaisAltaClara,
    outline = ContornoClaro,
    outlineVariant = ContornoSuaveClaro,
)

/**
 * As formas do aplicativo: **retas** (cantos de 0 dp) em menus, diálogos, cartões e folhas, a pedido do Allan (para testar; voltar ao
 * arredondado é trocar `CANTO_DAS_FORMAS`). Os botões (formato "cheio", que não vem destas formas), o botão flutuante e os chips
 * (`small`, 8 dp) seguem arredondados.
 */
val CANTO_DAS_FORMAS = 0.dp

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(CANTO_DAS_FORMAS),
    small = RoundedCornerShape(8.dp), // os chips voltam ao canto arredondado de antes
    medium = RoundedCornerShape(CANTO_DAS_FORMAS),
    large = RoundedCornerShape(CANTO_DAS_FORMAS),
    extraLarge = RoundedCornerShape(CANTO_DAS_FORMAS),
)

/**
 * O tema do Imagineer. Segue o claro/escuro do aparelho; a **cor dinâmica** do Android (que pintaria o app com o papel de parede) fica
 * **desligada**, para o app ter sempre a paleta dele.
 */
@Composable
fun ImagineerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** A cor de destaque que a pessoa escolheu em Configurações (o padrão é o âmbar). */
    destaque: DestaqueEscolhido = DestaqueEscolhido.AMBAR,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = comDestaque(if (darkTheme) EsquemaEscuro else EsquemaClaro, destaque.cor(darkTheme), darkTheme),
        typography = Typography,
        shapes = Formas,
        content = content,
    )
}
