# Reading parte 3 · opiniones (Cowork, 08-10-2026)

Es la única parte del examen Aptis que la app no tiene. Fero pidió con urgencia completar la preparación a B1 en las 4 destrezas.

**Qué es en el examen** ([guía oficial 2023](https://www.britishcouncil.vn/sites/default/files/aptis_esol_general_candidate_guide_2023_a4_0.pdf)): cuatro personas opinan sobre un tema; 7 preguntas «Who…?» y para cada una se elige a la persona en una lista desplegable. Una persona puede ser la respuesta de varias. En el [libro oficial de práctica](https://www.britishcouncil.vn/sites/default/files/aptis_esol_general_practice_tests.pdf) cada persona escribe unas 70-80 palabras.

## El banco: `parte3-opiniones.json`

18 tareas: **A2 4 · B1 10 · B2 4**. Ids `rop-01` a `rop-18`; ninguno existe en la app (comprobado).

| Nivel | Preguntas | Palabras por persona |
|---|---|---|
| A2 | 4 (una por persona) | 28-40 |
| B1 | 7 | 51-65 |
| B2 | 7, con inferencia | 64-73 (el largo del examen) |

- Formato: `{"partes": [ una parte ]}`, la misma estructura de `aptis-reading.json`.
- La parte tiene `"id": "opiniones"` porque en `aptis-reading.json` el id `parte3` ya lo usa la de títulos («Parte 4 · títulos»).
- Cada tarea:

```json
{"id": "rop-05", "level": "B1", "tipo": "opiniones",
 "instruccion": "Lee lo que opinan cuatro personas. ¿Quién dice cada cosa? …",
 "tema": "Social media",
 "personas": [{"nombre": "Daniel", "texto": "…"}, … 4],
 "preguntas": ["Who uses social media for their business?", … 7],
 "answer": ["Helen", … uno por pregunta],
 "citas": ["For my small bakery, social media is essential", … una por pregunta],
 "why": "…"}
```

- `citas[i]` es la frase **textual** del texto de `answer[i]` que prueba la respuesta. Va en la corrección: es la prueba, como la cita del juez en Writing.
- **Verificación:**
  - `validar_opiniones.py` da OK: 4 personas con nombres distintos, cada cita textual en el texto de su persona y en ningún otro, ninguna pregunta copia 4 palabras seguidas de su texto, toda persona es respuesta al menos una vez y como mucho tres, y largo por nivel.
  - Revisión adversarial: 14 arreglos, todos aplicados. Siete eran preguntas con una segunda respuesta posible: se reescribieron.

## Lo que hay que construir

1. **`Aptis.kt`**
   - Nueva `TareaLectura.Opiniones(id, level, instruccion, tema, personas: List<Persona(nombre, texto)>, preguntas, answer, citas, why)`, `tipo = "opiniones"`.
   - Rama `"opiniones"` en `lectura()`. Que falle con lección y motivo si:
     - no son 4 personas, hay nombres repetidos o vacíos;
     - `preguntas`, `answer` y `citas` no tienen el mismo tamaño, o están vacías;
     - un `answer` no es el nombre de una persona;
     - una cita no está dentro del `texto` de su persona.
   - Pista de la parte (donde están «ordenar» y «titulos», ~línea 900): `"opiniones" -> "Decir quién opina qué (parte 3 de Reading): busca la misma idea dicha con otras palabras, no la palabra repetida."`
2. **`build.gradle.kts` (`checkContent`)**: las mismas reglas, en los **dos** sitios donde hoy se valida `titulos` (~líneas 761 y 877).
3. **`ScreenAptis.kt`, `TareaOpinionesUi`:**
   - Arriba, el `tema`. Debajo, las 4 personas en tarjetas (nombre en negrita y texto), siempre a la vista con scroll: en el examen se relee todo el tiempo.
   - Luego «Pregunta i de n» con la pregunta y 4 botones `Opcion` con los nombres, más «Siguiente pregunta» / «Siguiente».
   - `puesto` = los nombres elegidos unidos con `" | "`.
   - **Acierto de la tarea:** `aciertos * 10 >= n * 7`, o sea A2 3 de 4 y B1/B2 5 de 7.
     - En el examen cada pregunta puntúa sola. Todo o nada con 7 preguntas haría que la promoción midiera suerte.
     - Es distinto de `titulos`, que con 3 párrafos sí es todo o nada.
4. **Corrección:**
   - Arriba: «✓ 6 de 7» o «✗ 4 de 7 · hacían falta 5».
   - Por pregunta: la pregunta, ✓ o ✗, el nombre correcto y la cita en cursiva. Si falló, también lo que eligió.
   - Al final, la tarjeta POR QUÉ.
5. **Integrar el banco:** meter la parte de `parte3-opiniones.json` en `app/src/main/assets/content/aptis-reading.json`, **entre** `parte2` (ordenar) y `parte3` (títulos). No cambia ninguna tarea existente.
   - Reading queda así: A1 13 · A2 18 · B1 40 · B2 14 = 85.
   - Ajustar `AptisTest` si cuenta tareas.
6. **Probar en el S25** sin tocar el progreso de Fero (trampa de CLAUDE.md: `force-stop`, copiar `aptis.json`, sembrar Reading en B1, probar, devolver y `cmp`). Mirar:
   - una tarea A2 y una B1 completas;
   - que la corrección muestre las citas;
   - que «5 de 7» cuente como acierto y «4 de 7» no.

El simulacro (`aptis-diagnostico.json`) no se toca en este lote.

## Herramienta

`validar_opiniones.py` también queda en `contenido-nuevo/herramientas-cowork/`. Uso: `python3 validar_opiniones.py parte3-opiniones.json [app/src/main/assets/content]`. Con la carpeta, revisa también que los ids no choquen con los de la app.
