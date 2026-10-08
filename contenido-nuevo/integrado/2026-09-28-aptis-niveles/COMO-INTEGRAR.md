# Aptis: niveles flojos completados (Cowork, 28-09-2026)

Salió de la evaluación general del 28-09. Las pistas empiezan en el nivel más bajo que tiene tareas, y Fero (A1-A2 real) va a empezar ahí. Pero A1, A2 y B2 tenían entre 2 y 9 tareas por pista, así que las iba a repetir y la pista mediría memoria.

Los cuatro `aptis-*.json` son **los archivos actuales de la app con tareas añadidas al final de cada parte**. Ninguna tarea existente cambió: lo comprobé id por id.

| Pista | A1 | A2 | B1 | B2 | Total |
|---|---|---|---|---|---|
| Writing | 2 → **8** | 4 → **10** | 30 | 4 → **12** | 40 → 60 |
| Speaking | 3 → **8** | 6 → **10** | 32 | 9 → **13** | 50 → 63 |
| Reading | 8 → **13** | 11 → **14** | 30 | 10 | 59 → 67 |
| Listening | 6 → **13** | 11 → **14** | 34 | 12 | 63 → 73 |

- **Sin fotos nuevas:** los añadidos de Speaking van en las partes 1 y 4.
- **Revisión adversarial:** 22 arreglos.
  - 6 tareas de Speaking repetían la situación de otras y se reemplazaron.
  - Dos de Reading repetían otra o no eran del nivel.
  - Hubo que ajustar rúbricas que no se podían juzgar desde la transcripción.
  - Había español de España.
- Los validadores dan OK.

**Integrar:** copiar los 4 archivos sobre `app/src/main/assets/content/`, y después `checkContent` y los tests.
