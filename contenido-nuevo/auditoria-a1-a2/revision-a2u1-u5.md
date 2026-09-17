> **Note (coordinator correction, applies to everything below).**
> 1. The app normalizes the numbers 0 to 100 written as digits ("8" = "eight"), so accept items that only swap a word for a digit are not needed. Drop them from: a2u1l1e4 ("at 8", "at 8 o'clock"; keep "at 8:00" only if times with a colon are not normalized), a2u2l4e5 ("for 3 years" x2), a2u3l1e5 (both), and a2u5l2e4 ("5 minutes" x2; keep the "Do you mind" items). Years like 2020 and "o'clock" are NOT normalized, so a2u2l4e7 ("since twenty twenty") stays.
> 2. Build exercises do NOT read `accept` today (BuildSentence has no accept field), so for build items prefer the fix that changes a decoy tile. Three build items can't be fixed that way, because the other clause order uses only the answer's own tiles: a2u2l4e6 ("In 2019 he worked here."), a2u4l3e7 ("When you come back, we will talk.") and a2u5l1e6 ("She would pass if she studied more."). Those need an app change (read `accept` in BuildSentence, or accept either clause order); until then they stay open defects. The decoy-change fixes for a2u1l3e6, a2u2l2e6 and a2u4l1e6 are enough on their own.

# A2 units 1–5: adversarial content review

I went through all 145 exercises and the 14 theory cards. I found 44 defects in the exercises and 10 in the theory cards.

Two problems come up across all five units:
- **Numbers written as digits.** No `accept` list anywhere in `curriculum.json` has a number written as digits. Version 0.9.1 lists a "números" fix, but I couldn't see the app code to check whether `normalizeAnswer` treats "8" and "eight" as the same. If it doesn't, the digit items marked ALTO below are real; if it does, they can be dropped.
- **Build exercises that accept only one word order.** Build exercises can carry `accept` (other build exercises in the file already use it). Several items below let the learner put the tiles in the other clause order, which is correct English but gets rejected.

## Unit a2u1

| id | severity | problem | exact fix |
|---|---|---|---|
| a2u1l1 (theory) | BAJO | "when + pasado simple" is presented as the rule, but *When I was walking home…* (when + continuous) is common. The TRAMPA also says *It rained when I went out* means "llovió después de salir"; it really means the rain started when you went out. | Add: «when también puede ir con el continuo: *When I was walking home, …*». Change the TRAMPA to «significa que empezó a llover justo cuando saliste». |
| a2u1l1e4 | ALTO | "at 8" written as digits is not accepted. | add to accept: ["What were you doing at 8?", "What were you doing at 8 o'clock?", "What were you doing at 8:00?"] |
| a2u1l1e5 | ALTO | The simple-past order that the tip itself allows is missing, and so is "paper" for "periódico". | add to accept: ["He read the newspaper while I was cooking.", "While I was cooking, he was reading the paper.", "He was reading the paper while I was cooking.", "While I was cooking, he read the paper."] |
| a2u1l1e7 | MEDIO | The tip says "wait siempre lleva for", which is false: *wait a minute*, *wait here*, *I can't wait*. | tip → «Esperar A algo o a alguien es wait for: wait for the bus.» |
| a2u1l2e2 | MEDIO | The distractor "She used to lived in Cali" sounds almost the same as the answer (the /d/ in "lived in" disappears, and Piper will flatten it too). | Change the option to "She usually lives in Cali." |
| a2u1l2e4 | MEDIO | The course is American English, but the main answer is "football", which means American football in AmE. | answer → "Did you use to play soccer?"; accept → ["Did you use to play football?"] |
| a2u1l2e5 | ALTO | "trabajaba" doesn't say "ya no" (the tip adds that meaning), so the plain past tense is a correct translation and gets rejected. | add to accept: ["My dad worked at a bank.", "My dad worked in a bank.", "My father worked at a bank.", "My father worked in a bank.", "My dad used to work for a bank.", "My father used to work for a bank."]. Or change es to «Antes mi papá trabajaba en un banco (ya no).» |
| a2u1l2e8 / e9 | BAJO | The two tips give different pronunciations of "used to": «YUST-tu» and «YUS-tu». | Use «YUUS-tu» in both. |
| a2u1l3 (theory) | MEDIO | The TRAMPA's example «Llovía, por eso tomé un taxi» has only one connector, so "no lleva las dos piezas" makes no sense. | Change the example to «**Como** llovía, **por eso** tomé un taxi» → *Because it was raining, so I took a taxi* ❌ |
| a2u1l3e4 | ALTO | "While I waited" (correct English) and "the paper" are missing. | add to accept: ["While I waited, I read the newspaper.", "I read the newspaper while I waited.", "While I was waiting, I read the paper.", "I read the paper while I was waiting."] |
| a2u1l3e5 | ALTO | "knock at the door" is correct and common, but only "on" is accepted, and the tip says "knock lleva on". | add to accept: ["Suddenly someone knocked at the door.", "Someone suddenly knocked at the door.", "Suddenly somebody knocked at the door.", "All of a sudden someone knocked on the door."]; tip → «knock on (o at) the door…» |
| a2u1l3e6 | MEDIO | The decoy "because" lets the learner build "I was late because I missed the bus.", which is correct English and gets rejected. | extra → ["but", "lost", "arrive"] |

