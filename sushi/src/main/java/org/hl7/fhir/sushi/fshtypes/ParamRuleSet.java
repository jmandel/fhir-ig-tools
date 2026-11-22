package org.hl7.fhir.sushi.fshtypes;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// port of sushi/src/fshtypes/ParamRuleSet.ts (SUSHI 3.21.1)
/** A parameterized RuleSet: kept as raw text and re-parsed per insert, after parameter substitution. */
public class ParamRuleSet extends FshEntity {
  public String name;
  public List<String> parameters = new ArrayList<>();
  public String contents = "";

  public ParamRuleSet(String name) {
    this.name = name;
  }

  /** Parameters never referenced as {@code {param}} (whitespace allowed inside the braces) in the contents. */
  public List<String> getUnusedParameters() {
    List<String> unused = new ArrayList<>();
    for (String param : parameters) {
      Pattern p = Pattern.compile("\\{" + Common.JS_WS + "*" + Pattern.quote(param) + Common.JS_WS + "*\\}");
      if (!p.matcher(contents).find()) unused.add(param);
    }
    return unused;
  }
}
