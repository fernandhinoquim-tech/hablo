> **Note (coordinator correction, applies to everything below):**
> 1. The app already treats digits 0-100 the same as the words ("8" = "eight"), so accept items that only swap words for digits are not needed. **Drop the digit accepts in a1u3l2e9** and keep only its tip fix.
> 2. Build exercises do **not** read `accept` today (`BuildSentence` has no accept field). Every "add to accept" on a build row below is replaced by the fix in this list:
>    - a1u2l1e4: extra → ["costs", "many"]
>    - a1u2l3e4: extra → ["repeated", "for"]
>    - a1u3l1e4: extra → ["on", "an"]
>    - a1u1l3e3: changing a decoy can't stop the words being swapped round. → es "Hablo español y un poco de inglés.", answer "I speak Spanish and a little English", extra ["talk", "the"]
>    - a1u3l2e4: changing a decoy can't stop the words being swapped round. → es "Vivo con mi familia.", answer "I live with my family", extra ["at", "the"]
>    - a1u4l3e5: changing a decoy can't stop "Usually" moving to the front. Convert it to a write exercise: answer "He usually works at home.", accept ["Usually he works at home."]. The other option is to add accept support to `BuildSentence`.

# Adversarial review: A1 units 1 to 5 (a1u1 to a1u5)

I went through every exercise in the five dumps, one by one, following REVIEW_BRIEF.md. I found **47 issues: 6 ALTO, 20 MEDIO and 21 BAJO**, plus 4 cross-cutting ones at the end.

- **Worst problem: build exercises that reject correct English.** In 5 of them, the answer tiles plus the decoy tiles can form another correct sentence, and the app would mark it wrong. Four are ALTO (a1u2l1e4, a1u2l3e4, a1u3l1e4, a1u3l2e4); a1u4l3e5 is MEDIO. The clearest case is a1u3l1e4: the rejected sentence is exactly the answer of a1u6l2e1.
- **Unverified:** I had no app source code, only the brief and the dumps. `accept` exists on two build exercises in `curriculum.json` (a2u7l3e6 and a2u10l3e6), but I could not check that `Armar.kt` reads it. If it doesn't, the build fixes below have to change the decoy instead.

## Unit a1u1

