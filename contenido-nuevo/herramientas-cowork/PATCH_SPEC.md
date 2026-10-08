# Patch spec (write your fixes as a JSON patch)

Write ONE file: parche-<RANGE>.json (RANGE given in the message). UTF-8, valid JSON.
Shape:
{
  "_comentario": "Parche de la auditoría 17-09, unidades ...",
  "<exercise id>": {"_por_que": "one short Spanish sentence", "<field>": <new value>, ...},
  "<lesson id>":   {"_por_que": "...", "theory": {"title": "...", "body": "...", "trap": "..."}}
}
Rules (the app's real behaviour, verified in code):
1. Every field REPLACES the old one entirely. `accept` = the COMPLETE new list (old entries you keep + new ones). `options` = full list. `theory` = full object (copy the current text and edit only what's needed; keep the **negrita**/*cursiva* style and \n\n paragraphs).
2. Allowed exercise fields: es, text, audio, sound, options, answer, accept, extra, meaning, sentence, play, tip. Never change id or type. If you change a listen `answer`, also set `audio` to the same string.
3. Grading = case/punctuation/accents/hyphens ignored; digits 0-100 == words ("8" == "eight", "25" == "twenty-five"); these contractions == long forms: I'm, you're, we're, they're, isn't, aren't, wasn't, weren't, don't, doesn't, didn't, can't/cannot (=can not), couldn't, won't, wouldn't, shouldn't, I'll…they'll, I've/you've/we've/they've, let's. `'s`=is and `'d`=would ONLY after he/she/it/you/we/they/what/who/where/when/how/why/that/there/here (and 'd after I). Not after nouns ("The phone's" ≠ "The phone is"). NOT normalized: years (2020), "o'clock", times like 8:00.
   => Do NOT add accept entries that only differ by the above (the app rejects duplicates at build time: 'accept repite la respuesta'). DO add noun+'s variants, years, o'clock if natural.
4. Digit-only accept entries: never.
5. build: the tiles are answer.split(" ") + extra. `extra` must not contain any word already in answer (case-insensitive). Prefer fixing by changing a decoy. You MAY add `accept` to a build (the app will read it after a code change): each accept must be buildable from the same tiles.
6. translate: you MAY add `accept` (used when the review deck turns the item into free writing). Never put a distractor option into accept. Add accept to translate items in your range whenever a natural alternative translation exists (this is the cross-cutting ALTO).
7. speak/shadow/minimalPair words must exist in CMUdict (common words are fine; avoid rare names; "Bogota" is fine only if already used).
8. American English. Spanish text correct (tildes, ¿¡). Tips short.
9. Include every ALTO and MEDIO from your report unless you now judge it wrong (then list it in "_descartados" with the reason). Include BAJO when the fix is safe. Things that need code or audio measurement go to "_pendientes".
10. Don't invent new exercises or lessons (gaps are handled later). Replacing the content of an existing exercise (same id/type) to fix a duplicate or off-topic item is OK.
11. After writing, run:  python3 validar_parche.py parche-<RANGE>.json   and fix until it prints OK.
