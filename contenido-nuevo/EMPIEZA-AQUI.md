# Hablo — traspaso para Cowork al 28-09-2026. Lee esto primero.

App Android para que **Fero** (colombiano, 40 años, doctorando, **no programa**) aprenda inglés.

- **Meta real:** **Aptis ESOL General**, que exige **B1 o superior en las CUATRO destrezas**. Es un piso, no un promedio: manda la destreza más floja.
- Él quiere llegar a B2. **No hay fecha de examen.**
- Escríbele siempre **en español**, corto y con clics exactos. Fero no corre comandos ni lee errores de programación.

## Reparto del trabajo

- **Claude Code**, en su PC con Android Studio: todo el Kotlin. Compila, instala por `adb`, prueba en el Samsung S25 y hace commit y push.
- **Cowork** (tú): el **contenido** (lecciones, vocabulario, historias, escenarios, pistas de Aptis, datos de actividades), **investigar antes de decidir** y **revisar lo que hace Claude Code**.
- **Fero**: lleva los mensajes entre los dos, prueba la app y genera las fotos con Gemini.

**Cómo se entrega:**
1. Cowork escribe en `contenido-nuevo/<carpeta>/`, con los archivos y un `COMO-INTEGRAR.md`.
2. Claude Code lo integra y lo mueve a `contenido-nuevo/integrado/<fecha>-<nombre>/`.
3. Cowork le da a Fero un mensaje listo para pegar en Claude Code.

## Qué leer

1. **`CLAUDE.md`** (en la raíz del repo): reglas duras, arquitectura, trampas ya pisadas, inventario y estado. **Es la fuente de verdad.**
2. Este archivo.
3. `contenido-nuevo/herramientas-cowork/LEEME.md`: los validadores de Cowork. **Úsalos, no los reescribas.**
4. Si hace falta más contexto:
   - `contenido-nuevo/auditoria-a1-a2/auditoria-a1-a2.md`: la auditoría del 17-09 y su plan, ya cumplido;
   - `actividades-nuevas.md`: la evidencia detrás de cada actividad.

## Estado verificado (28-09, v0.9.10, en los archivos de la app)

| Qué | Cuánto |
|---|---|
| Curso | **A1 43 · A2 45 · B1 45 · B2 45 = 178 lecciones, 2.179 ejercicios.** Cada lección trae su ficha de teoría con la trampa del hispanohablante |
| Vocabulario | 164 bancos, 3.339 parejas (A1 26 · A2 45 · B1 53 · B2 40 bancos), sin repetidos. Con las lecciones, **≈ 3.335 palabras distintas de A1 a B2 (71 % de English Profile)** |
| Crucigramas | 210 (A1 34 · A2 69 · B1 63 · B2 44) |
| Historias | 30 (una tanda por nivel, dos de A2) |
| Escenarios | 23 (A1 5 · A2 6 · B1 6 · B2 6) + charla libre con memoria |
| Oído | 16 bloques, 147 pares (medidos con las voces; u/uu quedó con 5) |
| Dictado de números | 80 ítems |
| Drills | 70 |
| Modo Aptis | Core 120 ítems. Writing 60 · Reading 67 · Listening 73 · Speaking 63 (sin el simulacro; ≥ 8 por nivel y B1 ≥ 30 en las cuatro). **Fotos de Speaking: 20 puestas; faltan 15** (`integrado/2026-09-24-aptis-b1/PROMPT-GEMINI-FOTOS-2.md`) |
| Pantallas | lecciones, repaso/mazo, Aguanta, contrarreloj, historias, conversación, pronunciación, Oído, dictado, crucigramas, Gramática + «¿Por qué?», Modo Aptis con simulacro, respaldo del progreso |

**Uso real de Fero** (cuaderno, 24-09): aprobó 11 de 178 lecciones, todas de A1, y no ha usado la app desde el 19-09. **El cuello de botella es usarla, no el contenido.** Claude Code preguntó si ~20 entradas del cuaderno del 16-09 eran pruebas suyas; está pendiente la respuesta de Fero.

## Entregado y pendiente de integrar

- Nada. `aptis-niveles/` (28-09) **se integró el 08-10** tal cual (ninguna tarea vieja cambió, las fotos siguen), archivado en `integrado/2026-09-28-aptis-niveles/`.