| id | severity | problem | exact fix |
|---|---|---|---|
| a1u1l1 (theory) | MEDIO | «En inglés el sujeto **nunca** se calla» is overstated, and the unit itself contradicts it: *Nice to meet you*, *Have a nice day*, *See you tomorrow*, *Good morning* and imperatives have no subject. | title → «En inglés el sujeto casi nunca se calla»; add to body: «Salvo en órdenes (*Sit down*) y fórmulas fijas (*Nice to meet you*, *See you tomorrow*).» |
| a1u1l1e6 | MEDIO | The lesson's TRAMPA (dropping the subject, *Am from Colombia*) is never tested in any exercise. | change option "I come of Colombia." → "Am from Colombia." |
| a1u1l1e9 | BAJO | The tip says «Origen con to be», but `accept` includes "I come from Colombia." PLAUSIBLE missing accept: "I'm Colombian." | tip → «Origen: I'm from... (también I come from...). Nunca 'I am of Colombia'.»; optionally add to accept: ["I'm Colombian."] |
| a1u1l1e2 | BAJO | «Siempre es What's your name?» is overstated. | tip → «...No se traduce literal: lo normal es What's your name?» |
| a1u1l1e12 | MEDIO | The phonetics are wrong on two counts. «como 'siiip'» uses **s**, which is a different Spanish-speaker error. «En español solo tenemos la corta» is also backwards: the Spanish /i/ is closer to the vowel of *sheep*, and the one Spanish lacks is the lax /ɪ/ of *ship*. | tip → «sheep es larga y tensa, como «shiiip»; ship es corta y relajada, casi entre i y e. La i española se parece más a la de sheep: la nueva es la de ship.» |
| a1u1l2 (TRAMPA) | BAJO | *I have cold* does not mean «resfriado»; *I have **a** cold* does. | → «...y *I have cold* suena a *I have a cold* («tengo un resfriado»).» |
| a1u1l2e2 | MEDIO | "I'm find, thanks" and "I'm fine, thanks" sound almost the same: the /d/ before /θ/ is unreleased, especially with Piper. | option "I'm find, thanks. And you?" → "I'm five, thanks. And you?" |
| a1u1l2e3 | MEDIO | «"zank iu"»: a Colombian reads the z as /s/, which gives *sank*. That is exactly the error this `th` exercise is meant to catch. | tip → «Thank you son dos palabras: la th es la z de España (lengua entre los dientes). Ni «tenkiu» ni «sankiu».» |
| a1u1l2e6 | BAJO | «antes de un sustantivo contable» leaves out *singular*. | tip → «...hace falta antes de un sustantivo contable en singular.» |
| a1u1l2e7 | MEDIO | `sound: h`, but the tip transcribes it as «hava». A Spanish reader makes that h silent («ava»), which is the error being tested. | tip → «Have a se pega: «HAV-a». La h se sopla suave, como empañando un vidrio; ni muda («ava») ni tan fuerte como la j.» |
| a1u1l2e9 | MEDIO | The cloze in e8 accepts *feel*, but this exercise doesn't. | add to accept: ["I feel tired, thank you.", "I feel tired, thanks."] |
| a1u1l3e3 | MEDIO | The same tiles form "I speak English and Spanish", which is a correct translation and would be rejected. | add to accept: ["I speak English and Spanish"] |
| a1u1l3e6 | MEDIO (PLAUSIBLE) | "You speak English?" is a common spoken question with rising intonation, so it is not clearly wrong. Separately, the tip «Las preguntas en presente simple necesitan do» contradicts e1 of the same lesson (*Are you from Spain?*), and do-support isn't taught until a1u4l2. | option "You speak English?" → "Are you speak English?"; tip → «Con verbos normales la pregunta lleva do delante (con am/is/are no: Are you from Spain?).» |
| a1u1l3e9 | BAJO | The "from" variant is missing when the second I is dropped. | add to accept: ["I'm from Colombia and speak Spanish."] |

- **a1u1l1 (lesson line):** grammar/functions actually covered: greetings, "My name is", origin with *from*. Notable gaps inside this lesson's own topic: the am/is/are conjugation in the theory is practised only by the *is* in e10; nothing uses he/she/we/they; the dropped subject is never tested; there is no *Hello/Hi* even though the lesson is «Hola y encantado».
- **a1u1l2 (lesson line):** grammar/functions actually covered: How are you / fine, thanks, and be + adjective (hungry, tired). Notable gaps inside this lesson's own topic: *I am cold*, *I am thirty* and the *I have hunger/cold* trap never show up as a distractor.
- **a1u1l3 (lesson line):** grammar/functions actually covered: Where are you from?, country vs nationality (only e9). Notable gaps inside this lesson's own topic: the TRAMPA (capital letters) cannot be tested because the app ignores case, so only the tips can teach it; there is no nationality item other than Colombian.

## Unit a1u2

