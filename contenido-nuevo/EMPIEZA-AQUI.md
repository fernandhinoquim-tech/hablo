# Hablo — traspaso al 17-09-2026. Lee esto primero.

App Android para que **Fero** (colombiano, 40 años, doctorando, **no programa**)
aprenda inglés. Meta real: **Aptis ESOL General**, que le exige **B1 o superior
en las CUATRO destrezas** — es un **piso, no un promedio**, así que lo que manda
es su destreza más floja. Él quiere llegar a B2. **No hay fecha de examen aún.**

## Reparto del trabajo

- **Claude Code** (su PC, Android Studio): todo el Kotlin, compilar, instalar
  por `adb`, probar en el teléfono.
- **Claude en Cowork** (este chat): el **contenido** —lecciones, escenarios,
  bancos, historias, pistas de Aptis—, **investigar antes de decidir**, y
  **revisar lo que hace Claude Code**.
- **Fero**: mensajero entre los dos, y el que prueba la app.

Fero no corre comandos ni lee stack traces. Se le dan clics exactos.

## Qué leer, en orden

1. **`CLAUDE.md`** del repo `fernandhinoquim-tech/hablo` — inventario de todo
   lo pedido, etapas, trampas ya pisadas. **Es la fuente de verdad.**
2. **`contenido-nuevo/plan-por-etapas.md`** — las 6 etapas y quién hace qué.
3. **`contenido-nuevo/actividades-nuevas.md`** — 17 actividades evaluadas con
   la cifra que las respalda, y las descartadas con su razón.

## Estado (v0.9.6)

**Etapas 0-4 CERRADAS.** Diagnóstico y arreglos · charla libre con memoria y
escenarios por nivel · adivina-antes-de-ver, mazo Leitner, corrige-tu-error,
contrarreloj y Aguanta · historias con retell.

**Contenido puesto:** A1 y A2 completos (56 lecciones, 565 ejercicios, 56
fichas de teoría) · 17 escenarios (5 A1 · 6 A2 · 6 B1) · 41 bancos de
vocabulario (368 parejas) · 12 historias (2 tandas).

## Actualización 17-09 (tarde): dos correcciones y una auditoría

- **El rediseño del Modo Aptis YA LO CONSTRUYÓ Claude Code** (0.9.7, en el PC,
  sin commit todavía): cinco pistas, tablero con el piso, simulacro bloqueado
  hasta B1. No hay que volver a mandar `CAMBIO-MODO-APTIS.md`.
- **Formato de las pistas:** resuelto. Claude Code ya integró Writing tal cual
  (`aptis-writing.json`, commit 9103ce1): el parser acepta `partes` y dentro
  de cada parte las tareas van en el formato de su sección del simulacro
  (`aptis-diagnostico.json`). Reading, Listening y Speaking: mismo esquema.
- Claude Code ya hizo los cambios de código de la auditoría: `accept` en
  translate, build y type, respetado en mazo, Aguanta y adivina (6ff3cc9).
- **Auditoría de A1 y A2** en `contenido-nuevo/auditoria-a1-a2/`: faltan 16
  lecciones básicas de A1 y 14 de A2, el vocabulario va al 38 % de A2, ≈ 220
  defectos en los 565 ejercicios (informes por unidad con el arreglo exacto),
  y tres cambios de código (`accept` en translate/build/type). **Su plan de 10
  pasos es el orden de trabajo vigente.**

## Lo que falta, en orden

0. **Estado al 17-09 (tarde), verificado en los assets y el código (0.9.8):**
   INTEGRADO todo lo de la auditoría: parche, A1 (43 lecciones), A2 (45),
   vocabulario A1-A2 (84 bancos), 24 historias, 70 drills, pistas de Aptis
   (Reading 42 · Listening 42 · Speaking 28 · Writing 16), crucigramas (103),
   Oído (132 pares, 15 descartados por medición: ver
   `integrado/2026-09-17-oido-dictado-drills/DESCARTES-OIDO.md`), dictado
   (80, comparación estricta), pantalla de Gramática + "¿Por qué?", soporte
   de `imagenes` en Speaking.
   ESCRITO, falta integrar: `b1-nivel/` (45 lecciones, 540 ejercicios) y
   `vocab-b1/` (40 bancos, 1.000 parejas + 63 crucigramas B1).
   Pendiente de Fero: las 20 fotos de Speaking.
   B2 COMPLETO escrito y verificado (24-09) en `contenido-nuevo/b2-completo/`
   (45 lecciones, 1.000 parejas, 44 crucigramas, 6 historias, 6 escenarios);
   INTEGRADO el 24-09 (0.9.10), archivado en `integrado/2026-09-24-b2-completo/`. Prompt de las fotos para Gemini: `contenido-nuevo/fotos-gemini/`.
   Pendiente de Cowork: repuestos para los 15 pares de Oído
   descartados (bloques t/d final y u/uu quedaron cortos).
1. **Pistas de Aptis**: Writing **hecho**
   (`contenido-nuevo/aptis/pista-writing.json`, 16 tareas). Faltan **Reading,
   Listening y Speaking** — mismo formato, ver `validar_pista.py`.
   El Core ya está: `core-gramatica.json` + `core-vocabulario.json`, 120 ítems.
2. **Contenido B1 y B2** del curso. Es la montaña: A1+A2 son el 27 % del camino.
3. **Cuatro actividades sin construir**: crucigramas (las rejillas las genera
   Cowork), pantalla de Oído, doblar la escena, dictogloss.
4. **Fotos de Speaking** — partes 2 y 3 son describir y comparar fotos. Las
   genera **Fero** con su suscripción de Google; Cowork escribe las preguntas.

## Reglas que no se negocian

- **El reconocedor nunca ve la frase esperada.** Ni hotwords ni sesgos.
- **Marcar mal algo que está bien es el peor fallo.** Le enseña que su inglés
  correcto es incorrecto.
- **No trabajar a ciegas** (regla suya): buscar si alguien ya lo midió antes de
  elegir modelo, umbral o actividad, y citar la cifra en el commit.
- **Todo contenido se verifica de forma adversarial antes de entregarlo.** Las
  seis pasadas hechas encontraron entre 12 y 33 defectos reales **cada una**.
  Ninguna entrega se salta ese paso. Nunca ha fallado en encontrar algo.
- **El curso va en inglés americano; el Modo Aptis enseña la diferencia con el
  británico** como contenido (Aptis es del British Council).
- **Aptis no puntúa fonemas**: el GOP sirve para hablar mejor, no para la nota.
- **Una etapa a la vez**, y termina cuando Fero la usó, no cuando compila.

## Trampa de Cowork

- **No correr `git` desde Cowork en la carpeta del repo.** El 17-09 un
  `git status` dejó `.git/index.lock` sin poder borrarlo (el puente no deja
  borrar sin permiso) y habría bloqueado los commits de Claude Code. Para
  saber el estado, leer archivos o preguntarle a Claude Code.

## Dos cosas que parecen bug y NO lo son

- **"Spanish" marcado dudoso con dictado perfecto**: el modelo de fonemas oye
  la ε de "espanish" y el dictado es ciego a eso. **No subir ese umbral.**
- **live/leave**: Piper lo dice bien. Fue error de oído real de Fero.
