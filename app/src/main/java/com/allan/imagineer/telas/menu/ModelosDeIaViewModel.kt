package com.allan.imagineer.telas.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.ConfiguracaoAtual
import com.allan.imagineer.rede.ModeloDeTexto
import com.allan.imagineer.rede.RepositorioDeModelos
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** As tarefas de **texto** que têm modelo próprio (MT1). [campo] é o nome no `PUT /configuracao`; [opcional] = pode ficar no padrão. */
enum class TarefaDeTexto(val campo: String, val titulo: String, val descricao: String, val opcional: Boolean) {
    EXTRACAO("modelo_extracao", "Extração e análise", "Lê o capítulo e sugere elementos e cenas. Barato e rápido.", false),
    PROMPT("modelo_prompt", "Prompt de imagem", "Escreve o prompt de cada imagem a partir do texto. É o passo mais caro.", false),
    PERFIL("modelo_perfil", "Perfil de renderização", "Sugere o perfil de um livro (uma vez por livro, então vale um modelo melhor).", false),
    SUAVIZACAO("modelo_suavizacao", "Suavização", "Reescreve um prompt que o provedor de imagem recusou.", true),
    TRADUCAO("modelo_traducao", "Tradução", "Traduz os prompts entre português e inglês. Curta e barata.", true),
}

/** O modelo escolhido para a [tarefa], ou `null` se ainda não há. */
fun modeloEscolhido(config: ConfiguracaoAtual, tarefa: TarefaDeTexto): String? = when (tarefa) {
    TarefaDeTexto.EXTRACAO -> config.modelo_extracao
    TarefaDeTexto.PROMPT -> config.modelo_prompt
    TarefaDeTexto.PERFIL -> config.modelo_perfil
    TarefaDeTexto.SUAVIZACAO -> config.modelo_suavizacao
    TarefaDeTexto.TRADUCAO -> config.modelo_traducao
}?.takeIf { it.isNotBlank() }

/** O modelo que **vale** para a tarefa quando nada foi escolhido (a mesma regra do servidor); `null` se nem isso há. */
fun modeloPadraoDe(config: ConfiguracaoAtual, tarefa: TarefaDeTexto): String? = when (tarefa) {
    TarefaDeTexto.SUAVIZACAO -> config.modelo_prompt
    TarefaDeTexto.TRADUCAO -> config.modelo_suavizacao ?: config.modelo_extracao ?: config.modelo_prompt
    else -> null
}?.takeIf { it.isNotBlank() }

/** O que a tela mostra no cartão: o escolhido, ou "Padrão: ..." (quando a tarefa é opcional), ou que falta escolher. */
fun descricaoDoModeloAtual(config: ConfiguracaoAtual, tarefa: TarefaDeTexto): String {
    modeloEscolhido(config, tarefa)?.let { return it }
    if (!tarefa.opcional) return "Nenhum escolhido"
    return modeloPadraoDe(config, tarefa)?.let { "Padrão: $it" } ?: "Padrão (nenhum modelo de texto escolhido ainda)"
}

/** Os modelos que combinam com a [busca] (no nome ou no id, sem ligar para maiúsculas); busca vazia = todos. */
fun filtrarModelos(modelos: List<ModeloDeTexto>, busca: String): List<ModeloDeTexto> {
    val termo = busca.trim()
    if (termo.isEmpty()) return modelos
    return modelos.filter { it.id.contains(termo, ignoreCase = true) || it.nome.contains(termo, ignoreCase = true) }
}

/** O preço de saída de um modelo como se costuma ver: dólares por **1 milhão** de tokens, ou "grátis". */
fun precoDoModelo(modelo: ModeloDeTexto): String =
    if (modelo.gratuito) "grátis" else formatarDolar((modelo.custo_saida * 1_000_000).toString()) + " / 1M saída"

sealed interface CargaDosModelos {
    data object Carregando : CargaDosModelos
    data class Pronta(val configuracao: ConfiguracaoAtual) : CargaDosModelos
    data class Erro(val motivo: String) : CargaDosModelos
}

/** A lista de modelos do servidor, lida só quando a pessoa abre a escolha. */
sealed interface ListaDeModelos {
    data object Nao : ListaDeModelos
    data object Carregando : ListaDeModelos
    data class Pronta(val modelos: List<ModeloDeTexto>) : ListaDeModelos
    data class Erro(val motivo: String) : ListaDeModelos
}

data class EstadoDosModelos(
    val carga: CargaDosModelos = CargaDosModelos.Carregando,
    val lista: ListaDeModelos = ListaDeModelos.Nao,
    /** A tarefa cuja escolha está aberta. */
    val escolhendo: TarefaDeTexto? = null,
    val salvando: Boolean = false,
    val recado: String? = null,
    val recadoEhErro: Boolean = false,
)

/** A tela **Modelos de IA** (MT1): mostra o modelo de cada tarefa de texto e deixa trocar, com a lista do servidor. */
class ModelosDeIaViewModel(private val repositorio: RepositorioDeModelos) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDosModelos())
    val estado: StateFlow<EstadoDosModelos> = _estado.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            when (val r = repositorio.configuracao()) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(carga = CargaDosModelos.Pronta(r.dado)) }
                is ResultadoDaChamada.Falha -> _estado.update {
                    if (it.carga is CargaDosModelos.Pronta) it.copy(recado = r.motivo, recadoEhErro = true)
                    else it.copy(carga = CargaDosModelos.Erro(r.motivo))
                }
            }
        }
    }

    /** Abre a escolha da [tarefa] e lê a lista de modelos, se ainda não leu. */
    fun abrirEscolha(tarefa: TarefaDeTexto) {
        _estado.update { it.copy(escolhendo = tarefa, recado = null) }
        if (_estado.value.lista is ListaDeModelos.Pronta) return
        carregarLista()
    }

    fun carregarLista() {
        _estado.update { it.copy(lista = ListaDeModelos.Carregando) }
        viewModelScope.launch {
            when (val r = repositorio.modelosDeTexto()) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(lista = ListaDeModelos.Pronta(r.dado)) }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(lista = ListaDeModelos.Erro(r.motivo)) }
            }
        }
    }

    fun fecharEscolha() {
        _estado.update { it.copy(escolhendo = null) }
    }

    /** Grava o [modelo] para a [tarefa] (`null` = voltar ao padrão) e fecha a escolha; a falha fica num recado e a escolha antiga vale. */
    fun escolher(tarefa: TarefaDeTexto, modelo: String?) {
        if (_estado.value.salvando) return
        _estado.update { it.copy(salvando = true, recado = null) }
        viewModelScope.launch {
            when (val r = repositorio.escolher(tarefa.campo, modelo)) {
                is ResultadoDaChamada.Sucesso -> _estado.update {
                    it.copy(
                        carga = CargaDosModelos.Pronta(r.dado), escolhendo = null,
                        recado = if (modelo == null) "${tarefa.titulo}: voltou ao padrão." else "${tarefa.titulo}: modelo trocado.",
                        recadoEhErro = false,
                    )
                }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(recado = r.motivo, recadoEhErro = true) }
            }
            _estado.update { it.copy(salvando = false) }
        }
    }
}
