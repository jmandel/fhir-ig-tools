package org.hl7.fhir.sushi.fshtypes.rules;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.Common;

// port of sushi/src/fshtypes/rules/OnlyRule.ts (SUSHI 3.21.1)
public class OnlyRule extends Rule implements SdRule, LrRule {
  public List<OnlyRuleType> types = new ArrayList<>();

  public OnlyRule(String path) {
    super(path);
  }

  @Override public String constructorName() {
    return "OnlyRule";
  }

  @Override public String toFSH() {
    return "* " + path + " only " + Common.typeString(types);
  }
}
