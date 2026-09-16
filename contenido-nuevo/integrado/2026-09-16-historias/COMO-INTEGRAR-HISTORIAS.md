# Historias cortas (etapa 4)

`historias.json` — **2 tandas, 12 historias** (6 A2 de ~67 palabras, 6 B1 de
~83). `validar_historias.py` las comprueba.

## Por qué en tandas y no una por lección

El meta-análisis de intervención narrativa oral (29 estudios) da **d = 1,36**
global — lectura 1,87, gramática 1,71, habla 1,38, vocabulario 1,31 —, pero el
moderador significativo es la **duración**: tandas de **3 a 12 sesiones** dan
d = 2,53; sueltas se quedan en 0,94 y las larguísimas bajan a 1,34.

Por eso son tandas de 6 y no una historia por lección para siempre.

## El retell NO es opcional

Flujo: la profesora la lee y está en pantalla → 3 preguntas → **él la cuenta de
vuelta en voz alta** con las pistas → las palabras del glosario pasan al mazo.

Si se quita el retell, esto deja de ser intervención narrativa oral y pasa a
ser comprensión lectora: **se pierde la mitad del efecto**. Es el paso que
parece prescindible y es el que hace el trabajo.

El retell se puntúa como `shadow` —cobertura de palabras y soltura—, **no por
fonemas**.

## Verificado: 13 defectos corregidos

Dos van más allá de estas historias y conviene anotarlos en `CLAUDE.md`:

**1. Inglés americano en todo el curso.** El corpus se me fue a británico sin
querer (*neighbour, flat, pyjamas, corridor, apologised, recognised*) mientras
A1 y A2 ya estaban en americano (*store, movie, canceled*). Unificado en
**americano**, que es a lo que está expuesto un colombiano.

La excepción es el **Modo Aptis**: Aptis es del British Council, así que ahí
sí hay que exponerlo al británico — por eso *stopover* y no *layover* en el
banco del Core. La regla queda así: **el curso en americano; el Modo Aptis
enseña la diferencia como contenido**, porque en el examen va a oír acentos y
vocabulario británicos aunque él produzca americano.

**2. Tres historias marcadas A2 usaban pasado perfecto**, que es B1
(*"They had lived on the same street"*, *"He had remembered her order"*).
Reescritas en pasado simple. Hay un barrido en el validador que ahora busca
pasado perfecto, pasiva, condicional y *supposed to* en cualquier historia A2.

Otros que importan como precedente:

- **Dos preguntas que el texto no sostenía.** Una preguntaba *por qué* el museo
  la contrató y el texto nunca lo dice; otra daba por buena "la recuerdan
  mejor" cuando el texto solo dice que "hablan más de ella". Leer bien y que
  te marquen mal es el peor fallo posible.
- **`10:03` y `1998` los lee mal el TTS** ("ten colon zero three"). Escritos en
  palabras, como el resto del corpus.
- **Un `trap` enseñaba una regla falsa:** que *afterwards* va siempre al final.
  No es cierto.
- **Glosario con cognados gratis** (*decision*, *candidate*, *mistake*): para un
  hispanohablante no hay nada que aprender ahí. Cambiados por colocaciones que
  sí cuestan: *raise your hand*, *in cash*, *not arrive at all*, *most of them*.
- **`gate` estaba glosado como "reja"**, que es otra cosa. Es *portón*. Iba a
  entrar al mazo con esa traducción mal puesta para siempre.

## Lo que sigue

Cuando las use, hay que ver si 6 por tanda le cansan o le saben a poco, y
ajustar la siguiente. Las tandas de B2 se escriben cuando exista el nivel B2
del curso.
