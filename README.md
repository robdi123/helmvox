# HelmVox

**Asistente de voz para motoristas con intercomunicador Cardo.** Habla con tu móvil a través del
casco sin soltar el manillar: mensajes, música, rutas, llamadas y ubicación, y además controla el
intercom, la radio y la batería de tu Cardo.

> 🇬🇧 *HelmVox is a free, open-source Android voice assistant for motorcyclists using a **Cardo**
> helmet intercom (tested on Cardo PackTalk and PackTalk Bold). Spanish-first. Optional AI
> understanding with your own Anthropic API key.*

---

## Compatibilidad

| Intercomunicador | Estado |
|---|---|
| **Cardo PackTalk** | ✅ Probado |
| **Cardo PackTalk Bold** | ✅ Probado |
| Otros Cardo con el protocolo clásico (Freecom 2/4 Plus, SmartPack, PackTalk Slim…) | ⚠️ Probablemente funcione, sin probar |
| Cardo nuevos (PackTalk Edge, Neo, Outdoor…) | ❌ Usan otro protocolo: las funciones del casco no funcionan |
| Otras marcas (Sena, Interphone…) | ❌ Sin soporte. Las funciones del móvil podrían ir, pero no está probado |

HelmVox está pensada **solo para Cardo**. Si la pruebas en otro modelo, cuéntanos cómo te fue
abriendo un *issue*.

## Qué hace

**Desde el móvil, a través del casco:**

- **Mensajes:** te lee los WhatsApp, Telegram y SMS al llegar y te deja responder con la voz.
- **Música:** pon / pausa / siguiente / anterior / volumen, o «pon música de Queen».
- **Rutas:** «llévame a casa», «¿cómo llego a Valparaíso?», «necesito bencina».
- **Llamadas:** «llama a Laura», «vuelve a llamar».
- **Ubicación:** «¿dónde estoy?», «mándale mi ubicación a Juan».
- **Batería del móvil** y avisos automáticos de batería baja.

**Control del casco Cardo:**

- Cambiar entre intercom **DMC** y **Bluetooth** (sabiendo en cuál estás).
- Silenciar el grupo DMC, tu micrófono, abrir y cortar el intercom.
- **Radio FM:** encender, apagar, buscar, emisoras guardadas.
- Compartir música o radio.
- **Batería del casco**, avisos de batería baja y diagnóstico.

**Entiende frases naturales.** No hace falta saberse las órdenes: «tengo notificaciones de
WhatsApp», «está muy fuerte», «pásate al DMC»… Y si activas la IA opcional, entiende casi cualquier
forma de decirlo.

## Cómo se usa

1. Instala el APK (ver *Releases*) o compílalo (ver abajo).
2. Abre HelmVox y concede los permisos de la tarjeta **Permisos** (todo en verde).
3. En tu Cardo, activa el **asistente de voz del teléfono**: con el botón del casco o diciendo
   «Ok Google» (requiere los comandos de voz avanzados activados en Cardo Connect).
   HelmVox se abrirá en su lugar.
4. Para las funciones del casco, **cierra Cardo Connect**: ambas apps no pueden usar el canal de
   control del casco a la vez.
5. Oirás un pitido: di lo que quieras.

## IA opcional (con tu propia clave)

Si la app no entiende una frase **y hay internet**, puede preguntársela a **Claude** (Anthropic)
para saber qué orden querías dar. Para usarlo:

1. Consigue una clave de API en [console.anthropic.com](https://console.anthropic.com/).
2. Pégala en **HelmVox → Inteligencia artificial** y activa «Usar IA cuando no entienda».
3. Elige el modelo: *Haiku* (rápido y barato), *Sonnet* (equilibrado) u *Opus* (el más listo).

Pagas tú directamente a Anthropic según tu uso. Como solo se consulta cuando la app no entiende
por sí sola, suelen ser céntimos al mes. Sin clave o sin cobertura, todo sigue funcionando en local.

## Privacidad

- **Todo se procesa en tu móvil.** HelmVox no tiene servidores ni recoge datos.
- Tu clave de API se guarda **solo en el almacenamiento privado de la app**.
- Con la IA activada, **solo la frase que no se entendió** se envía a Anthropic para interpretarla.
- El reconocimiento de voz usa el servicio de voz de tu móvil (normalmente Google).

## Compilar

Requisitos: Android Studio (o JDK 17 + Android SDK 36).

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. Pruebas: `./gradlew testDebugUnitTest`.

## Aviso legal

HelmVox es un proyecto independiente **sin relación alguna con Cardo Systems**. Cardo, PackTalk y
Freecom son marcas de sus respectivos propietarios. Las funciones específicas de Cardo usan el
protocolo de control del casco con fines de **interoperabilidad**; una actualización del firmware
podría cambiarlo. Úsalo bajo tu responsabilidad y, sobre todo, **conduce con atención**: la app
está pensada para no tener que mirar el móvil.

## Apoya el proyecto

HelmVox es gratis y de código abierto. Si te sirve en la ruta, puedes
[regalarme un café ☕](https://buymeacoffee.com/SpawnRipple).

## Licencia

[MIT](LICENSE) — úsala, modifícala y compártela libremente manteniendo el aviso de copyright.
