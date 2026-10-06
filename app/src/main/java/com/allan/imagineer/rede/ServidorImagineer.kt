package com.allan.imagineer.rede

/** O resultado do botão "Testar" da tela de Configuração. */
sealed interface ResultadoDoTeste {

    /** O servidor respondeu como o Imagineer. [servidorTemChave]: se ele tem chave de API própria. */
    data class Conectado(val servidorTemChave: Boolean, val quem: EuAtual? = null) : ResultadoDoTeste

    /** Não deu certo; [motivo] já está escrito para o usuário ler. */
    data class Falhou(val motivo: String) : ResultadoDoTeste
}

/**
 * O que o app precisa saber fazer com o servidor. É uma interface para o
 * ViewModel poder ser testado sem rede (item 7.3a) — a mesma ideia do
 * `ProvedorFalso` do backend.
 */
interface ServidorImagineer {
    /** Testa um endereço, ainda não salvo, chamando `GET /configuracao`. */
    suspend fun testarConexao(urlBase: String): ResultadoDoTeste
}

/** A implementação de verdade, sobre o Retrofit. */
class ServidorPeloRetrofit : ServidorImagineer {

    override suspend fun testarConexao(urlBase: String): ResultadoDoTeste {
        val api = criarApi(urlBase)
        return when (val resultado = chamarApi { api.configuracao() }) {
            is ResultadoDaChamada.Sucesso -> {
                // AP5: quem o servidor diz que sou; um servidor antigo, sem a rota, só não mostra.
                val quem = (chamarApi { api.eu() } as? ResultadoDaChamada.Sucesso)?.dado
                ResultadoDoTeste.Conectado(servidorTemChave = resultado.dado.tem_chave_api, quem = quem)
            }
            is ResultadoDaChamada.Falha -> ResultadoDoTeste.Falhou(resultado.motivo)
        }
    }
}
