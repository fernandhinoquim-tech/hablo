# Plan por etapas — todo lo pedido, en orden

Inventario completo de lo que Fero ha pedido desde el principio, verificado
contra el repo el 15-09. Nada de esto es memoria: cada "ya está" se comprobó
abriendo el código.

---

## Lo que YA ESTÁ (para no volver a pedirlo)

Cuatro profesoras con voz propia y elegibles · voces femeninas · retratos con
halo al hablar · lecciones y pronunciación **sin internet** · reconocimiento de
voz honesto (no "arregla" lo mal dicho) · puntaje por fonema (GOP) con umbrales
calibrados · conversación con IA en 5 escenarios · corrección en español ·
informe descargable · cuaderno de errores · racha y puntos · **A1 y A2
completos: 56 lecciones, 565 ejercicios** · 13 tests · juego de Parejas.

## Lo que FALTA (verificado, no supuesto)

| # | pedido | estado real |
|---|---|---|
| 1 | Marca mal respuestas que están bien | **bug abierto**, en diagnóstico |
| 2 | Leyendas viejas y textos sin actualizar | en diagnóstico |
| 3 | Charla libre sin escenario ni nivel, **con memoria** | **no existe** (no hay `perfil.json`) |
| 4 | Escenarios divididos **por nivel**, sin memoria | hay 5, **todos A1** |
| 5 | Actividades más diversas | diseñadas, sin construir |
| 6 | Retos con presión | diseñados, sin construir |
| 7 | Vocabulario con cronómetro | Parejas existe, **sin reloj ni banco** |
| 8 | Historias cortas con preguntas | no existe |
| 9 | Crucigramas | no existe |
| 10 | Repaso espaciado (mazo) | no existe — era el punto 4 del plan del 13-09 |
| 11 | Pantalla de Oído (pares mínimos) | el tipo existe, la **pantalla no** |
| 12 | Contenido B1 y B2 | no existe |
| 13 | Escritura con motor de reglas (Fase 4) | no existe |
| 14 | Respaldo del progreso y modo oscuro (Fase 6) | no existe |
| 15 | Recalibrar audio al llegar a ~100 grabaciones | pendiente con condición |

---

## La regla que evita que se pierda algo

**Esta tabla va a `CLAUDE.md`, no a un chat.** Claude Code lee ese archivo cada
sesión; los chats se pierden. Cada etapa terminada tacha su línea ahí mismo,
con la fecha y el commit. Es lo único que garantiza que dentro de tres semanas
no se nos olvide la charla libre otra vez — que ya se olvidó una vez.

---

## Etapa 0 — Diagnóstico · EN CURSO

Claude Code ya está en esto: sacar los datos reales del teléfono, encontrar en
ellos los casos donde marcó mal algo bueno, recorrer la app con `adb` buscando
textos viejos, correr los tests. **Reporta y no toca nada.**

No se avanza a la etapa 1 hasta tener esa lista.

---

## Etapa 1 — Que no enseñe nada incorrecto

**Claude Code:** arregla lo que salió del diagnóstico. Prioridad absoluta al
bug de marcar mal lo correcto — su propio criterio dice que un falso "bien"
deja el error puesto para siempre, y esto es la otra cara: un falso "mal" le
enseña que su inglés bueno es malo. Limpia las leyendas viejas. Mete el
inventario de arriba en `CLAUDE.md`.

**Yo:** reviso los casos que encontró y digo cuáles son bug de código y cuáles
son `accept` que me faltó poner en el contenido.

**Termina cuando:** él puede usar la app una semana sin que lo corrija mal.

---

## Etapa 2 — La profesora que se acuerda

Lo que más ha pedido y lo único que se olvidó del plan anterior.

**Claude Code:**
- **Charla libre**: sin escenario, sin nivel, hilo propio. Ficha de memoria en
  `files/memoria/perfil.json` — datos del alumno, errores con contador, resumen
  de la charla. Topes duros: 12 datos, 15 errores, 60 palabras, 400 tokens. Los
  errores se registran gratis desde la línea `CORRECCIÓN:`; el resumen se hace
  con **una** llamada a Haiku al cerrar. Si el JSON no parsea, se conserva la
  ficha vieja.
- **Escenarios por nivel**: agrupados por `level` en la pantalla, **sin
  memoria** — cada escenario arranca limpio. Esa separación ya estaba decidida
  el 13-09.

