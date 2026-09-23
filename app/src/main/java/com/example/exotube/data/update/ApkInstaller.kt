package com.example.exotube.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.security.MessageDigest

/**
 * Entrega el APK descargado al instalador de Android.
 *
 * Aquí conviene tener claro qué NO puede hacer una app: instalarse sola. Android siempre muestra
 * su propia pantalla de confirmación, y además exige que el usuario haya dado permiso a ExoTube
 * para instalar aplicaciones. No es un obstáculo que se pueda saltar, y está bien que sea así:
 * si una app pudiera reemplazarse a sí misma sin preguntar, cualquiera podría colarte otra cosa.
 *
 * Lo que sí se hace es dejarlo en un solo toque y explicar qué va a pasar.
 */
class ApkInstaller(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Dónde se guarda el APK: la carpeta privada de la app en el almacenamiento externo.
     *
     * No va a Descargas a propósito: ahí quedaría un archivo de 65 MB que el usuario tendría que
     * borrar a mano. Aquí se borra solo al desinstalar la app, y se puede reemplazar sin permisos.
     */
    fun destinationFor(versionName: String): File {
        val folder = File(appContext.getExternalFilesDir(null), FOLDER).apply { mkdirs() }
        return File(folder, "ExoTube-$versionName.apk")
    }

    /** Borra descargas de versiones anteriores que quedaron a medias o ya se instalaron. */
    fun clearOldDownloads(keep: File? = null) {
        File(appContext.getExternalFilesDir(null), FOLDER)
            .listFiles()
            ?.filter { it != keep }
            ?.forEach { it.delete() }
    }

    /**
     * Android no deja pasar rutas de archivo entre apps, así que el instalador recibe una Uri
     * "content://" servida por FileProvider, con permiso de lectura solo para este envío.
     */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * true solo si [apk] es ExoTube y está firmado con la MISMA llave que la app instalada.
     *
     * La firma es lo que de verdad protege al usuario de una copia con virus. Cualquiera puede
     * bajarse el código de ExoTube, cambiarlo y compilarlo, pero no puede firmarlo con nuestra
     * llave (el archivo exotube.jks, que nunca sale de este ordenador). Android ya se niega a
     * instalar una actualización con otra firma; aquí lo comprobamos ANTES, para no enseñar
     * siquiera el botón de instalar si alguien llegara a colar un APK falso en GitHub.
     */
    fun isSignedLikeThisApp(apk: File): Boolean {
        val packageManager = appContext.packageManager
        val downloaded = packageManager.getPackageArchiveInfo(apk.path, signatureFlags) ?: return false
        if (downloaded.packageName != appContext.packageName) return false
        val installed = packageManager.getPackageInfo(appContext.packageName, signatureFlags)
        return sameSigners(signerDigests(downloaded), signerDigests(installed))
    }

    private val signatureFlags: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }

    /** La huella SHA-256 de cada certificado que firma el paquete. */
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        return signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** false si el usuario todavía no ha autorizado a ExoTube a instalar aplicaciones. */
    fun canInstall(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    /** Los ajustes de Android donde se concede ese permiso, ya abiertos en ExoTube. */
    fun permissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${appContext.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val FOLDER = "actualizaciones"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

/**
 * Si dos paquetes los firma la misma gente: exactamente los mismos certificados. Un conjunto
 * vacío (no se pudo leer la firma) nunca vale: ante la duda, no se instala.
 */
internal fun sameSigners(downloaded: Set<String>, installed: Set<String>): Boolean =
    downloaded.isNotEmpty() && downloaded == installed
