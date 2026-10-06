package com.allan.imagineer.dados

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// O cofre das chaves de IA da própria pessoa (bloco K, AP2): só no aparelho, cifradas, nunca em texto simples nem no log.

/**
 * Onde ficam as chaves de IA, **por cabeçalho** (o header em que cada uma vai ao servidor, AP3). Interface, para os testes trocarem o Keystore
 * do Android por uma versão em memória.
 */
interface CofreDeChaves {
    /** Todas as chaves guardadas: cabeçalho → chave. */
    fun chaves(): Map<String, String>

    fun gravar(cabecalho: String, chave: String)

    fun remover(cabecalho: String)

    fun tem(cabecalho: String): Boolean = chaves().containsKey(cabecalho)
}

/** O cofre sem Android, para os testes (e para quem ainda não tem cofre). */
class CofreEmMemoria(inicial: Map<String, String> = emptyMap()) : CofreDeChaves {
    private val guardadas = inicial.toMutableMap()
    override fun chaves(): Map<String, String> = guardadas.toMap()
    override fun gravar(cabecalho: String, chave: String) { guardadas[cabecalho] = chave }
    override fun remover(cabecalho: String) { guardadas.remove(cabecalho) }
}

/**
 * Por que uma chave **não** pode ser guardada, ou `null` se pode. Um valor de header só aceita ASCII visível (o OkHttp recusaria o pedido
 * inteiro); sem isso, uma chave colada com um espaço ou uma aspa tipográfica derrubaria **toda** chamada ao servidor.
 */
fun motivoParaNaoGuardarAChave(chave: String): String? = when {
    chave.isBlank() -> "Cole a chave."
    chave.any { it.code !in 0x21..0x7E } -> "A chave tem espaço ou caracteres que não são de uma chave (confira se não colou a mais)."
    else -> null
}

/** A chave para mostrar na tela sem revelá-la: só o começo e o fim ("sk-or…a1b2"). Chave curta demais não mostra nada dela. */
fun mascararChave(chave: String): String =
    if (chave.length < 12) "••••••••" else "${chave.take(5)}…${chave.takeLast(4)}"

/**
 * O cofre de verdade: cada chave é cifrada com **AES-GCM** por uma chave que mora no **Android Keystore** (ela nunca sai dele) e o texto
 * cifrado vai para um arquivo na pasta **sem backup** do app. Se a chave do Keystore se perder (um restauro de aparelho, por exemplo), as
 * chaves guardadas viram ilegíveis e são tratadas como **ausentes**: a pessoa só as cadastra de novo.
 */
class CofreDeChavesDoAndroid(private val arquivo: File) : CofreDeChaves {

    private var lidas: MutableMap<String, String>? = null

    @Synchronized
    override fun chaves(): Map<String, String> = carregadas().toMap()

    @Synchronized
    override fun gravar(cabecalho: String, chave: String) {
        carregadas()[cabecalho] = chave
        salvar()
    }

    @Synchronized
    override fun remover(cabecalho: String) {
        carregadas().remove(cabecalho)
        salvar()
    }

    private fun carregadas(): MutableMap<String, String> {
        lidas?.let { return it }
        val resultado = mutableMapOf<String, String>()
        if (arquivo.isFile) {
            val propriedades = Properties().apply { arquivo.inputStream().use { load(it) } }
            for (cabecalho in propriedades.stringPropertyNames()) {
                decifrar(propriedades.getProperty(cabecalho))?.let { resultado[cabecalho] = it }
            }
        }
        lidas = resultado
        return resultado
    }

    private fun salvar() {
        val propriedades = Properties()
        lidas.orEmpty().forEach { (cabecalho, chave) -> propriedades.setProperty(cabecalho, cifrar(chave)) }
        arquivo.parentFile?.mkdirs()
        arquivo.outputStream().use { propriedades.store(it, null) }
    }

    private fun chaveDoKeystore(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gerador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gerador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gerador.generateKey()
    }

    private fun cifrar(texto: String): String {
        val cifra = Cipher.getInstance(TRANSFORMACAO).apply { init(Cipher.ENCRYPT_MODE, chaveDoKeystore()) }
        return Base64.encodeToString(cifra.iv + cifra.doFinal(texto.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decifrar(guardado: String): String? = try {
        val bytes = Base64.decode(guardado, Base64.NO_WRAP)
        val cifra = Cipher.getInstance(TRANSFORMACAO).apply {
            init(Cipher.DECRYPT_MODE, chaveDoKeystore(), GCMParameterSpec(128, bytes.copyOfRange(0, TAMANHO_DO_IV)))
        }
        String(cifra.doFinal(bytes, TAMANHO_DO_IV, bytes.size - TAMANHO_DO_IV), Charsets.UTF_8)
    } catch (erro: Exception) {
        null // chave do Keystore perdida ou arquivo adulterado: conta como ausente
    }

    private companion object {
        const val ALIAS = "imagineer_chaves_de_ia"
        const val TRANSFORMACAO = "AES/GCM/NoPadding"
        const val TAMANHO_DO_IV = 12
    }
}
