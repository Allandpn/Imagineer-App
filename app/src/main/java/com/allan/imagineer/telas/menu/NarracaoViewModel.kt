package com.allan.imagineer.telas.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.ConfiguracaoAtual
import com.allan.imagineer.rede.ModeloDeNarracao
import com.allan.imagineer.rede.RepositorioDeModelos
import com.allan.imagineer.rede.RepositorioDeNarracao
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// A tela "Narração" (RL26, AN1): o motor (voz do aparelho ou voz de IA), o modelo e a voz da IA, e as instruções de tom (guardadas, ainda não enviadas).

/** O que a pessoa pode editar: o motor, o modelo e a voz da IA e as instruções de tom. */
data class NarracaoEdicao(val motor: String = "APARELHO", val modelo: String = "", val voz: String = "", val instrucoes: String = "")

/** Os textos da configuração como o formulário os mostra: o que não existe vira vazio. */
fun ConfiguracaoAtual.paraNarracaoEdicao() = NarracaoEdicao(
    motor = if (narracao_motor == "IA") "IA" else "APARELHO",
    modelo = modelo_narracao.orEmpty(),
    voz = narracao_voz.orEmpty(),
    instrucoes = narracao_instrucoes.orEmpty(),
)

/** Se o formulário difere do que o servidor tem (só então o botão de salvar vale). Espaços nas pontas não contam. */
fun mudouANarracao(edicao: NarracaoEdicao, configuracao: ConfiguracaoAtual): Boolean {
    val guardado = configuracao.paraNarracaoEdicao()
    return edicao.motor != guardado.motor ||
        edicao.modelo.trim() != guardado.modelo.trim() ||
        edicao.voz.trim() != guardado.voz.trim() ||
        edicao.instrucoes.trim() != guardado.instrucoes.trim()
}

/** O motor como a pessoa lê. */
fun rotuloDoMotor(motor: String) = if (motor == "IA") "Voz de IA" else "Voz do aparelho"

/** Trocar de modelo desfaz a voz (cada modelo tem as suas), a menos que a voz de antes também exista no novo. */
fun edicaoComOModelo(edicao: NarracaoEdicao, modelo: ModeloDeNarracao): NarracaoEdicao =
    edicao.copy(modelo = modelo.id, voz = edicao.voz.takeIf { it in modelo.vozes }.orEmpty())

sealed interface CargaDaNarracao {
    data object Carregando : CargaDaNarracao
    data class Pronta(val configuracao: ConfiguracaoAtual) : CargaDaNarracao
    data class Erro(val motivo: String) : CargaDaNarracao
}

data class EstadoDaNarracaoConfigurada(
    val carga: CargaDaNarracao = CargaDaNarracao.Carregando,
    val edicao: NarracaoEdicao = NarracaoEdicao(),
    val salvando: Boolean = false,
    /** Os modelos de voz do servidor (AN1); vazia = ainda não lidos, ou o servidor não os trouxe (ver [erroDosModelos]). */
    val modelos: List<ModeloDeNarracao> = emptyList(),
    val erroDosModelos: String? = null,
    /** Um recado curto ("Salvo", ou o motivo da falha); nulo quando não há nada a dizer. */
    val aviso: String? = null,
)

class NarracaoViewModel(
    private val repositorio: RepositorioDeModelos,
    private val narracao: RepositorioDeNarracao? = null,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDaNarracaoConfigurada())
    val estado: StateFlow<EstadoDaNarracaoConfigurada> = _estado.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            when (val r = repositorio.configuracao()) {
                is ResultadoDaChamada.Sucesso -> _estado.value = _estado.value.copy(carga = CargaDaNarracao.Pronta(r.dado), edicao = r.dado.paraNarracaoEdicao())
                is ResultadoDaChamada.Falha -> _estado.value = _estado.value.copy(carga = CargaDaNarracao.Erro(r.motivo))
            }
        }
        narracao?.let { repositorioDeVozes ->
            viewModelScope.launch {
                when (val r = repositorioDeVozes.modelos()) {
                    is ResultadoDaChamada.Sucesso -> _estado.value = _estado.value.copy(modelos = r.dado, erroDosModelos = null)
                    is ResultadoDaChamada.Falha -> _estado.value = _estado.value.copy(erroDosModelos = r.motivo)
                }
            }
        }
    }

    fun tentarDeNovo() {
        _estado.value = _estado.value.copy(carga = CargaDaNarracao.Carregando)
        carregar()
    }

    fun mudar(edicao: NarracaoEdicao) {
        _estado.value = _estado.value.copy(edicao = edicao)
    }

    /** Escolhe o modelo de voz: a voz de antes só fica se o novo modelo também a tem (AN1). */
    fun escolherModelo(modelo: ModeloDeNarracao) {
        _estado.value = _estado.value.copy(edicao = edicaoComOModelo(_estado.value.edicao, modelo))
    }

    /** Grava o motor, o modelo, a voz e as instruções; texto em branco vai como vazio, que o servidor entende como "apagar". */
    fun salvar() {
        val atual = _estado.value
        if (atual.salvando || atual.carga !is CargaDaNarracao.Pronta) return
        _estado.value = atual.copy(salvando = true)
        viewModelScope.launch {
            val campos = mapOf(
                "narracao_motor" to atual.edicao.motor,
                "modelo_narracao" to atual.edicao.modelo.trim(),
                "narracao_voz" to atual.edicao.voz.trim(),
                "narracao_instrucoes" to atual.edicao.instrucoes.trim(),
            )
            when (val r = repositorio.gravar(campos)) {
                is ResultadoDaChamada.Sucesso -> _estado.value = _estado.value.copy(
                    carga = CargaDaNarracao.Pronta(r.dado),
                    edicao = r.dado.paraNarracaoEdicao(),
                    salvando = false,
                    aviso = "Salvo.",
                )
                is ResultadoDaChamada.Falha -> _estado.value = _estado.value.copy(salvando = false, aviso = "Não consegui salvar: ${r.motivo}")
            }
        }
    }

    fun avisoLido() {
        _estado.value = _estado.value.copy(aviso = null)
    }
}
