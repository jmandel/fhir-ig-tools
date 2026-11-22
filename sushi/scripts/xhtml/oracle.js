// WP-9 oracle: run html-minifier-terser 5.1.1 (SUSHI's options) and sax 1.6.1 strict on each input.
// usage: node oracle.js <sushi-node_modules> < inputs.ndjson > results.ndjson
// input line: a JSON string; output line: [minified-or-null, minifyError-or-null, saxError-or-null, saxThrow-or-null]
'use strict';
const nm = process.argv[2];
const { minify } = require(nm + '/html-minifier-terser');
const sax = require(nm + '/sax');
const lines = require('fs').readFileSync(0, 'utf8').split('\n');
const out = [];
for (const line of lines) {
  if (!line) continue;
  const v = JSON.parse(line);
  let m = null, me = null, s = null, st = null;
  try { m = minify(v, { collapseWhitespace: true, html5: false, keepClosingSlash: true }); } catch (e) { me = String(e && e.message); }
  try { const p = sax.parser(true).write(v); s = p.error == null ? null : p.error.message; } catch (e) { st = String(e && e.message); }
  out.push(JSON.stringify([m, me, s, st]));
}
process.stdout.write(out.join('\n') + (out.length ? '\n' : ''));
