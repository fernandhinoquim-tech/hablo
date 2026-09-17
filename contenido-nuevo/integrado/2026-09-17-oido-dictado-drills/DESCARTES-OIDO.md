# Pares de Oído descartados (medidos con `oir_oido.py`, 17-09-2026)

Criterio: un par pasa con una voz si sus DOS palabras las reconoce el modelo de fonemas (tramo alineado más cerca de la palabra dicha que de su pareja) O Parakeet 0.6B (escribe la palabra); se descarta el que no pasa con al menos 3 de las 4 voces. Es el oído de dos modelos, no de una persona.

**Resultado: 130 de 147 pasan; 15 descartados (quedan 132 pares en 16 bloques).** Dos que el juez marcaba
y NO se descartaron porque era culpa del juez: **there/dare** (Parakeet escribe *their/they're*: homófonos;
ahora compara por pronunciación con CMUdict → 3/4) y **thirteen/thirty** (Parakeet escribe «$13»/«$30» en
cifras; ahora las lee → 4/4).

Notas para reponer:
- **Piper no es determinista del todo**: un par al borde (2/4 ↔ 3/4) puede cambiar entre corridas. Los de 0/4 y 1/4
  son seguros; los de 2/4 son marginales.
- Los dos jueces son **flojos con la d final** (el modelo de fonemas la oye t: *ride→ɹaɪt* 4 de 4) y con b/v; por
  eso el bloque **t/d final se queda con 4 pares** (bet/bed, write/ride, bat/bad, sat/sad) y **u/uu con 5**. El
  mínimo por bloque en la app bajó de 6 a 4 mientras llegan los repuestos.
- Dos bloques perdieron el par de su TÍTULO: «car / card» (oido-final-consonante) y «play / played» (oido-ed);
  el título se quedó igual. Si repones, quizá otro par para el título (o cambiar el título).
- Grace es la voz menos fiable en casi todo (ya sabido); con los otros tres jueces el 3/4 la absorbe.
- **Deletreos del dictado (dict-063 a dict-070):** la «A,» NO se lee como artículo en ninguna voz. Lo que sí: con
  **Grace las letras sueltas no se entienden** con ninguna forma («K, Y, L, E» → "case of white's L. E."; los
  nombres de las letras en palabras tampoco); Sophie 8/8 limpias, Emma y Mia bien. La app lee los deletreos con
  Sophie cuando la profesora es Grace, y lo dice en pantalla.

## oido-dh-d · they / day (7 → 6 pares)
- **loathe / load**: 0/4 (fonemas 0/4, parakeet 0/4) · emma: loathe→lʌv✗/«Now, we say loath again.»✗ load→loʊd✓/«Now we say load again.»✓ | sophie: loathe→loʊz✗/«Now we say load again.»✗ load→loʊd✓/«Now we say load again.»✓ | mia: loathe→loʊ✗/«Now we say log again.»✗ load→loʊd✓/«Now we say load again.»✓ | grace: loathe→lod✗/«Now we say load again.»✗ load→leɪt✗/«Now we say load again.»✓

## oido-ae-uh · cat / cut (10 → 9 pares)
- **hat / hut**: 1/4 (fonemas 0/4, parakeet 0/4) · emma: hat→hæd✓/«Please write hat on the board.»✓ hut→hɑt✗/«Please write HUD on the board.»✗ | sophie: hat→hɑʃ✗/«Please write hat on the board.»✓ hut→hʌʃ✓/«Please write hot on the board.»✗ | mia: hat→hæ✓/«Please write hat on the board.»✓ hut→hɑt✗/«Please write hat on the board.»✗ | grace: hat→hɑt✗/«Please write hat on the board.»✓ hut→hɑt✗/«Please write hat on the board.»✗