**Yo:** escribo escenarios A2 y B1 para que haya qué dividir. Hoy hay 5 y todos
son A1.

**Termina cuando:** puede abrir la app, hablar de lo que quiera, y al día
siguiente la profesora se acuerda de lo básico.

---

## Etapa 3 — Retos y repaso

Ataca las dos quejas a la vez: "es repetitiva" y "se me acaba el contenido".
Todo aquí se apoya en lo que ya existe, **sin contenido nuevo mío**.

**Claude Code, en este orden:**
1. **Adivina antes de ver** — antes de presentar algo nuevo, te lo pregunta.
   Lo más barato del plan y lo de mejor relación evidencia/trabajo.
2. **Corrige tu propio error** — saca del cuaderno una frase que **él** escribió
   mal semanas atrás y se la devuelve. Los datos ya están en `progreso.json`.
3. **Contrarreloj** — Parejas con reloj y con banco de vocabulario, y la regla
   que importa: **lo fallado vuelve hasta que salga**. El reloj es velocímetro,
   no juez: guarda la curva, nunca reprueba por lento.
4. **Aguanta** — ronda mezclando todo lo visto, termina a los tres errores.
   Sin puntos: *"llegaste a 14; tu marca es 11"*.
5. **Mazo de repaso (Leitner)** 1/3/7/16/35, con la escalera de dificultad del
   mismo ítem: elegir → armar → escribir → oír y escribir → decir. Un ítem no
   está aprendido hasta que se **produjo**, no solo se reconoció.

El mazo es el que convierte 565 ejercicios en práctica infinita en vez de
contenido que se acaba.

**Termina cuando:** puede abrir la app sin lección pendiente y aun así tener
veinte minutos de práctica distinta cada día.

---

## Etapa 4 — Las historias

La actividad de mejor evidencia de todo el proyecto (d = 1,36; lectura 1,87,
gramática 1,71, habla 1,38).

**Claude Code:** tipo `story` — la profesora la lee y está en pantalla →
3 preguntas → **él la cuenta de vuelta en voz alta** (el retell es la mitad del
efecto, sin eso es solo comprensión lectora) → 2-3 palabras pasan al mazo.
Más `ordenar la historia`, que se monta encima del mismo material.

**Yo:** escribo las historias **por tandas de 3 a 12**, que es donde el efecto
es máximo (d = 2,53). No una por lección para siempre.

---

## Etapa 5 — El resto de actividades

Por orden de valor sobre trabajo:

| actividad | quién construye | quién escribe |
|---|---|---|
| Crucigramas (5-8 palabras, pistas en español) | Code dibuja | yo genero las rejillas |
| Pantalla de **Oído** (pares mínimos, 4 voces, rotando **por bloque**) | Code | yo los pares |
| **Doblar la escena** (diálogo por papeles) | Code | yo los diálogos |
| Reconstruir el texto (dictogloss) | Code | yo los textos |
| Escribir un mensaje corto | Code | yo las consignas |
| Anagramas · dictado de números y fechas | Code | yo las listas |
| Vocabulario con icono + español | Code | yo elijo las ~700 concretas |

---

## Etapa 6 — B1, B2 y pulido

**Yo:** contenido B1 y B2. Es la montaña real: A1 y A2 juntos son el 27 % del
camino a B2.

**Claude Code:** respaldo del progreso, modo oscuro, y recalibrar los umbrales
de audio cuando el corpus llegue a ~100 grabaciones.

---

## Cómo se manejan las etapas

- **Una etapa a la vez.** No se abre la siguiente hasta que la anterior esté en
  el teléfono y él la haya usado.
- **Cada etapa termina con Fero usándola**, no con un commit verde.
- **Si aparece un bug, vuelve a la etapa 1.** Un fallo que le enseña inglés
  incorrecto le gana a cualquier actividad nueva.
- **Lo que yo escribo va en paralelo**, no bloquea. Mientras él construye la
  etapa N, yo escribo el contenido de la N+1.

---

# Actualización 15-09: Modo Aptis

Fero va a presentar **Aptis ESOL General**. Sin fecha todavía y sin saber qué
nivel le exigen. Decisión suya, y es la correcta: **el curso se queda como
está** y Aptis entra como **sección aparte**.

El razonamiento: el curso general sube el nivel; la práctica de Aptis sube la
nota a un nivel dado. Hacen falta las dos y el nivel va primero. Y una sección
aparte se construye una vez, no contamina las 56 lecciones, y está lista el día
que aparezca la fecha.

