// Mechanical first pass of a SUSHI Jest test file -> JUnit 5 (WP-7 tool). The output is a starting point that is
// then fixed by hand: anything it cannot translate is emitted as a `/* TODO-TS2J: <ts> */` marker that fails
// compilation on purpose. Structure: describe -> @Nested class, it -> @Test with @DisplayName(fullName).
// usage: NODE_PATH=<sushi>/node_modules node ts2junit.js <file.test.ts> <JavaClassName> <javaPackage> [fromLine toLine]
const ts = require('typescript');
const fs = require('fs');

const [, , file, className, pkg, fromLine, toLine] = process.argv;
const text = fs.readFileSync(file, 'utf8');
const sf = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
const lineOf = n => sf.getLineAndCharacterOfPosition(n.getStart(sf)).line + 1;
const src = n => n.getText(sf);

// ---- type knowledge -------------------------------------------------------------------------------------------
const FSH_TYPES = new Set(('Profile Extension Logical Resource Instance FshValueSet FshCodeSystem Invariant RuleSet ' +
  'ParamRuleSet Mapping FshCode FshQuantity FshRatio FshReference FshCanonical ValueSetFilter Configuration ' +
  'AddElementRule AssignmentRule BindingRule CardRule CaretValueRule ConceptRule ContainsRule FlagRule InsertRule ' +
  'MappingRule ObeysRule OnlyRule PathRule Rule ValueSetComponentRule ValueSetConceptComponentRule ' +
  'ValueSetFilterComponentRule FSHDocument FSHTank Package TestFisher MasterFisher SourceInfo').split(' '));
const MODEL_TYPES = new Set('CodeSystem ValueSet InstanceDefinition StructureDefinition ElementDefinition Object JsObject Metadata'.split(' '));
const ID_GETTER_TYPES = new Set('Profile Extension Logical Resource Instance FshValueSet FshCodeSystem Invariant'.split(' '));
const PACKAGE_FIELDS = { profiles: 'StructureDefinition', extensions: 'StructureDefinition', logicals: 'StructureDefinition',
  resources: 'StructureDefinition', instances: 'InstanceDefinition', valueSets: 'ValueSet', codeSystems: 'CodeSystem' };
const METHOD_TYPES = {
  export: 'Package', exportCodeSystem: 'CodeSystem', exportValueSet: 'ValueSet', exportInstance: 'InstanceDefinition',
  exportStructDef: 'StructureDefinition', fishForStructureDefinition: 'StructureDefinition', fishForFHIR: 'Object',
  fishForMetadata: 'Metadata', getLastMessage: 'String', getFirstMessage: 'String', getMessageAtIndex: 'String',
  getAllMessages: 'List<String>', getAllLogs: 'List<Log>', toJSON: 'Object', findElement: 'ElementDefinition',
  findElementByPath: 'ElementDefinition', getFileName: 'String'
};
const COMMON_FNS = new Set('getUrlFromFshDefinition getVersionFromFshDefinition splitOnPathPeriods isExtension getAllConcepts applyInsertRules cleanResource'.split(' '));
const scopes = [new Map()];
const typeOf = name => { for (let i = scopes.length - 1; i >= 0; i--) if (scopes[i].has(name)) return scopes[i].get(name); return null; };
const declare = (name, type) => scopes[scopes.length - 1].set(name, type);
const isList = t => t && t.startsWith('List<');
const elemOf = t => (isList(t) ? t.slice(5, -1) : 'Object');
const isModel = t => !t || MODEL_TYPES.has(t);

const TS_TYPE = { string: 'String', number: 'Double', boolean: 'Boolean', any: 'Object', void: 'void' };
function javaType(tn) {
  if (!tn) return 'Object';
  const s = src(tn);
  if (TS_TYPE[s]) return TS_TYPE[s];
  if (/\[\]$/.test(s)) return `List<${javaType({ getText: () => s.slice(0, -2) })}>`;
  if (tn.kind === ts.SyntaxKind.FunctionType) {
    const ps = tn.parameters.map(p => javaType(p.type));
    const rt = javaType(tn.type);
    if (ps.length === 0) return rt === 'void' || rt === 'Object' ? 'Runnable' : `java.util.function.Supplier<${rt}>`;
    if (ps.length === 1) return `java.util.function.Function<${ps[0]}, ${rt}>`;
    return 'FUNCTION';
  }
  return s.replace(/^jest\.SpyInstance$/, 'Object');
}

