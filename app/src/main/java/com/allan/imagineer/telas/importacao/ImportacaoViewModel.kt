package com.allan.imagineer.telas.importacao

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.dados.ArquivoEscolhido
import com.allan.imagineer.dados.LeitorDeArquivos
import com.allan.imagineer.rede.LivroAjuste
import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.LivroResumo
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** O limite de tamanho do servidor (item 6.2): 60 MB. */
const val LIMITE_DO_EPUB_EM_BYTES = 60L * 1024 * 1024

/**
 * Confere o arquivo escolhido **antes de subir qualquer byte** (item 7.3a,
 * incremento 5): descobrir que um PDF não serve, ou que o arquivo passa de 60 MB,
 * só depois de enviá-lo inteiro seria desperdício.
 *
 * O filtro do seletor de arquivos é só uma sugestão — o usuário pode escolher
 * qualquer coisa —, então a extensão é conferida aqui.
 *
 * @return o motivo da recusa, já escrito para o usuário, ou `null` se o arquivo serve.
 */
fun validarArquivo(nome: String, tamanho: Long?): String? = when {
    !nome.endsWith(".epub", ignoreCase = true) -> "Escolha um arquivo .epub."
    tamanho == 0L -> "O arquivo está vazio."
    tamanho != null && tamanho > LIMITE_DO_EPUB_EM_BYTES -> "O arquivo passa do limite de 60 MB."
    else -> null
}

/**
 * Onde a importação está. Cinco estados; a lista da Biblioteca continua visível
 * atrás deles (item 7.3a, incremento 5).
 */
sealed interface EstadoDaImportacao {
    /** Nada em andamento. */
    data object Nenhuma : EstadoDaImportacao

    /** O arquivo está subindo. [total] nulo = o seletor de arquivos não informou o tamanho. */
    data class Enviando(
        val nome: String,
        val enviados: Long,
        val total: Long?,
    ) : EstadoDaImportacao

    /**
     * O servidor já tinha um livro com o mesmo identificador. Aviso, nunca bloqueio
     * (item 3.4a). O livro novo **já foi gravado** — por isso "Remover o novo".
     */
    data class Semelhantes(
        val livro: LivroDetalhe,
        val semelhantes: List<LivroResumo>,
        val removendo: Boolean = false,
        val erro: String? = null,
    ) : EstadoDaImportacao

    /** Título e/ou autor que a extração não achou: o usuário precisa preencher. */
    data class MetadadosPendentes(
        val livro: LivroDetalhe,
        val salvando: Boolean = false,
        val removendo: Boolean = false,
        val erro: String? = null,
    ) : EstadoDaImportacao

    /**
     * Não deu certo. [arquivo] nulo = não adianta tentar de novo (o arquivo em si
     * foi recusado); preenchido = dá para reenviar.
     */
    data class Falhou(
        val motivo: String,
        val arquivo: ArquivoEscolhido? = null,
    ) : EstadoDaImportacao
}

/**
 * A lógica de importar um EPUB (item 7.3): validar, subir, e depois resolver o que
 * o servidor devolve — livros semelhantes, título/autor pendentes.
 *
 * Fica separada do [com.allan.imagineer.telas.biblioteca.BibliotecaViewModel]
 * porque é uma camada por cima da tela, como o diálogo de remoção.
 */