- **a2u1l1:** grammar/functions actually covered: past continuous (affirmative, negative, question), plus when/while with the simple past; notable gaps inside this lesson's own topic: "weren't" is never practised, and no exercise makes the learner choose between when and while.
- **a2u1l2:** grammar/functions actually covered: used to (affirmative, negative, question); notable gaps inside this lesson's own topic: *be used to* (= estar acostumbrado) is never contrasted, and it is the classic confusion for Spanish speakers.
- **a2u1l3:** grammar/functions actually covered: when, while, so, because, then, suddenly; notable gaps inside this lesson's own topic: "so" is never produced by the learner (only heard or built), and "then" is only in a dictation.

## Unit a2u2

| id | severity | problem | exact fix |
|---|---|---|---|
| a2u2l1e4 | ALTO | "I still haven't eaten" (= todavía no he comido) and "I have not yet eaten" are missing. PLAUSIBLE: "I didn't eat yet" is normal American English. | add to accept: ["I still haven't eaten.", "I have not yet eaten.", "I didn't eat yet."] |
| a2u2l1e5 | BAJO | "finished the work" is accepted but "finished the job" is not. | add to accept: ["They have finished the job."] |
| a2u2l1e7 | BAJO | The gloss "Perdí las llaves" uses the simple past for a present-perfect sentence and "las" for "my". | meaning → «He perdido mis llaves (y siguen perdidas).» |
| a2u2l1e9 | BAJO | The tip writes «shis-GON», but the contracted "she's" is voiced: /ʃiz/. | «shiz-GON» |
| a2u2l2e3 | BAJO | The context «se fue ayer» contains a time word; the very next lesson says a time word forces the simple past. | es → «Ella se fue a París y todavía está allá.» |
| a2u2l2e5 | MEDIO | "worked the night shift" is missing. | add to accept: ["Have you ever worked the night shift?", "Have you ever worked a night shift?"] |
| a2u2l2e6 | MEDIO | The decoy "went" lets the learner build "He went to London twice.", which is an acceptable translation, especially in American English. | extra → ["gone", "being", "two"] |
| a2u2l3 (theory) + e5 tip | MEDIO | «still … antes del verbo», but the card's own example *I'm **still** waiting* puts still after "be". The e5 tip repeats "wait siempre con for". | Theory: «still va después de am/is/are y de los auxiliares, y antes del verbo principal: *I'm still waiting*, *I still live here*». In e5, drop «siempre». |
| a2u2l3 (theory) | MEDIO | The course is American English, where *I just arrived / I already ate / Did you eat yet?* are the everyday forms. The card never says so, yet e4 accepts "She just left". | Add: «En inglés americano también se oye el pasado simple con just, already y yet: *I just arrived*, *Did you eat yet?*» |
| a2u2l3e2 | MEDIO | The distractor "I haven't finish yet" is spelling-only when spoken: the /t/ of "finished" merges into "yet". | option → "I've already finished." |
| a2u2l3e4 | ALTO | «salir» = go out, which is not accepted. | add to accept: ["She has just gone out.", "She just went out."] |
| a2u2l3e8 | BAJO | "I have just arrived home" is stiff. | text → "I have just gotten home." (check both words are in cmudict) |
| a2u2l4 (theory) | ALTO | Two problems. (1) «Palabras que piden present perfect: ever, never, already, yet, just» is false for American English (*Did you ever…?*, *I just ate*). (2) The card never teaches for/since, but e5, e7 and e8 test them. | Add the American English note from a2u2l3. Add: «**for** + duración (*for three years*), **since** + punto de inicio (*since 2020*) → con present perfect». |
| a2u2l4e2 | MEDIO | The tip «Con una fecha, siempre pasado simple» contradicts e7, *I have known her since 2020*. | tip → «Con *in 2020* (un momento ya cerrado) va pasado simple. Ojo: con *since 2020* sí va present perfect.» |
| a2u2l4e4 | ALTO | "Last week I watched…" is missing, even though "Last week I saw…" is accepted. | add to accept: ["Last week I watched that movie.", "Last week I watched that film."] |
| a2u2l4e5 | ALTO | "3" written as a digit, and "been living" without "for", are missing. | add to accept: ["I have lived here for 3 years.", "I have been living here for 3 years.", "I have been living here three years."] |
| a2u2l4e6 | ALTO | The tiles also form "In 2019 he worked here.", which is correct and gets rejected. | add accept: ["In 2019 he worked here."] |
| a2u2l4e7 | MEDIO (PLAUSIBLE) | This is a dictation, and the learner hears "twenty twenty" and may type it in words. | add accept: ["I have known her since twenty twenty."] |
| a2u2l4e10 | BAJO | The carrier sentence gives the answer away ("I have known her sense 2020" means nothing), so the item doesn't test the ear. | sentence → "It makes sense." / "Since then." pair? Better: keep play=sentence but use "Since when?" / check that Piper keeps /ɪ/ first. |

