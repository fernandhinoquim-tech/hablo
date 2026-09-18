# Vocabulario B1 (Cowork, 17-09-2026)

- **`vocabulario-b1.json`**: 40 bancos B1 de 25 parejas cada uno, **1.000 parejas en total**. Temas:
  - Trabajo, negocios, dinero.
  - Estudio, ciencia, salud, cuerpo.
  - Medio ambiente, naturaleza, ciudad.
  - Medios, tecnología, sociedad, ley.
  - Cultura, deporte, viajes, vivienda, compras, comida.
  - Tres bancos de verbos, tres de phrasal verbs y cuatro de colocaciones.
  - Adjetivos (dos bancos), adjetivos extremos, sentimientos, personalidad, relaciones, adverbios, conectores, sustantivos abstractos y expresiones de tiempo.
- **Garantías:**
  - Ningún inglés ni español se repite en todo `vocabulario.json`, contando los 84 bancos actuales.
  - Dentro de un banco nunca hay dos respuestas válidas.
  - Todo el inglés está en CMUdict y no hay heterónimos.
- **Revisión:** un revisor adversarial resolvió 51 choques entre redactores y corrigió 31 defectos más:
  - 9 heterónimos (close, suspect, estimate, export…);
  - 3 casos de dos respuestas válidas;
  - formas británicas;
  - español poco colombiano («vacilar» → «titubear»);
  - niveles mal puestos.
- **Total después de integrar:** 124 bancos y 2.339 parejas. Con las lecciones, el curso cubre **≈ 2.400 palabras distintas de A1 a B1**. English Profile da ~2.950 acumuladas para B1, así que vamos en un 81 %.
- **`crucigramas-b1.json`**: 63 crucigramas nuevos de B1, generados y validados con los mismos `generar.py` y `validar_cruci.py`, contra el vocabulario ya con B1. Los ids no chocan con los 103 actuales.

## Integrar

1. `python tools/content/anexar_vocabulario.py contenido-nuevo/vocab-b1/vocabulario-b1.json`
2. Sumar `crucigramas-b1.json` a `assets/content/crucigramas.json`. Basta concatenar la lista `crucigramas`, o que el cargador lea los dos archivos.
3. `checkContent`, y después a `integrado/`.

Con 124 bancos, la lista del contrarreloj ya necesita agruparse por nivel si todavía no lo está.
