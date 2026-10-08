# Herramientas de Cowork para Hablo

Son los validadores y generadores que usa Cowork para escribir y verificar contenido **antes** de entregarlo. Replican las reglas de `checkContent` y de `Correccion.kt`.

## Cómo usarlos en un chat nuevo

1. Copia esta carpeta a un directorio de trabajo del contenedor en la nube.
2. Pon al lado **los archivos actuales de la app**. Se piden con `device_stage_files` desde `app/src/main/assets/content/` y se renombran así:
   - `curriculum.json` → `curriculum.base.json`
   - `historias.json` → `historias.base.json`
   - `scenarios.json` → `scenarios.base.json`
   - `vocabulario.json` → `vocab_total.json`
   - también `cmudict.dict`, que está en `app/src/main/assets/gop/`
3. Los scripts usan rutas relativas: córrelos desde esa carpeta.

## Qué hace cada uno

| Script | Para qué |
|---|---|
| `grader.py` | Réplica de `Correccion.kt`: `estricta()` detecta duplicados en `accept` y `suelta()`/`acepta()` aproximan la corrección. Al día al 24-09, contracciones incluidas. La app además lee `'d` como would **o** had y `'s` como is **o** has; aquí solo would/is. |
| `validar_nivel.py` | Lecciones: valida los archivos `{"units":[...]}` anexados como un nivel nuevo sobre `curriculum.base.json`. Cambia `"XX"` por el id del nivel. |
| `anexar_unidades.py` | Mete unidades **intercaladas** dentro de un nivel existente. |
| `parchar2.py` | Parches por id de ejercicio o de lección (en las lecciones, solo `theory`). |
| `validar_b2v.py` + `cobertura.py` | Bancos de vocabulario: sin inglés ni español repetidos en todo el vocabulario, sin casi sinónimos en un banco, todo en CMUdict. `cobertura.py` cuenta palabras distintas contra English Profile. |
| `validar_hist.py` / `validar_esc.py` | Historias y escenarios. |
| `validar_aptis.py` / `validar_pista.py` | Pistas de Aptis: Reading, Listening y Speaking / Writing. |
| `validar_extra.py` | Oído, dictado y drills. |
| `generar.py` + `validar_cruci.py` | Crucigramas a partir de los bancos, con semilla fija, y su validación. |

**Briefs de ejemplo** (qué se les dice a los redactores y a los revisores): `BRIEF-lecciones-ejemplo.md`, `BRIEF-vocab-ejemplo.md`, `BRIEF_HIST.md`, `SPEC_APTIS.md`, `COMUN.md` (cómo lee Piper), `REVIEW_BRIEF.md` (tipos de ejercicio y cómo corrige la app), `PATCH_SPEC.md` y `VERIFY_B2.md`. Tienen rutas viejas: ajústalas.