- **a2u2l1:** grammar/functions actually covered: have/has + participle, irregular participles; notable gaps inside this lesson's own topic: no question form ("Have you finished?") and no short answers ("Yes, I have").
- **a2u2l2:** grammar/functions actually covered: ever/never, been vs gone; notable gaps inside this lesson's own topic: the been/gone contrast is only tested once as a cloze, never produced in a write, and "Have you ever…?" answers are never produced.
- **a2u2l3:** grammar/functions actually covered: just, already, yet, still; notable gaps inside this lesson's own topic: "already" in questions or at the end of a sentence, and "still" is only used with "be".
- **a2u2l4:** grammar/functions actually covered: time word → simple past, otherwise present perfect; for/since (untaught); notable gaps inside this lesson's own topic: no write where the learner must turn a sentence with "yesterday" into the past, and no "How long have you…?".

## Unit a2u3

| id | severity | problem | exact fix |
|---|---|---|---|
| a2u3l1e1 | ALTO | The distractor "I have work on Saturday." is correct, natural English with the same meaning. | option → "I have working on Saturday." |
| a2u3l1e2 | BAJO | "have to" and "has to" differ only in /f/ vs /s/. | Acceptable; optional: change the distractor to "She had to wear a uniform." |
| a2u3l1e4 | MEDIO | "need to" is missing, even though a2u3l2e5 accepts it for «tener que». | add to accept: ["Do you need to work tomorrow?"] |
| a2u3l1e5 | ALTO | "2 hours" written as a digit is missing. | add to accept: ["I had to wait 2 hours.", "I had to wait for 2 hours."] |
| a2u3l2 (theory) | MEDIO | In American English, "mustn't" is rare in speech; people say *can't* or *you're not allowed to*. The TRAMPA's claim «el español no separa las dos ideas» is also doubtful: «no debes» and «no tienes que» are different. | Add: «En EE. UU. la prohibición hablada casi siempre es *You can't smoke here*; mustn't se ve en letreros y reglas.» TRAMPA → «En el habla, «no tienes que tocar eso» se usa a veces como prohibición…» |
| a2u3l2e4 | MEDIO | "mustn't touch" vs "mustn't to touch" is hard to hear: the "to" merges with the /t/. | option → "You must touch that." |
| a2u3l2e5 | ALTO | "need to leave" and "have got to" are missing (the second is very common American English). | add to accept: ["We need to leave now.", "We have got to go now.", "We have got to leave now."] |
| a2u3l2e9 | BAJO (PLAUSIBLE) | The claim that stressing DON'T «suena a regaño» is doubtful; it is contrastive stress ("not required"). | tip → «El peso en COME y en HAVE; no hay regaño en la frase.» |
| a2u3l3e2 | MEDIO | "shouldn't works so" sounds the same as "shouldn't work so" (the two /s/ sounds merge). | option → "He should work so much." |
| a2u3l3e4 | ALTO | "that fast" is very common American English and not accepted. | add to accept: ["You shouldn't eat that fast.", "You shouldn't eat that quickly."] |
| a2u3l3e5 | ALTO | Common versions of «descansar un poco» are missing. | add to accept: ["Why don't you get some rest?", "Why don't you rest for a while?", "Why don't you rest for a bit?", "Why don't you take a rest?", "Why don't you take a short break?"] |
| a2u3l3e9 | MEDIO | The tip marks stress on «DONCHU», which is exactly the reproachful pattern it warns against. | tip → «why don't you va rápido y pegado: «why-dontchu TRY again». El peso en TRY; el tono baja al final.» |

