package com.example.exotube.ui

import com.example.exotube.R
import com.example.exotube.settings.phoneKindLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lo que Ajustes dice del teléfono, para saber qué APK bajar de la web. */
class PhoneKindTest {

    @Test
    fun `un teléfono moderno es de 64 bits`() {
        assertEquals(R.string.phone_kind_arm64, phoneKindLabel(listOf("arm64-v8a", "armeabi-v7a", "armeabi")))
    }

    @Test
    fun `con Android de 32 bits manda el primero, aunque el procesador sea de 64`() {
        assertEquals(R.string.phone_kind_arm32, phoneKindLabel(listOf("armeabi-v7a", "armeabi")))
    }

    @Test
    fun `el emulador es Intel`() {
        assertEquals(R.string.phone_kind_intel, phoneKindLabel(listOf("x86_64", "arm64-v8a")))
    }

    @Test
    fun `sin datos no se inventa nada`() {
        assertEquals(R.string.phone_kind_unknown, phoneKindLabel(emptyList()))
    }
}
