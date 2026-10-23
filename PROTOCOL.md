# Protocolo de control de los Cardo clásicos

Documentación del canal de control que usan los intercomunicadores **Cardo de la familia clásica**
(PackTalk, PackTalk Bold y modelos de la misma época). Se obtuvo **observando la comunicación entre
el casco y su app oficial** con el único fin de que HelmVox pueda **interoperar** con el casco.

Este documento describe **hechos técnicos** (qué bytes se envían y qué significan). No contiene
código, firmware ni ficheros de Cardo. HelmVox no está afiliada con Cardo Systems.

> 🇬🇧 *Control channel of classic Cardo intercoms, documented for interoperability. Byte values and
> meanings only — no Cardo code or firmware.*

**Leyenda de la columna "Estado":** ✅ comprobado en un casco real · 🧪 implementado en HelmVox,
sin confirmar todavía · ❓ visto pero sin entender del todo.

---

## 1. Transporte

| Qué | Valor | Estado |
|---|---|---|
| Conexión | Bluetooth clásico, **RFCOMM canal 12** (sin consulta SDP) | ✅ PackTalk Bold |
| UUID alternativos (si el canal fijo falla) | `cd007f85-8b0b-11e6-ae22-56b6b6499611`, `0000fd01-0000-1000-8000-00805f9b34fb` | 🧪 |
| Concurrencia | Solo una app a la vez: si la app oficial está conectada, el canal queda ocupado | ✅ |

Los modelos **nuevos** (PackTalk Edge, Neo, Outdoor…) usan otro protocolo sobre **Bluetooth LE**
que todavía no está documentado aquí. ¡Contribuciones bienvenidas! (ver [CONTRIBUTING.md](CONTRIBUTING.md)).

## 2. Formato de paquete

```
[versión] [longitud de datos] [código de operación] [datos…]
```

- Sin checksum ni delimitadores. La longitud cuenta solo los datos.
- Versión `0x09` en todo, salvo el saludo inicial (`0x03`).
- Varios paquetes pueden llegar juntos en una misma lectura: hay que separarlos por la longitud.

### Códigos de operación

| Código | Sentido | Qué es | Respuesta |
|---|---|---|---|
| `0x00` | → casco | Saludo / versión (paquete `03 00 00`) | `09 00 00` (protocolo 9) ✅ |
| `0x01` | → casco | Leer ajustes. Datos: pares `[tipo][00]`; vacío = todos | Lista de ajustes con código `0x01` ✅ |
| `0x10` | → casco | Suscribirse a servicios. Datos: lista de ids de servicio | Avisos con `0x82` 🧪 |
| `0x20` | → casco | Evento (acción) — ver §3 | A veces ninguna |
| `0x28` | → casco | Alternar "compartir radio FM", sin datos | 🧪 |
| `0x81` | ← casco | Ajustes enviados por iniciativa del casco (p. ej. tras cambiar de modo) — ver §4 | |
| `0x82` | ← casco | Aviso de un servicio suscrito — ver §5 | |

## 3. Eventos (código `0x20`)

Datos del evento:

```
[perfil] [evento] [nº de parámetros] [parámetros…]
```

`perfil` es `0x00` en general y `0x60` en los eventos de teléfono.

Ejemplo completo — alternar DMC ⇄ Bluetooth: `09 03 20 00 CB 00`

| Evento | Perfil | Parámetros | Acción | Estado |
|---|---|---|---|---|
| `0xCB` | `00` | — | Alterna intercom **DMC ⇄ Bluetooth**. Solo funciona con el casco **en reposo** (sin llamada, voz, música ni intercom activos) | ✅ PackTalk Bold |
| `0xF2` | `00` | — | Silencia / reactiva el **grupo DMC** | 🧪 |
| `0xF1` | `00` | — | Abre / cierra **tu micrófono** hacia el grupo DMC | 🧪 |
| `0x02` | `00` | — | **Radio FM** encendida | 🧪 |
| `0x03` | `00` | — | Radio FM apagada | 🧪 |
| `0x07` | `00` | — | Buscar emisora siguiente | 🧪 |
| `0x06` | `00` | — | Buscar emisora anterior | 🧪 |
| `0xBB` | `00` | `n` (1–6) | Emisora guardada `n` | 🧪 |
| `0x2D` | `00` | — | Empieza / deja de **compartir la música** | 🧪 |
| `0x9E` | `00` | canal (A=1, B=2, C=3) | Abre el **intercom Bluetooth** en ese canal | 🧪 |
| `0x17` | `00` | canal | Corta el intercom Bluetooth en ese canal | 🧪 |
| `0x05` | `60` | — | **Rellamada** (última llamada del teléfono) | 🧪 |

Tras cambiar de modo, el casco envía por su cuenta la lista completa de ajustes (`0x81`).

## 4. Ajustes (respuesta `0x01` / aviso `0x81`)

Los datos son una lista de registros:

```
[tipo] [longitud] [valor…]   [tipo] [longitud] [valor…]   …
```

| Tipo | Qué es | Valor | Estado |
|---|---|---|---|
| `0x07` | Idioma de los avisos del casco | código numérico (p. ej. `0x14`) | ❓ |
| `0x13` | **Modo de intercom** | `01` = DMC, `00` = Bluetooth | ✅ |
| `0x16` | **Capacidades** del casco (8 bytes) | ver abajo | ✅ leído |
| `0x1A` | Umbrales del reconocimiento de voz del casco | `[sensibilidad][umbral «Ok Google»]`; 50 = alta … 250 = baja | ✅ leído |
| `0x1B` | **Comandos de voz avanzados** activados | `00` = no, otro = sí | ✅ leído |

**Capacidades (`0x16`)**, bits contados desde el menos significativo (bit 0):

| Byte | Bit | Significado |
|---|---|---|
| 2 | 7 | Radio FM |
| 2 | 6 | Compartir radio FM |
| 2 | 5 / 4 / 3 | Canales de intercom A / B / C |
| 2 | 2 | Compartir música |
| 3 | 6 | Menú de voz |
| 3 | 5 | **Comandos de voz avanzados** (incluye «Ok Google» → asistente del móvil) |
| 3 | 4 | DMC |

Ejemplo real de un PackTalk Bold: `16 08 c2 f8 f7 77 01 00 00 00` → FM, compartir FM, canales A y B,
compartir música, menú de voz, voz avanzada y DMC.

## 5. Servicios (avisos `0x82`)

Suscripción: `09 01 10 <id>`. El casco responde con el estado actual y avisa en cada cambio:

```
09 [len] 82 [id de servicio] [?] [estado…]
```

| Servicio | Id | Estado | Estado |
|---|---|---|---|
| **Batería** | `0x01` | byte 5: `0`→~0 %, `1`→~20 %, `3`→~50 %, `5`→~75 %, `7`→~80 %, `9`→~100 % | ✅ PackTalk Bold |

## 6. Comportamiento observado

- El casco **ignora órdenes de cambio de modo** mientras hay un canal de voz abierto (por ejemplo,
  el del asistente). Hay que cerrar el canal de voz (SCO), esperar ~2 s y entonces enviar. ✅
- Leer ajustes o batería funciona aunque el canal de voz esté abierto. ✅ (ajustes)
- Los cascos con voz avanzada reconocen «Ok Google» por sí mismos y piden al móvil que abra su
  asistente de voz (comando estándar de manos libres), igual que el botón del casco. ✅

---

¿Tienes otro modelo o descubriste algo nuevo? Lee [CONTRIBUTING.md](CONTRIBUTING.md) y abre un
*issue* con la plantilla **"Nuevo dispositivo"**.
