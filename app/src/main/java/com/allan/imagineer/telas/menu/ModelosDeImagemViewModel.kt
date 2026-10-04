package com.allan.imagineer.telas.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.ModeloDeImagem
import com.allan.imagineer.rede.RepositorioDeModelos
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.TesteDeImagem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// A escolha do modelo de imagem (MI6): ver preço por imagem, moderação e resolução, usar, mostrar ao gerar e testar.

/** O preço por imagem como se lê na tela: o valor (com "~" se é estimado), de onde veio, ou que ainda não há (PD1). */
fun precoDaImagem(modelo: ModeloDeImagem): String {
    val valor = modelo.preco_por_imagem ?: return "Sem preço por imagem ainda: informe o preço ou teste para medir"
    val texto = formatarDolar(valor, estimado = modelo.origem_do_preco == "fornecedor") + " por imagem"
    return when (modelo.origem_do_preco) {
        "medido" -> "$texto (média do que já custou)"
        "informado" -> "$texto (informado por você)"
        "fornecedor" -> "$texto (estimado: fal.ai, por megapixel)"
        else -> texto
    }
}

/** Os filtros da lista por fornecedor (PD6). [rotulo] é o que a tela mostra; [fornecedor] é o nome que o servidor devolve (nulo = sem filtro). */
enum class FiltroDeFornecedor(val rotulo: String, val fornecedor: String?) {
    TODOS("Todos", null),
    MINHA_LISTA("Minha lista", null),
    OPENROUTER("OpenRouter", "OpenRouter"),
    FAL("fal.ai", "fal.ai"),
    REPLICATE("Replicate", "Replicate"),
}

/** Os modelos do [filtro]: "Minha lista" são os que aparecem ao gerar (e o padrão); os demais, o fornecedor. */
fun filtrarPorFornecedor(modelos: List<ModeloDeImagem>, filtro: FiltroDeFornecedor): List<ModeloDeImagem> = when (filtro) {
    FiltroDeFornecedor.TODOS -> modelos
    FiltroDeFornecedor.MINHA_LISTA -> modelos.filter { it.disponivel || it.em_uso }
    else -> modelos.filter { it.fornecedor == filtro.fornecedor }
}

/** Quantos modelos a lista mostra de cada vez (a lista completa passa de 300). */
const val TAMANHO_DA_PAGINA_DO_CATALOGO = 30

/** A linha de preço de token do OpenRouter, quando há: o preço **não** é por imagem, e a tela diz isso. */
fun precoPorTokenDeImagem(modelo: ModeloDeImagem): String? =
    modelo.preco_por_milhao_de_tokens?.let { "${formatarDolar(it)} por 1 milhão de tokens de imagem (a imagem usa vários)" }

/** Os modelos que combinam com a [busca] (nome, id ou fornecedor); busca vazia = todos. */
fun filtrarCatalogo(modelos: List<ModeloDeImagem>, busca: String): List<ModeloDeImagem> {
    val termo = busca.trim()
    if (termo.isEmpty()) return modelos
    return modelos.filter {
        it.nome.contains(termo, ignoreCase = true) || it.id.contains(termo, ignoreCase = true) || it.fornecedor.contains(termo, ignoreCase = true)
    }
}

/** A lista de "mostrar ao gerar" depois de trocar o [alvo]: sai se estava, entra se não estava. */
fun listaAoAlternar(modelos: List<ModeloDeImagem>, alvo: String): List<String> {
    val atuais = modelos.filter { it.disponivel }.map { it.id }
    return if (alvo in atuais) atuais - alvo else atuais + alvo
}

/** O que a confirmação do teste diz sobre o custo: o preço conhecido, ou que ainda não se sabe. */
fun avisoDeCustoDoTeste(modelo: ModeloDeImagem): String {
    val preco = modelo.preco_por_imagem?.let { formatarDolar(it, estimado = modelo.origem_do_preco == "fornecedor") }
    return if (preco != null) "Gera uma imagem de teste e cobra cerca de $preco." else "Gera uma imagem de teste e cobra (o preço ainda não é conhecido; costuma ser de um a poucos centavos)."
}

/** A resolução de um teste como "1024×1536", ou que não deu para ler. */
fun resolucaoDoTeste(teste: TesteDeImagem): String =
    if (teste.largura != null && teste.altura != null) "${teste.largura}×${teste.altura}" else "não foi possível ler"

sealed interface CargaDoCatalogo {
    data object Carregando : CargaDoCatalogo
    data class Pronta(val modelos: List<ModeloDeImagem>, val aviso: String?) : CargaDoCatalogo
    data class Erro(val motivo: String) : CargaDoCatalogo
}

/** Em que pé está o teste de um modelo: confirmando o custo, gerando, com o resultado ou com a falha. */
sealed interface TesteDeModelo {
    data class Confirmando(val modelo: ModeloDeImagem) : TesteDeModelo
    data class Rodando(val modelo: ModeloDeImagem) : TesteDeModelo
    data class Pronto(val modelo: ModeloDeImagem, val resultado: TesteDeImagem) : TesteDeModelo
    data class Falhou(val modelo: ModeloDeImagem, val motivo: String) : TesteDeModelo
}

