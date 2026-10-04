package com.allan.imagineer.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Onde moram os **textos** dos capítulos, um arquivo por capítulo (item 7.0a, passo 1).
 *
 * Um texto de ~100 KB não pertence a um banco de dados: arquivo é simples de medir, de
 * apagar e de ler inteiro. Interface para os testes do repositório poderem trocar o disco
 * por uma versão em memória quando isso simplificar.
 */
interface ArmazemDeTextos {

    /** O texto guardado, ou `null` se não há (ou se o arquivo não pôde ser lido). */
    suspend fun ler(chave: ChaveDoCache, livroId: Int, capituloId: Int): String?

    /** Grava o texto **inteiro ou nada** (regra L3) e devolve quantos bytes ocupa. */
    suspend fun gravar(chave: ChaveDoCache, livroId: Int, capituloId: Int, texto: String): Long

    /** Apaga todos os textos do livro. */
    suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int)
}

/**
 * A versão de verdade, em arquivos sob [raiz].
 *
 * Em produção, [raiz] fica na pasta "sem backup" do app (`noBackupFilesDir`): o Android
 * nunca a envia para a nuvem do Google, o que cumpre a regra A12 sem configuração extra.
 * Estrutura: `<raiz>/<identificador-da-chave>/livro-<id>/capitulo-<id>.txt`.
 */
class ArmazemDeTextosEmArquivos(private val raiz: File) : ArmazemDeTextos {

    private fun pastaDoLivro(chave: ChaveDoCache, livroId: Int) =
        File(raiz, "${chave.identificador}/livro-$livroId")

    private fun arquivo(chave: ChaveDoCache, livroId: Int, capituloId: Int) =
        File(pastaDoLivro(chave, livroId), "capitulo-$capituloId.txt")

    override suspend fun ler(chave: ChaveDoCache, livroId: Int, capituloId: Int): String? =
        withContext(Dispatchers.IO) {
            val arquivo = arquivo(chave, livroId, capituloId)
            if (!arquivo.isFile) return@withContext null
            try {
                arquivo.readText(Charsets.UTF_8)
            } catch (erro: IOException) {
                null
            }
        }

    override suspend fun gravar(
        chave: ChaveDoCache,
        livroId: Int,
        capituloId: Int,
        texto: String,
    ): Long = withContext(Dispatchers.IO) {
        val destino = arquivo(chave, livroId, capituloId)
        destino.parentFile?.mkdirs()
        // Escreve num arquivo temporário e só então o renomeia: se o app for morto no meio,
        // sobra um ".tmp" inofensivo, nunca um texto pela metade com cara de completo (A5).
        val temporario = File(destino.parentFile, "${destino.name}.tmp")
        val bytes = texto.toByteArray(Charsets.UTF_8)
        try {
            temporario.writeBytes(bytes)
            Files.move(temporario.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (erro: IOException) {
            temporario.delete()
            throw erro
        }
        bytes.size.toLong()
    }

    override suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int) {
        withContext(Dispatchers.IO) { pastaDoLivro(chave, livroId).deleteRecursively() }
    }
}
