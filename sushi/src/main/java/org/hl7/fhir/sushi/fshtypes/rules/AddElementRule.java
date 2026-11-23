package org.hl7.fhir.sushi.fshtypes.rules;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.Common;

// port of sushi/src/fshtypes/rules/AddElementRule.ts (SUSHI 3.21.1)
public class AddElementRule extends FlagCarryingRule implements LrRule {
  /** JS number; null = undefined. */
  public Double min;
  public String max;
  public List<OnlyRuleType> types = new ArrayList<>();
  public String contentReference;
  /** TS `short` (a Java keyword). */
  public String short_;
  public String definition;

  public AddElementRule(String path) {
    super(path);
  }

  @Override public String constructorName() {
    return "AddElementRule";
  }

  @Override public String toFSH() {
    String cardPart = Common.jsString(min) + ".." + Common.jsString(max);
    List<String> f = flags();
    String flagPart = !f.isEmpty() ? " " + String.join(" ", f) : "";
    String shortPart = Common.truthy(short_) ? " \"" + Common.fshifyString(short_) + "\"" : "";
    String definitionPart = Common.truthy(definition) ? " \"" + Common.fshifyString(definition) + "\"" : shortPart;
    if (!types.isEmpty()) {
      return "* " + path + " " + cardPart + flagPart + " " + Common.typeString(types) + shortPart + definitionPart;
    }
    return "* " + path + " " + cardPart + flagPart + " contentReference " + Common.jsString(contentReference)
        + shortPart + definitionPart;
  }
}
