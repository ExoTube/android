package com.example.exotube.data.update

/**
 * Convierte las notas de la publicación de GitHub en texto corriente.
 *
 * Hace falta porque las dos cosas se escriben una sola vez y se leen en dos sitios muy
 * distintos: la página de GitHub, que entiende Markdown y lo pinta bonito, y la hoja de la app,
 * que es texto y nada más. Sin esto, al usuario le aparecería "## Novedades" con las almohadillas
 * y "**Álbumes**" con los asteriscos, que es justo la primera impresión que da esta función.
 *
 * No se convierte Markdown entero: solo lo que de verdad se usa al escribir una publicación.
 */
internal fun plainTextFrom(markdown: String): String = markdown
    .lineSequence()
    .map { it.asPlainLine() }
    .joinToString("\n")
    .trim()

private fun String.asPlainLine(): String {
    val line = trim()
    return when {
        // Los títulos ("## Novedades") pierden las almohadillas pero se quedan como línea suelta.
        line.startsWith("#") -> line.trimStart('#').trim().withoutEmphasis()
        // Las viñetas de Markdown se escriben con guion o asterisco; aquí se ve mejor un punto.
        line.startsWith("- ") || line.startsWith("* ") -> "• " + line.drop(2).trim().withoutEmphasis()
        else -> line.withoutEmphasis()
    }
}

/**
 * Quita las marcas de negrita y cursiva dejando el texto.
 *
 * Se van los asteriscos y los guiones bajos que envuelven palabras, pero NO los que están dentro
 * de una palabra, porque ahí no son formato: "arm64-v8a" o "libc++_shared" tienen que sobrevivir
 * enteros para que se entienda lo que se está contando.
 */
private fun String.withoutEmphasis(): String = replace(EMPHASIS) { match -> match.groupValues[2] }

/** `**negrita**`, `*cursiva*`, `__negrita__` y `_cursiva_`, con el texto dentro en el grupo 2. */
private val EMPHASIS = Regex("""(\*{1,2}|_{1,2})(\S(?:.*?\S)?)\1""")
