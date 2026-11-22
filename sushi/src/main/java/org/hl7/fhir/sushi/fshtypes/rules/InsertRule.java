package org.hl7.fhir.sushi.fshtypes.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.Common;

// port of sushi/src/fshtypes/rules/InsertRule.ts (SUSHI 3.21.1)
public class InsertRule extends Rule implements SdRule, LrRule {
  // JS /(^\s+)|(\s+$)|[,)]/ : JS $ without the m flag is end of input, i.e. Java \z.
  private static final Pattern NEEDS_BRACKETS =
      Pattern.compile("(^" + Common.JS_WS + "+)|(" + Common.JS_WS + "+\\z)|[,)]");
  private static final Pattern BRACKET_END = Pattern.compile("(\\]\\]" + Common.JS_WS + "*)([,)])");

  public String ruleSet;
  public List<String> params = new ArrayList<>();
  /** For inserts on a code in a CodeSystem/ValueSet: the code hierarchy. */
  public List<String> pathArray = new ArrayList<>();

  public InsertRule(String path) {
    super(path);
  }

  @Override public String constructorName() {
    return "InsertRule";
  }

  private String fshifyParameters() {
    List<String> out = new ArrayList<>();
    for (String originalParam : params) {
      if (NEEDS_BRACKETS.matcher(originalParam).find()) {
        String escaped = originalParam.replace("\\", "\\\\");
        out.add("[[" + BRACKET_END.matcher(escaped).replaceAll("$1\\\\$2") + "]]");
      } else {
        out.add(originalParam.replace("\\", "\\\\"));
      }
    }
    return String.join(", ", out);
  }

  @Override public String toFSH() {
    String printablePath;
    if (!pathArray.isEmpty()) {
      List<String> codes = new ArrayList<>();
      for (String code : pathArray) codes.add("#" + code);
      printablePath = String.join(" ", codes) + " ";
    } else {
      printablePath = !path.isEmpty() ? path + " " : "";
    }
    String paramPart = !params.isEmpty() ? "(" + fshifyParameters() + ")" : "";
    return "* " + printablePath + "insert " + ruleSet + paramPart;
  }
}
