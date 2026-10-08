"""Réplica en Python de Correccion.kt (fichas, sueltaEstricta, suelta, acepta) y normalizeAnswer.
Al día con Correccion.kt del 24-09: incluye must've…haven't. OJO: la app además lee 'd como would O had y 's como is O has
(bifurca); aquí suelta() solo prueba would/is: sirve para duplicados (estricta) y como aproximación para aceptar."""
import re, unicodedata
CONTR=[("i'm","i am"),("you're","you are"),("we're","we are"),("they're","they are"),
 ("isn't","is not"),("aren't","are not"),("wasn't","was not"),("weren't","were not"),
 ("don't","do not"),("doesn't","does not"),("didn't","did not"),
 ("can't","can not"),("cannot","can not"),("couldn't","could not"),
 ("won't","will not"),("wouldn't","would not"),("shouldn't","should not"),
 ("i'll","i will"),("you'll","you will"),("he'll","he will"),("she'll","she will"),
 ("it'll","it will"),("we'll","we will"),("they'll","they will"),
 ("i've","i have"),("you've","you have"),("we've","we have"),("they've","they have"),
 ("let's","let us"),
 ("must've","must have"),("should've","should have"),("would've","would have"),("could've","could have"),("might've","might have"),
 ("hadn't","had not"),("hasn't","has not"),("haven't","have not")]
SUJ=["i","you","he","she","it","we","they","what","who","where","when","how","why","that","there","here"]
AMB=[(f"{s}'s",f"{s} is") for s in SUJ if s!="i"]+[(f"{s}'d",f"{s} would") for s in SUJ]
UN=["zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen"]
DEC={20:"twenty",30:"thirty",40:"forty",50:"fifty",60:"sixty",70:"seventy",80:"eighty",90:"ninety"}
def letras(n):
    if n<0 or n>100: return None
    if n<20: return UN[n]
    if n==100: return "one hundred"
    if n%10==0: return DEC[n]
    return DEC[n//10*10]+"-"+UN[n%10]
def normalize(t):
    t=unicodedata.normalize("NFD",t.lower())
    t="".join(c for c in t if unicodedata.category(c)!="Mn").replace("’","'")
    t="".join(c for c in t if c.isalnum() or c in " '")
    return re.sub(r"\s+"," ",t.strip())
def numeros(tok):
    out=[];i=0
    while i<len(tok):
        t=tok[i]
        if t.isdigit():
            l=letras(int(t))
            if l is not None: out+=l.split(" "); i+=1; continue
        if t in DEC.values() and i+1<len(tok) and tok[i+1] in UN and 1<=UN.index(tok[i+1])<=9:
            out.append(t+"-"+tok[i+1]); i+=2; continue
        if t=="a" and i+1<len(tok) and tok[i+1]=="hundred": out.append("one"); i+=1; continue
        out.append(t); i+=1
    return out
def fichas(t):
    return " ".join(numeros([x for x in normalize(t.replace("-"," ").replace("–"," ")).split(" ") if x]))
def estricta(t):
    s=" "+fichas(t)+" "
    for a,b in CONTR: s=s.replace(f" {a} ",f" {b} ")
    return s.strip()
def suelta(t):
    s=" "+estricta(t)+" "
    for a,b in AMB: s=s.replace(f" {a} ",f" {b} ")
    return s.strip()
def acepta(g,answer,accept=()):
    g=suelta(g)
    return bool(g) and (g==suelta(answer) or any(g==suelta(x) for x in accept))
