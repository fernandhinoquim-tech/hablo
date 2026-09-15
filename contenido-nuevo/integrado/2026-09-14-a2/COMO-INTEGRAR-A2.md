# Nivel A2 — 29 lecciones, 265 ejercicios

`a2-nivel-completo.json` trae un solo objeto `level`. Se **anexa a `levels[]`**
de `curriculum.json`, después del nivel A1. Formato v2, con los nueve tipos
desde el primer ejercicio.

Corre `validar2.py` sobre el `curriculum.json` final antes de comitear.

## Qué cubre

| unidad | tema | lecciones |
|---|---|---|
| a2u1 | Pasado continuo, used to, contar una historia | 3 |
| a2u2 | Present perfect, ever/never, yet/already/just, **perfect vs pasado simple** | 4 |
| a2u3 | have to, must/mustn't, should | 3 |
| a2u4 | Tres futuros, might, **primer condicional** | 3 |
| a2u5 | Segundo condicional, would para cortesía | 2 |
| a2u6 | too/enough, something/anybody/nothing, most | 3 |
| a2u7 | Adverbios, superlativos y as...as, oraciones de relativo | 3 |
| a2u8 | Pasiva, phrasal verbs, preposiciones de movimiento | 3 |
| a2u9 | Estilo indirecto (say/tell), so am I / neither do I | 2 |
| a2u10 | -ing o to, do o make, propósito con to/for | 3 |

Orden de gramática tomado de un sílabo A2 real (test-english.com/grammar-points/a2),
no inventado.

**Producción real: 173 de 265 = 65 %** (en A1 quedó en 53 %). Sube porque el
nivel lo permite: 57 `write`, 29 `cloze`, 29 `type`, 29 `speak`, 29 `shadow`.

## Verificado antes de mandarlo

Dos agentes revisaron los 265 en paralelo y encontraron **33 defectos reales**,
todos corregidos. El más grave, y vale la pena que lo mires porque es del tipo
que ningún validador atrapa:

- **La regla de separabilidad de los phrasal verbs estaba mal enunciada.** Yo
  había escrito que si el objeto es un pronombre "va obligatoriamente en el
  medio". Eso solo vale para los separables (turn off, put on, give up). Para
  `look for`, `look after` y `get on` —que la misma lección enseña— es falso:
  **look after her** ✅, *look her after* ❌. Tal como estaba, la teoría le
  mandaba decir "look her after". Reescrita separando los dos grupos.

Otros que importan como precedente:

- **Dos contradicciones internas.** La teoría de `to`/`for` decía que *for*
  solo va delante de sustantivos, y tres ejercicios después pedía
  *This knife is **for cutting** bread*. Y la trampa de `because`/`so` abría
  afirmando algo que su propio ejemplo desmentía. Las dos reescritas.
- **Cuatro afirmaciones fonéticas falsas mías:** que en *who* no suena la h
  (sí suena; lo que calla es la w), que la doble o de *looking* es larga (es
  corta), que *since* y *sense* se distinguen por la s final (es por la vocal),
  y «zruu» para *through* —que un lector colombiano lee "sruu"—.
- **Siete `build` que armaban otra frase igual de válida** con las mismas
  fichas. Por ejemplo *The car that I bought is red* permitía armar
  *The car I bought is red*, que la propia teoría declara correcta.
- **Un distractor que era inglés correcto:** *I haven't yet finished* es
  gramatical (formal), y estaba puesto como opción mala.
- **Español peninsular en cuatro sitios** («estupendo», «no hace falta»,
  «lo bastante», «entró en la habitación»). Cambiado a colombiano neutro.

## Siguiente

Con A1 (300) y A2 (265) van **565 ejercicios y 56 lecciones**. B1 es el
siguiente salto y es más grande: present perfect continuous, pasado perfecto,
condicionales 3, reported questions, modales de deducción, voz pasiva
completa, phrasal verbs en serio y mucho más vocabulario.

Antes de escribirlo me sirve saber cómo se siente A1 en el teléfono: si las
fichas de teoría son muy largas, si el ritmo de 10-11 ejercicios por lección
cansa, y si los `write` marcan mal alguna respuesta buena. Eso último es lo
único que no puedo verificar desde aquí.
