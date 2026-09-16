# Banco de vocabulario para el contrarreloj (etapa 3)

`banco-vocabulario.json` — **368 pares** español/inglés en **14 temas**, cada
uno con nivel (A1 165 · A2 127 · B1 76). `validar_vocab.py` lo comprueba.

## La regla que no se puede romper

**Una ronda se arma con pares de UN SOLO tema.**

Dentro de cada tema garanticé que no hay dos ingleses casi sinónimos ni dos
españoles equivalentes. Si se mezclan temas en una ronda, esa garantía
desaparece y pueden salir dos respuestas válidas a la vez — que en un juego de
parejas es el peor fallo posible: acierta y le dices que no.

El validador ya atrapó tres casos de eso al armarlo:

- **`answer`** estaba dos veces: como verbo *responder* y como sustantivo
  *respuesta*. En una ronda con las dos, ambas fichas servirían. El verbo pasó
  a `reply`.
- **`seguro`** traducía dos cosas distintas: *insurance* en viajes y *safe* en
  adjetivos. Ahora es *seguro de viaje*.
- Una lista de casi sinónimos vigilados (store/shop, movie/film, big/large,
  speak/talk, job/work…) que no pueden compartir tema.

Si algún día se añaden pares, **correr el validador antes de integrar**.

## Cómo debería jugarse

- **Ronda de 8 a 10 pares** de un tema, filtrados por nivel.
- **Lo fallado vuelve** a salir hasta que se acierte. Esa es la parte que
  enseña: los ítems que siguen en el ciclo de prueba se recuerdan al 80 % una
  semana después contra el 35 % de los que se sacan del ciclo.
- **El reloj mide, no reprueba.** Guarda pares por minuto y muestra la marca de
  hoy contra la de hace dos semanas. Nunca un "fallaste por lento".
  Lo que mejoró en el estudio de hispanohablantes A1 no fue el vocabulario que
  sabían (no significativo) sino la **velocidad al hablar**: de 1,23 a 2,04
  palabras por segundo. El reloj entrena eso.
- **Se puede decir en voz alta:** todas las palabras inglesas están en
  `cmudict.dict`, así que Piper puede pronunciarlas y el juego puede tener
  modo de oído.

## Qué falta

Los temas cubren lo cotidiano y lo de trabajo. Cuando lleguen B1 y B2 del
curso habrá que subir el banco a unos 800 pares y añadir temas abstractos
(economía, medio ambiente, opinión), que es lo que pide el Core de Aptis.
Aviso para entonces: los abstractos son justo donde más fácil se cuelan dos
traducciones válidas, así que ese lote necesita el validador sí o sí.
