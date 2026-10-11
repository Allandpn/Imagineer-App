package com.allan.imagineer.telas.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.DicionarioDoServidor
import com.allan.imagineer.rede.PreferenciasDosDicionarios
import com.allan.imagineer.rede.RepositorioDeDicionario
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// A tela "Dicionários" (RL28): a lista que o servidor encontrou, com liga/desliga e a ordem de preferência.

/** Liga ou desliga o dicionário [id]; os outros ficam como estão. */
fun alternarDicionario(lista: List<DicionarioDoServidor>, id: String): List<DicionarioDoServidor> =
    lista.map { if (it.id == id) it.copy(ativo = !it.ativo) else it }

/** Sobe ([passo] = -1) ou desce ([passo] = +1) o dicionário [id] uma posição; no limite da lista, ou com um id desconhecido, não muda nada. */
fun moverDicionario(lista: List<DicionarioDoServidor>, id: String, passo: Int): List<DicionarioDoServidor> {
    val de = lista.indexOfFirst { it.id == id }
    val para = de + passo
    if (de < 0 || para !in lista.indices) return lista
    return lista.toMutableList().also { it.add(para, it.removeAt(de)) }
}

/** O que o servidor recebe: a ordem inteira da lista e os ids dos desligados. */
fun preferenciasDe(lista: List<DicionarioDoServidor>) =
    PreferenciasDosDicionarios(ordem = lista.map { it.id }, desativados = lista.filterNot { it.ativo }.map { it.id })

/** Para que serve o dicionário, em uma linha: os livros em que ele aparece ao tocar numa palavra. */
fun usoDoDicionario(d: DicionarioDoServidor): String {
    val idiomas = d.padrao_para.joinToString(", ") { nomeDoIdioma(it) }
    val onde = if (idiomas.isEmpty()) "só em \"Procurar em todos\"" else "livros em $idiomas"
    return "$onde · ${descreverQuantidade(d.palavras)} palavras"
}

private fun nomeDoIdioma(codigo: String) = when (codigo) {
    "pt" -> "português"
    "en" -> "inglês"
    "es" -> "espanhol"
    else -> codigo
}

private fun descreverQuantidade(n: Int): String = when {
    n >= 1_000_000 -> "%.1f milhão de".format(n / 1_000_000.0).replace('.', ',')
    n >= 1_000 -> "${n / 1_000} mil"
    else -> "$n"
}

sealed interface CargaDosDicionarios {
    data object Carregando : CargaDosDicionarios
    data class Pronta(val dicionarios: List<DicionarioDoServidor>) : CargaDosDicionarios
    data class Erro(val motivo: String) : CargaDosDicionarios
}

data class EstadoDosDicionarios(val carga: CargaDosDicionarios = CargaDosDicionarios.Carregando, val aviso: String? = null)

class DicionariosViewModel(private val repositorio: RepositorioDeDicionario) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDosDicionarios())
    val estado: StateFlow<EstadoDosDicionarios> = _estado.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            val carga = when (val r = repositorio.listar()) {
                is ResultadoDaChamada.Sucesso -> CargaDosDicionarios.Pronta(r.dado)
                is ResultadoDaChamada.Falha -> CargaDosDicionarios.Erro(r.motivo)
            }
            _estado.value = _estado.value.copy(carga = carga)
        }
    }

    fun tentarDeNovo() {
        _estado.value = _estado.value.copy(carga = CargaDosDicionarios.Carregando)
        carregar()
    }

    fun alternar(id: String) = mudar { alternarDicionario(it, id) }

    fun subir(id: String) = mudar { moverDicionario(it, id, -1) }

    fun descer(id: String) = mudar { moverDicionario(it, id, +1) }

    /** Soltar a linha arrastada (CF2): leva o dicionário [id] para a posição [alvo] e grava, como as outras mudanças. */
    fun moverPara(id: String, alvo: Int) = mudar { moverDicionarioPara(it, id, alvo) }

    /**
     * Grava **a cada toque** (RL30): a tela já mostra a lista nova, o servidor confirma; se falhar, volta ao que estava e avisa.
     */
    private fun mudar(transformar: (List<DicionarioDoServidor>) -> List<DicionarioDoServidor>) {
        val antes = (_estado.value.carga as? CargaDosDicionarios.Pronta)?.dicionarios ?: return
        val depois = transformar(antes)
        if (depois == antes) return
        _estado.value = _estado.value.copy(carga = CargaDosDicionarios.Pronta(depois))
        viewModelScope.launch {
            when (val r = repositorio.gravarPreferencias(preferenciasDe(depois))) {
                is ResultadoDaChamada.Sucesso -> _estado.value = _estado.value.copy(carga = CargaDosDicionarios.Pronta(r.dado))
                is ResultadoDaChamada.Falha -> _estado.value = _estado.value.copy(
                    carga = CargaDosDicionarios.Pronta(antes),
                    aviso = "Não consegui salvar: ${r.motivo}",
                )
            }
        }
    }

    fun avisoLido() {
        _estado.value = _estado.value.copy(aviso = null)
    }
}

// ---------------------------------------------------------------------------------------------------------------- //
// Ordenar por arrastar (item 7.5g, CF2)
// ---------------------------------------------------------------------------------------------------------------- //

/** Leva o dicionário [id] para a posição [alvo] (0 = o primeiro), empurrando os outros; id desconhecido, posição fora da lista ou a mesma não mudam nada. */
fun moverDicionarioPara(lista: List<DicionarioDoServidor>, id: String, alvo: Int): List<DicionarioDoServidor> {
    val de = lista.indexOfFirst { it.id == id }
    if (de < 0 || alvo !in lista.indices || alvo == de) return lista
    return lista.toMutableList().also { it.add(alvo, it.removeAt(de)) }
}

/** Onde uma linha está na tela durante o arrastar: o id, o começo (px) e a altura (px). */
data class LinhaNaTela(val id: String, val inicio: Int, val altura: Int)

/**
 * Para que posição o dicionário [arrastado] foi? Soma o [deslocamento] (px, para baixo é positivo) ao meio da linha dele e vê **sobre qual
 * linha** esse ponto caiu. `null` quando a linha arrastada não está na tela; passando do fim ou do começo, vale a última ou a primeira linha.
 */
fun alvoDoArrasto(linhas: List<LinhaNaTela>, arrastado: String, deslocamento: Float): String? {
    val dele = linhas.firstOrNull { it.id == arrastado } ?: return null
    val ponto = dele.inicio + dele.altura / 2f + deslocamento
    val primeira = linhas.minByOrNull { it.inicio } ?: return null
    val ultima = linhas.maxByOrNull { it.inicio } ?: return null
    return when {
        ponto <= primeira.inicio -> primeira.id
        ponto >= ultima.inicio + ultima.altura -> ultima.id
        else -> linhas.first { ponto >= it.inicio && ponto < it.inicio + it.altura }.id
    }
}
