package com.allan.imagineer.rede

import kotlinx.serialization.Serializable

/** Um modelo de imagem do catálogo do servidor (`GET /configuracao/modelos-de-imagem`, MI1). Os preços chegam **como texto**. */
@Serializable
@Suppress("PropertyName")
data class ModeloDeImagem(
    /** Como a configuração o guarda: sem prefixo = OpenRouter; `fal:` ou `replicate:` nos outros. */
    val id: String,
    val nome: String,
    val fornecedor: String,
    /** Só OpenRouter: o preço de 1 milhão de tokens de imagem. **Não** é o preço por imagem. */
    val preco_por_milhao_de_tokens: String? = null,
    /** Nulo = ainda sem preço (testar para medir). */
    val preco_por_imagem: String? = null,
    /** `medido` (o que as imagens do modelo já custaram), `tabela` (estimado) ou nulo. */
    val origem_do_preco: String? = null,
    val moderacao: String = "",
    val aceita_referencia: Boolean = false,
    /** `largura×altura` da imagem mais recente do modelo. */
    val resolucao_tipica: String? = null,
    /** É o modelo de imagem padrão. */
    val em_uso: Boolean = false,
    /** Está na lista de escolha ao gerar. */
    val disponivel: Boolean = false,
)

@Serializable
data class CatalogoDeImagem(
    val modelos: List<ModeloDeImagem> = emptyList(),
    val aviso: String? = null,
)

/** O resultado do teste de um modelo (`POST /configuracao/modelos-de-imagem/testar`, MI5). */
@Serializable
@Suppress("PropertyName")
data class TesteDeImagem(
    val modelo: String,
    val largura: Int? = null,
    val altura: Int? = null,
    val tamanho_em_bytes: Long = 0,
    val custo: String? = null,
    /** O custo veio de uma tabela de preços, e não do fornecedor. */
    val estimado: Boolean = false,
    val segundos: Double = 0.0,
    /** A imagem reduzida (JPEG, até 512 px) em base64. */
    val previa_base64: String = "",
)

/** O corpo de `PUT /configuracao/modelos-de-imagem/preco` (PD5): [preco] em dólares por imagem; nulo ou vazio limpa. */
@Serializable
data class PrecoInformado(val modelo: String, val preco: String? = null)

/** O corpo de `PUT /configuracao` que troca a **lista** de modelos de imagem que se pode escolher ao gerar. */
@Serializable
@Suppress("PropertyName")
data class ListaDeModelosDeImagem(val modelos_de_imagem: List<String>)
