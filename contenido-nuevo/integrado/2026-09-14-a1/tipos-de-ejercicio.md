# Nuevos tipos de ejercicio, y por qué esos

Investigado antes de proponer, como manda la regla. Lo que sigue no es opinión
de diseño: cada tipo viene con la cifra que lo justifica y con lo que la
evidencia **no** respalda.

## El hallazgo que obliga a cambiar el reparto

*The Effects of Receptive and Productive Word Retrieval Practice on Second
Language Vocabulary Learning* (KATE Journal 30) comparó dos formas de
practicar, con el **mismo tiempo** de estudio:

- **Receptiva** (reconocer: elegir entre opciones) → sirve solo para reconocer.
- **Productiva** (producir la forma: escribirla) → **d = 1,38** sobre la
  receptiva en pruebas de producción, y **d = 0,61** una semana después.

Y el detalle que cierra la discusión: en las pruebas *receptivas*, la práctica
productiva **empató** con la receptiva (d = 0,63 sobre el control). O sea:
producir te da lo mismo que reconocer en reconocimiento, **y además** te da
producción. Reconocer no cuesta menos tiempo y rinde menos.

**Dónde está Hablo hoy:** de los 207 ejercicios de A1, `listen` y `translate`
son de elegir entre opciones. `build` da las palabras hechas (ordenar, no
recordar). Solo `type` y `speak` son producción de verdad. Más o menos la mitad
del curso está en el formato que la evidencia deja en segundo lugar.

Esto no es un error mío ni de nadie: es exactamente lo que hacen las apps
grandes. El estudio comparativo de Babbel, Memrise y Duolingo
(*Journal of Curriculum and Teaching*) le señala a Duolingo justo eso —
"excessive reliance on translation" y falta de profundidad — junto con
"lessons lack logical order, featuring nonsensical sentences". La ventaja de
Hablo es que puede no copiar esa parte.

## Los cuatro tipos que propongo

### 1. `write` — Escríbelo en inglés  ⭐ el de mayor valor por trabajo

Ve el español, escribe el inglés. Sin opciones, sin fichas de palabras.
Es recuperación productiva pura: el d = 1,38 de arriba.

```json
{"id":"a1u4l1e5", "type":"write", "es":"Ella trabaja en un hospital.",
 "answer":"She works in a hospital.",
 "accept":["She works at a hospital."],
 "tip":"..."}
```

