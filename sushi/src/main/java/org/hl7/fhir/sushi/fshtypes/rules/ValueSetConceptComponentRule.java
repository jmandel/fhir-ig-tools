package org.hl7.fhir.sushi.fshtypes.rules;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.Common;
import org.hl7.fhir.sushi.fshtypes.FshCode;

// port of sushi/src/fshtypes/rules/ValueSetComponentRule.ts class ValueSetConceptComponentRule (SUSHI 3.21.1)
public class ValueSetConceptComponentRule extends ValueSetComponentRule {
  public List<FshCode> concepts = new ArrayList<>();

  public ValueSetConceptComponentRule(boolean inclusion) {
    super(inclusion);
  }

  @Override public String constructorName() {
    return "ValueSetConceptComponentRule";
  }

  /** One line per concept. Note: like the TS, fills in a missing concept system from the rule's system. */
  @Override public String toFSH() {
    String inclusionPart = "* " + (inclusion ? "" : "exclude ");
    List<String> lines = new ArrayList<>();
    if (from.valueSets != null && !from.valueSets.isEmpty()) {
      String originalFromPart = fromValueSetsString(from, " and ");
      for (FshCode concept : concepts) {
        String conceptPart = concept.toString();
        String fromPart = originalFromPart;
        // over 100 characters: build it again with line breaks
        if (inclusionPart.length() + conceptPart.length() + fromPart.length() > 100) {
          fromPart = Common.EOL + "   " + fromValueSetsString(from, " and" + Common.EOL + "    ");
        }
        lines.add(inclusionPart + conceptPart + fromPart);
      }
    } else {
      for (FshCode concept : concepts) {
        if (!Common.truthy(concept.system) && Common.truthy(from.system)) {
          concept.system = from.system;
        }
        lines.add(inclusionPart + concept);
      }
    }
    return String.join(Common.EOL, lines);
  }
}
