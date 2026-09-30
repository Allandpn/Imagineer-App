package com.allan.imagineer.telas.livro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.CapituloAjuste
import com.allan.imagineer.rede.CapituloResumo
import com.allan.imagineer.rede.LivroAjuste
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.PerfilRenderizacao
import com.allan.imagineer.rede.RepositorioDeCapitulos
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.RepositorioDePerfis
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.comum.ControleDeRemocao
import com.allan.imagineer.telas.comum.EstadoDaRemocao
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
     * O livro está na tela.
     *
     * @property ajustando os ids dos capítulos cujo interruptor está esperando o servidor.
     * @property perfil o perfil padrão do livro, **com o nome** — buscado à parte
     * (`GET /perfis-renderizacao/{id}`) e por isso nulo enquanto não chega ou se a busca
     * falhar; nesse caso a tela cai no "definido" (incremento 7).
     */
    data class Pronto(
        val livro: LivroDetalhe,
        val ajustando: Set<Int> = emptySet(),
        val perfil: PerfilRenderizacao? = null,
    ) : EstadoDoLivro

    /** [motivo] já está escrito para o usuário ler. */
    data class Erro(val motivo: String) : EstadoDoLivro
}

/** O diálogo de editar título, autor e idioma (incremento 7). */
sealed interface EstadoDaEdicao {
    data object Nenhuma : EstadoDaEdicao

    /** [erro] fica dentro do diálogo, que continua aberto com o que foi digitado. */
    data class Editando(val salvando: Boolean = false, val erro: String? = null) : EstadoDaEdicao
}

/** O diálogo de escolher o perfil padrão (incremento 7). */
sealed interface EstadoDaEscolhaDePerfil {
    data object Nenhuma : EstadoDaEscolhaDePerfil

    data object Carregando : EstadoDaEscolhaDePerfil

    /** Os perfis existentes. Escolher grava na hora — a escolha é o gesto, não há "Salvar". */
    data class Lista(
        val perfis: List<PerfilRenderizacao>,
        val salvando: Boolean = false,
        val erro: String? = null,
    ) : EstadoDaEscolhaDePerfil

