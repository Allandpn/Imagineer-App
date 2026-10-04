package com.allan.imagineer.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.allan.imagineer.rede.MidiasDoLivro
import com.allan.imagineer.rede.ProvedorDeApi
import com.allan.imagineer.rede.ResultadoDaChamada
import com.allan.imagineer.rede.TextoDoCapitulo
import com.allan.imagineer.rede.chamarApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** A [FonteDoDownload] de verdade: a API do servidor em uso. */
class FonteDoDownloadPelaApi(private val provedor: ProvedorDeApi) : FonteDoDownload {

    override suspend fun chave(): ChaveDoCache? = provedor.emUso()?.chave

    override suspend fun textos(livroId: Int): ResultadoDaChamada<List<TextoDoCapitulo>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.textosDoLivro(livroId) }
    }

    override suspend fun midias(livroId: Int): ResultadoDaChamada<MidiasDoLivro> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.midiasDoLivro(livroId) }
    }

    override suspend fun baixarImagem(imagemId: Int, tamanho: String, destino: File): ResultadoDaChamada<Unit> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return when (val resposta = chamarApi { api.baixarImagem(imagemId, tamanho) }) {
            is ResultadoDaChamada.Falha -> resposta
            is ResultadoDaChamada.Sucesso -> resposta.dado.use { corpo ->
                try {
                    withContext(Dispatchers.IO) {
                        destino.parentFile?.mkdirs()
                        corpo.byteStream().use { entrada -> destino.outputStream().use { saida -> entrada.copyTo(saida) } }
                    }
                    ResultadoDaChamada.Sucesso(Unit)
                } catch (erro: IOException) {
                    destino.delete()
                    ResultadoDaChamada.Falha("Não consegui baixar a imagem $imagemId.")
                }
            }
        }
    }
}

/** O [EstadoDaRede] do Android: Wi-Fi (ou cabo), dados móveis ou nada. */
class EstadoDaRedeDoAndroid(contexto: Context) : EstadoDaRede {
    private val gerente = contexto.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun tipo(): TipoDeRede {
        val capacidades = gerente.getNetworkCapabilities(gerente.activeNetwork) ?: return TipoDeRede.NENHUMA
        return when {
            capacidades.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capacidades.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TipoDeRede.WIFI
            // A VPN (como o Tailscale) herda o transporte da rede de baixo; sem Wi-Fi nem cabo, é dado móvel.
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) || capacidades.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TipoDeRede.MOVEL
            else -> TipoDeRede.NENHUMA
        }
    }
}
