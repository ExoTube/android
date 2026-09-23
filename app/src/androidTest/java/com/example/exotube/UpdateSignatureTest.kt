package com.example.exotube

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.exotube.data.update.ApkInstaller
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * La comprobación de firma del actualizador, con APK de verdad y el PackageManager de Android.
 *
 * Estas pruebas corren con la versión de depuración, firmada con la llave de depuración de
 * Android Studio. Así se puede probar el "no" con un APK auténtico de ExoTube: uno publicado, que
 * lleva la llave de verdad, es para esta app instalada "otro firmante", igual que lo sería una
 * copia modificada.
 */
@RunWith(AndroidJUnit4::class)
class UpdateSignatureTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val installer = ApkInstaller(context)

    @Test
    fun el_propio_apk_instalado_se_acepta() {
        assertTrue(installer.isSignedLikeThisApp(File(context.packageCodePath)))
    }

    @Test
    fun un_apk_de_otra_app_se_rechaza() {
        val testApk = File(InstrumentationRegistry.getInstrumentation().context.packageCodePath)
        assertFalse(installer.isSignedLikeThisApp(testApk))
    }

    @Test
    fun un_archivo_que_no_es_un_apk_se_rechaza() {
        val fake = File(context.cacheDir, "falso.apk").apply { writeText("esto no es una app") }
        assertFalse(installer.isSignedLikeThisApp(fake))
        fake.delete()
    }

    /** Hay que subirlo antes: adb push ExoTube-1.4-x86_64.apk /data/local/tmp/otra-firma.apk */
    @Test
    fun exotube_con_otra_llave_se_rechaza() {
        val otherKey = File("/data/local/tmp/otra-firma.apk")
        assumeTrue("Falta subir el APK de prueba", otherKey.canRead())
        assertFalse(installer.isSignedLikeThisApp(otherKey))
    }
}
