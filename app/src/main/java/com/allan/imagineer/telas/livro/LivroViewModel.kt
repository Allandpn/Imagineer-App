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
 * Um aviso temporário no rodapé (Snackbar). É um **evento com dados**, e não só uma
 * frase: quem o exibe sabe, sem consultar a lista, quais capítulos restaurar se o
 * usuário tocar em "Desfazer".
 *
 * @property desfazerCapitulos os ids que o "Desfazer" restaura — **exatamente** os do
 * lote que gerou o aviso —; vazia se o aviso não tem ação (uma falha, por exemplo).
 */
data class Aviso(
    val texto: String,
    val desfazerCapitulos: List<Int> = emptyList(),
)

/** O que a seleção em lote faz com os capítulos marcados (item 7.5a, segunda revisão do incremento 6). */
enum class ModoDeSelecao {
    /** Na lista principal: os capítulos ativos marcados serão arquivados. */
    ARQUIVAR,

    /** Na área de arquivados: os capítulos arquivados marcados serão restaurados. */
    RESTAURAR,
}

/**
 * A seleção em andamento. `null` no ViewModel = fora do modo de seleção.
 *
 * @property ids os capítulos marcados.
 * @property executando o lote está no ar: nada mais pode ser marcado nem cancelado.
 */
data class Selecao(
    val modo: ModoDeSelecao,
    val ids: Set<Int> = emptySet(),
    val executando: Boolean = false,
)

