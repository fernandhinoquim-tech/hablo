# Adversarial content review: a2u6 to a2u10 (15 lessons, 136 exercises)

> **Coordinator's note (applied to this report):**
> - The app normalizes digits 0-100 ("8" = "eight"), so digit-only accept items are not needed. Years and "o'clock" are NOT normalized.
> - Build exercises do NOT read `accept` today (BuildSentence has no accept field). For build items, the fixes below change the decoys, the answer or the Spanish instead of adding accepts.

I found 52 real defects: 15 ALTO, 24 MEDIO and 13 BAJO, including the PLAUSIBLE ones.

Checks I could not run:
- **I could not hear any audio.** Neither espeak-ng nor Piper is installed in the review environment, so the audio findings are PLAUSIBLE.
- **I don't know whether build requires every answer tile.** The ALTO build findings (a2u7l3e6, a2u10l1e6) assume the learner can submit with an answer tile left unused. They stay PLAUSIBLE until that is checked.

## a2u6

| id | severity | problem | exact fix |
|---|---|---|---|
| a2u6l1 (theory) | MEDIO | The TRAMPA says "*It's very expensive* = está caro". It means "está **muy** caro", and the whole point of the card is very vs too. | Change it to "*It's very expensive* = está muy caro (pero quizá lo compro)." |
| a2u6l1 (theory) | BAJO | "too … Siempre antes del adjetivo" is too absolute. "too" = «también» goes at the end, and e1 uses that as a distractor. It also goes before adverbs ("too fast"). | Change it to "too = demasiado: va antes del adjetivo o adverbio (too big, too fast). Ojo: al final de la frase, too = también." |
| a2u6l1e5 | MEDIO | Indoors, "aquí" is naturally "in here", and "loud" is the everyday AmE word. Both are rejected. | Add to accept: ["There is too much noise in here.", "It is too loud here.", "It is too loud in here.", "It is too noisy in here."] |
| a2u6l1e6 | MEDIO (PLAUSIBLE) | The decoy "much" lets the learner build "You are driving much too fast.", which is correct English for "demasiado rápido". It would be rejected. | Change extra "much" to "many". |
| a2u6l2 (theory) | BAJO | The table has only -body forms, but e4/e5 accept anyone/everyone and the learner will meet someone/no one. | Add a line: "-one = -body: someone, anyone, no one, everyone." |
| a2u6l2e4 | ALTO | "Is there someone here?" is correct English (some- in a question that expects yes) and is rejected. So is the natural "in here". | Add to accept: ["Is there someone here?", "Is there somebody here?", "Is someone here?", "Is somebody here?", "Is anyone in here?", "Is anybody in here?", "Is there anyone in here?", "Is there anybody in here?"] |
| a2u6l2e6 | BAJO | The tip implies "I can find my keys nowhere" is a normal alternative. It is stilted. | Change the tip to "Con can't va anywhere. Nunca can't + nowhere (serían dos negaciones)." |
| a2u6l2e7 | BAJO | The tip "somebody va con verbo en singular" is irrelevant: "left" has no singular or plural form. | Change the tip to "somebody = alguien, en frase afirmativa." |
| a2u6l3e2 | MEDIO (PLAUSIBLE) | The distractor "Most the students passed." differs only by a reduced /ə/ ("of"). The lesson's own e9 tip says "most of" runs together. It is nearly impossible to tell apart by ear. | Change that option to "The most of the students passed." |
| a2u6l3e4 | ALTO | "This one is the most expensive." is the most natural rendering and is rejected. | Add to accept: ["This one is the most expensive.", "This one is the most expensive one."] |
| a2u6l3e5 | ALTO | The Spanish comma structure invites "Most Colombians, in general, speak Spanish.", which is rejected. This is the known pending "in general" item. | Add to accept: ["Most Colombians, in general, speak Spanish.", "Most Colombians generally speak Spanish.", "Most Colombians speak Spanish in general.", "Most people in Colombia speak Spanish."]. Also change es to "En general, la mayoría de los colombianos habla español." |
| a2u6l3e7 | MEDIO | "Most of my time goes into work." is unnatural, and "goes into work" can be heard as "goes in to work" (commutes). | Change the audio to "I spend most of my time at work." and the meaning to "Paso la mayor parte de mi tiempo en el trabajo." |
| a2u6l3e9 | MEDIO (PLAUSIBLE) | "live" is a heteronym that Piper reads as /laɪv/ (CLAUDE.md). The teacher may say "laiv" in this shadow item. | Measure it with oir_pares. If it fails, change the text to "Most of my friends are from here." |

