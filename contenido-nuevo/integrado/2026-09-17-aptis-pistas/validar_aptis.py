import json,sys,re,collections
LV={'A1','A2','B1','B2'}
def main(kind,f):
    d=json.load(open(f));P=[];ids=set();lv=collections.Counter()
    for p in d['partes']:
        for k in ('id','aptis','title','descripcion'):
            if not p.get(k): P.append(f"parte {p.get('id')}: falta {k}")
        for t in p['tareas']:
            w=t.get('id')
            if w in ids: P.append(f"{w}: id repetido")
            ids.add(w)
            if t.get('level') not in LV: P.append(f"{w}: level")
            lv[t.get('level')]+=1
            if kind!='speaking' and not t.get('why'): P.append(f"{w}: falta why")
            if kind=='reading':
                tp=t.get('tipo')
                if tp=='completar':
                    if t['text'].count('___')!=1 or len(t['options'])!=3 or len(set(t['options']))!=3 or t['answer'] not in t['options']: P.append(f"{w}: completar mal")
                elif tp=='ordenar':
                    if not t.get('primera') or sorted(t['desordenadas'])!=sorted(t['orden']) or len(t['orden'])<4 or t['desordenadas']==t['orden']: P.append(f"{w}: ordenar mal")
                elif tp=='titulos':
                    pa,ti,an=t['parrafos'],t['titulos'],t['answer']
                    if len(ti)!=len(pa)+1 or len(an)!=len(pa) or len(set(an))!=len(an) or any(a not in ti for a in an): P.append(f"{w}: titulos mal")
                else: P.append(f"{w}: tipo {tp}")
            elif kind=='listening':
                tp=t.get('tipo')
                if tp not in ('dato','quien') or not t.get('audio') or not t.get('pregunta'): P.append(f"{w}: tipo/audio/pregunta")
                o=t.get('options',[])
                if len(o)!=3 or len(set(o))!=3 or t.get('answer') not in o: P.append(f"{w}: opciones")
                if tp=='quien' and sorted(o)!=sorted(["El hombre","La mujer","Los dos"]): P.append(f"{w}: quien opciones")
                if tp=='quien' and not ('MAN:' in t['audio'] and 'WOMAN:' in t['audio']): P.append(f"{w}: quien sin MAN/WOMAN")
                if re.search(r"\d",t.get('audio','')): P.append(f"{w}: cifras en el audio")
            elif kind=='speaking':
                for k in ('prompt_en','prompt_es'):
                    if not t.get(k): P.append(f"{w}: falta {k}")
                if t.get('prep_seg') not in (0,30,60) or t.get('hablar_seg') not in (30,45,60,120): P.append(f"{w}: tiempos")
                r=t.get('rubrica',[])
                if len(r)<3 or any(not x.strip().endswith('?') for x in r): P.append(f"{w}: rubrica")
    print('\n'.join(P) if P else f"OK {len(ids)} tareas {dict(lv)}")
main(sys.argv[1],sys.argv[2])
