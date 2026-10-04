package com.allan.imagineer.telas.estatisticas

import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.TempoDoDia
import com.allan.imagineer.rede.TempoDoLivro
import java.time.LocalDate

// As contas das estatísticas de leitura (RL17), em funções puras para serem testadas na JVM.

/** Um dia só conta para a sequência com ao menos **1 minuto** de leitura. */
const val SEGUNDOS_MINIMOS_PARA_CONTAR_O_DIA = 60

/** Com menos de **5 minutos** medidos no livro, o ritmo não é confiável e não se estima o fim. */
const val SEGUNDOS_MINIMOS_PARA_ESTIMAR = 300

/** O resumo de cima da tela. */
data class ResumoDaLeitura(val hoje: Int, val ultimosSeteDias: Int, val sequenciaDeDias: Int, val total: Int)

/**
 * Hoje, os últimos 7 dias (contando hoje), a sequência de dias seguidos e o tempo total. A sequência conta a partir de **hoje**, ou de
 * **ontem** se hoje ainda não chegou a 1 minuto (quem leu todo dia até ontem não perdeu a sequência só porque ainda não abriu o livro hoje).
 */
fun resumirALeitura(dias: List<TempoDoDia>, hoje: LocalDate, totalDosLivros: Int): ResumoDaLeitura {
    val porDia = dias.associate { LocalDate.parse(it.dia) to it.segundos }
    fun lido(dia: LocalDate) = porDia[dia] ?: 0
    val ultimosSete = (0..6).sumOf { lido(hoje.minusDays(it.toLong())) }

    var ponta = if (lido(hoje) >= SEGUNDOS_MINIMOS_PARA_CONTAR_O_DIA) hoje else hoje.minusDays(1)
    var sequencia = 0
    while (lido(ponta) >= SEGUNDOS_MINIMOS_PARA_CONTAR_O_DIA) {
        sequencia++
        ponta = ponta.minusDays(1)
    }
    return ResumoDaLeitura(lido(hoje), ultimosSete, sequencia, totalDosLivros)
}

/** Como um livro aparece na lista: o progresso, o tempo gasto e a estimativa para terminar. */
data class LinhaDoLivro(
    val livroId: Int,
    val titulo: String,
    val capitulosLidos: Int,
    val capitulosAtivos: Int,
    val segundos: Int,
    val diasLidos: Int,
    val estimativa: EstimativaDeTermino,
)

sealed interface EstimativaDeTermino {
    /** Todos os capítulos ativos já foram lidos. */
    data object Terminado : EstimativaDeTermino

    /** Falta medir mais leitura (menos de 5 minutos) ou ainda não há capítulo lido: não se chuta. */
    data object SemDados : EstimativaDeTermino

    /** O que falta, em segundos, ao ritmo medido do próprio livro. */
    data class Faltam(val segundos: Int) : EstimativaDeTermino
}

/**
 * Estima o fim do livro (RL17). O ritmo é **os caracteres dos capítulos lidos pelo tempo medido**; como o app só sabe quantos capítulos
 * foram lidos (e não o tamanho de cada um), os caracteres lidos são **proporcionais** aos capítulos lidos. É uma estimativa, e a tela a
 * chama de "cerca de".
 */
fun estimarOTermino(livro: LivroResumo, segundos: Int): EstimativaDeTermino {
    val ativos = (livro.total_de_capitulos - livro.capitulos_ignorados).coerceAtLeast(0)
    if (ativos > 0 && livro.capitulos_lidos >= ativos) return EstimativaDeTermino.Terminado
    if (ativos == 0 || livro.capitulos_lidos <= 0 || livro.total_de_caracteres <= 0 || segundos < SEGUNDOS_MINIMOS_PARA_ESTIMAR) {
        return EstimativaDeTermino.SemDados
    }
    val lidos = livro.total_de_caracteres.toDouble() * livro.capitulos_lidos / ativos
    val ritmo = lidos / segundos  // caracteres por segundo
    val faltam = (livro.total_de_caracteres - lidos) / ritmo
    return EstimativaDeTermino.Faltam(faltam.toInt())
}

/** As linhas por livro: só os que têm tempo ou capítulo lido, o mais recente primeiro. */
fun linhasPorLivro(livros: List<LivroResumo>, tempos: List<TempoDoLivro>): List<LinhaDoLivro> {
    val porLivro = tempos.associateBy { it.livro_id }
    return livros
        .filter { (porLivro[it.id]?.segundos ?: 0) > 0 || it.capitulos_lidos > 0 }
        .sortedWith(compareByDescending<LivroResumo> { porLivro[it.id]?.ultimo_dia.orEmpty() }.thenBy { it.id })
        .map { livro ->
            val tempo = porLivro[livro.id]
            LinhaDoLivro(
                livroId = livro.id,
                titulo = livro.titulo,
                capitulosLidos = livro.capitulos_lidos,
                capitulosAtivos = (livro.total_de_capitulos - livro.capitulos_ignorados).coerceAtLeast(0),
                segundos = tempo?.segundos ?: 0,
                diasLidos = tempo?.dias_lidos ?: 0,
                estimativa = estimarOTermino(livro, tempo?.segundos ?: 0),
            )
        }
}

/** "menos de 1 min", "23 min", "3 h 05 min". */
fun descreverDuracao(segundos: Int): String {
    val minutos = segundos / 60
    return when {
        segundos < 60 -> "menos de 1 min"
        minutos < 60 -> "$minutos min"
        else -> "${minutos / 60} h ${"%02d".format(minutos % 60)} min"
    }
}

fun descreverEstimativa(estimativa: EstimativaDeTermino): String = when (estimativa) {
    EstimativaDeTermino.Terminado -> "Livro lido"
    EstimativaDeTermino.SemDados -> "Ainda sem dados para estimar o fim"
    is EstimativaDeTermino.Faltam -> "Faltam cerca de ${descreverDuracao(estimativa.segundos)}"
}
