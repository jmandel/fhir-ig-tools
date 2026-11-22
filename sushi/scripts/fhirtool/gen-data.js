// Generates the data the Java port of fhir-tool 5.0.2's xmlToObj needs, from fhir-tool's own files (so it is
// byte-faithful to what TS SUSHI 3.21.1 / FPL 2.2.4 run):
//   1. fhirtool-types.json.gz: ParseConformance(true).parsedStructureDefinitions after ensurePropertyMetaData,
//      reduced to what ConvertToJs reads (_name, _type, _multiple, _properties), deduplicated (url/id aliases).
//   2. SaxEntities124.java body: sax 1.2.4's ENTITIES table (the full HTML set; xml-js sets strictEntities only
//      after construction, so the full table is what it uses).
// usage (in the container): node gen-data.js <fhir-tool dir> <out dir>
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const [ftDir, outDir] = process.argv.slice(2);
const { ParseConformance } = require(path.join(ftDir, 'parseConformance.js'));
const sax = require(path.join(ftDir, 'node_modules/sax'));

const p = new ParseConformance(true);
const defs = p.parsedStructureDefinitions;
function strip(prop) {
  const r = { n: prop._name, t: prop._type };
  if (prop._multiple) r.m = 1;
  if (prop._properties) r.p = prop._properties.map(strip);
  return r;
}
const unique = new Map(); // JSON text -> index
const list = [];
const keys = {};
for (const k of Object.keys(defs)) {
  const d = defs[k];
  const s = { p: (d._properties || []).map(strip) };
  if (!d._properties) s.noProps = 1;
  const text = JSON.stringify(s);
  if (!unique.has(text)) {
    unique.set(text, list.length);
    list.push(s);
  }
  keys[k] = unique.get(text);
}
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, 'fhirtool-types.json.gz'),
  zlib.gzipSync(Buffer.from(JSON.stringify({ keys: Object.keys(defs), index: keys, defs: list })), { level: 9 }));

// sax 1.2.4 ENTITIES as Java string pairs
const esc = s => [...s].map(ch => {
  const c = ch.codePointAt(0);
  if (ch === '"' || ch === '\\') return '\\' + ch; // (a \u0022 escape would end the Java string literal)
  if (c >= 0x20 && c <= 0x7e) return ch;
  return ch.split('').map(u => '\\u' + u.charCodeAt(0).toString(16).padStart(4, '0')).join('');
}).join('');
const lines = Object.keys(sax.ENTITIES).map(k => `    "${esc(k)}", "${esc(sax.ENTITIES[k])}",`);
fs.writeFileSync(path.join(outDir, 'entities.txt'), lines.join('\n') + '\n');
console.log(`types: ${Object.keys(defs).length} keys, ${list.length} distinct; entities: ${lines.length}`);
