"""Genera crucigramas pequeños (5-8 palabras, rejilla <= 10x10) a partir de los bancos de vocabulario A1-A2.
Una rejilla = palabras de UN solo banco (dentro de un banco no hay casi sinónimos: la pista en español apunta a una sola palabra).
Reglas de rejilla estrictas: cada casilla ocupada pertenece a 1 o 2 palabras; dos palabras solo se tocan donde se cruzan;
ninguna palabra queda pegada por los extremos a otra letra; todo conectado."""
import json,random,re,sys
MAXN=10
def ok_word(en): return re.fullmatch(r"[a-z]{3,9}",en) is not None
def build(words,rnd,tries=400):
    best=None
    for _ in range(tries):
        ws=words[:]; rnd.shuffle(ws); ws.sort(key=len,reverse=True)
        grid={}; placed=[]
        def can(w,r,c,d):
            dr,dc=(0,1) if d=='H' else (1,0)
            cells=[(r+i*dr,c+i*dc) for i in range(len(w))]
            rs=[x[0] for x in cells]+[k[0] for k in grid]; cs=[x[1] for x in cells]+[k[1] for k in grid]
            if max(rs)-min(rs)+1>MAXN or max(cs)-min(cs)+1>MAXN: return None
            before=(r-dr,c-dc); after=(r+len(w)*dr,c+len(w)*dc)
            if before in grid or after in grid: return None
            cross=0
            for i,(y,x) in enumerate(cells):
                ch=grid.get((y,x))
                if ch:
                    if ch[0]!=w[i]: return None
                    if d in ch[1]: return None
                    cross+=1
                else:
                    # vecinos perpendiculares vacíos
                    n1=(y+dc,x+dr); n2=(y-dc,x-dr)
                    if n1 in grid or n2 in grid: return None
            return cross
        first=ws[0]; 
        for i,ch in enumerate(first): grid[(0,i)]=(ch,{'H'})
        placed.append((first,0,0,'H'))
        for w in ws[1:]:
            opts=[]
            for (y,x),(ch,dirs) in list(grid.items()):
                for i,wc in enumerate(w):
                    if wc!=ch: continue
                    for d in ('H','V'):
                        if d in dirs: continue
                        r,c=(y,x-i) if d=='H' else (y-i,x)
                        k=can(w,r,c,d)
                        if k: opts.append((k,r,c,d))
            if not opts: continue
            opts.sort(key=lambda o:-o[0]); k,r,c,d=opts[0] if rnd.random()<0.7 else rnd.choice(opts)
            dr,dc=(0,1) if d=='H' else (1,0)
            for i,ch in enumerate(w):
                cell=(r+i*dr,c+i*dc)
                if cell in grid: grid[cell][1].add(d)
                else: grid[cell]=(ch,{d})
            placed.append((w,r,c,d))
            if len(placed)>=8: break
        if len(placed)>=5 and (best is None or len(placed)>len(best[1]) or (len(placed)==len(best[1]) and area(grid)<area(best[0]))):
            best=(dict(grid),list(placed))
    return best
def area(g):
    rs=[k[0] for k in g]; cs=[k[1] for k in g]
    return (max(rs)-min(rs)+1)*(max(cs)-min(cs)+1)
def main():
    rnd=random.Random(17)
    v=json.load(open('vocab_total.json'))
    out=[]
    for b in v['bancos']:
        if b['level'] not in ('A1','A2'): continue
        pares=[p for p in b['pares'] if ok_word(p['en'])]
        if len(pares)<6: continue
        es_of={p['en']:p['es'] for p in pares}
        pool=list(es_of)
        usados=set()
        for n in range(2 if len(pool)>=14 else 1):
            cand=[w for w in pool if w not in usados] if n else pool
            if len(cand)<6: break
            res=build(cand,rnd)
            if not res: continue
            grid,placed=res
            rs=[k[0] for k in grid]; cs=[k[1] for k in grid]; r0,c0=min(rs),min(cs)
            # numeración clásica: por posición de inicio (fila, col)
            starts=sorted({(r-r0,c-c0) for _,r,c,_ in placed})
            num={s:i+1 for i,s in enumerate(starts)}
            palabras=[{"numero":num[(r-r0,c-c0)],"dir":d,"fila":r-r0,"col":c-c0,"en":w,"pista":es_of[w]} for w,r,c,d in placed]
            palabras.sort(key=lambda p:(p['dir'],p['numero']))
            usados|={w for w,*_ in placed}
            out.append({"id":f"cruci-{b['id']}-{n+1}","title":f"{b['title']} · {n+1}","level":b['level'],"banco":b['id'],
                        "filas":max(rs)-r0+1,"columnas":max(cs)-c0+1,"palabras":palabras})
    json.dump({"_comentario":"Crucigramas de Cowork (17-09-2026). Cada uno sale de UN banco de vocabulario.json (la pista en español apunta a una sola palabra del banco). Pistas en español, respuestas en inglés (la dirección difícil). Sin reloj. Coordenadas desde 0; dir H = horizontal, V = vertical; numero = numeración clásica por casilla de inicio.","crucigramas":out},open('crucigramas.json','w'),ensure_ascii=False,indent=1)
    print(len(out),'crucigramas')
main()