## oido-u-uu · full / fool (9 → 5 pares)
- **pulled / pooled**: 2/4 (fonemas 1/4, parakeet 1/4) · emma: pulled→pold✗/«I say polled in English.»✗ pooled→puld✓/«I say pooled in English.»✓ | sophie: pulled→pul✗/«I say pulled in English.»✓ pooled→pud✓/«I say pooled in English.»✓ | mia: pulled→koʊld✓/«I say polled in English.»✗ pooled→tpuld✓/«I say pooled in English.»✓ | grace: pulled→poʊd✗/«I say polled in English.»✗ pooled→koʊld✗/«I say pulled in English.»✗
- **wood / wooed**: 1/4 (fonemas 1/4, parakeet 0/4) · emma: wood→wʊd✓/«Please write wood on board.»✓ wooed→wʊd✗/«Please write wood on the board.»✗ | sophie: wood→wʊd✓/«Please write wood on the board.»✓ wooed→wud✓/«Please write wood on the board.»✗ | mia: wood→wʌt✗/«Please write what on the board.»✗ wooed→wud✓/«Please write wood on the board.»✗ | grace: wood→wɪt✗/«Please write wood on the board.»✓ wooed→eɪt✗/«Please write word on the board.»✗
- **pulling / pooling**: 1/4 (fonemas 1/4, parakeet 1/4) · emma: pulling→pʊɪn✓/«Did you say pulling or not?»✓ pooling→pʊɪ✗/«Did you say pulling or not?»✗ | sophie: pulling→pʊlɪŋ✓/«Did you say pulling or not?»✓ pooling→pulɪŋ✓/«Did you say pooling or not?»✓ | mia: pulling→pɔlɪŋ✗/«Did you say polling or not?»✗ pooling→pulɪŋ✓/«Did you say pooling or not?»✓ | grace: pulling→pɛlʌn✗/«Did he say Pelin Monot?»✗ pooling→kulɪŋ✓/«Did he say cooling or not?»✗
- **hood / who'd**: 2/4 (fonemas 0/4, parakeet 0/4) · emma: hood→hɚt✗/«Now we say hood again.»✓ who'd→hut✓/«Now we say hood again.»✗ | sophie: hood→hud✗/«Now we say, hood again.»✓ who'd→huz✓/«Now we say good again.»✗ | mia: hood→hʊt✓/«Now we say hood again.»✓ who'd→bʊd✗/«Now we say boot again.»✗ | grace: hood→hɛd✗/«Now we say hit again.»✗ who'd→hub✓/«Now we say who again.»✗

