// Differential oracle for the Java port of fhir-tool 5.0.2 xmlToObj (fhirdefs.xml.FhirXmlToJs).
// usage: node oracle.js <fhir-tool dir> inputs <corpus dir> <inputs.ndjson> [mutations-per-file] [seed]
//          -> writes inputs (corpus files + mutated variants) as {id, xml}
//        node oracle.js <fhir-tool dir> run <inputs.ndjson> <out.ndjson>
//          -> {id, out} where out = JSON.stringify(new Fhir().xmlToObj(xml)) or "ERR:" + message
//             (and "STDOUT:" lines fhir-tool prints, e.g. its console.log('do something'))
'use strict';
const fs = require('fs');
const path = require('path');
const [ftDir, mode, ...args] = process.argv.slice(2);

function walk(d, out) {
  for (const e of fs.readdirSync(d, { withFileTypes: true }).sort((a, b) => (a.name < b.name ? -1 : 1))) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.isFile() && /\.xml$/i.test(e.name)) out.push(p);
  }
  return out;
}

let seed = 1;
function rnd(n) {
  seed = (seed * 1103515245 + 12345) & 0x7fffffff;
  return seed % n;
}

const MUTATIONS = [
  x => x.replace(/<(\w+)/, '<UnknownThing'), // unknown resource type
  x => x.replace(/<id value="[^"]*"\/>/, m => m + '<notAnElement value="x"/>'),
  x => x.replace(/(<\w+ value=")([^"]*)("\/>)/, '$1$2$3<!-- a comment -->$1$2$3'),
  x => x.replace(/<status value="([^"]*)"\/>/, '<!-- c1 --><!-- c2 --><status id="s1" value="$1"><extension url="http://x"><valueString value="y"/></extension></status>'),
  x => x.replace(/value="(\d+)"/, 'value="$1.50"'),
  x => x.replace(/value="(\d+)"/, 'value="1e3"'),
  x => x.replace(/value="(true|false)"/, 'value="TRUE"'),
  x => x.replace(/value="(true|false)"/, ''),
  x => x.replace(/<div([^>]*)>/, '<div$1><!-- note -->&nbsp;&copy;<![CDATA[ a < b ]]>&amp;lt;'),
  x => x.replace(/<div([^>]*)>/, '<div$1 xml:space="preserve">'),
  x => x.replace(/<div([^>]*)>[\s\S]*?<\/div>/, '<div$1/>'),
  x => '﻿' + x,
  x => x.replace(/^(<\?xml[^>]*\?>)?/, '$1<?xml-stylesheet href="a.xsl"?><!DOCTYPE foo>'),
  x => x.replace(/<\/(\w+)>\s*$/, ''), // unclosed root
  x => x.replace(/<(\w+) /, '<$1 bad '), // attribute without value
  x => x.replace(/value="/, "value='").replace(/"\/>/, "'/>"),
  x => x.replace(/value="([^"]*)"/, 'value="$1&#10;line2&#x9;tab"'),
  x => x.replace(/value="([^"]*)"/, 'value="$1\r\nraw newline"'),
  x => x.replace(/&/, '&bogus;'),
  x => x.replace(/<(\w+)>/, '<$1 id="x" id="y">'),
  x => x.replace(/<extension url="([^"]*)">/, '<extension url="$1" url="dup">'),
  x => x.replace(/(<(\w+) value="[^"]*"\/>)/, '$1$1$1'),
  x => x.replace(/<text>/, '<text><!-- before status -->'),
  x => x.replace(/<div /, '<!-- before div --><div '),
  x => x.replace(/xmlns="http:\/\/hl7.org\/fhir"/, 'xmlns:f="http://hl7.org/fhir"'),
  x => x.replace(/<(\w+)/, '<f:$1'),
  x => x.slice(0, Math.max(1, x.length >> 1)), // truncated
  x => x.replace(/<contained>\s*<(\w+)/, '<contained><!-- c --><$1'),
  x => x.replace(/value="([^"]*)"/, 'value="&#x1F600;&#128512;"'),
  x => x.replace(/>\s+</g, '><'),
];

if (mode === 'inputs') {
  const [corpusDir, outFile, perFile = '6', s = '7'] = args;
  seed = Number(s);
  const files = walk(corpusDir, []);
  const lines = [];
  for (const f of files) {
    const rel = path.relative(corpusDir, f);
    const xml = fs.readFileSync(f).toString();
    lines.push(JSON.stringify({ id: rel, xml }));
    for (let k = 0; k < Number(perFile); k++) {
      const m = rnd(MUTATIONS.length);
      const v = MUTATIONS[m](xml);
      if (v !== xml) lines.push(JSON.stringify({ id: `${rel}#m${m}.${k}`, xml: v }));
    }
  }
  fs.writeFileSync(outFile, lines.join('\n') + '\n');
  console.log(`${files.length} files, ${lines.length} inputs`);
} else if (mode === 'run') {
  const [inFile, outFile] = args;
  const { Fhir } = require(path.join(ftDir, 'fhir.js'));
  const fhir = new Fhir();
  const out = [];
  const origLog = console.log;
  for (const line of fs.readFileSync(inFile, 'utf8').split('\n')) {
    if (!line) continue;
    const { id, xml } = JSON.parse(line);
    let res;
    const printed = [];
    console.log = (...a) => printed.push(a.join(' '));
    try {
      res = JSON.stringify(fhir.xmlToObj(xml));
      if (res === undefined) res = 'UNDEFINED';
    } catch (e) {
      res = 'ERR:' + (e && e.message !== undefined ? e.message : String(e));
    } finally {
      console.log = origLog;
    }
    if (printed.length) res = printed.map(p => 'STDOUT:' + p).join('\n') + '\n' + res;
    out.push(JSON.stringify({ id, out: res }));
  }
  fs.writeFileSync(outFile, out.join('\n') + '\n');
  console.log(`${out.length} results`);
}
