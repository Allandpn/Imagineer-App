package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

// Os nomes dos campos são os do JSON do backend (item 7.3a).

/** O que `GET /capitulos/{id}/artefatos` devolve (item 6.8): os ícones a desenhar sobre o texto. */
@Serializable
data class ArtefatosDoCapitulo(
    val artefatos: List<Artefato> = emptyList(),
)

/**
 * Um ícone sobre o texto do capítulo (item 7.5b). [tipo_do_elemento] escolhe o ícone e
 * [situacao] o mostra mais ou menos "cheio": a sugestão ainda não confirmada fica só no contorno.
 */
@Serializable
@Suppress("PropertyName")
data class Artefato(
    /** `ELEMENTO` ou `CENA`. */
    val tipo: String,
    /** Só nos de `ELEMENTO`: `PERSONAGEM`, `AMBIENTE`, `OBJETO`, `CRIATURA`, `GRUPO`, `VEICULO` ou `EDIFICACAO`. */
    val tipo_do_elemento: String? = null,
    val sugestao_id: Int? = null,
    val frame_id: Int? = null,
    val rotulo: String,
    /** Início do parágrafo da primeira menção, em unidades UTF-16 desde o começo do texto; nulo = sem posição. */
    val posicao_no_texto: Int? = null,
    /** `SUGERIDO`, `CONFIRMADO`, `PROMPT_PRONTO` ou `ILUSTRADO`. */
    val situacao: String,
    val imagem_id: Int? = null,
    /** O formato da imagem real, para escolher o quadro no texto (I1): `RETRATO` ou `PAISAGEM`; nulo sem dimensões (I7). */
    val imagem_orientacao: String? = null,
    val imagem_largura: Int? = null,
    val imagem_altura: Int? = null,
)

/**
 * O que o leitor do capítulo precisa para desenhar os ícones. Interface, para o ViewModel ser testado
 * com uma versão falsa. **Nunca chama a IA**: é só leitura.
 */
interface RepositorioDeArtefatos {
    /** `GET /capitulos/{id}/artefatos`. */
    suspend fun ler(capituloId: Int): ResultadoDaChamada<List<Artefato>>
}

/** A implementação de verdade, sobre o Retrofit. */
class RepositorioDeArtefatosPeloRetrofit(
    private val provedor: ProvedorDeApi,
) : RepositorioDeArtefatos {

    override suspend fun ler(capituloId: Int): ResultadoDaChamada<List<Artefato>> {
        val api = provedor.obter() ?: return provedor.semServidor()
        return chamarApi { api.artefatos(capituloId).artefatos }
    }
}