## oido-final-t-d · bet / bed (10 → 4 pares)
- **seat / seed**: 1/4 (fonemas 1/4, parakeet 1/4) · emma: seat→sit✓/«Now I say seat for you.»✓ seed→sid✓/«Now I say a seed for you.»✓ | sophie: seat→sit✓/«Now I say seat for you.»✓ seed→sit✗/«Now I say seek for you.»✗ | mia: seat→sik✗/«Now I say seek for you.»✗ seed→si✗/«Now I say see for you.»✗ | grace: seat→si✗/«Now I say seek for you.»✗ seed→siɪ✗/«Now I say seed for you.»✓
- **heart / hard**: 2/4 (fonemas 2/4, parakeet 2/4) · emma: heart→hɑt✓/«You will hear heart five times.»✓ hard→hɑɪd✓/«You will hear hard five times.»✓ | sophie: heart→hɑɚt✓/«You will hear heart five times.»✓ hard→hɑɹd✓/«You will hear hard five times.»✓ | mia: heart→hɑɹd✗/«You will hear harp five times.»✗ hard→hɑɹd✓/«you will hear hard five times.»✓ | grace: heart→hɑ✗/«You will hear harp five times.»✗ hard→hɑd✓/«Your hair hard five times.»✓
- **neat / need**: 2/4 (fonemas 1/4, parakeet 2/4) · emma: neat→nid✗/«Now I say meet once more.»✗ need→nid✓/«Now I say need once more.»✓ | sophie: neat→niʃ✗/«Now I say neat once more.»✓ need→nidʒ✓/«Now I say need once more.»✓ | mia: neat→nit✓/«Now I say neat once more.»✓ need→nid✓/«Now I say need once more.»✓ | grace: neat→ni✗/«Now I say meet once more.»✗ need→nid✓/«Now I say Neib once more,»✗
- **white / wide**: 2/4 (fonemas 2/4, parakeet 2/4) · emma: white→waɪt✓/«Say white for me, please.»✓ wide→waɪd✓/«Say wide for me please.»✓ | sophie: white→hwaɪt✓/«Say white for me please.»✓ wide→waɪd✓/«Say wide for me please.»✓ | mia: white→waɪt✓/«Say white for me, please.»✓ wide→waɪ✗/«Say why for me, please.»✗ | grace: white→waɪt✓/«Say white for me please.»✓ wide→waɪɪ✗/«Say word for me please.»✗
- **coat / code**: 2/4 (fonemas 1/4, parakeet 1/4) · emma: coat→khoʊt✓/«Please say code one more time.»✗ code→koʊd✓/«Please say code one more time.»✓ | sophie: coat→koʊs✗/«Please say code one more time.»✗ code→kod✓/«Please say code one more time.»✓ | mia: coat→koʊ✗/«please say code one more time.»✗ code→koʊ✗/«Please say code one more time.»✓ | grace: coat→keɪ✗/«Please say coat one more time.»✓ code→keɪd✓/«Please say code one more time.»✓
- **feet / feed**: 1/4 (fonemas 1/4, parakeet 1/4) · emma: feet→fɪt✓/«Now I save feet for you.»✓ feed→fid✓/«Now I say feed for you.»✓ | sophie: feet→feɪt✓/«Now I say feet for you.»✓ feed→fɪt✗/«Now I say feet for you.»✗ | mia: feet→feɪt✓/«Now I save it for you.»✗ feed→fin✗/«Now I save them for you.»✗ | grace: feet→θi✗/«Now I safe for you.»✗ feed→fit✗/«Now I say feed for you.»✓

## oido-final-consonante · car / card (10 → 9 pares)
- **car / card**: 2/4 (fonemas 2/4, parakeet 2/4) · emma: car→khɚ✓/«Now I say car again.»✓ card→khɑt✗/«Now I say cut again.»✗ | sophie: car→kɑɹ✓/«Now I say car again.»✓ card→kɑɹɚd͡ʒ✓/«Now I say card again.»✓ | mia: car→kɔɹ✓/«Now I say car again.»✓ card→kɔɹd✓/«Now I say card again.»✓ | grace: car→kɔɹ✓/«Now I say carried then.»✗ card→kɑd✗/«Now I say Carla Den.»✗

## oido-ed · play / played (10 → 9 pares)
- **play / played**: 1/4 (fonemas 1/4, parakeet 1/4) · emma: play→pleɪ✓/«Now, I say play again.»✓ played→pleɪt✗/«Now I say play it again.»✗ | sophie: play→peɪ✓/«Now I say play again.»✓ played→pleɪd✓/«Now, I say played again.»✓ | mia: play→pleɪ✓/«Now I say play again.»✓ played→pleɪt✗/«Now I say play it again.»✗ | grace: play→pleɪ✓/«Now I say play again.»✓ played→pueɪt✗/«Now I say prayed again.»✗

## oido-s-z · bus / buzz (10 → 9 pares)
- **sue / zoo**: 2/4 (fonemas 2/4, parakeet 1/4) · emma: sue→su✓/«Now I say Sue again.»✓ zoo→su✗/«Now I say so again.»✗ | sophie: sue→su✓/«Now I say so again.»✗ zoo→zu✓/«Now I say zoo again.»✓ | mia: sue→su✓/«Now I say sue again.»✓ zoo→zu✓/«Now I say zoo again.»✓ | grace: sue→su✓/«Now I say Sudan.»✗ zoo→su✗/«Now I say to you again.»✗

