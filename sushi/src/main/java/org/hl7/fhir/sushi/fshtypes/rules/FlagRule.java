package org.hl7.fhir.sushi.fshtypes.rules;

// port of sushi/src/fshtypes/rules/FlagRule.ts (SUSHI 3.21.1)
public class FlagRule extends FlagCarryingRule implements SdRule, LrRule {
  public FlagRule(String path) {
    super(path);
  }

  @Override public String constructorName() {
    return "FlagRule";
  }

  public String flagsToString() {
    return String.join(" ", flags());
  }

  @Override public String toFSH() {
    return "* " + path + " " + flagsToString();
  }
}
