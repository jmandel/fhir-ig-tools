// WP-2 gate oracle: run TS SUSHI 3.21.1's importer on one FSH folder and write
//   <out>/tank.json     canonical dump of the FSHDocuments (sorted keys, $class, $map, $bigint, $regex, $num)
//   <out>/log.txt       console log lines of the import (level padded, as printed; no colours)
//   <out>/_preprocessed writePreprocessedFSH(out, inDir, tank) straight after import (no export)
// usage: node tank-dump.js <sushi-dist-dir> <input/fsh dir> <out dir>
// The Java side (src/test/.../importer/TankDump.java) writes the same three things; scripts/tank-dump.sh diffs them.
'use strict';
const fs = require('fs');
const path = require('path');
const [, , DIST, IN, OUT] = process.argv;
const lines = [];
// capture console output exactly as printed (winston Console transport -> console._stdout / console.log)
const origWrite = process.stdout.write.bind(process.stdout);
process.stdout.write = (chunk, ...rest) => { lines.push(String(chunk)); return true; };
const { importText, FSHTank, RawFSH } = require(path.join(DIST, 'import'));
const { writePreprocessedFSH, getFilesRecursive } = require(path.join(DIST, 'utils', 'Processing'));
const { logger } = require(path.join(DIST, 'utils', 'FSHLogger'));
logger.transports.forEach(t => { if (t.log) { const orig = t.log.bind(t); t.log = (info, cb) => { lines.push(`${info.level.padEnd(5)} ${info.message}\n`); if (cb) cb(); return true; }; } });

function dump(v) {
  if (v === undefined || typeof v === 'function') return undefined;
  if (v === null) return null;
  if (typeof v === 'bigint') return { $bigint: v.toString() };
  if (typeof v === 'number') return Number.isFinite(v) ? v : { $num: String(v) };
  if (v instanceof RegExp) return { $regex: v.source };
  if (v instanceof Map) return { $map: [...v.entries()].map(([k, x]) => [k, dump(x)]) };
  if (Array.isArray(v)) return v.map(x => { const d = dump(x); return d === undefined ? null : d; });
  if (typeof v === 'object') {
    const o = {};
    if (v.constructor && v.constructor.name !== 'Object') o.$class = v.constructor.name;
    for (const k of Object.keys(v).sort()) {
      const d = dump(v[k]);
      if (d !== undefined && d !== null) o[k] = d;
    }
    return o;
  }
  return v;
}

const files = getFilesRecursive(IN).filter(f => f.endsWith('.fsh'));
const raws = files.map(f => new RawFSH(fs.readFileSync(f, 'utf8'), f));
const docs = importText(raws);
const tank = new FSHTank(docs, { canonical: 'http://example.org', version: '1.0.0', FSHOnly: true });
tank.checkDuplicateNameEntities();
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(path.join(OUT, 'tank.json'), JSON.stringify(dump(docs), null, 1) + '\n');
writePreprocessedFSH(OUT, IN, tank);
process.stdout.write = origWrite;
fs.writeFileSync(path.join(OUT, 'log.txt'), lines.join(''));
