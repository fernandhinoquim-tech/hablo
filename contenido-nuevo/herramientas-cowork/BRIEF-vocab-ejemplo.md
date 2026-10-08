# Writing vocabulary banks for Hablo (read fully)

Hablo teaches AMERICAN English to Fero (adult Colombian). The banks feed a timed matching game ("contrarreloj": tap English↔Spanish pairs, rounds of up to 8 pairs from ONE bank), the review deck and future crosswords. Goal of this batch: add B2 vocabulary: English Vocabulary Profile adds ~1,710 words at B2. All banks in this batch are level B2, ids '<tema>-b2' (check existing ids in existentes.txt).

## Format (one file, path in your task)
{"bancos": [ {"id": "animales-a1", "title": "🐶 Animales · A1", "level": "A1", "pares": [{"en": "dog", "es": "perro"}, ...]} ]}
- id: lowercase, "<tema>-<a1|a2>"; must not exist already (see existentes.txt headers). If the theme exists (casa, comida, cuerpo, trabajo, ciudad, viajes, tiempo, gente, ropa, verbos1, verbos2, adjetivos, sentimientos, estudio) use a new name like "casa2-a2".
- title: one emoji + Spanish name + " · B2".
- 20-25 pairs per bank (the validator allows 6-25).

## The rules that matter (a matching game fails if TWO answers are valid at once)
1. **Global uniqueness:** no English word/phrase and no Spanish word/phrase may repeat ANYWHERE in the whole vocabulary (existing banks in existentes.txt + all new banks). If a Spanish word has two meanings, disambiguate in parentheses: "tiempo (clima)", "banco (para sentarse)", "inglés (idioma)".
2. **Inside a bank:** no two near-synonyms in English (store/shop, big/large, look/watch/see…) and no two Spanish glosses that could both fit the same English word. Each Spanish gloss must point to exactly ONE of the English words in that bank.
3. English: American (apartment, cell phone, pants, fall, soccer, gas, trash, check, elevator, cookie, vacation, subway, zip code, drugstore). Every English word must be in CMUdict (common words are; avoid brand names, rare compounds; two-word items like "post office" are fine).
4. Spanish: Colombian-neutral everyday words (carro, celular, nevera, computador, bus, plata only if noted as coloquial — prefer "dinero"), with correct tildes. Nouns without article unless needed ("la cuenta" is fine to disambiguate). Verbs in infinitive (en: "to"-less base form: "cook" = "cocinar").
5. Level: B2 (B2 First level): precise, less frequent but useful words for an adult professional/academic. Prefer high-frequency, everyday, useful words. Use candidatos_b2.txt (word, estimated CEFR, frequency; words NOT yet in the course) as your main source — pick the useful ones for your themes; skip proper nouns, politics/war/crime-heavy, slang, offensive words.
6. One sense per item; if the English word is ambiguous (e.g. "fair", "match"), give the Spanish of the sense you mean and make sure no other item in the bank also fits it.
7. Collocations (A2) are welcome in an "expresiones" bank: "make a mistake" = "cometer un error".

## Self-check
Run: python3 validar_b2v.py <your file>   (must print OK). It checks global duplicates only against EXISTING banks and your own file; other writers work in parallel on other themes, so stick to your themes.
Reply with one line: banks and pair counts.
