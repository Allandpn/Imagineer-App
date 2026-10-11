package com.allan.imagineer.telas.livro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.ModelosDoLivro
import com.allan.imagineer.rede.RepositorioDeModelos
import com.allan.imagineer.rede.RepositorioDeModelosDoLivro
import com.allan.imagineer.rede.RepositorioDeNarracao
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.telas.menu.CategoriaDeModelo
import com.allan.imagineer.telas.menu.TarefaDeTexto
import com.allan.imagineer.telas.menu.precoDoModelo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// "Modelos deste livro" (item 7.5g, MV4): cada livro pode usar outro modelo de IA, sem mexer na configuração global. Vazio = vale o global.

/** De onde vêm as opções que a pessoa escolhe numa tarefa. */
enum class FonteDeOpcoes(val categoriaDeFavoritos: String?) {
    TEXTO(CategoriaDeModelo.TEXTO),
    IMAGEM(CategoriaDeModelo.IMAGEM),
    MODELO_DE_VOZ(CategoriaDeModelo.NARRACAO),

    /** As vozes do modelo de voz que vale no livro; não tem estrela. */
    VOZ(null),
}

/** Uma linha da tela: [campo] é a chave que o servidor usa em `/livros/{id}/modelos`. */
data class TarefaDoLivro(val campo: String, val titulo: String, val descricao: String, val fonte: FonteDeOpcoes)

/** As tarefas que o livro pode trocar: as de texto (as mesmas de *Modelos de IA*), a imagem e a narração (modelo e voz). */
val TAREFAS_DO_LIVRO: List<TarefaDoLivro> =
    TarefaDeTexto.entries.map { TarefaDoLivro(it.campo, it.titulo, it.descricao, FonteDeOpcoes.TEXTO) } + listOf(
        TarefaDoLivro("modelo_imagem", "Imagem", "Gera a imagem do prompt.", FonteDeOpcoes.IMAGEM),
        TarefaDoLivro("modelo_narracao", "Modelo de voz", "Lê o capítulo em voz alta (voz de IA).", FonteDeOpcoes.MODELO_DE_VOZ),
        TarefaDoLivro("narracao_voz", "Voz", "A voz do modelo de voz que vale neste livro.", FonteDeOpcoes.VOZ),
    )

/** Uma opção da lista de escolha, já pronta para mostrar. */
data class OpcaoDeEscolha(val id: String, val titulo: String, val detalhe: String)

/** O que a linha mostra: o modelo trocado neste livro, ou o que vale por ser o global, ou que falta escolher. */
fun descricaoDaTarefaNoLivro(modelos: ModelosDoLivro, tarefa: TarefaDoLivro): String {
    modelos.trocas[tarefa.campo]?.takeIf { it.isNotBlank() }?.let { return "Neste livro: $it" }
    val global = modelos.valendo[tarefa.campo]?.modelo?.takeIf { it.isNotBlank() }
    return if (global != null) "Padrão (global): $global" else "Padrão (nenhum escolhido ainda)"
}

/** O livro trocou esta tarefa? */
fun tarefaTrocada(modelos: ModelosDoLivro, tarefa: TarefaDoLivro): Boolean = !modelos.trocas[tarefa.campo].isNullOrBlank()

/**
 * As trocas depois de escolher [id] em [campo] (`null` = "Usar o padrão"). O servidor **substitui** tudo, então mandamos as trocas de antes
 * com esta mudada — inclusive as que a tela não mostra. Trocar o modelo de voz desfaz a troca de voz, a menos que o novo modelo a tenha.
 */
fun trocasComEscolha(trocas: Map<String, String>, campo: String, id: String?, vozesDoNovoModelo: List<String> = emptyList()): Map<String, String> {
    val novas = LinkedHashMap(trocas)
    if (id.isNullOrBlank()) novas.remove(campo) else novas[campo] = id
    if (campo == "modelo_narracao" && novas["narracao_voz"] !in vozesDoNovoModelo) novas.remove("narracao_voz")
    return novas
}

