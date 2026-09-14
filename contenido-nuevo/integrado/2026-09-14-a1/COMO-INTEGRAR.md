# A1 completo — cómo integrarlo

Tres archivos:

- **`a1-unidades-nuevas.json`** — 6 unidades, 19 lecciones, 152 ejercicios.
  Se anexan a `levels[0].units` de `curriculum.json`, después de `a1u3`.
- **`teoria-lecciones-existentes.json`** — las 8 fichas `theory` de las lecciones
  que ya existen, por id. Se insertan como campo `theory` en cada lección; no
  tocan sus ejercicios.
- **`validar.py`** — el validador con el que se revisó todo. Úsalo como
  referencia de lo que `checkContent` tiene que comprobar.

## Corrección a lo que te dije antes

En el mensaje anterior escribí `extraWords` para las palabras señuelo de
`build`. **La clave real en el JSON es `extra`** (`Content.kt`:
`extraWords = strings(o, "extra")`). El contenido usa `extra`, que es lo
correcto; la regla de validación es la misma.

## Formato en el que está escrito

```json
{
  "id": "a1u4l1",
  "title": "Lo que haces todos los días",
  "theory": {"title": "...", "body": "...", "trap": "..."},
  "exercises": [
    {"id": "a1u4l1e1", "type": "listen", "audio": "She works in a hospital.",
     "options": ["...", "She works in a hospital.", "..."],
     "answer": "She works in a hospital.", "tip": "..."}
  ]
}
```

`answer` es **texto**, no índice. En `listen`, `audio` y `answer` son iguales
(el validador lo exige: si difieren, el audio no es ninguna de las opciones).

`theory.body` lleva `**negrita**` y alguna tabla en Markdown ligero. Si la
pantalla de teoría todavía no existe, el campo queda guardado y no estorba.

## Lo que ya está verificado (no hace falta repetirlo)

- `answer` siempre está entre `options`; sin opciones repetidas; mínimo 3.
- **Ningún distractor es también inglés correcto.** Se revisó uno por uno;
  aparecieron 12 defectos reales y están corregidos.
- En `build`, ni los señuelos ni las palabras de la respuesta arman otra frase
  válida que también traduzca el español.
- Toda palabra de `speak` existe en `assets/gop/cmudict.dict`.
- Ids únicos entre las 6 unidades.
- Español natural de Colombia, revisado.

## Una cosa que decidir tú

De los 38 ejercicios de hablar: `h` 11 · `es` 9 · `th` 6 · `sh` 6 ·
`general` 5 · `ed` 2. Con `MIN_PRECISION = 0.50`, `th` hoy **no muestra
veredicto** — esos 6 quedan con puntaje de palabra y el tip, nada más. Está
bien así (el tag dice qué entrena, no qué se muestra), pero si quieres subir
el peso de lo que sí da señal fiable, `ed` es el de mejor precisión (1,00) y
solo tiene 2. Dime y muevo algunos.
