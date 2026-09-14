# A1 reequilibrado — 89 ejercicios de producción

`adiciones_a1.json` es un diccionario **`id de lección` → lista de ejercicios
nuevos**. Se **anexan** al final de `exercises` de esa lección, numerando los
ids a continuación de los que ya tiene (`a1u4l1` tiene e1..e8 → los nuevos son
e9, e10, e11). Nada se borra ni se reescribe.

`a1u1l1` no está en el archivo: es tu lección de muestra y ya los tiene.

`validar2.py` replica `checkContent` **más** `Correccion.suelta` para los
cuatro tipos nuevos. Córrelo sobre el `curriculum.json` final.

## Qué queda

| | antes | ahora |
|---|---|---|
| ejercicios | 207 | **300** |
| producción real (type, speak, write, cloze, shadow) | 62 (30 %) | **161 (53 %)** |

Es el reparto que propuso el documento de tipos, sin borrar nada de lo que ya
estaba verificado: 27 `cloze`, 27 `write`, 27 `shadow` y 12 `minimalPair`.

## Verificado antes de mandarlo

Un agente revisó los 89 uno por uno y encontró **31 defectos reales**, todos
corregidos. Los que importan como precedente:

- **`accept` incompleto en 10 ejercicios de `write`.** Como `'s` y `'d` no se
  expanden a propósito, cada variante necesita su propia entrada: si
  `"There's a shop near here."` no está listada, se marca mal aunque sea
  correcta. Ojo con esto al escribir contenido nuevo.
- **Un `cloze` que contradecía dos lecciones.** Pedía completar
  *I ___ a coffee* → "want", cuando `a1u2l1` y `a1u7l2` enseñan que
  *I want a coffee* suena a orden. Cambiado a *I ___ a new phone*.
- **Dos pares mínimos malos.** `house`/`horse` no es confundible para un
  hispanohablante (cambian la vocal entera y la r) → `chair`/`share`.
  `card`/`cart` depende de que la voz no ensordezca la d final → `can`/`can't`,
  que además es el contraste más útil de esa lección.
- **Acento mal puesto en tips de `shadow`:** decía «Can I HAVE a coffee» cuando
  el natural es «Can I have a COFFEE», y «excuse ME» cuando es «exCUSE me».
  Los dos casos convertían una frase educada en una insistente.
- **Tips que pedían contraer lo que el texto no contrae.** El texto dice
  *I am a student here* y el tip decía «suena I'm». Con puntaje por cobertura
  de palabras eso le costaba una palabra al alumno. Ya no.

## Dos cosas para ti, no para el contenido

1. **`a1u1l3e9` no puede evaluar lo que enseña.** La lección es sobre
   mayúsculas (English, Spanish, Colombian) pero la corrección normaliza a
   minúsculas, así que `i'm colombian and i speak spanish` pasa como correcta.
   Cambié el tip para no prometer lo que no se mide. Si algún día quieres
   evaluarlo de verdad, haría falta una marca por ejercicio del tipo
   `"caseSensitive": true`.
2. **`can`/`can't` y los pares de consonante final** dependen de cómo los
   sintetice Piper. Vale la pena oírlos una vez en el teléfono antes de darlos
   por buenos; si la voz se come la t final, hay que reproducir la `sentence`
   en vez de la palabra suelta.

## Siguiente

Con esto A1 queda cerrado. Arranco A2 en el mismo formato: `levels[]` con
`{"id":"A2", "title":…, "goal":…, "units":[…]}`, ~30 lecciones, y ya con los
nueve tipos desde el primer ejercicio.
