# Core ampliado + simulacro abierto (Cowork, 08-10-2026)

**Contexto:** Fero tiene el examen en **20 días (≈ 28-10)** y necesita B1 en las cuatro destrezas. El Core es el desempate: si una destreza queda justo bajo el corte, un buen Core la sube al nivel siguiente ([Aptis scoring system v2.1](https://www.britishcouncil.org/sites/default/files/aptis_scoring_system_v2.1.pdf)). Con 120 ítems y rondas de 10, los habría repetido en tres sesiones.

## 1. Los dos bancos del Core: copiar y listo

`aptis-core-gramatica.json` y `aptis-core-vocabulario.json` son **los archivos actuales de la app con 100 ítems añadidos al final de cada uno**.

- Los 60 ítems de cada banco quedaron idénticos: lo comprobé comparando el JSON.
- Solo cambia el `_comentario`.

| Banco | Antes | Ahora | Nuevos por nivel |
|---|---|---|---|
| Gramática | 60 | **160** (g061-g160) | A2 25 · B1 50 · B2 25; 11 de gramática hablada («A: … B: ___») |
| Vocabulario | 60 | **160** (v061-v160) | A2 20 · B1 50 · B2 30; 25 de cada subtipo |

**Verificación:**
- `validar_core.py` da OK en los dos.
- Ningún id ni enunciado se repite contra los bancos actuales.
- La respuesta correcta está balanceada: primera opción 34 veces, segunda 33 y tercera 33 en cada banco.
- Revisión adversarial: 10 arreglos, todos aplicados.
  - Cuatro eran ítems con una segunda opción defendible: *do a good impression*, *Me too* tras una negación, *I'll* contra *going to* y *deposit*.
  - Tres eran `why` engañosos.
  - Dos repetían un punto.

**Ajustar `AptisTest` (línea 38):** hoy exige un Core de `121..135` tareas. Pasa a `321..335`: 320 ítems más los 15 del simulacro, menos los que se repiten.

## 2. Abrir el simulacro ya, como diagnóstico

Hoy `Aptis.simulacroDesbloqueado` lo cierra hasta que las cinco pistas alcanzan B1, y con 20 días no hay tiempo de esperar.

- **Pedido:** que esté **siempre abierto**.
- **Texto mientras no estén las cinco en B1:** «⏱ Simulacro · hazlo ahora como diagnóstico: estima tu nivel en cada destreza y te dice tu piso. Repítelo cuando las cinco lleguen a B1.»
- Cuando las cinco estén en B1, el texto queda como hoy.
- `faltanParaSimulacro` se puede seguir mostrando como dato, pero ya no bloquea.
- `ResultadoSeccion.vigente` ya invalida el resultado si cambia el contenido: con el Core nuevo, un simulacro viejo no cuenta. Es lo correcto.

**Plan de uso:** Fero lo hace el día 1, el día 10 y el día 17. Si `resultadoSimulacro` guarda solo el último, está bien: el plan de la guía le dice que anote su piso cada vez.

## Orden sugerido

Lo más barato primero:

1. Copiar los dos bancos y ajustar `AptisTest`.
2. Abrir el simulacro.
3. Después, Reading parte 3 (`contenido-nuevo/aptis-reading-parte3/`).

Compilar, instalar y avisarle a Fero, para que haga el simulacro hoy mismo.
