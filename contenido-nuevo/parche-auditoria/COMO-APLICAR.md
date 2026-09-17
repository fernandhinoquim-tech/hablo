# Parche de la auditoría A1-A2 (Cowork, 17-09-2026)

Arregla los defectos de `contenido-nuevo/auditoria-a1-a2/` (revisión adversarial de los 565 ejercicios).

**Qué trae:** 4 archivos, uno por tramo de unidades, con 276 ids y 381 cambios.
- **37 fichas de teoría** reescritas donde decían algo falso o exagerado.
- **≈ 150 `accept`** para dejar de marcar mal inglés correcto. Incluye los de `translate`, `build` y `type`, que tu commit 6ff3cc9 ya lee.
- **Opciones y señuelos** que tenían una segunda respuesta correcta o que sonaban igual.
- **Pistas corregidas.**
- **42 ejercicios con el contenido reemplazado** (mismo id y tipo): eran duplicados, se salían del tema o no tenían arreglo de otra forma.

**Verificación hecha:**
1. Cuatro revisores escribieron los parches desde sus informes.
2. Otros cuatro verificaron los parches de forma adversarial: encontraron 50 fallos del propio parche y se corrigieron todos.
3. Validador propio que replica `Correccion.kt` (números, contracciones, `'s` tras sujeto) y `checkProduced`, más `tools/content/validar2.py` sobre el resultado: **OK, 56 lecciones, 565 ejercicios.**
4. Se aplicó sobre el `curriculum.json` actual, sin cambios desde el 16-09.

## Cómo aplicarlo

```
python contenido-nuevo/parche-auditoria/parchar2.py app/src/main/assets/content/curriculum.json contenido-nuevo/parche-auditoria/parche-a1u1-u5.json contenido-nuevo/parche-auditoria/parche-a1u6-u9.json contenido-nuevo/parche-auditoria/parche-a2u1-u5.json contenido-nuevo/parche-auditoria/parche-a2u6-u10.json
```

**Cómo trabaja `parchar2.py`:**
- Es `parchar.py` con una diferencia: una clave puede ser **id de lección**, y en ese caso solo trae `theory` completa.
- **No escribe encima**: deja `curriculum.json.parchado.json` al lado, ya con el formato del proyecto (usa `migrar_a1.escribir`, que encuentra solo en `tools/content`).
- Revisa el resultado y cópialo sobre `curriculum.json`.

**Después:**
1. `gradlew checkContent` y los tests.
2. Mover la carpeta a `integrado/2026-09-17-parche-auditoria/`.

## Lo que queda para ti (de los `_pendientes` de cada archivo)

1. **El mazo guarda copias `en`/`es`.** Estos 42 ids cambiaron de contenido, así que en el teléfono hay que refrescarlos o retirarlos de `mazo.json`. Lo mismo con sus fallos viejos de `progreso.json` (en «Corrige tu propio error» aparecería un error viejo junto a un ejercicio nuevo). Lo más limpio es que, al cargar, el mazo tome `en`/`es` del curso por id.
   `a1u1l3e3 a1u1l3e6 a1u2l1e5 a1u2l2e7 a1u2l3e4 a1u3l2e2 a1u3l2e4 a1u3l2e6 a1u4l1e4 a1u4l3e5 a1u5l2e2 a1u4l3e9 a1u6l2e2 a1u6l3e5 a1u6l3e8 a1u7l1e9 a1u7l2e1 a1u7l2e7 a1u7l2e11 a1u7l3e6 a1u7l3e10 a1u8l1e8 a1u8l2e3 a1u8l2e10 a1u8l4e3 a1u8l4e4 a1u9l1e2 a1u9l1e10 a1u9l2e9 a1u9l3e8 a2u1l2e4 a2u2l2e3 a2u2l3e8 a2u6l3e5 a2u6l3e7 a2u7l1e3 a2u7l3e6 a2u9l2e4 a2u9l2e5 a2u10l1e6 a2u10l2e5 a2u10l2e6`
2. **Oír con las 4 voces de Piper** (`oir_pares.py` o equivalente) los audios nuevos o dudosos:
   - a1u2l1e5 «Here you go.»
   - a1u7l3e6 «Can you say that again?»
   - a1u8l2e3 «They played at home.»
   - a1u8l4e3 «She didn't visit us.»
   - a1u8l4e4 «Did they want more food?»
   - a2u6l3e7 «I spend most of my time at work.»
   - a1u9l2e3: ¿se distingue «I'll help you» de «I help you»? Si no, el distractor pasa a «I'm help you.».
   - a2u8l1e2: ¿Piper lee 1920 como «nineteen twenty»?
   - a2u6l3e9: *live* es heterónimo. Si sale /laɪv/, el texto pasa a «Most of my friends are from here.».
3. **Pares mínimos cuya frase delata la respuesta por gramática:**
   - a1u6l3e12 y a1u9l2e12
   - a2u2l4e10 (since/sense)
   - a2u5l2e10 (ban/van)

   Decide si la frase se muestra antes de responder; si no se muestra, no hace falta cambiarlos.
4. **a2u2l3e8** cambió el texto de hablar a «I have just gotten home.». Confirma que el jurado de `h` sigue bien.

Los "huecos" de cada `_pendientes` (cosas que la teoría enseña y ningún ejercicio practica) **no son para ti**: van en el lote de ejercicios y lecciones nuevas que escribe Cowork.
