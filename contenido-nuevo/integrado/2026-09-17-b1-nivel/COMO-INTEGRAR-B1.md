# Nivel B1 (Cowork, 17-09-2026)

**15 unidades, 45 lecciones y 540 ejercicios.** Hay 12 ejercicios por lección y el 70 % son de producción (cloze y write). Cada lección trae su ficha de teoría con la trampa del hispanohablante. Temario tomado del Core Inventory B1 y de English File Intermediate.

| Unidad | Lecciones |
|---|---|
| b1u1 🕰️ El presente perfecto a fondo | present perfect continuous · simple vs continuous · present perfect vs past simple |
| b1u2 📖 Contar historias | past perfect · los tres pasados juntos · used to / would / be-get used to |
| b1u3 🔮 El futuro con matices | future continuous y perfect · be about to / likely to / supposed to · predicciones con grados de certeza |
| b1u4 🕵️ Deducir y especular | must / can't / might (presente) · must have / can't have · should have / could have |
| b1u5 🔀 Si hubiera… | repaso + unless / as long as / in case · tercer condicional · wish / if only |
| b1u6 🏭 La voz pasiva a fondo | todos los tiempos · con modales · have / get something done |
| b1u7 🗣️ Estilo indirecto a fondo | preguntas · órdenes y pedidos · verbos para reportar |
| b1u8 🔗 Oraciones de relativo | defining / non-defining · whose / where / when · combinar frases |
| b1u9 ❓ Preguntar con cortesía | preguntas indirectas · question tags · I was wondering if… |
| b1u10 🧩 -ing o to, a fondo | remember / stop / try · want someone to, make / let · so that / in order to |
| b1u11 ⚖️ Comparar y cuantificar | much / far + comparativo, the more… · so / such / too / enough · each / both / either / neither |
| b1u12 🧷 Phrasal verbs | separables · de trabajo y problemas · de personas |
| b1u13 🧠 Conectar y argumentar | contraste · causa y resultado · organizar un texto |
| b1u14 💬 Opinar, acordar y reclamar | opinar y discrepar · quejarse formalmente · correo formal vs informal (Aptis W4) |
| b1u15 💼 Trabajo, planes y sentimientos | entrevista · planes y metas · adjetivos extremos |

**Verificado:**
- Cinco redactores y tres revisores adversariales. Los revisores corrigieron 74 defectos (37 ALTO), entre ellos:
  - unos 100 `accept` con un espacio suelto («I 'm»);
  - `accept` con inglés incorrecto que se habían generado por combinación;
  - una opción que en inglés americano también era correcta;
  - fichas que decían algo falso.
- El validador con las reglas de `checkContent` y `Correccion.kt` da OK sobre el curso completo (A1 + A2 + B1).

## Integrar

`python tools/content/anexar_nivel.py contenido-nuevo/b1-nivel/b1-nivel-completo.json`

Anexa el nivel B1 después de A2. Luego `checkContent`, los tests y a `integrado/`.

## ⚠️ Para Claude Code

1. **`'d` = would o had.** `Correccion.suelta` convierte siempre `I'd` en `I would`. En B1 aparece mucho `I'd` = *I had* (tercer condicional, past perfect: *If I'd known*, *I'd already left*).
   - Para que no se rechacen, los `accept` traen formas con `'d`. Efecto secundario: también se aceptaría el error raro «I would left».
   - **Arreglo recomendado:** que `suelta` pruebe las dos expansiones (`'d` → would y `'d` → had) y acepte si alguna coincide.
   - Tampoco se expanden `must've`, `should've`, `would've`, `hadn't`, `hasn't` ni `haven't`. Si las añades a `CONTRACCIONES`, los `accept` que las repiten quedarían como duplicados y `checkContent` lo va a decir: avísame y los limpio.
2. **`accept` largos:** hay hasta 143 por ejercicio (3.296 en total en B1). Son combinaciones verificadas. Si la corrección "qué te faltó" se vuelve lenta, que compare con el más parecido usando un prefiltro por longitud.
3. **Desbloqueo:** B1 va después de A2, así que la regla de `desbloqueadas()` lo abre al terminar A2. Si Fero quiere entrar ya a B1 (el Modo Aptis le pide B1), conviene que el primer bloque de cada nivel se pueda abrir a mano.
