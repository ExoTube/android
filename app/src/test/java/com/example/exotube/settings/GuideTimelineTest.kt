package com.example.exotube.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * El guion de la guía animada: que el dedo esté encima de cada botón justo cuando lo "toca", y
 * que el paso resaltado en el texto vaya con lo que se ve.
 */
class GuideTimelineTest {

    private fun assertNear(expected: Float, actual: Float) = assertTrue("$expected ≈ $actual", abs(expected - actual) < 0.001f)

    @Test
    fun `el dedo está sobre cada botón en el momento de tocarlo, en todas las redes`() {
        GuidePlatform.entries.forEach { platform ->
            val stops = listOf(
                GuideTimeline.TAP_SHARE to platform.shareAt,
                GuideTimeline.TAP_MORE to GuideTimeline.MORE_BUTTON,
                GuideTimeline.TAP_EXOTUBE to GuideTimeline.EXOTUBE_ICON,
                GuideTimeline.TAP_QUALITY to GuideTimeline.QUALITY_ROW,
            )
            stops.forEach { (time, target) ->
                val finger = GuideTimeline.fingerAt(time, platform.shareAt)
                assertNear(target.x, finger.x)
                assertNear(target.y, finger.y)
            }
        }
    }

    @Test
    fun `cada toque llega antes de que aparezca lo siguiente`() {
        assertTrue(GuideTimeline.TAP_SHARE < GuideTimeline.PANEL_IN)
        assertTrue(GuideTimeline.TAP_MORE < GuideTimeline.SYSTEM_IN)
        assertTrue(GuideTimeline.TAP_EXOTUBE < GuideTimeline.EXOTUBE_IN)
        assertTrue(GuideTimeline.TAP_QUALITY < GuideTimeline.DOWNLOAD_START)
        assertTrue(GuideTimeline.DOWNLOAD_END < GuideTimeline.TOTAL_MS)
    }

    @Test
    fun `el paso resaltado sigue a la animación`() {
        assertEquals(0, GuideTimeline.stepAt(0))
        assertEquals(0, GuideTimeline.stepAt(GuideTimeline.TAP_SHARE))
        assertEquals(1, GuideTimeline.stepAt(GuideTimeline.TAP_MORE))
        assertEquals(2, GuideTimeline.stepAt(GuideTimeline.TAP_EXOTUBE))
        assertEquals(3, GuideTimeline.stepAt(GuideTimeline.TAP_QUALITY))
        assertEquals(3, GuideTimeline.stepAt(GuideTimeline.TOTAL_MS))
    }

    @Test
    fun `la barra de descarga va de vacía a llena`() {
        assertEquals(0f, GuideTimeline.downloadProgress(GuideTimeline.DOWNLOAD_START - 1))
        assertEquals(0.5f, GuideTimeline.downloadProgress((GuideTimeline.DOWNLOAD_START + GuideTimeline.DOWNLOAD_END) / 2), 0.01f)
        assertEquals(1f, GuideTimeline.downloadProgress(GuideTimeline.DOWNLOAD_END + 1))
    }

    @Test
    fun `cada red tiene sus cuatro pasos`() {
        GuidePlatform.entries.forEach { assertEquals(4, it.steps.size) }
    }
}
