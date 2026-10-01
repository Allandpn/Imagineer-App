package com.allan.imagineer.telas.capitulo

import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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

/** A cor da situação: do apagado (sugerido) ao destaque (ilustrado), para ver onde se parou. */
@Composable
private fun corDoArtefato(artefato: Artefato): Color = when (artefato.situacao) {
    "SUGERIDO" -> MaterialTheme.colorScheme.onSurfaceVariant
    "PROMPT_PRONTO" -> MaterialTheme.colorScheme.tertiary
    "ILUSTRADO" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface
}

/** O ícone tocável de um artefato; tocar leva ao item no painel de IA. */
@Composable
fun IconeDoArtefato(artefato: Artefato, aoTocar: (Artefato) -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = { aoTocar(artefato) }, modifier = modifier.size(32.dp)) {
        Icon(
            imageVector = iconeDoArtefato(artefato),
            contentDescription = descreverArtefato(artefato),
            tint = corDoArtefato(artefato),
            modifier = Modifier.size(20.dp),
        )
    }
}
