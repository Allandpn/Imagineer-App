package com.allan.imagineer

import android.app.Application
import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.dados.ArmazenamentoNoDataStore
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
}
