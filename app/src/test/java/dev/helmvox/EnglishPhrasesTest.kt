package dev.helmvox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frases en inglés, dichas de forma natural. */
class EnglishPhrasesTest {

    private fun p(s: String) = CommandParser.parse(s, Lang.EN)

    @Test fun messages() {
        assertEquals(Command.ReadMessages, p("Read my messages"))
        assertEquals(Command.ReadMessages, p("do I have any new WhatsApp?"))
        assertEquals(Command.ReadMessages, p("check my notifications please"))
        assertEquals(Command.ReadMessages, p("who texted me?"))
        assertEquals(Command.Message("Laura", "I'm on my way"), p("Text Laura saying I'm on my way"))
        assertEquals(Command.Message("Mike", "running late"), p("send a message to Mike that running late"))
        assertEquals(Command.Message("Pedro", "call me later"), p("tell Pedro to say call me later"))
        assertEquals(Command.Message("Nano"), p("send a WhatsApp to Nano"))
        assertEquals(Command.Message("Nano"), p("text Nano"))
        assertEquals(Command.Message("Nano"), p("message to Nano"))
    }

    @Test fun music() {
        assertEquals(Command.Media(MediaAction.PLAY), p("play some music"))
        assertEquals(Command.Media(MediaAction.PLAY), p("resume"))
        assertEquals(Command.Media(MediaAction.PAUSE), p("pause"))
        assertEquals(Command.Media(MediaAction.PAUSE), p("stop the music"))
        assertEquals(Command.Media(MediaAction.NEXT), p("skip this song"))
        assertEquals(Command.Media(MediaAction.NEXT), p("next track"))
        assertEquals(Command.Media(MediaAction.PREVIOUS), p("go back to the previous song"))
        assertEquals(Command.PlaySearch("Queen"), p("play Queen"))
        assertEquals(Command.PlaySearch("Pink Floyd"), p("play music by Pink Floyd on Spotify"))
    }

    @Test fun volume() {
        assertEquals(Command.Volume(up = false), p("it's too loud"))
        assertEquals(Command.Volume(up = false), p("turn it down a bit"))
        assertEquals(Command.Volume(up = true), p("louder"))
        assertEquals(Command.Volume(up = true), p("I can't hear anything"))
    }

    @Test fun routeAndCalls() {
        assertEquals(Command.Navigate("home"), p("take me home"))
        assertEquals(Command.Navigate("Boston"), p("navigate to Boston"))
        assertEquals(Command.Navigate("Golden Gate Bridge"), p("how do I get to the Golden Gate Bridge"))
        assertEquals(Command.Navigate("gas station"), p("find me a gas station"))
        assertEquals(Command.Navigate("gas station"), p("I need fuel"))
        assertEquals(Command.Call("Mom"), p("call Mom"))
        assertEquals(Command.Redial, p("call him back"))
        assertEquals(Command.Redial, p("redial"))
    }

    @Test fun locationBatteryTime() {
        assertEquals(Command.WhereAmI, p("where am I?"))
        assertEquals(Command.ShareLocation("Laura"), p("send my location to Laura"))
        assertEquals(Command.ShareLocation("Juan"), p("text Juan my location"))
        assertEquals(Command.Battery(BatteryOf.HEADSET), p("how much battery does the helmet have?"))
        assertEquals(Command.Battery(BatteryOf.PHONE), p("phone battery"))
        assertEquals(Command.Battery(BatteryOf.BOTH), p("battery"))
        assertEquals(Command.Time, p("what time is it?"))
    }

    @Test fun headset() {
        assertEquals(Command.IntercomMode(wantDmc = true), p("switch to DMC"))
        assertEquals(Command.IntercomMode(wantDmc = false), p("switch to Bluetooth mode"))
        assertEquals(Command.IntercomMode(wantDmc = null), p("change mode"))
        assertEquals(Command.DmcGroupMute, p("mute the group"))
        assertEquals(Command.DmcMic, p("mute my mic"))
        assertEquals(Command.ShareMusic, p("share the music"))
        assertEquals(Command.ShareRadio, p("share the radio"))
        assertEquals(Command.IntercomCall(1), p("open the intercom"))
        assertEquals(Command.IntercomCall(2), p("intercom channel B"))
        assertEquals(Command.IntercomCall(1), p("talk to my passenger"))
        assertEquals(Command.IntercomEnd, p("close the intercom"))
        assertEquals(Command.Radio(RadioAction.ON), p("turn on the radio"))
        assertEquals(Command.Radio(RadioAction.OFF), p("turn the radio off"))
        assertEquals(Command.Radio(RadioAction.NEXT), p("next station"))
        assertEquals(Command.Radio(RadioAction.PREVIOUS), p("previous station"))
        assertEquals(Command.Radio(RadioAction.STATION, 3), p("station three"))
    }

    @Test fun controlAndYes() {
        assertEquals(Command.Help, p("help"))
        assertEquals(Command.Cancel, p("never mind"))
        assertNull(p("the quick brown fox"))
        assertTrue(CommandParser.isYes("Yes", Lang.EN))
        assertTrue(CommandParser.isYes("yeah send it", Lang.EN))
        assertFalse(CommandParser.isYes("no", Lang.EN))
        // El español sigue igual
        assertEquals(Command.ReadMessages, CommandParser.parse("lee mis mensajes"))
    }
}
