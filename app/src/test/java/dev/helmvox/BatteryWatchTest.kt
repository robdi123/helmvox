package dev.helmvox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryWatchTest {

    @Test fun announcesEachThresholdOnce() {
        val w = BatteryWatch()
        assertNull(w.update(50, charging = false))
        assertEquals(20, w.update(20, charging = false))
        assertNull(w.update(19, charging = false))
        assertNull(w.update(15, charging = false))
        assertEquals(10, w.update(10, charging = false))
        assertNull(w.update(5, charging = false))
    }

    @Test fun bigDropAnnouncesLowest() {
        assertEquals(10, BatteryWatch().update(8, charging = false))
    }

    @Test fun rearmsAfterCharging() {
        val w = BatteryWatch()
        assertEquals(20, w.update(18, charging = false))
        assertNull(w.update(30, charging = true))
        assertEquals(20, w.update(19, charging = false))
    }

    @Test fun rearmsWhenLevelRisesWithMargin() {
        val w = BatteryWatch()
        assertEquals(20, w.update(20, charging = false))
        assertNull(w.update(22, charging = false)) // sin margen suficiente: no se rearma
        assertNull(w.update(20, charging = false))
        assertNull(w.update(25, charging = false)) // ahora sí se rearma
        assertEquals(20, w.update(20, charging = false))
    }
}
