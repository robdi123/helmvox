package dev.helmvox

/**
 * Decide cuándo avisar de batería baja: una sola vez al bajar de cada umbral (20 % y 10 %),
 * y se rearma al cargar o al volver a subir por encima del umbral con margen.
 */
class BatteryWatch(private val thresholds: List<Int> = listOf(20, 10), private val rearmMargin: Int = 5) {

    private val announced = mutableSetOf<Int>()

    /** Devuelve el umbral recién cruzado (para avisar) o null si no hay que decir nada. */
    fun update(level: Int, charging: Boolean): Int? {
        if (charging) {
            announced.clear()
            return null
        }
        announced.removeAll { level >= it + rearmMargin }
        val crossed = thresholds.filter { level <= it && it !in announced }
        if (crossed.isEmpty()) return null
        announced += crossed
        return crossed.min()
    }
}
