package com.example.exotube.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decidir si hay versión nueva es lo único que separa "avisar a tiempo" de "no avisar nunca", y
 * un fallo aquí no se nota hasta meses después, cuando nadie recibe la actualización.
 */
class VersionNamesTest {

    @Test
    fun `una version mayor es mas nueva`() {
        assertTrue(isNewerVersion("1.2", "1.1"))
        assertTrue(isNewerVersion("2.0", "1.9"))
    }

    @Test
    fun `la misma version no es mas nueva`() {
        assertFalse(isNewerVersion("1.2", "1.2"))
    }

    @Test
    fun `una version anterior no es mas nueva`() {
        assertFalse(isNewerVersion("1.1", "1.2"))
    }

    /**
     * El error clásico: comparadas como TEXTO, "1.10" es menor que "1.9" porque el "1" va antes
     * que el "9". La app se quedaría callada desde la 1.10 hasta la 2.0.
     */
    @Test
    fun `la 1_10 es posterior a la 1_9`() {
        assertTrue(isNewerVersion("1.10", "1.9"))
        assertFalse(isNewerVersion("1.9", "1.10"))
    }

    /** Las publicaciones de GitHub se etiquetan "v1.2"; la app se llama "1.2". */
    @Test
    fun `se ignora la v de la etiqueta`() {
        assertTrue(isNewerVersion("v1.2", "1.1"))
        assertFalse(isNewerVersion("v1.1", "1.1"))
    }

    @Test
    fun `las partes que faltan cuentan como cero`() {
        assertFalse(isNewerVersion("1.2.0", "1.2"))
        assertTrue(isNewerVersion("1.2.1", "1.2"))
    }

    /** Ante una etiqueta rara, callarse: mejor no avisar que mandar a instalar cualquier cosa. */
    @Test
    fun `una etiqueta que no son numeros no cuenta como novedad`() {
        assertFalse(isNewerVersion("beta", "1.1"))
        assertFalse(isNewerVersion("v1.2-rc1", "1.1"))
        assertFalse(isNewerVersion("", "1.1"))
    }
}

/**
 * Se publica un APK por tipo de procesador. Bajar el que no toca son 65 MB de datos del usuario
 * tirados y, al final, un "no se pudo instalar".
 */
class AssetForAbiTest {

    private val assets = listOf(
        asset("ExoTube-1.2-arm64-v8a.apk"),
        asset("ExoTube-1.2-armeabi-v7a.apk"),
        asset("ExoTube-1.2-x86_64.apk"),
    )

    private fun asset(name: String) = AssetDto(name = name, downloadUrl = "https://ejemplo/$name", size = 1)

    /** Android da los procesadores de mejor a peor: un teléfono moderno debe llevarse el de 64 bits. */
    @Test
    fun `un telefono moderno se lleva el de 64 bits`() {
        val elegido = assets.forAbi(listOf("arm64-v8a", "armeabi-v7a", "armeabi"))

        assertEquals("ExoTube-1.2-arm64-v8a.apk", elegido?.name)
    }

    @Test
    fun `un telefono antiguo se lleva el de 32 bits`() {
        val elegido = assets.forAbi(listOf("armeabi-v7a", "armeabi"))

        assertEquals("ExoTube-1.2-armeabi-v7a.apk", elegido?.name)
    }

    /**
     * "ExoTube-1.2-x86_64.apk" CONTIENE "x86". Si se buscara por "que lo contenga", un equipo de
     * 32 bits se llevaría el de 64 y Android se negaría a instalarlo.
     */
    @Test
    fun `x86 de 32 bits no se lleva el de x86_64`() {
        assertNull(assets.forAbi(listOf("x86")))
    }

    @Test
    fun `si no hay APK para ese procesador no se elige ninguno`() {
        assertNull(assets.forAbi(listOf("riscv64")))
    }
}

/** Lo que se hace con la respuesta de GitHub antes de enseñarle nada al usuario. */
class ReleaseToUpdateTest {

    private val abis = listOf("arm64-v8a", "armeabi-v7a")

    private fun release(
        tag: String = "v1.2",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<AssetDto> = listOf(
            AssetDto("ExoTube-1.2-arm64-v8a.apk", "https://ejemplo/app.apk", size = 70_000_000),
        ),
    ) = ReleaseDto(
        tagName = tag,
        body = "Videoclips, álbumes y recorte de audio.",
        htmlUrl = "https://github.com/ExoTube/exotube.github.io/releases/tag/$tag",
        prerelease = prerelease,
        draft = draft,
        assets = assets,
    )

    @Test
    fun `una version mas nueva se ofrece con su APK y su peso`() {
        val update = release().toUpdateOrNull(currentVersionName = "1.1", abis = abis)

        assertEquals("1.2", update?.versionName)
        assertEquals("https://ejemplo/app.apk", update?.downloadUrl)
        assertEquals(70_000_000L, update?.sizeBytes)
        assertEquals("Videoclips, álbumes y recorte de audio.", update?.notes)
    }

