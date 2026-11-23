// Generates JS-semantics oracle vectors for org.hl7.fhir.common.js (run with node; output is committed test data).
// usage: node js-vectors.js > src/test/resources/js/vectors.json
let seed = 42;
const rnd = () => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648; };
const nums = [0, -0, 1, -1, 0.1, 0.2 + 0.1, 1.5, 100, 1e21, 1e20, 123456789012345680000, 1e-6, 1e-7, 1.234e-7,
  5e-324, 1.7976931348623157e308, 2 ** 53, 2 ** 53 + 2, 0.000001, 0.0000012, 123.456, 1 / 3, 2 / 3, 100.5,
  4.35, 0.07, 1e300, 9.95, 12345.6789e10, 3.0e-5];
for (let i = 0; i < 2000; i++) {
  const e = Math.floor(rnd() * 60) - 30;
  nums.push(Number((rnd() * 10).toPrecision(1 + Math.floor(rnd() * 17))) * Math.pow(10, e));
  const buf = new DataView(new ArrayBuffer(8));
  buf.setUint32(0, Math.floor(rnd() * 2 ** 32)); buf.setUint32(4, Math.floor(rnd() * 2 ** 32));
  const d = buf.getFloat64(0); if (Number.isFinite(d)) nums.push(d);
}
const numbers = nums.map(n => [n.toString(), JSON.stringify(n), JSON.stringify(n)]);
// strings as code unit arrays so lone surrogates survive the trip
const strs = ['plain', 'quote " and \\ backslash', 'ctl \u0000\u0001\u001f\u007f', '\b\f\n\r\t', '/slash', 'é ü ☃ 😀',
  '\ud800 lone high', 'lone low \udc00', '  ', '<script>&amp;</script>'];
const strings = strs.map(s => [Array.from({ length: s.length }, (_, i) => s.charCodeAt(i)), JSON.stringify(s)]);
const objects = [
  { b: 1, a: [1, 2, { c: null }], '2': 'x', '1': 'y', '-1': 'z', '01': 'w', 'e': {}, 'f': [] },
  { resourceType: 'StructureDefinition', nested: { deep: [[], [{}], 1.0, 2.50, -0.0] } },
  [], {}, [[]], [{}], 'top', 3.14, null, true
];
const pretty = objects.map(o => [JSON.stringify(o), JSON.stringify(o, null, 2)]);
// [parse input, JSON.stringify(JSON.parse(input), null, 2)]: duplicate keys, big ints, escapes, exponents
const parseCases = ['{"a":1,"b":2,"a":3}', '{"n":12345678901234567890}', '{"s":"\\u00e9\\/\\ud83d\\ude00"}',
  '[1E2,1e-2,-0,0.0,1.10]', '{"10":1,"9":2,"x":3,"4294967295":4,"4294967294":5}', ' \n[ true , false , null ] '];
const parsed = parseCases.map(t => [t, JSON.stringify(JSON.parse(t), null, 2)]);
process.stdout.write(JSON.stringify({ numbers, strings, pretty, parsed }) + '\n');