## Lo que hay que saber del examen

**18 tipos de tarea** en cinco componentes. Hoy Hablo practica 2.

**Core** (25 min): 25 de gramática MC + 25 de vocabulario en cuatro subtipos —
sinónimos, definiciones, uso, y **combinaciones de palabras**.
**Reading** (35 min): completar frase · **ordenar 6 frases** ×2 · 4 opiniones →
7 frases · texto de **750 palabras** con 7 de 8 títulos.
**Listening** (40 min): **números, horas y lugares** · 4 monólogos → 6 datos ·
¿lo dijo él, ella o los dos? · pistas en el tono.
**Writing** (50 min): 5 respuestas de una palabra · 20-30 palabras · 3× 30-40
palabras · **dos correos, informal y formal, 120-150 palabras**.
**Speaking** (12 min): 3 preguntas personales 30 s · **describir una foto** 45 s
· **comparar dos fotos** 45 s · tema abstracto con 1 min de preparación y 2 de
habla.

**Dos hallazgos que cambian prioridades:**

1. **El Core es un desempate.** Si una destreza queda justo bajo el umbral de un
   nivel, la nota de gramática y vocabulario puede subirla. Es el único
   componente que rescata a los otros cuatro: lo más rentable de estudiar.
2. **Aptis no puntúa fonemas.** El speaking se califica de forma holística. La
   evaluación por fonema (GOP), que es nuestra pieza más trabajada, **no es la
   palanca grande para el examen**. Sigue sirviendo para hablar mejor; no para
   la nota directamente.

Y tres actividades que yo había puesto en prioridad baja **son tareas literales
del examen**: ordenar frases (Reading 2), dictado de números y horas
(Listening 1) y las combinaciones de palabras (un cuarto del vocabulario del
Core). Suben de prioridad.

## Dónde encaja en las etapas

Las etapas **0 a 4 no se tocan**: el bug, la charla libre, los retos y el mazo,
y las historias. Eso sube su nivel, que es el requisito previo.

**La etapa 5 pasa a ser "Modo Aptis"** y absorbe las actividades que ya estaban
previstas, pero **construidas en formato de examen desde el principio** — mismo
trabajo, forma útil:

| ya estaba previsto | se construye como |
|---|---|
| Ordenar el diálogo | Reading parte 2 |
| Dictado de números y fechas | Listening parte 1 |
| Escribir un mensaje corto | Writing parte 4 (los dos correos) |
| Historias cortas | base para Reading parte 4 (textos de 750 palabras) |
| Frases hechas *(las había descartado)* | Core: combinaciones de palabras |

Y se suman las que son solo de Aptis: opiniones de cuatro personas, títulos
para párrafos, él/ella/los dos, describir y comparar fotos, tema abstracto.

## Lo primero del Modo Aptis: saber dónde está

**No sabe qué nivel le exigen ni en cuál está.** Así que lo primero que se
construye no es práctica: es un **diagnóstico corto** — una tarea de cada
componente, cronometrada — que le diga en qué nivel anda hoy en cada destreza.

Eso decide todo lo demás: si le falta un nivel entero, toca curso; si le falta
medio, toca técnica de examen. Y le dice si vale la pena ya agendar la fecha.

**Tarea para Fero, y cuesta cero:** averiguar **qué nivel le piden**. Es el dato
que más cambia la preparación y no depende de nosotros.

## Lo que hace falta y todavía no tenemos

- **Imágenes.** Speaking 2 y 3 son describir una foto y comparar dos. Sin
  imágenes no hay cómo practicarlas. Él ya generó los retratos de las
  profesoras con una herramienta de imágenes: el mismo camino sirve.
- **Corrección de writing y speaking.** La hace Claude API contra una rúbrica
  derivada de los descriptores del MCER y del documento oficial de puntuación.
  **Hay que decirlo en pantalla: es una estimación, no la nota real.** Prometer
  una nota exacta sería mentir.
- **Bancos grandes.** Un simulacro se gasta al hacerlo. Para que sirva de verdad
  hacen falta varios cientos de ítems de Core y varios simulacros completos.
  Eso lo escribo yo, y es trabajo de volumen parecido al de un nivel.

## Etapa 6, revisada

Contenido B1 y B2 (que es lo que de verdad lo lleva al nivel del examen), más
respaldo del progreso, modo oscuro y la recalibración del audio.
