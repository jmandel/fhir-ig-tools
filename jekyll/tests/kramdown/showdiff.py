import json,sys
for l in open(sys.argv[1]):
    d=json.loads(l)
    w,g=d['want'],d['got']
    i=next((k for k in range(min(len(w),len(g))) if w[k]!=g[k]),min(len(w),len(g)))
    print('==',d['id']); print('want:',repr(w[max(0,i-200):i+150])); print('got: ',repr(g[max(0,i-200):i+150]))
