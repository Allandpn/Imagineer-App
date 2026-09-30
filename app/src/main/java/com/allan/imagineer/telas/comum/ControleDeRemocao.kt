package com.allan.imagineer.telas.comum

import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * O diálogo de remover livro (item 7.3a, incrementos 4 e 7). Um de quatro estados.
 * É uma camada por cima da tela: o que estava atrás continua visível.
 */
sealed interface EstadoDaRemocao {
    /** Nenhum diálogo aberto. */
    data object Nenhuma : EstadoDaRemocao

    /** Perguntando "tem certeza?". */
    data class Confirmando(val livro: LivroResumo) : EstadoDaRemocao

    /** A chamada está no ar. Os botões ficam desabilitados, para o duplo toque não disparar dois `DELETE`. */
    data class Removendo(val livro: LivroResumo) : EstadoDaRemocao

    /** Falhou. O diálogo não fecha sozinho: o usuário precisa ver o [motivo]. */
    data class Falhou(val livro: LivroResumo, val motivo: String) : EstadoDaRemocao
}

/**
 * A máquina de estados de "remover um livro", usada pela Biblioteca **e** pela tela
 * de Livro. Fica num lugar só para as duas não divergirem: escrita duas vezes, uma
 * delas acabaria com um `404` tratado de um jeito e a outra de outro.
 *
 * @param aoRemovido o que a tela quer fazer depois de um sucesso (a Biblioteca
 * recarrega a lista; a tela de Livro volta para a Biblioteca).
 */
class ControleDeRemocao(
    private val repositorio: RepositorioDeLivros,
    private val escopo: CoroutineScope,
    private val aoRemovido: () -> Unit,
) {
    private val _estado = MutableStateFlow<EstadoDaRemocao>(EstadoDaRemocao.Nenhuma)
    val estado: StateFlow<EstadoDaRemocao> = _estado.asStateFlow()

    /** O usuário escolheu "Remover": abre o diálogo de confirmação. */
    fun pedir(livro: LivroResumo) {
        _estado.value = EstadoDaRemocao.Confirmando(livro)
    }

    /** "Cancelar" ou "Fechar": fecha o diálogo sem remover nada. */
    fun cancelar() {
        // Com a chamada no ar, o diálogo não pode ser fechado: a remoção já foi pedida.
        if (_estado.value is EstadoDaRemocao.Removendo) return
        _estado.value = EstadoDaRemocao.Nenhuma
    }

    /**
     * "Remover" (ou "Tentar de novo", depois de uma falha): apaga o livro no
     * servidor. Se der certo, fecha o diálogo e chama [aoRemovido]; se falhar, mantém
     * o diálogo aberto mostrando o motivo.
     */
    fun confirmar() {
        val livro = when (val atual = _estado.value) {
            is EstadoDaRemocao.Confirmando -> atual.livro
            is EstadoDaRemocao.Falhou -> atual.livro
            // Já removendo (duplo toque) ou sem diálogo: nada a fazer.
            else -> return
        }

        _estado.value = EstadoDaRemocao.Removendo(livro)
        escopo.launch {
            when (val resultado = repositorio.removerLivro(livro.id)) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.value = EstadoDaRemocao.Nenhuma
                    aoRemovido()
                }
                is ResultadoDaChamada.Falha ->
                    _estado.value = EstadoDaRemocao.Falhou(livro, resultado.motivo)
            }
        }
    }
}
