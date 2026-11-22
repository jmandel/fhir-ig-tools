package org.hl7.fhir.sushi.fshtypes.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.Common;

// port of sushi/src/fshtypes/rules/ConceptRule.ts (SUSHI 3.21.1)
public class ConceptRule extends Rule {
  private static final Pattern NEEDS_QUOTES = Pattern.compile("^\"|" + Common.JS_WS);

  /** Unused, but allowed so RuleSets can hold either a ConceptRule or a ValueSetConceptComponentRule. */
  public String system;
  /** Codes enclosing this one (top-level code: empty). */
  public List<String> hierarchy = new ArrayList<>();
  public String code;
  public String display;
  public String definition;

  public ConceptRule(String code) {
    this(code, null, null);
  }

  public ConceptRule(String code, String display) {
    this(code, display, null);
  }

  public ConceptRule(String code, String display, String definition) {
    super("");
    this.code = code;
    this.display = display;
    this.definition = definition;
  }

  @Override public String constructorName() {
    return "ConceptRule";
  }

  @Override public String toFSH() {
    List<String> quotedCodes = new ArrayList<>();
    List<String> all = new ArrayList<>(hierarchy);
    all.add(code);
    for (String c : all) {
      quotedCodes.add(NEEDS_QUOTES.matcher(c).find() ? "#\"" + Common.fshifyString(c) + "\"" : "#" + c);
    }
    String line = "* " + String.join(" ", quotedCodes);
    if (Common.truthy(display)) {
      line += " \"" + Common.fshifyString(display) + "\"";
    }
    if (Common.truthy(definition)) {
      // Without a display, a definition must use triple quotes to be told apart from a display.
      String quotes = Common.truthy(display) ? "\"" : "\"\"\"";
      line += " " + quotes + Common.fshifyString(definition) + quotes;
    }
    return line;
  }
}
