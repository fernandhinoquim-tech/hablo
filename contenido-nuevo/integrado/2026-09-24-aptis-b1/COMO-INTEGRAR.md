# Aptis B1 a fondo + repuestos de Oído (Cowork, 24-09-2026)

Pedido de Claude Code, a partir del cuaderno de errores de Fero: no más lecciones; la prioridad es B1 en las pistas de Aptis, porque es el umbral del examen.

## Pistas de Aptis: archivos completos para reemplazar

Los cuatro `aptis-*.json` son el archivo actual con las tareas nuevas **añadidas al final de cada parte**. No se modificó ni se borró ninguna tarea existente: lo comprobé id por id.

| Pista | Tareas B1 antes → ahora | Total | Nuevas, por parte del examen |
|---|---|---|---|
| Writing | 6 → **30** | 40 | P2 +8 · P3 +8 · P4 +8 (w2-05…w4-12) |
| Speaking | 10 → **32** | 50 | P1 +6 · P2 +5 · P3 +5 · P4 +6 (s-029…s-050) |
| Reading | 13 → **30** | 59 | P1 +7 · P2 +5 · P4 +5 (r-043…r-059) |
| Listening | 13 → **34** | 63 | P1 +6 · P2 +5 · P3 +5 · P4 +5 (l-043…l-063) |

- **Verificación:** 4 redactores y 2 revisores adversariales.
  - Corrigieron 23 defectos.
  - Dos tareas de Listening eran en realidad A2 y se reescribieron.
  - Una situación de Writing repetía otra.
  - Una rúbrica no se podía juzgar desde el texto.
  - Había español de España («piso», «móvil», «billete»).
  - Los validadores dan OK.
- **Integrar:** copiar los 4 archivos sobre `app/src/main/assets/content/`. Después `checkContent` y los tests.
- **Fotos:** las 10 tareas nuevas de Speaking partes 2 y 3 necesitan **15 fotos más**. Fero las genera con `PROMPT-GEMINI-FOTOS-2.md`. Mientras tanto, `prompt_es` describe la foto y la tarea funciona igual.

## Oído: `oido-repuestos.json` (candidatos para medir)

- Son 30 candidatos en total, repartidos por bloque (`{"candidatos": {bloque: [{a, b, frase}]}}`), en orden de preferencia.
- **Mídelos con tu `oir_oido.py`** y añade los que pasen con 3 de 4 voces, hasta reponer lo descartado.

| Bloque | Candidatos |
|---|---|
| oido-final-t-d | 12 |
| oido-b-v | 4 |
| oido-final-consonante | 3 (incluye no/note para el título) |
| oido-ed | 3 (incluye start/started para el título) |
| oido-numeros | 2 (thirteenth/thirtieth, fourteenth/fortieth) |
| oido-ae-uh | 2 |
| oido-s-z | 2 |
| oido-u-uu | 1 |
| oido-dh-d | 1 |

**Honesto:** para **y/j** y **u/uu** ya no quedan pares comunes en inglés; el revisor quitó los nombres propios y las palabras raras. Esos dos bloques se quedan en 7 y 5-6 pares. La alternativa es fusionar u/uu con otro bloque de vocales, o aceptar bloques más cortos. Tú decides.

## Los 5 `accept` abiertos del diagnóstico del 15-09

Ya están cerrados en el curso actual: a2u6l3e5, a2u9l1e5, a1u1l3e9, a2u1l3e5 y a2u9l2e4 tienen sus alternativas desde el parche de la auditoría. Los revisé uno por uno. No hace falta nada.
