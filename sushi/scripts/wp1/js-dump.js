// Dump tokens, syntax errors and the parse tree of FSH (or MiniFSH) inputs with SUSHI's own generated JS parser on the
// antlr4 JS runtime -- the ground truth the Java parsers are compared against. Output format = CstDump.java.
//
// usage: node js-dump.js <sushi checkout with dist/ and node_modules/> <fsh|mini> <list file>
//   list file: lines "<input path>\t<output path>". Inputs are read as UTF-8; in fsh mode a final newline is added when
//   missing, exactly as FSHImporter.import does before parsing (mini inputs are parsed as read).
'use strict';
const fs = require('fs');
const path = require('path');
const [sushiDir, mode, listFile] = process.argv.slice(2);
const req = m => require(path.join(sushiDir, m));
const antlr4 = require(path.join(sushiDir, 'node_modules/antlr4'));
const gen = path.join('dist/import/generated');
const Lexer = req(path.join(gen, mode === 'mini' ? 'MiniFSHLexer' : 'FSHLexer')).default;
const Parser = req(path.join(gen, mode === 'mini' ? 'MiniFSHParser' : 'FSHParser')).default;

function esc(s) {
  if (s === null || s === undefined) return String(s);
  let o = '';
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    const ch = s[i];
    if (ch === '\\') o += '\\\\';
    else if (ch === '\n') o += '\\n';
    else if (ch === '\r') o += '\\r';
    else if (ch === '\t') o += '\\t';
    else if (ch === "'") o += "\\'";
    else if (c < 0x20 || c > 0x7e) o += '\\u' + c.toString(16).padStart(4, '0');
    else o += ch;
  }
  return o;
}
const tok = t => t == null ? 'null'
  : `${t.tokenIndex}:${t.type}:${t.channel}:${t.start}-${t.stop}:${t.line}.${t.column}'${esc(t.text)}'`;
const idx = t => t == null ? 'null' : t.tokenIndex < 0 ? `conj(${tok(t)})` : String(t.tokenIndex);

function tree(node, ruleNames, depth, out) {
  const pad = '  '.repeat(depth);
  if (node instanceof antlr4.ParserRuleContext) {
    let s = `${pad}(${ruleNames[node.ruleIndex]} start=${idx(node.start)} stop=${idx(node.stop)}`;
    if (node.exception) {
      const kind = node.exception instanceof antlr4.NoViableAltException ? 'NO_VIABLE_ALT' : 'INPUT_MISMATCH';
      s += ` exc=${kind}@${idx(node.exception.offendingToken)}`;
    }
    out.push(s);
    for (const c of node.children || []) tree(c, ruleNames, depth + 1, out);
  } else if (node.isErrorNode && node.isErrorNode()) {
    out.push(`${pad}!ERR ${tok(node.symbol)}`);
  } else {
    out.push(`${pad}${tok(node.symbol)}`);
  }
}

function dump(text) {
  const errors = [];
  const listener = new antlr4.ErrorListener();
  listener.syntaxError = (rec, sym, line, col, msg) => {
    errors.push(`${line}:${col} ${sym == null ? 'lexer' : idx(sym)} ${esc(msg)}`);
  };
  const lexer = new Lexer(new antlr4.InputStream(text));
  lexer.removeErrorListeners();
  lexer.addErrorListener(listener);
  const tokens = new antlr4.CommonTokenStream(lexer);
  const parser = new Parser(tokens);
  parser.removeErrorListeners();
  parser.addErrorListener(listener);
  parser.buildParseTrees = true;
  const t = mode === 'mini' ? parser.ruleSet() : parser.doc();
  tokens.fill();
  const out = ['#tokens'];
  for (const k of tokens.tokens) out.push(tok(k));
  out.push('#errors', ...errors, '#tree');
  tree(t, parser.ruleNames, 0, out);
  return out.join('\n') + '\n';
}

for (const line of fs.readFileSync(listFile, 'utf8').split('\n')) {
  if (!line.trim()) continue;
  const [inp, outp] = line.split('\t');
  let result;
  try {
    let text = fs.readFileSync(inp, 'utf8');
    if (mode === 'fsh' && !text.endsWith('\n')) text += '\n';
    result = dump(text);
  } catch (e) {
    result = '#crash\n' + esc(String(e && e.stack || e)) + '\n';
  }
  fs.mkdirSync(path.dirname(outp), { recursive: true });
  fs.writeFileSync(outp, result);
}
