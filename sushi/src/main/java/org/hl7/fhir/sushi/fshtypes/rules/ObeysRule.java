package org.hl7.fhir.sushi.fshtypes.rules;

// port of sushi/src/fshtypes/rules/ObeysRule.ts (SUSHI 3.21.1)
public class ObeysRule extends Rule implements SdRule, LrRule {
  public String invariant;

  public ObeysRule(String path) {
    super(path);
  }

  @Override public String constructorName() {
    return "ObeysRule";
  }

  @Override public String toFSH() {
    return "* " + (".".equals(path) ? "" : path + " ") + "obeys " + invariant;
  }
}
