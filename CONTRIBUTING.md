# Cómo colaborar con HelmVox

¡Gracias por querer ayudar! Hay tres formas de colaborar, de menos a más técnica:

1. **Probar y contar.** ¿Tienes un Cardo que no está en la tabla de compatibilidad? Prueba HelmVox
   y abre un *issue* con la plantilla **"Nuevo dispositivo"**, aunque sea para decir qué funciona.
2. **Frases e idiomas.** Si dices algo natural y HelmVox no lo entiende, abre un *issue* con la frase
   exacta. Si quieres, añádela tú en `CommandParser.kt`, `FuzzyIntent.kt` (español) o
   `EnglishParser.kt` (inglés), con su prueba en `app/src/test/`.
3. **Nuevos dispositivos.** Ayúdanos a documentar el protocolo de otros modelos (abajo).

> 🇬🇧 *Contributions welcome: device reports, phrases, and protocol captures for new models. Please
> read the rules below — we document byte-level facts only, never third-party code or firmware.*

---

## Reglas importantes (léelas antes de nada)

HelmVox analiza los protocolos **solo para interoperar** con los cascos que la gente ya tiene.
Para mantenerlo así:

- ✅ **Sí:** capturas de tráfico Bluetooth (solo los paquetes relevantes), descripciones de lo que
  hace cada paquete, tus propias pruebas y resultados.
- ❌ **Nunca subas:** código decompilado o desensamblado de apps oficiales, firmware, ficheros
  extraídos de apps o del casco, claves, ni métodos para saltarse protecciones o licencias.
- 🔒 **Privacidad:** las capturas pueden contener **datos personales** (direcciones Bluetooth,
  nombres de contactos, números de teléfono, mensajes). Comparte **solo los paquetes que importan**
  y tapa lo personal (por ejemplo `XX:XX:XX:XX:5E:8E`).
- ⚖️ Esto no es asesoría legal. Si tienes dudas sobre lo que permite la ley de tu país, consúltalo.

Los *pull requests* que no cumplan estas reglas se cerrarán.

---

## Documentar un dispositivo nuevo

La idea es simple: **grabar lo que la app oficial le dice al casco** mientras haces una acción, y
encontrar el paquete que se repite cada vez que la haces.

### 1. Grabar el tráfico Bluetooth (Android)

1. Activa las **Opciones de desarrollador** (toca 7 veces "Número de compilación").
2. Activa **"Registro de búsqueda HCI de Bluetooth"** (en algunos móviles: "Habilitado" o
   "Completo", no "Filtrado") y **apaga y enciende el Bluetooth**.
3. Abre la app oficial y haz **una sola acción, 3 veces** (por ejemplo: cambiar a DMC y volver).
   **Apunta la hora** de cada vez.
4. Saca el registro:
   - La mayoría de móviles: `adb bugreport bugreport.zip` y busca `btsnoop_hci.log` dentro.
   - Samsung: marca `*#9900#` → **"Copy to sdcard"** y busca en `/sdcard/log/`.
5. Desactiva el registro HCI cuando termines.

### 2. Analizar

- Abre `btsnoop_hci.log` con [Wireshark](https://www.wireshark.org/).
- Filtra por `btrfcomm` (cascos clásicos) o `btatt` (cascos BLE nuevos).
- Busca los paquetes **enviados por el móvil** a las horas que apuntaste. El que se repite en las
  3 veces es el candidato.
- Compara con el formato de [PROTOCOL.md](PROTOCOL.md): ¿empieza por `09`? ¿qué código de
  operación tiene?

### 3. Compartir

Abre un *issue* con la plantilla **"Nuevo dispositivo"** e incluye:

- Modelo exacto y versión de firmware (la ves en la app oficial).
- La acción que hiciste y los **bytes del paquete en hexadecimal** (p. ej. `09 03 20 00 CB 00`).
- Qué respondió el casco, si respondió.
- Si es BLE: el UUID del servicio y de la característica donde se escribe.

Con eso podemos añadirlo a `PROTOCOL.md` y a HelmVox.

---

## Compilar y probar

```bash
./gradlew testDebugUnitTest   # pruebas
./gradlew assembleDebug       # APK en app/build/outputs/apk/debug/
```

Estilo: Kotlin, sin dependencias de AndroidX; comentarios en español; cada frase nueva que deba
entender la app va acompañada de su prueba.
