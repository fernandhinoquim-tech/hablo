# Crucigramas (Cowork, 17-09-2026) — para Claude Code

Actividad nueva, pedida por Fero. La evidencia está en `contenido-nuevo/actividades-nuevas.md`, anexo 2: producir la palabra desde una pista es recuperación productiva (d = 1,38), y la rejilla se corrige sola.

## El contenido: `crucigramas.json`

- **103 crucigramas** (34 A1 · 69 A2). Cada uno sale de **un solo banco** de `vocabulario.json`: dentro de un banco no hay casi sinónimos, así que la pista en español apunta a una sola palabra.
- Tienen 5 a 8 palabras (88 tienen 8) y la rejilla mide como mucho 10 × 10.
- **Pistas en español, respuestas en inglés** (la dirección difícil).
- Hay hasta 2 crucigramas por banco, con palabras distintas entre sí.

```json
{"id": "cruci-casa-a1-1", "title": "🏠 La casa · A1 · 1", "level": "A1", "banco": "casa-a1",
 "filas": 9, "columnas": 10,
 "palabras": [{"numero": 2, "dir": "H", "fila": 0, "col": 6, "en": "bed", "pista": "cama"}, ...]}
```

- **Coordenadas:** empiezan en 0. `dir` es H (horizontal) o V (vertical).
- **`numero`:** numeración clásica, en orden de lectura de las casillas de inicio. Una palabra H y una V pueden compartir número.
- **`pista`:** es el `es` del banco, tal cual (a veces con aclaración: «cuarto (4.º)»).

**Validado** (`validar_cruci.py`, se puede correr en el PC):
- no hay dos letras distintas en la misma casilla;
- toda corrida de 2 o más letras, en horizontal o en vertical, es una palabra de la lista (no hay palabras "fantasma");
- la rejilla está conectada;
- la numeración es la clásica;
- cada pista coincide con su banco.

**Dependencia:** los crucigramas usan el `vocabulario.json` **ya ampliado y parchado** (el de `integrado/2026-09-17-vocab-ampliado`). Si cambias un banco, regenera con `generar.py`, que usa semilla fija, y vuelve a validar.

## La pantalla

1. **Lista** agrupada por nivel, con ✓ en los terminados. Opcional: primero los de bancos cuyas palabras falló en el contrarreloj.
2. **Rejilla:** casillas blancas para las letras y oscuras para el resto; el número va en la esquina de la casilla de inicio.
   - Al tocar una casilla se selecciona su palabra; si hay cruce, tocar otra vez cambia de dirección.
   - Arriba de la rejilla se muestra la pista de la palabra seleccionada: «3 ↓ cocina».
3. **Teclado:** el del sistema, solo letras. Al escribir, el cursor avanza por la palabra.
4. **Corrección inmediata por palabra:** cuando una palabra está completa y es correcta, se pinta de verde y **Piper la dice**. Si está completa y es incorrecta, se marca suave, sin castigo.
5. **Ayudas:**
   - «Mostrar una letra» cuesta nada, pero la palabra ya no cuenta como "sin ayuda".
   - «Oír la palabra» la pronuncia Piper, y así se convierte en dictado.
6. **Sin reloj** (decisión de diseño: el crucigrama es el descanso; la tensión la ponen Aguanta y el contrarreloj).
7. **Al terminar:** «8 de 8 · 6 sin ayuda». Las palabras que necesitaron ayuda entran al mazo (`Mazo.alimentarPares`, ids `cruci:<banco>|<en>`).
8. Guardar el avance a mitad de camino, para poder salir y volver.
9. Nada de esto toca la red.