data class EstadoDoCatalogo(
    val carga: CargaDoCatalogo = CargaDoCatalogo.Carregando,
    val ocupados: Set<String> = emptySet(),
    val teste: TesteDeModelo? = null,
    val recado: String? = null,
    val recadoEhErro: Boolean = false,
)

/** A tela do catálogo de modelos de imagem (MI6). */
class ModelosDeImagemViewModel(private val repositorio: RepositorioDeModelos) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDoCatalogo())
    val estado: StateFlow<EstadoDoCatalogo> = _estado.asStateFlow()

    /** Lê o catálogo; uma leitura que falha **não apaga** o que já estava na tela. */
    fun carregar() {
        viewModelScope.launch {
            when (val r = repositorio.catalogoDeImagem()) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(carga = CargaDoCatalogo.Pronta(r.dado.modelos, r.dado.aviso)) }
                is ResultadoDaChamada.Falha -> _estado.update {
                    if (it.carga is CargaDoCatalogo.Pronta) it.copy(recado = r.motivo, recadoEhErro = true)
                    else it.copy(carga = CargaDoCatalogo.Erro(r.motivo))
                }
            }
        }
    }

    private val modelos: List<ModeloDeImagem> get() = (_estado.value.carga as? CargaDoCatalogo.Pronta)?.modelos.orEmpty()

    /** Faz do [modelo] o padrão (`modelo_imagem`). */
    fun usar(modelo: ModeloDeImagem) {
        gravar(modelo.id, "${modelo.nome}: agora é o modelo padrão.") { repositorio.escolher("modelo_imagem", modelo.id) }
    }

    /** Mostra o [modelo] na escolha ao gerar, ou o tira de lá. O padrão não sai: ele sempre aparece. */
    fun alternarDisponivel(modelo: ModeloDeImagem) {
        if (modelo.em_uso) {
            _estado.update { it.copy(recado = "O modelo padrão sempre aparece ao gerar.", recadoEhErro = false) }
            return
        }
        val nova = listaAoAlternar(modelos, modelo.id)
        val saiu = modelo.disponivel
        gravar(modelo.id, if (saiu) "${modelo.nome}: saiu da lista de escolha." else "${modelo.nome}: aparece ao gerar.") {
            repositorio.definirModelosDeImagem(nova)
        }
    }

    /** Acrescenta um id à mão (um modelo do fal.ai ou do Replicate que não está no catálogo) à lista de escolha. */
    fun adicionar(id: String) {
        val limpo = id.trim()
        if (limpo.isEmpty()) return
        val nova = (modelos.filter { it.disponivel }.map { it.id } + limpo).distinct()
        gravar(limpo, "$limpo: aparece ao gerar.") { repositorio.definirModelosDeImagem(nova) }
    }

    private fun gravar(chave: String, sucesso: String, chamada: suspend () -> ResultadoDaChamada<*>) {
        if (chave in _estado.value.ocupados) return
        _estado.update { it.copy(ocupados = it.ocupados + chave, recado = null) }
        viewModelScope.launch {
            when (val r = chamada()) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.update { it.copy(recado = sucesso, recadoEhErro = false) }
                    carregar()
                }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(recado = r.motivo, recadoEhErro = true) }
            }
            _estado.update { it.copy(ocupados = it.ocupados - chave) }
        }
    }

    /** Informa o preço por imagem de um [modelo] ([preco] em dólares; nulo ou vazio limpa) e relê o catálogo (PD5). */
    fun informarPreco(modelo: ModeloDeImagem, preco: String?) {
        gravar(modelo.id, if (preco.isNullOrBlank()) "${modelo.nome}: preço informado removido." else "${modelo.nome}: preço informado.") {
            repositorio.informarPreco(modelo.id, preco)
        }
    }

    // ---- O teste de resolução (MI5): gasta dinheiro, então só depois de confirmar ----

    fun pedirTeste(modelo: ModeloDeImagem) {
        _estado.update { it.copy(teste = TesteDeModelo.Confirmando(modelo)) }
    }

    fun fecharTeste() {
        if (_estado.value.teste is TesteDeModelo.Rodando) return  // não dá para desistir no meio (a imagem seria cobrada mesmo assim)
        _estado.update { it.copy(teste = null) }
    }

    fun confirmarTeste() {
        val pedido = _estado.value.teste as? TesteDeModelo.Confirmando ?: return
        _estado.update { it.copy(teste = TesteDeModelo.Rodando(pedido.modelo)) }
        viewModelScope.launch {
            when (val r = repositorio.testarImagem(pedido.modelo.id)) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.update { it.copy(teste = TesteDeModelo.Pronto(pedido.modelo, r.dado)) }
                    carregar()  // o custo medido e a resolução entram no catálogo
                }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(teste = TesteDeModelo.Falhou(pedido.modelo, r.motivo)) }
            }
        }
    }
}
