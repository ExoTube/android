package com.example.exotube.data.update

import android.content.Context
import androidx.core.content.edit

/**
 * Lo poco que hay que recordar entre arranques sobre las actualizaciones.
 *
 * Son dos cosas y cada una evita una molestia concreta:
 *  - la versión que el usuario dijo "ahora no", para no volver a insistir con esa misma;
 *  - la versión que ya vio estrenada, para mostrar las novedades una sola vez.
 */
class UpdateSettings(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences("actualizaciones", Context.MODE_PRIVATE)

    // --- "Ahora no" ---

    /** true si el usuario ya rechazó ESTA versión; una más nueva vuelve a avisar. */
    fun isDismissed(versionName: String): Boolean =
        preferences.getString(KEY_DISMISSED, null) == versionName

    fun dismiss(versionName: String) {
        preferences.edit { putString(KEY_DISMISSED, versionName) }
    }

    // --- Novedades tras actualizar ---

    /**
     * Llamar una sola vez al arrancar: dice si toca enseñar las novedades y ya lo deja anotado
     * para no repetirlo. La decisión en sí está en [shouldShowWhatsNew].
     */
    fun consumeWhatsNew(currentVersionCode: Int, hasBeenUpdated: Boolean): Boolean {
        val seen = preferences.getInt(KEY_WHATS_NEW_SEEN, NEVER_SEEN)
        if (seen == currentVersionCode) return false
        preferences.edit { putInt(KEY_WHATS_NEW_SEEN, currentVersionCode) }
        return shouldShowWhatsNew(seen, currentVersionCode, hasBeenUpdated)
    }

    private companion object {
        const val KEY_DISMISSED = "version_rechazada"
        const val KEY_WHATS_NEW_SEEN = "novedades_vistas"
    }
}

/**
 * Si toca enseñar las novedades al arrancar.
 *
 * Distinguir "se acaba de actualizar" de "se acaba de instalar" tiene su miga, y por eso está
 * aparte, donde se puede comprobar con tests. Lo normal sería mirar qué versión se vio la última
 * vez, pero eso no vale para quien viene de la 1.1: esa versión no guardaba nada, así que al
 * abrir la 1.2 el dato aparece vacío igual que en una instalación recién hecha. De ahí el
 * segundo dato: [hasBeenUpdated] lo dice Android, y si la app viene de una versión anterior hay
 * novedades que contar aunque no haya nada guardado.
 *
 * Se compara por versionCode (el número interno, que siempre sube) y no por el nombre: así
 * funciona igual si algún día se publica una "1.2.1".
 *
 * @param seenVersionCode la última versión cuyas novedades se enseñaron, o [NEVER_SEEN].
 * @param hasBeenUpdated si Android dice que esta app se instaló encima de otra anterior.
 */
internal fun shouldShowWhatsNew(
    seenVersionCode: Int,
    currentVersionCode: Int,
    hasBeenUpdated: Boolean,
): Boolean = when {
    // Ya se enseñaron las de esta versión: no se repiten en cada arranque.
    seenVersionCode == currentVersionCode -> false
    // Una versión MAS VIEJA que la guardada solo pasa si el usuario instaló una anterior a mano.
    // No son "novedades", así que tampoco se enseñan.
    seenVersionCode > currentVersionCode -> false
    // Guardaba versión: viene de una 1.2 o posterior, seguro que se actualizó.
    seenVersionCode != NEVER_SEEN -> true
    // Sin nada guardado: son novedades solo si viene de una versión anterior (la 1.1 o la 1.0).
    else -> hasBeenUpdated
}

/** Lo que se lee cuando nunca se ha guardado nada, incluida la 1.1, que no guardaba. */
internal const val NEVER_SEEN = 0
