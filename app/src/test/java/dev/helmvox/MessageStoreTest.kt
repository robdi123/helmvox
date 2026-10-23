package dev.helmvox

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageStoreTest {
    @Test fun speakableNames() {
        assertEquals(
            "SERVICIOS Y VENTAS CONDOMINIO, Ana Maria",
            MessageStore.speakableName("‎🌸SERVICIOS  Y VENTAS 🌸            🌸CONDOMINIO🌸 (4 mensajes), ~ Ana Maria"),
        )
        assertEquals("Residentes Condominio, gaston", MessageStore.speakableName("Residentes Condominio, ~ gaston"))
        assertEquals("Laura", MessageStore.speakableName("Laura"))
        assertEquals("Mom ❤".let { MessageStore.speakableName(it) }, "Mom")
    }
}
