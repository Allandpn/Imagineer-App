package com.allan.imagineer.telas.menu

/** Como a biblioteca mostra os livros (CP4, MN4): em capas ou em lista. Guardado no aparelho. */
enum class ModoDaBiblioteca(val rotulo: String) {
    CAPAS("Capas"),
    LISTA("Lista");

    companion object {
        /** O modo guardado; sem nada guardado (ou com um texto que não conhecemos) vale **capas**. */
        fun deTexto(texto: String?): ModoDaBiblioteca = entries.firstOrNull { it.name == texto } ?: CAPAS
    }
}
