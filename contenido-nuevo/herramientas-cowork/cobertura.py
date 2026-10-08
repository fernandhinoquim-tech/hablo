import json,re,wordfreq,cefrpy,sys
from lemminflect import getAllLemmas
A=cefrpy.CEFRAnalyzer()
def lem(w):
    L=getAllLemmas(w)
    for k in ('VERB','NOUN','ADJ','ADV'):
        if k in L: return L[k][0].lower()
    return w
def words(t): return [w.split("'")[0] for w in re.findall(r"[a-z]+(?:'[a-z]+)?",t.lower())]
def curso(path):
    d=json.load(open(path)); S=set()
    for lv in d['levels']:
        for u in lv['units']:
            for l in u['lessons']:
                for e in l['exercises']:
                    for k in ('audio','text','answer','sentence'):
                        if isinstance(e.get(k),str):
                            S|={lem(w) for w in words(e[k]) if w}
    return S
def bancos(path):
    S=set()
    for b in json.load(open(path))['bancos']:
        if b['level'] in ('A1','A2'):
            for p in b['pares']: S|={lem(w) for w in words(p['en'])}
    return S
if __name__=='__main__':
    C=curso('curriculum.TODO.json'); B=bancos(sys.argv[1] if len(sys.argv)>1 else 'vocabulario.json')
    T=C|B
    print('lemas curso',len(C),'bancos A1-A2',len(B),'total',len(T))
    K=set(l.split(' ',1)[0].split('(')[0] for l in open('cmudict.dict',errors='ignore') if not l.startswith(';;;'))
    cand=[]
    seen=set()
    for w in wordfreq.top_n_list('en',25000):
        if not re.fullmatch(r'[a-z]{2,}',w): continue
        l=lem(w)
        if l in seen or l in T: continue
        seen.add(l)
        if not A.is_word_in_database(l) or l not in K: continue
        lv=A.get_average_word_level_float(l)
        if lv is None or lv>2.0: continue
        cand.append((l,round(lv,2),round(wordfreq.zipf_frequency(l,'en'),2)))
    print('candidatos A1-A2 no cubiertos',len(cand))
    json.dump(cand,open('candidatos.json','w'))
    print(' '.join(c[0] for c in cand[:400]))
