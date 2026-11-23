// Jest setupFiles hook: records every input SUSHI's tests feed to the FSH and MiniFSH parsers (one file per distinct
// text, named by SHA-1) into $WP1_CAPTURE/{fsh,mini}/. Used to build the parser differential test set from the
// FSH snippets inlined in SUSHI's Jest tests (many intentionally invalid).
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const OUT = process.env.WP1_CAPTURE;
const SUSHI = process.env.WP1_SUSHI;
function wrap(mod, method, kind) {
  const P = require(path.join(SUSHI, 'src/import/generated', mod)).default;
  const orig = P.prototype[method];
  if (orig.__wp1) return;
  const wrapped = function (...args) {
    try {
      const text = this._input.tokenSource._input.strdata;
      const h = crypto.createHash('sha1').update(text).digest('hex');
      fs.mkdirSync(path.join(OUT, kind), { recursive: true });
      fs.writeFileSync(path.join(OUT, kind, `${h}.${kind}`), text);
    } catch (e) {
      // capture must never change test behaviour
    }
    return orig.apply(this, args);
  };
  wrapped.__wp1 = true;
  P.prototype[method] = wrapped;
}
wrap('FSHParser.js', 'doc', 'fsh');
wrap('MiniFSHParser.js', 'ruleSet', 'mini');
