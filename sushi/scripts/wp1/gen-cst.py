#!/usr/bin/env python3
"""Generate the CST node classes (importer.parser.FSHParser / MiniFSHParser nested *Context classes) from the
ANTLR-generated Java parser (test-scope oracle), so the hand-written parser's tree has exactly ANTLR's rule names and
accessors.  Run after a grammar change:

  scripts/wp1/gen-cst.py <dir with generated FSHParser.java + MiniFSHParser.java> src/main/java/org/hl7/fhir/sushi/importer/parser

The generated parser lives in the build dir on the VM: $VMDATA/sushi-port/build/<checkout>/generated-test-sources/antlr4/
org/hl7/fhir/sushi/importer/antlr.  Only the CST part of each file is generated (between the BEGIN/END GENERATED CST
markers); the rest of the target file is kept.
"""
import re, sys, pathlib

def extract(src: str, parser: str):
    # token + rule constants and the name tables, verbatim
    m = re.search(r'\tpublic static final int\n(.*?);\n\tpublic static final int\n(.*?);\n', src, re.S)
    tokens, rules = m.group(1), m.group(2)
    def block(fn):
        mm = re.search(r'\tprivate static String\[\] ' + fn + r'\(\) \{\n\t\treturn new String\[\] \{\n(.*?)\t\t\};\n\t\}', src, re.S)
        return mm.group(1)
    rule_names = block('makeRuleNames')
    literal = block('makeLiteralNames')
    symbolic = block('makeSymbolicNames')
    ctxs = []
    for cm in re.finditer(r'\tpublic static class (\w+)Context extends ParserRuleContext \{\n(.*?)\t\tpublic \1Context\(ParserRuleContext parent, int invokingState\) \{', src, re.S):
        name, body = cm.group(1), cm.group(2)
        body = body.replace('\t\t\t', '      ').replace('\t\t', '    ')
        rule = name[0].lower() + name[1:]
        ctxs.append((name, rule, body))
    return tokens, rules, rule_names, literal, symbolic, ctxs

def render(parser, tokens, rules, rule_names, literal, symbolic, ctxs):
    ind = lambda s, n: '\n'.join((' ' * n) + l.strip() for l in s.strip('\n').split('\n') if l.strip())
    out = []
    out.append('  // ---- BEGIN GENERATED CST (scripts/wp1/gen-cst.py from the ANTLR 4.13.2 Java target; do not edit by hand) ----')
    out.append('  public static final int EOF = Token.EOF;')
    out.append('  public static final int')
    out.append(ind(tokens, 4) + ';')
    out.append('  public static final int')
    out.append(ind(rules, 4) + ';')
    out.append('  public static final String[] ruleNames = {')
    out.append(ind(rule_names, 4))
    out.append('  };')
    out.append('  /** ANTLR literal names by token type (JS runtime: {@code recognizer.literalNames}). */')
    out.append('  public static final String[] literalNames = {')
    out.append(ind(literal, 4))
    out.append('  };')
    out.append('  /** ANTLR symbolic names by token type (JS runtime: {@code recognizer.symbolicNames}). */')
    out.append('  public static final String[] symbolicNames = {')
    out.append(ind(symbolic, 4))
    out.append('  };')
    for name, rule, body in ctxs:
        out.append('')
        out.append(f'  public static class {name}Context extends ParserRuleContext {{')
        out.append(body.rstrip('\n'))
        out.append(f'    public {name}Context(ParserRuleContext parent, int invokingState) {{ super(parent, invokingState); }}')
        out.append(f'    @Override public int getRuleIndex() {{ return RULE_{rule}; }}')
        out.append('  }')
    out.append('')
    out.append('  /** Creates the empty context object for a rule index (used by the parser and the test adapter). */')
    out.append('  public static ParserRuleContext newContext(int ruleIndex, ParserRuleContext parent, int invokingState) {')
    out.append('    return switch (ruleIndex) {')
    for name, rule, body in ctxs:
        out.append(f'      case RULE_{rule} -> new {name}Context(parent, invokingState);')
    out.append('      default -> throw new IllegalArgumentException("rule " + ruleIndex);')
    out.append('    };')
    out.append('  }')
    out.append('  // ---- END GENERATED CST ----')
    return '\n'.join(out)

def main():
    gen, target = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    for parser in ('FSHParser', 'MiniFSHParser'):
        src = (gen / f'{parser}.java').read_text()
        cst = render(parser, *extract(src, parser))
        f = target / f'{parser}.java'
        text = f.read_text()
        text = re.sub(r'  // ---- BEGIN GENERATED CST.*?// ---- END GENERATED CST ----', lambda _: cst, text, flags=re.S)
        f.write_text(text)
        print(f'{f}: {text.count("Context extends ParserRuleContext")} contexts')

main()
