// WP-9 fuzz inputs (deterministic): grammar-generated xhtml-ish fragments plus token/char mutations of corpus values.
// usage: node fuzz-gen.js <seed> <count> [corpus.ndjson] > fuzz.ndjson
'use strict';
let seed = (+process.argv[2] >>> 0) || 1;
const count = +process.argv[3] || 1000;
function rnd() { // mulberry32
  seed = (seed + 0x6D2B79F5) >>> 0; let t = seed;
  t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const ri = n => Math.floor(rnd() * n);
const pick = a => a[ri(a.length)];
const corpus = process.argv[4] ? require('fs').readFileSync(process.argv[4], 'utf8').split('\n').filter(Boolean).map(JSON.parse) : [];

const TAGS = ['div', 'p', 'span', 'b', 'i', 'a', 'br', 'table', 'tr', 'td', 'th', 'tbody', 'thead', 'ul', 'li', 'ol',
  'pre', 'textarea', 'code', 'em', 'strong', 'img', 'h1', 'h3', 'sub', 'sup', 'wbr', 'nobr', 'svg', 'SVG', 'g', 'rect',
  'script', 'style', 'Div', 'B', 'font', 'label', 'input', 'meta', 'link', 'col', 'colgroup', 'caption', 'dl', 'dt',
  'dd', 'blockquote', 'hr', 'html', 'head', 'body', 'option', 'select', 'button', 'q', 'del', 'ins', 'small', 'xhtml:div',
  'source', 'object', 'abbr', 'time', 'mark', 'math', 'tt', 'u', 's', 'kbd', 'iframe', 'noscript', 'template', 'x-y'];
const ATTRS = ['class', 'style', 'id', 'href', 'src', 'title', 'xmlns', 'lang', 'xml:lang', 'onclick', 'onmouseover',
  'tabindex', 'colspan', 'rowspan', 'srcset', 'content', 'name', 'http-equiv', 'type', 'media', 'rel', 'checked',
  'disabled', 'cite', 'action', 'value', 'viewBox', 'CLASS', 'data-x', 'hasOwnProperty', '__proto__', 'constructor'];
const WS = [' ', '  ', '\n', '\t', '\r\n', '\f', ' ', '   ', ' ', '　', '﻿', '\u000b', '​', ''];
const TEXT = ['hello', 'x', 'Patient', '&amp;', '&lt;', '&gt;', '&nbsp;', '&#160;', '&#xA0;', '&copy;', '&bogus;',
  '&#0;', '&#x110000;', '&#55296;', '&', '<', '>', '"', "'", '=', '/', 'a < b', 'é', '😀', '\ud800',
  'undefined', '<?php x ?>', '<% y %>', '<!-- c -->', '<!---->', '<!-->', '<!-- htmlmin:ignore -->',
  '<![CDATA[ z ]]>', '<![if IE]>', '<![endif]>', '<!DOCTYPE html>', '<!doctype x>', ']]>', '--', '0.90000', '1.0x',
  'img.png 2x', 'a.png  1x , b.png 100w', 'javascript: f()', 'color: red ;', 'width=device-width, initial-scale=1.0',
  'text/css', 'text/javascript;charset=x', 'stylesheet', 'viewport', 'content-security-policy', '12', ' 3 ', 'İ'];
const CLEAN_TEXT = TEXT.filter(t => !/[<&>]/.test(t) || /^&(amp|lt|gt|nbsp|copy|#160|#xA0);$/.test(t));
function wsTag() { return clean ? pick(['', '', ' ', '\n', '\t', '\r\n', '  ']) : ws(); }
function ws() { return rnd() < 0.5 ? '' : pick(WS) + (rnd() < 0.3 ? pick(WS) : ''); }
function attrValue() {
  let v = ''; const n = ri(4); for (let i = 0; i < n; i++) v += pick([pick(TEXT), pick(WS), pick(ATTRS)]);
  return v;
}
let clean = false;
function attr() {
  const name = pick(ATTRS); const r = rnd();
  if (clean && name === 'hasOwnProperty' && rnd() < 0.9) return 'title="t"';
  if (clean) return name + '="' + attrValue().replace(/[<&"]/g, c => ({ '<': '&lt;', '&': '&amp;', '"': '&quot;' }[c])) + '"';
  if (r < 0.1) return name;
  const v = attrValue();
  if (r < 0.55) return name + ws() + '=' + ws() + '"' + v.replace(/"/g, rnd() < 0.8 ? '&quot;' : '"') + '"';
  if (r < 0.85) return name + '=\'' + v.replace(/'/g, rnd() < 0.8 ? '&apos;' : "'") + '\'';
  return name + '=' + v.replace(/[\s"'`=<>]/g, '');
}
function node(depth) {
  const r = rnd();
  if (depth > 4 || r < 0.35) {
    if (clean) { let t = ''; const n = 1 + ri(3); for (let i = 0; i < n; i++) t += pick([pick(CLEAN_TEXT), pick(WS), ws() + 'word' + ws()]); return t; }
    let t = ''; const n = 1 + ri(3); for (let i = 0; i < n; i++) t += pick([pick(TEXT), pick(WS), ws() + 'word' + ws()]); return t; }
  const tag = pick(TAGS);
  let a = ''; const na = ri(4); for (let i = 0; i < na; i++) a += (clean || rnd() < 0.95 ? ' ' + wsTag() : '') + attr();
  if (rnd() < 0.15) return '<' + tag + a + wsTag() + (rnd() < 0.7 ? '/>' : ' />');
  let inner = ''; const nk = ri(4); for (let i = 0; i < nk; i++) inner += node(depth + 1) + ws();
  if (clean) return '<' + tag + a + wsTag() + '>' + ws() + inner + '</' + tag + '>';
  const close = rnd() < 0.85 ? '</' + tag + ws() + '>' : rnd() < 0.5 ? '' : '</' + pick(TAGS) + '>';
  return '<' + tag + a + ws() + '>' + ws() + inner + close;
}
function generated() {
  if (clean) {
    let body = ''; const n = 1 + ri(3); for (let i = 0; i < n; i++) body += node(1) + ws();
    return pick(['', ' ', '\n', '  \n']) + '<div xmlns="http://www.w3.org/1999/xhtml"' + (rnd() < 0.3 ? ' ' + attr() : '') + '>' + ws() + body + '</div>' + pick(['', ' ', '\n']);
  }
  const wrap = rnd() < 0.6;
  let body = ''; const n = 1 + ri(3); for (let i = 0; i < n; i++) body += node(1) + ws();
  return wrap ? ws() + '<div xmlns="http://www.w3.org/1999/xhtml"' + (rnd() < 0.3 ? ' ' + attr() : '') + '>' + ws() + body + '</div>' + ws() : body;
}
function mutate(s) {
  const k = 1 + ri(4);
  for (let j = 0; j < k; j++) {
    const p = ri(s.length + 1); const r = rnd();
    if (r < 0.25) s = s.slice(0, p) + s.slice(p + 1 + ri(8));
    else if (r < 0.5) s = s.slice(0, p) + pick(TEXT) + s.slice(p);
    else if (r < 0.65) s = s.slice(0, p) + pick(WS) + s.slice(p);
    else if (r < 0.8) s = s.slice(0, p) + pick(['<', '>', '/', '"', "'", '=', '&', ';', '!', '?', '-', ']']) + s.slice(p);
    else if (r < 0.9) s = s.slice(0, p) + node(2) + s.slice(p);
    else { const q = ri(s.length + 1); s = s.slice(0, Math.min(p, q)) + s.slice(Math.max(p, q)).toUpperCase(); }
  }
  return s;
}
const out = [];
for (let i = 0; i < count; i++) {
  const r = rnd();
  let s;
  clean = rnd() < 0.5;
  if (clean) { s = generated(); out.push(JSON.stringify(s)); continue; }
  if (corpus.length && r < 0.4) { s = pick(corpus); if (s.length > 4000) s = s.slice(0, ri(4000)); s = mutate(s); }
  else if (r < 0.75) s = generated();
  else s = mutate(generated());
  out.push(JSON.stringify(s));
}
process.stdout.write(out.join('\n') + '\n');
