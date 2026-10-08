# Brief for adversarial content reviewers (Hablo app)

Hablo is an Android app that teaches **American English** to an adult Colombian (Spanish speaker), A1->B2. Content lives in JSON. You review lesson dumps: each lesson has a THEORY card (Spanish explanation + TRAMPA = the typical Spanish-speaker trap) and exercises, one JSON per line.

Exercise types and how the app grades them:
- listen: audio is played, learner picks the option matching the audio (answer == audio). Problem if two options sound identical or if distractors are too trivially different in spelling only (not audible).
- translate: sees Spanish `es`, picks the English option = `answer`. PROBLEM if any other option is ALSO a correct translation (learner marked wrong for correct English = worst possible bug).
- build: arrange word tiles to form `answer`; `extra` are decoy tiles. PROBLEM if the decoys + answer words can form another correct sentence for `es` that the app would reject.
- type: hears `audio`, types it (dictation); `meaning` is the Spanish gloss. Problem if meaning is wrong.
- speak / shadow: learner says `text`. `sound` = which phoneme is scored. Problem if text is unnatural/wrong or sound label doesn't match anything in the text.
- write: sees Spanish `es`, types English. Graded against `answer` + `accept` list. Normalization the app ALREADY does (do NOT flag these): case, punctuation, accents, extra spaces; contractions expanded both ways (I'm = I am, don't = do not, isn't = is not, can't = cannot, won't = will not, 's = is and 'd = would only after a subject pronoun/wh-word/that/there/here). It also shows a diff hint. PROBLEM (the most important one to catch): a natural, correct English translation of `es` that is NOT in answer/accept and differs by more than contraction/punctuation — e.g. synonyms (big/large), "in a bank"/"at a bank", "I'm 40"/"I'm forty", "I'm 40 years old", optional words ("right now", "the"), alternative word order, "going to" vs "will" when Spanish is ambiguous, you/you all, he/she ambiguity from Spanish, numbers as digits vs words. List the concrete missing accepts.
- cloze: `text` has one ___, learner types `answer` (or an `accept`). Same rule: flag other correct fillers not accepted, and gaps where Spanish `es` doesn't pin down the answer.
- minimalPair: a word is played ("The word is X." or the `sentence` if play=sentence), learner identifies it among `options`. Problem if sentence doesn't contain the answer, or pair isn't really minimal.
- tip: Spanish hint. Problem if it is factually wrong or contradicts the theory.

Also check:
- Grammar/usage errors in answers, audio, theory examples. Theory statements that are false or overgeneralised (e.g., "never", "always" when there are common exceptions a learner will meet).
- American English consistency (course is AmE: "apartment", "check", "movie", "fall", "I have" rather than "I've got" as the default). Flag British-only forms presented as the only answer.
- Spanish text errors (spelling/accents) and Spanish that is ambiguous in a way that makes the expected answer unguessable (e.g., "usted/tú", gender of "they", tense ambiguity).
- Pedagogy: exercises that test something not taught yet in that lesson or earlier; lessons whose exercises don't actually practise the theory point; A1 content that is really B1.

OUTPUT FORMAT (be concrete, no praise, no generic advice). A markdown table per unit:
| id | severity (ALTO = marks correct English wrong or teaches wrong English; MEDIO = ambiguous/misleading/unnatural; BAJO = polish) | problem | exact fix (JSON-level: e.g. add to accept: ["..."], change option X to "...", rewrite tip to "...") |
Then for each lesson ONE line: "grammar/functions actually covered: ...; notable gaps inside this lesson's own topic: ...".
Only report real issues; if unsure, say PLAUSIBLE and why. Do not report contraction/punctuation/case variants.