`accept` es obligatorio pensarlo: en producción libre casi siempre hay más de
una respuesta correcta, y marcar mal una buena es el peor fallo posible.
Normalizar mayúsculas, puntuación, espacios y contracciones (*I'm* = *I am*)
con el `normalizeAnswer()` que ya existe.

**Y la corrección importa tanto como el ejercicio:** recuperación *con*
corrección rinde g = 0,73; sin ella, g = 0,39 (Rowland 2014, *Psychological
Bulletin*, 159 comparaciones — ya está en CLAUDE.md). Así que si la respuesta
está a una palabra o a dos letras de la correcta, no digas "mal": di qué
falló — *"casi: te faltó la -s de works"*. Eso es la mitad del efecto.

### 2. `cloze` — Completa el hueco

Frase en inglés con un hueco, y se **escribe** lo que falta (no se elige).
Sirve para apuntar exactamente al punto de gramática de la lección.

```json
{"id":"...", "type":"cloze", "text":"She ___ in a hospital.",
 "answer":"works", "es":"Ella trabaja en un hospital.",
 "accept":[], "tip":"..."}
```

Es el mecanismo central de Babbel, y es al que ese mismo estudio le reconoce
"well-structured progression, explicit grammar explanations, varied exercise
types and detailed feedback". Barato de construir: es `write` con menos texto.

### 3. `shadow` — Repite con la profesora

La profesora dice la frase, y él la repite **enseguida**, intentando seguir el
ritmo. La revisión sistemática de shadowing para pronunciación L2 (Oxford,
2025) es clara en las dos direcciones:

- **Sí funciona** para fluidez (8 de 8 estudios), prosodia (11 estudios) y
  comprensibilidad/inteligibilidad (10 de 11).
- **No está probado** para sonidos sueltos: *"research into the impact of
  shadowing on segmental pronunciation control was inconclusive"*.

Por eso **no hay que puntuarlo por fonema**. El `th` se arregla en los drills
con GOP; el shadowing es para el ritmo, que es justo lo que falta al conversar.

```json
{"id":"...", "type":"shadow", "text":"I would like a coffee, please.",
 "tip":"..."}
```

Medida honesta y barata: que salgan todas las palabras (el puntaje de palabra
que ya existe) + **cuánto tardó él contra cuánto tardó la profesora**. Se
muestra como dato, no como nota: *"tú 3,1 s, la profesora 2,2 s — vas bien,
pégalo más"*. Nada de inventar un porcentaje que no está validado.

### 4. `minimalPair` — la pantalla "Oído"

Ya estaba en el plan de CLAUDE.md con sus cifras (Uchihara, Karas & Thomson
2025, *SSLA* 47(3), 79 estudios: percepción g = 0,92). Dos detalles de ese
mismo paper que hay que respetar al construirla:

- **Identificar la palabra (g = 0,95), no preguntar "¿son iguales?" (g = 0,57).**
  Casi el doble de efecto por el mismo trabajo.
- **La variabilidad de hablantes es un moderador** — y la app tiene cuatro
  profesoras. Es una ventaja real sobre las apps de una sola voz.

```json
{"id":"...", "type":"minimalPair", "options":["ship","sheep"],
 "answer":"ship", "sentence":"My ship is very big.", "tip":"..."}
```

**Trampa ya documentada:** `Speaker` mantiene **una sola voz inglesa** cargada
(`ensureVoiceLoaded` libera y recarga). Rotar profesora por ítem serían ~12
recargas por bloque. Rotar **por bloque**, o pre-sintetizar el bloque al entrar.

Y hay que decir en pantalla lo que el paper dice: la transferencia del oído a
la boca existe pero es moderada (d = 0,54) y la correlación entre las dos no
fue significativa (r = 0,31). Por eso cada bloque de Oído debe **cerrar
hablando**, no solo escuchando.

## Reparto nuevo por lección (8 ejercicios)

| | ahora | propuesto |
|---|---|---|
| `listen` (elegir) | 2 | 1 |
| `translate` (elegir) | 2 | 1 |
| `build` (ordenar) | 1 | 1 |
| `cloze` (escribir) | — | 1 |
| `write` (escribir) | — | 1 |
| `type` (dictado) | 1 | 1 |
| `speak` / `shadow` | 2 | 2 |

De 2 de 8 en producción real a 5 de 8, sin alargar la lección. Reconocer sigue
estando: sirve para **presentar** algo nuevo. Lo que cambia es que ya no se
queda ahí.

## Y el mazo de repaso (punto 4 del plan)

La escalera que ya estaba pensada — *elegir → armar → escribir → oír y escribir
→ decir* — resulta estar exactamente respaldada por esto. Vale la pena dejarla
escrita como regla: **un ítem no se considera aprendido hasta que se produjo,
no solo reconoció.** Que suba de peldaño al acertar y baje al fallar.

## Lo que NO propongo, y por qué

- **Puntuar el shadowing por fonema.** La revisión dice que en segmentales es
  inconcluso. Prometer más de lo que mide es lo que rompe la confianza.
- **Traducir inglés→español como tipo nuevo.** El dato de Terai, Yamashita &
  Pasich (2021) que ya está en CLAUDE.md dice que esa dirección rinde más en
  principiantes, pero es **receptiva**, y el hallazgo de arriba pesa más para
  alguien que quiere hablar. Se queda como está.
- **Rachas, ligas, vidas, cofres.** No hay evidencia de aprendizaje; es
  retención de usuario. La app es para una persona que ya quiere usarla.
