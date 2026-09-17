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

## ⚠️ LO ÚNICO QUE NO ESTÁ EN NINGÚN REPO: el rediseño del Modo Aptis

Decidido el 17-09 y **Fero todavía no se lo ha mandado a Claude Code.** Si se
pierde esto, se construye lo que no es.

**El Modo Aptis NO es un diagnóstico. Es una PISTA DE PREPARACIÓN de 0 a B2
por destreza, que evalúa continuamente mientras entrena.** Fero lo pidió así
y tiene razón: una pista mide con cada tarea, no con una foto de 36 minutos, y
subir rápido de nivel *es* el diagnóstico.

Claude Code ya construyó el diagnóstico (`Aptis.kt`, `ScreenAptis.kt`,
`AptisTest.kt`). **Casi todo se reaprovecha:** `Condicion.cumple()` pasa a ser
la regla de promoción; `EstimacionAptis` sirve igual con datos acumulados; las
clases de tarea no cambian. Lo que cambia es el flujo y la pantalla.

El mensaje completo para Claude Code está en
**`contenido-nuevo/aptis/CAMBIO-MODO-APTIS.md`**. Mandarlo es lo primero.

## Lo que falta, en orden

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

## Dos cosas que parecen bug y NO lo son

- **"Spanish" marcado dudoso con dictado perfecto**: el modelo de fonemas oye
  la ε de "espanish" y el dictado es ciego a eso. **No subir ese umbral.**
- **live/leave**: Piper lo dice bien. Fue error de oído real de Fero.
