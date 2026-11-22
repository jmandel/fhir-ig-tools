package org.hl7.fhir.sushi.fshtypes.rules;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.Common;

// port of sushi/src/fshtypes/rules/ContainsRule.ts (SUSHI 3.21.1)
public class ContainsRule extends Rule implements SdRule {
  public List<ContainsRuleItem> items = new ArrayList<>();

  public ContainsRule(String path) {
    super(path);
  }

  @Override public String constructorName() {
    return "ContainsRule";
  }

  @Override public String toFSH() {
    List<String> lines = new ArrayList<>();
    for (ContainsRuleItem item : items) {
      String line = item.type != null && !item.type.isEmpty() ? item.type + " named " + item.name : item.name;
      // A cardinality for syntactic correctness; a later CardRule provides the real one.
      lines.add(line + " 0..");
    }
    return "* " + path + " contains" + (lines.size() > 1 ? Common.EOL + "    " : " ")
        + String.join(" and" + Common.EOL + "    ", lines);
  }
}
