package org.hl7.fhir.sushi.fshtypes.rules;

import java.util.List;
import org.hl7.fhir.sushi.fshtypes.ValueSetComponentFrom;

// port of sushi/src/fshtypes/rules/ValueSetComponentRule.ts (SUSHI 3.21.1)
/** Base of ValueSetConceptComponentRule and ValueSetFilterComponentRule (same file in TS). */
public class ValueSetComponentRule extends Rule {
  public ValueSetComponentFrom from = new ValueSetComponentFrom();
  public boolean inclusion;

  public ValueSetComponentRule(boolean inclusion) {
    super("");
    this.inclusion = inclusion;
  }

  @Override public String constructorName() {
    return "ValueSetComponentRule";
  }

  @Override public String toFSH() {
    return "* " + (inclusion ? "include" : "exclude") + " codes" + fromString(from, " and ");
  }

  static String fromString(ValueSetComponentFrom from, String separator) {
    if (from.system == null && from.valueSets == null) return "";
    String s = " from ";
    if (from.system != null && !from.system.isEmpty()) {
      s += "system " + from.system;
    }
    if (from.valueSets != null) {
      s += (from.system != null && !from.system.isEmpty() ? separator : "") + "valueset "
          + String.join(separator, from.valueSets);
    }
    return s;
  }

  static String fromValueSetsString(ValueSetComponentFrom from, String separator) {
    if (from.valueSets == null) return "";
    return " from valueset " + String.join(separator, from.valueSets);
  }

  static List<String> nonNull(List<String> l) {
    return l == null ? List.of() : l;
  }
}
