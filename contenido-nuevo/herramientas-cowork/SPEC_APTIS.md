# Aptis prep-track banks for Hablo (read fully, be efficient)
Hablo's "Modo Aptis" = a preparation track per skill, levels A1→B2; the learner (Fero, Colombian adult, needs B1+ in all 4 skills of Aptis ESOL General, British Council) is promoted when 4 of his last 5 tasks at a level are right (Reading/Listening) or 2 of 3 (Speaking, judged by Claude against the rubric, which must cite his words). The teacher voice is Piper (US voice) reading audio text. Aptis is British: its content may use British forms; teach the difference in "why" when relevant (the rest of the course is American).
FILE FORMAT (exactly this; the app parser rejects unknown "tipo"):
{"partes": [ {"id": "parte1", "aptis": "Parte 1 · completar frases", "title": "<Spanish>", "descripcion": "<Spanish: what the real exam part is and how to do it>", "tareas": [ ... ] } ] }
Every task: "id" (unique, prefix given in your task), "level" (A1|A2|B1|B2), "why" (Spanish, 1-2 sentences, shown after answering: why the answer is right / the trap).
READING task types:
- {"tipo":"completar","text":"<sentence with one ___>","options":[3],"answer":"<one option>"} (Aptis R1: choose the word that completes a sentence in a short message).
- {"tipo":"ordenar","instruccion":"Pon las frases en orden. La primera ya está puesta.","primera":"...","desordenadas":[5 sentences, shuffled],"orden":[same 5 in correct order]} (Aptis R2; cohesion clues: pronouns, then/after that, articles a→the).
- {"tipo":"titulos","instruccion":"Cada párrafo lleva un título. Sobra uno.","parrafos":[3-5 paragraphs],"titulos":[paragraphs+1 headings],"answer":[heading for each paragraph, in order]} (Aptis R4).
LISTENING task types (audio = what Piper reads; dialogues as "MAN: ... WOMAN: ..."; numbers IN WORDS; no stage directions):
- {"tipo":"dato","audio":"...","pregunta":"<Spanish question>","options":[3],"answer":"..."} (Aptis L1/L2/L4: specific information, a speaker's opinion or attitude, main idea).
- {"tipo":"quien","audio":"<MAN/WOMAN dialogue>","pregunta":"<¿Quién ...?>","options":["El hombre","La mujer","Los dos"],"answer":"..."} (Aptis L3).
SPEAKING task: {"prep_seg": 0|30|60, "hablar_seg": 30|45|60|120, "prompt_en":"...", "prompt_es":"...", "rubrica":[3-4 Spanish yes/no questions ending in "?"], optional "foto":"<English description of the photo Fero must generate: everyday scene, no text, no brands, no famous people>"}.
Rules: exactly one correct option; distractors plausible and clearly wrong by the text; A1-A2 short and concrete, B1-B2 longer with inference/opinion; no two tasks with the same content; British/American vocabulary difference explained in "why" when it appears; no copyrighted text; English natural. Levels must really match CEFR.
Validate: python3 validar_aptis.py <kind> <file>   (kind = reading|listening|speaking) → OK. Reply one line.
