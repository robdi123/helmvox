package dev.helmvox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Frases dichas "a lo natural", sin saberse la orden exacta. */
class NaturalPhrasesTest {

    private fun p(s: String) = CommandParser.parse(s)

    @Test fun messages() {
        assertEquals(Command.ReadMessages, p("tengo notificaciones de WhatsApp"))
        assertEquals(Command.ReadMessages, p("¿me llegó algún WhatsApp?"))
        assertEquals(Command.ReadMessages, p("¿quién me escribió?"))
        assertEquals(Command.ReadMessages, p("léeme lo que me escribieron"))
        assertEquals(Command.ReadMessages, p("hay algo nuevo en el chat"))
        assertEquals(Command.Message("Laura", "voy saliendo"), p("escríbele a Laura que voy saliendo"))
        assertEquals(Command.Message("Pedro", "llego tarde"), p("dile a Pedro que llego tarde"))
        // Sin texto: la app preguntará qué decir (y a veces el reconocedor se come el verbo)
        assertEquals(Command.Message("Nano"), p("manda un mensaje a Nano"))
        assertEquals(Command.Message("Nano"), p("un mensaje de WhatsApp a Nano"))
        assertEquals(Command.Message("Nano"), p("Enviar WhatsApp a Nano"))
        assertEquals(Command.Message("Nano"), p("Mensaje a Nano"))
        assertEquals(Command.Message("Laura"), p("escríbele a la Laura"))
        assertEquals(Command.ReadMessages, p("tengo mensajes de WhatsApp"))
    }

    @Test fun music() {
        assertEquals(Command.Media(MediaAction.PLAY), p("ponme algo de música por favor"))
        assertEquals(Command.Media(MediaAction.PLAY), p("puedes poner la música"))
        assertEquals(Command.Media(MediaAction.PAUSE), p("quita esa música"))
        assertEquals(Command.Media(MediaAction.PAUSE), p("para un rato la música"))
        assertEquals(Command.Media(MediaAction.NEXT), p("salta esta"))
        assertEquals(Command.Media(MediaAction.NEXT), p("pon otra canción"))
        assertEquals(Command.Media(MediaAction.PREVIOUS), p("vuelve al tema de antes, el anterior"))
        assertEquals(Command.PlaySearch("Bruno Mars"), p("puedes poner una canción de Bruno Mars"))
        assertEquals(Command.PlaySearch("Shakira"), p("¿podrías poner algo de Shakira?"))
        assertEquals(Command.Media(MediaAction.PLAY), p("algo para escuchar por favor"))
        assertEquals(Command.Media(MediaAction.PLAY), p("pon algo para escuchar"))
        assertEquals(Command.Media(MediaAction.PLAY), p("¿me puedes poner la música?"))
    }

    @Test fun volume() {
        assertEquals(Command.Volume(up = false), p("está muy fuerte"))
        assertEquals(Command.Volume(up = false), p("bájale un poco"))
        assertEquals(Command.Volume(up = true), p("súbele al volumen"))
        assertEquals(Command.Volume(up = true), p("no se escucha nada"))
    }

    @Test fun batteryTimeLocation() {
        assertEquals(Command.Battery(BatteryOf.HEADSET), p("¿cuánta batería le queda al casco?"))
        assertEquals(Command.Battery(BatteryOf.PHONE), p("cómo va la pila del celular"))
        assertEquals(Command.Time, p("¿qué hora tenemos?"))
        assertEquals(Command.WhereAmI, p("¿dónde ando?"))
        assertEquals(Command.ShareLocation("Laura"), p("mándale mi ubicación a la Laura"))
    }

    @Test fun routeAndCalls() {
        assertEquals(Command.Navigate("casa"), p("llévame a mi casa"))
        assertEquals(Command.Navigate("Valparaíso"), p("¿cómo llego a Valparaíso?"))
        assertEquals(Command.Navigate("gasolinera"), p("tengo que cargar bencina"))
        assertEquals(Command.Call("Juan"), p("puedes llamar a Juan"))
        assertEquals(Command.Redial, p("llama al último que me llamó"))
    }

    @Test fun headset() {
        assertEquals(Command.IntercomMode(wantDmc = true), p("pásate al DMC"))
        assertEquals(Command.IntercomMode(wantDmc = false), p("ponte en bluetooth"))
        assertEquals(Command.DmcGroupMute, p("silencia a los del grupo"))
        assertEquals(Command.DmcMic, p("apágame el micro"))
        assertEquals(Command.ShareMusic, p("quiero compartir lo que escucho"))
        assertEquals(Command.ShareRadio, p("comparte la emisora"))
        assertEquals(Command.IntercomCall(1), p("conéctame con el pasajero"))
        assertEquals(Command.IntercomEnd, p("corta con el pasajero"))
        assertEquals(Command.Radio(RadioAction.ON), p("quiero la radio"))
        assertEquals(Command.Radio(RadioAction.NEXT), p("busca otra emisora"))
        assertEquals(Command.Radio(RadioAction.STATION, 4), p("la radio en la cuatro"))
    }

    @Test fun stillUnknown() {
        assertNull(p("el perro de san roque no tiene rabo"))
        assertNull(p("hola"))
    }
}
