package dev.helmvox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardoLinkTest {

    private fun bytes(hex: String) = hex.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    // Inicio real de la respuesta de ajustes del PackTalk Bold tras cambiar a Bluetooth.
    private val settings = bytes(
        "09 1f 81 00 01 00 02 01 07 04 01 01 05 01 01 07 01 14 09 01 00 0a 01 00 0b 01 00 0c 02 00 00 13 01 00"
    )

    @Test fun readsDmcModeFromSettings() {
        assertArrayEquals(bytes("00"), CardoLink.settingValue(settings, 0x13))
        assertArrayEquals(bytes("00 00"), CardoLink.settingValue(settings, 0x0c))
        assertArrayEquals(bytes("01"), CardoLink.settingValue(bytes("09 03 81 13 01 01"), 0x13))
    }

    @Test fun batteryLevels() {
        val update = { status: String -> bytes("09 03 82 01 00 $status") }
        assertEquals(true, CardoLink.isBatteryUpdate(update("05")))
        assertEquals(75, CardoLink.batteryPercent(update("05")))
        assertEquals(100, CardoLink.batteryPercent(update("09")))
        assertEquals(0, CardoLink.batteryPercent(update("00")))
        assertNull(CardoLink.batteryPercent(update("04")))
        assertEquals(false, CardoLink.isBatteryUpdate(bytes("09 03 82 02 00 05"))) // otro servicio
        assertEquals(false, CardoLink.isBatteryUpdate(bytes("09 03 81 01 00 05"))) // no es aviso de servicio
    }

    @Test fun diagnosisFromRealSettings() {
        // Valores que mandó el PackTalk Bold el primer día (idioma, modo, capacidades, ASR).
        val frame = bytes("09 17 81 07 01 14 13 01 00 16 08 c2 f8 f7 77 01 00 00 00 1a 02 32 64 1b 01 00")
        val d = CardoLink.parseDiagnosis(frame, battery = 75)
        assertEquals(false, d.dmcMode)
        assertEquals(true, d.advancedVoiceSupported)
        assertEquals(false, d.advancedVoiceEnabled)
        assertEquals(50, d.endDetectThreshold)
        assertEquals(100, d.okGoogleThreshold)
        assertEquals(0x14, d.languageCode)
        assertEquals(75, d.batteryPercent)
    }

    @Test fun missingSetting() {
        assertNull(CardoLink.settingValue(settings, 0x99))
        assertNull(CardoLink.settingValue(bytes("09 02 81 13 05"), 0x13)) // longitud que no cabe
    }
}
