package com.example.exotube.domain.model

/**
 * Errores de negocio que la UI sabe explicar al usuario.
 * Heredan de [Exception] para poder viajar dentro de un [Result].
 */
sealed class MediaError(
    /** Si tiene sentido mostrar el botón "Reintentar". */
    val isRetryable: Boolean = false,
    cause: Throwable? = null,
) : Exception(cause) {

    /** El texto compartido no contiene ninguna URL http(s) válida. */
    data object InvalidLink : MediaError()

    /** La URL es válida pero no es de una plataforma soportada. */
    data object UnsupportedPlatform : MediaError()

    /** Video privado, con restricción de edad/login, o eliminado. */
    data object PrivateContent : MediaError()

    /** El enlace existe pero no tiene nada descargable (un perfil, una foto, un tweet sin video…). */
    data object NoMediaFound : MediaError()

    data object NoConnection : MediaError(isRetryable = true)

    /** Cualquier fallo no previsto; guardamos la causa original para depurar. */
    class Unknown(cause: Throwable) : MediaError(isRetryable = true, cause = cause)
}
