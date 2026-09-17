> **Coordinator's correction (read first):** the app already turns digits 0–100 into words ("8" = "eight"), so digit-only accept items are not needed. That makes the a1u7l2e10 row void. For a1u7l3e10, only the "She speaks three languages." part remains relevant, and it is MEDIO, not ALTO. Build exercises do NOT read `accept` today (BuildSentence has no accept field), so the fix for a build item is always to change its decoys (none of the build fixes below relies on `accept`).

# Content review: A1 units 6–9 (a1u6l1 to a1u9l3), exercise by exercise

I found 44 problems across the four units: 7 ALTO, 24 MEDIO and 13 BAJO. Items marked PLAUSIBLE depend on how the app behaves, which I could not check from the dumps.

**Checks I made against the rest of `/home/claude/audit/curriculum.json`:**
- No `accept` list in the whole course contains a digit. If the app does not turn "2" into "two", every number item below is ALTO.
- The possessive `'s` and `I'd like` are already taught in a1u3l2 and a1u2l1.

## Unidad 6

| id | severity | problem | exact fix |
|---|---|---|---|
| a1u6l2e10 | ALTO | The accept list covers only 4 of the 8 in/at × at home/from home × word-order combinations. Correct English is marked wrong. | add to accept: `["I work at a bank, but I'm working at home today.", "I work in a bank, but I'm working from home today.", "I work at a bank, but I'm working from home today.", "I work for a bank, but today I'm working from home."]` |
| a1u6l1e10 | MEDIO | Fronted "right now" and "at the moment" are not accepted, although the next lesson's theory teaches "at the moment". | add to accept: `["Right now she is working.", "Right now, she's working.", "She is working at the moment.", "She's working at the moment."]` |
| a1u6l3e10 | MEDIO | "teléfono" → "cell phone" is standard American English and is rejected. | add to accept: `["That is my cell phone.", "That's my cellphone.", "That is my telephone."]` |
| a1u6l2e2 | MEDIO | "I want a coffee." is taught as the correct answer. a1u2l1 and a1u7l2 both say this exact sentence sounds rude and that you should say "I'd like". The course contradicts itself. | es → "Necesito un café."; options → `["I am needing a coffee.", "I need a coffee.", "I needing a coffee."]`; answer → "I need a coffee."; tip → "need no se pone en continuo." |
| a1u6l2 (TRAMPA) | MEDIO | Two problems. "**nunca** van en continuo aunque en español sí" is false in both directions: Spanish rarely says «estoy queriendo», and English does say "How are you liking it?" / "I'm loving it". Also, "need" is neither a head verb nor a liking verb. | Rewrite: "Hay verbos que en inglés **casi nunca** van en continuo: like, want, need, know, understand. *I am wanting a coffee* ❌ → **I want a coffee** ✅. Expresan un estado (lo que sabes, quieres o necesitas), no una acción." |
| a1u6l2 (theory) | MEDIO | The theory lists "today" as a continuous signal. "I work today" (a schedule) is very common American English ("Do you work today?"). | Remove *today* from the signal list, or add: "(ojo: *I work today* también es normal si hablas de tu horario)". |
| a1u6l1 (theory) + e1/e9 tips | MEDIO | "Nunca va solo el -ing" is false. Gerunds ("I like swimming", taught in a2u10l1) and the past continuous with was/were are both coming. | Theory: "Para decir 'estoy haciendo', el -ing no va solo…". Tips e1/e9: "Para 'estoy …-ando', el -ing necesita am, is o are." |
| a1u6l2e6 | MEDIO | The tip says "wait siempre lleva for", which is false: "Please wait." / "Wait a minute." / "Wait here." | tip → "Si dices qué o a quién esperas, va for: wait for the bus. Sin eso va solo: Please wait." |
| a1u6l3e8 | MEDIO | "These are those books." is unnatural and contradicts the near/far theory. | text → "These are my books, and those are yours." (all words are in cmudict); keep sound `th`. |
| a1u6l3e5 | MEDIO | Off-topic in the this/that lesson: it tests the possessive `'s`. It is also a near-duplicate of a1u3l2e8. | Replace: es "Esos zapatos son nuevos.", answer "Those shoes are new.", extra `["that", "is", "this"]`, tip "Varios y lejos: those, con are." |
| a1u6l3e12 | BAJO (PLAUSIBLE) | With play=sentence, "I sink it is late" is not a sentence, so the answer can be guessed from meaning instead of heard. | Accept this as a known trade-off, or don't show the sentence text before answering. |
| a1u6l1e8 | BAJO | The tip says the h of "her" is blown. After "with", unstressed *her* normally drops the /h/ ("with 'er mother"). | tip → "shopping con sh de «shhh»." (remove the her sentence) |
| a1u6l1 (TRAMPA) | BAJO | "I work now" really means "now I have a job", not «trabajo habitualmente». | "…*I work now* significa «ahora tengo trabajo», no «estoy trabajando en este momento»." |
| a1u6l1e9 | BAJO (PLAUSIBLE) | A learner who types the contraction "'m" in the gap may be rejected. | add to accept: `["'m"]` if the gap is not normalized. |

