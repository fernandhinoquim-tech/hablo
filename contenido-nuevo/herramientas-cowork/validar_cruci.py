import json,sys,collections
def check(c,bancos):
    P=[];g={}
    b=bancos.get(c['banco'])
    if not b: return [f"{c['id']}: banco inexistente"]
    es={p['en']:p['es'] for p in b['pares']}
    if not 5<=len(c['palabras'])<=8: P.append(f"{c['id']}: {len(c['palabras'])} palabras")
    if c['filas']>10 or c['columnas']>10: P.append(f"{c['id']}: rejilla grande")
    ens=[p['en'] for p in c['palabras']]
    if len(set(ens))!=len(ens): P.append(f"{c['id']}: palabra repetida")
    starts={}
    for p in c['palabras']:
        if es.get(p['en'])!=p['pista']: P.append(f"{c['id']}: {p['en']} pista no coincide con el banco")
        dr,dc=(0,1) if p['dir']=='H' else (1,0)
        for i,ch in enumerate(p['en']):
            cell=(p['fila']+i*dr,p['col']+i*dc)
            if not(0<=cell[0]<c['filas'] and 0<=cell[1]<c['columnas']): P.append(f"{c['id']}: {p['en']} se sale")
            if g.get(cell,ch)!=ch: P.append(f"{c['id']}: choque en {cell}")
            g[cell]=ch
        k=(p['fila'],p['col'])
        if k in starts and starts[k]!=p['numero']: P.append(f"{c['id']}: numeración inconsistente")
        starts[k]=p['numero']
    # numeración clásica
    exp={s:i+1 for i,s in enumerate(sorted(starts))}
    if exp!=starts: P.append(f"{c['id']}: numeración no clásica")
    # corridas
    runs=set()
    for (r,cc) in g:
        for dr,dc,d in ((0,1,'H'),(1,0,'V')):
            if (r-dr,cc-dc) in g: continue
            w='';y,x=r,cc
            while (y,x) in g: w+=g[(y,x)]; y+=dr; x+=dc
            if len(w)>=2: runs.add((w,r,cc,d))
    words={(p['en'],p['fila'],p['col'],p['dir']) for p in c['palabras']}
    if runs!=words: P.append(f"{c['id']}: corridas no coinciden: sobran {runs-words} faltan {words-runs}")
    # conectividad
    seen=set();st=[next(iter(g))]
    while st:
        z=st.pop()
        if z in seen: continue
        seen.add(z); y,x=z
        st+= [n for n in ((y+1,x),(y-1,x),(y,x+1),(y,x-1)) if n in g]
    if len(seen)!=len(g): P.append(f"{c['id']}: no conectado")
    return P
def render(c):
    g=[['·']*c['columnas'] for _ in range(c['filas'])]
    for p in c['palabras']:
        dr,dc=(0,1) if p['dir']=='H' else (1,0)
        for i,ch in enumerate(p['en']): g[p['fila']+i*dr][p['col']+i*dc]=ch.upper()
    return "\n".join(' '.join(r) for r in g)
if __name__=='__main__':
    v=json.load(open('vocab_total.json')); B={b['id']:b for b in v['bancos']}
    d=json.load(open(sys.argv[1] if len(sys.argv)>1 else 'crucigramas.json'))['crucigramas']
    P=[x for c in d for x in check(c,B)]
    ids=collections.Counter(c['id'] for c in d)
    P+= [f"id repetido {k}" for k,n in ids.items() if n>1]
    print(P or 'OK', len(d), 'crucigramas', collections.Counter(len(c['palabras']) for c in d), collections.Counter(c['level'] for c in d))
    for c in d[:2]: print(c['title']); print(render(c)); print([(p['numero'],p['dir'],p['pista']) for p in c['palabras']])