- **a2u3l1:** grammar/functions actually covered: have to (affirmative, negative, question, past), don't have to = no hace falta; notable gaps inside this lesson's own topic: no "Does she have to…?" and no "need to".
- **a2u3l2:** grammar/functions actually covered: must, mustn't vs don't have to; notable gaps inside this lesson's own topic: "can't" / "not allowed to" as prohibition (the natural American English form).
- **a2u3l3:** grammar/functions actually covered: should/shouldn't, Why don't you…; notable gaps inside this lesson's own topic: "You could…" is taught in the theory but never practised, and "Should I…?" is only in the cloze.

## Unit a2u4

| id | severity | problem | exact fix |
|---|---|---|---|
| a2u4l1 (theory) | MEDIO | «el presente simple solo vale para horarios fijos de trenes y cines» contradicts a2u4l3 (present after when/if) and is too narrow (*The meeting starts at 9*). | «…vale para horarios y calendarios fijos (*the class starts at 9*) y después de when/if (lección 3).» |
| a2u4l1e1 | MEDIO (PLAUSIBLE) | "Tomorrow I will go to the doctor." is grammatical and acceptable, but it is marked wrong. | option → "Tomorrow I am go to the doctor." |
| a2u4l1e4 | ALTO | "'s" after a noun is not expanded, so "The phone's ringing. I'll answer it." is rejected. Other replies are missing too. | add to accept: ["The phone's ringing. I'll answer it.", "The phone is ringing. I will answer.", "The phone is ringing. I will pick it up.", "The phone's ringing. I'll pick it up."] |
| a2u4l1e5 | ALTO | "going to" is missing. | add to accept: ["What are you going to do this weekend?", "What are you up to this weekend?", "What are you doing over the weekend?"] |
| a2u4l1e6 | MEDIO | The decoy "will" lets the learner build "She will study medicine.", a valid rendering of «va a estudiar». | extra → ["goes", "studying", "studies"] |
| a2u4l2 (theory) | BAJO | «soy capaz de ir» misreads *I can go* (usually "puedo / tengo permiso o tiempo"). "maybe" is not taught, yet e4 accepts it. | «…eso significa «puedo ir» (tengo tiempo o permiso)…». Add «**maybe** (una palabra) = quizás: *Maybe I'll go*; no confundir con *may be*.» |
| a2u4l2e4 | ALTO | Several common versions are missing. | add to accept: ["I may arrive late.", "Maybe I will arrive late.", "I might get there late.", "Maybe I will get there late."] |
| a2u4l2e5 | ALTO | "need to" and "maybe" are missing. | add to accept: ["We might need to wait.", "We may need to wait.", "Maybe we will have to wait."] |
| a2u4l3 (theory) | MEDIO | «Nunca» is too strong: when "when" is the question word, will is correct (*I don't know when she'll arrive*). «en inglés no hay subjuntivo» is false (*If I were*). | Add «(Ojo: si when es «cuándo» en una pregunta, sí va will: *Do you know when she'll arrive?*)». Change to «como el inglés no usa subjuntivo aquí». |
| a2u4l3e5 | ALTO | The reversed clause order and the missing going-to combination are rejected. | add to accept: ["If you don't hurry up, you are going to miss the bus.", "You will miss the bus if you don't hurry.", "You are going to miss the bus if you don't hurry.", "Unless you hurry, you will miss the bus."] |
| a2u4l3e6 | ALTO | "till" (common American English), "gets here" and "comes" are missing. | add to accept: ["I will wait till she arrives.", "I will wait until she gets here.", "I am going to wait until she gets here.", "I will wait until she comes.", "I am going to wait till she arrives."] |
| a2u4l3e7 | ALTO | The tiles also form "When you come back, we will talk.", which is correct and gets rejected. | add accept: ["When you come back, we will talk."] |
| a2u4l3e8 | BAJO | «Llámame antes de salir» is ambiguous about who leaves. | meaning → «Llámame antes de irte.» |

- **a2u4l1:** grammar/functions actually covered: will (instant decision), going to (plan), present continuous (arrangement); notable gaps inside this lesson's own topic: will for predictions and going to with visible evidence are never practised.
- **a2u4l2:** grammar/functions actually covered: might / might not, may; notable gaps inside this lesson's own topic: maybe vs may be, and "might" in questions (unusual, and worth saying so).
- **a2u4l3:** grammar/functions actually covered: present after when/if/as soon as/until/before, first conditional; notable gaps inside this lesson's own topic: "after" is never practised, and "unless" isn't taught.

## Unit a2u5

| id | severity | problem | exact fix |
|---|---|---|---|
| a2u5l1 (theory) + e2 tip | MEDIO | «lo correcto es were, no was» and «se dice así siempre» are too strong: *If I was you* is common in American English speech. | «were es la forma cuidada y la del examen; en conversación se oye was, pero tú usa were.» |
| a2u5l1e4 | ALTO | The reversed clause order and "go swimming" are missing. | add to accept: ["I would swim every day if I lived at the beach.", "I would swim every day if I lived on the beach.", "I would swim every day if I lived by the beach.", "If I lived at the beach, I would go swimming every day."] |
| a2u5l1e5 | ALTO | "do that" is missing. The accepted "I would leave it" is not «no lo haría» (harmless, since it only over-accepts). | add to accept: ["If I were you, I wouldn't do that.", "I wouldn't do that if I were you."]; optionally remove "If I were you, I would leave it." |
| a2u5l1e6 | ALTO | The tiles also form "She would pass if she studied more.", which is correct and gets rejected. | add accept: ["She would pass if she studied more."] |
| a2u5l1e8 | ALTO | `sound: "v"`, but the text "If I were you, I would wait" has no /v/ at all (the tip talks about w). | sound → "general" (or text → "If I were you, I would leave." and keep "v") |
| a2u5l1e9 | BAJO | The tip writes «what-WUD-ju» (capitals = stress) and then says the stress is on DO and WON. | «what-wud-ju DO…» |
| a2u5l2e4 | ALTO | "5" written as a digit and "Do you mind" are missing. | add to accept: ["Would you mind waiting 5 minutes?", "Would you mind waiting for 5 minutes?", "Do you mind waiting five minutes?", "Do you mind waiting for five minutes?"] |
| a2u5l2e5 | MEDIO | Other words for «genial» and the -ing subject are missing. | add to accept: ["It would be awesome to see you.", "It would be wonderful to see you.", "Seeing you would be great.", "It would be good to see you."] |
| a2u5l2e10 | BAJO | The carrier sentence gives the answer away ("The ban is outside" makes no sense), and van comes out with its /v/ in only 9 of 12 recordings. | Accept as marginal, or sentence → "I saw the van." (a /b/ reading, "I saw the ban", is also possible, so the learner has to listen) |

- **a2u5l1:** grammar/functions actually covered: second conditional, If I were you, would questions; notable gaps inside this lesson's own topic: "could" in the if-clause only appears in a dictation, and "were" with he/she is never produced.
- **a2u5l2:** grammar/functions actually covered: would love, would rather, would mind + -ing, would be; notable gaps inside this lesson's own topic: the "No, not at all" reply from the TRAMPA has no exercise, "I'd rather" is never produced in a write, and "Would you like…?" offers are missing.
