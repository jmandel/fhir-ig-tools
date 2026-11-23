// Lookup probe for TypeScript SUSHI 3.21.1 (WP-8, PORTING.md D1a).
// Preload with `node -r probe.js <sushi>/bin/sushi ...`. Records, without changing behaviour:
//   - the inputs of dependency loading (config.dependencies, fhirVersion, config path) and predefined-resource loading
//   - every loadPackage / loadVirtualPackage call and its status
//   - every package-DB lookup (BasePackageLoader.findResourceInfo / findResourceInfos: key + options) and its result
//     (each matching row: package#version, resourceType, id, url, version, sdFlavor, file name), deduplicated
//   - findPackageInfo(s) / findPackageJSON lookups
// Output: NDJSON at $SUSHI_PROBE_OUT (default /srv/ig/sushi-port/probe/<IG>.ts.ndjson, IG from the cwd).
'use strict';
const fs = require('fs');
const path = require('path');

const SUSHI_DIR = process.env.SUSHI_DIR || '/srv/ig/determinism/tools/sushi-3.21.1/lib/node_modules/fsh-sushi';
const FPL = path.join(SUSHI_DIR, 'node_modules/fhir-package-loader/dist');
const out = process.env.SUSHI_PROBE_OUT || (() => {
  const m = process.cwd().match(/runs\/([^/]+)-[^/-]+\/ig/);
  return `/srv/ig/sushi-port/probe/${m ? m[1] : 'unknown'}.ts.ndjson`;
})();

const events = [];
const queries = new Map(); // dedup key -> record
let seq = 0;

function row(info) {
  if (!info) return null;
  const rp = info.resourcePath || '';
  return [
    `${info.packageName}#${info.packageVersion}`,
    info.resourceType ?? null,
    info.id ?? null,
    info.url ?? null,
    info.version ?? null,
    info.sdFlavor ?? null,
    rp.startsWith('virtual:') ? rp.replace(/^virtual:[^:]+:/, '').split('/').pop() : path.basename(rp)
  ];
}

function opts(o) {
  if (!o) return null;
  return {
    type: o.type ?? null,
    scope: o.scope ?? null,
    sort: o.sort ? o.sort.map(s => (s.sortBy === 'LoadOrder' ? `LoadOrder:${s.ascending}` : `Type:${s.types.join(',')}`)) : null,
    limit: o.limit ?? null
  };
}

function record(fn, key, o, result) {
  const k = JSON.stringify([fn, key, opts(o)]);
  const r = queries.get(k);
  if (r) {
    r.n++;
    return;
  }
  queries.set(k, { ev: 'q', i: seq++, fn, key, opt: opts(o), n: 1, res: result });
}

const { BasePackageLoader } = require(path.join(FPL, 'loader/BasePackageLoader.js'));
const P = BasePackageLoader.prototype;
let depth = 0; // only record the outermost DB lookup of a call (findResourceJSON -> findResourceInfo)

const origInfo = P.findResourceInfo;
P.findResourceInfo = function (key, o) {
  depth++;
  try {
    const res = origInfo.call(this, key, o);
    if (depth === 1) record('info', key, o, res ? [row(res)] : []);
    return res;
  } finally {
    depth--;
  }
};
const origInfos = P.findResourceInfos;
P.findResourceInfos = function (key, o) {
  depth++;
  try {
    const res = origInfos.call(this, key, o);
    if (depth === 1) record('infos', key, o, res.map(row));
    return res;
  } finally {
    depth--;
  }
};
for (const fn of ['findPackageInfos', 'findPackageInfo', 'findPackageJSON']) {
  const orig = P[fn];
  P[fn] = function (...args) {
    const res = orig.apply(this, args);
    let summary;
    if (fn === 'findPackageInfos') summary = res.map(p => `${p.name}#${p.version}`);
    else if (fn === 'findPackageInfo') summary = res ? `${res.name}#${res.version}` : null;
    else summary = res ? `${res.name}#${res.version}` : null;
    record(fn, args.join('#'), null, summary);
    return res;
  };
}
const origLoad = P.loadPackage;
P.loadPackage = async function (name, version) {
  const ev = { ev: 'load', i: seq++, name, version };
  events.push(ev);
  try {
    ev.status = await origLoad.call(this, name, version);
  } catch (e) {
    ev.status = 'THREW';
    ev.error = e && e.message;
    throw e;
  }
  return ev.status;
};
const origVLoad = P.loadVirtualPackage;
P.loadVirtualPackage = async function (pkg) {
  const pj = pkg.getPackageJSON();
  const ev = { ev: 'vload', i: seq++, name: pj?.name, version: pj?.version, paths: pkg.paths ?? null };
  events.push(ev);
  ev.status = await origVLoad.call(this, pkg);
  return ev.status;
};

// inputs of dependency loading and predefined resources
const Processing = require(path.join(SUSHI_DIR, 'dist/utils/Processing.js'));
const origLED = Processing.loadExternalDependencies;
const wrapLED = async function (defs, config) {
  events.push({
    ev: 'config', i: seq++, filePath: config.filePath, fhirVersion: config.fhirVersion,
    dependencies: config.dependencies ?? null, parameters: config.parameters ?? null
  });
  return origLED.call(this, defs, config);
};
// app.js calls (0, utils_1.loadExternalDependencies); the index re-export is a getter reading Processing's exports
Processing.loadExternalDependencies = wrapLED;
const predef = require(path.join(SUSHI_DIR, 'dist/ig/predefinedResources.js'));
const origLPR = predef.loadPredefinedResources;
const wrapLPR = async function (defs, resourceDir, projectDir, configParameters) {
  events.push({ ev: 'predef', i: seq++, resourceDir, projectDir, parameters: configParameters ?? null,
    paths: predef.getPredefinedResourcePaths(resourceDir, projectDir, configParameters) });
  return origLPR.call(this, defs, resourceDir, projectDir, configParameters);
};
predef.loadPredefinedResources = wrapLPR;

process.on('exit', () => {
  fs.mkdirSync(path.dirname(out), { recursive: true });
  const lines = [...events, ...queries.values()].sort((a, b) => (a.i ?? -1) - (b.i ?? -1)).map(e => JSON.stringify(e));
  fs.writeFileSync(out, lines.join('\n') + '\n');
});
