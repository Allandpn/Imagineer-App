package com.allan.imagineer

import android.app.Application
import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.dados.ArmazenamentoNoDataStore
import com.allan.imagineer.dados.LeitorDeArquivos
import com.allan.imagineer.dados.LeitorDeArquivosDoAndroid
import com.allan.imagineer.rede.ProvedorDeApi
import com.allan.imagineer.rede.RepositorioDeCapitulos
import com.allan.imagineer.rede.RepositorioDeCapitulosPeloRetrofit
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.RepositorioDeLivrosPeloRetrofit
import com.allan.imagineer.rede.ServidorImagineer
import com.allan.imagineer.rede.ServidorPeloRetrofit

/**
 * A "aplicação": o objeto que vive enquanto o app estiver na memória.
 *
 * É onde moram as dependências compartilhadas (injeção manual, item 7.0 — sem
 * Hilt). Os ViewModels as recebem pelo construtor, montados por uma fábrica na
 * hora de criar cada tela.
 */
class ImagineerApp : Application() {

    /** Criado só na primeira vez que alguém precisa (`lazy`). */
    val armazenamento: ArmazenamentoDeConfiguracao by lazy { ArmazenamentoNoDataStore(this) }

    val servidor: ServidorImagineer = ServidorPeloRetrofit()

    val leitorDeArquivos: LeitorDeArquivos by lazy { LeitorDeArquivosDoAndroid(this) }

    private val provedorDeApi: ProvedorDeApi by lazy { ProvedorDeApi(armazenamento) }

    val repositorioDeLivros: RepositorioDeLivros by lazy {
        RepositorioDeLivrosPeloRetrofit(provedorDeApi, leitorDeArquivos)
    }

    val repositorioDeCapitulos: RepositorioDeCapitulos by lazy {
        RepositorioDeCapitulosPeloRetrofit(provedorDeApi)
    }
}
