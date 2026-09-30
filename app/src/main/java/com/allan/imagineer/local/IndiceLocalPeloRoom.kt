package com.allan.imagineer.local

import com.allan.imagineer.rede.LivroDetalhe
import com.allan.imagineer.rede.jsonDoImagineer
import kotlinx.serialization.SerializationException

/** A implementação de verdade do [IndiceLocal], sobre o banco Room. */
class IndiceLocalPeloRoom(
    private val dao: DaoLocal,
    private val agora: () -> Long = System::currentTimeMillis,
) : IndiceLocal {

    override suspend fun livro(chave: ChaveDoCache, livroId: Int): LivroGuardado? {
        val linha = dao.livro(chave.identificador, livroId) ?: return null
        val detalhe = try {
            jsonDoImagineer.decodeFromString<LivroDetalhe>(linha.detalheJson)
        } catch (erro: SerializationException) {
            // Formato que o app não entende mais (regra L8): vale como "não tenho".
            return null
        }
        return LivroGuardado(linha.revisao, detalhe)
    }

    override suspend fun guardarLivro(chave: ChaveDoCache, detalhe: LivroDetalhe) {
        dao.guardarLivro(
            LivroLocal(
                chave = chave.identificador,
                livroId = detalhe.id,
                revisao = detalhe.revisao,
                detalheJson = jsonDoImagineer.encodeToString(LivroDetalhe.serializer(), detalhe),
                guardadoEm = agora(),
            ),
        )
    }

    override suspend fun texto(chave: ChaveDoCache, capituloId: Int): TextoGuardado? =
        dao.texto(chave.identificador, capituloId)
            ?.let { TextoGuardado(it.capituloId, it.livroId, it.bytes) }

    override suspend fun registrarTexto(chave: ChaveDoCache, texto: TextoGuardado) {
        dao.guardarTexto(
            TextoLocal(chave.identificador, texto.capituloId, texto.livroId, texto.bytes, agora()),
        )
    }

    override suspend fun apagarLivro(chave: ChaveDoCache, livroId: Int) {
        dao.apagarLivro(chave.identificador, livroId)
    }
}