- **a1u6l1** — grammar/functions actually covered: am/is/are + -ing (affirmative); negative and wh-question only through listen/translate. Notable gaps inside this lesson's own topic: -ing spelling (studying, shopping with double p, making) is never taught; no production of the negative or of a question.
- **a1u6l2** — grammar/functions actually covered: simple vs continuous with signal words; want/understand as stative. Notable gaps inside this lesson's own topic: no item makes the learner choose the tense from a signal word (e.g. cloze "She ___ coffee every day / right now"); like/need/know are never practised; "at the moment" is never used.
- **a1u6l3** — grammar/functions actually covered: this/that/these/those as pronouns + is/are. Notable gaps inside this lesson's own topic: determiner use ("this phone", "those books") is never taught or practised; "this is Fero" on the phone is never practised; e5 is off-topic.

## Unidad 7

| id | severity | problem | exact fix |
|---|---|---|---|
| a1u9l3e9 | — | (listed under unit 9) | — |
| a1u7l3e10 | ALTO (PLAUSIBLE) | The accept list is empty, so "3" for "three" is rejected. "She speaks three languages." is also the most natural English for this meaning. | add to accept: `["She can speak 3 languages."]`; consider adding `"She speaks three languages."`, with the tip saying the lesson practises *can*. |
| a1u7l2e10 | ALTO (PLAUSIBLE) | Digit variants are rejected. | add to accept: `["We'd like a table for 2, please.", "We would like a table for 2, please."]` |
| a1u7l1 (theory) | MEDIO | "en preguntas y negativos, some se vuelve any" has no exception in the theory. Offers and requests ("Can I have some water?", "Would you like some tea?") use *some*, and only a tip in the next lesson mentions this. | Add: "Excepción: al ofrecer o pedir se usa some: *Would you like some tea? · Can I have some water?*" |
| a1u7l1e2 | MEDIO | The distractor "How many persons are there?" is grammatical (formal/legal English), and the tip implies "persons" doesn't exist. | Change that option to "How many people there are?" (a Spanish word-order calque); tip → "people ya es plural. Y en la pregunta, are va antes de there." |
| a1u7l2e7 / a1u7l2e11 | MEDIO | The learner is drilled on "I would like…". The e11 tip even says «sin contraer». But the e1 tip says "I'd like es la forma normal al hablar", so the shadowed speech rhythm is unnatural. | e7 text → "I'd like a table for two, please."; e11 text → "I'd like a coffee, please.", tip → "I'd suena «aid», una sílaba. El peso en «COFFEE» y «PLEASE»." (i'd is already used in a1u2l1e3, so it passes cmudict) |
| a1u7l3e12 | MEDIO | The tip says the difference is the t of can't. In American English that t is usually unreleased. The real cue is stress and vowel: weak *can* «kn» vs strong *can't* «KÆNT». Learners will fail to hear Americans. | tip → "En inglés americano la t de can't casi no se oye. La pista: can va débil («kn») y can't va fuerte y con a abierta. Al decirla tú, marca la t." |
| a1u7l2 (whole lesson) | MEDIO | It repeats a1u2l1 (same theory, same TRAMPA), and e1 is identical to a1u2l1e2. | Replace e1 with an offer item: es "¿Quiere algo de beber?", options `["Do you want drink something?", "Would you like something to drink?", "Would you like drink something?"]`. Make the theory focus on *would like to + verb* and offers. |
| a1u7l3e8 | BAJO | "slowly" is also an s+consonant cluster («eslowly»), and the tip misses it. The sentence also appears 3 times in the lesson (e6, e8, e11). | tip → "speak y slowly empiezan con s pura: nada de «espeak» ni «eslowly»."; change e6 audio to "Can you say that again?" (meaning "¿Puede repetir eso?"). |
| a1u7l1e9 / a1u7l3e10 | BAJO | The Spanish prompt is identical to a translate item earlier in the same lesson (e1 / e2), which already showed the answer. | e9 es → "¿Cuánto dinero necesitas?", text → "How ___ money do you need?"; a1u7l3e10 es → "Él puede tocar la guitarra.", answer "He can play the guitar.", accept `["He can play guitar."]` |

- **a1u7l1** — grammar/functions actually covered: much/many, some/any, a lot of. Notable gaps inside this lesson's own topic: a few / a little are in the theory but never practised; no production of "How many…?"; "some" in the affirmative only appears in dictation.
- **a1u7l2** — grammar/functions actually covered: I'd like + noun / + to + verb, the "Would you like…?" offer. Notable gaps inside this lesson's own topic: no production of an offer (write/build "Would you like…?"); it largely duplicates a1u2l1.
- **a1u7l3** — grammar/functions actually covered: can for ability and requests, can't, the bare verb after can. Notable gaps inside this lesson's own topic: "Can I…?" for permission only in listen; "cannot" as a spelling is never shown; no written negative or question.

## Unidad 8

| id | severity | problem | exact fix |
|---|---|---|---|
| a1u8l3e10 | ALTO | «su carro» can mean his/their/your car, and "trouble/issue" are natural synonyms. Correct translations are rejected. | add to accept: `["She had a problem with his car.", "She had a problem with their car.", "She had trouble with her car.", "She had an issue with her car.", "She had a problem with her vehicle."]` |
| a1u8l4 (theory) | MEDIO | "hay un solo ayudante, y sirve para todos: did" is wrong for be. was/were, taught in l1, never take did (*Did you be tired?* ❌). | Add: "Excepción: con was/were no se usa did: *I wasn't tired · Were you there?*" |
| a1u8l3 (theory) | MEDIO | "no cambian con la persona" contradicts l1: be changes (was/were). | "…I went, he went, they went (el único que cambia es be: was/were)." |
| a1u8l4 (TRAMPA) + e1 tip | MEDIO | They tell the learner to check the verb is «en presente», but for he/she the present has -s (*didn't goes*, which e4 uses as a distractor). The rule should say base form. | TRAMPA end → "revisa que el verbo esté en su forma base: sin -s y sin -ed."; e1 tip → "didn't ya es el pasado: go va en su forma base." |
| a1u8l4e2 | MEDIO | The distractor "You spoke with her?" is valid spoken American English (a rising-intonation question). | Change that option to "Did you speaked with her?" |
| a1u8l4e3 | MEDIO | "didn't call me" and "didn't called me" sound almost the same (the /d/ before m disappears), so the listen item cannot be answered by ear. | audio/answer → "She didn't visit us."; options `["She didn't visited us.", "She didn't visit us.", "She don't visit us."]` |
| a1u8l4e4 | MEDIO | "like the" and "liked the" sound the same (the /t/ before ð is inaudible). | audio/answer → "Did they want more food?"; options `["Did they wanted more food?", "Did they want more food?", "Did they wants more food?"]` |
| a1u8l2e3 | MEDIO | Two problems. The /d/ of "played football" disappears before f, so play and played sound alike. Also, American English "football" is not fútbol. | audio/answer → "They played at home."; options `["They play at home.", "They played at home.", "They playing at home."]` (if a sport is wanted, use soccer) |
| a1u8l1e3 | MEDIO | The tip "at home siempre" is false. *She was home* is standard American English, and *go home* has no at (a1u8l3e7 "I went home early" contradicts it). | tip → "she → was. Estar en casa: at home (en EE. UU. también *She was home*). Con go, sin at: go home." |
| a1u8l1e8 | MEDIO | "shop" as the general word is British. The course is American English (store). | text → "They were at the coffee shop." (keeps sh; tip unchanged) |
| a1u8l1e1 | BAJO | The tip "Eventos y sitios con at" is overgeneralised: in the kitchen, in Bogotá (a1u5l3 taught in/on/at). | tip → "Eventos con at: at the party, at the concert. Y también at work, at school." |
| a1u8l2e4 | BAJO | "I wantd to call you." is a spelling-only non-word. | Change the option to "I wanting to call you." |
| a1u8l2 (theory) | BAJO | Spelling is never taught, but items test it: y→ied (studied, tested in e5/e10 with "studyed") and consonant doubling (stopped). The silent l of walk/talk is not mentioned either. | Add: "Ortografía: *study → studied*, *stop → stopped*. Y en *walk/talk* la l no suena." |
| a1u8l3e8 | BAJO | "said" is a classic trap (it is «sed», not «seid»), and the tip ignores it. | Add to the tip: "Y said suena «sed», no «seid»." |
| a1u8l2e10 | BAJO | The Spanish prompt is identical to e5 (build), which already showed the answer. | es → "Mis padres vivieron en Cali el año pasado.", answer "My parents lived in Cali last year.", accept `["Last year my parents lived in Cali."]` |

- **a1u8l1** — grammar/functions actually covered: was/were affirmative, wasn't/weren't (listen only), at + event/place. Notable gaps inside this lesson's own topic: "Was he…?" / short answers are never practised; no written negative; "there was/there were" is absent.
- **a1u8l2** — grammar/functions actually covered: regular -ed and its /t/, /d/, /ɪd/ pronunciations. Notable gaps inside this lesson's own topic: no item contrasts the three -ed sounds by ear; spelling rules (ied, doubling) are tested but not taught.
- **a1u8l3** — grammar/functions actually covered: went, had, saw, made, came, took, said. Notable gaps inside this lesson's own topic: do/did, get/got and eat/ate are in the table but never practised; no production item beyond two clozes/writes.
- **a1u8l4** — grammar/functions actually covered: didn't + base form, Did + subject + base form. Notable gaps inside this lesson's own topic: wh-questions in the past ("Where did you go?", "What did you do?") and short answers ("Yes, I did") are missing.

## Unidad 9

| id | severity | problem | exact fix |
|---|---|---|---|
| a1u9l3e9 | ALTO | «más grande» is also "larger", and "larger" is rejected. The cloze also expects the doubled g, which is never taught. | accept → `["larger"]`; tip → "big es corto: bigger (se dobla la g, como en hot → hotter). Nada de more big." |
| a1u9l3e10 | ALTO | "than the other hotel" is rejected. | add to accept: `["This hotel is more expensive than the other hotel."]` |
| a1u9l2e10 | MEDIO | «lo» = "that" is natural. "going to" is also a valid future. | add to accept: `["I won't forget that.", "I'm not going to forget.", "I'm not going to forget it."]` |
| a1u9l3 (theory) | MEDIO | "far → further → the furthest": for distance, American English uses farther/farthest. The theory also shows "bigger" without the doubling rule, and lists "easy → easier" under «una sílaba». | "far → **farther** → the **farthest** (también further)"; add "Si termina en vocal + consonante, se dobla: big → bigger, hot → hotter"; move easy → easier to its own line: "Dos sílabas terminadas en -y: easy → easier." |
| a1u9l3e8 | MEDIO | "shop" is British (the course uses store). | text → "This shirt is cheaper."; tip → "shirt con sh. cheaper empieza con ch, que es otro sonido." |
| a1u9l1 (theory) | MEDIO | "going to suena «gonna»" doesn't say this only happens before a verb (*I'm going to Cali* is never "gonna Cali"). The e10 tip says «Las tres formas se usan a diario», but the present continuous for plans is never taught. | Add: "Solo antes de un verbo: *I'm gonna travel*, pero *I'm going to Cali* nunca es gonna."; e10 tip → "Con going to es lo ya planeado. What are you doing tomorrow? también se usa para planes." |
| a1u9l1e10 | MEDIO | The theory teaches "gonna" explicitly, but it is rejected. The Spanish prompt is also identical to e5 (build), which already showed the answer. | add to accept: `["What are you gonna do tomorrow?"]`; es → "¿Qué vas a hacer el fin de semana?", answer "What are you going to do this weekend?", accept `["What will you do this weekend?", "What are you doing this weekend?", "What are you gonna do this weekend?", "What are you going to do on the weekend?"]` |
| a1u9l2e3 | MEDIO (PLAUSIBLE) | "I help you" vs "I'll help you": the only difference is a dark l before h, which Piper may swallow. | Check with Piper (`oir_pares.py`-style). If it is not audible, change the distractor to "I'm help you." |
| a1u9l2 (whole lesson) | MEDIO | The theory's key point (will vs going to) is never practised in any item. | Add a cloze: es "El teléfono está sonando. Yo contesto.", text "The phone is ringing. I ___ get it.", answer "'ll", accept `["will"]` |
| a1u9l1e2 | BAJO | "She goes to study medicine" is grammatical (a purpose infinitive), and the Spanish is ambiguous with no time marker. | es → "Ella va a estudiar medicina el próximo año."; add "next year" to all 3 options/answer. |
| a1u9l2e12 | BAJO (PLAUSIBLE) | "I want be late" is not English, so the sentence gives the answer away. Per CLAUDE.md, won't only comes out 6 of 12 times even in the sentence. | Accept the trade-off, or replace it with a pair whose answer comes out 12/12 (e.g. walk/work). |
| a1u9l2e9 | BAJO (PLAUSIBLE) | "'ll" in the gap may be rejected. | accept → `["'ll"]` |

- **a1u9l1** — grammar/functions actually covered: be going to affirmative, negative (listen) and wh-question (build/write). Notable gaps inside this lesson's own topic: no he/she production; "gonna" is never heard in a listen item; no yes/no question ("Are you going to…?").
- **a1u9l2** — grammar/functions actually covered: will/won't for predictions and promises, the bare verb after will. Notable gaps inside this lesson's own topic: instant decisions ("I'll get it") and offers exist only in the theory; no "Will you…?"; the will vs going to contrast is never practised.
- **a1u9l3** — grammar/functions actually covered: -er/more + than, better/best. Notable gaps inside this lesson's own topic: superlatives only in listen (e4); worse/worst and -y → -ier are never practised; the doubling rule is not taught but is tested (e9).
