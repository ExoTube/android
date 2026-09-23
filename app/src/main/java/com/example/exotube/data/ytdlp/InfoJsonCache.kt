package com.example.exotube.data.ytdlp

import java.io.File
import java.security.MessageDigest

/**
 * Guarda el análisis que hizo yt-dlp para la hoja de descarga, para que la descarga lo reutilice.
 *
 * Sin esto, al elegir una calidad yt-dlp volvía a analizar el enlace desde cero: arrancar Python,
 * pedir la página, resolver el reto de TikTok… Otros dos segundos de "Preparando descarga" para
 * averiguar lo mismo que ya se sabía. Con el análisis guardado, yt-dlp lo lee del archivo
 * (--load-info-json) y se pone a descargar directamente.
 *
 * Los enlaces de video que trae el análisis caducan (en YouTube, a las pocas horas), así que un
 * análisis solo se usa durante [maxAgeMs]. Y si aun así falla, se descarga desde el enlace como
 * siempre: esto solo puede ahorrar tiempo, nunca estropear una descarga.
 */
class InfoJsonCache(
    private val folder: File,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun save(url: String, json: String) {
        folder.mkdirs()
        forgetOld()
        fileFor(url).writeText(json)
    }

    /** El análisis de [url] si existe y aún está fresco; null en cualquier otro caso. */
    fun freshFileFor(url: String): File? = fileFor(url).takeIf { it.isFile && now() - it.lastModified() <= maxAgeMs }

    fun forget(url: String) {
        fileFor(url).delete()
    }

    /** Un nombre por enlace. Se usa su huella SHA-256 porque un enlace no vale como nombre de archivo. */
    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return File(folder, digest.joinToString("") { "%02x".format(it) } + ".json")
    }

    private fun forgetOld() {
        folder.listFiles()?.filter { now() - it.lastModified() > maxAgeMs }?.forEach { it.delete() }
    }

    private companion object {
        /** Media hora: de sobra para elegir la calidad, y lejos de que caduquen los enlaces. */
        const val DEFAULT_MAX_AGE_MS = 30 * 60 * 1_000L
    }
}
