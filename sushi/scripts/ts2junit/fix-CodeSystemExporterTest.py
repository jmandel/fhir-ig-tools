import re,sys
p=sys.argv[1]
s=open(p).read()
pat=re.compile(r'(\n\s*)(?:var|List<Object>) (\w+) = map\(new ArrayList<>\(Arrays\.asList\((JsAssert\.json\("""(?:.|\n)*?"""\)(?:, JsAssert\.json\("""(?:.|\n)*?"""\))*)\)\), \(\{ pathArray, value \}\) -> \{\n(?:.|\n)*?\n\}\);')
s,n1=pat.subn(lambda m: f'{m.group(1)}List<CaretValueRule> {m.group(2)} = designationRules({m.group(3)});',s)
pat2=re.compile(r'(\n\s*)(?:var|List<Object>) \[([\w, ]+)\] = map\(new ArrayList<>\(Arrays\.asList\((JsAssert\.json\("""(?:.|\n)*?"""\)(?:, JsAssert\.json\("""(?:.|\n)*?"""\))*)\)\), \(\{ pathArray, value \}\) -> \{\n(?:.|\n)*?\n\}\);')
def r2(m):
    names=[x.strip() for x in m.group(2).split(',')]
    out=f'{m.group(1)}List<CaretValueRule> designations = designationRules({m.group(3)});'
    for i,nm in enumerate(names):
        out+=f'{m.group(1)}CaretValueRule {nm} = designations.get({i});'
    return out
s,n2=pat2.subn(r2,s)
s=s.replace("codeSystem.rules.add(/* TODO-TS2J expr: ...[new ConceptRule('myCode'), new ConceptRule('anotherCode')] */);",'codeSystem.rules.addAll(Arrays.asList(new ConceptRule("myCode"), new ConceptRule("anotherCode")));')
helper='''
  /** The TS test's {@code [{ pathArray, value }, ...].map(...)}: code caret rules setting designation[0].value. */
  @SuppressWarnings("unchecked")
  static List<CaretValueRule> designationRules(Object... specs) {
    List<CaretValueRule> rules = new ArrayList<>();
    for (Object spec : specs) {
      CaretValueRule rule = new CaretValueRule("");
      rule.pathArray = new ArrayList<>((List<String>) (List<?>) at(spec, "pathArray"));
      rule.caretPath = "designation[0].value";
      rule.value = at(spec, "value");
      rules.add(rule);
    }
    return rules;
  }
'''
s=s.replace('class CodeSystemExporterTest {\n','class CodeSystemExporterTest {\n'+helper,1)
assert n1 == 1 and n2 == 1, (n1, n2)
open(p,'w').write(s)
