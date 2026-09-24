# Nivel B2 completo (Cowork, 24-09-2026)

Temario tomado del Core Inventory B2, English File Upper-intermediate y Cambridge B2 First, pensado para un adulto que hace un doctorado.

| Archivo | Qué es | Se integra con |
|---|---|---|
| `b2-nivel-completo.json` | **15 unidades, 45 lecciones, 540 ejercicios** (12 por lección, 70 % de producción) | `tools/content/anexar_nivel.py` |
| `vocabulario-b2.json` | **40 bancos B2, 1.000 parejas** | `tools/content/anexar_vocabulario.py` |
| `crucigramas-b2.json` | **44 crucigramas B2**, generados y validados contra el vocabulario ya con B2 | sumar la lista a `crucigramas.json` |
| `historias-b2.json` | **tanda-5, 6 historias B2** (h-b2-01 a h-b2-06) | `tools/content/anexar_historias.py` |
| `escenarios-b2.json` | **6 escenarios de conversación B2** (ver la última tabla) | `tools/content/anexar_escenarios.py` |

## Las lecciones

| Unidad | Lecciones |
|---|---|
| b2u1 ⏳ Tiempos narrativos avanzados | past perfect continuous · future in the past · future perfect continuous |
| b2u2 🧠 Matices de obligación y consejo | needn't have / didn't need to · had better / it's time / would rather · deducción y arrepentimiento con matices |
| b2u3 🔀 Condicionales mixtos y alternativas | condicionales mixtos · unless / provided that / even if / whether or not · wish e if only avanzado |
| b2u4 🏗️ La pasiva avanzada | It is said that / He is said to have · infinitivos y gerundios pasivos · causativa y need doing |
| b2u5 🗣️ Reportar con precisión | patrones de verbos (accuse of, insist on…) · cuándo no retroceder · reportar en escritura formal |
| b2u6 🔗 Relativos y participios | preposición + which/whom, all of which · oraciones de participio · relativas reducidas |
| b2u7 🧩 Gerundio e infinitivo avanzados | infinitivos perfectos y pasivos · see him cross / crossing · adjetivo o sustantivo + infinitivo |
| b2u8 ✨ Énfasis y orden | cleft sentences · do enfático e intensificadores · orden de adverbios y adjetivos, not only… but also |
| b2u9 🔢 Artículos, cantidades y comparación | artículos avanzados · a great deal of / hardly any · the sooner the better, nowhere near as |
| b2u10 🏷️ Formar palabras | prefijos · sufijos de sustantivo · sufijos de adjetivo y adverbio |
| b2u11 🧷 Phrasal verbs y colocaciones | con varios significados · de tres partes · colocaciones fuertes |
| b2u12 🎯 Falsos amigos y errores que se quedan | falsos amigos · errores fosilizados del hispanohablante · preposiciones difíciles |
| b2u13 📝 Escribir con estilo formal | ensayo y conectores · registro formal / informal · informes y tendencias |
| b2u14 🗣️ Argumentar, persuadir y matizar | hedging · persuadir y negociar · problemas y soluciones |
| b2u15 💼 Mundo profesional y académico | reuniones y presentaciones · correos diplomáticos · inglés académico para el doctorado |

## Los escenarios

negociar_b2 · defender_investigacion · reclamo_escalado · debate_b2 · conflicto_vecino · networking_academico.

## Verificación

- Diez redactores y cinco revisores adversariales.
- **Lecciones:** los revisores corrigieron 85 defectos, 55 de ellos ALTO:
  - respuestas correctas que se habrían rechazado;
  - inglés incorrecto dentro de `accept`;
  - pistas falsas;
  - una fecha británica;
  - fichas incompletas.
- **Vocabulario:** 47 choques resueltos y 33 defectos corregidos (heterónimos, británico, niveles, español poco colombiano).
- **Historias y escenarios:** 10 arreglos. Por ejemplo, un «error típico» que no lo era y una pregunta con dos respuestas.
- Todos los validadores (réplicas de `checkContent` y `Correccion.kt`) dan OK. Los probé en el PC sobre copias de los archivos actuales.

**Totales después de integrar:**
- 178 lecciones y 2.179 ejercicios.
- 164 bancos y 3.339 parejas.
- ≈ 3.335 palabras distintas de A1 a B2. English Profile pone 4.666 acumuladas, así que es el 71 %: el vocabulario de B2 es donde más queda por hacer.

## ⚠️ Para Claude Code

1. **`accept` muy largos:** hay 6.074 en B2 y el ejercicio más largo tiene 559. Son combinaciones verificadas. Si la corrección "qué te faltó" o `checkContent` se vuelven lentos, avísame y recorto los que puedan resolverse con una regla.
2. **`'d` = had:** sigue valiendo lo de B1. Hay que probar las dos expansiones (would y had).
3. **Desbloqueo:** B2 va después de B1.