/** As opções cujo título, id ou detalhe combinam com a [busca] (sem ligar para maiúsculas); busca vazia = todas. */
fun filtrarOpcoes(opcoes: List<OpcaoDeEscolha>, busca: String): List<OpcaoDeEscolha> {
    val termo = busca.trim()
    if (termo.isEmpty()) return opcoes
    return opcoes.filter { it.id.contains(termo, true) || it.titulo.contains(termo, true) || it.detalhe.contains(termo, true) }
}

sealed interface CargaDosModelosDoLivro {
    data object Carregando : CargaDosModelosDoLivro
    data class Pronta(val modelos: ModelosDoLivro) : CargaDosModelosDoLivro
    data class Erro(val motivo: String) : CargaDosModelosDoLivro
}

sealed interface OpcoesDaEscolha {
    data object Carregando : OpcoesDaEscolha
    data class Prontas(val opcoes: List<OpcaoDeEscolha>) : OpcoesDaEscolha
    data class Erro(val motivo: String) : OpcoesDaEscolha
}

data class EstadoDosModelosDoLivro(
    val carga: CargaDosModelosDoLivro = CargaDosModelosDoLivro.Carregando,
    /** A tarefa cuja escolha está aberta. */
    val escolhendo: TarefaDoLivro? = null,
    val opcoes: OpcoesDaEscolha = OpcoesDaEscolha.Carregando,
    val salvando: Boolean = false,
    val recado: String? = null,
    val recadoEhErro: Boolean = false,
)

