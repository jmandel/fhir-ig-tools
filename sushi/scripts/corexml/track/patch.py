#!/usr/bin/env python3
"""Write harness copies of Frozen.java and FHIRDefinitions.java with XmlTrack hooks (scripts/corexml/track.sh).
usage: patch.py <repo root> <out dir>"""
import os, sys
root, out = sys.argv[1], sys.argv[2]
def sub(s, old, new):
    assert old in s, old
    return s.replace(old, new, 1)
f = open(os.path.join(root, 'common/src/main/java/org/hl7/fhir/common/js/Frozen.java')).read()
f = sub(f, 'public static Object deepFreeze(Object v) {\n',
        'public static Object deepFreeze(Object v) {\n    return deepFreeze(v, v instanceof XmlTrack.Root r ? r.label + "\\u0001" : null);\n  }\n\n'
        '  private static Object deepFreeze(Object v, String track) {\n')
f = sub(f, 'FrozenObject o = new FrozenObject();\n', 'FrozenObject o = new FrozenObject();\n      o.track = track;\n')
f = sub(f, 'o.init((String) e.getKey(), deepFreeze(e.getValue()));',
        'o.init((String) e.getKey(), deepFreeze(e.getValue(), track == null ? null : track + "." + e.getKey()));')
f = sub(f, 'for (Object x : l) a.init(deepFreeze(x));', 'for (Object x : l) a.init(deepFreeze(x, track == null ? null : track + "[]"));')
f = sub(f, '    boolean frozen;\n\n    void init(String k, Object v) {',
        '''    boolean frozen;
    String track;

    private void r(Object k) { if (frozen && track != null) XmlTrack.read(track, String.valueOf(k)); }
    @Override public Object get(Object k) { r(k); return super.get(k); }
    @Override public Object getOrDefault(Object k, Object d) { r(k); return super.getOrDefault(k, d); }
    @Override public boolean containsKey(Object k) { r(k); return super.containsKey(k); }
    @Override public void forEach(java.util.function.BiConsumer<? super String, ? super Object> a) { r("*"); super.forEach(a); }

    void init(String k, Object v) {''')
f = sub(f, '@Override public Set<String> keySet() {', '@Override public Set<String> keySet() { r("*");')
f = sub(f, '@Override public Collection<Object> values() {', '@Override public Collection<Object> values() { r("*");')
f = sub(f, '@Override public Set<Map.Entry<String, Object>> entrySet() {\n', '@Override public Set<Map.Entry<String, Object>> entrySet() {\n      r("*");\n')
# put0/computeIfAbsent call containsKey/get: harmless (they throw or read)
d = open(os.path.join(root, 'sushi/src/main/java/org/hl7/fhir/sushi/fhirdefs/FHIRDefinitions.java')).read()
d = sub(d, '    if (info == null) return null;\n    Metadata m = new Metadata();',
        '    if (info == null) return null;\n    org.hl7.fhir.common.js.XmlTrack.meta(info.resourceType, info.id, info.resourcePath);\n    Metadata m = new Metadata();')
for rel, s in (('org/hl7/fhir/common/js/Frozen.java', f), ('org/hl7/fhir/sushi/fhirdefs/FHIRDefinitions.java', d)):
    p = os.path.join(out, rel); os.makedirs(os.path.dirname(p), exist_ok=True); open(p, 'w').write(s)
