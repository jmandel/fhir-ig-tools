// Oracle vectors for WP-7 helpers: valid-url isUri, lodash merge / mergeWith (SUSHI's meta._profile customizer) /
// isMatch. Run with node from a SUSHI checkout (module resolution: NODE_PATH=<sushi>/node_modules).
// usage: NODE_PATH=/srv/ig/sushi-spike/sushi/node_modules node wp7-vectors.js > src/test/resources/wp7/vectors.json
const { isUri } = require('valid-url');
const { merge, mergeWith, isMatch, isArray, cloneDeep } = require('lodash');
const uris = ['http://example.org', 'http://example.org/fhir/CodeSystem/x', 'urn:oid:1.2.3', 'urn:uuid:abc',
  'http://ex.org/a b', 'foo', '', 'MyCodeSystem', 'http://ex.org|1.0', 'mailto:me@x.org', '#local', '//no-scheme',
  'http:', 'http://', 'http://x/%41', 'http://x/%4', 'http://x/%4g', 'http://x/%%41', 'a:b', '1http://x',
  'h+t.t-p://x', 'http://x?y#z', 'http://x/é', 'file:///tmp/x', 'http:////x', 'x://a:80/p?q', 'ftp:x//y',
  'http://hl7.org/fhir/sid/icd-10', 'http://loinc.org', 'http://snomed.info/sct', 'urn:ietf:bcp:47', 'A:',
  'http://x/[y]', 'http://x/{y}', 'http://x/`y', 'http://x/^y', 'http://x/|y', 'http://x/\\y', 'a\nb:c',
  'http://ex.org/%20', 'http://ex.org/%2', 'ab-c:d', '-ab:d', '.ab:d', 'Ab:d'];
const uriCases = uris.map(u => [u, isUri(u) ?? null]);
const metaCustomizer = (a, b) => {
  if (isArray(a) && isArray(b)) {
    return a.concat(b);
  } else if (typeof a !== 'object' || typeof b !== 'object') {
    return a ?? b;
  }
};
const mergePairs = [
  [{ a: 1, b: { c: 2 } }, { b: { d: 3 }, e: 4 }],
  [{ a: [1, 2, 3] }, { a: [9] }],
  [{ a: [{ x: 1 }, { y: 2 }] }, { a: [{ z: 3 }] }],
  [{ a: 'str' }, { a: { b: 1 } }],
  [{ a: { b: 1 } }, { a: 'str' }],
  [{ a: { b: 1 } }, { a: [1, 2] }],
  [{ a: [1] }, { a: { 0: 5, k: 1 } }],
  [{ a: 1 }, { a: null }],
  [{ a: null }, { a: { b: 1 } }],
  [{ z: 1, y: 2 }, { x: 3, y: 4, w: { v: 5 } }],
  [{ resourceType: 'Patient', id: 'p', name: [{ given: ['a'] }] },
    { resourceType: 'Patient', id: 'p', name: [{ given: ['a', 'b'], family: 'f' }], gender: 'male' }],
  [{ '2': 'b', '1': 'a', k: 'v' }, { '10': 'c', '1': 'z', j: 'w' }],
  [{ a: [1, 2] }, { a: [] }],
  [{ meta: { profile: ['x'] } }, { meta: { profile: ['x', 'y'], _profile: [null, { extension: [{ url: 'e' }] }] } }]
];
const mergeCases = mergePairs.map(([o, s]) => [cloneDeep(o), cloneDeep(s), merge(cloneDeep(o), cloneDeep(s))]);
const mergeWithPairs = [
  [{ extension: [{ url: 'a' }] }, { extension: [{ url: 'b' }] }],
  [{ id: 'x' }, { id: 'y' }],
  [{ id: null }, { id: 'y' }],
  [{ a: { b: 1, c: [1] } }, { a: { b: 2, c: [2], d: 3 } }],
  [{ a: 'x' }, { a: { b: 1 } }],
  [{ a: null }, { a: null }],
  [{}, { a: [1], b: { c: 1 } }]
];
const mergeWithCases = mergeWithPairs.map(([o, s]) => [cloneDeep(o), cloneDeep(s),
  mergeWith(cloneDeep(o), cloneDeep(s), metaCustomizer)]);
const matchPairs = [
  [{ a: 1, b: 2 }, { a: 1 }], [{ a: 1 }, { a: 1, b: 2 }], [{ a: [1, 2, 3] }, { a: [3, 1] }],
  [{ a: [1, 2] }, { a: [1, 2, 3] }], [{ a: [{ x: 1, y: 2 }] }, { a: [{ x: 1 }] }],
  [{ a: { b: { c: 1, d: 2 } } }, { a: { b: { c: 1 } } }], [{ a: null }, { a: null }], [{}, { a: null }],
  [{ a: 'x' }, { a: 'y' }], [{ a: [1, 1] }, { a: [1, 1] }], [{ a: [1, 2] }, { a: [1, 1] }],
  [{ coding: [{ system: 's', code: 'c', display: 'd' }] }, { coding: [{ system: 's', code: 'c' }] }],
  [{ a: [] }, { a: {} }], [{ a: {} }, { a: [] }], [{ a: 1 }, {}], [{ a: [{}] }, { a: [] }],
  [{ a: 1.5 }, { a: 1.5 }], [{ a: true }, { a: 'true' }], [{ a: { b: [] } }, { a: { b: [] } }]
];
const matchCases = matchPairs.map(([o, s]) => [o, s, isMatch(o, s)]);
process.stdout.write(JSON.stringify({ uriCases, mergeCases, mergeWithCases, matchCases }) + '\n');