## Pendiente (en orden de valor)

1. **Que Fero use la app a diario** y reporte lo que marque mal. Un fallo que le enseña inglés incorrecto va antes que cualquier cosa nueva.
2. **Fero:** las 15 fotos de la segunda tanda.
3. **Claude Code:** medir `integrado/2026-09-24-aptis-b1/oido-repuestos.json`, si no lo hizo. Los bloques y/j y u/uu no tienen más pares comunes.
4. **Cowork, opcional:** subir el vocabulario B2 hacia English Profile (faltan ~1.300 palabras) y la parte 3 de Reading de Aptis (opiniones), que necesita un tipo nuevo en la app.
5. **Deudas pequeñas:**
   - 7 ejercicios con más de 150 `accept` (a2u16l2e8, b2u13l1e2, b2u13l1e8, b2u14l3e2…). Se reducen fijando el sujeto en el español.
   - 2 fichas pasan de 130 palabras.
   - Actividades sin construir: doblar la escena y dictogloss (evidencia en `actividades-nuevas.md`).

## Cómo trabaja Cowork (método que funcionó)

- **Redactores en paralelo + un revisor adversarial por lote.** Todas las pasadas encontraron defectos reales (entre 12 y 130 por lote). **Ninguna entrega sale sin verificación.**
- Antes de entregar: validar con `herramientas-cowork/` **y** probar sobre una copia de los archivos actuales en el PC, con los scripts de `tools/content/` del repo.
- Cada redactor usa un script con **nombre único** en el scratchpad. Dos agentes se pisaron un `gen.py` compartido.
- **Para ahorrar recursos** (Fero lo pidió):
  - lotes grandes en un solo pedido;
  - un revisor para varios archivos;
  - respuestas cortas de los agentes.

## Reglas que no se negocian

- **Marcar mal algo que está bien es el peor fallo.** Cada `write` o `cloze` lleva en `accept` todas las respuestas naturales. **Un `accept` con inglés incorrecto es igual de grave**: le enseña que lo malo está bien.
- **El reconocedor nunca ve la frase esperada.**
- **No trabajar a ciegas:** buscar la evidencia antes de elegir un modelo, un umbral o una actividad, y citar la cifra.
- **El curso va en inglés americano.** El Modo Aptis enseña el británico como contenido, en el `why`. El español, colombiano: carro, celular, apartamento, plata solo como coloquial, no «piso», «móvil» ni «vacilar» con sentido de España.
- **Aptis no puntúa fonemas.**
- **Una etapa a la vez**: termina cuando Fero la usó.

## Trampas de Cowork (ya pisadas)

- **No correr `git` desde Cowork** en la carpeta del repo: deja `.git/index.lock` y bloquea a Claude Code.
- **Cómo corrige la app** (`Correccion.kt`):
  - mayúsculas, puntuación, tildes y guiones no cuentan;
  - las cifras 0-100 se igualan a palabras;
  - se expanden `I'm…let's`, `must've…might've`, `hadn't/hasn't/haven't`;
  - `'s` y `'d` solo tras sujeto (is/has, would/had);
  - **no** se normalizan «7:30», «$12.50», los años ni «o'clock».
  - Un `accept` que repite la respuesta según esas reglas **rompe la compilación**; eso se limpia con `dedupe_accept.py` de `tools/content/`.
  - El dictado usa una comparación estricta: «$650» no vale por «$6.50».
- **«May 3» se lee «may three»**: no pongas «May 3» en `accept`.
- **Piper (las voces):**
  - con palabras sueltas es inestable; mejor dentro de una frase;
  - **nunca heterónimos**: live, read, lead, close, record, present, produce…;
  - con la voz Grace, las letras sueltas no se entienden.
- **Los bancos de vocabulario:** ningún inglés ni español se repite en todo el archivo. Dentro de un banco no puede haber dos respuestas válidas a la vez.
- **`device_commit_files` puede tardar en reflejarse.** Comprueba con `device_bash` antes de dar algo por escrito.

## Dos cosas que parecen bug y NO lo son

- **"Spanish" marcado dudoso con dictado perfecto:** el modelo de fonemas oye la ε de «espanish». No se sube ese umbral.
- **live/leave:** Piper lo dice bien. Fue un error de oído real de Fero.
