# Auditoría de A1 y A2 — 17-09-2026 (Cowork)

Pedido de Fero: *"revisa a fondo todo el contenido, creo que faltan cosas en A1 o A2… necesito que mi app quede completa y abarque todo, que sea didáctica"*.

Lo que se hizo:
- **Inventario** de las 56 lecciones, los 565 ejercicios, los 41 bancos de vocabulario, las 12 historias, los 17 escenarios y los 40 drills.
- **Comparación** contra el Core Inventory del British Council/EAQUALS, English Profile, Cambridge Empower A1/A2, English File Pre-intermediate y las apps grandes.
- **Revisión adversarial** de los 565 ejercicios, uno por uno (cuatro revisores; los informes completos están en `revision-*.md`).
- **Verificación en el código** de lo que la app hace con esos datos.

---

## En cinco líneas

1. **Fero tiene razón: faltan cosas, y son de las básicas.** Hablo tiene lo que más cuesta (tiempos verbales, modales, condicionales, pasiva), pero no enseña las bases de cualquier A1:
   - el alfabeto, los números grandes, la hora, las fechas, los meses y los colores;
   - el imperativo y las direcciones;
   - los pronombres de objeto (*me, him, them*);
   - *their*, que no aparece ni una vez en todo el curso.
2. **El vocabulario está al 38 % de la meta de A2.** Hay unas 580 palabras distintas entre lecciones y bancos; English Profile pone A2 en ≈ 1.526.
3. **La revisión encontró ≈ 220 defectos**, 55-60 de ellos graves: la mayoría marcan mal inglés correcto (el peor fallo según la regla de la casa). Hay tres problemas de código detrás de muchos de ellos.
4. **La teoría sí está y sí se ve** (la ficha 📖 al abrir cada lección, desde el 16-09). Faltan dos cosas: una **biblioteca de gramática** para consultarla fuera de las lecciones y el botón **"¿Por qué?"** al fallar. Unas 35 fichas tienen alguna afirmación falsa o exagerada.
5. **De las actividades prometidas faltan seis**: crucigramas, pantalla de Oído, doblar la escena, dictogloss, dictado de números y fechas, y vocabulario con icono.

---

## 1. Cuánto hay contra cuánto debería haber

| | Hoy | Referencia | Estado |
|---|---|---|---|
| Lecciones A1 | 27 (300 ejercicios) | Empower A1: 12 unidades × ~4 lecciones | faltan los temas básicos (sección 2) |
| Lecciones A2 | 29 (265 ejercicios) | Empower A2: 12 unidades × ~4 lecciones | faltan 12-14 temas; a cambio, trae 3 de B1 (sección 3) |
| Palabras A1-A2 (lemas distintos, lecciones + bancos) | **≈ 580** | EVP: A1 = 601, A2 = **1.526** acumuladas (Capel 2010) | **38 %** |
| Palabras del top-1000 más frecuente del inglés | 33 % | — | medido con `wordfreq` |
| Historias | 0 A1 · 6 A2 · 6 B1 | tandas de 3-12 | **A1 no tiene ninguna** |
| Escenarios | 5 A1 · 6 A2 · 6 B1 | Duolingo Adventures: café, tienda, direcciones… | bien encaminado |
| Drills de pronunciación | 40 (`rl` tiene 1) | Empower: acento de palabra, formas débiles, -s final, entonación | faltan temas (sección 4) |

**Temas de vocabulario ausentes o casi ausentes** (búsqueda palabra por palabra en lecciones y bancos):

| Tema | Hay | Faltan (muestra) |
|---|---|---|
| **Meses** | 1 de 12 (y es *may*, el verbo) | todos |
| **Colores** | 3 de 11 | blue, green, yellow, black, brown… |
| **Números** | 9 de 22 | nine a twelve, thirteen, fifteen, thirty, fifty, hundred, thousand, **first/second/third** |
| **La hora** | *o'clock* solo en una alternativa aceptada | half past, quarter, noon, *What time…?* |
| **Estaciones y clima** | 4 de 12 | spring, summer, fall, winter, sunny, cloudy |
| **Animales** | 2 de 6 | dog, bird, horse |
| **Países y nacionalidades** | 3 de 11 · 3 de 7 | United States, Canada, Mexican, American… |
| **Describir personas** | 2 de 11 | young, short, friendly, funny, shy, smart |
| **Posesivos** | 5 de 10 | **our, their**, mine, ours, theirs |
| **Deportes y ocio** | 6 de 10 | sport, soccer, dance, game |

