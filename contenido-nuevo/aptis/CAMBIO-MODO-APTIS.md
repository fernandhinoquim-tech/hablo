# Para Claude Code: el Modo Aptis cambia de diseño

Pedido de Fero el 17-09-2026. **Mandar esto antes que nada.**

El Modo Aptis **NO va a ser un diagnóstico**. Va a ser una **pista de
preparación de 0 a B2 por destreza**, que evalúa continuamente mientras
entrena.

**Por qué:** un test de 30 tareas da una foto con margen de error; una pista
graduada mide con cada tarea que hace. Y subir rápido de nivel **es** el
diagnóstico — si ya es B1 en lectura, se barre el A2 en una sesión y la pista
lo promueve. No hay que preguntárselo.

## Qué se CONSERVA (casi todo, no lo botes)

- `Condicion.cumple(aciertos, total)` → pasa a ser la **regla de promoción**.
- `EstimacionAptis.porTodos / .core / .porIa` → igual. Reciben una lista de
  (nivel, acertó); les da lo mismo si viene de un test o de práctica acumulada.
- `ItemCore`, `TareaEscucha`, `TareaEscrita`, `TareaHablada`, `SeccionAptis`.
- `AptisTest.kt`: los tests valen; añade los de promoción.
- El aviso de "es una estimación, no tu nota de Aptis" y la regla de que **la
  IA cite una frase del alumno como prueba de cada juicio**. Los dos se quedan.

## Qué CAMBIA

- `Diagnostico` deja de ser un test fijo y pasa a ser un **banco de tareas por
  destreza × nivel** (A1, A2, B1, B2).
- **Cinco pistas**: Core, Reading, Listening, Writing, Speaking. Cada una
  arranca en A1 y sube.
- **Promoción**: sube de nivel en una destreza al cumplir la `Condicion` sobre
  las últimas N tareas de ese nivel. Si baja, **no lo degrades de golpe**:
  mézclale tareas del nivel anterior hasta que se recupere.
- La pantalla de resultados pasa a ser un **tablero**: una barra por destreza
  con su nivel actual y, arriba, **cuál va última**. Se actualiza solo.
- El **simulacro completo cronometrado** se desbloquea cuando las cinco pistas
  estén en B1 o más. Ese sí es de una sentada y con el reloj real del examen.

**Recuerda por qué el tablero importa:** a Fero le exigen **B1 o superior en
las CUATRO** destrezas. Es un piso, no un promedio. La destreza que va última
es donde tiene que estudiar; las demás no le suben la nota.

## Contenido disponible

- **Core**: `contenido-nuevo/aptis/core-gramatica.json` y
  `core-vocabulario.json` — 120 ítems con `level`.
- **Writing**: `contenido-nuevo/aptis/pista-writing.json` — 16 tareas, las 4
  partes del examen, A1 a B2, cada una con rúbrica.
- Las tareas de `diagnostico.json` se **reciclan** como primeras tareas de sus
  pistas.
- Faltan Reading, Listening y Speaking. Los manda Cowork.

## Y anótalo en CLAUDE.md

Reemplazando lo que diga del diagnóstico, para que no se construya lo que no es.
