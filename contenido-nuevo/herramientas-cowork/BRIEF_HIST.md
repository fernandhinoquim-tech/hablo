# Writing short stories for Hablo (read fully)
Hablo teaches AMERICAN English to Fero, a 40-year-old Colombian adult (doctoral student, not a child: stories must be adult, everyday, a little funny or surprising, never childish). The screen: the teacher (TTS, Piper) reads the story sentence by sentence with the text and glossary visible → 3 comprehension questions (3 options) → the learner RETELLS it aloud in parts guided by "pistas" (up to 20 s per pista; scored by how many content words of the story he uses) → glossary words enter his review deck.
Evidence: oral narrative interventions d = 1.36; batches of 3-12 stories are best.

Format: see real examples in ejemplos.txt (copy that exact structure: id, level, title, titleEs, text[6-10 sentences], glosario[2-4 {en, es}], preguntas[3 × {q, options[3], answer}], retell{prompt_es, pistas[3]}, trap). Output file: {"tandas": [{"id": "...", "level": "...", "title": "<Spanish>", "historias": [ ... 6 stories ... ]}]}.

Rules:
- Level control is the whole point. A1: present simple, present continuous, can, there is/are, was/were and simple past of the most common verbs only in the last stories; sentences of 5-10 words; vocabulary from A1 (family, home, food, city, work, days, time, weather, colors, numbers, shopping). A2: past simple + past continuous, going to/will, present perfect, comparatives, have to, should; 7-14 words per sentence.
- One clear mini-plot with a small twist at the end. Characters with Latin American names are welcome; settings can be Colombia or the US.
- AmE spelling and vocabulary (apartment, store, cell phone, check, soccer, downtown, neighbor).
- Every word the teacher reads should be common and pronounceable (the validator warns if a word isn't in CMUdict; fix names/rare words).
- Questions in simple English, one unambiguous right answer; distractors plausible but clearly false according to the text. Mix detail + inference.
- Retell prompt_es in Spanish telling the tense to use (A1 stories in present: "Cuenta la historia en presente"). Pistas: 3 short English cue fragments that follow the story order.
- glosario: 2-4 items that literally appear in the text (exact substring), useful words, Spanish gloss correct for that context.
- trap: one Spanish sentence about the typical Spanish-speaker error that the story helps with (pronunciation or grammar), like the examples.
- Don't repeat titles/plots of existing stories (listed at the end of ejemplos.txt).
Self-check: python3 validar_hist.py <your file> → OK. Reply one line.
