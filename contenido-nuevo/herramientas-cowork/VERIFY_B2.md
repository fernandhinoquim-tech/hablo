# Adversarial verification of NEW B2 content (Hablo)

Read BRIEF_B2.md (what the writers were told) and REVIEW_BRIEF.md (exercise types and grading). Past reviews ALWAYS found many real defects; find them. NOTE: accept lists were generated combinatorially — scan them for WRONG English (e.g. 'If I would have known', wrong tense combos): a wrong accept entry is ALTO (it tells the learner wrong English is right). Also note the grader expands I'd→I would, so don't add 'I'd' variants that collapse to wrong forms.

For every lesson/exercise in your file check:
1. ALTO: a correct, natural English answer that the app would reject (missing accept in write/cloze/translate/build; build tiles that reorder or combine with decoys into another correct sentence not in accept); a translate/listen with two correct options; wrong English taught anywhere (answers, audio, theory examples, accept entries that are wrong English); false statements in theory/tips.
2. MEDIO: listen distractors not audibly distinct for TTS; ambiguous Spanish (tú/usted, he/she, tense) whose answer is unguessable and not covered by accept; content using grammar not yet taught at that point of the course (see indice_a1_b1.txt for order; the new unit is inserted where stated); British forms in an American course; minimalPair whose sentence gives away the answer by meaning, or pairs TTS can't voice; speak `sound` not present in text; Spanish errors.
3. BAJO: unnatural phrasing, weak tips, duplicates.
Also judge each theory card: is it complete for the topic at B2 (and clearly above B1), true, clear for a Colombian learner?

Then APPLY your fixes directly in the source JSON (path given in your task); you are the only one editing it. Keep ids consecutive. After editing run the validator command given in your task until OK.
Final reply (be brief to save resources): counts ALTO/MEDIO/BAJO fixed and at most 6 lines with the most important fixes.
