# Modo Aptis — bancos del Core (etapa 5, no construir todavía)

Dos archivos, 120 ítems. **Esto es de la etapa 5**: no se integra hasta que
estén cerradas la 1 (arreglos) y la 2 (charla libre y escenarios por nivel).
Lo escribo ahora porque no bloquea a nadie y porque ya sé el formato exacto.

- `core-gramatica.json` — 60 ítems, `text` con un hueco `___` y 3 opciones.
- `core-vocabulario.json` — 60 ítems en los **cuatro subtipos oficiales**:
  `synonym`, `definition`, `usage`, `collocation` (15 de cada uno).
- `validar_core.py` — el validador.

## Por qué el Core primero

De los cinco componentes, el Core es el único que **rescata a los otros
cuatro**: si una destreza queda justo bajo el umbral de un nivel, la nota de
gramática y vocabulario puede subirla. Es lo más rentable por hora de estudio.

## El formato del examen, para que la pantalla lo respete

**25 minutos para 50 preguntas: 30 segundos cada una.** La pantalla tiene que
entrenar eso, no solo el contenido. Un ítem, tres opciones, sin volver atrás,
con el reloj a la vista. Es el único sitio de toda la app donde la presión de
tiempo está justificada: **es la condición real del examen**.

El banco tiene 60 de cada tipo y el examen presenta 25: alcanza para dos
simulacros completos sin repetir, más práctica suelta.

## Campos

```json
{"id":"g016","level":"B1","point":"present perfect vs pasado simple",
 "text":"I ___ to London three times so far this year.",
 "options":["went","have been","was going"],
 "answer":"have been",
 "why":"'So far this year' es un periodo que sigue abierto..."}
```

`why` es obligatorio y **se muestra al corregir**, no solo al fallar: la
corrección explicada rinde g = 0,73 contra 0,39 sin ella. `level` permite
graduar la dificultad y estimar en qué nivel anda.

## Verificado: 28 ítems corregidos

Un agente revisó los 120 uno por uno. Lo que encontró, por si sirve de
precedente:

- **Nueve ítems tenían más de una opción correcta.** El peor: *"I ___ to London
  three times"* con `have been` como clave — pero *"I went to London three
  times"* es inglés perfecto. Le habrías marcado mal una respuesta buena.
- **Un sesgo de posición que invalidaba el banco entero.** La respuesta correcta
  estaba en la **opción 2 en 42 de los 60** ítems de gramática: contestando
  siempre la del medio se sacaba 70 % sin saber inglés. Barajadas: ahora quedan
  26/18/16 y 18/26/16.
- **Dos ítems duplicados entre los dos bancos**, con el mismo `why` palabra por
  palabra (*make a decision* y *say/tell*). Reescritos.
- **Un americanismo:** tenía `layover` para la escala. Aptis es del British
  Council: es **stopover**.
- **Un ítem que enseñaba un patrón discutido:** *recommend someone to do* no es
  estándar. Cambiado por *encourage someone to do*, que sí lo es.
- **Un ítem de gramática metido en el banco de vocabulario** (concordancia de
  sujeto y verbo, que no prueba nada léxico).
- Cuatro niveles mal puestos, incluidos dos que eran C1 marcados como B2.

## Lo que falta del Modo Aptis

Esto es 1 de los 5 componentes. Faltan los bancos de Reading, Listening,
Writing y Speaking, el **diagnóstico corto** que le diga en qué nivel está hoy,
y el simulacro completo cronometrado. Los voy escribiendo por orden de
rentabilidad: Core → Writing → Reading → Listening → Speaking.

Y sigue pendiente lo que él tiene que averiguar: **qué nivel le exigen**.
