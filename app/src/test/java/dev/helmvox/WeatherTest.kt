package dev.helmvox

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeatherTest {

    @Test fun spanishPhrases() {
        for (s in listOf(
            "¿cómo está el clima?", "¿qué tiempo hace?", "¿va a llover?", "¿hay lluvia en el camino?",
            "qué temperatura hace", "¿hace frío afuera?", "dame el pronóstico",
        )) assertEquals(s, Command.Weather, CommandParser.parse(s))
        // "tiempo" en otro sentido no es el clima
        assertNotEquals(Command.Weather, CommandParser.parse("cuánto tiempo falta"))
        assertEquals(Command.Time, CommandParser.parse("¿qué hora es?"))
    }

    @Test fun englishPhrases() {
        for (s in listOf("what's the weather like?", "is it going to rain?", "weather forecast", "how cold is it outside?")) {
            assertEquals(s, Command.Weather, CommandParser.parse(s, Lang.EN))
        }
        assertEquals(Command.Time, CommandParser.parse("what time is it?", Lang.EN))
    }

    @Test fun parsesOpenMeteoResponse() {
        val json = JSONObject(
            """{"current":{"temperature_2m":17.6,"weather_code":2,"wind_speed_10m":12.4},
               "hourly":{"precipitation_probability":[10,35,null,20]}}"""
        )
        val w = Weather.parse(json)
        assertEquals(WeatherNow(temperature = 18, code = 2, windKmh = 12, rainChance = 35), w)
        assertEquals(
            "Ahora hay 18 grados y está parcialmente nublado. Viento de 12 kilómetros por hora. " +
                "Probabilidad de lluvia en las próximas 4 horas: 35 por ciento.",
            Phrases(Lang.ES).weather(w),
        )
        assertEquals("raining", Weather.describe(63, Lang.EN))
    }
}
