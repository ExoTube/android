package com.example.exotube.data.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La respuesta de verdad de GitHub el día que se publicó la 1.3.
 *
 * Esta publicación es especial: es la primera que llega por el actualizador de dentro de la app
 * (la 1.2 fue la que lo trajo). Si aquí algo no encajara, nadie con la 1.2 se enteraría nunca de
 * que hay versión nueva. Ver [RealReleaseTest] para la idea de fondo.
 */
class RealRelease13Test {

    private val json = Json { ignoreUnknownKeys = true }

    private val release: ReleaseDto = json.decodeFromString(
        checkNotNull(javaClass.getResourceAsStream("/github-release-v1.3.json")) {
            "Falta el archivo de prueba con la respuesta de GitHub"
        }.reader().readText(),
    )

    @Test
    fun `la respuesta de GitHub encaja con lo que la app espera`() {
        assertEquals("v1.3", release.tagName)
        assertFalse(release.draft)
        assertFalse(release.prerelease)
        assertEquals(3, release.assets.size)
    }

    /** El caso que importa: alguien con la 1.2 abre la app y tiene que ver la 1.3. */
    @Test
    fun `a quien tiene la 1_2 se le ofrece la 1_3`() {
        val update = release.toUpdateOrNull("1.2", abis = listOf("arm64-v8a", "armeabi-v7a"))

        assertNotNull(update)
        assertEquals("1.3", update?.versionName)
        assertTrue(update?.downloadUrl?.endsWith("/v1.3/ExoTube-1.3-arm64-v8a.apk") == true)
        assertTrue((update?.sizeBytes ?: 0) > 0)
    }

    @Test
    fun `cada telefono recibe su APK`() {
        assertEquals("ExoTube-1.3-arm64-v8a.apk", release.assets.forAbi(listOf("arm64-v8a", "armeabi-v7a"))?.name)
        assertEquals("ExoTube-1.3-armeabi-v7a.apk", release.assets.forAbi(listOf("armeabi-v7a", "armeabi"))?.name)
        assertEquals("ExoTube-1.3-x86_64.apk", release.assets.forAbi(listOf("x86_64", "x86"))?.name)
    }

    @Test
    fun `las notas llegan limpias a la app`() {
        val notas = release.toUpdateOrNull("1.2", abis = listOf("arm64-v8a"))?.notes.orEmpty()

        assertFalse(notas.contains("#"))
        assertFalse(notas.contains("**"))
        assertTrue(notas.startsWith("Novedades"))
        assertTrue(notas.contains("Widget tocadiscos"))
    }

    @Test
    fun `a quien ya tiene la 1_3 no se le ofrece nada`() {
        assertNull(release.toUpdateOrNull("1.3", abis = listOf("arm64-v8a")))
    }
}
