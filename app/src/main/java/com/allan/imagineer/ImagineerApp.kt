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
class ImagineerApp : Application(), coil3.SingletonImageLoader.Factory {

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

    /** Se o aparelho tem conexão (PL8). */
    val conexao: com.allan.imagineer.local.MonitorDeConexao by lazy { com.allan.imagineer.local.MonitorDeConexao(this) }

    /** A tela Armazenamento (PL10). */
    val gerenteDeArmazenamento: com.allan.imagineer.local.GerenteDeArmazenamento by lazy {
        com.allan.imagineer.local.GerenteDeArmazenamento(
            fonte = com.allan.imagineer.local.FonteDoDownloadPelaApi(provedorDeApi),
            espaco = com.allan.imagineer.local.EspacoLocalPeloRoom(banco.dao()),
            textos = armazemDeTextos,
            registro = com.allan.imagineer.local.RegistroDeDownloadsPeloRoom(banco.dao()),
            baixador = baixadorDeLivros,
        )
    }

    /** As imagens baixadas (PL1): na pasta "sem backup", como os textos (A12). */
    val armazemDeImagens: com.allan.imagineer.local.ArmazemDeImagens by lazy {
        com.allan.imagineer.local.ArmazemDeImagensEmArquivos(File(noBackupFilesDir, "imagens"))
    }

    /** O carregador de imagens com o passo local antes da rede (PL2, regra A3). */
    override fun newImageLoader(context: coil3.PlatformContext): coil3.ImageLoader =
        coil3.ImageLoader.Builder(context)
            .components { add(com.allan.imagineer.local.FetcherDeImagemLocal.Factory(armazemDeImagens)) }
            .build()

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

    val repositorioDaLixeiraDeLivros: com.allan.imagineer.rede.RepositorioDaLixeiraDeLivros by lazy {
        com.allan.imagineer.rede.RepositorioDaLixeiraDeLivrosPeloRetrofit(provedorDeApi)
    }

    val repositorioDaLixeiraDeFrames: com.allan.imagineer.rede.RepositorioDaLixeiraDeFrames by lazy {
        com.allan.imagineer.rede.RepositorioDaLixeiraDeFramesPeloRetrofit(provedorDeApi)
    }

    val repositorioDeModelos: com.allan.imagineer.rede.RepositorioDeModelos by lazy {
        com.allan.imagineer.rede.RepositorioDeModelosPeloRetrofit(provedorDeApi)
    }

    val repositorioDaLixeiraDeElementos: com.allan.imagineer.rede.RepositorioDaLixeiraDeElementos by lazy {
        com.allan.imagineer.rede.RepositorioDaLixeiraDeElementosPeloRetrofit(provedorDeApi)
    }

    /** "Baixar para ler offline" (PL3 a PL5): vive no escopo do app, então sair da tela não interrompe o download. */
    val baixadorDeLivros: com.allan.imagineer.local.BaixadorDeLivros by lazy {
        com.allan.imagineer.local.BaixadorDeLivros(
            fonte = com.allan.imagineer.local.FonteDoDownloadPelaApi(provedorDeApi),
            textos = armazemDeTextos,
            indice = indiceLocal,
            imagens = armazemDeImagens,
            registro = com.allan.imagineer.local.RegistroDeDownloadsPeloRoom(banco.dao()),
            rede = com.allan.imagineer.local.EstadoDaRedeDoAndroid(this),
            escopo = escopoDeFundo,
        )
    }

    val repositorioDeArtefatosDoLivro: com.allan.imagineer.rede.RepositorioDeArtefatosDoLivro by lazy {
        com.allan.imagineer.rede.RepositorioDeArtefatosDoLivroPeloRetrofit(provedorDeApi)
    }

    val repositorioDeCustos: com.allan.imagineer.rede.RepositorioDeCustos by lazy {
        com.allan.imagineer.rede.RepositorioDeCustosPeloRetrofit(provedorDeApi)
    }

    val repositorioDeMarcador: com.allan.imagineer.rede.RepositorioDeMarcador by lazy {
        com.allan.imagineer.rede.RepositorioDeMarcadorPeloRetrofit(provedorDeApi)
    }

    val repositorioDeDicionario: com.allan.imagineer.rede.RepositorioDeDicionario by lazy {
        com.allan.imagineer.rede.RepositorioDeDicionarioPeloRetrofit(provedorDeApi)
    }

    val repositorioDeEstatisticas: com.allan.imagineer.rede.RepositorioDeEstatisticas by lazy {
        com.allan.imagineer.rede.RepositorioDeEstatisticasPeloRetrofit(provedorDeApi)
    }

    /** O tempo de leitura (RL16): conta no aparelho e envia ao servidor, guardando o que não foi enviado. */
    val registroDeTempoDeLeitura: com.allan.imagineer.telas.capitulo.RegistroDeTempoDeLeitura by lazy {
        com.allan.imagineer.telas.capitulo.RegistroDeTempoDeLeitura(armazenamento, repositorioDeEstatisticas)
    }

    val repositorioDeDestaques: com.allan.imagineer.rede.RepositorioDeDestaques by lazy {
        com.allan.imagineer.rede.RepositorioDeDestaquesPeloRetrofit(provedorDeApi)
    }

    val repositorioDeBusca: com.allan.imagineer.rede.RepositorioDeBusca by lazy {
        com.allan.imagineer.rede.RepositorioDeBuscaPeloRetrofit(provedorDeApi)
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
