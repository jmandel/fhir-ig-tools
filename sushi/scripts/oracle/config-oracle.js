// Node oracle for WP-3: YAML.parse (yaml@1.10) and importConfiguration of TS SUSHI 3.21.1 on a set of configs.
// usage (reference image, VM): node scripts/oracle/config-oracle.js <sushi checkout> <inputs.json> > <out.json>
//   inputs.json: [{"name": ..., "file": "sushi-config.yaml", "text": ...}, ...]
// Output per name: {yaml | yamlError: {name, message}, config | threw, logs: [{level, message}]}.
// Values are dumped with tagged JSON (dump() below) so key order, undefined-valued keys, -0, NaN, Infinity survive;
// CorpusConfigTest compares the Java port against this.
const path = require('path');
const fs = require('fs');
const sushi = process.argv[2];
const YAML = require(path.join(sushi, 'node_modules/yaml'));
const { importConfiguration } = require(path.join(sushi, 'dist/import'));
const { logger } = require(path.join(sushi, 'dist/utils/FSHLogger'));
let logs = [];
logger.transports[0].write = info => {
  logs.push({ level: info.level, message: info.message });
  return true;
};
// tagged JSON: objects as {"$o": [[k, v], ...]} in own-key order; undefined {"$u":1}; non-finite/-0 numbers {"$n": s}
function dump(v) {
  if (v === undefined) return { $u: 1 };
  if (v === null || typeof v === 'string' || typeof v === 'boolean') return v;
  if (typeof v === 'number') {
    if (!Number.isFinite(v) || Object.is(v, -0)) return { $n: Object.is(v, -0) ? '-0' : String(v) };
    return v;
  }
  if (typeof v === 'bigint') return { $big: v.toString() };
  if (Array.isArray(v)) return v.map(dump);
  if (typeof v === 'object') {
    const o = Object.keys(v).map(k => [k, dump(v[k])]);
    const ctor = v.constructor && v.constructor.name;
    return ctor && ctor !== 'Object' ? { $o: o, $class: ctor } : { $o: o };
  }
  return { $other: String(v) };
}
const inputs = JSON.parse(fs.readFileSync(process.argv[3], 'utf8'));
const out = {};
const origWarn = process.emitWarning;
for (const inp of inputs) {
  const r = {};
  const warnings = [];
  process.emitWarning = w => warnings.push(String(w && w.message ? w.message : w));
  try {
    r.yaml = dump(YAML.parse(inp.text));
  } catch (e) {
    r.yamlError = { name: e.name, message: e.message };
  }
  r.yamlWarnings = warnings.splice(0);
  logs = [];
  try {
    r.config = dump(importConfiguration(inp.text, inp.file));
  } catch (e) {
    r.threw = { name: e.name, message: e.message };
  }
  process.emitWarning = origWarn;
  r.logs = logs;
  out[inp.name] = r;
}
process.stdout.write(JSON.stringify(out) + '\n');
