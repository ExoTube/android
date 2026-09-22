package com.example.exotube.data.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La respuesta de verdad de GitHub el día que se publicó la 1.4, que llega por el actualizador
 * a quien tenga la 1.2 o la 1.3. Ver [RealReleaseTest] para la idea de fondo.
 */
class RealRelease14Test {

    private val json = Json { ignoreUnknownKeys = true }

    private val release: ReleaseDto = json.decodeFromString(
        checkNotNull(javaClass.getResourceAsStream("/github-release-v1.4.json")) {
            "Falta el archivo de prueba con la respuesta de GitHub"
        }.reader().readText(),
    )

    @Test
    fun `la respuesta de GitHub encaja con lo que la app espera`() {
        assertEquals("v1.4", release.tagName)
        assertFalse(release.draft)
        assertFalse(release.prerelease)
        assertEquals(3, release.assets.size)
    }

    /** Quien tenga la 1.3 (o aún la 1.2) abre la app y tiene que ver la 1.4. */
    @Test
    fun `a quien tiene la 1_3 o la 1_2 se le ofrece la 1_4`() {
        listOf("1.3", "1.2").forEach { installed ->
            val update = release.toUpdateOrNull(installed, abis = listOf("arm64-v8a", "armeabi-v7a"))

            assertNotNull(update)
            assertEquals("1.4", update?.versionName)
            assertTrue(update?.downloadUrl?.endsWith("/v1.4/ExoTube-1.4-arm64-v8a.apk") == true)
            assertTrue((update?.sizeBytes ?: 0) > 0)
        }
    }

    @Test
    fun `cada telefono recibe su APK`() {
        assertEquals("ExoTube-1.4-arm64-v8a.apk", release.assets.forAbi(listOf("arm64-v8a", "armeabi-v7a"))?.name)
        assertEquals("ExoTube-1.4-armeabi-v7a.apk", release.assets.forAbi(listOf("armeabi-v7a", "armeabi"))?.name)
        assertEquals("ExoTube-1.4-x86_64.apk", release.assets.forAbi(listOf("x86_64", "x86"))?.name)
    }

    @Test
    fun `las notas llegan limpias a la app`() {
        val notas = release.toUpdateOrNull("1.3", abis = listOf("arm64-v8a"))?.notes.orEmpty()

        assertFalse(notas.contains("#"))
        assertFalse(notas.contains("**"))
        assertTrue(notas.startsWith("Novedades"))
        assertTrue(notas.contains("El canal del autor"))
    }

    @Test
    fun `a quien ya tiene la 1_4 no se le ofrece nada`() {
        assertNull(release.toUpdateOrNull("1.4", abis = listOf("arm64-v8a")))
    }
}
