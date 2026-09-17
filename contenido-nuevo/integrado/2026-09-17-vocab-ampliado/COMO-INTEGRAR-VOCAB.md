# Vocabulario A1-A2 ampliado (Cowork, 17-09-2026)

Paso 6 del plan de la auditoría: subir el vocabulario de A1-A2 hacia las ~1.526 palabras que English Profile pone para A2.

**Qué trae:**
- **43 bancos nuevos, 971 parejas** (`vocabulario-ampliado.json`): 12 de A1 y 31 de A2. Temas:
  - Personas y seres vivos: animales, países y nacionalidades, cómo es una persona, partes del cuerpo, profesiones, personalidad, familia y relaciones, salud.
  - Tiempo y medidas: colores, números, ordinales, meses y estaciones, frecuencia, expresiones de tiempo, materiales y medidas, pronombres y cantidades.
  - Cosas y lugares: accesorios, frutas y verduras, cocina, quehaceres, dinero, compras, servicios, transporte, hotel, restaurante, ciudad, clima, naturaleza, viajes.
  - Actividades: deportes, ocio, tecnología y medios, estudio, trabajo.
  - Gramaticales: verbos (tres bancos), adjetivos (dos bancos), sustantivos abstractos y colocaciones con make/do/take/have/get.
- **13 arreglos a bancos que ya existen** (`parche-vocab.json`):
  - Británico → americano: trousers → pants, neighbour → neighbor, neighbourhood → neighborhood, tap → faucet, bill → check, mark → grade, hairdresser → hair salon.
  - España → Colombia y ajustes de sentido: calcetín → media, comisaría → estación de policía, move = mudanza → mudarse (el inglés es verbo).
  - Choques con los bancos nuevos: pavement, car park y town hall pasan a curb, prison y courthouse.
- **Total después de integrar: 84 bancos, 1.339 parejas.**
- **Cobertura A1-A2:** lecciones y bancos juntos suman **≈ 1.480 palabras distintas**, contra ~580 antes de la auditoría y ~1.526 que es la meta de English Profile.

**Verificado:**
1. Ningún inglés ni español se repite en todo `vocabulario.json`, viejo más nuevo. Cuando una palabra española tiene dos sentidos, lleva la aclaración entre paréntesis: «tiempo (clima)», «inglés (idioma)», «reserva (de hotel)».
2. Dentro de cada banco no conviven casi sinónimos, así que en una ronda nunca hay dos respuestas válidas.
3. Todo el inglés está en CMUdict.
4. Seis redactores y tres revisores adversariales: encontraron 33 defectos y todos están corregidos. Hubo dos ALTO de "dos respuestas a la vez" (rebajas/descuento, todo/whole), sinónimos, británico y niveles mal puestos.

## Orden para integrar

1. **Primero el parche** (si no, `anexar_vocabulario.py` choca con parejas repetidas):
   `python contenido-nuevo/vocab-ampliado/parchar_vocab.py app/src/main/assets/content/vocabulario.json contenido-nuevo/vocab-ampliado/parche-vocab.json`
   Escribe en sitio, con el mismo formato que `anexar_vocabulario.py`.
2. `python tools/content/anexar_vocabulario.py contenido-nuevo/vocab-ampliado/vocabulario-ampliado.json`
3. `checkContent`. Después, a `integrado/2026-09-17-vocab-ampliado/`.

## Para la pantalla

Con 84 bancos, la lista del contrarreloj se hace larga. Conviene agruparla por nivel, como las lecciones, y marcar cuáles ya tienen marca.