| id | severity | problem | exact fix |
|---|---|---|---|
| a1u2l1e4 | **ALTO** | The tiles How, much, does, it, cost form "How much does it cost", which is correct and is the most literal translation of «¿Cuánto cuesta?». The app rejects it. | add to accept: ["How much does it cost"] (or extra → ["many", "costs"]) |
| a1u2l1e9 | MEDIO | The most natural option is missing. | add to accept: ["I'd like a cup of tea, please.", "I would like a cup of tea, please."] |
| a1u2l1e6 | BAJO | The UK phrase is *to take away* (*takeaway* is the noun). | tip → «En Reino Unido se dice to take away.» |
| a1u2l1e5 | BAJO | The course uses American English, and *Here you go* is the usual American form. | audio → "Here you go."; tip → «...También se oye Here you are.» |
| a1u2l2e2 | **ALTO** (PLAUSIBLE) | "No, thanks. That's everything." is also acceptable English for «Eso es todo». | option → "No, thanks. Is all." (a subject-drop error, linked to a1u1l1) |
| a1u2l2e7 | BAJO | The course is American, yet "bill" is the primary answer. The tip is also overstated: Americans say *bill* too. | answer → "check", accept → ["bill"]; tip → «check es lo más americano; bill también se oye (y es lo británico). account NO: es la del banco.» |
| a1u2l2e8 | MEDIO | «Puedo» also translates as May. | add to accept: ["May I pay by card?", "May I pay with a card?"] |
| a1u2l3e1 | MEDIO | The theory says *sorry* is only for apologising and *Excuse me* is for getting attention or interrupting, but the answer uses *Sorry* to make a complaint to a waiter. | tip → «Aquí sorry suaviza la queja; Excuse me, this isn't what I ordered también vale.» (or change all three options to start with "Excuse me,") |
| a1u2l3e2 | MEDIO | "fix that" and "fixed that" sound nearly the same: the /t/ disappears before /ð/. | option → "I'm so sorry, let me to fix that." |
| a1u2l3e4 | **ALTO** | The tiles form "Could you repeat that please", which is correct and is the most literal translation of «repetirlo». | add to accept: ["Could you repeat that please"] (or change extra "repeat" → "repeated") |
| a1u2l3e8 | MEDIO | Natural translations are missing. | add to accept: ["Sorry for being late.", "I'm sorry for being late.", "Sorry, I'm running late."] |
| a1u2l3 (theory) | BAJO (PLAUSIBLE) | In American English *Excuse me* is also used after bumping into someone, so «excuse me para molestar, sorry para disculparte» is overstated. | add: «(En EE. UU. también se dice Excuse me al tropezar con alguien.)» |

- **a1u2l1 (lesson line):** grammar/functions actually covered: Can I have / I'd like / please, to go. Notable gaps inside this lesson's own topic: no exercise makes the learner choose between *I want* and *Can I have* (the TRAMPA); *Can I get* appears only as an accepted answer.
- **a1u2l2 (lesson line):** grammar/functions actually covered: check/bill, anything else, pay by card. Notable gaps inside this lesson's own topic: *Do you take cash?*, *Keep the change* and *Can we split it?* are only in the theory.
- **a1u2l3 (lesson line):** grammar/functions actually covered: Excuse me vs sorry, asking for repetition. Notable gaps inside this lesson's own topic: the only exercise that asks the learner to choose *sorry* or *excuse me* is e7, and e1 contradicts the rule.

## Unit a1u3

| id | severity | problem | exact fix |
|---|---|---|---|
| a1u3l1e4 | **ALTO** | The decoy "in" forms "I work in a bank", which is correct and is exactly the answer of a1u6l2e1. The app rejects it. | add to accept: ["I work in a bank"] (or extra "in" → "on") |
| a1u3l1e8 | MEDIO | «years old, siempre las dos» contradicts the theory, which says «o simplemente I'm 25». | tip → «Si dices years, va con old: 25 years old (o solo I'm 25). Nunca 25 years a secas, y el verbo es am, no have.» |
| a1u3l1e7 | MEDIO (PLAUSIBLE) | "What do you do" and "What you do" sound almost the same in natural speech («whaddya do»). This exercise also uses do-support before it is taught. | option "What you do for a living?" → "What are you do for a living?" |
| a1u3l2 (whole lesson) | MEDIO | The theory is about the possessive 's, but only 1 of the 10 exercises (e8) practises it. | replace e2 (see next row) |
| a1u3l2e2 | MEDIO | It is an exact duplicate of a1u4l1e2 and tests the third-person -s one unit before it is taught. | → es "La casa de mi hermano es grande.", options ["My brother's house is big.", "My house's brother is big.", "My brother house is big."], answer "My brother's house is big.", tip «El dueño va primero con 's: my brother's house.» |
| a1u3l2e4 | **ALTO** | The same tiles form "I live with my family in Bogota", which is correct and would be rejected. | add to accept: ["I live with my family in Bogota"] |
| a1u3l2e9 | MEDIO | The digit version is missing. The tip also calls *siblings* formal, when it is everyday American English («Do you have any siblings?»). | add to accept: ["I have 2 sisters and 1 brother.", "I have 2 sisters and a brother."]; tip → «...(existe siblings, muy usada en preguntas: Do you have any siblings?; al contar se dice sisters y brothers).» |
| a1u3l2e6 | BAJO | `sound: th` rests only on "with", which is word-final and often weak. | text → "I live in Bogota with my three brothers." |
| a1u3l2e1 | BAJO | «hermanos» is ambiguous (it can mean a mixed group), and e9 of the same lesson says exactly that. | meaning → "Tengo dos hermanos (hombres)." |