Sí están bien cubiertos: días, familia, casa, comida, cuerpo y ciudad.

---

## 2. Huecos de A1: gramática y situaciones

Referencia: Core Inventory A1 y Empower A1. **P1** = lo usa todo el mundo desde el primer día.

| # | Lección que falta | Por qué | Prioridad |
|---|---|---|---|
| 1 | **El alfabeto y deletrear** (*How do you spell it?*; a/e/i, g/j) | Situación A1 básica. Las letras a/e/i son la trampa del hispanohablante. | P1 |
| 2 | **Números 0-100, teléfono y edad** (*thirteen/thirty*) | Solo hay números sueltos. Además es tarea literal de Aptis Listening 1. | P1 |
| 3 | **Precios** (*How much is it? It's $12.50*) | No aparecen ni *price* ni *dollars*. | P1 |
| 4 | **La hora y el horario** (*What time is it? at 7:30, half past*) | No existe. | P1 |
| 5 | **Fechas: meses, ordinales, cumpleaños** (*on May 3rd, in June*) | No existe. | P1 |
| 6 | **Colores y el adjetivo antes del sustantivo** (*a red car*; nunca *reds*) | Trampa clásica del español que no se enseña en ninguna parte. | P1 |
| 7 | **to be en negativo y preguntas, con respuesta corta** (*Are you…? Yes, I am*) | Hoy solo aparece de pasada. | P1 |
| 8 | **have/has y *Do you have…?*** | El error *he have* no se enseña (lo vio el revisor). | P1 |
| 9 | **me, him, her, us, them + our, their** | *their* no aparece ni una vez en el curso. | P1 |
| 10 | **Órdenes y cómo llegar** (imperativo, *turn left, go straight, don't…*) | Solo existe el escenario "camino", sin lección que lo prepare. | P1 |
| 11 | **Gustos: like/love/hate + -ing, *Do you like…?*** | *hate* no aparece. | P2 |
| 12 | **Las palabras de pregunta** (*which, whose, how often, what time*) | *which* y *whose* no se enseñan. | P2 |
| 13 | **¿Adónde fuiste? Preguntas en pasado + *there was/were*** | No hay ni un *Where did…?* ni un *there was*. | P2 |
| 14 | **Invitar y proponer** (*Let's…, Would you like to…?, Sorry, I can't*) | *Let's* no aparece. | P2 |
| 15 | **El clima y las estaciones** (*It's sunny, in summer*) | Solo existe el banco. | P2 |
| 16 | **Describir a alguien** (*He's tall, she has long hair*) | No existe. | P2 |

**Huecos dentro de las lecciones que ya existen** (detalle en los informes de revisión):
- *-es* y *has* en tercera persona.
- Respuestas cortas (*Yes, I do*).
- Negativo y preguntas del presente continuo.
- *a few / a little* (están en la teoría y nunca se practican).
- Superlativos en A1 (solo aparecen en un listen).
- *will* contra *going to* (la teoría los contrasta y ningún ejercicio lo hace).

---

## 3. Huecos de A2

| # | Lección que falta | Por qué | Prioridad |
|---|---|---|---|
| 1 | **Present perfect con for/since + *How long…?*** | a2u2l4 lo **evalúa** en tres ejercicios sin haberlo enseñado. | P1 |
| 2 | **few / a few / little / a little** | Core Inventory A2. | P1 |
| 3 | **mine, yours, whose** | No existe. | P1 |
| 4 | **¿Quién llamó? / ¿A quién llamaste?** (preguntas de sujeto y de objeto) | Error típico: *Who did call you?* | P1 |
| 5 | **bored o boring** (-ed/-ing) | *I am boring* es de los errores más frecuentes del hispanohablante. No existe. | P1 |
| 6 | **Palabras que piden preposición** (*interested in, afraid of, depend on, listen to*) | Hoy va disperso, sin lección propia. | P1 |
| 7 | **get** (*get up, get home, get a job, get tired*) | English File lo trata aparte. No existe. | P2 |
| 8 | **could / was able to, y pedir permiso** (*Can/Could/May I…?*) | *couldn't* no aparece. | P1 |
| 9 | **Proponer, invitar, aceptar y rechazar** (*How about…?, Why don't we…?, I'd love to, but…*) | Función A2 del Core Inventory. | P2 |
| 10 | **Describir personas: *look like* / *be like* / carácter** | Función A2. No existe. | P2 |
| 11 | **Por teléfono** (*Can I speak to…? Can I take a message?*) | Situación de Empower y de English File. | P2 |
| 12 | **En el médico y la farmacia** (*I have a headache, It hurts*) | Solo hay escenario; *headache* no aparece. | P2 |
| 13 | **Conectar ideas: *although, however, first… then… finally*** | Core Inventory A2. Aptis los mide en Writing. | P2 |
| 14 | **myself, each other** + **condicional cero** (*If you heat ice, it melts*) | No existen. | P3 |

**Al revés:** A2 ya trae tres temas que el Core Inventory pone en **B1**: segundo condicional, pasiva y estilo indirecto. No se quitan (le sirven a Aptis), pero conviene saber que A2 está cargado arriba y flojo abajo.

---

## 4. Pronunciación

Los 40 drills cubren los sonidos insignia (sh, th, h, v, -ed, e+s). Faltan temas que Empower enseña en A1-A2:
- **acento de palabra**: *thirTEEN* / *THIRty*, *PHOtograph*;
- **formas débiles**: *can/can't*, *to*, *do you*;
- **la -s final /s z ɪz/**: *works, plays, watches*;
- **entonación de preguntas**;
- **vocal larga contra corta** (ship/sheep): solo existe como par mínimo;
- **r/l**: tiene 1 drill.

---

## 5. Lo que encontró la revisión adversarial

Hay **≈ 220 hallazgos en 565 ejercicios**: ~60 ALTO, ~100 MEDIO y ~65 BAJO. Algunos ALTO eran de cifras, que la app ya normaliza, y se descuentan. Los informes completos, con el arreglo exacto de cada uno, están en:
- `revision-a1u1-u5.md`
- `revision-a1u6-u9.md`
- `revision-a2u1-u5.md`
- `revision-a2u6-u10.md`

### Por tipo

- **Marca mal inglés correcto (el grueso).** Faltan alternativas en `accept`: *in here*, *ride the bus*, *switch it off*, *get milk*, *Don't quit*, *He read the newspaper while…*, *till*, *I'd like a cup of tea*… Solo en A2 son unos 40 de los 113 ejercicios de producción.
- **Opciones "incorrectas" que son correctas**:
  - *I have work on Saturday* (a2u3l1e1)
  - *It has two bedrooms* (a1u5l1e1)
  - *That's everything* (a1u2l2e2)
  - *He speaks very good English too* (a2u7l1e1)
- **Listen donde las opciones suenan igual**: *call me* / *called me*, *like the* / *liked the*, *play* / *played football*, *fix* / *fixed that*. El oído no puede distinguirlas.
- **La teoría se contradice o exagera**:
  - «el sujeto **nunca** se calla»
  - «in para lugares, **of para grupos**» (y la misma lección usa *in the class*)
  - «**solo** did para todos» (olvida was/were)
  - «**nunca** van en continuo»
  - «far → further» (en EE. UU. es *farther*)
  - «wait **siempre** con for»
  - «ever/never/just piden present perfect» (en EE. UU. se dice *I just ate*)
- **Pistas de pronunciación que enseñan el error**: «zank iu» se lee con *s*; «siiip» por *sheep*; «hava» con h muda.
- **Se contradice consigo mismo**: a1u6l2e2 enseña *I want a coffee* como correcta, y a1u2l1 y a1u7l2 dicen que suena brusca.
- **Británico en un curso americano**: *shop*, *football*, *bill* como respuesta principal, *look after*, *do exercise*.
- **Ejercicios duplicados**: a1u3l2e2 = a1u4l1e2; «¿Hablas inglés?» aparece tres veces.
- **Pendientes del 15-09 que siguen abiertos**: a2u6l3e5 («en general»), a2u9l1e5, a2u1l3e5 y a2u9l2e4. Los cuatro reaparecieron en la revisión.

### Tres problemas de código detrás de muchos hallazgos (para Claude Code)

Verificados en el código el 17-09.

1. **`build` no lee `accept`.** `BuildSentence` no tiene ese campo, así que los `accept` de a2u7l3e6 y a2u10l3e6 se ignoran.
   - Tres ejercicios arman otra frase correcta **con sus propias fichas** (el otro orden de las cláusulas), y eso solo se arregla en código: a2u2l4e6, a2u4l3e7 y a2u5l1e6.
   - Los demás se arreglan cambiando el señuelo.
2. **Los `translate`, `build` y `type` se convierten en "escribir" sin alternativas.**
   - Pasa en el mazo (`Repaso.ejercicioDe`, escalón 2), en *Aguanta* y en *adivina antes de ver* (`Repaso.kt:166-170`).
   - Solo `write` y `cloze` pasan su `accept`; `translate` no tiene el campo.
   - Resultado: en el mazo, *I'm 25*, *I'm home* o *That's it* se marcan mal.
   - Arreglo: añadir `accept` opcional a `translate`, `build` y `type` (parser + `checkContent`) y pasarlo en esas tres conversiones. Cowork llena los `accept`.
3. **`'s` después de un sustantivo no se expande.**
   - *The phone's ringing* no es igual a *The phone is ringing* (`Correccion.suelta`, decisión del 16-09).
   - Tampoco se normalizan *o'clock* ni los años (*2020* / *twenty twenty*).
   - No hace falta cambiar la regla: basta con que Cowork ponga esos casos en `accept`.

---

## 6. La teoría: qué hay y qué falta

- **Hay:** 56 fichas (título + cuerpo de ~75 palabras + trampa del hispanohablante). Se muestran al abrir la lección (después de *adivina antes de ver*) y con el 📖 de la barra (`Ficha.kt`, 16-09). 533 de los 565 ejercicios traen una pista (`tip`) que sale al corregir.
- **Falta 1: una sección "Gramática"** en el inicio, para consultar todas las fichas sin entrar a una lección. Hoy, para releer *used to* hay que abrir la lección.
- **Falta 2: el botón "¿Por qué?"** al fallar, que abra la ficha de esa lección. Estaba en el plan del 13-09 y no se construyó. Con corrección explicada g = 0,73; sin ella, 0,39 (Rowland 2014).
- **Falta 3: corregir unas 35 fichas** con errores o exageraciones (sección 5).
- **Falta 4: fichas "de referencia"** que ninguna lección tiene hoy: verbos irregulares (tabla), pronombres (tabla completa), números y fechas, preposiciones de tiempo (*in/on/at*).

---

## 7. Actividades prometidas: estado real

| Actividad | Evidencia | Estado | Quién |
|---|---|---|---|
| **Crucigramas** (5-8 palabras, pistas en español, sin reloj) | recuperación productiva d = 1,38 | **no existe** | Cowork genera las rejillas; Code las dibuja |
| **Pantalla de Oído** (pares mínimos, 4 voces por bloque) | g = 0,92-0,98 | el tipo existe, **la pantalla no** | Code; Cowork los pares |
| **Doblar la escena** (diálogo por papeles) | shadowing: fluidez en 8 de 8 estudios | **no existe** | Code; Cowork los diálogos |
| **Dictogloss** (reconstruir el texto) | producción + corrección | **no existe** | Code; Cowork los textos |
| **Dictado de números, horas y fechas** | tarea literal de Aptis L1 | **no existe** | Code; Cowork las listas |
| **Ordenar el diálogo o la historia** | narrativa d = 1,36 | solo en Aptis Reading | Code puede reusar la de Aptis |
| **Escribir un mensaje corto** | tapa el hueco de escritura | solo en la pista de Writing de Aptis | Code; Cowork las consignas A1-A2 |
| **Vocabulario con icono + español** | g = 0,33 sobre cualquiera de los dos solos | **no existe** | Cowork elige las ~700 palabras concretas |
| **Anagramas** | calentamiento | **no existe** | baja prioridad |
| **Motor de reglas de escritura** (Fase 4) | las reglas no alucinan | **no existe** | después |

Ya construido y funcionando: historias con retell, mazo Leitner, contrarreloj, Aguanta, corrige tu error, adivina antes de ver, Parejas, charla libre con memoria, escenarios por nivel y Modo Aptis (pista de Writing y Core).

---

## 8. Contra las apps grandes

| | Duolingo | Babbel | Busuu | **Hablo** |
|---|---|---|---|---|
| Lecciones cortas por situación | ✓ | ✓ (5-10 min) | ✓ | ✓ |
| Explicación de gramática | poca | pop-ups | ✓ | **✓ ficha + trampa del hispanohablante** (mejor que las tres) |
| Repaso espaciado | ✓ | ✓ | ✓ | ✓ |
| Hablar con reconocimiento | ✓ | ✓ | ✓ | ✓ y además **por fonema (GOP)** |
| Conversación con IA | Video Call (Max) | Babbel Speak | AI Conversations | ✓ (Claude) con memoria |
| Rol en situaciones | Adventures | diálogos | ✓ | escenarios (17) |
| Historias | Stories | — | — | ✓ con retell (**falta A1**) |
| Escucha larga | DuoRadio | podcasts | — | **no** (dictogloss y Oído lo cubrirían) |
| Imágenes en vocabulario | ✓ | ✓ | ✓ | **no** |
| Volumen de contenido | muy alto | alto | alto | **bajo: 38 % del vocabulario de A2** |
| Crucigramas | no | no | no | pendiente (sería diferencial) |

Fuentes: blog de Duolingo (product highlights, Adventures, speaking), babbel.com/how-babbel-works, busuu.com (courses, conversations). Ninguna de las tres publica su temario por unidades; la comparación de temario va contra Cambridge y British Council.

**La conclusión:** en *cómo* enseña, Hablo ya está a la altura o por encima: la trampa del hispanohablante, el GOP y la memoria no las tiene ninguna. Donde pierde es en **cuánto** enseña.

---

## 9. Plan para dejarlo completo (en orden)

Regla de la casa: primero que no enseñe nada incorrecto, después ampliar.

| Paso | Qué | Quién | Tamaño |
|---|---|---|---|
| **1** | **Parche de correcciones**: los ≈ 220 hallazgos en formato `parchar.py` (accept, opciones, señuelos, pistas y ~35 fichas) | Cowork escribe, verifica e integra en la bandeja; Code aplica | 1 sesión |
| **2** | Tres cambios de código: `accept` en translate/build/type y pasarlo al mazo, Aguanta y adivinanzas | Code | pequeño |
| **3** | **16 lecciones nuevas de A1** (sección 2) + corrección de los huecos internos | Cowork | ~170 ejercicios |
| **4** | **Biblioteca "Gramática" + botón "¿Por qué?"** + 4 fichas de referencia | Code la pantalla; Cowork las fichas | pequeño |
| **5** | **14 lecciones nuevas de A2** (sección 3) | Cowork | ~140 ejercicios |
| **6** | **Vocabulario hasta ~1.500 palabras**: 12 bancos nuevos (colores, números y precios, meses y fechas, animales, deportes y ocio, países y nacionalidades, describir personas, clima y estaciones, síntomas, tallas y compras, transporte, quehaceres) y ampliar los existentes | Cowork | ~900 parejas |
| **7** | **Historias A1** (tanda de 6) + segunda tanda A2 | Cowork | 12 historias |
| **8** | Actividades: crucigramas → Oído → dictado de números → doblar la escena → dictogloss → mensaje corto | Code construye; Cowork los datos | por etapas |
| **9** | Drills nuevos: acento de palabra, formas débiles, -s final, entonación, r/l | Cowork | ~25 drills |
| **10** | Pistas de Aptis que faltan (Reading, Listening, Speaking), y después B1 y B2 | Cowork | lo que ya estaba |

**Dónde van las lecciones nuevas:** mejor **intercaladas** donde corresponden (el alfabeto y los números antes de a1u3, la hora con las rutinas de a1u4), no al final. Así lo hacen Empower y English File. Cómo encadenarlas lo decide Claude Code con `anexar_nivel.py`; como los ids nuevos no chocan con los viejos, el progreso guardado no se pierde.

---

## Fuentes

- British Council / EAQUALS, *Core Inventory for General English*, 2.ª ed. 2015 — https://www.teachingenglish.org.uk/sites/teacheng/files/pub-british-council-eaquals-core-inventoryv2.pdf
- Cambridge, *American Empower Starter A1* (front matter) — https://assets.cambridge.org/97811088/18131/frontmatter/9781108818131_frontmatter.pdf
- Cambridge, *Empower Elementary A2* (contents) — https://assets.cambridge.org/97811089/65262/toc/9781108965262_toc.pdf
- *English File Pre-intermediate* + *Empower A2* (syllabus) — https://www.galwaylanguage.com/wp-content/uploads/2025/07/English-File-Empower-Syllabus-A2-.pdf
- Capel (2010), *A1–B2 vocabulary: insights and issues arising from the English Profile Wordlists project*, English Profile Journal 1(1) — A1 = 601, A2 = +925
- Cambridge, *A2 Key vocabulary list* (temas) — https://www.cambridgeenglish.org/images/506886-a2-key-2020-vocabulary-list.pdf
- Duolingo — https://blog.duolingo.com/product-highlights/ · https://blog.duolingo.com/adventures/
- Babbel — https://www.babbel.com/how-babbel-works
- Busuu — https://www.busuu.com/en/it-works/courses
- Rowland (2014), *Psychological Bulletin* 140 — g = 0,73 con corrección
- Cobertura de frecuencia: paquete `wordfreq` (lista combinada del inglés), lematizado con `lemminflect`. Es un indicador, no una lista oficial.
