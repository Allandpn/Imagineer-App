package com.allan.imagineer.telas.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.CustosDoMes
import com.allan.imagineer.rede.RepositorioDeCustos
import com.allan.imagineer.rede.ResultadoDaChamada
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface EstadoDosCustos {
    data object Carregando : EstadoDosCustos
    data class Pronto(val dados: CustosDoMes) : EstadoDosCustos

    /** O servidor não tem a rota de custos (servidor antigo): CU6. */
    data object Indisponivel : EstadoDosCustos
    data class Erro(val motivo: String) : EstadoDosCustos
}

/** Os custos de IA (CU5): lê o mês escolhido e navega entre os meses que têm gasto. */
class CustosViewModel(private val repositorio: RepositorioDeCustos) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoDosCustos>(EstadoDosCustos.Carregando)
    val estado: StateFlow<EstadoDosCustos> = _estado.asStateFlow()

    /** Lê os custos do [mes] (`AAAA-MM`); `null` = o mês atual. Mantém o que já está na tela enquanto lê (sem piscar). */
    fun carregar(mes: String? = (_estado.value as? EstadoDosCustos.Pronto)?.dados?.mes) {
        if (_estado.value !is EstadoDosCustos.Pronto) _estado.value = EstadoDosCustos.Carregando
        viewModelScope.launch {
            _estado.value = when (val resultado = repositorio.custos(mes)) {
                is ResultadoDaChamada.Sucesso -> EstadoDosCustos.Pronto(resultado.dado)
                is ResultadoDaChamada.Falha ->
                    if (resultado.codigoHttp == 404) EstadoDosCustos.Indisponivel else EstadoDosCustos.Erro(resultado.motivo)
            }
        }
    }

    /** Vai ao mês **anterior** que tem gasto (a seta para trás). Sem mês anterior, não faz nada. */
    fun mesAnterior() {
        val dados = (_estado.value as? EstadoDosCustos.Pronto)?.dados ?: return
        mesesNavegaveis(dados).firstOrNull { it < dados.mes }?.let { carregar(it) }
    }

    /** Vai ao mês **seguinte** que tem gasto (a seta para a frente). */
    fun mesSeguinte() {
        val dados = (_estado.value as? EstadoDosCustos.Pronto)?.dados ?: return
        mesesNavegaveis(dados).lastOrNull { it > dados.mes }?.let { carregar(it) }
    }
}

/** Os meses por onde as setas andam: os que têm gasto mais o mês que está na tela, do mais novo ao mais antigo. */
fun mesesNavegaveis(dados: CustosDoMes): List<String> = (dados.meses_com_gasto + dados.mes).distinct().sortedDescending()

fun temMesAnterior(dados: CustosDoMes): Boolean = mesesNavegaveis(dados).any { it < dados.mes }

fun temMesSeguinte(dados: CustosDoMes): Boolean = mesesNavegaveis(dados).any { it > dados.mes }

private val PORTUGUES_DO_BRASIL = Locale("pt", "BR")

/**
 * Um valor em dólares para ler: "US$ 12,40" a partir de 1 dólar; de 1 centavo a 1 dólar, três casas ("US$ 0,025"); abaixo de 1 centavo,
 * quatro ("US$ 0,0004"), porque uma chamada barata custa frações de centavo. [estimado] antepõe o "~" (o valor veio, em parte, de uma tabela de preços).
 */
fun formatarDolar(valor: String, estimado: Boolean = false): String {
    val numero = valor.toDoubleOrNull() ?: 0.0
    val casas = when {
        numero == 0.0 || numero >= 1.0 -> 2
        numero >= 0.01 -> 3
        else -> 4 // abaixo de um centavo (uma tradução, uma chamada barata): "0,0004" e não "0,000"
    }
    val texto = String.format(PORTUGUES_DO_BRASIL, "%.${casas}f", numero)
    return (if (estimado) "~" else "") + "US$ $texto"
}

/** "outubro de 2026" para um mês `AAAA-MM`; um valor mal escrito volta como veio. */
fun nomeDoMes(mes: String): String {
    val partes = mes.split("-")
    val numero = partes.getOrNull(1)?.toIntOrNull() ?: return mes
    val nomes = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
    return "${nomes.getOrNull(numero - 1) ?: return mes} de ${partes[0]}"
}

/** O nome do provedor como se escreve: `fal` vira "fal.ai". */
fun nomeDoProvedor(provedor: String): String = when (provedor) {
    "openrouter" -> "OpenRouter"
    "fal" -> "fal.ai"
    "replicate" -> "Replicate"
    else -> provedor
}

/** O nome da operação de IA em português para a pessoa. */
fun nomeDaOperacao(operacao: String): String = when (operacao) {
    "extracao" -> "Análise do capítulo"
    "estado" -> "Estado do elemento"
    "identidade" -> "Identidade do elemento"
    "fundamentacao" -> "Contexto da cena"
    "prompt" -> "Prompt de imagem"
    "prompt_de_video" -> "Prompt de vídeo"
    "suavizacao" -> "Suavização do prompt"
    "traducao" -> "Tradução de prompt"
    "perfil" -> "Perfil de renderização"
    "imagem" -> "Imagens geradas"
    else -> operacao.replaceFirstChar { it.uppercase() }
}
