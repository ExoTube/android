package com.example.exotube.player

import android.os.Bundle

/**
 * Modos de bucle de ExoTube.
 *
 * Media3 solo sabe de dos: "no repetir" y "repetir esta canción para siempre". "Repetir 1 vez" o
 * "2 veces" es un invento nuestro: [PlaybackService] activa el bucle infinito y va descontando
 * cada repetición hasta llegar a cero, momento en el que lo apaga.
 *
 * Este enum es el idioma común entre la pantalla y el servicio; [repeats] es lo único que viaja
 * entre ellos (0 = sin bucle, -1 = para siempre).
 */
enum class RepeatPlan(val repeats: Int) {
    OFF(0),
    ONCE(1),
    TWICE(2),
    FOREVER(-1);

    companion object {
        /** Siguiente modo al pulsar el botón: sin bucle → 1 → 2 → siempre → sin bucle. */
        fun after(current: RepeatPlan): RepeatPlan = entries[(current.ordinal + 1) % entries.size]

        /** Traduce las repeticiones que quedan (las que publica el servicio) a un modo. */
        fun ofRepeats(repeats: Int): RepeatPlan = entries.firstOrNull { it.repeats == repeats } ?: OFF
    }
}

/**
 * Nombres del comando y del dato que intercambian la pantalla y el servicio.
 *
 * La pantalla envía el comando; el servicio responde publicando en los "extras" de la sesión
 * cuántas repeticiones quedan. Así el botón muestra la verdad aunque el servicio apague el bucle
 * él solo al terminar la cuenta.
 */
internal object RepeatSession {
    const val COMMAND_SET_PLAN = "com.example.exotube.SET_REPEAT_PLAN"
    private const val KEY_REPEATS = "repeats"

    fun bundleOfRepeats(repeats: Int): Bundle = Bundle().apply { putInt(KEY_REPEATS, repeats) }

    fun planOf(bundle: Bundle): RepeatPlan = RepeatPlan.ofRepeats(bundle.getInt(KEY_REPEATS))
}
