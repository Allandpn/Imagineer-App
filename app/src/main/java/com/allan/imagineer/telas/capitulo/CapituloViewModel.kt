package com.allan.imagineer.telas.capitulo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.CapituloDetalhe
import com.allan.imagineer.rede.Artefato
import com.allan.imagineer.rede.RepositorioDeCapitulos
import com.allan.imagineer.rede.RepositorioDeArtefatos
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Divide o texto de um capítulo em parágrafos (item 7.5a, incremento 8).
 *
 * O backend separa parágrafos com uma linha em branco (`\n\n`) e mantém `\n` sozinho
 * para quebras **dentro** de um parágrafo (`<br>`); a importação já normaliza `\r\n` e
 * junta 3 ou mais quebras em 2. Aqui divide em `\n{2,}`, descarta pedaços vazios e
 * deixa o `\n` interno onde está — o `Text` do Compose o desenha como quebra de linha.
 *
 * Função pura, fora do Compose e do ViewModel, para ser testada na JVM.
 */
fun dividirEmParagrafos(texto: String): List<String> =
    texto.split(Regex("\n{2,}"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/** Os três estados da tela de Capítulo. */
sealed interface EstadoDoCapitulo {
    data object Carregando : EstadoDoCapitulo

    /**
     * O capítulo, com o texto **já dividido em parágrafos** — a divisão é feita uma
     * vez, aqui, e não a cada desenho da tela.
     */
    data class Pronto(
        val capitulo: CapituloDetalhe,
        val paragrafos: List<String>,
    ) : EstadoDoCapitulo

    /** [motivo] já está escrito para o usuário ler. */
    data class Erro(val motivo: String) : EstadoDoCapitulo
}

/**
 * A lógica da tela de Capítulo (item 7.5), por enquanto só leitura: carregar o texto.
 *
 * Diferente da Biblioteca e do Livro, **não recarrega quando a tela volta a ficar
 * visível**: o texto de um capítulo não muda.
 */
class CapituloViewModel(
    private val capituloId: Int,
    private val capitulos: RepositorioDeCapitulos,
    private val artefatosDoCapitulo: RepositorioDeArtefatos,
    /** Onde a pessoa parou (LE2). O padrão não faz nada (testes antigos). */
    private val marcador: com.allan.imagineer.rede.RepositorioDeMarcador = com.allan.imagineer.rede.MarcadorSemServidor,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoDoCapitulo>(EstadoDoCapitulo.Carregando)
    val estado: StateFlow<EstadoDoCapitulo> = _estado.asStateFlow()

    private val _artefatos = MutableStateFlow<List<Artefato>>(emptyList())

    /**
     * Os ícones a desenhar sobre o texto (item 7.5b, incremento 11). **O texto nunca espera por eles**: vêm
     * por uma chamada à parte, depois, e, se falharem, o capítulo continua legível, só sem ícones.
     */
    val artefatos: StateFlow<List<Artefato>> = _artefatos.asStateFlow()

    /**
     * Lê (ou relê) os artefatos. Só leitura — **nunca chama a IA**. Falha em silêncio: ícone é um
     * enfeite útil, não pode atrapalhar a leitura nem trocar a tela por um erro. Quem chama relê quando
     * o painel de IA muda o que há de sugestão (analisar, confirmar, descartar...).
     */
    fun carregarArtefatos() {
        viewModelScope.launch {
            val resultado = artefatosDoCapitulo.ler(capituloId)
            if (resultado is ResultadoDaChamada.Sucesso) _artefatos.value = resultado.dado
        }
    }

    private var carregamentoEmAndamento: Job? = null

    /**
     * Marca (ou desmarca) o capítulo como lido (LE1, LE5). **Otimista**: o ✓ aparece na hora e, se o servidor recusar, volta ao que
     * era. Marcar um capítulo que já está lido não chama o servidor.
     */
    fun marcarLido(lido: Boolean) {
        val pronto = _estado.value as? EstadoDoCapitulo.Pronto ?: return
        if (pronto.capitulo.lido == lido) return
        _estado.value = pronto.copy(capitulo = pronto.capitulo.copy(lido = lido))
        viewModelScope.launch {
            val resultado = capitulos.ajustarCapitulo(capituloId, com.allan.imagineer.rede.CapituloAjuste(lido = lido))
            if (resultado is ResultadoDaChamada.Falha) {
                val agora = _estado.value as? EstadoDoCapitulo.Pronto ?: return@launch
                _estado.value = agora.copy(capitulo = agora.capitulo.copy(lido = !lido))
            }
        }
    }

    /** O leitor chegou ao fim do texto (LE4, LE7): marca como lido, uma vez; reler não desmarca (só a pessoa desmarca). */
    fun chegouAoFim() = marcarLido(true)

    /** Grava onde a pessoa está lendo ([posicao] = início do parágrafo à vista, UTF-16) (LE2). Falhar em silêncio: não atrapalha a leitura. */
    fun gravarPosicao(posicao: Int) {
        val pronto = _estado.value as? EstadoDoCapitulo.Pronto ?: return
        viewModelScope.launch { marcador.gravar(pronto.capitulo.livro_id, capituloId, posicao) }
    }

    /**
     * Carrega o capítulo, **a não ser que já esteja carregado ou carregando**. A tela
     * pode chamar isto de novo (ao girar o tablet, a composição recomeça) sem gastar
     * outra chamada: o ViewModel sobrevive à rotação e já tem o texto.
     */
    fun carregar() {
        if (_estado.value is EstadoDoCapitulo.Pronto) return
        if (carregamentoEmAndamento?.isActive == true) return
        buscar()
    }

    /** "Tentar de novo" depois de um erro. */
    fun tentarDeNovo() {
        if (_estado.value !is EstadoDoCapitulo.Erro) return
        buscar()
    }

    private fun buscar() {
        _estado.value = EstadoDoCapitulo.Carregando
        carregamentoEmAndamento = viewModelScope.launch {
            _estado.value = when (val resultado = capitulos.abrirCapitulo(capituloId)) {
                is ResultadoDaChamada.Sucesso ->
                    EstadoDoCapitulo.Pronto(resultado.dado, dividirEmParagrafos(resultado.dado.texto))
                is ResultadoDaChamada.Falha -> EstadoDoCapitulo.Erro(resultado.motivo)
            }
        }
    }
}
