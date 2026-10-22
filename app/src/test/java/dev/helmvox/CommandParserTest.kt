package dev.helmvox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    private fun p(s: String) = CommandParser.parse(s)

    @Test fun navigation() {
        assertEquals(Command.Navigate("Plaza de España"), p("Navega a Plaza de España"))
        assertEquals(Command.Navigate("Málaga"), p("oye casco, llévame a Málaga por favor"))
        assertEquals(Command.Navigate("casa"), p("a casa"))
        assertEquals(Command.Navigate("gasolinera"), p("llévame a la gasolinera más cercana"))
        assertEquals(Command.Navigate("gasolinera"), p("necesito gasolina"))
        assertEquals(Command.Navigate("gasolinera"), p("llévame a la bencinera más cercana"))
        assertEquals(Command.Navigate("gasolinera"), p("necesito cargar nafta"))
        assertEquals(Command.Navigate("Calle Mayor 5"), p("quiero ir a Calle Mayor 5"))
    }

    @Test fun calls() {
        assertEquals(Command.Call("Mamá"), p("Llama a Mamá"))
        assertEquals(Command.Call("Juan Pérez"), p("llamar a Juan Pérez."))
        assertEquals(Command.Call("600 123 456"), p("marca al 600 123 456"))
    }

    @Test fun messages() {
        assertEquals(
            Command.Message("Laura", "llego en 10 minutos"),
            p("Manda un mensaje a Laura diciendo llego en 10 minutos"),
        )
        assertEquals(
            Command.Message("Pedro", "voy de camino"),
            p("envíale un whatsapp a Pedro que voy de camino"),
        )
    }

    @Test fun media() {
        assertEquals(Command.Media(MediaAction.PAUSE), p("pausa"))
        assertEquals(Command.Media(MediaAction.PAUSE), p("para la música"))
        assertEquals(Command.Media(MediaAction.PLAY), p("pon la música"))
        assertEquals(Command.Media(MediaAction.NEXT), p("siguiente canción"))
        assertEquals(Command.Media(MediaAction.NEXT), p("cambia de canción"))
        assertEquals(Command.Media(MediaAction.PREVIOUS), p("canción anterior"))
        assertEquals(Command.Volume(up = true), p("sube el volumen"))
        assertEquals(Command.Volume(up = false), p("más bajo"))
    }

    @Test fun info() {
        assertEquals(Command.Time, p("¿Qué hora es?"))
        assertEquals(Command.Battery(BatteryOf.BOTH), p("cuánta batería queda"))
        assertEquals(Command.Battery(BatteryOf.PHONE), p("batería del teléfono"))
        assertEquals(Command.Battery(BatteryOf.PHONE), p("¿cuánta batería tiene el móvil?"))
        assertEquals(Command.Battery(BatteryOf.HEADSET), p("batería del intercomunicador"))
        assertEquals(Command.Battery(BatteryOf.HEADSET), p("batería del casco"))
        assertEquals(Command.Battery(BatteryOf.BOTH), p("batería del casco y del móvil"))
        assertEquals(Command.Help, p("ayuda"))
        assertEquals(Command.Cancel, p("cancela"))
        assertNull(p("el perro de san roque no tiene rabo"))
    }

    @Test fun musicVariants() {
        assertEquals(Command.Media(MediaAction.PLAY), p("Pon la música, por favor"))
        assertEquals(Command.Media(MediaAction.PLAY), p("pon música"))
        assertEquals(Command.Media(MediaAction.PLAY), p("ponme algo de música"))
        assertEquals(Command.Media(MediaAction.PLAY), p("música"))
        assertEquals(Command.Media(MediaAction.PLAY), p("quiero escuchar música"))
        assertEquals(Command.Media(MediaAction.PLAY), p("oye casco pon la música"))
        assertEquals(Command.PlaySearch("Queen"), p("pon música de Queen"))
        assertEquals(Command.PlaySearch("Bad Bunny"), p("Pon Bad Bunny en Spotify"))
        assertEquals(Command.PlaySearch("Hotel California"), p("reproduce Hotel California"))
    }

    @Test fun intercomMode() {
        assertEquals(Command.IntercomMode(wantDmc = true), p("cambia a modo DMC"))
        assertEquals(Command.IntercomMode(wantDmc = false), p("cambia a modo bluetooth"))
        assertEquals(Command.IntercomMode(wantDmc = false), p("pasa al intercom Bluetooth"))
        assertEquals(Command.IntercomMode(wantDmc = true), p("pon modo d m c"))
        assertEquals(Command.IntercomMode(wantDmc = null), p("cambia de modo"))
        assertEquals(Command.IntercomMode(wantDmc = null), p("cambia el modo de intercom"))
        assertEquals(Command.Media(MediaAction.NEXT), p("pasa"))
    }

    @Test fun dmcGroup() {
        assertEquals(Command.DmcGroupMute, p("silencia el grupo"))
        assertEquals(Command.DmcGroupMute, p("quita el silencio del grupo"))
        assertEquals(Command.DmcGroupMute, p("mute en el grupo"))
        assertEquals(Command.DmcMic, p("silencia mi micro"))
        assertEquals(Command.DmcMic, p("activa el micrófono"))
        assertEquals(Command.DmcMic, p("abre el micro del grupo"))
    }

    @Test fun radio() {
        assertEquals(Command.Radio(RadioAction.ON), p("enciende la radio"))
        assertEquals(Command.Radio(RadioAction.ON), p("pon la radio"))
        assertEquals(Command.Radio(RadioAction.OFF), p("apaga la radio"))
        assertEquals(Command.Radio(RadioAction.NEXT), p("siguiente emisora"))
        assertEquals(Command.Radio(RadioAction.NEXT), p("busca emisora"))
        assertEquals(Command.Radio(RadioAction.PREVIOUS), p("emisora anterior"))
        assertEquals(Command.Radio(RadioAction.STATION, 3), p("pon la emisora 3"))
        assertEquals(Command.Radio(RadioAction.STATION, 2), p("emisora dos"))
        assertEquals(Command.Media(MediaAction.PAUSE), p("para la música"))
    }

    @Test fun stopMusic() {
        assertEquals(Command.Media(MediaAction.PAUSE), p("apaga la música"))
        assertEquals(Command.Media(MediaAction.PAUSE), p("corta la música"))
        assertEquals(Command.Media(MediaAction.PAUSE), p("silencia la música"))
        assertEquals(Command.Radio(RadioAction.OFF), p("apaga la radio"))
        assertEquals(Command.DmcGroupMute, p("silencia el grupo"))
    }

    @Test fun sharing() {
        assertEquals(Command.ShareMusic, p("comparte la música"))
        assertEquals(Command.ShareMusic, p("compartir música con el pasajero"))
        assertEquals(Command.ShareMusic, p("deja de compartir la música"))
        assertEquals(Command.ShareMusic, p("música compartida"))
        assertEquals(Command.ShareRadio, p("comparte la radio"))
    }

    @Test fun intercomAndPhone() {
        assertEquals(Command.IntercomCall(1), p("abre el intercom"))
        assertEquals(Command.IntercomCall(2), p("habla con el canal B"))
        assertEquals(Command.IntercomCall(3), p("abre el intercom canal tres"))
        assertEquals(Command.IntercomCall(1), p("llama a la pasajera"))
        assertEquals(Command.IntercomEnd, p("corta el intercom"))
        assertEquals(Command.Redial, p("vuelve a llamar"))
        assertEquals(Command.Redial, p("repite la última llamada"))
        assertEquals(Command.Redial, p("llama otra vez"))
        assertEquals(Command.Call("Juan"), p("llama a Juan"))
    }

    @Test fun locationAndMessages() {
        assertEquals(Command.ShareLocation("Laura"), p("manda mi ubicación a Laura"))
        assertEquals(Command.ShareLocation("Juan Pérez"), p("comparte mi ubicación con Juan Pérez"))
        assertEquals(Command.ShareLocation("Laura"), p("mándale a Laura mi ubicación"))
        assertEquals(Command.ShareLocation("mamá"), p("envía la ubicación a mamá por favor"))
        assertEquals(Command.WhereAmI, p("¿Dónde estoy?"))
        assertEquals(Command.WhereAmI, p("en qué comuna estoy"))
        assertEquals(Command.ReadMessages, p("lee mis mensajes"))
        assertEquals(Command.ReadMessages, p("¿tengo mensajes nuevos?"))
        assertEquals(Command.ReadMessages, p("léeme los whatsapps"))
        assertEquals(
            Command.Message("Laura", "llego en 10 minutos"),
            p("Manda un mensaje a Laura diciendo llego en 10 minutos"),
        )
    }

    @Test fun yesNo() {
        assertTrue(CommandParser.isYes("Sí"))
        assertTrue(CommandParser.isYes("vale, envíalo"))
        assertFalse(CommandParser.isYes("no"))
    }
}
