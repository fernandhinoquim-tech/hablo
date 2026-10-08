import json,sys
old=json.load(open('scenarios.base.json'))['scenarios']; ids={s['id'] for s in old}
P=[]
for s in json.load(open(sys.argv[1]))['scenarios']:
    w=s.get('id')
    for k in ("id","title","emoji","level","goalEs","role","opening"):
        if not str(s.get(k,'')).strip(): P.append(f"{w}: falta {k}")
    for k in ("targets","watch","help"):
        if not s.get(k): P.append(f"{w}: {k} vacío")
    for h in s.get('help',[]):
        if not h.get('en') or not h.get('es'): P.append(f"{w}: help incompleto")
    if w in ids: P.append(f"{w}: id repetido")
    ids.add(w)
    if s.get('level')!='B2': P.append(f"{w}: level")
print('\n'.join(P) or 'OK')