/** A tela **Modelos deste livro** (item 7.5g, MV4): mostra o modelo de cada tarefa neste livro e deixa trocar ou voltar ao padrão. */
class ModelosDoLivroViewModel(
    private val livroId: Int,
    private val repositorio: RepositorioDeModelosDoLivro,
    private val modelos: RepositorioDeModelos,
    private val narracao: RepositorioDeNarracao,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDosModelosDoLivro())
    val estado: StateFlow<EstadoDosModelosDoLivro> = _estado.asStateFlow()

    /** As vozes de cada modelo de voz e o nome de cada um, lidos quando a pessoa abre a escolha do modelo ou da voz. */
    private var vozesPorModelo: Map<String, List<String>> = emptyMap()
    private var nomesDosModelosDeVoz: List<Pair<String, String>> = emptyList()

    fun carregar() {
        viewModelScope.launch {
            when (val r = repositorio.ver(livroId)) {
                is ResultadoDaChamada.Sucesso -> _estado.update { it.copy(carga = CargaDosModelosDoLivro.Pronta(r.dado)) }
                is ResultadoDaChamada.Falha -> _estado.update {
                    if (it.carga is CargaDosModelosDoLivro.Pronta) it.copy(recado = r.motivo, recadoEhErro = true)
                    else it.copy(carga = CargaDosModelosDoLivro.Erro(r.motivo))
                }
            }
        }
    }

    /** Abre a escolha da [tarefa] e lê as opções dela. */
    fun abrirEscolha(tarefa: TarefaDoLivro) {
        _estado.update { it.copy(escolhendo = tarefa, recado = null, opcoes = OpcoesDaEscolha.Carregando) }
        viewModelScope.launch {
            val opcoes = lerOpcoes(tarefa)
            _estado.update { if (it.escolhendo == tarefa) it.copy(opcoes = opcoes) else it }
        }
    }

    fun tentarOpcoesDeNovo() {
        _estado.value.escolhendo?.let { abrirEscolha(it) }
    }

    fun fecharEscolha() {
        _estado.update { it.copy(escolhendo = null) }
    }

    /** Troca o modelo da [tarefa] só neste livro ([id] `null` = voltar ao padrão) e fecha a escolha; a falha fica num recado. */
    fun escolher(tarefa: TarefaDoLivro, id: String?) {
        val carga = _estado.value.carga as? CargaDosModelosDoLivro.Pronta ?: return
        if (_estado.value.salvando) return
        _estado.update { it.copy(salvando = true, recado = null) }
        viewModelScope.launch {
            val trocas = trocasComEscolha(carga.modelos.trocas, tarefa.campo, id, id?.let { vozesPorModelo[it] }.orEmpty())
            when (val r = repositorio.trocar(livroId, trocas)) {
                is ResultadoDaChamada.Sucesso -> _estado.update {
                    it.copy(
                        carga = CargaDosModelosDoLivro.Pronta(r.dado), escolhendo = null,
                        recado = if (id == null) "${tarefa.titulo}: voltou ao padrão." else "${tarefa.titulo}: trocado só neste livro.",
                        recadoEhErro = false,
                    )
                }
                is ResultadoDaChamada.Falha -> _estado.update { it.copy(recado = r.motivo, recadoEhErro = true) }
            }
            _estado.update { it.copy(salvando = false) }
        }
    }

    private suspend fun lerOpcoes(tarefa: TarefaDoLivro): OpcoesDaEscolha = when (tarefa.fonte) {
        FonteDeOpcoes.TEXTO -> when (val r = modelos.modelosDeTexto()) {
            is ResultadoDaChamada.Sucesso -> OpcoesDaEscolha.Prontas(
                r.dado.map { OpcaoDeEscolha(it.id, it.nome, listOfNotNull(it.id, precoDoModelo(it), if (it.moderado) "moderado" else null).joinToString(" · ")) },
            )
            is ResultadoDaChamada.Falha -> OpcoesDaEscolha.Erro(r.motivo)
        }
        FonteDeOpcoes.IMAGEM -> when (val r = modelos.catalogoDeImagem()) {
            is ResultadoDaChamada.Sucesso -> OpcoesDaEscolha.Prontas(
                r.dado.modelos.map {
                    OpcaoDeEscolha(it.id, it.nome, listOfNotNull(it.fornecedor, it.preco_por_imagem?.let { preco -> "$preco por imagem" }, it.moderacao.ifBlank { null }).joinToString(" · "))
                },
            )
            is ResultadoDaChamada.Falha -> OpcoesDaEscolha.Erro(r.motivo)
        }
        FonteDeOpcoes.MODELO_DE_VOZ -> lerModelosDeVoz()?.let { OpcoesDaEscolha.Erro(it) } ?: OpcoesDaEscolha.Prontas(modelosDeVozComoOpcoes())
        FonteDeOpcoes.VOZ -> lerModelosDeVoz()?.let { OpcoesDaEscolha.Erro(it) } ?: vozesDoModeloQueVale()
    }

    /** Lê os modelos de voz do servidor (uma vez); devolve o motivo da falha, ou `null` se deu certo. */
    private suspend fun lerModelosDeVoz(): String? {
        if (vozesPorModelo.isNotEmpty()) return null
        return when (val r = narracao.modelos()) {
            is ResultadoDaChamada.Sucesso -> {
                vozesPorModelo = r.dado.associate { it.id to it.vozes }
                nomesDosModelosDeVoz = r.dado.map { it.id to it.nome }
                null
            }
            is ResultadoDaChamada.Falha -> r.motivo
        }
    }

    private fun modelosDeVozComoOpcoes(): List<OpcaoDeEscolha> =
        nomesDosModelosDeVoz.map { (id, nome) -> OpcaoDeEscolha(id, nome, "$id · ${vozesPorModelo[id].orEmpty().size} vozes") }

    private fun vozesDoModeloQueVale(): OpcoesDaEscolha {
        val carga = _estado.value.carga as? CargaDosModelosDoLivro.Pronta ?: return OpcoesDaEscolha.Erro("Os modelos do livro ainda não carregaram.")
        val modelo = carga.modelos.trocas["modelo_narracao"] ?: carga.modelos.valendo["modelo_narracao"]?.modelo
        if (modelo.isNullOrBlank()) return OpcoesDaEscolha.Erro("Escolha antes o modelo de voz (aqui ou em Configurações → Narração).")
        val vozes = vozesPorModelo[modelo].orEmpty()
        if (vozes.isEmpty()) return OpcoesDaEscolha.Erro("O modelo $modelo não tem vozes para escolher.")
        return OpcoesDaEscolha.Prontas(vozes.map { OpcaoDeEscolha(it, it, modelo) })
    }
}