    data class Falhou(val motivo: String) : EstadoDaEscolhaDePerfil
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

/** O livro sem a lista de capítulos, no formato que o diálogo de remoção espera. */
fun LivroDetalhe.comoResumo() = LivroResumo(
    id = id,
    titulo = titulo,
    autor = autor,
    idioma = idioma,
    nome_arquivo = nome_arquivo,
    data_importacao = data_importacao,
    total_de_capitulos = total_de_capitulos,
    capitulos_ignorados = capitulos_ignorados,
)

/**
 * A lógica da tela de Livro (item 7.4): carregar o livro, alternar o "ignorado" de
 * cada capítulo, editar os dados, escolher o perfil padrão e apagar o livro. Não sabe
 * nada de rede — só fala com os repositórios.
 */
class LivroViewModel(
    private val livroId: Int,
    private val livros: RepositorioDeLivros,
    private val capitulos: RepositorioDeCapitulos,
    private val perfis: RepositorioDePerfis,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoDoLivro>(EstadoDoLivro.Carregando)
    val estado: StateFlow<EstadoDoLivro> = _estado.asStateFlow()

    private val _edicao = MutableStateFlow<EstadoDaEdicao>(EstadoDaEdicao.Nenhuma)
    val edicao: StateFlow<EstadoDaEdicao> = _edicao.asStateFlow()

    private val _escolhaDePerfil = MutableStateFlow<EstadoDaEscolhaDePerfil>(EstadoDaEscolhaDePerfil.Nenhuma)
    val escolhaDePerfil: StateFlow<EstadoDaEscolhaDePerfil> = _escolhaDePerfil.asStateFlow()

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

    private val _livroRemovido = Channel<Unit>(Channel.BUFFERED)

    /** Evento de uso único: o livro foi apagado, e a tela não tem mais o que mostrar. */
    val livroRemovido: Flow<Unit> = _livroRemovido.receiveAsFlow()

    // A máquina de estados de "remover livro" é a mesma da Biblioteca (compartilhada).
    private val controleDeRemocao = ControleDeRemocao(livros, viewModelScope) {
        _livroRemovido.trySend(Unit)
    }
    val remocao: StateFlow<EstadoDaRemocao> = controleDeRemocao.estado

    private var carregamentoEmAndamento: Job? = null

    /**
     * Conta as alterações confirmadas pelo servidor. Serve para uma recarga que
     * estava no ar **antes** de uma alteração não sobrescrever, ao chegar, o que o
     * usuário acabou de mudar: a resposta velha traria o interruptor no estado antigo.
     */
    private var alteracoesConfirmadas = 0

    // ------------------------------------------------------------------ //
    // Carregar
    // ------------------------------------------------------------------ //

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
                    val livro = resultado.dado
                    val anterior = _estado.value as? EstadoDoLivro.Pronto
                    // O perfil já conhecido serve se ainda é o mesmo; senão, busca de novo.
                    val perfil = anterior?.perfil?.takeIf { it.id == livro.perfil_renderizacao_padrao_id }
                    _estado.value = EstadoDoLivro.Pronto(livro, anterior?.ajustando.orEmpty(), perfil)
                    if (perfil == null) buscarNomeDoPerfil(livro)
                }
                is ResultadoDaChamada.Falha -> _estado.value = EstadoDoLivro.Erro(resultado.motivo)
            }
        }
    }

    /**
     * Busca o perfil padrão para mostrar o **nome**. *Best-effort*: se falhar, a tela
     * fica no "definido" — não vale derrubar a tela por um detalhe de cabeçalho.
     */
    private suspend fun buscarNomeDoPerfil(livro: LivroDetalhe) {
        val perfilId = livro.perfil_renderizacao_padrao_id ?: return
        val resultado = perfis.abrirPerfil(perfilId) as? ResultadoDaChamada.Sucesso ?: return
        atualizarSePronto {
            // Só vale se o livro na tela ainda aponta para este perfil.
            if (it.livro.perfil_renderizacao_padrao_id == perfilId) it.copy(perfil = resultado.dado) else it
        }
    }

    // ------------------------------------------------------------------ //
    // Ignorar capítulo
    // ------------------------------------------------------------------ //

    /**
     * Liga ou desliga o "ignorado" de um capítulo. **Não é otimista**: o interruptor
     * só muda quando o servidor confirma, e enquanto isso é trocado por um indicador
     * de progresso (evita o duplo toque e um estado que pisca e depois volta atrás).
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

    // ------------------------------------------------------------------ //
    // Editar título, autor e idioma
    // ------------------------------------------------------------------ //

    fun abrirEdicao() {
        if (_estado.value is EstadoDoLivro.Pronto) _edicao.value = EstadoDaEdicao.Editando()
    }

    fun cancelarEdicao() {
        if ((_edicao.value as? EstadoDaEdicao.Editando)?.salvando == true) return
        _edicao.value = EstadoDaEdicao.Nenhuma
    }

    /**
     * "Salvar" no diálogo de edição.
     *
     * - **Título e autor são mandatórios**: em branco bloqueia. No autor isso protege
     *   de um detalhe do backend — `autor: ""` não é nulo para ele.
     * - **Idioma é opcional**: em branco limpa (manda `null` explícito) se havia um.
     * - **Só o que mudou é enviado.** Mandar `titulo` o *confirma* no backend, e um
     *   título que o usuário nem tocou não deve ser confirmado por tabela — a não ser
     *   que ele esteja pendente, e então confirmar é justamente o que se quer.
     * - Nada mudou: só fecha, sem chamada.
     */
    fun salvarEdicao(titulo: String, autor: String, idioma: String) {
        val pronto = _estado.value as? EstadoDoLivro.Pronto ?: return
        val edicao = _edicao.value as? EstadoDaEdicao.Editando ?: return
        if (edicao.salvando) return

        val livro = pronto.livro
        val novoTitulo = titulo.trim()
        val novoAutor = autor.trim()
        val novoIdioma = idioma.trim()

        val vazios = buildList {
            if (novoTitulo.isEmpty()) add("título")
            if (novoAutor.isEmpty()) add("autor")
        }
        if (vazios.isNotEmpty()) {
            _edicao.value = EstadoDaEdicao.Editando(erro = "Preencha: ${vazios.joinToString(" e ")}.")
            return
        }

        val mudouTitulo = novoTitulo != livro.titulo || "titulo" in livro.metadados_pendentes
        val mudouAutor = novoAutor != livro.autor.orEmpty()
        val mudouIdioma = novoIdioma != livro.idioma.orEmpty()

        if (!mudouTitulo && !mudouAutor && !mudouIdioma) {
            _edicao.value = EstadoDaEdicao.Nenhuma
            return
        }

        val ajuste = LivroAjuste(
            titulo = novoTitulo.takeIf { mudouTitulo },
            autor = novoAutor.takeIf { mudouAutor },
            idioma = novoIdioma.takeIf { mudouIdioma && it.isNotEmpty() },
            limparIdioma = mudouIdioma && novoIdioma.isEmpty(),
        )

        _edicao.value = EstadoDaEdicao.Editando(salvando = true)
        viewModelScope.launch {
            when (val resultado = livros.ajustarLivro(livro.id, ajuste)) {
                is ResultadoDaChamada.Sucesso -> {
                    aplicarLivro(resultado.dado, perfilConhecido = null)
                    _edicao.value = EstadoDaEdicao.Nenhuma
                }
                is ResultadoDaChamada.Falha ->
                    _edicao.value = EstadoDaEdicao.Editando(erro = resultado.motivo)
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Perfil padrão
    // ------------------------------------------------------------------ //

    /** Abre o diálogo e busca a lista de perfis. */
    fun abrirEscolhaDePerfil() {
        if (_estado.value !is EstadoDoLivro.Pronto) return
        _escolhaDePerfil.value = EstadoDaEscolhaDePerfil.Carregando
        viewModelScope.launch {
            _escolhaDePerfil.value = when (val resultado = perfis.listarPerfis()) {
                is ResultadoDaChamada.Sucesso -> EstadoDaEscolhaDePerfil.Lista(resultado.dado)
                is ResultadoDaChamada.Falha -> EstadoDaEscolhaDePerfil.Falhou(resultado.motivo)
            }
        }
    }

    fun fecharEscolhaDePerfil() {
        if ((_escolhaDePerfil.value as? EstadoDaEscolhaDePerfil.Lista)?.salvando == true) return
        _escolhaDePerfil.value = EstadoDaEscolhaDePerfil.Nenhuma
    }

    /**
     * Escolhe o perfil padrão e grava na hora. [perfil] nulo é "Nenhum": manda `null`
     * explícito, que limpa. Escolher o que já está escolhido só fecha o diálogo.
     */
    fun escolherPerfil(perfil: PerfilRenderizacao?) {
        val pronto = _estado.value as? EstadoDoLivro.Pronto ?: return
        val lista = _escolhaDePerfil.value as? EstadoDaEscolhaDePerfil.Lista ?: return
        if (lista.salvando) return

        if (perfil?.id == pronto.livro.perfil_renderizacao_padrao_id) {
            _escolhaDePerfil.value = EstadoDaEscolhaDePerfil.Nenhuma
            return
        }

        val ajuste = if (perfil == null) {
            LivroAjuste(limparPerfilPadrao = true)
        } else {
            LivroAjuste(perfil_renderizacao_padrao_id = perfil.id)
        }

        _escolhaDePerfil.value = lista.copy(salvando = true, erro = null)
        viewModelScope.launch {
            when (val resultado = livros.ajustarLivro(pronto.livro.id, ajuste)) {
                is ResultadoDaChamada.Sucesso -> {
                    aplicarLivro(resultado.dado, perfilConhecido = perfil)
                    _escolhaDePerfil.value = EstadoDaEscolhaDePerfil.Nenhuma
                }
                is ResultadoDaChamada.Falha ->
                    _escolhaDePerfil.value = lista.copy(salvando = false, erro = resultado.motivo)
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Apagar o livro
    // ------------------------------------------------------------------ //

    fun pedirRemocao() {
        val pronto = _estado.value as? EstadoDoLivro.Pronto ?: return
        controleDeRemocao.pedir(pronto.livro.comoResumo())
    }

    fun cancelarRemocao() = controleDeRemocao.cancelar()

    fun confirmarRemocao() = controleDeRemocao.confirmar()

    // ------------------------------------------------------------------ //
    // Por dentro
    // ------------------------------------------------------------------ //

    /**
     * Troca o livro na tela por uma versão nova que o servidor acabou de confirmar.
     * Conta como alteração confirmada (uma recarga velha, ainda no ar, não a desfaz).
     * Se o perfil padrão mudou e o nome ainda não é conhecido, busca-o.
     */
    private fun aplicarLivro(novo: LivroDetalhe, perfilConhecido: PerfilRenderizacao?) {
        alteracoesConfirmadas++
        atualizarSePronto {
            val perfil = perfilConhecido ?: it.perfil?.takeIf { p -> p.id == novo.perfil_renderizacao_padrao_id }
            it.copy(livro = novo, perfil = perfil)
        }
        val pronto = _estado.value as? EstadoDoLivro.Pronto
        if (novo.perfil_renderizacao_padrao_id != null && pronto?.perfil == null) {
            viewModelScope.launch { buscarNomeDoPerfil(novo) }
        }
    }

    private fun atualizarSePronto(transformacao: (EstadoDoLivro.Pronto) -> EstadoDoLivro.Pronto) {
        _estado.update { if (it is EstadoDoLivro.Pronto) transformacao(it) else it }
    }
}
