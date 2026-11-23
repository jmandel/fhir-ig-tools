// Extract the xhtml corpus for the WP-9 differential: every FSH string literal that looks like markup (contains '<'),
// unescaped exactly like SUSHI's FSHImporter (extractString / extractMultilineString), plus every narrative
// `text.div` string in JSON files. usage: node extract-corpus.js <root-dir>... > corpus.ndjson (one JSON string/line)
'use strict';
const fs = require('fs');
const path = require('path');

function unescapeUnicode(match, p1) { return String.fromCharCode(parseInt(p1.slice(1), 16)); }
function unescapeQuotedString(str) {
  const strNoQuotes = str.slice(1, str.length - 1);
  return strNoQuotes.split(/\\\\/g).map(s => s
    .replace(/\\(u[A-F,a-f,0-9]{4})/g, unescapeUnicode)
    .replace(/\\"/g, '"').replace(/\\n/g, '\n').replace(/\\r/g, '\r').replace(/\\t/g, '\t')).join('\\');
}
function extractMultilineString(mlstr) {
  mlstr = mlstr.slice(3, -3);
  let lines = mlstr.split(/\r?\n/);
  lines = lines.map(l => l.replace(/\\(u[A-F,a-f,0-9]{4})/g, unescapeUnicode)
    .replace(/\\n/g, '\n').replace(/\\r/g, '\r').replace(/\\t/g, '\t'));
  if (lines[0].search(/\S/) === -1) lines.shift();
  if (lines.length > 0 && lines[lines.length - 1].search(/\S/) === -1) lines.pop();
  lines = lines.map(l => (/^\s*$/.test(l) ? '' : l));
  const minSpaces = lines.reduce((min, l) => {
    if (l.length === 0) return min;
    const n = l.search(/\S/); return min === -1 || n < min ? n : min;
  }, -1);
  return lines.map(l => (l.length ? l.slice(minSpaces) : l)).join('\n');
}

const seen = new Set();
function emit(s) {
  if (typeof s !== 'string' || !s.includes('<') || seen.has(s)) return;
  seen.add(s);
  process.stdout.write(JSON.stringify(s) + '\n');
}
function fromFsh(text) {
  // strip // and /* */ comments outside strings while scanning
  const re = /"""[\s\S]*?"""|"(?:\\.|[^"\\\n])*"|\/\/[^\n]*|\/\*[\s\S]*?\*\//g;
  let m;
  while ((m = re.exec(text))) {
    const t = m[0];
    if (t.startsWith('"""')) emit(extractMultilineString(t));
    else if (t.startsWith('"')) emit(unescapeQuotedString(t));
  }
}
function fromJson(v) {
  if (Array.isArray(v)) v.forEach(fromJson);
  else if (v && typeof v === 'object') {
    if (v.text && typeof v.text === 'object' && typeof v.text.div === 'string') emit(v.text.div);
    Object.values(v).forEach(fromJson);
  }
}
function walk(p) {
  const st = fs.statSync(p);
  if (st.isDirectory()) { fs.readdirSync(p).sort().forEach(e => walk(path.join(p, e))); return; }
  try {
    if (p.endsWith('.fsh')) fromFsh(fs.readFileSync(p, 'utf8'));
    else if (p.endsWith('.json')) fromJson(JSON.parse(fs.readFileSync(p, 'utf8').replace(/^﻿/, '')));
  } catch (e) { /* not JSON etc. */ }
}
process.argv.slice(2).forEach(walk);
process.stderr.write(`extracted ${seen.size} distinct values\n`);
