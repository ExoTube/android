package com.example.exotube.domain.model

/**
 * Una versión de ExoTube más nueva que la instalada, ya lista para descargar.
 *
 * [downloadUrl] apunta al APK que le toca a ESTE teléfono: se publica uno por tipo de procesador
 * y cada uno pesa unos 65 MB, así que bajar el que no es sería tirar los datos del usuario y
 * además no se instalaría.
 */
data class AppUpdate(
    /** Lo que ve el usuario: "1.2". */
    val versionName: String,
    /** Qué trae de nuevo, tal como se escribió en la publicación. */
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** La página de la versión, para quien prefiera descargarla desde el navegador. */
    val pageUrl: String,
)
