// Extracts the part of fhir-tool 5.0.2's R4 type model (profiles/types.json) that converting an ImplementationGuide
// XML resource needs (types reachable from ImplementationGuide; contained resources excluded), keeping only the
// fields ConvertToJs reads, plus the names of all resource types in the model. Output: src/main/resources/org/hl7/fhir/sushi/importer/config/fhir-tool-r4-ig-model.json
// usage (reference image, VM): node scripts/oracle/fhir-tool-model.js /srv/ig/sushi-spike/sushi > <output>
const path = require('path');
const types = require(path.join(process.argv[2], 'node_modules/fhir-tool/profiles/types.json'));
const out = {};
const queue = ['ImplementationGuide', 'Extension'];
function slim(p) {
  const r = { _name: p._name, _type: p._type, _multiple: !!p._multiple };
  if (p._properties) r._properties = p._properties.map(slim);
  return r;
}
function collect(props) {
  for (const p of props) {
    if (p._type && !p._type.startsWith('#') && types[p._type] && p._type !== 'Resource') queue.push(p._type);
    if (p._properties) collect(p._properties);
  }
}
while (queue.length) {
  const t = queue.shift();
  if (out[t] || !types[t]) continue;
  out[t] = { _properties: types[t]._properties.map(slim) };
  collect(types[t]._properties);
}
// names ConvertToJs knows as resources (an XML file with another root element makes it throw 'Unknown resource type')
const resourceTypes = Object.keys(types).filter(k => types[k]._kind === 'resource');
process.stdout.write(JSON.stringify({ resourceTypes, types: out }) + '\n');
