package com.allan.imagineer.telas.livro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.CapituloAjuste
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.RepositorioDeCapitulos
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Os três estados da tela de Livro (item 7.5a, incremento 6). */
sealed interface EstadoDoLivro {
    data object Carregando : EstadoDoLivro

    /**
     * O livro está na tela. [ajustando] são os ids dos capítulos cujo interruptor
     * está esperando o servidor — ficam desabilitados até a resposta chegar.
     */
    data class Pronto(
        val livro: LivroDetalhe,
        val ajustando: Set<Int> = emptySet(),
    ) : EstadoDoLivro

    /** [motivo] já está escrito para o usuário ler. */
    data class Erro(val motivo: String) : EstadoDoLivro
}

/**
 * Devolve o livro com um capítulo trocado pela versão nova do servidor, e a
 * contagem de ignorados **recalculada a partir da própria lista** — assim o
 * cabeçalho nunca discorda das linhas. Função pura, para testar sem ViewModel.
 */
fun LivroDetalhe.comCapituloAtualizado(novo: CapituloResumo): LivroDetalhe {
    val capitulosNovos = capitulos.map { if (it.id == novo.id) novo else it }
    return copy(
        capitulos = capitulosNovos,
        capitulos_ignorados = capitulosNovos.count { it.ignorado },
    )
}

/**
 * A lógica da tela de Livro (item 7.4): carregar o livro e alternar o "ignorado"
 * de cada capítulo. Não sabe nada de rede — só fala com os repositórios.
 */
class LivroViewModel(
    private val livroId: Int,
    private val livros: RepositorioDeLivros,
    private val capitulos: RepositorioDeCapitulos,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoDoLivro>(EstadoDoLivro.Carregando)
    val estado: StateFlow<EstadoDoLivro> = _estado.asStateFlow()

    private val _avisos = Channel<String>(Channel.BUFFERED)

    /**
     * Mensagens de uso único (falha ao alternar), mostradas num Snackbar.
     *
     * É um **evento**, e não um estado, de propósito: com o servidor fora do ar toda
     * falha tem a mesma mensagem, e um `StateFlow` não emite quando o valor novo é
     * igual ao atual — os toques seguintes ao primeiro ficariam sem nenhum retorno.
     * Um canal entrega cada falha, mesmo repetida.
     */
    val avisos: Flow<String> = _avisos.receiveAsFlow()

    private var carregamentoEmAndamento: Job? = null

    /**
     * Conta as alterações confirmadas pelo servidor. Serve para uma recarga que
     * estava no ar **antes** de uma alteração não sobrescrever, ao chegar, o que o
     * usuário acabou de mudar: a resposta velha traria o interruptor no estado antigo.
     */
    private var alteracoesConfirmadas = 0

    /**
     * Busca o livro. A tela chama isto toda vez que fica visível (ao voltar de um
     * capítulo, `sugestoes_pendentes` pode ter mudado). Com o livro já na tela, a
     * recarga é silenciosa; nos demais casos, mostra o indicador de carregando.
     */
    fun carregar() {
        carregamentoEmAndamento?.cancel()
        if (_estado.value !is EstadoDoLivro.Pronto) _estado.value = EstadoDoLivro.Carregando

        val alteracoesAntes = alteracoesConfirmadas
        carregamentoEmAndamento = viewModelScope.launch {
            when (val resultado = livros.abrirLivro(livroId)) {
                is ResultadoDaChamada.Sucesso -> {
                    // Houve alteração enquanto esta busca estava no ar: o que veio é velho.
                    if (alteracoesConfirmadas != alteracoesAntes) return@launch
                    val ajustando = (_estado.value as? EstadoDoLivro.Pronto)?.ajustando.orEmpty()
                    _estado.value = EstadoDoLivro.Pronto(resultado.dado, ajustando)
                }
                is ResultadoDaChamada.Falha -> _estado.value = EstadoDoLivro.Erro(resultado.motivo)
            }
        }
    }

    /**
     * Liga ou desliga o "ignorado" de um capítulo. **Não é otimista**: o interruptor
     * só muda quando o servidor confirma, e enquanto isso fica desabilitado (evita o
     * duplo toque e um estado que pisca e depois volta atrás).
     */
    fun alternarIgnorado(capituloId: Int) {
        val atual = _estado.value as? EstadoDoLivro.Pronto ?: return
        val capitulo = atual.livro.capitulos.firstOrNull { it.id == capituloId } ?: return
        if (capituloId in atual.ajustando) return

        _estado.value = atual.copy(ajustando = atual.ajustando + capituloId)
        viewModelScope.launch {
            val ajuste = CapituloAjuste(ignorado = !capitulo.ignorado)
            when (val resultado = capitulos.ajustarCapitulo(capituloId, ajuste)) {
                is ResultadoDaChamada.Sucesso -> {
                    alteracoesConfirmadas++
                    atualizarSePronto {
                        it.copy(
                            livro = it.livro.comCapituloAtualizado(resultado.dado),
                            ajustando = it.ajustando - capituloId,
                        )
                    }
                }
                is ResultadoDaChamada.Falha -> {
                    atualizarSePronto { it.copy(ajustando = it.ajustando - capituloId) }
                    _avisos.trySend(resultado.motivo)
                }
            }
        }
    }

    private fun atualizarSePronto(transformacao: (EstadoDoLivro.Pronto) -> EstadoDoLivro.Pronto) {
        _estado.update { if (it is EstadoDoLivro.Pronto) transformacao(it) else it }
    }
}
