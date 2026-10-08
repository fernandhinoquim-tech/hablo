import json,sys,copy
sys.path.insert(0,'.')
import grader as g
K=set()
for line in open('cmudict.dict',encoding='utf-8',errors='ignore'):
    if not line.startswith(';;;'): K.add(line.split(' ',1)[0].split('(')[0])
SOUNDS={"sh","th","h","v","ed","final","es","rl","general"}
ORDER=["id","type","audio","es","text","sound","options","answer","accept","extra","meaning","sentence","play","tip"]
def spoken(t):
    t=t.lower().replace("’","'").replace("-"," ")
    t="".join(c for c in t if c.isalnum() or c in " '")
    return [w for w in t.split(" ") if w.strip()]
def insertar(base,files):
    A1=next(lv for lv in base['levels'] if lv['id']=='A2')
    for f in files:
        for ins in json.load(open(f,encoding='utf-8'))['insertar']:
            idx=[u['id'] for u in A1['units']].index(ins['despues_de'])
            A1['units'].insert(idx+1,ins['unit'])
    return base
def check(d):
    P=[];lids=set();eids=set()
    for lv in d['levels']:
        for u in lv['units']:
            for k in ('id','emoji','title','subtitle'):
                if not u.get(k): P.append(f"{u.get('id')}: falta {k}")
            for l in u['lessons']:
                if l['id'] in lids: P.append(f"{l['id']}: id de lección repetido")
                lids.add(l['id'])
                th=l.get('theory',{})
                if set(th)!={'title','body','trap'} or not all(str(v).strip() for v in th.values()): P.append(f"{l['id']}: theory mal")
                for n,e in enumerate(l['exercises'],1):
                    w=e.get('id');t=e.get('type')
                    if w in eids: P.append(f"{w}: id repetido")
                    eids.add(w)
                    extra=[k for k in e if k not in ORDER]
                    if extra: P.append(f"{w}: claves desconocidas {extra}")
                    if not e.get('tip'): P.append(f"{w}: sin tip") if l['id'].startswith(('b2u',)) else None
                    def prod(a,acc):
                        v={g.estricta(a)}
                        for x in acc or []:
                            if not x.strip() or g.estricta(x) in v: P.append(f"{w}: accept vacío/repite {x!r}")
                            v.add(g.estricta(x))
                    if t in('listen','translate','minimalPair'):
                        o=e.get('options',[]);a=e.get('answer')
                        if len(o)<2 or len(set(o))!=len(o) or a not in o: P.append(f"{w}: options/answer")
                        if t=='listen' and e.get('audio')!=a: P.append(f"{w}: audio!=answer")
                        if t=='translate':
                            if not e.get('es'): P.append(f"{w}: falta es")
                            prod(a,e.get('accept'))
                            for x in e.get('accept',[]):
                                if any(op!=a and g.suelta(op)==g.suelta(x) for op in o): P.append(f"{w}: accept es distractor {x!r}")
                        if t=='minimalPair':
                            for x in o:
                                if ' ' in x or any(y not in K for y in spoken(x)): P.append(f"{w}: opción {x}")
                            s=e.get('sentence')
                            if s and a.lower() not in s.lower(): P.append(f"{w}: sentence sin answer")
                            if e.get('play') not in (None,'sentence') or (e.get('play') and not s): P.append(f"{w}: play")
                            if s and l['id'].startswith(('b2u',)) and any(y not in K for y in spoken(s)): P.append(f"{w}: sentence fuera de cmudict {[y for y in spoken(s) if y not in K]}")
                    elif t=='build':
                        if not e.get('es') or not e.get('answer'): P.append(f"{w}: build incompleto")
                        prop={x.strip().lower() for x in e['answer'].split(' ') if x.strip()}
                        ex=e.get('extra',[])
                        if [x for x in ex if x.strip().lower() in prop] or len(set(ex))!=len(ex): P.append(f"{w}: extra repite")
                        prod(e['answer'],e.get('accept'))
                        bank=g.fichas(' '.join(e['answer'].split(' ')+ex)).split()
                        for x in e.get('accept',[]):
                            b=list(bank)
                            for tk in g.fichas(x).split():
                                if tk in b: b.remove(tk)
                                else: P.append(f"{w}: accept de build usa ficha inexistente {tk}"); break
                    elif t=='type':
                        if not e.get('audio') or not e.get('meaning'): P.append(f"{w}: type incompleto")
                        prod(e['audio'],e.get('accept'))
                    elif t in('speak','shadow'):
                        if t=='speak' and e.get('sound') not in SOUNDS: P.append(f"{w}: sound")
                        if t=='shadow' and 'sound' in e: P.append(f"{w}: shadow con sound")
                        m=[y for y in spoken(e.get('text','')) if y not in K]
                        if m: P.append(f"{w}: fuera de cmudict {m}")
                        if any(c.isdigit() for c in e.get('text','')): P.append(f"{w}: dígitos en texto hablado")
                    elif t in('write','cloze'):
                        if not e.get('es') or not e.get('answer'): P.append(f"{w}: incompleto")
                        prod(e['answer'],e.get('accept'))
                        if t=='cloze' and e.get('text','').count('___')!=1: P.append(f"{w}: cloze ___")
                    else: P.append(f"{w}: tipo {t}")
    return P
if __name__=='__main__':
    import glob
    files=sys.argv[1:] or sorted(glob.glob('nivel-u*.json'))
    base=json.load(open('curriculum.base.json',encoding='utf-8'))
    units=[]
    for f in files:
        us=json.load(open(f,encoding='utf-8'))['units']
        for u in us:
            for l in u['lessons']:
                ids=[e['id'] for e in l['exercises']]
                if ids!=[f"{l['id']}e{i}" for i in range(1,len(ids)+1)]: print(f"AVISO {l['id']}: ids no consecutivos")
                if not 11<=len(ids)<=14: print(f"AVISO {l['id']}: {len(ids)} ejercicios")
                if not l['id'].startswith(u['id']+'l'): print(f"AVISO {l['id']}: id no empieza por {u['id']}")
        units+=us
    base['levels'].append({"id":"XX","title":"Nivel nuevo","units":units})
    P=check(base)
    if P: print("PROBLEMAS:"); [print(' -',x) for x in P]; sys.exit(1)
    n=sum(len(l['exercises']) for u in units for l in u['lessons'])
    print('OK', len(units),'unidades', sum(len(u['lessons']) for u in units),'lecciones', n, 'ejercicios B2')
