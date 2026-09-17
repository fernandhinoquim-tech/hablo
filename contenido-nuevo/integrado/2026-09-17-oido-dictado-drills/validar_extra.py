import json,sys,re
sys.path.insert(0,'/home/claude/extra')
import grader as g
K=set(l.split(' ',1)[0].split('(')[0] for l in open('/home/claude/extra/cmudict.dict',errors='ignore') if not l.startswith(';;;'))
HET={'live','read','lead','wind','close','tear','bow','row','wound','minute','object','present','record','use','desert','content','permit','refuse','produce','bass','dove','polish'}
SOUNDS={"sh","th","h","v","ed","final","es","rl","general"}
def pal(t): return [w for w in re.sub(r"[^a-z' ]"," ",t.lower().replace("-"," ")).split() if w]
def cmu(where,t,P,het=True):
    m=[w for w in pal(t) if w not in K]
    if m: P.append(f"{where}: fuera de cmudict {m}")
    if het:
        h=[w for w in pal(t) if w in HET]
        if h: P.append(f"{where}: heterónimo {h}")
def oido(d,P):
    ids=set()
    for b in d['bloques']:
        w=b.get('id')
        if w in ids: P.append(f"{w}: id repetido")
        ids.add(w)
        for k in ('id','title','level','contraste','explicacion','hablar','sound','pares'):
            if not b.get(k): P.append(f"{w}: falta {k}")
        if b.get('sound') not in SOUNDS: P.append(f"{w}: sound")
        cmu(w+' hablar',b.get('hablar',''),P)
        if not 6<=len(b.get('pares',[]))<=10: P.append(f"{w}: {len(b.get('pares',[]))} pares")
        vistos=set()
        for p in b.get('pares',[]):
            a,bb=p.get('a',''),p.get('b','')
            if ' ' in a or ' ' in bb or a==bb: P.append(f"{w}: par mal {a}/{bb}")
            if (a,bb) in vistos: P.append(f"{w}: par repetido")
            vistos.add((a,bb))
            cmu(f"{w} {a}/{bb}",a+' '+bb,P)
            f=p.get('frase','')
            if f.count('___')!=1: P.append(f"{w} {a}/{bb}: frase necesita un ___")
            cmu(f"{w} frase",f.replace('___',''),P)
def dictado(d,P):
    ids=set()
    for it in d['items']:
        w=it.get('id')
        if w in ids: P.append(f"{w}: id repetido")
        ids.add(w)
        for k in ('id','level','tipo','modo','audio','tip'):
            if not it.get(k): P.append(f"{w}: falta {k}")
        cmu(w+' audio',it.get('audio',''),P,het=False)
        if it.get('modo')=='escribir':
            if not it.get('answer') or not it.get('pregunta_es'): P.append(f"{w}: escribir sin answer/pregunta_es")
            v={g.estricta(it['answer'])}
            for x in it.get('accept',[]):
                if g.estricta(x) in v: P.append(f"{w}: accept repite {x!r}")
                v.add(g.estricta(x))
        elif it.get('modo')=='elegir':
            o=it.get('options',[])
            if len(o)!=3 or len(set(o))!=3 or it.get('answer') not in o or not it.get('pregunta_es'): P.append(f"{w}: elegir mal")
        else: P.append(f"{w}: modo")
        if any(ch.isdigit() for ch in it.get('audio','')): P.append(f"{w}: el audio lleva cifras (escríbelo en palabras para que Piper lo lea como se dice)")
def drills(d,P):
    old={x['text'].lower() for x in json.load(open('/home/claude/extra/drills-existentes.json'))['drills']}
    for x in d['drills']:
        w=x.get('text','')[:30]
        for k in ('text','sound','focus','tip'):
            if not x.get(k): P.append(f"{w}: falta {k}")
        if x.get('sound') not in SOUNDS: P.append(f"{w}: sound")
        if x['text'].lower() in old: P.append(f"{w}: ya existe")
        old.add(x['text'].lower())
        cmu(w,x['text'],P)
        if any(ch.isdigit() for ch in x['text']): P.append(f"{w}: cifras")
if __name__=='__main__':
    P=[]
    kind,f=sys.argv[1],sys.argv[2]
    {'oido':oido,'dictado':dictado,'drills':drills}[kind](json.load(open(f)),P)
    print('\n'.join(P) if P else 'OK')
