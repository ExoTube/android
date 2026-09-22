package com.example.exotube.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Qué versión del widget cabe en cada hueco. La normal mide 264 dp de lado: con menos, se
 * saldría y el lanzador la cortaría.
 */
class WidgetLayoutTest {

    /** 3x3 casillas en un teléfono de 4 columnas: unos 270 dp. Cabe la normal. */
    @Test
    fun `con sitio de sobra va la normal`() {
        assertEquals(WidgetLayout.REGULAR, WidgetLayout.forSide(270))
        assertEquals(WidgetLayout.REGULAR, WidgetLayout.forSide(264))
    }

    /** 3x3 en un teléfono de casillas pequeñas: unos 210 dp. Va la compacta. */
    @Test
    fun `en un hueco pequeno va la compacta`() {
        assertEquals(WidgetLayout.COMPACT, WidgetLayout.forSide(263))
        assertEquals(WidgetLayout.COMPACT, WidgetLayout.forSide(210))
    }

    /** Recién añadido, el lanzador aún no ha dicho el tamaño: mejor pequeña que cortada. */
    @Test
    fun `sin saber el tamano va la compacta`() {
        assertEquals(WidgetLayout.COMPACT, WidgetLayout.forSide(null))
    }
}
