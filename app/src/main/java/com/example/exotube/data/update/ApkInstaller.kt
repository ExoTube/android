package com.example.exotube.data.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

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
