package org.hl7.fhir.sushi.fshtypes.rules;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.Common;
import org.hl7.fhir.sushi.fshtypes.ValueSetFilter;

// port of sushi/src/fshtypes/rules/ValueSetComponentRule.ts class ValueSetFilterComponentRule (SUSHI 3.21.1)
public class ValueSetFilterComponentRule extends ValueSetComponentRule {
  public List<ValueSetFilter> filters = new ArrayList<>();

  public ValueSetFilterComponentRule(boolean inclusion) {
    super(inclusion);
  }

  @Override public String constructorName() {
    return "ValueSetFilterComponentRule";
  }

  @Override public String toFSH() {
    String inclusionPart = "* " + (inclusion ? "include" : "exclude") + " codes";
    String fromPart = fromString(from, " and ");
    String filterPart = buildFilterString(" and ");
    // over 100 characters: build it again with line breaks
    if (inclusionPart.length() + fromPart.length() + filterPart.length() > 100) {
      fromPart = fromString(from, " and" + Common.EOL + "    ");
      filterPart = Common.EOL + "   " + buildFilterString(" and" + Common.EOL + "    ");
    }
    return inclusionPart + fromPart + filterPart;
  }

  private String buildFilterString(String separator) {
    if (filters.isEmpty()) return "";
    List<String> parts = new ArrayList<>();
    for (ValueSetFilter f : filters) {
      parts.add(f.property + " " + f.operator + " " + f.valueToFSH());
    }
    return " where " + String.join(separator, parts);
  }
}
