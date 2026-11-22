#!/bin/bash
# WP-7 gate: run the Java pipeline (Wp7GateTest driver, from a wp7-combo checkout) on one reference IG with the
# same frozen package snapshot + per-IG overlay as the TS reference run, then compare CodeSystem/ValueSet/instance
# files (and, for information, the rest) with TS SUSHI 3.21.1's fsh-generated/resources.
# usage: wp7-gate.sh <IG name, e.g. IHE__ITI.MHD> [combo-name]     (start under work/locks/waitfree.sh timing)
set -eu
R=$ROOT; . "$R/scripts/lib/vmdata.sh"
NAME=$1; COMBO=${2:-gate}; C=$R/work/wp7-combo-$COMBO
SP=$VMDATA/sushi-spike; SNAP=$VMDATA/determinism/snap/baseline-20261120; PKG=$SP/pkg/$NAME
G=$VMDATA/sushi-port/wp7gate/$NAME; OUT=$G/java-out
REFTAR=$SP/runs/$NAME-ref-a/out.tar; REFDIR=$VMDATA/sushi-port/ref/$NAME/fsh-generated
vm "rm -rf '$G' && mkdir -p '$G' '$PKG/upper/packages' '$PKG/work' && cp -a '$SP/src/$NAME' '$G/ig'"
vol="wp7gate-$NAME"; vol="${vol//[^A-Za-z0-9_.-]/_}"
docker volume rm -f "$vol" >/dev/null 2>&1 || true
docker volume create --driver local --opt type=overlay --opt device=overlay \
  --opt "o=lowerdir=$SNAP,upperdir=$PKG/upper,workdir=$PKG/work" "$vol" >/dev/null
MVN=$VMDATA/sushi-port/tools/apache-maven-3.9.9/bin/mvn
docker run --rm -i -v "$VMDATA:$VMDATA" -v "$C:$C:ro" -v "$vol:/root/.fhir" -w "$C" --entrypoint bash "$IMAGE" -c "
  set -e; B=$VMDATA/sushi-port/build/wp7-combo-$COMBO; mkdir -p \$B
  $MVN -q -B -Dmaven.repo.local=$VMDATA/sushi-port/m2 -Dsushi.buildDir=\$B test -Dtest=Wp7GateTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dsushi.wp7gate.ig='$G/ig' -Dsushi.wp7gate.out='$OUT' >'$G/mvn.log' 2>&1 || { tail -40 '$G/mvn.log'; exit 1; }
  mkdir -p '$G/ref'; if [ -d '$REFDIR' ]; then cp -a '$REFDIR' '$G/ref/'; else tar -C '$G/ref' -xf '$REFTAR'; fi
  python3 - '$G/ref/fsh-generated/resources' '$OUT/fsh-generated/resources' <<'PY'
import sys, os, json
ref, out = sys.argv[1], sys.argv[2]
def kind(f):
    if f.startswith('CodeSystem-'): return 'CS'
    if f.startswith('ValueSet-'): return 'VS'
    if f.startswith('StructureDefinition-'): return 'SD'
    if f.startswith('ImplementationGuide-'): return 'IG'
    return 'Instance'
def first_diff(a, b, path=''):
    if type(a) != type(b): return path or '/'
    if isinstance(a, dict):
        if list(a.keys()) != list(b.keys()):
            for k in a:
                if k not in b: return path + '/' + k + ' (missing in java)'
            for k in b:
                if k not in a: return path + '/' + k + ' (extra in java)'
            return path + ' (key order: ref ' + ','.join(list(a)[:8]) + ' / java ' + ','.join(list(b)[:8]) + ')'
        for k in a:
            d = first_diff(a[k], b[k], path + '/' + k)
            if d: return d
        return None
    if isinstance(a, list):
        if len(a) != len(b): return path + ' (len %d vs %d)' % (len(a), len(b))
        for i, (x, y) in enumerate(zip(a, b)):
            d = first_diff(x, y, path + '[%d]' % i)
            if d: return d
        return None
    return None if a == b else path + ' (%r vs %r)' % (str(a)[:60], str(b)[:60])
rf = set(os.listdir(ref)); of = set(os.listdir(out)) if os.path.isdir(out) else set()
stats = {}
for f in sorted(rf | of):
    k = kind(f); s = stats.setdefault(k, {'identical': 0, 'differ': [], 'missing': [], 'extra': []})
    if f not in of: s['missing'].append(f); continue
    if f not in rf: s['extra'].append(f); continue
    A = open(os.path.join(ref, f), 'rb').read(); B = open(os.path.join(out, f), 'rb').read()
    if A == B: s['identical'] += 1; continue
    try: d = first_diff(json.loads(A), json.loads(B)) or 'text-level (formatting)'
    except Exception as e: d = 'unparsable: %s' % e
    s['differ'].append((f, d))
for k in ('CS', 'VS', 'Instance', 'SD', 'IG'):
    if k not in stats: continue
    s = stats[k]
    print('%-8s identical %4d  differ %3d  missing %3d  extra %3d' % (k, s['identical'], len(s['differ']), len(s['missing']), len(s['extra'])))
    for f, d in s['differ'][:15]: print('   differ  ', f, '->', d)
    for f in s['missing'][:8]: print('   missing ', f)
    for f in s['extra'][:8]: print('   extra   ', f)
PY
  grep -c '^error' '$OUT/sushi.log' | sed 's/^/java errors: /'; grep -c '^warn' '$OUT/sushi.log' | sed 's/^/java warnings: /'
  TSLOG=\$(ls $SP/runs/$NAME-ref-a/sushi.log $SP/runs/$NAME-t1/sushi.log 2>/dev/null | head -1)
  python3 - \$TSLOG '$OUT/sushi.log' <<'PY'
import sys, re
# warn/error messages (with their File/Line continuation lines) in order; TS log has timing prefixes and colours.
# The IG exporter's messages (WP-10) are not produced by the gate driver and are dropped from the TS side.
def msgs(p, ts):
    out = []; cur = None
    for l in open(p, encoding='utf8', errors='replace'):
        l = re.sub(r'\x1b\[[0-9;]*m', '', l.rstrip('\n'))
        if ts: l = re.sub(r'^\s*[0-9.]+ ', '', l)
        l = re.sub(r'(File: )\S*/ig/input/', r'\1<ig>/input/', l)
        m = re.match(r'^(warn |error) (.*)', l)
        if m: cur = [m.group(1).strip(), m.group(2)]; out.append(cur)
        elif re.match(r'^(info |debug) ', l) or not l.startswith(' '): cur = None
        elif cur is not None: cur[1] += '\n' + l
    return out
ign = ('The following files were not added', 'Files not in the supported file types')
ref = [r for r in msgs(sys.argv[1], True) if not r[1].startswith(ign)]
java = msgs(sys.argv[2], False)
same = sum(1 for a, b in zip(ref, java) if a == b)
print('log: TS %d warn/error, Java %d, identical in order: %d' % (len(ref), len(java), same))
for a, b in list(zip(ref, java)) + [(r, None) for r in ref[len(java):]] + [(None, j) for j in java[len(ref):]]:
    if a != b: print('  first difference:\n   TS:  ', a, '\n   Java:', b); break
PY
"
docker volume rm -f "$vol" >/dev/null 2>&1 || true
