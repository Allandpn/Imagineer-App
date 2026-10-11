package com.allan.imagineer.telas.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.AmostraDeVoz
import com.allan.imagineer.rede.ConfiguracaoAtual
import com.allan.imagineer.rede.EstimativaDoTesteDeVoz
import com.allan.imagineer.rede.decimalOuNulo
import com.allan.imagineer.rede.dolares
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
    /** O teste de voz (item 7.5g, AU4): fechado, lendo a estimativa, esperando o "Ouvir", gerando ou tocando. */
    val teste: TesteDeVoz = TesteDeVoz.Fechado,
)

/** Em que passo está o teste de voz da tela de Narração (item 7.5g, AU4). */
sealed interface TesteDeVoz {
    data object Fechado : TesteDeVoz

    /** Lendo a frase e o custo no servidor (não gasta). */
    data object Lendo : TesteDeVoz

    /** A frase e o custo à vista, esperando a pessoa tocar em Ouvir (aí sim gasta uma fração de centavo). */
    data class Confirmar(val estimativa: EstimativaDoTesteDeVoz) : TesteDeVoz

    data class Gerando(val estimativa: EstimativaDoTesteDeVoz) : TesteDeVoz

    /** O MP3 chegou: a tela o toca. [custo] é o estimado que o servidor mandou, se mandou. */
    class Tocando(val estimativa: EstimativaDoTesteDeVoz, val amostra: AmostraDeVoz) : TesteDeVoz

    data class Falhou(val motivo: String) : TesteDeVoz
}

/** Dá para testar a voz? Só com a voz de IA escolhida e um modelo de voz escolhido (AU4). */
fun podeTestarAVoz(edicao: NarracaoEdicao): Boolean = edicao.motor == "IA" && edicao.modelo.isNotBlank()

/** O que o diálogo diz do custo do teste, antes de gastar (AU4): "grátis", "custa ≈ US$ 0,0016" ou "sem estimativa de custo". */
fun descreverCustoDoTesteDeVoz(estimativa: EstimativaDoTesteDeVoz): String {
    val valor = decimalOuNulo(estimativa.custo_estimado) ?: return "Sem estimativa de custo (este modelo cobra por token ou por segundo)."
    return if (valor <= 0.0) "Grátis: este modelo não cobra." else "Custa cerca de ${dolares(valor)}."
}

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

    /**
     * "Testar voz" (AU4): lê a frase e o custo do teste com o modelo e a voz que estão **no formulário** (mesmo sem salvar). Não gasta nada;
     * o gasto só vem em [gerarTesteDeVoz].
     */
    fun abrirTesteDeVoz() {
        val repositorioDeVozes = narracao ?: return
        val edicao = _estado.value.edicao
        if (!podeTestarAVoz(edicao)) return
        _estado.value = _estado.value.copy(teste = TesteDeVoz.Lendo)
        viewModelScope.launch {
            val teste = when (val r = repositorioDeVozes.estimativaDoTeste(edicao.modelo.trim(), edicao.voz.trim())) {
                is ResultadoDaChamada.Sucesso -> TesteDeVoz.Confirmar(r.dado)
                is ResultadoDaChamada.Falha -> TesteDeVoz.Falhou(r.motivo)
            }
            _estado.value = _estado.value.copy(teste = teste)
        }
    }

    /** "Ouvir" no diálogo do teste: pede o MP3 ao servidor (gasta uma fração de centavo, nada num modelo gratuito) e o entrega para tocar. */
    fun gerarTesteDeVoz() {
        val repositorioDeVozes = narracao ?: return
        val atual = _estado.value.teste
        val estimativa = when (atual) {
            is TesteDeVoz.Confirmar -> atual.estimativa
            is TesteDeVoz.Tocando -> atual.estimativa // "Ouvir de novo"
            else -> return
        }
        val edicao = _estado.value.edicao
        _estado.value = _estado.value.copy(teste = TesteDeVoz.Gerando(estimativa))
        viewModelScope.launch {
            val teste = when (val r = repositorioDeVozes.testarVoz(edicao.modelo.trim(), edicao.voz.trim())) {
                is ResultadoDaChamada.Sucesso -> if (r.dado.mp3.isEmpty()) TesteDeVoz.Falhou("O servidor não mandou áudio.") else TesteDeVoz.Tocando(estimativa, r.dado)
                is ResultadoDaChamada.Falha -> TesteDeVoz.Falhou(r.motivo)
            }
            _estado.value = _estado.value.copy(teste = teste)
        }
    }

    fun fecharTesteDeVoz() {
        if (_estado.value.teste is TesteDeVoz.Gerando) return // gastou; espera o resultado
        _estado.value = _estado.value.copy(teste = TesteDeVoz.Fechado)
    }
}
