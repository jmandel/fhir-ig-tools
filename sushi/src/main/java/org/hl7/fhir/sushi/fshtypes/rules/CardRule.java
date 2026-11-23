package org.hl7.fhir.sushi.fshtypes.rules;

import org.hl7.fhir.sushi.fshtypes.Common;

// port of sushi/src/fshtypes/rules/CardRule.ts (SUSHI 3.21.1)
public class CardRule extends Rule implements SdRule, LrRule {
  /** JS number; null = undefined, NaN possible from the importer. */
  public Double min;
  /** "*" or a number as text; null = undefined. */
  public String max;

  public CardRule(String path) {
    super(path);
  }

  @Override public String constructorName() {
    return "CardRule";
  }

  public String cardToString() {
    return (min != null && !min.isNaN() ? Common.jsNumberToString(min) : "") + ".." + (max != null ? max : "");
  }

  @Override public String toFSH() {
    return "* " + path + " " + cardToString();
  }
}
