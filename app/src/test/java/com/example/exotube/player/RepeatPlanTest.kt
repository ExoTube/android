package com.example.exotube.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RepeatPlan] es Kotlin puro (sin Android), así que se puede probar en el ordenador, sin emulador.
 * Es la pieza que traducen entre sí la pantalla y el servicio: si se rompe, el botón de bucle
 * mostraría un modo y sonaría otro.
 */
class RepeatPlanTest {

    @Test
    fun `el boton recorre los cuatro modos y vuelve al principio`() {
        var plan = RepeatPlan.OFF
        val recorrido = List(RepeatPlan.entries.size) {
            plan = RepeatPlan.after(plan)
            plan
        }

        assertEquals(
            listOf(RepeatPlan.ONCE, RepeatPlan.TWICE, RepeatPlan.FOREVER, RepeatPlan.OFF),
            recorrido,
        )
    }

    @Test
    fun `las repeticiones que publica el servicio vuelven a ser el mismo modo`() {
        RepeatPlan.entries.forEach { plan ->
            assertEquals(plan, RepeatPlan.ofRepeats(plan.repeats))
        }
    }

    /** El servicio va descontando: con "2 veces", tras la primera vuelta quedan 1. */
    @Test
    fun `una repeticion pendiente se muestra como repetir una vez`() {
        assertEquals(RepeatPlan.ONCE, RepeatPlan.ofRepeats(RepeatPlan.TWICE.repeats - 1))
    }

    /** Un número que no corresponde a ningún modo no debe dejar el botón en un estado imposible. */
    @Test
    fun `un valor desconocido se entiende como sin bucle`() {
        assertEquals(RepeatPlan.OFF, RepeatPlan.ofRepeats(7))
        assertEquals(RepeatPlan.OFF, RepeatPlan.ofRepeats(-5))
    }
}
