package com.allan.imagineer.telas.capitulo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allan.imagineer.rede.Artefato

// Os ícones sobre o texto (item 7.5b): um por tipo de elemento, mais um para a cena. Só o desenho fica
// aqui; as regras (quem vai em qual parágrafo) estão em ArtefatosNoTexto.kt, testadas na JVM.

/**
 * O ícone de um artefato: **o tipo** escolhe o desenho (um por tipo de elemento, mais a cena) e **a
 * situação** o enche — sugestão ainda não confirmada fica só no **contorno**; confirmada, **cheia**.
 * Um tipo que o app não conhece (novo no servidor) cai num ícone genérico, em vez de quebrar.
 */
fun iconeDoArtefato(artefato: Artefato): ImageVector {
    val contorno = artefato.situacao == "SUGERIDO"
    return if (artefato.tipo == "CENA") {
        if (contorno) Icons.Outlined.Movie else Icons.Filled.Movie
    } else {
        when (artefato.tipo_do_elemento) {
            "PERSONAGEM" -> if (contorno) Icons.Outlined.Person else Icons.Filled.Person
            "AMBIENTE" -> if (contorno) Icons.Outlined.Landscape else Icons.Filled.Landscape
            "OBJETO" -> if (contorno) Icons.Outlined.Category else Icons.Filled.Category
            "CRIATURA" -> if (contorno) Icons.Outlined.Pets else Icons.Filled.Pets
            "GRUPO" -> if (contorno) Icons.Outlined.Groups else Icons.Filled.Groups
            "VEICULO" -> if (contorno) Icons.Outlined.DirectionsCar else Icons.Filled.DirectionsCar
            "EDIFICACAO" -> if (contorno) Icons.Outlined.Domain else Icons.Filled.Domain
            else -> Icons.Filled.AutoStories
        }
    }
}

/**
 * As cores do fundo do selo, uma por situação (SE2). O glifo é sempre branco, então todas são escuras o bastante para o
 * branco ter contraste de pelo menos 4,5:1 (um teste confere). Moram aqui, num lugar só, para trocar uma sem mexer no resto.
 * Âmbar e verde-azulado (e não vermelho e verde) para quem não distingue essas duas cores ainda separar as situações.
 */
object CoresDoSelo {
    val SUGERIDO = Color(0xFF546E7A)
    val CONFIRMADO = Color(0xFF1565C0)
    val PROMPT_PRONTO = Color(0xFFB45F06)
    val ILUSTRADO = Color(0xFF00796B)
    val GLIFO = Color.White

    /** A cor da [situacao]; uma situação que o app não conhece (nova no servidor) cai no cinza-azulado. */
    fun da(situacao: String): Color = when (situacao) {
        "CONFIRMADO" -> CONFIRMADO
        "PROMPT_PRONTO" -> PROMPT_PRONTO
        "ILUSTRADO" -> ILUSTRADO
        else -> SUGERIDO
    }
}

/** O selo mede isto vezes o tamanho da fonte do texto (SE3): "um pouco maior que a letra". */
internal const val PROPORCAO_DO_SELO = 1.5f

/** Nunca menor que isto, em sp, mesmo com a fonte pequena. */
internal const val TAMANHO_MINIMO_DO_SELO = 24f

/** O tamanho do selo para uma fonte de [fonte] (SE3); fonte sem tamanho em sp vira o mínimo. */
fun tamanhoDoSelo(fonte: TextUnit): TextUnit =
    if (fonte.isSp) maxOf(TAMANHO_MINIMO_DO_SELO, fonte.value * PROPORCAO_DO_SELO).sp else TAMANHO_MINIMO_DO_SELO.sp

/**
 * O selo de um artefato (SE1): um círculo com o fundo da **situação** e o glifo do **tipo** em branco, que ocupa uns 60%.
 * Quem chama dá o tamanho em [modifier]; o glifo acompanha.
 */
@Composable
fun SeloDoArtefato(artefato: Artefato, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(CircleShape).background(CoresDoSelo.da(artefato.situacao)), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = iconeDoArtefato(artefato),
            contentDescription = null, // quem chama descreve (a situação é dita em palavras, nunca só pela cor)
            tint = CoresDoSelo.GLIFO,
            modifier = Modifier.fillMaxSize(0.6f),
        )
    }
}

/** O selo tocável de um artefato; tocar leva ao item no painel de IA. */
@Composable
fun IconeDoArtefato(artefato: Artefato, aoTocar: (Artefato) -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = { aoTocar(artefato) }, modifier = modifier.size(32.dp).semantics { contentDescription = descreverArtefato(artefato) }) {
        SeloDoArtefato(artefato, Modifier.size(28.dp))
    }
}

/** Os rótulos da legenda (SE5), na ordem do caminho de um artefato. */
private val SITUACOES_DA_LEGENDA = listOf("SUGERIDO" to "Sugerido", "CONFIRMADO" to "Confirmado", "PROMPT_PRONTO" to "Prompt pronto", "ILUSTRADO" to "Ilustrado")

/** A legenda das cores do selo (SE5): uma bolinha da cor e o nome da situação, para não precisar decorar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LegendaDosSelos(modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SITUACOES_DA_LEGENDA.forEach { (situacao, nome) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(CoresDoSelo.da(situacao)))
                Text(nome, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