    @Test
    fun `estando en la ultima version no se ofrece nada`() {
        assertNull(release().toUpdateOrNull(currentVersionName = "1.2", abis = abis))
    }

    /** Un borrador es una publicación a medio escribir: no se le ofrece a nadie. */
    @Test
    fun `un borrador no se ofrece`() {
        assertNull(release(draft = true).toUpdateOrNull(currentVersionName = "1.1", abis = abis))
    }

    @Test
    fun `una version de prueba no se ofrece`() {
        assertNull(release(prerelease = true).toUpdateOrNull(currentVersionName = "1.1", abis = abis))
    }

    /** Sin APK para este teléfono, avisar sería mandarlo a una descarga que no puede instalar. */
    @Test
    fun `sin APK para este telefono no se ofrece nada`() {
        val soloOtros = release(assets = listOf(AssetDto("ExoTube-1.2-x86_64.apk", "https://ejemplo/x.apk")))

        assertNull(soloOtros.toUpdateOrNull(currentVersionName = "1.1", abis = abis))
    }
}

/**
 * Cuándo se enseñan las novedades. El caso que importa de verdad es el de quien viene de la 1.1:
 * esa versión no guardaba nada, así que a primera vista es indistinguible de una instalación
 * recién hecha, y si se confunden, justo la gente a la que hay que contarle lo nuevo no lo ve.
 */
class WhatsNewTest {

    private val v1_2 = 3

    /** El caso 1.1 → 1.2: no hay nada guardado, pero Android dice que se actualizó. */
    @Test
    fun `quien viene de la 1_1 ve las novedades`() {
        assertTrue(shouldShowWhatsNew(NEVER_SEEN, v1_2, hasBeenUpdated = true))
    }

    /** A quien estrena ExoTube no se le cuentan "novedades" de algo que no conocía. */
    @Test
    fun `una instalacion nueva no ve novedades`() {
        assertFalse(shouldShowWhatsNew(NEVER_SEEN, v1_2, hasBeenUpdated = false))
    }

    /** De la 1.2 en adelante ya queda guardado, y basta con comparar. */
    @Test
    fun `quien venga de la 1_2 vera las de la 1_3`() {
        assertTrue(shouldShowWhatsNew(seenVersionCode = 3, currentVersionCode = 4, hasBeenUpdated = false))
    }

    @Test
    fun `no se repiten en cada arranque`() {
        assertFalse(shouldShowWhatsNew(seenVersionCode = v1_2, currentVersionCode = v1_2, hasBeenUpdated = true))
    }

    /** Si alguien instala a mano una versión anterior, eso no son novedades. */
    @Test
    fun `volver a una version anterior no enseña novedades`() {
        assertFalse(shouldShowWhatsNew(seenVersionCode = 4, currentVersionCode = 3, hasBeenUpdated = true))
    }
}

/**
 * Las notas se escriben una vez y se leen en dos sitios: la página de GitHub, que pinta el
 * Markdown, y la hoja de la app, que es texto pelado. Sin limpiarlas, al usuario le saldrían las
 * almohadillas y los asteriscos en la cara.
 */
class ReleaseNotesTest {

    @Test
    fun `los titulos pierden las almohadillas`() {
        assertEquals("Novedades", plainTextFrom("## Novedades"))
        assertEquals("Explorar", plainTextFrom("### Explorar"))
    }

    @Test
    fun `las vinetas se ven como puntos`() {
        assertEquals("• Pantalla completa", plainTextFrom("- Pantalla completa"))
        assertEquals("• Pantalla completa", plainTextFrom("* Pantalla completa"))
    }

    @Test
    fun `la negrita y la cursiva se quedan solo con el texto`() {
        assertEquals("Álbumes", plainTextFrom("**Álbumes**"))
        assertEquals("Álbumes", plainTextFrom("*Álbumes*"))
        assertEquals("ya está aquí", plainTextFrom("ya __está__ aquí"))
    }

    /**
     * Lo que esto protege: los nombres técnicos llevan guiones bajos y asteriscos en medio y no
     * son formato. Si se quitaran, "libc++_shared" quedaría irreconocible.
     */
    @Test
    fun `no toca los simbolos dentro de una palabra`() {
        assertEquals("ExoTube-1.2-arm64-v8a.apk", plainTextFrom("ExoTube-1.2-arm64-v8a.apk"))
        assertEquals("libc++_shared.so", plainTextFrom("libc++_shared.so"))
    }

    @Test
    fun `conserva los saltos de linea y quita los espacios de sobra`() {
        val notas = "## Novedades\n\n- Videoclips\n- Álbumes\n"

        assertEquals("Novedades\n\n• Videoclips\n• Álbumes", plainTextFrom(notas))
    }

    @Test
    fun `un texto sin formato se queda igual`() {
        assertEquals("Mejoras y corrección de fallos.", plainTextFrom("Mejoras y corrección de fallos."))
    }
}
