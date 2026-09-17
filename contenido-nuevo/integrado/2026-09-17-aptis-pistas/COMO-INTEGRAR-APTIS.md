# Pistas de Aptis: Reading, Listening y Speaking (Cowork, 17-09-2026)

Con esto las cinco pistas tienen banco propio: Core, Writing, Reading, Listening y Speaking.

| Archivo | Tareas | Partes del examen |
|---|---|---|
| `aptis-reading.json` | 42 (A1 8 · A2 11 · B1 13 · B2 10) | P1 completar (22) · P2 ordenar (12) · P4 títulos (8) |
| `aptis-listening.json` | 42 (A1 6 · A2 11 · B1 13 · B2 12) | P1 datos (14) · P2 qué dice cada uno (10) · P3 él/ella/los dos (10) · P4 opinión y actitud (8) |
| `aptis-speaking.json` | 28 (A1 3 · A2 6 · B1 10 · B2 9) | P1 preguntas personales (8) · P2 describir foto (8) · P3 comparar dos fotos (6) · P4 tema abstracto, 1 min de preparación y 2 de habla (6) |

**Formato:** es el de `aptis-writing.json` (`partes` → `tareas`), con los tipos que el parser ya conoce: `completar`, `ordenar`, `titulos`, `dato`, `quien` y las tareas habladas. Todas traen `why`.

**Verificación:** un revisor adversarial corrigió lo siguiente.
- Las rúbricas de Speaking preguntaban cosas que no se pueden juzgar desde una transcripción (pausas, fluidez). Ahora piden un número aproximado de palabras según el nivel y el tiempo, más chequeos concretos.
- Una tarea de «los dos» tenía dos respuestas defendibles.
- Una tarea marcada B2 era en realidad B1.
- `validar_aptis.py` da OK.

**Lo que no se cubre:** Reading parte 3 (cuatro opiniones que se emparejan con frases) no tiene tipo en la app. Si lo construyes (`tipo: "opiniones"`), escribo el banco.

## Integrar

1. Copiar los tres archivos a `app/src/main/assets/content/` con esos nombres.
2. `checkContent` y los tests.
3. Comprobar que cada nivel tiene al menos 5 tareas por pista, que es lo que necesita la promoción «4 de 5». Speaking A1 tiene 3: con «2 de 3» alcanza.

## Fotos (Speaking partes 2 y 3)

- Cada tarea trae `foto`, una descripción en inglés. Mientras no haya imagen, `prompt_es` describe la foto, así que la tarea funciona igual.
- Fero va a generar las **20 fotos** con Gemini (`FOTOS-PARA-GENERAR.md`: nombres de archivo y descripciones).
- Cuando lleguen:
  - van a `assets/images/speaking/`;
  - se añade `"imagenes": ["speaking/s-009.webp"]` (en la parte 3, `_1` y `_2`);
  - se aplica la validación estricta que ya se pidió (archivo existe, `.webp`/`.jpg`, ≤ 400 KB, parte 3 con 2 imágenes).
