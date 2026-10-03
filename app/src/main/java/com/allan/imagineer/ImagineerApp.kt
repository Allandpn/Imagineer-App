package com.allan.imagineer

import android.app.Application
import com.allan.imagineer.analise.ServicoDeAnalises
import com.allan.imagineer.dados.ArmazenamentoDeConfiguracao
import com.allan.imagineer.dados.ArmazenamentoNoDataStore
import com.allan.imagineer.dados.LeitorDeArquivos
import com.allan.imagineer.dados.LeitorDeArquivosDoAndroid
import com.allan.imagineer.local.ArmazemDeTextos
import com.allan.imagineer.local.ArmazemDeTextosEmArquivos
import com.allan.imagineer.local.BancoLocal
import com.allan.imagineer.local.IndiceLocal
import com.allan.imagineer.local.IndiceLocalPeloRoom
import com.allan.imagineer.rede.ProvedorDeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import com.allan.imagineer.rede.RepositorioDaLixeira
import com.allan.imagineer.rede.RepositorioDaLixeiraPeloRetrofit
import com.allan.imagineer.rede.RepositorioDeCapitulos
import com.allan.imagineer.rede.RepositorioDeCapitulosPeloRetrofit
import com.allan.imagineer.rede.RepositorioDeElementos
import com.allan.imagineer.rede.RepositorioDeElementosPeloRetrofit
import com.allan.imagineer.rede.RepositorioDeLivros
import com.allan.imagineer.rede.RepositorioDeArtefatos
import com.allan.imagineer.rede.RepositorioDePrompts
import com.allan.imagineer.rede.RepositorioDePromptsPeloRetrofit
import com.allan.imagineer.rede.RepositorioDeArtefatosPeloRetrofit
import com.allan.imagineer.rede.RepositorioDePerfis
import com.allan.imagineer.rede.RepositorioDePerfisPeloRetrofit
import com.allan.imagineer.rede.RepositorioDeSugestoes
import com.allan.imagineer.rede.RepositorioDeSugestoesPeloRetrofit
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

    // O que fica guardado no aparelho (item 7.0a). Na pasta "sem backup": o Android não
    // envia estes arquivos para a nuvem do Google (regra A12).
    private val banco: BancoLocal by lazy { BancoLocal.abrir(this) }

    private val indiceLocal: IndiceLocal by lazy { IndiceLocalPeloRoom(banco.dao()) }

    private val armazemDeTextos: ArmazemDeTextos by lazy {
        ArmazemDeTextosEmArquivos(File(noBackupFilesDir, "textos"))
    }

    /** Vive tanto quanto o app: o adiantamento do próximo capítulo não pode morrer com a tela. */
    private val escopoDeFundo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repositorioDeLivros: RepositorioDeLivros by lazy {
        RepositorioDeLivrosPeloRetrofit(provedorDeApi, leitorDeArquivos, indiceLocal, armazemDeTextos)
    }

    val repositorioDeCapitulos: RepositorioDeCapitulos by lazy {
        RepositorioDeCapitulosPeloRetrofit(provedorDeApi, indiceLocal, armazemDeTextos, escopoDeFundo)
    }

    val repositorioDeSugestoes: RepositorioDeSugestoes by lazy {
        RepositorioDeSugestoesPeloRetrofit(provedorDeApi)
    }

    /** As análises de IA, no escopo do app: sobrevivem a quem sai da tela e geram o aviso final (D1). */
    val servicoDeAnalises: ServicoDeAnalises by lazy { ServicoDeAnalises(repositorioDeSugestoes, escopoDeFundo, repositorioDePrompts) }

    val repositorioDePrompts: RepositorioDePrompts by lazy {
        RepositorioDePromptsPeloRetrofit(provedorDeApi, leitorDeArquivos)
    }

    val repositorioDeArtefatos: RepositorioDeArtefatos by lazy {
        RepositorioDeArtefatosPeloRetrofit(provedorDeApi)
    }

    val repositorioDeElementos: RepositorioDeElementos by lazy {
        RepositorioDeElementosPeloRetrofit(provedorDeApi)
    }

    val repositorioDaLixeira: RepositorioDaLixeira by lazy {
        RepositorioDaLixeiraPeloRetrofit(provedorDeApi)
    }

    val repositorioDePerfis: RepositorioDePerfis by lazy {
        RepositorioDePerfisPeloRetrofit(provedorDeApi)
    }
}
