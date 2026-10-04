package com.allan.imagineer.telas.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.ConfiguracaoAtual
import com.allan.imagineer.rede.RepositorioDeModelos
import com.allan.imagineer.rede.ResultadoDaChamada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// A tela "Narração" (RL26): mostra e guarda a configuração da narração. **Nada gera áudio ainda**: o motor de IA e o modo por personagem
// aparecem desligados ("em breve"), e a narração continua sempre pela voz do aparelho.

/** O que a pessoa pode editar hoje: a voz do motor de IA e as instruções de tom (guardadas para quando a narração por IA existir). */
data class NarracaoEdicao(val voz: String = "", val instrucoes: String = "")

/** Os textos da configuração como o formulário os mostra: o que não existe vira vazio. */
fun ConfiguracaoAtual.paraNarracaoEdicao() = NarracaoEdicao(voz = narracao_voz.orEmpty(), instrucoes = narracao_instrucoes.orEmpty())

/** Se o formulário difere do que o servidor tem (só então o botão de salvar vale). Espaços nas pontas não contam. */
fun mudouANarracao(edicao: NarracaoEdicao, configuracao: ConfiguracaoAtual): Boolean {
    val guardado = configuracao.paraNarracaoEdicao()
    return edicao.voz.trim() != guardado.voz.trim() || edicao.instrucoes.trim() != guardado.instrucoes.trim()
}

/** O motor como a pessoa lê. */
fun rotuloDoMotor(motor: String) = if (motor == "IA") "Voz de IA" else "Voz do aparelho"

sealed interface CargaDaNarracao {
    data object Carregando : CargaDaNarracao
    data class Pronta(val configuracao: ConfiguracaoAtual) : CargaDaNarracao
    data class Erro(val motivo: String) : CargaDaNarracao
}

data class EstadoDaNarracaoConfigurada(
    val carga: CargaDaNarracao = CargaDaNarracao.Carregando,
    val edicao: NarracaoEdicao = NarracaoEdicao(),
    val salvando: Boolean = false,
    /** Um recado curto ("Salvo", ou o motivo da falha); nulo quando não há nada a dizer. */
    val aviso: String? = null,
)

class NarracaoViewModel(private val repositorio: RepositorioDeModelos) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDaNarracaoConfigurada())
    val estado: StateFlow<EstadoDaNarracaoConfigurada> = _estado.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            when (val r = repositorio.configuracao()) {
                is ResultadoDaChamada.Sucesso -> _estado.value = _estado.value.copy(carga = CargaDaNarracao.Pronta(r.dado), edicao = r.dado.paraNarracaoEdicao())
                is ResultadoDaChamada.Falha -> _estado.value = _estado.value.copy(carga = CargaDaNarracao.Erro(r.motivo))
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

    /** Grava a voz e as instruções; texto em branco vai como vazio, que o servidor entende como "apagar". */
    fun salvar() {
        val atual = _estado.value
        if (atual.salvando || atual.carga !is CargaDaNarracao.Pronta) return
        _estado.value = atual.copy(salvando = true)
        viewModelScope.launch {
            val campos = mapOf("narracao_voz" to atual.edicao.voz.trim(), "narracao_instrucoes" to atual.edicao.instrucoes.trim())
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
