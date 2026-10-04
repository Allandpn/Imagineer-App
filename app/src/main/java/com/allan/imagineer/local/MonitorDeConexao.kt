package com.allan.imagineer.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Se o aparelho tem conexão agora (PL8). Interface, para as telas e os testes não dependerem do Android. */
interface Conexao {
    val online: StateFlow<Boolean>
}

/** O texto da etiqueta que aparece sem conexão (PL8). */
const val ETIQUETA_SEM_CONEXAO = "Sem conexão · só leitura"

/** Acompanha a rede do Android e avisa quando ela vai e volta. */
class MonitorDeConexao(contexto: Context) : Conexao {
    private val gerente = contexto.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _online = MutableStateFlow(consultar())
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    private fun consultar(): Boolean =
        gerente.getNetworkCapabilities(gerente.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    init {
        runCatching {
            gerente.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { _online.value = true }
                    override fun onLost(network: Network) { _online.value = consultar() }
                    override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
                        _online.value = capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    }
                },
            )
        }
    }
}
