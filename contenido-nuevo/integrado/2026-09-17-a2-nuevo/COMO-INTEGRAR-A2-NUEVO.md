# A2 ampliado (Cowork, 17-09-2026)

Paso 5 del plan de la auditoría. Es el mismo método que A1 ampliado (que ya integraste).

**Qué trae:**
- **16 lecciones nuevas en 6 unidades** (195 ejercicios), intercaladas:

| Unidad | Va después de | Lecciones |
|---|---|---|
| a2u11 ⏳ Desde cuándo y lo que pudiste | a2u2 | for / since / How long…? · could / was able to |
| a2u12 🙋 Pedir, invitar y llamar | a2u3 | Can/Could/May I…? · How about / Why don't we + aceptar y rechazar · al teléfono |
| a2u13 🔢 Poco, mío y yo mismo | a2u6 | few / a few / little / a little · mine / whose · myself / each other |
| a2u14 🧍 Cómo es y cómo se siente | a2u7 | bored / boring · look like / be like · adjetivo y verbo + preposición |
| a2u15 🩺 La salud y el verbo get | a2u8 | en el médico y la farmacia · los usos de get |
| a2u16 🔗 Preguntar y conectar ideas | a2u10 | preguntas de sujeto / objeto · although, however, first… finally · condicional cero |

- **71 ejercicios añadidos a 22 lecciones existentes** (`adiciones-a2.json`). Son los "huecos" de los `_pendientes` del parche de la auditoría: *be used to*, respuestas cortas, *going to* con evidencia, *enough to*, pasiva en negativo y pregunta, *can → could*, *So does he*, *stop to / stop -ing*, *can't* como prohibición y otros.
  - En tres ejercicios de a2u7l3 la respuesta principal ahora es *that* (lo americano) y *which* va aceptado.
- **Siete fichas existentes** ganan una línea para eso mismo (`parche-fichas-a2.json`: a2u1l2, a2u2l1, a2u4l1, a2u6l1, a2u8l1, a2u9l1, a2u9l2).
- **Total A2 después de integrar: 45 lecciones, 531 ejercicios** (antes 29 y 265).
- **Curso completo: A1 43 lecciones / 568 ejercicios + A2 45 / 531 = 88 lecciones, 1.099 ejercicios.**

**Verificado:**
- Cuatro redactores escribieron el contenido y cuatro revisores adversariales distintos lo revisaron. Encontraron 91 defectos (42 ALTO, casi todos respuestas correctas que no se aceptaban) y todos están corregidos.
- El validador (reglas de `checkContent` y `Correccion.kt`) da OK.
- Lo probé en tu PC sobre una copia del `curriculum.json` actual, que ya trae A1 ampliado.

## Orden para integrar (los mismos scripts de A1)

1. Las fichas:
   `python contenido-nuevo/integrado/2026-09-17-parche-auditoria/parchar2.py app/src/main/assets/content/curriculum.json contenido-nuevo/a2-nuevo/parche-fichas-a2.json`
   Después copia el `.parchado.json` encima.
2. Las unidades:
   `python contenido-nuevo/integrado/2026-09-17-a1-nuevo/anexar_unidades.py app/src/main/assets/content/curriculum.json contenido-nuevo/a2-nuevo/nuevo-a2u11-u12.json contenido-nuevo/a2-nuevo/nuevo-a2u13-u14.json contenido-nuevo/a2-nuevo/nuevo-a2u15-u16.json`
3. Los ejercicios añadidos:
   `python tools/content/anexar.py contenido-nuevo/a2-nuevo/adiciones-a2.json`
4. `checkContent`, los tests y a `integrado/2026-09-17-a2-nuevo/`.

## Notas

- **a2u16l2e8 tiene 540 `accept`.** Son las combinaciones de although / even though / though × lugar × final × he/she/you (el español «vive» no dice quién) × orden de las cláusulas. Son correctas y no se repiten. Si pesa demasiado en la corrección o en el informe, avísame y cambio el español para que fije el sujeto.
- **La cadena de desbloqueo:** con el arreglo que hiciste para A1, las unidades intercaladas en A2 no deberían bloquear nada. Confírmalo con el progreso real de Fero.
- **Fichas un poco largas:** a2u4l1 queda en ~122 palabras.
