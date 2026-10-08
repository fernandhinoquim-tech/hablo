"""Valida bancos NUEVOS contra los existentes. Regla: ningún inglés ni español se repite en TODO el banco (viejo + nuevo);
casi sinónimos no conviven en un banco; todo en cmudict; nivel A1/A2."""
import json,sys,re,collections,glob
SINON=[{"store","shop"},{"movie","film"},{"couch","sofa"},{"trash","garbage"},{"flat","apartment"},{"lift","elevator"},
 {"holiday","vacation"},{"mum","mom"},{"begin","start"},{"big","large"},{"small","little"},{"buy","purchase"},{"quick","fast"},
 {"happy","glad"},{"sick","ill"},{"job","work"},{"speak","talk"},{"end","finish"},{"sad","unhappy"},{"cab","taxi"},{"pants","trousers"},
 {"cookie","biscuit"},{"fall","autumn"},{"road","street"},{"trip","journey"},{"travel","trip"},{"look","see","watch"},{"say","tell"},
 {"hear","listen"},{"learn","study"},{"close","shut"},{"under","below"},{"above","over"},{"near","close"},{"certain","sure"},
 {"pretty","beautiful"},{"smart","intelligent","clever"},{"doctor","physician"},{"kid","child"},{"guy","man"},{"gift","present"},
 {"stone","rock"},{"sick","nauseous"},{"hurt","ache","pain"},{"earn","win"},{"lend","borrow"},{"cost","price"},{"cellphone","phone","cell phone"}]
K=set(l.split(' ',1)[0].split('(')[0] for l in open('cmudict.dict',errors='ignore') if not l.startswith(';;;'))
def pal(t): return [w for w in re.sub(r"[^a-z' ]","",t.lower().replace("-"," ")).split() if w]
def norm_es(s): return re.sub(r"\s+"," ",s.lower().strip())
def main(files):
    old=json.load(open('vocab_total.json'))['bancos']
    new=[b for f in files for b in json.load(open(f))['bancos']]
    P=[]
    ids=collections.Counter(b['id'] for b in old+new)
    for i,n in ids.items():
        if n>1: P.append(f"id repetido {i}")
    en=collections.defaultdict(list); es=collections.defaultdict(list)
    for b in old+new:
        for p in b['pares']:
            en[p['en'].lower().strip()].append(b['id']); es[norm_es(p['es'])].append(b['id'])
    newids={b['id'] for b in new}
    for w,l in en.items():
        if len(l)>1 and set(l)&newids: P.append(f"inglés repetido {w!r} en {l}")
    for w,l in es.items():
        if len(l)>1 and set(l)&newids: P.append(f"español repetido {w!r} en {l}")
    for b in new:
        for k in ('id','title','level','pares'):
            if not b.get(k): P.append(f"{b.get('id')}: falta {k}")
        if b.get('level')!='B2': P.append(f"{b['id']}: level {b.get('level')}")
        if not (6<=len(b['pares'])<=25): P.append(f"{b['id']}: {len(b['pares'])} pares (6-25)")
        ing={p['en'].lower() for p in b['pares']}
        for s in SINON:
            if len(s&ing)>1: P.append(f"{b['id']}: casi sinónimos juntos {sorted(s&ing)}")
        for p in b['pares']:
            if not p.get('en','').strip() or not p.get('es','').strip(): P.append(f"{b['id']}: pareja vacía {p}")
            m=[w for w in pal(p['en']) if w not in K]
            if m: P.append(f"{b['id']}/{p['en']}: fuera de cmudict {m}")
            if set(p)-{'en','es'}: P.append(f"{b['id']}/{p['en']}: claves extra {set(p)-{'en','es'}}")
    if P: print("PROBLEMAS:"); [print(' -',x) for x in P]; return 1
    print(f"OK: {len(new)} bancos nuevos, {sum(len(b['pares']) for b in new)} pares")
    return 0
if __name__=='__main__': sys.exit(main(sys.argv[1:]))
