package com.example.exotube.data.update

import android.os.Build
import com.example.exotube.domain.model.AppUpdate
import com.example.exotube.domain.repository.UpdateRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Busca la versión nueva en las publicaciones de GitHub.
 *
 * Se usa GitHub y no un servidor propio porque la publicación ya existe: es donde se suben los
 * APK de cada versión. Así no hay nada que mantener, nada que pagar y nada que se pueda caer
 * aparte de GitHub. Tampoco hace falta cuenta ni clave: la consulta es pública.
 */
class GitHubUpdateRepository(
    private val currentVersionName: String,
    private val abis: List<String> = Build.SUPPORTED_ABIS.toList(),
) : UpdateRepository {

    // Se crea al primer uso y se reutiliza: abrir un cliente HTTP por consulta es un desperdicio.
    private val client by lazy { HttpClient(OkHttp) }

    private val json = Json { ignoreUnknownKeys = true } // GitHub devuelve decenas de campos

    override suspend fun findUpdate(): Result<AppUpdate?> = withContext(Dispatchers.IO) {
        try {
            val body = client.get(LATEST_RELEASE_URL) {
                // Sin esto GitHub puede devolver un formato distinto en el futuro.
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }.bodyAsText()

            val release = json.decodeFromString<ReleaseDto>(body)
            Result.success(release.toUpdateOrNull(currentVersionName, abis))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin internet, GitHub caído o respuesta inesperada: no es un error que valga la pena
            // enseñar. Quien abrió la app no pidió buscar actualizaciones.
            Result.failure(e)
        }
    }

    private companion object {
        /**
         * Las publicaciones viven en el repositorio de la página web, que es donde están los APK
         * de la 1.0 y la 1.1 y a donde apuntan los enlaces de descarga. Cambiarlo de sitio
         * rompería esos enlaces.
         */
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/ExoTube/exotube.github.io/releases/latest"
    }
}

// --- Lo que devuelve GitHub ---

@Serializable
internal data class ReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
internal data class AssetDto(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

/** null cuando ya estamos en la última versión, o cuando no hay APK para este teléfono. */
internal fun ReleaseDto.toUpdateOrNull(currentVersionName: String, abis: List<String>): AppUpdate? {
    if (draft || prerelease) return null
    if (!isNewerVersion(tagName, currentVersionName)) return null
    val asset = assets.forAbi(abis) ?: return null
    return AppUpdate(
        versionName = tagName.removePrefix("v"),
        notes = body?.trim().orEmpty(),
        downloadUrl = asset.downloadUrl,
        sizeBytes = asset.size,
        pageUrl = htmlUrl,
    )
}

/**
 * El APK que le toca a este teléfono.
 *
 * Los APK se llaman "ExoTube-1.2-arm64-v8a.apk", con el procesador al final del nombre. Android
 * da la lista de procesadores que entiende ORDENADA de mejor a peor: un teléfono moderno acepta
 * tanto arm64 como arm de 32 bits, y hay que darle el de 64, que es el que aprovecha. Por eso se
 * recorre en ese orden y se coge el primero que exista.
 *
 * Se compara con el final del nombre y no "que lo contenga" porque los nombres de los
 * procesadores se solapan: "ExoTube-1.2-x86_64.apk" CONTIENE "x86", así que un teléfono de 32
 * bits se llevaría el de 64 y luego Android se negaría a instalarlo.
 */
internal fun List<AssetDto>.forAbi(abis: List<String>): AssetDto? {
    abis.forEach { abi ->
        firstOrNull { it.name.endsWith("-$abi.apk", ignoreCase = true) }?.let { return it }
    }
    return null
}
