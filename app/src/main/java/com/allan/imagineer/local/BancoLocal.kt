package com.allan.imagineer.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import java.io.File

/**
 * Um livro visto. A lista de capítulos e os metadados vão **inteiros em JSON**: são sempre
 * lidos e gravados juntos, nunca consultados campo a campo (item 7.0a, passo 1).
 */
@Entity(tableName = "livro_local", primaryKeys = ["chave", "livroId"])
data class LivroLocal(
    val chave: String,
    val livroId: Int,
    val revisao: Int,
    val detalheJson: String,
    val guardadoEm: Long,
)

/** O registro de um texto de capítulo que está em arquivo. */
@Entity(tableName = "texto_local", primaryKeys = ["chave", "capituloId"])
data class TextoLocal(
    val chave: String,
    val capituloId: Int,
    val livroId: Int,
    val bytes: Long,
    val guardadoEm: Long,
)

/** O registro de que um livro está **Baixado** (PL4); [imagensIds] são os ids separados por vírgula. */
@Entity(tableName = "download_local", primaryKeys = ["chave", "livroId"])
data class DownloadLocal(
    val chave: String,
    val livroId: Int,
    val baixadoEm: Long,
    val bytes: Long,
    val imagensIds: String,
)

/** As consultas ao banco. Classe abstrata para poder ter uma função com `@Transaction`. */
@Dao
abstract class DaoLocal {

    @Query("SELECT * FROM livro_local WHERE chave = :chave AND livroId = :livroId")
    abstract suspend fun livro(chave: String, livroId: Int): LivroLocal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun guardarLivro(livro: LivroLocal)

    @Query("SELECT * FROM texto_local WHERE chave = :chave AND capituloId = :capituloId")
    abstract suspend fun texto(chave: String, capituloId: Int): TextoLocal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun guardarTexto(texto: TextoLocal)

    @Query("SELECT * FROM livro_local WHERE chave = :chave")
    abstract suspend fun livros(chave: String): List<LivroLocal>

    @Query("SELECT * FROM texto_local WHERE chave = :chave")
    abstract suspend fun textos(chave: String): List<TextoLocal>

    @Query("SELECT * FROM download_local WHERE chave = :chave")
    abstract suspend fun downloads(chave: String): List<DownloadLocal>

    @Query("SELECT * FROM download_local WHERE chave = :chave AND livroId = :livroId")
    abstract suspend fun download(chave: String, livroId: Int): DownloadLocal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun guardarDownload(download: DownloadLocal)

    @Query("DELETE FROM download_local WHERE chave = :chave AND livroId = :livroId")
    abstract suspend fun apagarDownload(chave: String, livroId: Int)

    @Query("DELETE FROM livro_local WHERE chave = :chave AND livroId = :livroId")
    abstract suspend fun apagarLivroGuardado(chave: String, livroId: Int)

    @Query("DELETE FROM texto_local WHERE chave = :chave AND livroId = :livroId")
    abstract suspend fun apagarTextosDoLivro(chave: String, livroId: Int)

    /** As duas exclusões juntas: ou acontecem as duas, ou nenhuma. */
    @Transaction
    open suspend fun apagarLivro(chave: String, livroId: Int) {
        apagarLivroGuardado(chave, livroId)
        apagarTextosDoLivro(chave, livroId)
    }
}

/**
 * O banco do aparelho. `version = 1` e sem exportar esquema: a cópia é descartável (regras
 * A10 e L8) — se o formato mudar, o banco é apagado e refeito, sem migração à mão.
 */
@Database(
    entities = [LivroLocal::class, TextoLocal::class, DownloadLocal::class],
    version = 4,
    exportSchema = false,
)
abstract class BancoLocal : RoomDatabase() {

    abstract fun dao(): DaoLocal

    companion object {
        /**
         * Abre o banco na pasta "sem backup" do app (`noBackupFilesDir`), que o Android não
         * envia para a nuvem (regra A12).
         */
        fun abrir(contexto: Context): BancoLocal =
            Room.databaseBuilder(
                contexto,
                BancoLocal::class.java,
                File(contexto.noBackupFilesDir, "imagineer.db").path,
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
