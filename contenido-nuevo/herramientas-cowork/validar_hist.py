import json,sys,re
K=set(l.split(' ',1)[0].split('(')[0] for l in open('cmudict.dict',errors='ignore') if not l.startswith(';;;'))
def pal(t): return [w for w in re.sub(r"[^a-z' ]"," ",t.lower().replace("-"," ")).split() if w]
def main(files):
    old=json.load(open('historias.base.json'))['tandas']
    tids={t['id'] for t in old}; hids={h['id'] for t in old for h in t['historias']}
    titles={h['title'].lower() for t in old for h in t['historias']}
    P=[]
    for f in files:
        for t in json.load(open(f))['tandas']:
            w=f"tanda {t.get('id')}"
            for k in ('id','level','title'):
                if not str(t.get(k,'')).strip(): P.append(f"{w}: falta {k}")
            if t['id'] in tids: P.append(f"{w}: id repetido")
            tids.add(t['id'])
            hs=t.get('historias',[])
            if not 3<=len(hs)<=12: P.append(f"{w}: {len(hs)} historias")
            for h in hs:
                x=f"{h.get('id')}"
                for k in ('id','level','title','titleEs','trap'):
                    if not str(h.get(k,'')).strip(): P.append(f"{x}: falta {k}")
                if h['id'] in hids: P.append(f"{x}: id repetido")
                hids.add(h['id'])
                if h['title'].lower() in titles: P.append(f"{x}: título repetido")
                titles.add(h['title'].lower())
                if set(h)-{'id','level','title','titleEs','text','glosario','preguntas','retell','trap'}: P.append(f"{x}: claves extra {set(h)-{'id','level','title','titleEs','text','glosario','preguntas','retell','trap'}}")
                if not 6<=len(h.get('text',[]))<=10: P.append(f"{x}: {len(h.get('text',[]))} frases")
                m=[y for s in h.get('text',[]) for y in pal(s) if y not in K]
                if m: P.append(f"{x}: palabras del texto fuera de cmudict (Piper igual las lee, pero revisa): {sorted(set(m))}")
                qs=h.get('preguntas',[])
                if len(qs)!=3: P.append(f"{x}: {len(qs)} preguntas")
                for q in qs:
                    o=q.get('options',[])
                    if len(o)!=3 or len(set(o))!=3 or q.get('answer') not in o or not q.get('q'): P.append(f"{x}: pregunta mal {q.get('q')}")
                r=h.get('retell') or {}
                if not str(r.get('prompt_es','')).strip() or len(r.get('pistas',[]))<2: P.append(f"{x}: retell")
                g=h.get('glosario',[])
                if len(g)<2: P.append(f"{x}: glosario <2")
                for p in g:
                    if not p.get('en','').strip() or not p.get('es','').strip(): P.append(f"{x}: glosario vacío")
                    mm=[y for y in pal(p['en']) if y not in K]
                    if mm: P.append(f"{x}: glosario fuera de cmudict {mm}")
                    if not any(p['en'].lower() in s.lower() for s in h['text']): P.append(f"{x}: glosario {p['en']!r} no aparece en el texto")
    if P: print("PROBLEMAS:"); [print(' -',y) for y in P]; return 1
    print("OK"); return 0
if __name__=='__main__': sys.exit(main(sys.argv[1:]))
