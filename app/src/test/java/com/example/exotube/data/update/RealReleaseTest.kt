package com.example.exotube.data.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsea la respuesta DE VERDAD de GitHub, guardada tal cual el día que se publicó la 1.2.
 *
 * Los demás tests usan datos inventados por mí, y eso tiene un punto ciego: comprueban que mi
 * código hace lo que yo creo, no que encaje con lo que GitHub manda. Si algún día GitHub cambia
 * el nombre de un campo, o si alguien renombra los APK de una publicación, esto salta aquí y no
 * en el teléfono de alguien que se queda sin recibir actualizaciones y nunca se entera.
 */
class RealReleaseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val release: ReleaseDto = json.decodeFromString(
        checkNotNull(javaClass.getResourceAsStream("/github-release-v1.2.json")) {
            "Falta el archivo de prueba con la respuesta de GitHub"
        }.reader().readText(),
    )

    @Test
    fun `la respuesta de GitHub encaja con lo que la app espera`() {
        assertEquals("v1.2", release.tagName)
        assertFalse(release.draft)
        assertFalse(release.prerelease)
        assertEquals(3, release.assets.size)
    }

    /** El caso real: alguien con la 1.1 abre la app y tiene que ver la 1.2. */
    @Test
    fun `a quien tiene la 1_1 se le ofrece la 1_2`() {
        val update = release.toUpdateOrNull("1.1", abis = listOf("arm64-v8a", "armeabi-v7a"))

        assertNotNull(update)
        assertEquals("1.2", update?.versionName)
        assertTrue(update?.downloadUrl?.endsWith("ExoTube-1.2-arm64-v8a.apk") == true)
        assertTrue("el peso tiene que venir para poder avisar de cuánto ocupa", (update?.sizeBytes ?: 0) > 0)
    }

    /** Los tres APK publicados cubren los procesadores que existen hoy en Android. */
    @Test
    fun `hay APK para cada tipo de telefono`() {
        val telefonoModerno = listOf("arm64-v8a", "armeabi-v7a", "armeabi")
        val telefonoAntiguo = listOf("armeabi-v7a", "armeabi")
        val emulador = listOf("x86_64", "x86")

        assertEquals("ExoTube-1.2-arm64-v8a.apk", release.assets.forAbi(telefonoModerno)?.name)
        assertEquals("ExoTube-1.2-armeabi-v7a.apk", release.assets.forAbi(telefonoAntiguo)?.name)
        assertEquals("ExoTube-1.2-x86_64.apk", release.assets.forAbi(emulador)?.name)
    }

    /** Las notas se escriben en Markdown para la web; en la app se leen como texto. */
    @Test
    fun `las notas llegan limpias a la app`() {
        val notas = release.toUpdateOrNull("1.1", abis = listOf("arm64-v8a"))?.notes.orEmpty()

        assertFalse("no deben verse las almohadillas de los títulos", notas.contains("#"))
        assertFalse("no deben verse los asteriscos de las negritas", notas.contains("**"))
        assertTrue(notas.startsWith("Novedades"))
        assertTrue(notas.contains("• ExoTube-1.2-arm64-v8a.apk"))
    }

    /** Quien ya está en la 1.2 no tiene que ver ningún aviso. */
    @Test
    fun `a quien ya tiene la 1_2 no se le ofrece nada`() {
        assertEquals(null, release.toUpdateOrNull("1.2", abis = listOf("arm64-v8a")))
    }
}