// ---- literals -------------------------------------------------------------------------------------------------
function jstr(s) {
  let o = '"';
  for (const ch of s) {
    const c = ch.codePointAt(0);
    if (ch === '"') o += '\\"';
    else if (ch === '\\') o += '\\\\';
    else if (ch === '\n') o += '\\n';
    else if (ch === '\r') o += '\\r';
    else if (ch === '\t') o += '\\t';
    else if (c < 0x20) o += '\\u' + c.toString(16).padStart(4, '0');
    else o += ch;
  }
  return o + '"';
}
// a pure-JSON object/array literal -> JS value (undefined if not pure); undefined-valued properties dropped
function pureValue(n) {
  if (ts.isStringLiteral(n) || ts.isNoSubstitutionTemplateLiteral(n)) return { v: n.text };
  if (ts.isNumericLiteral(n)) return { v: Number(n.text) };
  if (ts.isPrefixUnaryExpression(n) && n.operator === ts.SyntaxKind.MinusToken && ts.isNumericLiteral(n.operand)) return { v: -Number(n.operand.text) };
  if (n.kind === ts.SyntaxKind.TrueKeyword) return { v: true };
  if (n.kind === ts.SyntaxKind.FalseKeyword) return { v: false };
  if (n.kind === ts.SyntaxKind.NullKeyword) return { v: null };
  if (ts.isIdentifier(n) && n.text === 'undefined') return { v: undefined };
  if (ts.isParenthesizedExpression(n)) return pureValue(n.expression);
  if (ts.isArrayLiteralExpression(n)) {
    const out = [];
    for (const e of n.elements) { const p = pureValue(e); if (!p) return null; out.push(p.v === undefined ? null : p.v); }
    return { v: out };
  }
  if (ts.isObjectLiteralExpression(n)) {
    const out = {};
    for (const p of n.properties) {
      if (!ts.isPropertyAssignment(p)) return null;
      const k = ts.isIdentifier(p.name) || ts.isStringLiteral(p.name) || ts.isNumericLiteral(p.name) ? p.name.text : null;
      if (k == null) return null;
      const v = pureValue(p.initializer); if (!v) return null;
      if (v.v !== undefined) out[k] = v.v;
    }
    return { v: out };
  }
  return null;
}
function textBlock(json) {
  // Java text block; escape backslashes and triple quotes
  const body = json.replace(/\\/g, '\\\\').replace(/"""/g, '\\"""');
  return '"""\n' + body.split('\n').map(l => '        ' + l).join('\n') + '\n        """';
}
function jsonArg(v) {
  const j = JSON.stringify(v, null, 2);
  return j.includes('\n') ? textBlock(j) : jstr(j);
}

// ---- expressions ----------------------------------------------------------------------------------------------
// returns { c: javaCode, t: javaType|null }
let ctxNumberAsDouble = false;
function E(n, hint) {
  if (ts.isParenthesizedExpression(n)) { const r = E(n.expression, hint); return { c: `(${r.c})`, t: r.t }; }
  if (ts.isAwaitExpression(n)) return E(n.expression, hint);
  if (ts.isStringLiteral(n) || ts.isNoSubstitutionTemplateLiteral(n)) return { c: jstr(n.text), t: 'String' };
  if (ts.isTemplateExpression(n)) {
    const parts = [jstr(n.head.text)];
    for (const sp of n.templateSpans) { parts.push(`Js.toStr(${E(sp.expression).c})`); if (sp.literal.text) parts.push(jstr(sp.literal.text)); }
    return { c: parts.join(' + '), t: 'String' };
  }
  if (ts.isNumericLiteral(n)) {
    const isInt = /^\d+$/.test(n.text);
    if (hint === 'double' || ctxNumberAsDouble) return { c: isInt ? n.text + '.0' : n.text, t: 'Double' };
    return { c: n.text, t: isInt ? 'int' : 'double' };
  }
  if (ts.isBigIntLiteral(n)) return { c: `new java.math.BigInteger("${n.text.slice(0, -1)}")`, t: 'BigInteger' };
  if (ts.isPrefixUnaryExpression(n)) {
    const op = ts.tokenToString(n.operator); const r = E(n.operand, hint);
    if (op === '!') return { c: `!Js.truthy(${r.c})`, t: 'boolean' };
    return { c: op + r.c, t: r.t };
  }
  switch (n.kind) {
    case ts.SyntaxKind.TrueKeyword: return { c: 'true', t: 'boolean' };
    case ts.SyntaxKind.FalseKeyword: return { c: 'false', t: 'boolean' };
    case ts.SyntaxKind.NullKeyword: return { c: 'null', t: null };
    case ts.SyntaxKind.ThisKeyword: return { c: 'this', t: null };
  }
  if (ts.isIdentifier(n)) {
    if (n.text === 'undefined') return { c: 'null', t: null };
    if (n.text === 'minimalConfig') return { c: 'MinimalConfig.minimalConfig()', t: 'Configuration' };
    const t = typeOf(n.text);
    if (t && t.startsWith('CAST:')) return { c: `((${t.slice(5)}) ${n.text})`, t: t.slice(5) };
    return { c: n.text, t };
  }
  if (ts.isRegularExpressionLiteral(n)) {
    const m = n.text.match(/^\/(.*)\/([a-z]*)$/s);
    return { c: `${jstr(m[1])}`, t: 'REGEX', flags: m[2] };
  }
  if (ts.isArrayLiteralExpression(n)) {
    if (hint === 'int[]') return { c: `new int[] {${n.elements.map(e => E(e).c).join(', ')}}`, t: 'int[]' };
    const p = pureValue(n);
    if (p && hint === 'json') return { c: `JsAssert.json(${jsonArg(p.v)})`, t: 'Object' };

    const els = n.elements.map(e => E(e, hint === 'double' ? 'double' : undefined).c);
    if (els.length === 1 && els[0] === 'null') return { c: 'new ArrayList<>(Collections.singletonList(null))', t: 'List<Object>' };
    return { c: `new ArrayList<>(Arrays.asList(${els.join(', ')}))`, t: 'List<Object>' };
  }
  if (ts.isObjectLiteralExpression(n)) {
    const keys = n.properties.map(p => p.name && p.name.getText(sf)).sort().join(',');
    const prop = k => n.properties.find(p => p.name && p.name.getText(sf) === k).initializer;
    if (keys === 'operator,property,value') {
      const v = prop('value');
      const vc = ts.isRegularExpressionLiteral(v) ? `Pattern.compile(${E(v).c})` : E(v).c;
      return { c: `new ValueSetFilter(${E(prop('property')).c}, ${E(prop('operator')).c}, ${vc})`, t: 'ValueSetFilter' };
    }
    if (hint === 'from') {
      const sys = n.properties.find(p => p.name.getText(sf) === 'system');
      const vss = n.properties.find(p => p.name.getText(sf) === 'valueSets');
      return { c: `fromOf(${sys ? E(sys.initializer).c : 'null'}, ${vss ? E(vss.initializer).c : 'null'})`, t: 'ValueSetComponentFrom' };
    }
    const p = pureValue(n);
    if (p) return { c: `JsAssert.json(${jsonArg(p.v)})`, t: 'Object' };
    if (n.properties.every(q => ts.isPropertyAssignment(q) || ts.isShorthandPropertyAssignment(q) || ts.isSpreadAssignment(q))) {
      const parts = [];
      for (const q of n.properties) {
        if (ts.isSpreadAssignment(q)) { parts.push(`SPREAD, ${E(q.expression).c}`); continue; }
        const k = q.name.getText(sf).replace(/^['"]|['"]$/g, '');
        const v = ts.isShorthandPropertyAssignment(q) ? E(q.name, 'double') : E(q.initializer, 'double');
        parts.push(`${jstr(k)}, ${v.c}`);
      }
      return { c: `obj(${parts.join(', ')})`, t: 'Object' };
    }
    return { c: `/* TODO-TS2J object literal: ${src(n).replace(/\*\//g, '*\\/').replace(/\n\s*/g, ' ')} */`, t: 'Object' };
  }
  if (ts.isAsExpression(n) || ts.isTypeAssertionExpression(n) || ts.isNonNullExpression(n)) {
    const r = E(n.expression, hint);
    const tt = n.type ? javaType(n.type) : r.t;
    if (n.type && FSH_TYPES.has(tt) || (n.type && MODEL_TYPES.has(tt) && tt !== 'Object')) return { c: `((${tt}) ${r.c})`, t: tt };
    return { c: r.c, t: r.t };
  }
  if (ts.isNewExpression(n) && src(n.expression) === 'RegExp') {
    const as = n.arguments.map(a => E(a).c);
    return { c: `jsRegex(${as[0]}, ${as[1] || '""'})`, t: 'Pattern' };
  }
  if (ts.isNewExpression(n)) {
    const cls = src(n.expression);
    const args = (n.arguments || []).map(a => E(a, cls === 'TextLocation' ? undefined : 'double').c);
    return { c: `new ${cls}(${args.join(', ')})`, t: cls };
  }
  if (ts.isBinaryExpression(n)) {
    const op = n.operatorToken.getText(sf);
    const l = E(n.left), r = E(n.right);
    if (op === '===' || op === '==') return { c: `Objects.equals(${l.c}, ${r.c})`, t: 'boolean' };
    if (op === '!==' || op === '!=') return { c: `!Objects.equals(${l.c}, ${r.c})`, t: 'boolean' };
    if (op === '+' && (l.t === 'String' || r.t === 'String')) return { c: `${l.c} + ${r.c}`, t: 'String' };
    return { c: `${l.c} ${op} ${r.c}`, t: l.t };
  }
  if (ts.isArrowFunction(n) || ts.isFunctionExpression(n)) {
    const ps = n.parameters.map(p => p.name.getText(sf));
    const pts = hint && hint.paramTypes ? hint.paramTypes : [];
    scopes.push(new Map()); ps.forEach((p, i) => declare(p, pts[i] && pts[i] !== 'Object' ? (hint.noCast ? pts[i] : 'CAST:' + pts[i]) : null));
    let body;
    if (ts.isBlock(n.body)) body = '{\n' + n.body.statements.map(s => S(s, '  ')).join('') + '}';
    else body = E(n.body).c;
    scopes.pop();
    return { c: `(${ps.join(', ')}) -> ${body}`, t: 'FUNCTION' };
  }
  if (ts.isElementAccessExpression(n) || ts.isPropertyAccessExpression(n)) return access(n);
  if (ts.isCallExpression(n)) return call(n);
  return { c: `/* TODO-TS2J expr: ${src(n).replace(/\*\//g, '*\\/').replace(/\n\s*/g, ' ')} */`, t: null };
}

// flatten a.b[0].c into [base, steps]
function chain(n) {
  const steps = [];
  let cur = n;
  while (true) {
    if (ts.isPropertyAccessExpression(cur)) { steps.unshift({ k: 'p', name: cur.name.text, opt: !!cur.questionDotToken }); cur = cur.expression; }
    else if (ts.isElementAccessExpression(cur)) {
      const a = cur.argumentExpression;
      if (ts.isNumericLiteral(a)) steps.unshift({ k: 'i', idx: a.text });
      else if (ts.isStringLiteral(a)) steps.unshift({ k: 'p', name: a.text });
      else steps.unshift({ k: 'e', expr: a });
      cur = cur.expression;
    } else if (ts.isNonNullExpression(cur)) cur = cur.expression;
    else break;
  }
  return { base: cur, steps };
}

const ENUMS = { VsOperator: 'VsOperator', InstanceUsage: 'InstanceUsage', Type: 'FishableType' };
function access(n) {
  const { base, steps } = chain(n);
  if (ts.isIdentifier(base) && ENUMS[base.text] && steps.length === 1 && steps[0].k === 'p') return { c: `${ENUMS[base.text]}.${steps[0].name}`, t: ENUMS[base.text] };
  let r = E(base);
  let i = 0;
  // typed (non-model) prefix
  for (; i < steps.length; i++) {
    const s = steps[i];
    if (isList(r.t)) {
      if (s.k === 'i') { r = { c: `${r.c}.get(${s.idx})`, t: elemOf(r.t) }; continue; }
      if (s.k === 'e') { r = { c: `${r.c}.get(${E(s.expr).c})`, t: elemOf(r.t) }; continue; }
      if (s.name === 'length') { r = { c: `${r.c}.size()`, t: 'int' }; continue; }
      break;
    }
    if (r.t === 'String' && s.k === 'p' && s.name === 'length') { r = { c: `${r.c}.length()`, t: 'int' }; continue; }
    if (r.t === 'Package' && s.k === 'p') {
      if (PACKAGE_FIELDS[s.name]) { r = { c: `${r.c}.${s.name}`, t: `List<${PACKAGE_FIELDS[s.name]}>` }; continue; }
      if (s.name === 'fshMap') { r = { c: `${r.c}.fshMap`, t: 'Map<String, FshMapEntry>' }; continue; }
    }
    if (r.t === 'Log' && s.k === 'p') { r = { c: `${r.c}.${s.name}()`, t: 'String' }; continue; }
    if (r.t && FSH_TYPES.has(r.t) && s.k === 'p') {
      if (s.name === 'id' && ID_GETTER_TYPES.has(r.t)) { r = { c: `${r.c}.getId()`, t: 'String' }; continue; }
      const ft = s.name === 'rules' ? 'List<Rule>' : s.name === 'config' ? 'Configuration' : s.name === 'sourceInfo' ? 'SourceInfo' : (s.name === 'instances' || s.name === 'codeSystems' || s.name === 'valueSets' || s.name === 'profiles' || s.name === 'extensions' || s.name === 'logicals' || s.name === 'resources' || s.name === 'ruleSets' || s.name === 'mappings' || s.name === 'aliases' || s.name === 'invariants') && r.t === 'FSHDocument' ? 'DOCMAP' : null;
      r = { c: `${r.c}.${s.name}`, t: ft };
      continue;
    }
    if ((r.t === 'InstanceDefinition') && s.k === 'p' && s.name === '_instanceMeta') { r = { c: `${r.c}._instanceMeta`, t: 'InstanceMeta' }; continue; }
    if (r.t === 'InstanceMeta' && s.k === 'p') { r = { c: `${r.c}.${s.name}`, t: 'String' }; continue; }
    if (r.t === 'StructureDefinition' && s.k === 'p' && s.name === 'elements') { r = { c: `${r.c}.elements`, t: 'List<ElementDefinition>' }; continue; }
    if (r.t === 'Metadata' && s.k === 'p') { r = { c: `${r.c}.${s.name}`, t: 'String' }; continue; }
    break;
  }
  if (i === steps.length) return r;
  // value-model remainder -> at(x, "a.b[0]"), with .length -> len()
  let path = '';
  let tail = null;
  for (; i < steps.length; i++) {
    const s = steps[i];
    if (s.k === 'p' && s.name === 'length' && i === steps.length - 1) { tail = 'len'; break; }
    if (s.k === 'p') path += (path ? '.' : '') + s.name;
    else if (s.k === 'i') path += `[${s.idx}]`;
    else return { c: `/* TODO-TS2J computed access: ${src(n)} */`, t: null };
  }
  let c = path ? `at(${r.c}, ${jstr(path)})` : r.c;
  if (tail === 'len') return { c: `len(${c})`, t: 'int' };
  return { c, t: 'Object' };
}

function call(n) {
  const callee = n.expression;
  const args = n.arguments;
  // expect(...).matcher(...)
  const m = matcher(n);
  if (m) return m;
  if (ts.isIdentifier(callee)) {
    const f = callee.text;
    if (f === 'cloneDeep') {
      if (src(args[0]) === 'minimalConfig') return { c: 'MinimalConfig.minimalConfig()', t: 'Configuration' };
      const a = E(args[0]); return { c: `Js.cloneDeep(${a.c})`, t: a.t };
    }
    if (f === 'BigInt') return { c: `new java.math.BigInteger(${jstr(src(args[0]))})`, t: 'BigInteger' };
    if (COMMON_FNS.has(f)) return { c: `org.hl7.fhir.sushi.fhirtypes.Common.${f}(${args.map(a => E(a).c).join(', ')})`, t: null };
    if (f === 'testDefsPath') return { c: `testDefsPath(${args.map(a => E(a).c).join(', ')})`, t: 'String' };
    if (f === 'getTestFHIRDefinitions') return { c: `getTestFHIRDefinitions(${args.map(a => E(a).c).join(', ')})`, t: 'TestFHIRDefinitions' };
    const t = typeOf(f);
    if (t === 'FUNCTION' || t === 'Runnable') return { c: `${f}.run()`, t: 'void' };
    if (t && t.startsWith('java.util.function.Function<')) return { c: `${f}.apply(${args.map(a => E(a).c).join(', ')})`, t: t.slice(t.lastIndexOf(', ') + 2, -1) };
    if (t && t.startsWith('java.util.function.Supplier<')) return { c: `${f}.get()`, t: t.slice(28, -1) };
    return { c: `${f}(${args.map(a => E(a).c).join(', ')})`, t: METHOD_TYPES[f] || null };
  }
  if (ts.isPropertyAccessExpression(callee)) {
    const name = callee.name.text;
    const recv = E(callee.expression);
    const A = (h) => args.map(a => E(a, h).c);
    if (name === 'slice' && recv.t === 'String') return { c: `${recv.c}.substring(${A().join(', ')})`, t: 'String' };
    if (name === 'withLocation') return { c: `${recv.c}.withLocation(${A('int[]').join(', ')})`, t: recv.t };
    if (name === 'withFile') return { c: `${recv.c}.withFile(${A().join(', ')})`, t: recv.t };
    if (name === 'set' && (recv.t === 'DOCMAP' || /^Map</.test(recv.t || ''))) return { c: `${recv.c}.put(${A().join(', ')})`, t: null };
    if (name === 'push' && args.some(a => ts.isSpreadElement(a))) {
      return { c: args.map(a => ts.isSpreadElement(a) ? `${recv.c}.addAll(${E(a.expression).c})` : `${recv.c}.add(${E(a).c})`).join('; '), t: 'void' };
    }
    if (name === 'push' && ts.isPropertyAccessExpression(callee.expression) && ['items', 'types'].includes(callee.expression.name.text)) {
      const conv = callee.expression.name.text === 'items' ? 'containsRuleItem' : 'onlyRuleType';
      const xs = args.map(a => `${conv}(${E(a, 'double').c})`);
      return { c: xs.length === 1 ? `${recv.c}.add(${xs[0]})` : `${recv.c}.addAll(Arrays.asList(${xs.join(', ')}))`, t: 'boolean' };
    }
    if (['some', 'every', 'filter', 'map', 'find', 'findIndex', 'forEach'].includes(name) && args.length === 1 && !(name === 'find' && isList(recv.t))) {
      const et = isList(recv.t) ? elemOf(recv.t) : null;
      const ph = { paramTypes: [et] };
      let fnc = E(args[0], ph).c;
      const fnArg = args[0];
      if (['some', 'every', 'filter', 'find', 'findIndex'].includes(name) && (ts.isArrowFunction(fnArg)) && !ts.isBlock(fnArg.body)) {
        scopes.push(new Map()); fnArg.parameters.forEach(p => declare(p.name.getText(sf), et && et !== 'Object' ? 'CAST:' + et : null));
        const b = E(fnArg.body); scopes.pop();
        if (b.t !== 'boolean') fnc = `(${fnArg.parameters.map(p => p.name.getText(sf)).join(', ')}) -> Js.truthy(${b.c})`;
      }
      return { c: `${name}(${recv.c}, ${fnc})`, t: name === 'some' || name === 'every' ? 'boolean' : name === 'findIndex' ? 'int' : name === 'filter' || name === 'map' ? 'List<Object>' : name === 'forEach' ? 'void' : 'Object' };
    }
    if (name === 'test' && ts.isRegularExpressionLiteral(callee.expression)) {
      const r = E(callee.expression);
      return { c: `jsRegex(${r.c}, ${jstr(r.flags)}).matcher(Js.toStr(${E(args[0]).c})).find()`, t: 'boolean' };
    }
    if (name === 'match' && args.length === 1) {
      const r = E(args[0]);
      return { c: `jsMatch(${recv.c}, ${r.t === 'REGEX' ? `jsRegex(${r.c}, ${jstr(r.flags)})` : `Pattern.compile(${r.c})`})`, t: 'Object' };
    }
    if (name === 'stringify' && src(callee.expression) === 'JSON') return { c: `Json.stringify(${E(args[0]).c})`, t: 'String' };
    if (name === 'push' && (isList(recv.t) || true)) {
      if (args.length === 1) return { c: `${recv.c}.add(${A().join(', ')})`, t: 'boolean' };
      return { c: `${recv.c}.addAll(Arrays.asList(${A().join(', ')}))`, t: 'boolean' };
    }
    if (name === 'find' && isList(recv.t) && args.length === 1) {
      const fn = E(args[0], { paramTypes: [elemOf(recv.t)], noCast: true });
      return { c: `${recv.c}.stream().filter(${fn.c.replace(/Objects\.equals\(([a-z]+)\.id,/g, 'Objects.equals($1.getId(),')}).findFirst().orElse(null)`, t: elemOf(recv.t) };
    }
    if (name === 'slice' && isList(recv.t) && args.length === 1 && src(args[0]) === '-1') {
      return { c: `List.of(${recv.c}.get(${recv.c}.size() - 1))`, t: recv.t };
    }
    if (name === 'get' && recv.t && recv.t.startsWith('Map<')) return { c: `${recv.c}.get(${A().join(', ')})`, t: 'FshMapEntry' };
    const rt = METHOD_TYPES[name] || null;
    return { c: `${recv.c}.${name}(${A().join(', ')})`, t: rt };
  }
  return { c: `/* TODO-TS2J call: ${src(n).replace(/\n\s*/g, ' ')} */`, t: null };
}

function expectTarget(n) {
  // n is a CallExpression expect(x)
  if (ts.isCallExpression(n) && ts.isIdentifier(n.expression) && n.expression.text === 'expect') return n.arguments[0];
  return null;
}

function matcher(n) {
  if (!ts.isPropertyAccessExpression(n.expression)) return null;
  let name = n.expression.name.text;
  let obj = n.expression.expression;
  let not = false;
  if (ts.isPropertyAccessExpression(obj) && obj.name.text === 'not') { not = true; obj = obj.expression; }
  const target = expectTarget(obj);
  if (!target) return null;
  let a = E(target);
  if (a.t === 'FshMapEntry') a = { c: `fshMapJson(${a.c})`, t: 'Object' };
  const arg = n.arguments[0];
  const fail = () => ({ c: `/* TODO-TS2J matcher: ${src(n).replace(/\*\//g, '*\\/').replace(/\n\s*/g, ' ')} */`, t: 'void' });
  switch (name) {
    case 'toBe': {
      if (not) return { c: `assertNotEquals(${E(arg).c}, ${a.c})`, t: 'void' };
      if (arg.kind === ts.SyntaxKind.NullKeyword || (ts.isIdentifier(arg) && arg.text === 'undefined')) return { c: `assertNull(${a.c})`, t: 'void' };
      if (a.t === 'int') return { c: `assertEquals(${E(arg).c}, ${a.c})`, t: 'void' };
      const b = E(arg);
      if (b.t === 'String' || b.t === 'boolean') return { c: `assertEquals(${b.c}, ${a.c})`, t: 'void' };
      if (b.t === 'int' || b.t === 'double') return { c: `assertJsEqual(${E(arg, 'double').c}, ${a.c})`, t: 'void' };
      return { c: `assertJsEqual(${b.c}, ${a.c})`, t: 'void' };
    }
    case 'toEqual': case 'toStrictEqual': {
      const p = pureValue(arg);
      if (p && p.v && typeof p.v === 'object' && !Array.isArray(p.v) && '_instanceMeta' in p.v) a = { c: `withMeta(${a.c})`, t: 'Object' };
      if (p) return { c: `${not ? 'assertNotJsonEqual' : 'assertJsonEqual'}(${jsonArg(p.v === undefined ? null : p.v)}, ${a.c})`, t: 'void' };
      return { c: `${not ? 'assertNotJsEqual' : 'assertJsEqual'}(${E(arg).c}, ${a.c})`, t: 'void' };
    }
    case 'toHaveLength': return { c: `${not ? 'assertNotEquals' : 'assertEquals'}(${E(arg).c}, ${a.t === 'List<String>' || isList(a.t) ? a.c + '.size()' : 'len(' + a.c + ')'})`, t: 'void' };
    case 'toBeUndefined': case 'toBeNull': return { c: `${not ? 'assertNotNull' : 'assertNull'}(${a.c})`, t: 'void' };
    case 'toBeDefined': return { c: `${not ? 'assertNull' : 'assertNotNull'}(${a.c})`, t: 'void' };
    case 'toBeTrue': return { c: `assertEquals(${not ? 'false' : 'true'}, ${a.c})`, t: 'void' };
    case 'toBeFalse': return { c: `assertEquals(${not ? 'true' : 'false'}, ${a.c})`, t: 'void' };
    case 'toBeTruthy': return { c: `${not ? 'assertFalse' : 'assertTrue'}(Js.truthy(${a.c}))`, t: 'void' };
    case 'toBeFalsy': return { c: `${not ? 'assertTrue' : 'assertFalse'}(Js.truthy(${a.c}))`, t: 'void' };
    case 'toMatch': {
      const r = E(arg);
      if (a.t !== 'String') a = { c: `(String) ${a.c}`, t: 'String' };
      if (r.t === 'Pattern') return { c: `${not ? 'assertNotMatchesPattern' : 'assertMatchesPattern'}(${r.c}, ${a.c})`, t: 'void' };
      if (r.t === 'REGEX') return { c: `${not ? 'assertNotMatches' : 'assertMatches'}(${r.c}, ${jstr(r.flags)}, ${a.c})`, t: 'void' };
      return { c: `${not ? 'assertNotContainsText' : 'assertContainsText'}(${r.c}, ${a.c})`, t: 'void' };
    }
    case 'toContain': return { c: `${not ? 'assertNotContains' : 'assertContains'}(${E(arg).c}, ${a.c})`, t: 'void' };
    case 'toContainEqual': {
      const p = pureValue(arg);
      return { c: `${not ? 'assertNotContainsEqual' : 'assertContainsEqual'}(${p ? 'JsAssert.json(' + jsonArg(p.v) + ')' : E(arg).c}, ${a.c})`, t: 'void' };
    }
    case 'toBeEmpty': return { c: `${not ? 'assertNotEquals' : 'assertEquals'}(0, ${isList(a.t) ? a.c + '.size()' : 'len(' + a.c + ')'})`, t: 'void' };
    case 'toThrow': {
      const fn = (a.t === 'FUNCTION' || a.t === 'Runnable') && ts.isIdentifier(target) ? `${a.c}::run` : a.c;
      if (!arg) return { c: `assertThrows(RuntimeException.class, ${fn})`, t: 'void' };
      if (ts.isIdentifier(arg)) return { c: `assertThrows(${arg.text}.class, ${fn})`, t: 'void' };
      const r = E(arg);
      if (r.t === 'REGEX') return { c: `assertThrowsMatching(${r.c}, ${jstr(r.flags)}, ${fn})`, t: 'void' };
      return { c: `assertThrowsMessage(${r.c}, ${fn})`, t: 'void' };
    }
    case 'toEndWith': return { c: `assertTrue(${a.c}.endsWith(${E(arg).c}))`, t: 'void' };
    case 'toBeInstanceOf': return { c: `assertInstanceOf(${src(arg)}.class, ${a.c})`, t: 'void' };
  }
  return fail();
}

// ---- statements -----------------------------------------------------------------------------------------------
function S(s, ind) {
  const lead = leadingComments(s, ind);
  if (ts.isVariableStatement(s)) {
    return lead + s.declarationList.declarations.map(d => {
      const name = d.name.getText(sf);
      if (!d.initializer) { const t = javaType(d.type); declare(name, t); return `${ind}${t} ${name};\n`; }
      const r = E(d.initializer);
      const t = d.type ? javaType(d.type) : r.t;
      declare(name, t);
      const jt = t && !['int', 'double', 'boolean'].includes(t) && t !== 'REGEX' ? (t === 'FUNCTION' ? 'Runnable' : t) : (t || 'var');
      return `${ind}${jt === 'var' || jt === 'FshMapEntry' || jt === 'DOCMAP' || jt === 'TestFHIRDefinitions' ? 'var' : jt} ${name} = ${r.c};\n`;
    }).join('');
  }
  if (ts.isExpressionStatement(s)) {
    const e = s.expression;
    if (ts.isBinaryExpression(e) && e.operatorToken.kind === ts.SyntaxKind.EqualsToken) return lead + assign(e, ind);
    if (ts.isDeleteExpression(e)) {
      const { base, steps } = chain(e.expression);
      const last = steps[steps.length - 1];
      const owner = steps.length > 1 ? access(e.expression.expression) : E(base);
      if (owner.t && FSH_TYPES.has(owner.t)) return `${lead}${ind}${owner.c}.${last.name} = null;\n`;
      return `${lead}${ind}deleteProp(${owner.c}, ${jstr(last.name)});\n`;
    }
    const r = E(e);
    return `${lead}${ind}${r.c};\n`;
  }
  if (ts.isWhileStatement(s)) {
    const body = ts.isBlock(s.statement) ? s.statement.statements.map(x => S(x, ind + '  ')).join('') : S(s.statement, ind + '  ');
    return `${lead}${ind}while (${E(s.expression).c}) {\n${body}${ind}}\n`;
  }
  if (ts.isIfStatement(s)) {
    const body = ts.isBlock(s.thenStatement) ? s.thenStatement.statements.map(x => S(x, ind + '  ')).join('') : S(s.thenStatement, ind + '  ');
    return `${lead}${ind}if (Js.truthy(${E(s.expression).c})) {\n${body}${ind}}\n` + (s.elseStatement ? `/* TODO-TS2J else */\n` : '');
  }
  if (ts.isIfStatement(s) || ts.isForOfStatement(s) || ts.isForStatement(s) || ts.isReturnStatement(s) || ts.isBlock(s)) {
    if (ts.isReturnStatement(s)) return `${lead}${ind}return${s.expression ? ' ' + E(s.expression).c : ''};\n`;
    if (ts.isForOfStatement(s)) {
      const v = s.initializer.declarations[0].name.getText(sf); const it = E(s.expression);
      scopes.push(new Map()); declare(v, elemOf(it.t));
      const body = ts.isBlock(s.statement) ? s.statement.statements.map(x => S(x, ind + '  ')).join('') : S(s.statement, ind + '  ');
      scopes.pop();
      return `${lead}${ind}for (var ${v} : ${it.c}) {\n${body}${ind}}\n`;
    }
  }
  return `${lead}${ind}/* TODO-TS2J stmt: ${src(s).replace(/\*\//g, '*\\/').replace(/\n/g, '\n' + ind + ' * ')} */\n`;
}

function assign(e, ind) {
  const lhs = e.left;
  if (ts.isBinaryExpression(e) && false) {}
  if (ts.isIdentifier(lhs)) {
    const t = typeOf(lhs.text);
    const r = E(e.right, t === 'Double' ? 'double' : undefined);
    return `${ind}${lhs.text} = ${r.c};\n`;
  }
  if (ts.isPropertyAccessExpression(lhs)) {
    const owner = E(lhs.expression);
    const prop = lhs.name.text;
    if (owner.t && FSH_TYPES.has(owner.t)) {
      if (prop === 'id' && ID_GETTER_TYPES.has(owner.t)) return `${ind}${owner.c}.setId(${E(e.right).c});\n`;
      if (owner.t === 'SourceInfo' && prop === 'location' && ts.isObjectLiteralExpression(e.right)) {
        const v = pureValue(e.right).v;
        return `${ind}${owner.c}.location = new TextLocation(${v.startLine}, ${v.startColumn}, ${v.endLine}, ${v.endColumn});\n`;
      }
      if (prop === 'usage' && ts.isStringLiteral(e.right)) return `${ind}${owner.c}.usage = InstanceUsage.${e.right.text};\n`;
      const hint = prop === 'from' ? 'from' : 'double';
      if ((prop === 'items' || prop === 'types') && ts.isArrayLiteralExpression(e.right)) {
        const conv = prop === 'items' ? 'containsRuleItem' : 'onlyRuleType';
        return `${ind}${owner.c}.${prop} = new ArrayList<>(Arrays.asList(${e.right.elements.map(x => `${conv}(${E(x, 'double').c})`).join(', ')}));\n`;
      }
      if (prop === 'instanceOptions') return `${ind}${owner.c}.instanceOptions = instanceOptions(${E(e.right, 'double').c});\n`;
      const r = E(e.right, hint);
      return `${ind}${owner.c}.${prop} = ${r.c};\n`;
    }
    if (owner.t === 'InstanceMeta') return `${ind}${owner.c}.${prop} = ${E(e.right).c};\n`;
    if (owner.t === 'StructureDefinition' || owner.t === 'ElementDefinition') {
      if (prop === 'id') return `${ind}${owner.c}.setId(${E(e.right).c});\n`;
      return `${ind}${owner.c}.setProperty(${jstr(prop)}, ${E(e.right, 'json').c});\n`;
    }
    return `${ind}setProp(${owner.c}, ${jstr(prop)}, ${E(e.right, 'json').c});\n`;
  }
  return `${ind}/* TODO-TS2J assign: ${src(e).replace(/\*\//g, '*\\/').replace(/\n\s*/g, ' ')} */\n`;
}

function leadingComments(n, ind) {
  const ranges = ts.getLeadingCommentRanges(text, n.getFullStart()) || [];
  return ranges.map(r => {
    const c = text.slice(r.pos, r.end);
    return c.split('\n').map(l => ind + l.trim().replace(/^\*/, ' *')).join('\n') + '\n';
  }).join('');
}

// ---- describe / it --------------------------------------------------------------------------------------------
const used = new Map();
function methodName(title, cls) {
  let words = title.replace(/[^A-Za-z0-9 ]+/g, ' ').trim().split(/\s+/).filter(Boolean);
  let s = words.map((w, i) => (i === 0 ? w.toLowerCase() : w[0].toUpperCase() + w.slice(1))).join('');
  if (!/^[a-z]/.test(s)) s = 't' + s;
  if (s.length > 80) s = s.slice(0, 80);
  const key = cls + '.' + s;
  const n = (used.get(key) || 0) + 1; used.set(key, n);
  return n > 1 ? s + n : s;
}
function className2(title) {
  let s = title.replace(/[^A-Za-z0-9 ]+/g, ' ').trim().split(/\s+/).map(w => w[0].toUpperCase() + w.slice(1)).join('');
  if (!/^[A-Z]/.test(s)) s = 'D' + s;
  return s.slice(0, 60);
}
const inRange = n => !fromLine || (lineOf(n) >= Number(fromLine) && lineOf(n) <= Number(toLine));
const overlaps = n => {
  if (!fromLine) return true;
  const a = lineOf(n), b = sf.getLineAndCharacterOfPosition(n.getEnd()).line + 1;
  return b >= Number(fromLine) && a <= Number(toLine);
};

function isCall(st, names) {
  return ts.isExpressionStatement(st) && ts.isCallExpression(st.expression) &&
    (ts.isIdentifier(st.expression.expression) && names.includes(st.expression.expression.text) ||
     ts.isPropertyAccessExpression(st.expression.expression) && ts.isIdentifier(st.expression.expression.expression) &&
       names.includes(st.expression.expression.expression.text));
}
const callName = st => {
  const c = st.expression.expression;
  return ts.isIdentifier(c) ? c.text : c.expression.text + '.' + c.name.text;
};
function fnBody(fn) {
  return ts.isBlock(fn.body) ? fn.body.statements : [ts.factory.createExpressionStatement(fn.body)];
}

let count = 0;
let topCount = 0;
function describe(st, titles, ind, isTop) {
  const call = st.expression;
  const title = call.arguments[0].text;
  const fn = call.arguments[1];
  const names = [...titles, title];
  const cls = isTop ? (topCount++ === 0 ? className : className.replace(/Test$/, '') + className2(title.replace(/^\S+\s*/, '')) + 'Test') : className2(title);
  scopes.push(new Map());
  let out = '';
  out += `${ind}@DisplayName(${jstr(title)})\n`;
  out += isTop ? `${ind}class ${cls} {\n` : `${ind}@Nested\n${ind}class ${cls} {\n`;
  const body = fnBody(fn);
  // fields first
  for (const s of body) {
    if (ts.isVariableStatement(s)) {
      for (const d of s.declarationList.declarations) {
        const name = d.name.getText(sf);
        if (d.initializer) {
          const r = E(d.initializer); const t = d.type ? javaType(d.type) : r.t; declare(name, t);
          out += `${ind}  ${t && t !== 'int' && t !== 'double' ? t : 'Object'} ${name} = ${r.c};\n`;
        } else {
          const t = javaType(d.type); declare(name, t);
          const isStatic = isTop && /Definitions$/.test(t);
          out += `${ind}  ${isStatic ? 'static ' : ''}${t === 'FUNCTION' ? '/* TODO-TS2J function field */ Object' : t} ${name};\n`;
        }
      }
    }
  }
  out += '\n';
  for (const s of body) {
    if (ts.isVariableStatement(s)) continue;
    if (isCall(s, ['beforeAll', 'beforeEach', 'afterEach', 'afterAll'])) {
      if (!overlaps(s) && fromLine && !isTop && false) continue;
      const k = callName(s);
      const ann = { beforeAll: 'BeforeAll', beforeEach: 'BeforeEach', afterEach: 'AfterEach', afterAll: 'AfterAll' }[k];
      const stat = (k === 'beforeAll' || k === 'afterAll') && isTop ? 'static ' : '';
      scopes.push(new Map());
      const stmts = fnBody(s.expression.arguments[0]).map(x => S(x, ind + '    ')).join('');
      scopes.pop();
      out += `${ind}  @${(k === 'beforeAll' && !isTop) ? 'BeforeEach /* TODO-TS2J nested beforeAll */' : ann}\n${ind}  ${stat}void ${k}() {\n${stmts}${ind}  }\n\n`;
      continue;
    }
    if (isCall(s, ['describe', 'describe.skip', 'describe.only'])) {
      if (!overlaps(s)) continue;
      out += describe(s, names, ind + '  ', false);
      continue;
    }
    if (isCall(s, ['it', 'it.skip', 'it.todo', 'it.only', 'test'])) {
      if (!inRange(s)) continue;
      const k = callName(s);
      const ititle = s.expression.arguments[0].text;
      const full = [...names, ititle].join(' ');
      const mname = methodName(ititle, cls);
      count++;
      out += leadingComments(s, ind + '  ');
      out += `${ind}  @Test\n${ind}  @DisplayName(${jstr(full)})\n`;
      if (k !== 'it' && k !== 'test' && k !== 'it.only') out += `${ind}  @Disabled(${jstr(k)})\n`;
      const f = s.expression.arguments[1];
      scopes.push(new Map());
      const stmts = f ? fnBody(f).map(x => S(x, ind + '    ')).join('') : '';
      scopes.pop();
      out += `${ind}  void ${mname}() {\n${stmts}${ind}  }\n\n`;
      continue;
    }
    out += S(s, ind + '  ');
  }
  out += `${ind}}\n`;
  scopes.pop();
  return out;
}

let body = '';
for (const st of sf.statements) {
  if (isCall(st, ['describe'])) body += describe(st, [], '', true);
}
const header = `package ${pkg};

import static org.hl7.fhir.sushi.export.ExportTestUtil.*;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.*;
import org.hl7.fhir.sushi.export.Package.FshMapEntry;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirtypes.*;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.common.js.*;
import org.hl7.fhir.sushi.testhelpers.*;
import org.hl7.fhir.sushi.utils.*;
import org.junit.jupiter.api.*;

// port of sushi/test/${file.replace(/^.*\/test\//, '').replace(/^.*\/ts\//, '')} (SUSHI 3.21.1)${fromLine ? ` lines ${fromLine}-${toLine}` : ''}
`;
process.stdout.write(header + body);
process.stderr.write(`ts2junit: ${count} tests\n`);
