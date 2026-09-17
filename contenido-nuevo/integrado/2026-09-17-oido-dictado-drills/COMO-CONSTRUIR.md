# Oído, dictado de números y drills nuevos (Cowork, 17-09-2026) — para Claude Code

Datos para tres actividades del plan de la auditoría. Los tres archivos pasaron por un redactor y un revisor adversarial: 36 defectos encontrados y todos corregidos. `validar_extra.py oido|dictado|drills <archivo>` da OK.

---

## 1. Pantalla de Oído — `oido.json`

**Qué trae:** 16 bloques, 147 pares mínimos.

| Grupo | Contrastes |
|---|---|
| Vocales | ship/sheep · cat/cut · full/fool |
| Consonantes que el español no separa | b/v · sh/ch · th/t · th sonora/d · s/z · m/n · r/l · y/j |
| La h | la h |
| Finales | t/d final · consonante final que se cae |
| Terminaciones y números | -ed · 13/30 (y los demás "-teen/-ty") |

**Evidencia:**
- Identificar cuál palabra sonó da g = 0,95; preguntar si son iguales da 0,57.
- Oír varias voces ayuda.
- La relación entre oír bien y pronunciar bien es débil (r = 0,31), así que cada bloque termina hablando.

**Formato:**
```json
{"id": "oido-i-ii", "title": "ship / sheep", "level": "A1", "contraste": "/ɪ/ corta · /iː/ larga",
 "explicacion": "…", "hablar": "…", "sound": "sh",
 "pares": [{"a": "ship", "b": "sheep", "frase": "Now I say ___ again."}, …]}
```

**La pantalla:**
1. Lista de bloques por nivel; cada bloque muestra su acierto.
2. Al entrar se muestra `explicacion`.
3. **Una voz por bloque** (hay que rotar entre bloques; `Speaker` tiene una sola voz cargada).
4. Por cada par, en orden aleatorio, se elige al azar `a` o `b` y se reproduce la `frase` con `___` reemplazado por esa palabra.
5. Aparecen dos botones, `a` y `b`, y se puede volver a oír.
6. Corrección inmediata: si falla, suena la otra palabra para comparar.
7. Al final del bloque, el alumno **dice** la frase `hablar` con el jurado de su `sound`, como un drill.
8. Mensaje honesto en pantalla: «Oír la diferencia no garantiza decirla: por eso cierras hablando».

Las frases son **neutras**: las dos palabras caben igual, así que el sentido no delata la respuesta.

**Antes de publicar, mide con `oir_pares.py`** (adáptalo para leer `oido.json` y usar la frase de cada par):
- Descarta todo par cuyo fonema distintivo salga en menos de 3 de las 4 voces.
- Guárdame la lista de descartados y la repongo con otros pares.
- Riesgos conocidos:
  - van y three ya van en frase;
  - el bloque u/uu y el y/j usan algunas palabras raras (wooed, soot, jeer, yaw) porque no hay muchos pares buenos;
  - las letras sueltas no aplican aquí.

---

## 2. Dictado de números — `dictado.json`

**Qué trae:** 80 ítems (36 A1 · 44 A2). Es la tarea literal de Aptis Listening parte 1.

| Tipo | Ítems |
|---|---|
| número | 14 |
| teléfono | 8 |
| hora | 14 |
| fecha | 14 |
| precio | 12 |
| deletreo | 8 |
| dirección | 10 |

Hay dos modos, 40 ítems de cada uno:
- **`escribir`:** la profesora lee `audio` (los números van en palabras, para que Piper los diga como se dicen) y el alumno escribe lo que pide `pregunta_es`. Se compara con `answer` + `accept`.
- **`elegir`:** un mensaje corto (buzón de voz, anuncio) y una pregunta con 3 opciones. Los distractores son las confusiones clásicas: 13/30, quarter to/past, fecha americana.

**⚠️ Hace falta código: comparación estricta para el dictado.**
- `Correccion.suelta` quita `.`, `:`, `$` y `@`. Por eso **acepta respuestas malas**:
  - «$650» por «$6.50», «$1200» por «$12», «$999» por «$9.99»;
  - «14:50» por «1450»;
  - «wademail.com» por «wade@mail.com».
- En esta pantalla la regla de la casa dice que un falso «bien» es el peor fallo.
- **Propuesta:** para `dictado`, normalizar sin borrar esos signos cuando van entre cifras o letras. Que «6.50» y «6:50» sigan siendo distintos de «650», y el `@` distinto de nada. Lo demás, igual que `suelta`: mayúsculas, espacios y números 0-100 en palabras.
- Al revés, la estricta rechaza algunas formas correctas raras (09:00, 07:30, Oct 21). Si las quieres, iguala el cero inicial de la hora.

**La pantalla:**
- Por tipo o mezclado.
- Hasta dos escuchas, como en el examen.
- Después de responder, el texto del audio y el `tip`.
- **Sin reloj.**
- Los fallos entran al cuaderno.
- Ojo: en los deletreos, Piper podría leer la letra «A,» como el artículo «a». Óyelo una vez (dict-063 a dict-070).

---

## 3. Drills nuevos — `drills-nuevos.json`

**Qué trae:** 30 drills, 5 por grupo, con el mismo formato de `drills.json`. Se añaden al final.

| Grupo | `sound` |
|---|---|
| Acento de palabra | `general` |
| 13 frente a 30 | `general` |
| Formas débiles (can/can't, to, and, wanna, gonna) | `general` |
| -s final /s z ɪz/ | `final` |
| r / l | `rl`, que pasa de 1 drill a 6 |
| Entonación | `general` |

- Para integrarlos basta con sumarlos a la lista `drills`. `checkContent` ya valida `sound` y CMUdict.
- Si la pantalla de pronunciación agrupa por sonido, los de `general` conviene separarlos por `focus` (acento, formas débiles, entonación). Si no, quedan 20 drills bajo "general".
