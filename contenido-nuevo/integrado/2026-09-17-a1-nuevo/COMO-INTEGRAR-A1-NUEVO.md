# A1 ampliado (Cowork, 17-09-2026)

Paso 3 del plan de la auditoría (`contenido-nuevo/auditoria-a1-a2/`).

**Qué trae:**
- **16 lecciones nuevas en 6 unidades** (211 ejercicios), **intercaladas** donde les toca, como en Empower:

| Unidad | Va después de | Lecciones |
|---|---|---|
| a1u10 🔤 Letras, números y el sí/no de be | a1u1 | alfabeto y deletrear · números 0-100 y teléfono · to be negativo, preguntas y respuestas cortas |
| a1u11 🧑 Tú y los demás | a1u3 | have/has · me/him/her/us/them + our/their · describir a alguien |
| a1u12 📅 La hora, las fechas y el clima | a1u4 | la hora · meses, ordinales y cumpleaños · el clima y las estaciones |
| a1u13 🗺️ Colores, órdenes y preguntas | a1u5 | colores y el adjetivo antes del sustantivo · imperativo y direcciones · what/which/who/whose/how often |
| a1u14 🎉 Precios, gustos y planes | a1u7 | precios y plurales irregulares · like/love/hate + -ing · Let's / Would you like to…? |
| a1u15 🔎 Preguntar por el pasado | a1u8 | Where did you go? · respuestas cortas · there was/were |

- **57 ejercicios añadidos a 16 lecciones existentes** (`adiciones-a1.json`): lo que su ficha enseñaba y ningún ejercicio practicaba (has/-es, respuestas cortas, *How often*, *between*, ortografía del -ing, *a few/a little*, *Can I…?*, *Was he…?*, *did/got/ate*, *Will you…?*, superlativos…).
- **Seis fichas existentes** ganan una línea para eso mismo. Ese cambio va en `parche-fichas-a1.json` (a1u4l2, a1u4l3, a1u6l1, a1u7l3, a1u8l1, a1u9l2). Llegó después de que ya habías integrado el parche de la auditoría.
- **Total A1 después de integrar: 43 lecciones, 568 ejercicios** (antes 27 y 300: 300 + 211 + 57).

**Verificado:**
- Cuatro redactores escribieron el contenido y cuatro revisores adversariales distintos lo revisaron. Encontraron 88 defectos (30 ALTO) y todos están corregidos.
- Validador con las reglas de `checkContent` y `Correccion.kt` (fichas de build, `accept` sin duplicados ni distractores, CMUdict, `play`): OK.
- Probado de punta a punta sobre una copia del `curriculum.json` actual: parche → unidades → adiciones → `validar2.py` OK.

## Orden para integrar

1. **Las seis fichas:** `python contenido-nuevo/integrado/2026-09-17-parche-auditoria/parchar2.py app/src/main/assets/content/curriculum.json contenido-nuevo/a1-nuevo/parche-fichas-a1.json` y copiar el `.parchado.json` encima. (El parche grande ya lo integraste; comprobé que el curso actual es exactamente ese parche, salvo tu cambio en a2u6l3e9.)
2. Las unidades nuevas:
   `python contenido-nuevo/a1-nuevo/anexar_unidades.py app/src/main/assets/content/curriculum.json contenido-nuevo/a1-nuevo/nuevo-a1u10-u11.json contenido-nuevo/a1-nuevo/nuevo-a1u12-u13.json contenido-nuevo/a1-nuevo/nuevo-a1u14-u15.json`
   (escribe en sitio con `migrar_a1.escribir` y se para si algún id choca).
3. Los ejercicios añadidos: `python tools/content/anexar.py contenido-nuevo/a1-nuevo/adiciones-a1.json`
4. `validar2.py`, `checkContent` y los tests. Después, mover a `integrado/2026-09-17-a1-nuevo/`.

## ⚠️ Lo que necesita código (Claude Code)

1. **La cadena de desbloqueo se rompe al intercalar.** `ScreenHome.kt` (~98) abre una unidad solo si **todas** las anteriores están aprobadas. Con a1u10 metida después de a1u1, **todo lo que Fero ya aprobó desde a1u2 quedaría con candado** hasta que haga a1u10.
   - Arreglo propuesto: una unidad también queda abierta si alguna de sus lecciones ya tiene puntaje.
   - Mejor aún: las unidades nuevas se abren junto con la siguiente unidad que él ya tenga abierta, así las hace en paralelo sin perder nada.
2. **Fechas y números.** `Correccion.numeros` convierte «May 3» en «may three», así que aceptaría justo el error que enseña a1u12l2 («May three» en vez de «May third»). Por eso quité los `accept` tipo «May 3» y los tips dicen «escríbelo May 3rd o May third».
   - Si haces que un número justo después de un mes **no** se convierta, avísame y los vuelvo a poner: en EE. UU. se escribe «May 3».
3. **Oír con Piper antes de publicar:**
   - a1u10l1e8 (par E / I, letras sueltas dentro de «My name starts with …»)
   - a1u13l1e9 (white / wide)
   - las listen de a1u15l1e5 (*There was many* / *There were many*: la diferencia es la vocal)
4. **Fichas largas:** 4 fichas nuevas pasan un poco de 110 palabras (113-116). Si la pantalla de la ficha se ve bien con eso, no hay que hacer nada.
