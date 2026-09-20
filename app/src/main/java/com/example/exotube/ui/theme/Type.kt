package com.example.exotube.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

// Partimos de la tipografía de Material 3 y solo reforzamos los títulos.
val Typography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}
