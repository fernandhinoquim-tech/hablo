"""Validador v2: replica checkContent + Correccion.suelta para los 4 tipos nuevos."""
import json, sys, re, os

DICT=os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "app", "src", "main", "assets", "gop", "cmudict.dict")
SOUNDS={"sh","th","h","v","ed","final","es","rl","general"}
TYPES={"listen","translate","build","type","speak","write","cloze","shadow","minimalPair"}
CONTR=[("i'm","i am"),("you're","you are"),("we're","we are"),("they're","they are"),
 ("isn't","is not"),("aren't","are not"),("wasn't","was not"),("weren't","were not"),
 ("don't","do not"),("doesn't","does not"),("didn't","did not"),
 ("can't","can not"),("cannot","can not"),("couldn't","could not"),
 ("won't","will not"),("wouldn't","would not"),("shouldn't","should not"),
 ("i'll","i will"),("you'll","you will"),("he'll","he will"),("she'll","she will"),
 ("it'll","it will"),("we'll","we will"),("they'll","they will"),
 ("i've","i have"),("you've","you have"),("we've","we have"),("they've","they have"),
 ("let's","let us")]

UNIDADES=["zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve",
 "thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen"]
DECENAS={20:"twenty",30:"thirty",40:"forty",50:"fifty",60:"sixty",70:"seventy",80:"eighty",90:"ninety"}
MESES={"january","february","march","april","may","june","july","august","september","october","november","december"}
def en_letras(n):
    if n<0 or n>100: return None
    if n<20: return UNIDADES[n]
    if n==100: return "one hundred"
    if n%10==0: return DECENAS[n]
    return DECENAS[n//10*10]+"-"+UNIDADES[n%10]
def numeros(tokens):
    # como Correccion.numeros: "8"="eight", "twenty five"="twenty-five", "a hundred"="one hundred";
    # un numero pegado a un mes ("May 3") es una fecha y se queda en cifras
    out=[]; i=0
    while i<len(tokens):
        t=tokens[i]
        if t.isdigit() and (i==0 or tokens[i-1] not in MESES):
            l=en_letras(int(t))
            if l is not None: out+=l.split(" "); i+=1; continue
        if t in DECENAS.values() and i+1<len(tokens) and tokens[i+1] in UNIDADES[1:10]:
            out.append(t+"-"+tokens[i+1]); i+=2; continue
        if t=="a" and i+1<len(tokens) and tokens[i+1]=="hundred": out.append("one"); i+=1; continue
        out.append(t); i+=1
    return out
def norm(t):
    # normalizeAnswer + Correccion.fichas: sin tildes, guion = espacio, numeros en letras
    import unicodedata
    t=unicodedata.normalize("NFD",t.lower()).replace("\u2019","'").replace("-"," ").replace("\u2013"," ")
    t="".join(c for c in t if (c.isalnum() and not unicodedata.combining(c)) or c in " '")
    t=re.sub(r"\s+"," ",t.strip())
    return " ".join(numeros([x for x in t.split(" ") if x]))
def suelta(t):
    s=" "+norm(t)+" "
    for a,b in CONTR: s=s.replace(f" {a} ",f" {b} ")
    return s.strip()
def known():
    k=set()
    for line in open(DICT,encoding="utf-8",errors="ignore"):
        if not line.startswith(";;;"): k.add(line.split(" ",1)[0].split("(")[0])
    return k
def spoken(t):
    t=t.lower().replace("’","'").replace("-"," ")
    t="".join(c for c in t if c.isalnum() or c in " '")
    return [w for w in t.split(" ") if w.strip()]

def main(path):
    p=[]; d=json.load(open(path,encoding="utf-8")); K=known()
    lids,eids=set(),set(); nl=ne=0
    for lv in d["levels"]:
        for u in lv["units"]:
            for l in u["lessons"]:
                nl+=1; lid=l.get("id","?")
                if lid in lids: p.append(f"leccion {lid}: id repetido")
                lids.add(lid)
                th=l.get("theory")
                if not isinstance(th,dict): p.append(f"leccion {lid}: falta theory")
                else:
                    for k in ("title","body","trap"):
                        if not str(th.get(k,"")).strip(): p.append(f"leccion {lid}: theory.{k} vacio")
                for i,e in enumerate(l.get("exercises",[]),1):
                    ne+=1; w=f"{lid} ej{i} ({e.get('id','SIN ID')})"
                    eid=e.get("id")
                    if not eid: p.append(f"{w}: falta id")
                    elif eid in eids: p.append(f"{w}: id repetido")
                    else: eids.add(eid)
                    t=e.get("type")
                    if t not in TYPES: p.append(f"{w}: tipo desconocido {t}"); continue
                    if t in ("listen","translate","minimalPair"):
                        o=e.get("options") or []; a=e.get("answer")
                        if len(o)<2: p.append(f"{w}: menos de 2 opciones")
                        if len(set(x.strip() for x in o))!=len(o): p.append(f"{w}: opciones repetidas")
                        if not isinstance(a,str) or a not in o: p.append(f"{w}: answer no esta en options -> {a!r}")
                        if t=="listen" and e.get("audio")!=a: p.append(f"{w}: audio != answer")
                        if t=="translate" and not e.get("es"): p.append(f"{w}: falta es")
                        if t=="minimalPair":
                            for x in o:
                                if " " in x.strip(): p.append(f"{w}: opcion con espacio: {x!r}")
                                miss=[y for y in spoken(x) if y not in K]
                                if miss: p.append(f"{w}: fuera de cmudict: {','.join(miss)}")
                            s=e.get("sentence")
                            if s and a and a.lower() not in s.lower(): p.append(f"{w}: sentence no contiene {a!r}")
                            pl=e.get("play")
                            if pl is not None and pl!="sentence": p.append(f"{w}: play solo admite 'sentence'")
                            if pl=="sentence" and not s: p.append(f"{w}: play=sentence sin sentence")
                    elif t=="build":
                        if not e.get("answer"): p.append(f"{w}: falta answer")
                        if not e.get("es"): p.append(f"{w}: falta es")
                        base=set(spoken(e.get("answer","")))
                        ex=e.get("extra",[])
                        for x in ex:
                            if x.lower().strip(".,!?") in base: p.append(f"{w}: extra {x!r} ya esta en answer")
                        if len(set(ex))!=len(ex): p.append(f"{w}: extra repetidos")
                    elif t=="type":
                        if not e.get("audio"): p.append(f"{w}: falta audio")
                        if not e.get("meaning"): p.append(f"{w}: falta meaning")
                    elif t in ("speak","shadow"):
                        txt=e.get("text","")
                        if not txt: p.append(f"{w}: falta text")
                        if t=="speak":
                            s=e.get("sound")
                            if s not in SOUNDS: p.append(f"{w}: sound invalido {s!r}")
                        elif "sound" in e: p.append(f"{w}: shadow no lleva sound")
                        miss=[y for y in spoken(txt) if y not in K]
                        if miss: p.append(f"{w}: fuera de cmudict: {','.join(miss)}")
                    elif t in ("write","cloze"):
                        a=e.get("answer","")
                        if not a: p.append(f"{w}: falta answer")
                        if not e.get("es"): p.append(f"{w}: falta es")
                        vistas={suelta(a)}
                        for x in e.get("accept",[]):
                            if not x.strip(): p.append(f"{w}: accept con entrada vacia")
                            elif suelta(x) in vistas: p.append(f"{w}: accept duplica la respuesta (contracciones/puntuacion): {x!r}")
                            else: vistas.add(suelta(x))
                        if t=="cloze":
                            txt=e.get("text","")
                            if txt.count("___")!=1: p.append(f"{w}: text necesita exactamente un ___ (tiene {txt.count('___')})")
                    if t in ("translate","build","type") and e.get("accept") is not None:
                        # accept tambien en translate, build y type (16-09): el mazo los convierte en "escribir"
                        base=e.get("audio","") if t=="type" else e.get("answer","")
                        vistas={suelta(base)}
                        for x in e.get("accept",[]):
                            if not x.strip(): p.append(f"{w}: accept con entrada vacia")
                            elif suelta(x) in vistas: p.append(f"{w}: accept duplica la respuesta (contracciones/puntuacion): {x!r}")
                            else: vistas.add(suelta(x))
    if p:
        print(f"PROBLEMAS ({len(p)}):")
        for x in p: print("  -",x)
        return 1
    print(f"OK: {nl} lecciones, {ne} ejercicios")
    return 0

if __name__=="__main__": sys.exit(main(sys.argv[1]))
