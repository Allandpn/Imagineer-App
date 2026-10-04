package com.allan.imagineer.dados

import kotlinx.coroutines.flow.flowOf
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * O que o app guarda no próprio aparelho (item 7.0: só o endereço do servidor e,
 * mais tarde, a chave de API pessoal).
 *
 * É uma interface, e não uma classe direta, para os testes poderem trocar o
 * DataStore por uma versão falsa em memória — o ViewModel só conhece este contrato.
 */
interface ArmazenamentoDeConfiguracao {
    /** O endereço salvo, ou `null` se o usuário ainda não configurou. Emite a cada mudança. */
    val urlDoServidor: Flow<String?>

    /** Grava o endereço, que deve vir já normalizado por [normalizarUrl]. */
    suspend fun salvarUrlDoServidor(url: String)

    /** Como a biblioteca mostra os livros (`CAPAS` ou `LISTA`); `null` = nada guardado (vale capas). Padrão: sem armazenamento. */
    val modoDaBiblioteca: Flow<String?> get() = flowOf(null)

    suspend fun salvarModoDaBiblioteca(modo: String) {}

    /** A cor de destaque escolhida (o nome de um `DestaqueEscolhido`); `null` = nada guardado (vale o âmbar). */
    val corDeDestaque: Flow<String?> get() = flowOf(null)

    suspend fun salvarCorDeDestaque(nome: String) {}
}

private val Context.armazenamento: DataStore<Preferences> by preferencesDataStore(name = "configuracao")

/** A implementação de verdade, sobre o Jetpack DataStore (Preferences). */
class ArmazenamentoNoDataStore(private val contexto: Context) : ArmazenamentoDeConfiguracao {

    override val urlDoServidor: Flow<String?> =
        contexto.armazenamento.data.map { preferencias -> preferencias[CHAVE_URL] }

    override suspend fun salvarUrlDoServidor(url: String) {
        contexto.armazenamento.edit { preferencias -> preferencias[CHAVE_URL] = url }
    }

    override val modoDaBiblioteca: Flow<String?> =
        contexto.armazenamento.data.map { preferencias -> preferencias[CHAVE_MODO_DA_BIBLIOTECA] }

    override suspend fun salvarModoDaBiblioteca(modo: String) {
        contexto.armazenamento.edit { preferencias -> preferencias[CHAVE_MODO_DA_BIBLIOTECA] = modo }
    }

    override val corDeDestaque: Flow<String?> =
        contexto.armazenamento.data.map { preferencias -> preferencias[CHAVE_COR_DE_DESTAQUE] }

    override suspend fun salvarCorDeDestaque(nome: String) {
        contexto.armazenamento.edit { preferencias -> preferencias[CHAVE_COR_DE_DESTAQUE] = nome }
    }

    private companion object {
        val CHAVE_COR_DE_DESTAQUE = stringPreferencesKey("cor_de_destaque")
        val CHAVE_URL = stringPreferencesKey("url_do_servidor")
        val CHAVE_MODO_DA_BIBLIOTECA = stringPreferencesKey("modo_da_biblioteca")
    }
}
