package org.hl7.fhir.sushi.fshtypes.rules;

// port of sushi/src/fshtypes/rules/BindingRule.ts (SUSHI 3.21.1)
public class BindingRule extends Rule implements SdRule, LrRule {
  public String valueSet;
  /**
   * Kept as the FSH text (e.g. "required"), not an enum: the importer stores whatever was written and the exporter
   * reports invalid strengths (BindingStrengthError) with the original text.
   */
  public String strength;

  public BindingRule(String path) {
    super(path);
  }

  @Override public String constructorName() {
    return "BindingRule";
  }

  @Override public String toFSH() {
    return "* " + path + " from " + valueSet + " (" + strength + ")";
  }
}
