// Node oracle for WP-3: loadConfigurationFromIgResource of TS SUSHI 3.21.1 on IG roots without sushi-config.yaml.
// usage (reference image, VM): node scripts/oracle/ig-resource-oracle.js <sushi checkout> <igRoot>... > <out.json>
// Output per igRoot: {config (tagged JSON, see config-oracle.js) | threw, logs}.
const path = require('path');
const sushi = process.argv[2];
const { loadConfigurationFromIgResource } = require(path.join(sushi, 'dist/import'));
const { logger } = require(path.join(sushi, 'dist/utils/FSHLogger'));
let logs = [];
logger.transports[0].write = info => {
  logs.push({ level: info.level, message: info.message });
  return true;
};
function dump(v) {
  if (v === undefined) return { $u: 1 };
  if (v === null || typeof v === 'string' || typeof v === 'boolean') return v;
  if (typeof v === 'number') return Number.isFinite(v) && !Object.is(v, -0) ? v : { $n: Object.is(v, -0) ? '-0' : String(v) };
  if (Array.isArray(v)) return v.map(dump);
  if (typeof v === 'object') return { $o: Object.keys(v).map(k => [k, dump(v[k])]) };
  return { $other: String(v) };
}
const out = {};
for (const root of process.argv.slice(3)) {
  logs = [];
  const r = {};
  try {
    r.config = dump(loadConfigurationFromIgResource(root));
  } catch (e) {
    r.threw = { name: e.name, message: e.message };
  }
  r.logs = logs;
  out[root] = r;
}
process.stdout.write(JSON.stringify(out) + '\n');
