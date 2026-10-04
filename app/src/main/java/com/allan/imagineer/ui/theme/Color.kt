package com.allan.imagineer.ui.theme

import androidx.compose.ui.graphics.Color

// A paleta do Imagineer (item 7.3a): a **leitura é preta** (ou branca, no tema claro) e as **barras, menus e cartões** são um cinza
// levemente mais claro que ela, para se destacarem do texto sem pesar. Um único **destaque**, o âmbar (luz de lamparina: quente, bem
// visível no preto e diferente do azul do Kindle e do roxo do JW Library). **Para trocar o destaque, mexa só nas duas cores abaixo.**

/** O destaque no tema **claro**: âmbar escuro (contraste suficiente sobre branco). */
val DestaqueNoClaro = Color(0xFF8A5A00)

/** O destaque no tema **escuro**: âmbar claro (contraste suficiente sobre preto). */
val DestaqueNoEscuro = Color(0xFFF2B84B)

// Tema escuro: a leitura em preto puro; as camadas acima dela, em cinzas cada vez mais claros.
val LeituraEscura = Color(0xFF000000)
val BarraEscura = Color(0xFF1A1A1A)
val CamadaBaixaEscura = Color(0xFF141414)
val CamadaEscura = Color(0xFF1E1E1E)
val CamadaAltaEscura = Color(0xFF242424)
val CamadaMaisAltaEscura = Color(0xFF2B2B2B)
val TextoEscuro = Color(0xFFE8E8E8)
val TextoSecundarioEscuro = Color(0xFFB0B0B0)
val ContornoEscuro = Color(0xFF6B6B6B)
val ContornoSuaveEscuro = Color(0xFF3A3A3A)

// Tema claro: a leitura em branco; as camadas, em cinzas bem leves.
val LeituraClara = Color(0xFFFFFFFF)
val BarraClara = Color(0xFFF2F2F2)
val CamadaBaixaClara = Color(0xFFF7F7F7)
val CamadaClara = Color(0xFFF0F0F0)
val CamadaAltaClara = Color(0xFFEAEAEA)
val CamadaMaisAltaClara = Color(0xFFE4E4E4)
val TextoClaro = Color(0xFF1A1A1A)
val TextoSecundarioClaro = Color(0xFF555555)
val ContornoClaro = Color(0xFF8A8A8A)
val ContornoSuaveClaro = Color(0xFFCFCFCF)
