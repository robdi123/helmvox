package dev.helmvox

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiIntentTest {

    /** Respuesta como la que devuelve la IA: todos los campos presentes, nulos si no aplican. */
    private fun ai(intent: String, vararg fields: Pair<String, Any?>): Command? {
        val json = JSONObject()
        for (key in listOf("destination", "who", "text", "query", "want_dmc", "channel", "station", "battery_of")) {
            json.put(key, JSONObject.NULL)
        }
        json.put("intent", intent)
        for ((k, v) in fields) json.put(k, v ?: JSONObject.NULL)
        return AiIntent.toCommand(json)
    }

    @Test fun mapsIntentsWithData() {
        assertEquals(Command.Navigate("Valparaíso"), ai("navigate", "destination" to "Valparaíso"))
        assertEquals(Command.Message("Laura", "llego tarde"), ai("send_message", "who" to "Laura", "text" to "llego tarde"))
        assertEquals(Command.ShareLocation("Juan"), ai("share_location", "who" to "Juan"))
        assertEquals(Command.PlaySearch("Queen"), ai("play_search", "query" to "Queen"))
        assertEquals(Command.IntercomMode(wantDmc = true), ai("intercom_mode", "want_dmc" to true))
        assertEquals(Command.IntercomMode(wantDmc = null), ai("intercom_mode"))
        assertEquals(Command.IntercomCall(2), ai("intercom_call", "channel" to 2))
        assertEquals(Command.IntercomCall(1), ai("intercom_call"))
        assertEquals(Command.Radio(RadioAction.STATION, 4), ai("radio_station", "station" to 4))
        assertEquals(Command.Battery(BatteryOf.HEADSET), ai("battery", "battery_of" to "headset"))
        assertEquals(Command.Battery(BatteryOf.BOTH), ai("battery"))
    }

    @Test fun mapsSimpleIntents() {
        assertEquals(Command.ReadMessages, ai("read_messages"))
        assertEquals(Command.Volume(up = false), ai("volume_down"))
        assertEquals(Command.Media(MediaAction.NEXT), ai("next_track"))
        assertEquals(Command.Time, ai("time"))
        assertEquals(Command.Cancel, ai("cancel"))
    }

    @Test fun rejectsIncompleteOrUnknown() {
        assertNull(ai("unknown"))
        assertNull(ai("navigate")) // sin destino
        assertNull(ai("send_message")) // sin destinatario
        assertEquals(Command.Message("Laura"), ai("send_message", "who" to "Laura")) // sin texto: la app pregunta
        assertNull(ai("radio_station", "station" to 9)) // fuera de rango
        assertNull(ai("inventado"))
    }

    @Test fun schemaRequiresEveryField() {
        val schema = AiIntent.schema()
        @Suppress("UNCHECKED_CAST")
        val props = schema["properties"] as Map<String, Any>
        assertEquals(props.keys.toList(), schema["required"])
        assertEquals(false, schema["additionalProperties"])
        assertTrue("unknown" in AiIntent.INTENTS)
    }
}
