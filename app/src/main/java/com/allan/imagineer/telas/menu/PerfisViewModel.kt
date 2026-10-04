package com.allan.imagineer.telas.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.imagineer.rede.PerfilEdicao
import com.allan.imagineer.rede.PerfilRenderizacao
import com.allan.imagineer.rede.RepositorioDePerfis
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.paraEdicao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// A tela "Perfis de renderização" (item 7.9, BT5): ver, criar, editar e apagar perfis, e escolher a categoria de estilo.

/** O formulário aberto: [perfilId] nulo = perfil novo. */
data class FormularioDoPerfil(val perfilId: Int?, val edicao: PerfilEdicao, val erro: String? = null, val salvando: Boolean = false)

sealed interface CargaDosPerfis {
    data object Carregando : CargaDosPerfis
    data class Pronta(val perfis: List<PerfilRenderizacao>) : CargaDosPerfis
    data class Erro(val motivo: String) : CargaDosPerfis
}

data class EstadoDosPerfis(
    val carga: CargaDosPerfis = CargaDosPerfis.Carregando,
    val formulario: FormularioDoPerfil? = null,
    /** O perfil que a pessoa pediu para apagar e ainda não confirmou. */
    val apagando: PerfilRenderizacao? = null,
    /** Um recado curto (falha ao apagar, por exemplo); nulo quando está tudo bem. */
    val aviso: String? = null,
)

/** Um perfil só pode ser salvo com nome (o servidor também recusa; aqui a pessoa vê antes de esperar a rede). */
fun podeSalvarOPerfil(edicao: PerfilEdicao): Boolean = edicao.nome.isNotBlank()

class PerfisViewModel(private val repositorio: RepositorioDePerfis) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDosPerfis())
    val estado: StateFlow<EstadoDosPerfis> = _estado.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            val carga = when (val r = repositorio.listarPerfis()) {
                is ResultadoDaChamada.Sucesso -> CargaDosPerfis.Pronta(r.dado)
                is ResultadoDaChamada.Falha -> CargaDosPerfis.Erro(r.motivo)
            }
            _estado.value = _estado.value.copy(carga = carga)
        }
    }

    fun tentarDeNovo() {
        _estado.value = _estado.value.copy(carga = CargaDosPerfis.Carregando)
        carregar()
    }

    fun novo() {
        _estado.value = _estado.value.copy(formulario = FormularioDoPerfil(null, PerfilEdicao(nome = "")))
    }

    fun editar(perfil: PerfilRenderizacao) {
        _estado.value = _estado.value.copy(formulario = FormularioDoPerfil(perfil.id, perfil.paraEdicao()))
    }

    fun mudarFormulario(edicao: PerfilEdicao) {
        val atual = _estado.value.formulario ?: return
        _estado.value = _estado.value.copy(formulario = atual.copy(edicao = edicao, erro = null))
    }

    fun fecharFormulario() {
        _estado.value = _estado.value.copy(formulario = null)
    }

    fun salvar() {
        val formulario = _estado.value.formulario ?: return
        if (formulario.salvando) return
        if (!podeSalvarOPerfil(formulario.edicao)) {
            _estado.value = _estado.value.copy(formulario = formulario.copy(erro = "Dê um nome ao perfil."))
            return
        }
        _estado.value = _estado.value.copy(formulario = formulario.copy(salvando = true, erro = null))
        viewModelScope.launch {
            val resultado = if (formulario.perfilId == null) repositorio.criarPerfil(formulario.edicao)
            else repositorio.ajustarPerfil(formulario.perfilId, formulario.edicao)
            when (resultado) {
                is ResultadoDaChamada.Sucesso -> {
                    _estado.value = _estado.value.copy(formulario = null)
                    carregar()
                }
                // Nome repetido (409) e os outros erros do servidor voltam no próprio formulário, que continua aberto com o que se digitou.
                is ResultadoDaChamada.Falha -> _estado.value = _estado.value.copy(formulario = formulario.copy(salvando = false, erro = resultado.motivo))
            }
        }
    }

    fun pedirParaApagar(perfil: PerfilRenderizacao) {
        _estado.value = _estado.value.copy(apagando = perfil)
    }

    fun cancelarApagar() {
        _estado.value = _estado.value.copy(apagando = null)
    }

    fun confirmarApagar() {
        val perfil = _estado.value.apagando ?: return
        _estado.value = _estado.value.copy(apagando = null, formulario = null)
        viewModelScope.launch {
            when (val r = repositorio.removerPerfil(perfil.id)) {
                is ResultadoDaChamada.Sucesso -> carregar()
                is ResultadoDaChamada.Falha -> _estado.value = _estado.value.copy(aviso = "Não consegui apagar: ${r.motivo}")
            }
        }
    }

    fun avisoLido() {
        _estado.value = _estado.value.copy(aviso = null)
    }
}
