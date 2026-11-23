package org.hl7.fhir.sushi.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.Common;
import org.hl7.fhir.sushi.fshtypes.ParamRuleSet;
import org.hl7.fhir.sushi.importer.parser.MiniFSHParser;
import org.hl7.fhir.sushi.importer.parser.MiniFSHParser.RulePartContext;
import org.hl7.fhir.sushi.importer.parser.MiniFSHParser.RuleSetContext;
import org.hl7.fhir.sushi.importer.parser.MiniFSHParser.SomeRuleContext;
import org.hl7.fhir.sushi.importer.parser.TerminalNode;

// port of sushi/src/import/MiniFSHImporter.ts (SUSHI 3.21.1)
/** Substitutes insert-rule parameter values into a parameterized RuleSet's raw text (re-parsed by FSHImporter). */
public final class MiniFSHImporter {
  private static final String WS = Common.JS_WS;
  /** JS /(?:,|\()\s*\[\[.+?\]\]\s*(?=,|\))/g ('.' excludes JS line terminators). */
  private static final Pattern BRACKET_DETECTOR =
      Pattern.compile("(?:,|\\()" + WS + "*\\[\\[[^\\n\\r\\u2028\\u2029]+?\\]\\]" + WS + "*(?=,|\\))");

  private final ParamRuleSet ruleSet;
  private final List<String> values;
  private final Pattern bracketParamUsage;
  private final Pattern plainParamUsage;

  private MiniFSHImporter(ParamRuleSet ruleSet, List<String> values) {
    this.ruleSet = ruleSet;
    this.values = values;
    List<String> quoted = new ArrayList<>();
    for (String p : ruleSet.parameters) quoted.add(Pattern.quote(p));
    String escapedParameters = String.join("|", quoted);
    this.bracketParamUsage = Pattern.compile("\\[\\[\\{" + WS + "*(" + escapedParameters + ")" + WS + "*\\}\\]\\]|\\{"
        + WS + "*(" + escapedParameters + ")" + WS + "*\\}");
    this.plainParamUsage = Pattern.compile("\\{" + WS + "*(" + escapedParameters + ")" + WS + "*\\}");
  }

  /** TS applyRuleSetSubstitutions: the FSH text {@code RuleSet: <name>\n<rules with params substituted>}. */
  public static String applyRuleSetSubstitutions(ParamRuleSet ruleSet, List<String> values) {
    return new MiniFSHImporter(ruleSet, values).transformRuleSet();
  }

  private String transformRuleSet() {
    // TS uses the MiniFSH parser with ANTLR's default (console) error listener
    RuleSetContext ctx = MiniFSHParser.parseRuleSet(ruleSet.contents);
    List<String> transformedRules = visitRuleSet(ctx);
    return "RuleSet: " + ruleSet.name + Common.EOL + String.join(Common.EOL, transformedRules);
  }

  private List<String> visitRuleSet(RuleSetContext ctx) {
    List<String> out = new ArrayList<>();
    for (SomeRuleContext ruleCtx : ctx.someRule()) out.add(visitSomeRule(ruleCtx));
    return out;
  }

  private String visitSomeRule(SomeRuleContext ctx) {
    List<RulePartContext> ruleParts = ctx.rulePart();
    if (ruleParts.isEmpty()) throw JsTypeError.readUndefined("getText");
    boolean regularInsert = ruleParts.get(0).getText().equals("insert") && ruleParts.size() > 1;
    boolean pathInsert = ruleParts.size() > 1 && ruleParts.get(1).getText().equals("insert");
    String indent = " ".repeat(Math.max(0, getStarContextStartColumn(ctx) - 1));
    if (regularInsert || pathInsert) {
      return indent + doBracketAwareSubstitution(ctx);
    }
    return indent + doRegularSubstitution(ctx);
  }

  private static String ruleText(SomeRuleContext ctx) {
    List<String> parts = new ArrayList<>();
    for (RulePartContext rp : ctx.rulePart()) parts.add(rp.getText());
    return String.join(" ", parts);
  }

  private static String escapeBrackets(String v) {
    return v.replace("]],", "]]\\,").replace("]])", "]]\\)");
  }

  private String doBracketAwareSubstitution(SomeRuleContext ctx) {
    String ruleText = ruleText(ctx);
    List<int[]> bracketZones = new ArrayList<>();
    Matcher bz = BRACKET_DETECTOR.matcher(ruleText);
    while (bz.find()) bracketZones.add(new int[] {bz.start(), bz.end()});

    Matcher m = bracketParamUsage.matcher(ruleText);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String fullMatch = m.group();
      String replacement;
      if (fullMatch.startsWith("[")) {
        int matchIndex = ruleSet.parameters.indexOf(m.group(1));
        replacement = matchIndex > -1 ? "[[" + escapeBrackets(values.get(matchIndex)) + "]]" : "";
      } else {
        int matchIndex = ruleSet.parameters.indexOf(m.group(2));
        if (matchIndex > -1) {
          // a match without brackets might still be within brackets, but with extra contents.
          // check the bracket ranges to see which substitution to perform.
          int offset = m.start();
          boolean inZone = bracketZones.stream().anyMatch(z -> z[0] < offset && offset < z[1]);
          replacement = inZone ? escapeBrackets(values.get(matchIndex)) : values.get(matchIndex);
        } else {
          replacement = "";
        }
      }
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return "* " + sb;
  }

  private String doRegularSubstitution(SomeRuleContext ctx) {
    String ruleText = ruleText(ctx);
    Matcher m = plainParamUsage.matcher(ruleText);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      int matchIndex = ruleSet.parameters.indexOf(m.group(1));
      m.appendReplacement(sb, Matcher.quoteReplacement(matchIndex > -1 ? values.get(matchIndex) : ""));
    }
    m.appendTail(sb);
    return "* " + sb;
  }

  private static int getStarContextStartColumn(SomeRuleContext ctx) {
    TerminalNode star = ctx.STAR();
    if (star == null) throw JsTypeError.readNull("getText");
    String t = star.getText();
    return t.length() - t.lastIndexOf('\n') - 2;
  }
}
