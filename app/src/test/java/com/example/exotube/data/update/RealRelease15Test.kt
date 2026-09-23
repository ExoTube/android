package com.example.exotube.data.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La respuesta de verdad de GitHub el día que se publicó la 1.5, que llega por el actualizador
 * a quien tenga la 1.2, la 1.3 o la 1.4. Ver [RealReleaseTest] para la idea de fondo.
 */
class RealRelease15Test {

    private val json = Json { ignoreUnknownKeys = true }

    private val release: ReleaseDto = json.decodeFromString(
        checkNotNull(javaClass.getResourceAsStream("/github-release-v1.5.json")) {
            "Falta el archivo de prueba con la respuesta de GitHub"
        }.reader().readText(),
    )

    @Test
    fun `la respuesta de GitHub encaja con lo que la app espera`() {
        assertEquals("v1.5", release.tagName)
        assertFalse(release.draft)
        assertFalse(release.prerelease)
        assertEquals(3, release.assets.size)
    }

    /** Quien tenga la 1.4 (o aún la 1.3 o la 1.2) abre la app y tiene que ver la 1.5. */
    @Test
    fun `a quien tiene la 1_4, la 1_3 o la 1_2 se le ofrece la 1_5`() {
        listOf("1.4", "1.3", "1.2").forEach { installed ->
            val update = release.toUpdateOrNull(installed, abis = listOf("arm64-v8a", "armeabi-v7a"))

            assertNotNull(update)
            assertEquals("1.5", update?.versionName)
            assertTrue(update?.downloadUrl?.endsWith("/v1.5/ExoTube-1.5-arm64-v8a.apk") == true)
            assertTrue((update?.sizeBytes ?: 0) > 0)
        }
    }

    @Test
    fun `cada telefono recibe su APK`() {
        assertEquals("ExoTube-1.5-arm64-v8a.apk", release.assets.forAbi(listOf("arm64-v8a", "armeabi-v7a"))?.name)
        assertEquals("ExoTube-1.5-armeabi-v7a.apk", release.assets.forAbi(listOf("armeabi-v7a", "armeabi"))?.name)
        assertEquals("ExoTube-1.5-x86_64.apk", release.assets.forAbi(listOf("x86_64", "x86"))?.name)
    }

    @Test
    fun `las notas llegan limpias a la app`() {
        val notas = release.toUpdateOrNull("1.4", abis = listOf("arm64-v8a"))?.notes.orEmpty()

        assertFalse(notas.contains("#"))
        assertFalse(notas.contains("**"))
        assertTrue(notas.startsWith("Novedades"))
        assertTrue(notas.contains("Temas de colores"))
        assertTrue(notas.contains("e8153b5f7e6d78a0a596a95a11e81c6cd6bb77ec628c1c09652bade7755e4c62"))
    }

    @Test
    fun `a quien ya tiene la 1_5 no se le ofrece nada`() {
        assertNull(release.toUpdateOrNull("1.5", abis = listOf("arm64-v8a")))
    }
}
