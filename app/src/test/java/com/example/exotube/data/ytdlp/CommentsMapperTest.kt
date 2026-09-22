package com.example.exotube.data.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * yt-dlp devuelve los comentarios y sus respuestas mezclados en una sola lista plana, con un
 * campo `parent` que dice de quién cuelga cada uno. Si se pintaran tal cual, las respuestas
 * aparecerían sueltas entre los comentarios principales y no habría forma de seguir el hilo.
 */
class CommentsMapperTest {

    private fun comment(
        id: String,
        text: String = "hola",
        parent: String? = "root",
        author: String? = "alguien",
        likes: Long? = null,
        isUploader: Boolean? = null,
    ) = YtDlpCommentDto(
        id = id,
        text = text,
        author = author,
        likeCount = likes,
        authorIsUploader = isUploader,
        parent = parent,
    )

    @Test
    fun `solo salen los comentarios principales`() {
        val dto = YtDlpCommentsDto(
            listOf(
                comment("a"),
                comment("a.1", parent = "a"),
                comment("a.2", parent = "a"),
                comment("b"),
            ),
        )

        assertEquals(listOf("a", "b"), dto.toComments().map { it.id })
    }

    /** Las respuestas no se pierden: se cuentan y la fila dice cuántas hay. */
    @Test
    fun `las respuestas se cuentan en su comentario`() {
        val dto = YtDlpCommentsDto(
            listOf(comment("a"), comment("a.1", parent = "a"), comment("a.2", parent = "a"), comment("b")),
        )

        val comments = dto.toComments()
        assertEquals(2, comments.first { it.id == "a" }.replyCount)
        assertEquals(0, comments.first { it.id == "b" }.replyCount)
    }

    /** Algunas respuestas llegan sin `parent`; entonces son principales y así se tratan. */
    @Test
    fun `sin parent se considera principal`() {
        val dto = YtDlpCommentsDto(listOf(comment("a", parent = null)))

        assertEquals(1, dto.toComments().size)
    }

    @Test
    fun `lo del dueno del canal viene marcado`() {
        val dto = YtDlpCommentsDto(listOf(comment("a", isUploader = true), comment("b")))

        val comments = dto.toComments()
        assertTrue(comments.first { it.id == "a" }.isFromCreator)
        assertFalse(comments.first { it.id == "b" }.isFromCreator)
    }

    /** Un comentario sin texto no se puede enseñar; uno sin autor sí, con un nombre genérico. */
    @Test
    fun `se descarta lo que no se puede pintar`() {
        val dto = YtDlpCommentsDto(
            listOf(
                comment("a", text = ""),
                comment("b", text = "   "),
                YtDlpCommentDto(id = null, text = "sin id", parent = "root"),
                comment("d", author = null, text = "sin autor"),
            ),
        )

        val comments = dto.toComments()
        assertEquals(listOf("d"), comments.map { it.id })
        assertTrue(comments.single().author.isNotBlank())
    }

    /** Cero "me gusta" no se enseña: una fila llena de ceros solo estorba. */
    @Test
    fun `cero me gusta no se enseña`() {
        val dto = YtDlpCommentsDto(listOf(comment("a", likes = 0), comment("b", likes = 42)))

        val comments = dto.toComments()
        assertEquals(null, comments.first { it.id == "a" }.likeCount)
        assertEquals(42L, comments.first { it.id == "b" }.likeCount)
    }

    /** Un video con los comentarios desactivados: lista vacía, y eso no es un error. */
    @Test
    fun `sin comentarios no revienta`() {
        assertTrue(YtDlpCommentsDto().toComments().isEmpty())
    }

    @Test
    fun `el texto se limpia de espacios sobrantes`() {
        val dto = YtDlpCommentsDto(listOf(comment("a", text = "  Gracias totales.  ")))

        assertEquals("Gracias totales.", dto.toComments().single().text)
    }
}