- **a2u6l1** covers: too + adj/adv, too much/many, adj + enough, enough + noun, too vs very. Gaps: too much vs too many is produced only once (e5, "much"); nothing practises "enough" after an adverb or "enough to + verb"; e1 is the only item on too vs very.
- **a2u6l2** covers: some-/any-/no-/every- + thing/body/where and single negation. Gaps: the double-negation trap is only recognized (e1), never written; nobody, nowhere, everywhere and somewhere are never produced; nothing covers any- meaning "cualquier".
- **a2u6l3** covers: most + noun, most of + determiner/pronoun, the most (superlative). Gaps: "most of the + noun" is never produced (only listen); the superlative appears here before its own lesson (a2u7l2).

## a2u7

| id | severity | problem | exact fix |
|---|---|---|---|
| a2u7l1 (theory) | BAJO | "*He speaks slow* ❌" overstates it. The flat adverb "slow" is informal but standard in AmE ("Drive slow"). | Change it to "*He speaks slow* suena descuidado; lo correcto es **He speaks slowly**." |
| a2u7l1e1 | MEDIO | The distractor "He speaks very good English too." is wrong only because of "too". "He speaks very good English" is correct and idiomatic, so a learner who recognizes it gets confused. | Change the option to "He speaks very well English." (the typical Spanish word-order error). |
| a2u7l1e3 | MEDIO (PLAUSIBLE) | In Colombia, "hablar despacio" often means speak quietly («habla despacito»), so the Spanish is ambiguous for this learner. | Change es to "Por favor, habla más lento, no tan rápido." |
| a2u7l1e5 | ALTO | "I easily finished the work/job." (adverb before the verb) is correct and rejected. | Add to accept: ["I easily finished the work.", "I easily finished the job.", "I finished my work easily."] |
| a2u7l1e6 | MEDIO (PLAUSIBLE) | The decoys "drives" and "carefully" build "He drives carefully.", a natural equivalent of "Es un conductor cuidadoso". It would be rejected. | Change extra "drives" to "drive". |
| a2u7l2 (theory) + e6/e7 | ALTO | The TRAMPA rule "of para grupos" is contradicted by the lesson itself. e7 teaches "the tallest **in** the class" (a group), and e3/e6 use "of" with "the year" and "my life", which are periods, not groups. The e6 tip repeats the false rule. | Change the TRAMPA ending to "…se usa **in** con lugares y grupos (in the city, in the class, in my family) y **of** con periodos o cantidades (of the year, of my life, of the three)." Change the e6 tip to "bad → the worst. Con un periodo (my life) va of." |
| a2u7l2 (theory) | BAJO | The -y adjectives (easiest, happiest) are missing, and learners will meet them immediately. | Add "terminados en -y → -iest: easy → the easiest." |
| a2u7l2e4 | MEDIO | "toughest" is a common synonym and is rejected. | Add to accept: ["This is the toughest problem."] |
| a2u7l2e5 | ALTO | "…as I thought it was." and "…as I thought it would be." are the most natural AmE forms and are rejected. The subject "This" is rejected too. | Add to accept: ["It isn't as expensive as I thought it was.", "It isn't as expensive as I thought it would be.", "It isn't as expensive as I expected.", "This isn't as expensive as I thought."] |
| a2u7l3 (theory) | BAJO | "si el que es el objeto" is garbled Spanish without the emphasis on «que». | Change it to "si el **que** es el objeto (recibe la acción), se puede quitar." |
| a2u7l3e3 | BAJO | "in which" is a correct filler and is rejected. | Add to accept: ["in which"] |
| a2u7l3e5 | MEDIO | "Do you know the girl next door?" and "…the girl living next door?" are natural and rejected. | Add both to accept. |
| a2u7l3e6 | ALTO (PLAUSIBLE) | The theory says the object "that" can be dropped ("las dos son correctas"), but the build rejects "The car I bought is red." If the learner leaves the "that" tile unused, the lesson's own rule is contradicted. The `accept: []` field here is dead (build doesn't read it). | Build fix: change the answer to "The car I bought is red." (keep extra ["who", "it", "buy"]; "that" is then not a tile, so there's only one valid build). Change the tip to "El that se puede quitar cuando es objeto: the car (that) I bought. Y no se repite el it." Remove the dead `accept`. This also closes the gap that dropping the relative is never practised. |

- **a2u7l1** covers: adjective vs -ly adverb, well/fast/hard. Gaps: good → well is never produced in writing; fast → fast is never practised; nothing covers late/early.
- **a2u7l2** covers: -est, the most, best/worst, as…as. Gaps: the affirmative as…as is never produced (only e5, negative); nothing covers -iest; the best/worst appears only in translate, build and speak.
- **a2u7l3** covers: who/that/where relatives and no resumptive pronoun. Gaps: "which" is never produced; dropping the object relative is taught but never practised, and e6 actively rejects it (fixed above).

## a2u8

| id | severity | problem | exact fix |
|---|---|---|---|
| a2u8l1 (theory) | MEDIO | It implies "se" can only become a passive. AmE very often uses "they/people + active" ("They speak English here"). | Add "También se dice *They speak English here*; aquí practicamos la pasiva." |
| a2u8l1e2 | MEDIO (PLAUSIBLE) | The distractor "was build in" vs "was built in" differs only in /d/ vs /t/ before a vowel, which is inaudible in TTS. | Change that option to "This house is built in 1920." |
| a2u8l1e4 | ALTO | The AmE "mailed", the natural "on Mondays", and "get sent" are rejected. The active "They send…" also fits the Spanish but is rejected (MEDIO). | Add to accept: ["The letters are sent on Mondays.", "The letters are mailed every Monday.", "The letters get sent every Monday.", "Every Monday the letters are sent.", "They send the letters every Monday."] |
| a2u8l2 (theory) | BAJO | "get on = subirse" is misleading for Colombian «subirse al carro», which is "get in". | Change it to "get on = subirse (al bus, al tren; al carro es get in)." |
| a2u8l2 (theory) | BAJO | "I'm searching for my keys" is correct, but the card only says "searching my keys ❌", so learners may think "search" itself is wrong. | Add "(*searching **for** my keys* sí es correcto, pero menos común)." |
| a2u8l2e4 | ALTO | "switch it off" and the AmE "shut it off" are rejected. | Add to accept: ["Switch it off, please.", "Please switch it off.", "Shut it off, please.", "Please shut it off."] |
| a2u8l2e5 | ALTO | The AmE "Don't quit." is rejected. Meanwhile "Never give up." (= «nunca te rindas») is accepted although it doesn't match the Spanish. | Add to accept: ["Don't quit."]. Optionally remove "Never give up.". |
| a2u8l2e6 | MEDIO | In this AmE course, "look after" is the British-leaning option; the American default for «cuidar» is "take care of". The theory never mentions it. | Add to the theory: "look after = cuidar (en EE. UU. se oye más **take care of**)." |
| a2u8l2e8 | MEDIO | The sound is "rl" but the tip only talks about the ʊ vowel. The tip doesn't match the scored sound. | Change the tip to "looking con l (la lengua toca arriba) y for con r americana, sin vibrar." |
| a2u8l3 (theory) + e1 tip | BAJO | "home no lleva to" / "nunca" is too absolute: "go to his home" is correct. | Change it to "*home* solo (sin my/his) no lleva to." |
| a2u8l3e4 | ALTO | «caminar por el parque» most naturally means "walk in/around the park" or "take a walk in the park". All are rejected, and the Spanish doesn't pin down "through". | Add to accept: ["We walked around the park yesterday.", "Yesterday we walked around the park.", "We walked in the park yesterday.", "Yesterday we walked in the park.", "We went for a walk in the park yesterday.", "We took a walk in the park yesterday."]. Or change es to "Ayer atravesamos el parque a pie." |
| a2u8l3e5 | ALTO | The AmE "ride the bus" is rejected. The progressive reading and "get to work" are also rejected. | Add to accept: ["I ride the bus to work.", "I get to work by bus.", "I am going to work by bus.", "I take a bus to work."] |
| a2u8l3e10 | BAJO | It has no `"play": "sentence"`, so the sentence is only shown. If the sentence is visible, "go to ___ by bus" gives the answer away (walk makes no sense there). | Change the sentence to one where both words fit, e.g. "I like to walk after work." with answer "walk". Or hide the sentence until after the answer. |

- **a2u8l1** covers: present/past passive and "by". Gaps: no negative or question passive; "by" appears only in the cloze.
- **a2u8l2** covers: get up, look for/after, turn off, give up, put on, and it-in-the-middle. Gaps: an object pronoun with an inseparable verb ("look after her") is never produced; "take off" (both meanings) is never practised.
- **a2u8l3** covers: to/into/out of/across/through/up, and home without "to". Gaps: along, over and down are never produced (along only as a listen distractor); come here/there is never practised.

## a2u9

| id | severity | problem | exact fix |
|---|---|---|---|
| a2u9l1 (theory) | MEDIO | "say — no lleva a quién" is false: "He said **to me** that…" is correct. "tell siempre con persona" is also false ("tell the truth", "tell a story"), and the lesson's own e7 uses the first. Backshift is stated as absolute, although AmE often skips it when the fact is still true. | Change it to "say no lleva la persona pegada (si la pones, va con to: said to me); tell sí (told me), salvo en fijas como tell the truth / tell a story. El retroceso es lo normal; si algo sigue siendo verdad, en conversación a veces no se retrocede." |
| a2u9l1e3 | MEDIO | «trabajaba» also means "used to work", which is correct and rejected. | Add to accept: ["used to work"] |
| a2u9l1e4 | MEDIO (PLAUSIBLE) | "Can you explain this?" (Spanish «me» is often just politeness) and the colloquial AmE "explain this for me" are rejected. | Add to accept: ["Can you explain this?", "Could you explain this?", "Can you explain it to me?"] |
| a2u9l1e5 | ALTO | «llegaría tarde» translated literally as "arrive late" is rejected, as is the backshifted "going to". | Add to accept: ["He told me that he would arrive late.", "He told me he would arrive late.", "She told me that she would arrive late.", "She told me she would arrive late.", "He told me he was going to be late.", "She told me she was going to be late."] |
| a2u9l2 (theory) | BAJO | It says "Me too … no sirve para lo negativo" but never gives the negative counterpart "Me neither", which e4 accepts. | Add "Lo negativo informal es **Me neither**." |
| a2u9l2e4 / e5 | MEDIO | es shows both turns of a dialogue ("—No puedo nadar. —Yo tampoco."), so the learner may type both lines and be rejected. | Change es to "Responde «Yo tampoco» a: I can't swim." (e5: "Responde «Yo también» a: I've been there."). Or add the two-line versions to accept. |
| a2u9l2e5 | ALTO | "I have been there too." (the full natural form) is rejected. | Add to accept: ["I have been there too.", "I have been there as well."] |

- **a2u9l1** covers: say vs tell, backshift of present/present continuous/will, explain to. Gaps: can → could is never practised; "say to" is never shown.
- **a2u9l2** covers: So/Neither + auxiliary + subject with be/do/can/have/did. Gaps: "Me neither" and "I do too / I don't either" are accepted but never taught; nothing practises "So does he" in the third person present. This unit has only 2 lessons (the others have 3).

## a2u10

| id | severity | problem | exact fix |
|---|---|---|---|
| a2u10l1 (theory) | MEDIO | "stop" is listed as -ing only, but "stop to buy gas" is correct with another meaning (classic trap, next to a2u10l3's to = para). "try" also takes both forms. | Add "Ojo: **stop to + verbo** = detenerse PARA hacer algo (stop to eat); **try** acepta las dos." |
| a2u10l1e4 | ALTO | "stop + -ing" (taught in this lesson), the AmE "got off work", and "o'clock" (not normalized) are all rejected. Digits need nothing: "6" = "six" is already normalized. | Add to accept: ["I stopped working at six.", "I finished working at six o'clock.", "I got off work at six.", "I finished work at six o'clock."] |
| a2u10l1e5 | ALTO | "She's a good cook." and "She cooks well." are natural and rejected. | Add to accept: ["She is a good cook.", "She cooks well."] (the diff hint will still steer toward good at). |
| a2u10l1e6 | MEDIO (PLAUSIBLE) | "play guitar" without "the" is standard AmE. If the learner leaves the "the" tile unused, the build rejects it, and build can't take an accept. | Build fix: change the answer to "I want to learn to play guitar." (keep extra ["learning", "playing", "for"]). "the" is then not a tile, so there's only one valid build, in AmE form. |
| a2u10l2 (theory) + e5 | MEDIO | In AmE the natural form is "I exercise / work out", and "do exercise" sounds BrE or non-native. Yet it is the model answer shown to the learner. | Change the answer to "I exercise every morning." and move "I do exercise every morning." to accept. In the theory, change it to "do exercises / get exercise (en EE. UU. casi siempre solo *exercise*)". |
| a2u10l2e4 | ALTO | The accept list is inconsistent: "Could you cook dinner tonight?" is missing. | Add to accept: ["Could you cook dinner tonight?", "Can you fix dinner tonight?"] |
| a2u10l2e5 | ALTO | Fronted orders are accepted only with "do exercise". | Add to accept: ["Every morning I exercise.", "Every morning I work out.", "I do exercises every morning.", "I get exercise every morning."] |
| a2u10l2e6 | ALTO (PLAUSIBLE) | With the same tiles, "Please don't make noise." is correct and would be rejected; a decoy change can't prevent that order. The tip "noise no lleva -s" is false: "strange noises" and "a noise" exist, and the decoy "noises" builds a borderline-acceptable sentence. | Build fix: change es to "No hagas ruido." and the answer to "Don't make noise." (drop "please", so there's only one valid order). Change extra "noises" to "does". Change the tip to "El ruido se make. Aquí noise va sin -s: ruido en general." |
| a2u10l2e3 | BAJO | "reach" (reach a decision) is a correct filler. | Add to accept: ["reach"] |
| a2u10l3e3 | BAJO | "in order to" is a correct filler. | Add to accept: ["in order to"] |
| a2u10l3e4 | ALTO | "get milk", "some milk", and "for milk" (for + noun, this lesson's own rule) are all rejected. | Add to accept: ["I went to the store to get milk.", "I went to the store to buy some milk.", "I went to the store to get some milk.", "I went to the store for milk.", "I went to the store for some milk."] |
| a2u10l3e5 | ALTO | "find a better job", "so I can…" and "in order to" are rejected. | Add to accept: ["I study English to find a better job.", "I study English in order to get a better job.", "I study English so I can get a better job.", "I study English so that I can get a better job."] |
| a2u10l1e8 | BAJO | «en-YOY» misrepresents /dʒ/ in "enjoy". | Change the tip to "enjoy suena «in-YÓI», con una y fuerte, casi «dy»." |
| a2u10l3e6 | BAJO | `"accept": []` on a build is dead (build doesn't read it). | Remove the field. |

- **a2u10l1** covers: verb + to (want, decide, learn), verb + -ing (enjoy, finish), preposition + -ing. Gaps: need, hope, avoid, mind, keep and suggest are never practised; the stop to/-ing contrast is missing.
- **a2u10l2** covers: make a mistake/decision/dinner/noise/friends, do homework/exercise. Gaps: do the dishes, do business, do your best and make money are never practised; there is no production item where the learner must choose do and gets it right in writing.
- **a2u10l3** covers: to + verb for purpose, for + noun, for + -ing for function. Gaps: for + noun is never produced in writing (only listen and type); nothing practises "for + -ing" outside the build.
