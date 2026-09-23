package com.example.exotube.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.exotube.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los temas de colores. Lo importante es que TODOS se lean bien: un tema bonito con el texto
 * ilegible no sirve. Se mide con el contraste de las normas de accesibilidad (WCAG), donde 4.5
 * es lo mínimo para texto normal y 3 para botones y texto grande.
 */
class AppThemeTest {

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun `los ids no se repiten, porque es lo que se guarda en Ajustes`() {
        val ids = AppTheme.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `sin nada guardado, o con un tema que ya no existe, sale el clásico`() {
        assertEquals(AppTheme.CLASSIC, AppTheme.fromId(null))
        assertEquals(AppTheme.CLASSIC, AppTheme.fromId("tema-borrado"))
        assertEquals(AppTheme.ROCK, AppTheme.fromId("rock"))
    }

    @Test
    fun `en todos los temas el texto se lee bien sobre el fondo y las tarjetas`() {
        AppTheme.entries.forEach { theme ->
            val scheme = theme.colorScheme
            listOf(scheme.background, scheme.surfaceContainer, scheme.surfaceContainerHighest).forEach { surface ->
                assertTrue("${theme.name}: texto", contrast(scheme.onSurface, surface) >= 7f)
                assertTrue("${theme.name}: texto secundario", contrast(scheme.onSurfaceVariant, surface) >= 4.5f)
            }
        }
    }

    @Test
    fun `en todos los temas los botones de color se leen bien`() {
        AppTheme.entries.forEach { theme ->
            val scheme = theme.colorScheme
            assertTrue("${theme.name}: botón", contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
            assertTrue("${theme.name}: acento sobre fondo", contrast(scheme.primary, scheme.background) >= 3f)
            assertTrue("${theme.name}: chip", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
        }
    }
}
