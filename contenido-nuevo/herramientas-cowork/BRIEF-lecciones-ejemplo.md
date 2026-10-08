# Writing NEW B2 lessons for Hablo (read fully)

App: Android, teaches AMERICAN English to Fero, a 40-year-old Colombian (Spanish speaker), no programmer, preparing Aptis (needs B1 in all 4 skills). The course "sello de la casa": every lesson attacks the concrete error of someone who THINKS IN SPANISH (I have 25 years, He work, My sister is doctor, espeak, the life is hard, a car red...). Theory/tips are in Spanish (Colombian-neutral, tú), English is American (store, apartment, cell phone, check, soccer, fall, pants, zip code, gas).

Read first: REVIEW_BRIEF.md (exercise types + grading rules), PATCH_SPEC.md rule 3 (exact normalization), ejemplo_b1.json (house style of a finished lesson), indice_a1_b1.txt (everything A1-B1 already teaches: B2 builds on it; never re-teach a B1 point as if new — when a B2 lesson revisits one, go clearly deeper).

## Output format
Write ONE JSON file (path given in your task): {"units": [ {"id": "b1u1", "emoji": "...", "title": "<Spanish>", "subtitle": "<Spanish>", "lessons": [ LESSON, LESSON, LESSON ]} , ... ]}
LESSON = {"id": "b1u1l1", "title": "<short Spanish>", "theory": {"title": "...", "body": "...", "trap": "..."}, "exercises": [ ... ]}
Exercise ids "<lesson id>e<n>" from e1, consecutive. Key order: id, type, audio, es, text, sound, options, answer, accept, extra, meaning, sentence, play, tip. Every exercise has a Spanish "tip".
Be efficient: write the file in one go with a script if convenient, validate, fix, done. Reply with ONE line.

## Theory card
body: 80-130 words, Spanish, **negrita** for forms, *cursiva* for examples, blank line (\n\n) between blocks, ❌/✅ allowed. Must be TRUE with no false "nunca/siempre" (add the common exception if there is one). trap: 1-3 sentences: the precise error of a Spanish thinker and why it happens.

## Exercises per lesson (12-13), in this mix, interleaved (not grouped by type):
listen 1 · translate 1 · build 1 · type 1 · speak 1 · cloze 3 · write 3 · shadow 1 (12 total; no minimalPair). B2 is mostly production.
Rules per type (the build fails if broken):
- listen: audio == answer; 3 options; distractors must sound CLEARLY different when spoken by TTS (not just a final -s/-ed/-d swallowed before a consonant); distractors = typical Spanish-speaker errors.
- translate: es + 3 options, exactly ONE correct; add "accept" (other natural English translations, never a distractor) — it is used when the review deck turns it into free writing.
- build: es, answer (a sentence; tiles = answer.split(" ") + extra), extra = 2-3 decoy words, none equal to a word of answer (case-insensitive). CHECK: answer tiles in another order, or with decoys, must NOT form another correct translation; if an alternative order is correct, list it in "accept" (build reads accept).
- type: audio (English sentence the learner hears and types) + meaning (Spanish). Optional accept.
- speak: text + sound in {sh, th, h, v, ed, final, es, rl, general}; the sound must actually occur in the text (general if nothing specific). All words must exist in CMUdict (common words only; write numbers in words; no digits, no symbols).
- shadow: text only (no sound). CMUdict words.
- cloze: text with exactly one "___", answer (the gap), es (full Spanish sentence), accept (other correct fillers for that gap; [] if none).
- write: es, answer, accept = ALL other natural correct translations (synonyms, word order, optional words, AmE variants, he/she if Spanish is ambiguous, "the"/no "the"...). This is the most important field: marking correct English as wrong is the worst bug. Do NOT list pure contraction/digit/punctuation variants (they're normalized; duplicates break the build).
- minimalPair: options = 2 single words (in CMUdict), answer one of them, sentence containing the answer, and "play": "sentence" (the sentence is what sounds). The sentence must make sense with EITHER word or the answer is given away by meaning. Avoid pairs Piper can't voice: three/think/won't/since/van/live. Good: ship/sheep, chair/share, can/can't, walk/work, fifteen/fifty, thirteen/thirty, bit/beat, full/fool.
Numbers/times/prices in answers: write them in words ("seven thirty", "twelve dollars") and add the digit form to accept ONLY when it is not 0-100 plain (e.g. "It's 7:30." and "It costs $12.50." ARE needed as accept because "7:30"/"12.50" are not normalized; "8" alone is). Dates: "May third" answer, accept "May 3rd", "May 3".
Level: B2 (Core Inventory B2, English File Upper-intermediate, Cambridge B2 First). Work, academic and social topics for an adult doctoral student; sentences up to ~22 words; may use all A1-B1 grammar. The learner must feel the jump in level: richer vocabulary, nuance, register. Natural. Don't repeat Spanish prompts already used in other exercises of the same lesson. Recycle vocabulary of earlier units when possible.

## Self-check before finishing
Run: python3 validar_b2.py <your file>   → must print OK. Then re-read every write/cloze/translate/build and ask: "what else would a competent speaker type here?" and add it to accept.

## Extra rules learned in B1 (do not repeat these mistakes)
- If you generate accept lists combinatorially, NEVER insert a space before an apostrophe ("I 'm" is a bug) and check every combination is grammatical English.
- The grader expands 'd → would after a subject pronoun. "I'd left" therefore equals "I would left": do not rely on 'd for "had" in accept; write "I had" forms (the app will later accept both). Contractions NOT expanded by the app (list both forms if natural): must've, should've, would've, could've, hadn't, hasn't, haven't, it's been, noun + 's.
- Use a UNIQUELY named generator script in your scratchpad (other agents share it).
- American English everywhere (First/Second rather than Firstly, "get a master's", "gotten").
