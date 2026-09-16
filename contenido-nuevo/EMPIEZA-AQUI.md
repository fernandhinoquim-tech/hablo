# Si Fero abre un chat nuevo, lee esto primero

Proyecto **Hablo**: app Android para que Fero (colombiano, no programa) aprenda
inglés. Meta real: **presentar Aptis ESOL General**, que le exige **B1 o
superior en las CUATRO destrezas** — es un piso, no un promedio. Él quiere
llegar a B2.

## Cómo está repartido el trabajo

- **Claude Code** (en su PC, Android Studio): todo el código Kotlin, compilar,
  instalar por `adb`, probar en el teléfono.
- **Claude en Cowork** (este chat): el contenido —lecciones, escenarios,
  bancos, historias—, la investigación previa a cada decisión, y **revisar lo
  que hace Claude Code**.
- **Fero**: es el mensajero entre los dos, y el que prueba la app.

Fero no corre comandos ni lee stack traces. Se le dan clics exactos.

## Qué leer, en este orden

1. **`CLAUDE.md`** del repo (https://github.com/fernandhinoquim-tech/hablo).
   Tiene el inventario de todo lo pedido, las etapas, las trampas ya pisadas y
   la decisión de Aptis. **Es la fuente de verdad, no los chats.**
2. **`contenido-nuevo/plan-por-etapas.md`** — las 6 etapas y quién hace qué.
3. **`contenido-nuevo/actividades-nuevas.md`** — las 17 actividades evaluadas,
   cada una con la cifra que la respalda y las que se descartaron.

## Dónde va el proyecto (16-09-2026)

Etapas **0 a 3 hechas** (v0.9.3): diagnóstico y arreglos · charla libre con
memoria y escenarios por nivel · adivina antes de ver, mazo Leitner,
corrige-tu-error, contrarreloj y Aguanta.

Contenido puesto: **A1 y A2 completos** (56 lecciones, 565 ejercicios),
17 escenarios (5 A1 · 6 A2 · 6 B1), 41 bancos de vocabulario (368 parejas).

**Etapa 4:** historias cortas con preguntas (d = 1,36; las escribe Cowork).
**Etapa 5:** Modo Aptis, **sección aparte** — decisión de Fero, no se toca el
curso por niveles. Los bancos del Core ya están en `contenido-nuevo/aptis/`.
**Etapa 6:** B1 y B2 del curso, respaldo del progreso, modo oscuro.

## Reglas que no se negocian

- **El reconocedor nunca ve la frase esperada.** Nada de sesgos ni hotwords.
- **Marcar mal algo que está bien es el peor fallo.** Le enseña que su inglés
  correcto es incorrecto. Cada vez que se toca la corrección, se comprueba.
- **No trabajar a ciegas** (regla suya): antes de elegir un modelo, un umbral o
  una actividad, buscar si alguien ya lo midió y citar la cifra en el commit.
- **Todo contenido nuevo se verifica de forma adversarial antes de entregarlo.**
  Las cinco pasadas que se han hecho encontraron entre 12 y 33 defectos reales
  cada una. Ninguna entrega se salta ese paso.
- Aptis **no puntúa fonemas**: la evaluación por fonema sirve para hablar
  mejor, no para la nota del examen.

## Lo único que falta y no depende de nosotros

Nada: Fero ya confirmó que le piden **B1 o superior en todas las destrezas**.
Lo siguiente es el **diagnóstico** del Modo Aptis, para saber cuál de las
cuatro es su piso — que es donde debe ir cada hora de estudio.
