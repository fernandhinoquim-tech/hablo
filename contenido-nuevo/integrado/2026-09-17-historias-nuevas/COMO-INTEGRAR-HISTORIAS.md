# Historias nuevas (Cowork, 17-09-2026)

Paso 7 del plan de la auditoría: **A1 no tenía ninguna historia.**

- **tanda-3 · A1** (h-a1-01 a h-a1-06):
  - Las cuatro primeras van en presente: An Early Saturday · A Busy Monday · Salsa Upstairs · Spanish in Chicago.
  - Las dos últimas van en un pasado muy simple (was/were, went, had, saw): Left, Then Right · Three Cakes.
  - Frases de 5 a 10 palabras. Temas del A1 nuevo: la hora, el clima, precios, direcciones, familia.
- **tanda-4 · A2** (h-a2-07 a h-a2-12): The Song on Hold · The Wrong Glasses · The Man in the Elevator · The Borrowed Drill · Four Strangers and a Car · The Store on the Corner.
  - Gramática del A2 nuevo: past continuous, present perfect con for/since, could/was able to, should, going to, used to.
  - Temas del A2 nuevo: teléfono, médico, entrevista, prestar y pedir prestado, vuelo cancelado.

Mismo formato que las tandas 1 y 2. Validado con las reglas de `checkContent`: 6-10 frases, 3 preguntas de 3 opciones, retell con 3 pistas, glosario que aparece literal en el texto y está en CMUdict.

Un revisor adversarial encontró 14 defectos y todos están corregidos:
- 3 preguntas con dos respuestas defendibles o sin apoyo en el texto;
- una línea de tiempo imposible;
- una trama demasiado parecida a "What the Neighbours Heard".

**Integrar:**
1. `python tools/content/anexar_historias.py contenido-nuevo/historias-nuevas/historias-nuevas.json`
2. `checkContent`, y después a `integrado/`.

La pantalla de historias hoy agrupa por tanda. Conviene que la tanda A1 salga primero, antes de las de A2.