- **a1u3l1 (lesson line):** grammar/functions actually covered: age with *be*, a/an with professions, How old are you. Notable gaps inside this lesson's own topic: the short answer *I'm 25* is never produced; the TRAMPA's list (miedo, sueño, frío) is never practised.
- **a1u3l2 (lesson line):** grammar/functions actually covered: routine and family phrases, one 's item. Notable gaps inside this lesson's own topic: 's vs *of*, "the house of my brother" as a distractor, *Maria's*.

## Unit a1u4

| id | severity | problem | exact fix |
|---|---|---|---|
| a1u4l1 (theory) | BAJO | «La única que cambia es he, she, it» is true only in the present and forgets *to be* (am/is/are). The TRAMPA uses "on Monday", which means one specific Monday, while the exercises use "Mondays". | add «(en presente; to be es aparte)»; TRAMPA → «**He works on Mondays**» |
| a1u4l1 (gap) | MEDIO | The most common irregular form, *have → has* (the «he have» error), is not taught. Neither is -es after sh/ch (watches). | add to theory: «*I have* → *He **has*** · *I watch* → *She watch**es***»; add an exercise, e.g. cloze "She ___ two cars." answer "has" |
| a1u4l1e4 | BAJO | The options write "Bogotá" with the accent; every other English text writes "Bogota". | "Bogotá" → "Bogota" in all 3 options and in answer |
| a1u4l1e12 | BAJO | Same problem as the ship/sheep tip: a Spanish reader pronounces «liv» with a tense /i/. | tip → «leave es larga y tensa («liiiv»); live es corta y relajada, casi «lev». Cambian el significado por completo.» |
| a1u4l1e10 | BAJO (PLAUSIBLE) | The capital-letter tip is pending according to CLAUDE.md (it clashes with «no te preocupes por mayúsculas»). | tip → «...Y English va con mayúscula (la app no te lo marca, pero en un correo sí se nota).» |
| a1u4l2e3 | MEDIO (PLAUSIBLE) | "You speak English?" is acceptable in speech, and this is the third exercise with the same Spanish sentence (after a1u1l3e6 and a1u4l2e10). | option → "Are you speak English?" |
| a1u4l3 (theory) | MEDIO | «tienen un sitio fijo» is overstated: *Sometimes / Usually* at the start of a sentence is correct, and the learner will hear it often. | add: «sometimes y usually también pueden ir al principio: *Sometimes I walk to work.* always y never, no.» |
| a1u4l3e5 | MEDIO | The tiles form "Usually he works at home", which is correct. | add to accept: ["Usually he works at home"] |
| a1u4l3e10 | BAJO | A natural translation is missing. | add to accept: ["She doesn't ever work on Sundays."] |

- **a1u4l1 (lesson line):** grammar/functions actually covered: third-person -s, -ies. Notable gaps inside this lesson's own topic: *has*, *goes/watches* (-es).
- **a1u4l2 (lesson line):** grammar/functions actually covered: don't/doesn't, Do/Does questions, bare verb after does. Notable gaps inside this lesson's own topic: short answers (*Yes, I do / No, she doesn't*); the learner never writes a *Does* question.
- **a1u4l3 (lesson line):** grammar/functions actually covered: always/never/sometimes/usually, before the verb and after be. Notable gaps inside this lesson's own topic: *often* is never used; *How often...?* is missing; there is only one exercise with be + adverb for the learner to produce.

## Unit a1u5