class ImportacaoViewModel(
    private val repositorio: RepositorioDeLivros,
    private val leitor: LeitorDeArquivos,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoDaImportacao>(EstadoDaImportacao.Nenhuma)
    val estado: StateFlow<EstadoDaImportacao> = _estado.asStateFlow()

    private val _irParaLivro = MutableStateFlow<Int?>(null)

    /** Evento de uso único: "abra o livro N". A tela navega e chama [consumirNavegacao]. */
    val irParaLivro: StateFlow<Int?> = _irParaLivro.asStateFlow()

    private val _versaoDaBiblioteca = MutableStateFlow(0)

    /**
     * Sobe a cada mudança no servidor (livro criado, removido ou ajustado). A tela
     * recarrega a lista quando muda — mesmo que o fluxo termine sem navegar (por
     * exemplo, "Remover o novo").
     */
    val versaoDaBiblioteca: StateFlow<Int> = _versaoDaBiblioteca.asStateFlow()

    /**
     * Os ids dos livros que existiam **antes** do primeiro envio. Serve para, numa
     * nova tentativa depois de uma queda de rede, descobrir se o servidor chegou a
     * gravar o livro (ver [tentarDeNovo]). Nulo = não deu para obter a lista.
     */
    private var idsAntes: Set<Int>? = null

    /** O resultado do seletor de arquivos. [uri] nulo = o usuário cancelou. */
    fun escolherArquivo(uri: String?) {
        if (uri == null || _estado.value !is EstadoDaImportacao.Nenhuma) return

        val arquivo = leitor.descrever(uri)
        if (arquivo == null) {
            _estado.value = EstadoDaImportacao.Falhou("Não consegui abrir o arquivo escolhido.")
            return
        }
        val recusa = validarArquivo(arquivo.nome, arquivo.tamanho)
        if (recusa != null) {
            _estado.value = EstadoDaImportacao.Falhou(recusa)
            return
        }

        _estado.value = EstadoDaImportacao.Enviando(arquivo.nome, 0, arquivo.tamanho)
        viewModelScope.launch {
            // Se o servidor está fora do ar, esta chamada falha e o envio também;
            // sem retrato, simplesmente não há como conferir depois.
            idsAntes = (repositorio.listarLivros() as? ResultadoDaChamada.Sucesso)
                ?.dado?.map { it.id }?.toSet()
            enviar(arquivo)
        }
    }

    /**
     * "Tentar de novo" depois de uma falha. Se a rede caiu no meio do upload, o app
     * **não sabe** se o servidor chegou a gravar o livro — reenviar às cegas o
     * duplicaria em silêncio. Então, antes, confere: se apareceu um livro **novo**
     * com o mesmo nome de arquivo, o upload tinha funcionado, e o app segue a partir
     * dele, sem reenviar.
     */
    fun tentarDeNovo() {
        val arquivo = (_estado.value as? EstadoDaImportacao.Falhou)?.arquivo ?: return

        _estado.value = EstadoDaImportacao.Enviando(arquivo.nome, 0, arquivo.tamanho)
        viewModelScope.launch {
            val antes = idsAntes
            if (antes != null) {
                val lista = repositorio.listarLivros()
                val gravado = (lista as? ResultadoDaChamada.Sucesso)?.dado
                    ?.firstOrNull { it.nome_arquivo == arquivo.nome && it.id !in antes }

                if (gravado != null) {
                    when (val detalhe = repositorio.abrirLivro(gravado.id)) {
                        // Não dá para reconstruir o aviso de "semelhantes" por este caminho.
                        is ResultadoDaChamada.Sucesso -> tratarResposta(detalhe.dado, emptyList())
                        // O livro está lá; reenviar o duplicaria. Melhor falhar de novo.
                        is ResultadoDaChamada.Falha ->
                            _estado.value = EstadoDaImportacao.Falhou(detalhe.motivo, arquivo)
                    }
                    return@launch
                }
            }
            enviar(arquivo)
        }
    }

    /** "Fechar" numa falha. */
    fun fechar() {
        if (_estado.value is EstadoDaImportacao.Falhou) _estado.value = EstadoDaImportacao.Nenhuma
    }

    // ---------------------------------------------------------------- //
    // Livros semelhantes
    // ---------------------------------------------------------------- //

    /** "Seguir mesmo assim": aceita a duplicata e segue para o que faltar. */
    fun seguirMesmoAssim() {
        val atual = _estado.value as? EstadoDaImportacao.Semelhantes ?: return
        if (atual.removendo) return
        seguirDepoisDeSemelhantes(atual.livro)
    }

    /** "Abrir o existente": vai para o primeiro livro parecido. O novo continua gravado. */
    fun abrirExistente() {
        val atual = _estado.value as? EstadoDaImportacao.Semelhantes ?: return
        if (atual.removendo) return
        concluir(atual.semelhantes.first().id)
    }

    /** "Remover o novo": apaga o livro que acabou de ser importado. */
    fun removerONovo() {
        val atual = _estado.value as? EstadoDaImportacao.Semelhantes ?: return
        if (atual.removendo) return

        _estado.value = atual.copy(removendo = true, erro = null)
        viewModelScope.launch {
            when (val resultado = repositorio.removerLivro(atual.livro.id)) {
                is ResultadoDaChamada.Sucesso -> livroDescartado()
                is ResultadoDaChamada.Falha ->
                    _estado.value = atual.copy(removendo = false, erro = resultado.motivo)
            }
        }
    }

    // ---------------------------------------------------------------- //
    // Formulário de metadados
    // ---------------------------------------------------------------- //

    /**
     * "Salvar" no formulário. Só os campos **pendentes** são conferidos e enviados.
     *
     * Um campo vazio nunca é enviado: para o backend, `autor = ""` **não** é nulo —
     * seria gravado e o autor deixaria de contar como pendente sem existir.
     */
    fun salvarMetadados(titulo: String, autor: String) {
        val atual = _estado.value as? EstadoDaImportacao.MetadadosPendentes ?: return
        if (atual.salvando || atual.removendo) return

        val pendentes = atual.livro.metadados_pendentes
        val tituloAEnviar = titulo.trim().takeIf { "titulo" in pendentes }
        val autorAEnviar = autor.trim().takeIf { "autor" in pendentes }

        val vazios = buildList {
            if ("titulo" in pendentes && tituloAEnviar.isNullOrEmpty()) add("título")
            if ("autor" in pendentes && autorAEnviar.isNullOrEmpty()) add("autor")
        }
        if (vazios.isNotEmpty()) {
            _estado.value = atual.copy(erro = "Preencha: ${vazios.joinToString(" e ")}.")
            return
        }

        _estado.value = atual.copy(salvando = true, erro = null)
        viewModelScope.launch {
            val ajuste = LivroAjuste(titulo = tituloAEnviar, autor = autorAEnviar)
            when (val resultado = repositorio.ajustarLivro(atual.livro.id, ajuste)) {
                is ResultadoDaChamada.Sucesso -> {
                    _versaoDaBiblioteca.update { it + 1 }
                    val livro = resultado.dado
                    if (livro.metadados_pendentes.isEmpty()) {
                        concluir(livro.id)
                    } else {
                        _estado.value = EstadoDaImportacao.MetadadosPendentes(
                            livro = livro,
                            erro = "Ainda falta: ${nomesDosCampos(livro.metadados_pendentes)}.",
                        )
                    }
                }
                is ResultadoDaChamada.Falha ->
                    _estado.value = atual.copy(salvando = false, erro = resultado.motivo)
            }
        }
    }

    /**
     * "Remover livro" no formulário. O formulário não tem "Cancelar" (título e
     * autor são mandatórios), então este é o jeito de não ficar preso num livro
     * que o usuário não quer.
     */
    fun removerLivroDoFormulario() {
        val atual = _estado.value as? EstadoDaImportacao.MetadadosPendentes ?: return
        if (atual.salvando || atual.removendo) return

        _estado.value = atual.copy(removendo = true, erro = null)
        viewModelScope.launch {
            when (val resultado = repositorio.removerLivro(atual.livro.id)) {
                is ResultadoDaChamada.Sucesso -> livroDescartado()
                is ResultadoDaChamada.Falha ->
                    _estado.value = atual.copy(removendo = false, erro = resultado.motivo)
            }
        }
    }

    /** A tela já navegou; zera o evento para não navegar de novo ao girar o aparelho. */
    fun consumirNavegacao() {
        _irParaLivro.value = null
    }

    // ---------------------------------------------------------------- //
    // Por dentro
    // ---------------------------------------------------------------- //

    private suspend fun enviar(arquivo: ArquivoEscolhido) {
        _estado.value = EstadoDaImportacao.Enviando(arquivo.nome, 0, arquivo.tamanho)

        val resultado = repositorio.importarLivro(arquivo) { enviados, total ->
            // Vem de uma thread de rede. Só atualiza se ainda estamos enviando —
            // um aviso atrasado não pode sobrescrever o resultado.
            _estado.update { atual ->
                if (atual is EstadoDaImportacao.Enviando) {
                    EstadoDaImportacao.Enviando(arquivo.nome, enviados, total)
                } else {
                    atual
                }
            }
        }

        when (resultado) {
            is ResultadoDaChamada.Sucesso ->
                tratarResposta(resultado.dado.livro, resultado.dado.livros_semelhantes)
            is ResultadoDaChamada.Falha ->
                _estado.value = EstadoDaImportacao.Falhou(resultado.motivo, arquivo)
        }
    }

    /** A ordem (item 7.3a): semelhantes, depois metadados pendentes, senão concluído. */
    private fun tratarResposta(livro: LivroDetalhe, semelhantes: List<LivroResumo>) {
        _versaoDaBiblioteca.update { it + 1 }
        if (semelhantes.isNotEmpty()) {
            _estado.value = EstadoDaImportacao.Semelhantes(livro, semelhantes)
        } else {
            seguirDepoisDeSemelhantes(livro)
        }
    }

    private fun seguirDepoisDeSemelhantes(livro: LivroDetalhe) {
        if (livro.metadados_pendentes.isNotEmpty()) {
            _estado.value = EstadoDaImportacao.MetadadosPendentes(livro)
        } else {
            concluir(livro.id)
        }
    }

    private fun livroDescartado() {
        _versaoDaBiblioteca.update { it + 1 }
        _estado.value = EstadoDaImportacao.Nenhuma
    }

    private fun concluir(livroId: Int) {
        _estado.value = EstadoDaImportacao.Nenhuma
        _irParaLivro.value = livroId
    }
}

/** `["titulo", "autor"]` → "título e autor", para as mensagens. */
internal fun nomesDosCampos(campos: List<String>): String =
    campos.joinToString(" e ") { if (it == "titulo") "título" else it }

/**
 * Quanto do arquivo já subiu, de 0 a 1 — ou `null` se o tamanho é desconhecido
 * (progresso indeterminado). Função pura, para testar fora do Compose.
 */
fun fracaoEnviada(enviados: Long, total: Long?): Float? {
    if (total == null || total <= 0L) return null
    return (enviados.toFloat() / total).coerceIn(0f, 1f)
}
