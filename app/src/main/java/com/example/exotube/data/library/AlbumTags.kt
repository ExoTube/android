package com.example.exotube.data.library

/**
 * MediaStore miente un poco con los álbumes.
 *
 * Cuando un archivo de audio no trae el álbum etiquetado, Android **no** deja la columna vacía:
 * la rellena con el nombre de la carpeta donde está guardado. Por eso, sin esto, la pantalla de
 * Álbumes mostraría discos llamados "Music", "Download" o "ExoTube", que no son álbumes sino
 * sitios del teléfono.
 *
 * La forma de detectarlo es comparar: si el "álbum" coincide con la carpeta, no es un álbum.
 * Un disco que de verdad se llamara igual que su carpeta se nos escaparía, pero eso es rarísimo
 * y el daño es que aparezca en "Randoms".
 */
internal fun albumTagOrNull(album: String?, folderName: String?): String? {
    val tag = album?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (tag == UNKNOWN_TAG) return null
    if (folderName != null && tag.equals(folderName.trim(), ignoreCase = true)) return null
    return tag
}

/**
 * El nombre de la carpeta que contiene el archivo.
 *
 * Android da la ruta de dos maneras según la versión, y hay que tratar las dos:
 *  - Android 10+: la carpeta, ya suelta ("Music/ExoTube/").
 *  - Android 8-9: la ruta completa del archivo ("/storage/emulated/0/Music/ExoTube/tema.mp3").
 */
internal fun folderNameFrom(path: String?, isRelativePath: Boolean): String? {
    val cleaned = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val directory = if (isRelativePath) cleaned else cleaned.substringBeforeLast('/', missingDelimiterValue = "")
    return directory.trimEnd('/').substringAfterLast('/').takeIf { it.isNotEmpty() }
}

/** Valor que pone MediaStore cuando el archivo no trae el dato etiquetado. */
internal const val UNKNOWN_TAG = "<unknown>"