| id | severity | problem | exact fix |
|---|---|---|---|
| a1u5l1e1 | MEDIO (PLAUSIBLE) | When describing an apartment, "It has two bedrooms." is exactly what an English speaker says, so this distractor is arguably a correct answer. | option → "There have two bedrooms." |
| a1u5l1 (TRAMPA) | MEDIO | *It has a table* does not mean «ella tiene una mesa» (that would be *she has*), and it is correct English when talking about a room or a house. | → «...«hay una mesa» no es *There has a table* ni *Have a table*: es **There is a table**. (*It has…* solo sirve para describir algo ya mencionado: *The kitchen has a table*.)» |
| a1u5l1e6 | BAJO | «nunca peoples» is false: *peoples* exists and means «pueblos».  | tip → «people ya es plural (personas); peoples solo significa «pueblos, naciones».» |
| a1u5l1e10 | MEDIO | Common natural forms are missing. | add to accept: ["There is a store close by.", "There is a store around here.", "There is a shop close by.", "There is a shop around here."] |
| a1u5l2 (theory) | MEDIO | «Nada delante de cosas en general, en plural o incontables» reads as if plurals never take an article. | → «Nada delante de plurales o incontables **cuando hablas en general**: …» |
| a1u5l2e1 / a1u5l2e9 | BAJO | «Las profesiones **siempre** con a o an» is overstated (*They are doctors*, *She's the doctor here*). «el error número uno» contradicts a1u3l1e1, which gives that title to *I have 25 years*. e1 also duplicates a1u3l1e5. | both tips → «Una profesión en singular lleva a o an: is a doctor. Es de los errores más típicos.» |
| a1u5l2e2 | BAJO (PLAUSIBLE) | «Me gusta el café» taken out of context also allows the "the coffee" reading. | es → "Me gusta el café (en general)." |
| a1u5l2e10 | **ALTO** | `accept` is empty for a long sentence. | add to accept: ["I like coffee but don't like tea.", "I like coffee but not tea."] |
| a1u5l2 (gap) | MEDIO | The second half of the TRAMPA (*The life is hard*) and plural generics are never tested. | e4 → translate es "La vida es dura.", options ["The life is hard.", "Life is hard.", "A life is hard."], answer "Life is hard." |
| a1u5l3e4 | BAJO | «next siempre lleva su to» is false: the learner will meet *next week*, *next door*. | tip → «Para «al lado de», next lleva to: next to. (next week es otra cosa: la próxima semana.)» |

- **a1u5l1 (lesson line):** grammar/functions actually covered: there is/are, Is there…?, aren't any. Notable gaps inside this lesson's own topic: *Are there any…?* and *There isn't a…* are never produced.
- **a1u5l2 (lesson line):** grammar/functions actually covered: a/an with professions, zero article with coffee. Notable gaps inside this lesson's own topic: *the* for something already mentioned is never produced; *an hour* (silent h) is missing; the *Life is hard* trap is untested.
- **a1u5l3 (lesson line):** grammar/functions actually covered: in/on/at, under, next to, behind. Notable gaps inside this lesson's own topic: *between* and *in front of* are never practised; *at* for places appears only in e2.

## Cross-cutting

1. **ALTO (PLAUSIBLE): translate exercises have no `accept`, but the review deck (`Mazo`) uses them in its "escribir" step.** No translate item in the whole course has an `accept` field. Unless another exercise has the same Spanish, these natural answers would be marked wrong in the deck:
   - "I'm 25" or "I am twenty-five years old" (a1u3l1e1)
   - "I'm home" (a1u5l3e2)
   - "That's it" (a1u2l2e2)
   - "I always have coffee in the morning" (a1u4l3e1)
   - "Is there a restroom here?" (a1u5l1e3)
   - "…what I asked for" (a1u2l3e1)

   **Fix:** allow `accept` on translate exercises and fill it in for these.
2. **MEDIO: duplicated exercises.** a1u3l2e2 is identical to a1u4l1e2. a1u3l1e5 is nearly identical to a1u5l2e1. «¿Hablas inglés?» appears three times (a1u1l3e6, a1u4l2e3, a1u4l2e10).
3. **BAJO: do-support used before it is taught.** It appears in a1u1l3e6/e7, a1u2l3e6 and a1u3l1e7, while the theory comes in a1u4l2. Acceptable as fixed phrases, but no tip says so.
4. **Build fixes depend on the app reading `accept`.** See the note at the top.
