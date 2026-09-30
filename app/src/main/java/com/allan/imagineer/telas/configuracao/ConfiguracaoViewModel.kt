package com.allan.imagineer.telas.configuracao

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.dados.normalizarUrl
import com.allan.imagineer.rede.ResultadoDoTeste
import com.allan.imagineer.rede.ServidorImagineer
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Como está o botão "Testar" (item 7.3a). */
sealed interface EstadoDoTeste {
    /** Ainda não testado, ou o texto mudou depois do último teste. */
    data object Nenhum : EstadoDoTeste

    data object Testando : EstadoDoTeste

    data class Conectado(val servidorTemChave: Boolean) : EstadoDoTeste

    data class Falhou(val motivo: String) : EstadoDoTeste
}

/**
 * Tudo o que a tela de Configuração mostra. A tela é só uma função deste estado:
 * ela lê e desenha, e nunca decide nada sozinha (MVVM, item 7.0).
 */
data class EstadoDaConfiguracao(
    /** O texto do campo, exatamente como o usuário digitou. */
    val url: String = "",
    val teste: EstadoDoTeste = EstadoDoTeste.Nenhum,
    /** O texto não é um endereço utilizável (ver [normalizarUrl]). */
    val urlInvalida: Boolean = false,
    /** A URL foi gravada — a tela reage navegando para a Biblioteca. */
    val salvou: Boolean = false,
)

/**
 * A lógica da tela de Configuração mínima: digitar a URL, testar, salvar.
 *
 * Recebe as duas dependências pelo construtor (injeção manual, item 7.0), o que
 * permite testar tudo com versões falsas, sem rede e sem aparelho.
 */
class ConfiguracaoViewModel(
    private val armazenamento: ArmazenamentoDeConfiguracao,
    private val servidor: ServidorImagineer,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaConfiguracao())
    val estado: StateFlow<EstadoDaConfiguracao> = _estado.asStateFlow()

    /** O teste em andamento, para poder cancelá-lo se o texto mudar no meio. */
    private var testeEmAndamento: Job? = null

    init {
        // Mostra o endereço já salvo, para o usuário poder corrigi-lo em vez de redigitar.
        viewModelScope.launch {
            val salva = armazenamento.urlDoServidor.first()
            if (salva != null && _estado.value.url.isEmpty()) {
                _estado.update { it.copy(url = salva) }
            }
        }
    }

    /** O usuário digitou. Qualquer teste anterior deixa de valer para o texto novo. */
    fun aoMudarUrl(texto: String) {
        testeEmAndamento?.cancel()
        _estado.update {
            it.copy(url = texto, teste = EstadoDoTeste.Nenhum, urlInvalida = false)
        }
    }

    /** Chama `GET /configuracao` no endereço digitado, antes de salvar. */
    fun testar() {
        val url = normalizarUrl(_estado.value.url)
        if (url == null) {
            _estado.update { it.copy(urlInvalida = true) }
            return
        }

        testeEmAndamento?.cancel()
        _estado.update { it.copy(teste = EstadoDoTeste.Testando, urlInvalida = false) }
        testeEmAndamento = viewModelScope.launch {
            val resultado = servidor.testarConexao(url)
            // Se o texto mudou durante o teste, este resultado é de um endereço
            // que não está mais no campo: descarta em vez de sobrescrever o estado.
            ensureActive()
            _estado.update {
                it.copy(
                    teste = when (resultado) {
                        is ResultadoDoTeste.Conectado ->
                            EstadoDoTeste.Conectado(resultado.servidorTemChave)
                        is ResultadoDoTeste.Falhou -> EstadoDoTeste.Falhou(resultado.motivo)
                    },
                )
            }
        }
    }

    /**
     * Grava o endereço. Só vale depois de um teste: com [EstadoDoTeste.Conectado]
     * é o caminho normal; com [EstadoDoTeste.Falhou] é o "Salvar mesmo assim"
     * (útil quando o servidor está desligado agora, mas o endereço está certo).
     */
    fun salvar() {
        val teste = _estado.value.teste
        if (teste !is EstadoDoTeste.Conectado && teste !is EstadoDoTeste.Falhou) return

        val url = normalizarUrl(_estado.value.url)
        if (url == null) {
            _estado.update { it.copy(urlInvalida = true) }
            return
        }

        viewModelScope.launch {
            armazenamento.salvarUrlDoServidor(url)
            _estado.update { it.copy(url = url, salvou = true) }
        }
    }
}