/** O que um lote conseguiu fazer. */
private data class ResultadoDoLote(
    /** Os capítulos que o servidor confirmou, na ordem em que foram processados. */
    val concluidos: List<CapituloResumo>,
    /** Os que ficaram sem processar (o que falhou e os que vinham depois dele). */
    val restantes: List<Int>,
    /** O motivo da falha que interrompeu o lote, ou `null` se tudo deu certo. */
    val motivo: String?,
    /** Quantos capítulos o lote tentou (os que já estavam no estado pedido não contam). */
    val total: Int,
    /**
     * Os nomes para exibir, tirados da **lista** (e não da resposta do servidor): uma
     * frase da tela não deve depender do formato de uma resposta de rede.
     */
    val nomes: Map<Int, String> = emptyMap(),
)

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
 * A lógica da tela de Livro (item 7.4): carregar o livro, arquivar e restaurar
 * capítulos, editar os dados, escolher o perfil padrão e apagar o livro. Não sabe
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

    private val _avisos = Channel<Aviso>(Channel.BUFFERED)

    /**
     * Avisos de uso único — falha ao arquivar/restaurar, ou "Arquivado: ..." com
     * a ação Desfazer —, mostrados num Snackbar.
     *
     * É um **evento**, e não um estado, de propósito: com o servidor fora do ar toda
     * falha tem a mesma mensagem, e um `StateFlow` não emite quando o valor novo é
     * igual ao atual — os toques seguintes ao primeiro ficariam sem nenhum retorno.
     * Um canal entrega cada falha, mesmo repetida.
     */
    val avisos: Flow<Aviso> = _avisos.receiveAsFlow()

    private val _livroRemovido = Channel<Unit>(Channel.BUFFERED)

    /** Evento de uso único: o livro foi apagado, e a tela não tem mais o que mostrar. */
    val livroRemovido: Flow<Unit> = _livroRemovido.receiveAsFlow()

    // A máquina de estados de "remover livro" é a mesma da Biblioteca (compartilhada).
    private val controleDeRemocao = ControleDeRemocao(livros, viewModelScope) {
        _livroRemovido.trySend(Unit)
    }
    val remocao: StateFlow<EstadoDaRemocao> = controleDeRemocao.estado

    private val _selecao = MutableStateFlow<Selecao?>(null)

    /** A seleção em lote em andamento; `null` fora do modo de seleção. */
    val selecao: StateFlow<Selecao?> = _selecao.asStateFlow()

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
     * recarga é silenciosa — e, se falhar, o livro fica e um aviso diz que não deu para
     * atualizar; nos demais casos, mostra o indicador de carregando, e a falha vira Erro.
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
                    podarSelecao(livro)
                    if (perfil == null) buscarNomeDoPerfil(livro)
                }
                is ResultadoDaChamada.Falha ->
                    // 404: o livro não existe mais (apagado por outro caminho), então não há
                    // o que preservar — vai para Erro, com o motivo da API.
                    if (_estado.value is EstadoDoLivro.Pronto && resultado.codigoHttp != 404) {
                        // Recarga silenciosa que falhou (item 7.5a): o livro que já está na
                        // tela continua valendo — trocá-lo por Erro perderia a seleção em lote,
                        // as chamadas em andamento e os diálogos, por causa de um detalhe.
                        _avisos.trySend(Aviso("Não consegui atualizar o livro."))
                    } else {
                        _estado.value = EstadoDoLivro.Erro(resultado.motivo)
                    }
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
    // Arquivar e restaurar capítulos — um a um ou em lote por seleção
    // ------------------------------------------------------------------ //

    /**
     * Arquiva um capítulo: ele sai da lista principal e vai para a área de arquivados
     * (na API, `ignorado = true`). É um lote de um só.
     */
    fun arquivar(capituloId: Int) {
        viewModelScope.launch { executarLote(listOf(capituloId), arquivar = true) }
    }

    /** Restaura um capítulo arquivado: volta para a lista principal (`ignorado = false`). */
    fun restaurar(capituloId: Int) {
        viewModelScope.launch { executarLote(listOf(capituloId), arquivar = false) }
    }

    /**
     * O "Desfazer" do aviso de arquivamento: restaura exatamente os capítulos daquele
     * lote. Segue o mesmo caminho de restaurar — e, como restaurar não avisa quando dá
     * certo, não gera outro aviso (senão restaurar ofereceria desfazer, e assim por diante).
     */
    fun desfazerArquivamento(capitulosIds: List<Int>) {
        viewModelScope.launch { executarLote(capitulosIds, arquivar = false) }
    }

    // --- modo de seleção ---

    /**
     * Entra no modo de seleção: pelo botão do topo (sem [idInicial]) ou pelo toque
     * longo numa linha (que já a marca). Só vale se há capítulos elegíveis nessa tela,
     * e o [idInicial], quando dado, tem de ser um deles.
     */
    fun iniciarSelecao(modo: ModoDeSelecao, idInicial: Int? = null) {
        val pronto = _estado.value as? EstadoDoLivro.Pronto ?: return
        if (_selecao.value != null) return
        val elegiveis = elegiveisPara(modo, pronto.livro)
        if (elegiveis.isEmpty()) return
        if (idInicial != null && idInicial !in elegiveis) return
        _selecao.value = Selecao(modo, setOfNotNull(idInicial))
    }

    /**
     * Marca ou desmarca um capítulo. Só se marca o que o modo permite (ativos ao
     * arquivar, arquivados ao restaurar), e nunca um capítulo cuja chamada está em
     * andamento. Desmarcar o último **não** sai do modo — sai-se por cancelar ou confirmar.
     */
    fun alternarSelecao(capituloId: Int) {
        val selecao = _selecao.value ?: return
        val pronto = _estado.value as? EstadoDoLivro.Pronto ?: return
        if (selecao.executando) return
        if (capituloId !in elegiveisPara(selecao.modo, pronto.livro)) return
        if (capituloId in pronto.ajustando) return

        val novos = if (capituloId in selecao.ids) selecao.ids - capituloId else selecao.ids + capituloId
        _selecao.value = selecao.copy(ids = novos)
    }

    /**
     * "Selecionar todos" / "Desmarcar todos": um botão que **alterna** (R18). Marca todos os
     * capítulos elegíveis do modo — menos os que têm chamada em andamento, que nunca podem ser
     * marcados (R5). Se todos já estão marcados, desmarca tudo; continua no modo (R2).
     *
     * Não confirma nada: o usuário ainda precisa tocar em "Arquivar (N)" / "Restaurar (N)".
     */
    fun alternarTodos() {
        val selecao = _selecao.value ?: return
        val pronto = _estado.value as? EstadoDoLivro.Pronto ?: return
        if (selecao.executando) return

        val elegiveis = elegiveisPara(selecao.modo, pronto.livro) - pronto.ajustando
        val todosMarcados = elegiveis.isNotEmpty() && selecao.ids.containsAll(elegiveis)
        _selecao.value = selecao.copy(ids = if (todosMarcados) emptySet() else elegiveis)
    }

    /** Cancelar, ou o botão voltar do aparelho: sai do modo sem fazer nada. */
    fun cancelarSelecao() {
        if (_selecao.value?.executando == true) return
        _selecao.value = null
    }

    /**
     * O botão "Arquivar (N)" / "Restaurar (N)": **é a confirmação**, sem diálogo extra.
     * Não faz nada com zero marcados.
     *
     * Roda o lote (uma chamada por capítulo, em sequência). Se tudo dá certo, sai do
     * modo; se algo falha, os capítulos que sobraram **continuam marcados**, para tentar
     * de novo com o mesmo botão.
     */
    fun confirmarSelecao() {
        val selecao = _selecao.value ?: return
        val pronto = _estado.value as? EstadoDoLivro.Pronto ?: return
        if (selecao.executando || selecao.ids.isEmpty()) return

        // Na ordem da lista (a do livro), e não na ordem em que o usuário marcou.
        val ids = pronto.livro.capitulos.filter { it.id in selecao.ids }.map { it.id }
        _selecao.value = selecao.copy(executando = true)
        viewModelScope.launch {
            val resultado = executarLote(ids, arquivar = selecao.modo == ModoDeSelecao.ARQUIVAR)
            _selecao.value = if (resultado.motivo == null) {
                null
            } else {
                Selecao(selecao.modo, resultado.restantes.toSet())
            }
        }
    }

    // --- o lote em si ---

    /**
     * O coração das regras de lote (item 7.5a, segunda revisão do incremento 6):
     *
     * - **Uma chamada por capítulo, em sequência**: não existe rota em lote.
     * - **Não é otimista**: cada capítulo só troca de lista quando o servidor confirma
     *   o dele; os demais mostram progresso enquanto esperam a vez.
     * - **Para no primeiro erro.** Com o servidor fora do ar, tentar os N capítulos
     *   levaria N *timeouts* de 10 s para dar o mesmo erro N vezes. O que já deu certo
     *   fica; o resto continua marcado.
     * - Capítulo que já está no estado pedido, ou que já tem uma chamada em andamento,
     *   é ignorado — não gasta chamada.
     *
     * Depois, avisa: ver [avisoDoLote].
     */
    private suspend fun executarLote(ids: List<Int>, arquivar: Boolean): ResultadoDoLote {
        val pronto = _estado.value as? EstadoDoLivro.Pronto
            ?: return ResultadoDoLote(emptyList(), emptyList(), null, 0)
        val alvos = ids.filter { id ->
            val capitulo = pronto.livro.capitulos.firstOrNull { it.id == id }
            capitulo != null && capitulo.ignorado != arquivar && id !in pronto.ajustando
        }
        if (alvos.isEmpty()) return ResultadoDoLote(emptyList(), emptyList(), null, 0)

        atualizarSePronto { it.copy(ajustando = it.ajustando + alvos) }

        val concluidos = mutableListOf<CapituloResumo>()
        var restantes = emptyList<Int>()
        var motivo: String? = null

        for ((indice, id) in alvos.withIndex()) {
            when (val resultado = capitulos.ajustarCapitulo(id, CapituloAjuste(ignorado = arquivar))) {
                is ResultadoDaChamada.Sucesso -> {
                    alteracoesConfirmadas++
                    concluidos += resultado.dado
                    atualizarSePronto {
                        it.copy(
                            livro = it.livro.comCapituloAtualizado(resultado.dado),
                            ajustando = it.ajustando - id,
                        )
                    }
                }
                is ResultadoDaChamada.Falha -> {
                    motivo = resultado.motivo
                    restantes = alvos.drop(indice)
                    break
                }
            }
        }
        // O que não chegou a ser tentado deixa de estar "em andamento".
        if (restantes.isNotEmpty()) atualizarSePronto { it.copy(ajustando = it.ajustando - restantes.toSet()) }

        val nomes = pronto.livro.capitulos.associate { it.id to tituloDoCapitulo(it.titulo, it.ordem) }
        val resultado = ResultadoDoLote(concluidos, restantes, motivo, alvos.size, nomes)
        avisoDoLote(arquivar, resultado)?.let { _avisos.trySend(it) }
        return resultado
    }

    /**
     * O aviso que um lote gera — ou `null` se não há o que dizer.
     *
     * - **Arquivar, tudo certo:** "Arquivado: `<nome>`" (um) ou "N capítulos arquivados"
     *   (vários), com Desfazer para **exatamente** os do lote.
     * - **Arquivar, falhou no meio:** "K de N arquivados. `<motivo>`", com Desfazer para
     *   os K que deram certo. Se nenhum deu certo, só o motivo.
     * - **Restaurar:** só falhas geram aviso. Os capítulos somem da área de arquivados,
     *   que é retorno suficiente.
     */
    private fun avisoDoLote(arquivar: Boolean, r: ResultadoDoLote): Aviso? {
        val idsConcluidos = r.concluidos.map { it.id }
        return when {
            r.motivo != null && r.concluidos.isEmpty() -> Aviso(r.motivo)
            r.motivo != null -> Aviso(
                "${r.concluidos.size} de ${r.total} ${if (arquivar) "arquivados" else "restaurados"}. ${r.motivo}",
                desfazerCapitulos = if (arquivar) idsConcluidos else emptyList(),
            )
            !arquivar || r.concluidos.isEmpty() -> null
            r.concluidos.size == 1 -> {
                val unico = r.concluidos.single()
                Aviso("Arquivado: ${r.nomes[unico.id]}", desfazerCapitulos = idsConcluidos)
            }
            else -> Aviso("${r.concluidos.size} capítulos arquivados", desfazerCapitulos = idsConcluidos)
        }
    }

    /** Os capítulos que o modo permite marcar: ativos ao arquivar, arquivados ao restaurar. */
    private fun elegiveisPara(modo: ModoDeSelecao, livro: LivroDetalhe): Set<Int> =
        livro.capitulos
            .filter { if (modo == ModoDeSelecao.ARQUIVAR) !it.ignorado else it.ignorado }
            .map { it.id }
            .toSet()

    /**
     * Quando a lista recarrega, desmarca o que deixou de existir ou de ser elegível
     * (arquivado por outro caminho, removido) — em vez de gerar uma chamada que falharia.
     */
    private fun podarSelecao(livro: LivroDetalhe) {
        _selecao.update { selecao ->
            when {
                selecao == null || selecao.executando -> selecao
                else -> selecao.copy(ids = selecao.ids.intersect(elegiveisPara(selecao.modo, livro)))
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
