// Oracle vectors for the utils leaf ports (lodash string helpers, title-case, text-table, sanitize-filename, valid-url).
// usage: node util-vectors.js <sushi-node_modules-dir> <pagenames.txt> > src/test/resources/js/util-vectors.json
const nm = process.argv[2];
const req = m => require(nm + '/' + m);
const _ = req('lodash'), { titleCase } = req('title-case'), table = req('text-table'), sanitize = req('sanitize-filename');
const validUrl = req('valid-url');
const names = require('fs').readFileSync(process.argv[3], 'utf8').split('\n').filter(Boolean);
const extra = ['camelCaseName', 'XMLHttpRequest', 'version2Notes', 'the-end-of-the-world', 'a guide for and to the ig',
  'Über straße', 'naïve café', 'ÆØÅ-oversigt', 'émoji 😀 page', "don't stop", 'IHE_ITI.MHD', '1st-2nd_3rd 4th', 'http://x.org: a',
  'NUL.txt', 'con', '...', 'a/b\\c:d*e?f"g<h>i|j', 'trailing. . ', 'x\u0001y\u0085z', 'é'.repeat(200), '😀'.repeat(100), ''];
const strs = names.concat(extra);
const words = strs.map(s => [s, _.words(s), titleCase(_.words(s.replace(/\.[^.]+$/, '')).join(' ')), titleCase(s)]);
const san = strs.map(s => [s, sanitize(s), sanitize(s, { replacement: '-' })]);
const pads = ['', 'a', 'abc', '12 Errors ', '0 Warnings'].map(s => [s, _.pad(s, 13), _.padStart(s, 12), _.padEnd(s, 36),
  _.upperFirst(s), _.escapeRegExp(s + '.*+?^$(){}[]|\\')]);
const uris = ['http://hl7.org/fhir', 'urn:oid:1.2.3', 'urn:uuid:abc', 'foo', 'http://a b', 'a:b', '1a:b', 'http:', ':x',
  'http://x/%2', 'http://x/%zz', 'http://x/%2:', 'mailto:a@b.c', 'http://x?y#z', 'http:x//y', 'x:////y', 'http://x.org/é',
  '#frag', 'StructureDefinition/foo', 'http://[::1]/a'].map(u => [u, validUrl.isUri(u) ?? null]);
const tables = [
  [['Output File', 'Name', 'Type', 'FSH File', 'Lines'], ['StructureDefinition-a.json', 'A', 'Profile', 'a.fsh', '1 - 20'],
    ['ValueSet-very-long-name.json', 'VeryLong', 'ValueSet', 'dir/b.fsh', '5'], ['x', '', '', '', '']],
  [['a', 'b'], ['ccc'], []]];
const tbl = tables.map(t => [t, table(t)]);
process.stdout.write(JSON.stringify({ words, san, pads, uris, tbl }) + '\n');
